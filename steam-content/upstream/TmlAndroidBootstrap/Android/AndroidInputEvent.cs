namespace TmlAndroidBootstrap.Android;

public enum AndroidInputEventKind
{
    Lifecycle,
    Text,
    Key,
    Touch,
    Focus,
    Back,
    Permission,
    LauncherAction,
    ImeCommit,
    ImeComposition,
    ImeVisibility
}

public readonly record struct AndroidInputEvent(
    AndroidInputEventKind Kind,
    long TimestampNanos = 0,
    string? Text = null,
    int KeyCode = 0,
    bool Pressed = false,
    int PointerId = 0,
    float X = 0,
    float Y = 0,
    int Action = 0,
    bool Focused = false,
    bool Paused = false,
    int RequestCode = 0,
    bool Granted = false);
