using TmlAndroidBootstrap.Android;
using TmlAndroidContent.Steam;
using System.Diagnostics;

namespace TmlAndroidHost;

internal static class SteamContentPreparation
{
    public static async Task EnsureTerrariaContentAsync(
        IAndroidBridge bridge,
        string gameDirectory,
        CancellationToken cancellationToken = default,
        Func<CancellationToken, ValueTask>? waitForResume = null)
    {
        var contentDirectory = Path.Combine(gameDirectory, "Content");
        if (HasContent(gameDirectory, contentDirectory))
            return;

        bridge.SetStartupStatus("Steam", "Checking saved login...", -1);
        var store = new AndroidKeystoreSteamCredentialStore();
        var credentials = await store.LoadAsync(cancellationToken).ConfigureAwait(false);
        if (credentials is null)
            credentials = await LoginAsync(bridge, store, cancellationToken).ConfigureAwait(false);

        try
        {
            bridge.SetStartupStatus("Steam", "Verifying Terraria ownership...", -1);
            await DownloadOwnedContentAsync(
                    bridge,
                    credentials,
                    gameDirectory,
                    cancellationToken,
                    waitForResume)
                .ConfigureAwait(false);
        }
        catch (SteamAuthenticationException exception)
        {
            bridge.SetStartupStatus(
                "Steam",
                $"Saved Steam session rejected: {exception.ResultName}. Sign in again...",
                -1);
            await store.ClearAsync(cancellationToken).ConfigureAwait(false);
            credentials = await LoginAsync(bridge, store, cancellationToken).ConfigureAwait(false);
            await DownloadOwnedContentAsync(
                    bridge,
                    credentials,
                    gameDirectory,
                    cancellationToken,
                    waitForResume)
                .ConfigureAwait(false);
        }
    }

    private static async Task<SteamCredentials> LoginAsync(
        IAndroidBridge bridge,
        ISteamCredentialStore store,
        CancellationToken cancellationToken)
    {
        bridge.SetStartupStatus("Steam login", "Waiting for account name...", -1);
        var account = await bridge.PromptTextAsync(
            "Steam account", false, cancellationToken).ConfigureAwait(false);
        bridge.SetStartupStatus("Steam login", "Waiting for password...", -1);
        var password = await bridge.PromptTextAsync(
            "Steam password", true, cancellationToken).ConfigureAwait(false);
        if (string.IsNullOrWhiteSpace(account) || string.IsNullOrWhiteSpace(password))
            throw new InvalidOperationException("Steam login was cancelled.");

        try
        {
            bridge.SetStartupStatus("Steam login", "Connecting to Steam...", -1);
            using var login = new SteamLoginService();
            var result = await login.LoginAsync(
                account,
                password,
                guardData: null,
                new AndroidSteamGuardCodeProvider(bridge),
                cancellationToken).ConfigureAwait(false);
            await store.SaveAsync(result.Credentials, cancellationToken).ConfigureAwait(false);
            bridge.SetStartupStatus("Steam login", "Login successful.", 100);
            return result.Credentials;
        }
        finally
        {
            password = string.Empty;
        }
    }

    private static async Task DownloadOwnedContentAsync(
        IAndroidBridge bridge,
        SteamCredentials credentials,
        string gameDirectory,
        CancellationToken cancellationToken,
        Func<CancellationToken, ValueTask>? waitForResume)
    {
        using var session = new SteamSession(credentials);
        bridge.SetStartupStatus("Steam", "Connecting to Steam services...", -1);
        await session.ConnectAsync(cancellationToken).ConfigureAwait(false);
        bridge.SetStartupStatus("Steam", "Checking Terraria ownership...", -1);
        if (!await session.VerifyTerrariaOwnershipAsync(cancellationToken).ConfigureAwait(false))
            throw new InvalidOperationException("The signed-in Steam account does not own Terraria (105600).");

        var progressGate = new object();
        var lastProgressTimestamp = 0L;
        void ReportProgress(SteamDownloadProgress progress)
        {
            var now = Stopwatch.GetTimestamp();
            lock (progressGate)
            {
                var complete = progress.TotalFiles > 0 &&
                    progress.CompletedFiles >= progress.TotalFiles;
                if (!complete &&
                    lastProgressTimestamp != 0 &&
                    now - lastProgressTimestamp < Stopwatch.Frequency / 4)
                {
                    return;
                }

                lastProgressTimestamp = now;
            }

            var percent = progress.TotalBytes == 0
                ? -1
                : Math.Clamp((int)Math.Round(progress.Percentage), 0, 100);
            var currentFile = string.IsNullOrWhiteSpace(progress.CurrentFile)
                ? "Preparing files..."
                : Path.GetFileName(progress.CurrentFile);
            var detail = progress.TotalFiles == 0
                ? "Reading Steam content manifest..."
                : $"{percent}%  {progress.CompletedFiles}/{progress.TotalFiles} files  " +
                    $"{FormatBytes(progress.DownloadedBytes)}/{FormatBytes(progress.TotalBytes)}  " +
                    currentFile;
            bridge.SetStartupStatus("Downloading Terraria Content", detail, percent);
        }

        await new SteamTerrariaContentDownloader(
                ReportProgress,
                status => bridge.SetStartupStatus("Terraria Content", status, -1))
            .DownloadAsync(session, gameDirectory, cancellationToken, waitForResume)
            .ConfigureAwait(false);
        bridge.SetStartupStatus("Terraria Content", "Download complete.", 100);
    }

    private static bool HasContent(string gameDirectory, string contentDirectory) =>
        File.Exists(Path.Combine(gameDirectory, SteamTerrariaContentDownloader.CompletionMarker)) &&
        Directory.Exists(contentDirectory) &&
        File.Exists(Path.Combine(contentDirectory, "Images", "ChestStack_0.xnb")) &&
        File.Exists(Path.Combine(contentDirectory, "Images", "Projectile_981.xnb"));

    private static string FormatBytes(long bytes)
    {
        const long megabyte = 1024 * 1024;
        return bytes >= megabyte
            ? $"{bytes / (double)megabyte:0.0} MB"
            : $"{bytes / 1024d:0} KB";
    }

    private sealed class AndroidSteamGuardCodeProvider(IAndroidBridge bridge) : ISteamGuardCodeProvider
    {
        public async Task<string> GetCodeAsync(
            bool previousCodeWasIncorrect,
            CancellationToken cancellationToken)
        {
            var title = previousCodeWasIncorrect
                ? "Steam Guard code (try again)"
                : "Steam Guard code";
            bridge.SetStartupStatus(
                "Steam login",
                previousCodeWasIncorrect
                    ? "Steam Guard code was rejected. Waiting for a new code..."
                    : "Waiting for Steam Guard code...",
                -1);
            var code = await bridge.PromptTextAsync(title, false, cancellationToken)
                .ConfigureAwait(false);
            if (string.IsNullOrWhiteSpace(code))
                throw new InvalidOperationException("Steam Guard input was cancelled.");
            return code.Trim();
        }
    }
}
