using SteamKit2;
using System.Net.Http;

namespace TmlAndroidContent.Steam;

public sealed class SteamAuthenticationException(EResult result)
    : InvalidOperationException($"Steam login failed: {result}")
{
    public EResult Result { get; } = result;

    public string ResultName => Result.ToString();
}

public sealed class SteamSession : IDisposable
{
    public const uint TerrariaAppId = 105600;
    private static readonly TimeSpan ConnectionTimeout = TimeSpan.FromSeconds(30);

    private SteamClient? _client;
    private CallbackManager _callbacks;
    private SteamUser _user;
    private SteamApps _apps;
    private SteamContent _content;
    private readonly TaskCompletionSource _connected =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly TaskCompletionSource<EResult> _loggedOn =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly SteamCredentials? _credentials;
    private Thread? _callbackThread;
    private volatile bool _running;
    private int _disposed;

    /// <summary>匿名会话：只够查 appinfo / 创意工坊；下载游戏内容仍要有 license。</summary>
    public SteamSession()
    {
        _credentials = null;
        Init();
    }

    public SteamSession(SteamCredentials credentials)
    {
        ArgumentNullException.ThrowIfNull(credentials);
        _credentials = credentials;
        Init();
    }

    private void Init()
    {
        var configuration = SteamNetworking.Create();
        _client = new SteamClient(configuration);
        _callbacks = new CallbackManager(_client);
        _user = _client.GetHandler<SteamUser>()!;
        _apps = _client.GetHandler<SteamApps>()!;
        _content = _client.GetHandler<SteamContent>()!;

        _callbacks.Subscribe<SteamClient.ConnectedCallback>(_ =>
        {
            _connected.TrySetResult();
            if (_credentials is null)
            {
                _user.LogOnAnonymous();
                return;
            }

            _user.LogOn(new SteamUser.LogOnDetails
            {
                Username = _credentials.AccountName,
                AccessToken = _credentials.RefreshToken,
                ShouldRememberPassword = true,
            });
        });
        _callbacks.Subscribe<SteamUser.LoggedOnCallback>(callback =>
        {
            if (callback.Result == EResult.OK)
                SteamId = callback.ClientSteamID ?? default;
            _loggedOn.TrySetResult(callback.Result);
        });
        _callbacks.Subscribe<SteamFriends.PersonaStateCallback>(callback =>
        {
            if (!string.IsNullOrEmpty(callback.Name))
                PersonaName = callback.Name;
        });
    }

    public bool IsAnonymous => _credentials is null;

    /// <summary>登录后的 SteamID（匿名会话为 0）</summary>
    public SteamID SteamId { get; private set; }

    /// <summary>登录账号的昵称（来自 PersonaState 回调，可能为空）</summary>
    public string PersonaName { get; private set; } = string.Empty;

    private readonly Dictionary<uint, ulong> _appAccessTokens = new();
    private readonly HashSet<uint> _ownedApps = new();

    /// <summary>Terraria 的 app access token（旧接口兼容）；未验证时 0。</summary>
    public ulong AppAccessToken => AccessTokenFor(TerrariaAppId);

    public bool TerrariaOwnershipVerified => IsOwnershipVerified(TerrariaAppId);

    public ulong AccessTokenFor(uint appId) =>
        _appAccessTokens.TryGetValue(appId, out var token) ? token : 0UL;

    public bool IsOwnershipVerified(uint appId) => _ownedApps.Contains(appId);

    /// <summary>最近一次拥有校验的原始结果（诊断用）</summary>
    public EResult LastOwnershipResult { get; private set; } = EResult.Invalid;

    public SteamApps Apps => _apps;

    public SteamContent Content => _content;

    public SteamClient Client => _client;

    public SteamConfiguration Configuration => _client.Configuration;

    public async Task ConnectAsync(CancellationToken cancellationToken = default)
    {
        StartCallbacks();
        _client.Connect();
        await _connected.Task.WaitAsync(ConnectionTimeout, cancellationToken)
            .ConfigureAwait(false);
        var result = await _loggedOn.Task.WaitAsync(ConnectionTimeout, cancellationToken)
            .ConfigureAwait(false);
        if (result != EResult.OK)
            throw new SteamAuthenticationException(result);
    }

    public Task<bool> VerifyTerrariaOwnershipAsync(CancellationToken cancellationToken = default)
        => VerifyOwnershipAsync(TerrariaAppId, cancellationToken);

    /// <summary>
    /// 用 PICS 的 access token 判断账号是否拥有该 app。
    /// 免费 app（tModLoader）匿名也能拿到 token；付费 app 只有登录账号持有 license 才有。
    /// 真正的下载授权由 depot key 请求在服务端再判一次。
    /// </summary>
    public async Task<bool> VerifyOwnershipAsync(
        uint appId,
        CancellationToken cancellationToken = default)
    {
        // PICS 的 app access token：免费 app 匿名也有，付费 app 只有持有 license 才有；
        // PICS 查询（depots/分支）会用到，没有就按 0 传。
        var tokens = await _apps.PICSGetAccessTokens(appId, null)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        var accessToken = tokens.AppTokens.TryGetValue(appId, out var token) ? token : 0UL;
        _appAccessTokens[appId] = accessToken;

        // 拥有权以 ownership ticket 为准（免费/付费 app 通用；DepotDownloader 的 AccountHasAccess 同理）
        var ticket = await _apps.GetAppOwnershipTicket(appId)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        LastOwnershipResult = ticket.Result;
        if (ticket.Result == EResult.OK)
        {
            _ownedApps.Add(appId);
            return true;
        }

        // 免费 app（tModLoader）账号可能还没有 license，先申请一个再判定
        var freeLicense = await _apps.RequestFreeLicense(new[] { appId })
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        if (freeLicense.GrantedApps.Contains(appId))
        {
            _ownedApps.Add(appId);
            LastOwnershipResult = EResult.OK;
            return true;
        }

        return false;
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0)
            return;

        _running = false;
        try
        {
            _user.LogOff();
            _client.Disconnect();
        }
        catch (Exception)
        {
            // Disconnect is best effort during Android activity teardown.
        }

        _callbackThread?.Join(TimeSpan.FromSeconds(2));
    }

    private void StartCallbacks()
    {
        if (_callbackThread is { IsAlive: true })
            return;

        _running = true;
        _callbackThread = new Thread(() =>
        {
            while (_running)
                _callbacks.RunWaitCallbacks(TimeSpan.FromSeconds(1));
        })
        {
            IsBackground = true,
            Name = "TmlSteamSessionCallbacks",
        };
        _callbackThread.Start();
    }
}
