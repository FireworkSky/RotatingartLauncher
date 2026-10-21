using SteamKit2;
using SteamKit2.Authentication;
using System.Net;
using System.Net.Http;

namespace TmlAndroidContent.Steam;

public sealed record SteamLoginResult(SteamCredentials Credentials);

public sealed class SteamLoginService : IDisposable
{
    private static readonly TimeSpan ConnectionTimeout = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan AuthenticationTimeout = TimeSpan.FromMinutes(2);
    private readonly SteamClient _client;
    private readonly CallbackManager _callbacks;
    private readonly SteamUser _user;
    private readonly TaskCompletionSource _connected =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly object _reconnectLock = new();
    private TaskCompletionSource _reconnected =
        new(TaskCreationOptions.RunContinuationsAsynchronously);
    private volatile bool _needsReconnectForAuth;
    private Thread? _callbackThread;
    private volatile bool _running;
    private int _disposed;

    public SteamLoginService(Uri? proxyUri = null)
    {
        // 内置 CM 列表 + TCP + 禁用目录 WebAPI，代理仅在显式传入时启用
        var configuration = SteamNetworking.Create(proxyUri);

        _client = new SteamClient(configuration);
        _callbacks = new CallbackManager(_client);
        _user = _client.GetHandler<SteamUser>()!;
        _callbacks.Subscribe<SteamClient.ConnectedCallback>(_ => _connected.TrySetResult());
        _callbacks.Subscribe<SteamClient.ConnectedCallback>(_ => _reconnected.TrySetResult());
        _callbacks.Subscribe<SteamClient.DisconnectedCallback>(callback =>
        {
            if (!callback.UserInitiated)
                _needsReconnectForAuth = true;
        });
    }

    public async Task<SteamLoginResult> LoginAsync(
        string accountName,
        string password,
        string? guardData,
        ISteamGuardCodeProvider guardCodeProvider,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(accountName);
        ArgumentException.ThrowIfNullOrWhiteSpace(password);
        ArgumentNullException.ThrowIfNull(guardCodeProvider);

        StartCallbacks();
        _client.Connect();
        await _connected.Task.WaitAsync(ConnectionTimeout, cancellationToken)
            .ConfigureAwait(false);

        var authSession = await _client.Authentication.BeginAuthSessionViaCredentialsAsync(
            new AuthSessionDetails
            {
                Username = accountName,
                Password = password,
                GuardData = guardData,
                IsPersistentSession = true,
                Authenticator = new GuardAuthenticator(guardCodeProvider, cancellationToken, this),
            }).WaitAsync(AuthenticationTimeout, cancellationToken).ConfigureAwait(false);

        var response = await authSession.PollingWaitForResultAsync()
            .WaitAsync(AuthenticationTimeout, cancellationToken).ConfigureAwait(false);

        return new SteamLoginResult(new SteamCredentials(
            response.AccountName,
            response.RefreshToken,
            response.NewGuardData ?? guardData));
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0)
            return;

        _running = false;
        try
        {
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
            Name = "TmlSteamCallbacks",
        };
        _callbackThread.Start();
    }

    private sealed class GuardAuthenticator(
        ISteamGuardCodeProvider provider,
        CancellationToken cancellationToken,
        SteamLoginService login) : IAuthenticator
    {
        public async Task<string> GetDeviceCodeAsync(bool previousCodeWasIncorrect)
        {
            var code = await provider.GetCodeAsync(previousCodeWasIncorrect, cancellationToken)
                .ConfigureAwait(false);
            await login.ReconnectForAuthAsync(cancellationToken).ConfigureAwait(false);
            return code;
        }

        public async Task<string> GetEmailCodeAsync(string email, bool previousCodeWasIncorrect)
        {
            var code = await provider.GetCodeAsync(previousCodeWasIncorrect, cancellationToken)
                .ConfigureAwait(false);
            await login.ReconnectForAuthAsync(cancellationToken).ConfigureAwait(false);
            return code;
        }

        public Task<bool> AcceptDeviceConfirmationAsync() => Task.FromResult(true);
    }

    private async Task ReconnectForAuthAsync(CancellationToken cancellationToken)
    {
        if (!_needsReconnectForAuth)
            return;

        TaskCompletionSource reconnect;
        lock (_reconnectLock)
        {
            if (!_needsReconnectForAuth)
                return;

            reconnect = _reconnected = new(TaskCreationOptions.RunContinuationsAsynchronously);
            _needsReconnectForAuth = false;
            _client.Connect();
        }

        await reconnect.Task.WaitAsync(ConnectionTimeout, cancellationToken)
            .ConfigureAwait(false);
    }
}
