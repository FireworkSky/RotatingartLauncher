using TmlAndroidBootstrap;
using TmlAndroidBootstrap.Android;

using TmlAndroidHost;

var workingDirectory = Environment.GetEnvironmentVariable("TML_ANDROID_GAME_ROOT")
    ?? Directory.GetCurrentDirectory();
var cacheDirectory = Environment.GetEnvironmentVariable("TML_ANDROID_CACHE")
    ?? Path.Combine(workingDirectory, "cache");
var assemblyPath = Environment.GetEnvironmentVariable("TML_TMODLOADER_ASSEMBLY")
    ?? Path.Combine(workingDirectory, "tModLoader.dll");
var contentDirectory = Environment.GetEnvironmentVariable("TML_ANDROID_CONTENT")
    ?? Path.Combine(workingDirectory, "Content");

Directory.CreateDirectory(workingDirectory);
Directory.CreateDirectory(cacheDirectory);
RuntimeLogging.Initialize(workingDirectory);
Environment.SetEnvironmentVariable("TML_ANDROID_FILES", workingDirectory);
Environment.SetEnvironmentVariable("TML_ANDROID_CACHE", cacheDirectory);
Environment.SetEnvironmentVariable("TML_ANDROID_CONTENT", contentDirectory);

IAndroidBridge bridge = OperatingSystem.IsAndroid()
    ? new JniAndroidBridge(workingDirectory, cacheDirectory)
    : new NullAndroidBridge(workingDirectory, cacheDirectory);

try
{
    if (OperatingSystem.IsAndroid())
    {
        var shouldLaunch = await RunAndroidLauncherAsync(bridge, workingDirectory);
        if (!shouldLaunch)
            return 0;
    }

    if (OperatingSystem.IsAndroid())
    {
        bridge.SetStartupStatus("Launching tModLoader", "Starting the game renderer...", -1);
        bridge.HideStartupStatus();
    }

    return new TmlAndroidBootstrap.TmlAndroidBootstrap(bridge)
        .LaunchManagedGame(assemblyPath, args);
}
catch (Exception exception)
{
    Console.Error.WriteLine($"Android tModLoader startup failed: {exception}");
    try
    {
        bridge.SetStartupStatus("Startup failed", exception.ToString(), 0);
        bridge.ShowToast(
            $"tModLoader startup failed: {exception.Message}",
            longDuration: true);
    }
    catch (Exception bridgeException)
    {
        Console.Error.WriteLine($"Could not show Android error message: {bridgeException}");
    }

    return 1;
}

static async Task<bool> RunAndroidLauncherAsync(
    IAndroidBridge bridge,
    string workingDirectory)
{
    using var flowCancellation = new CancellationTokenSource();
    var operation = new LauncherOperationControl();
    var router = new LauncherActionRouter(bridge, operation, flowCancellation);
    var actionPump = router.PumpAsync(flowCancellation.Token);

    try
    {
        while (true)
        {
            try
            {
                operation.Resume();
                bridge.SetStartupStatus(
                    "Preparing tModLoader",
                    "Checking Terraria Content...",
                    -1);
                await SteamContentPreparation.EnsureTerrariaContentAsync(
                        bridge,
                        workingDirectory,
                        flowCancellation.Token,
                        operation.WaitIfPausedAsync)
                    .ConfigureAwait(false);

                bridge.SetStartupStatus(
                    "Ready",
                    "Terraria Content is installed and ready to launch.",
                    100);

                while (true)
                {
                    var action = await router.ReadAsync(flowCancellation.Token)
                        .ConfigureAwait(false);
                    if (action == AndroidLauncherAction.Launch)
                        return true;
                    if (action == AndroidLauncherAction.Cancel)
                        return false;
                    if (action is AndroidLauncherAction.Recheck or AndroidLauncherAction.Retry)
                        break;
                }
            }
            catch (OperationCanceledException) when (flowCancellation.IsCancellationRequested)
            {
                return false;
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine($"Content preparation failed: {exception}");
                bridge.SetStartupStatus(
                    "Content preparation failed",
                    exception.Message,
                    0);

                while (true)
                {
                    var action = await router.ReadAsync(flowCancellation.Token)
                        .ConfigureAwait(false);
                    if (action is AndroidLauncherAction.Retry or AndroidLauncherAction.Recheck)
                        break;
                    if (action == AndroidLauncherAction.Cancel)
                        return false;
                }
            }
        }
    }
    finally
    {
        flowCancellation.Cancel();
        operation.Resume();
        try
        {
            await actionPump.ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
        }
    }
}
