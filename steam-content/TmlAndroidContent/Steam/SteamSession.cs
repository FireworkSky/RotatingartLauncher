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

        var configuration = SteamConfiguration.Create(builder =>
            builder.WithProtocolTypes(ProtocolTypes.All)
                .WithConnectionTimeout(ConnectionTimeout)
                .WithHttpClientFactory(_ => new HttpClient
                {
                    Timeout = TimeSpan.FromSeconds(20),
                }));
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

    public ulong AppAccessToken { get; private set; }

    public bool TerrariaOwnershipVerified { get; private set; }

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

    public async Task<bool> VerifyTerrariaOwnershipAsync(
        CancellationToken cancellationToken = default)
    {
        var result = await _apps.PICSGetAccessTokens(TerrariaAppId, null)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        if (!result.AppTokens.TryGetValue(TerrariaAppId, out var token))
            return false;

        AppAccessToken = token;
        TerrariaOwnershipVerified = true;
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
