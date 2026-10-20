namespace TmlAndroidBootstrap.Android;

public interface IAndroidBridge
{
    string FilesDirectory { get; }

    string CacheDirectory { get; }

    void RunOnUiThread(Action action);

    bool OpenUrl(string url);

    string? GetClipboardText();

    void SetClipboardText(string text);

    void SetOrientation(bool landscape);

    bool TryGetDisplaySize(out int width, out int height);

    void SetFullscreen(bool fullscreen);

    void SetImeVisible(bool visible);

    void ShowToast(string message, bool longDuration = false);

    void SetStartupStatus(string title, string detail, int progressPercent = -1)
    {
    }

    void HideStartupStatus()
    {
    }

    bool HasPermission(string permission);

    void RequestPermission(string permission, int requestCode);

    ValueTask<string?> PromptTextAsync(
        string title,
        bool password,
        CancellationToken cancellationToken = default);

    ValueTask<AndroidLauncherAction> WaitForLauncherActionAsync(
        CancellationToken cancellationToken = default) =>
        ValueTask.FromResult(AndroidLauncherAction.Cancel);

    void PumpEvents();

    AndroidEventQueue Events { get; }
}
