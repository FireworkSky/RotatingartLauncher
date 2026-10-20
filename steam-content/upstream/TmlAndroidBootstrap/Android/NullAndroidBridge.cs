namespace TmlAndroidBootstrap.Android;

public sealed class NullAndroidBridge : IAndroidBridge
{
    public NullAndroidBridge(string filesDirectory, string cacheDirectory, AndroidEventQueue? events = null)
    {
        FilesDirectory = Path.GetFullPath(filesDirectory);
        CacheDirectory = Path.GetFullPath(cacheDirectory);
        Events = events ?? new AndroidEventQueue();
    }

    public string FilesDirectory { get; }

    public string CacheDirectory { get; }

    public AndroidEventQueue Events { get; }

    public void RunOnUiThread(Action action) => action();

    public bool OpenUrl(string url) => false;

    public string? GetClipboardText() => null;

    public void SetClipboardText(string text)
    {
    }

    public void SetOrientation(bool landscape)
    {
    }

    public bool TryGetDisplaySize(out int width, out int height)
    {
        width = 0;
        height = 0;
        return false;
    }

    public void SetFullscreen(bool fullscreen)
    {
    }

    public void SetImeVisible(bool visible)
    {
    }

    public void ShowToast(string message, bool longDuration = false)
    {
    }

    public void SetStartupStatus(string title, string detail, int progressPercent = -1)
    {
    }

    public void HideStartupStatus()
    {
    }

    public bool HasPermission(string permission) => false;

    public void RequestPermission(string permission, int requestCode)
    {
    }

    public ValueTask<string?> PromptTextAsync(
        string title,
        bool password,
        CancellationToken cancellationToken = default) =>
        ValueTask.FromResult<string?>(null);

    // The non-Android fallback must not block launcher orchestration.
    public ValueTask<AndroidLauncherAction> WaitForLauncherActionAsync(
        CancellationToken cancellationToken = default) =>
        ValueTask.FromResult(AndroidLauncherAction.Cancel);

    public void PumpEvents()
    {
    }
}
