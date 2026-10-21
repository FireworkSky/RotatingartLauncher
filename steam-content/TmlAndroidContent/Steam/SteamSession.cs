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

    private readonly SteamClient _client;
    private readonly CallbackManager _callbacks;
    private readonly SteamUser _user;
    private readonly SteamApps _apps;
    private readonly SteamContent _content;
    private readonly TaskCompletionSource _connected =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly TaskCompletionSource<EResult> _loggedOn =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly SteamCredentials _credentials;
    private Thread? _callbackThread;
    private volatile bool _running;
    private int _disposed;

    public SteamSession(SteamCredentials credentials)
    {
        ArgumentNullException.ThrowIfNull(credentials);
        _credentials = credentials;

        var configuration = SteamNetworking.Create();
        _client = new SteamClient(configuration);
        _callbacks = new CallbackManager(_client);
        _user = _client.GetHandler<SteamUser>()!;
        _apps = _client.GetHandler<SteamApps>()!;
        _content = _client.GetHandler<SteamContent>()!;

        _callbacks.Subscribe<SteamClient.ConnectedCallback>(_ =>
        {
            _connected.TrySetResult();
            _user.LogOn(new SteamUser.LogOnDetails
            {
                Username = _credentials.AccountName,
                AccessToken = _credentials.RefreshToken,
                ShouldRememberPassword = true,
            });
        });
        _callbacks.Subscribe<SteamUser.LoggedOnCallback>(callback =>
            _loggedOn.TrySetResult(callback.Result));
    }

    private readonly Dictionary<uint, ulong> _appAccessTokens = new();

    /// <summary>Terraria 的 app access token（旧接口兼容）；未验证时 0。</summary>
    public ulong AppAccessToken => AccessTokenFor(TerrariaAppId);

    public bool TerrariaOwnershipVerified => IsOwnershipVerified(TerrariaAppId);

    public ulong AccessTokenFor(uint appId) =>
        _appAccessTokens.TryGetValue(appId, out var token) ? token : 0UL;

    public bool IsOwnershipVerified(uint appId) => _appAccessTokens.ContainsKey(appId);

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
        var result = await _apps.PICSGetAccessTokens(appId, null)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        if (!result.AppTokens.TryGetValue(appId, out var token) || token == 0)
            return false;

        _appAccessTokens[appId] = token;
        return true;
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
