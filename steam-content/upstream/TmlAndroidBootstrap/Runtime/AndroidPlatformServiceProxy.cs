using System.Reflection;
using TmlAndroidBootstrap.Android;

namespace TmlAndroidBootstrap.Runtime;

/// <summary>
/// Adapts the bootstrap bridge to the Android platform service interface compiled
/// into tModLoader without making the upstream assembly reference this project.
/// </summary>
public class AndroidPlatformServiceProxy : DispatchProxy, IDisposable
{
    private IAndroidBridge? _bridge;
    private Action<char>? _imeListener;
    private Action<string>? _imeCompositionListener;
    private bool _imeDismissalReported;
    private readonly AndroidEventQueue _platformEvents = new();
    private readonly AndroidEventQueue _imeEvents = new();
    private CancellationTokenSource? _eventPumpCancellation;
    private Task? _eventPump;

    public static AndroidPlatformServiceProxy? TryConfigure(
        Assembly tModLoaderAssembly,
        IAndroidBridge bridge)
    {
        ArgumentNullException.ThrowIfNull(tModLoaderAssembly);
        ArgumentNullException.ThrowIfNull(bridge);

        var servicesType = tModLoaderAssembly.GetType("ReLogic.OS.AndroidPlatformServices")
            ?? Type.GetType("ReLogic.OS.AndroidPlatformServices, ReLogic", throwOnError: false);
        var interfaceType = servicesType?.Assembly.GetType("ReLogic.OS.IAndroidPlatformServices");
        var configure = servicesType?.GetMethod(
            "Configure",
            BindingFlags.Public | BindingFlags.Static,
            binder: null,
            new[] { interfaceType! },
            modifiers: null);
        if (servicesType is null || interfaceType is null || configure is null)
            return null;

        var proxy = (AndroidPlatformServiceProxy)DispatchProxy.Create(
            interfaceType,
            typeof(AndroidPlatformServiceProxy));
        proxy._bridge = bridge;
        configure.Invoke(null, new object?[] { proxy });
        proxy.StartEventPump(bridge);
        return proxy;
    }

    public void Dispose()
    {
        _eventPumpCancellation?.Cancel();
        try
        {
            _eventPump?.Wait(TimeSpan.FromSeconds(1));
        }
        catch (AggregateException)
        {
            // The game is already tearing down; the event pump is best effort.
        }
        _eventPumpCancellation?.Dispose();
        _eventPumpCancellation = null;
        _eventPump = null;
        _imeListener = null;
        _imeCompositionListener = null;
        _bridge = null;
    }

    protected override object? Invoke(MethodInfo? targetMethod, object?[]? args)
    {
        if (targetMethod is null)
            throw new MissingMethodException("Android platform service method was not resolved.");

        var bridge = _bridge ?? throw new ObjectDisposedException(nameof(AndroidPlatformServiceProxy));
        var name = targetMethod.Name;
        if (targetMethod.IsSpecialName && name == "get_FilesDirectory")
            return bridge.FilesDirectory;
        if (targetMethod.IsSpecialName && name == "get_CacheDirectory")
            return bridge.CacheDirectory;

        return name switch
        {
            "OpenUrl" => bridge.OpenUrl((string)args![0]!),
            "GetClipboardText" => bridge.GetClipboardText() ?? string.Empty,
            "SetClipboardText" => SetClipboard(bridge, (string)args![0]!),
            "SetOrientation" => SetOrientation(bridge, (bool)args![0]! ),
            "TryGetDisplaySize" => TryGetDisplaySize(bridge, args!),
            "SetFullscreen" => SetFullscreen(bridge, (bool)args![0]! ),
            "SetImeVisible" => SetImeVisible(bridge, (bool)args![0]! ),
            "AddImeTextListener" => AddImeTextListener((Action<char>)args![0]!),
            "RemoveImeTextListener" => RemoveImeTextListener((Action<char>)args![0]!),
            "AddImeCompositionListener" => AddImeCompositionListener((Action<string>)args![0]!),
            "RemoveImeCompositionListener" => RemoveImeCompositionListener((Action<string>)args![0]!),
            "PumpImeEvents" => PumpImeEvents(),
            "ShowToast" => ShowToast(bridge, (string)args![0]!, args.Length > 1 && args[1] is bool longDuration && longDuration),
            "HasPermission" => bridge.HasPermission((string)args![0]!),
            "RequestPermission" => RequestPermission(bridge, (string)args![0]!, (int)args[1]!),
            "TryDequeueEvent" => TryDequeuePlatformEvent(args!, targetMethod.GetParameters()[0].ParameterType.GetElementType()!),
            _ => throw new NotSupportedException($"Unsupported Android platform service: {name}")
        };
    }

    private static object? SetClipboard(IAndroidBridge bridge, string text)
    {
        bridge.SetClipboardText(text);
        return null;
    }

    private static object? SetOrientation(IAndroidBridge bridge, bool landscape)
    {
        bridge.SetOrientation(landscape);
        return null;
    }

    private static bool TryGetDisplaySize(IAndroidBridge bridge, object?[] args)
    {
        if (bridge.TryGetDisplaySize(out var width, out var height))
        {
            args[0] = width;
            args[1] = height;
            return true;
        }

        args[0] = 0;
        args[1] = 0;
        return false;
    }

    private static object? SetFullscreen(IAndroidBridge bridge, bool fullscreen)
    {
        bridge.SetFullscreen(fullscreen);
        return null;
    }

    private object? SetImeVisible(IAndroidBridge bridge, bool visible)
    {
        bridge.SetImeVisible(visible);
        if (visible)
            StartEventPump(bridge);
        return null;
    }

    private object? AddImeTextListener(Action<char> listener)
    {
        _imeListener += listener;
        if (_bridge is not null)
            StartEventPump(_bridge);
        return null;
    }

    private object? RemoveImeTextListener(Action<char> listener)
    {
        _imeListener -= listener;
        return null;
    }

    private object? AddImeCompositionListener(Action<string> listener)
    {
        _imeCompositionListener += listener;
        if (_bridge is not null)
            StartEventPump(_bridge);
        return null;
    }

    private object? RemoveImeCompositionListener(Action<string> listener)
    {
        _imeCompositionListener -= listener;
        return null;
    }

    private bool PumpImeEvents()
    {
        var dismissedByUser = false;
        while (_imeEvents.TryDequeue(out var inputEvent))
        {
            switch (inputEvent.Kind)
            {
                case AndroidInputEventKind.ImeCommit:
                    foreach (var character in inputEvent.Text ?? string.Empty)
                        _imeListener?.Invoke(character);
                    _imeCompositionListener?.Invoke(string.Empty);
                    break;
                case AndroidInputEventKind.ImeComposition:
                    _imeCompositionListener?.Invoke(inputEvent.Text ?? string.Empty);
                    break;
                case AndroidInputEventKind.ImeVisibility:
                    if (inputEvent.Focused)
                        _imeDismissalReported = false;
                    else if (inputEvent.Action != 0 && !_imeDismissalReported)
                    {
                        _imeDismissalReported = true;
                        dismissedByUser = true;
                    }
                    break;
            }
        }

        return dismissedByUser;
    }

    private static object? ShowToast(IAndroidBridge bridge, string message, bool longDuration)
    {
        bridge.ShowToast(message, longDuration);
        return null;
    }

    private static object? RequestPermission(IAndroidBridge bridge, string permission, int requestCode)
    {
        bridge.RequestPermission(permission, requestCode);
        return null;
    }

    private bool TryDequeuePlatformEvent(object?[] args, Type eventType)
    {
        if (!_platformEvents.TryDequeue(out var inputEvent))
        {
            args[0] = Activator.CreateInstance(eventType);
            return false;
        }

        args[0] = Activator.CreateInstance(eventType, new object?[]
        {
            (int)inputEvent.Kind,
            inputEvent.TimestampNanos,
            inputEvent.Text,
            inputEvent.KeyCode,
            inputEvent.Pressed,
            inputEvent.PointerId,
            inputEvent.X,
            inputEvent.Y,
            inputEvent.Action,
            inputEvent.Focused,
            inputEvent.Paused,
            inputEvent.RequestCode,
            inputEvent.Granted
        });
        return true;
    }

    private void StartEventPump(IAndroidBridge bridge)
    {
        if (_eventPump is { IsCompleted: false })
            return;

        _eventPumpCancellation?.Dispose();
        _eventPumpCancellation = new CancellationTokenSource();
        var cancellationToken = _eventPumpCancellation.Token;
        _eventPump = Task.Run(async () =>
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                bridge.PumpEvents();
                var events = bridge.Events;
                while (events.TryDequeue(out var inputEvent))
                {
                    if (inputEvent.Kind is AndroidInputEventKind.ImeCommit
                        or AndroidInputEventKind.ImeComposition
                        or AndroidInputEventKind.ImeVisibility)
                    {
                        _imeEvents.Enqueue(inputEvent);
                        continue;
                    }

                    _platformEvents.Enqueue(inputEvent);
                    if (inputEvent.Kind == AndroidInputEventKind.Text)
                        _imeEvents.Enqueue(inputEvent with { Kind = AndroidInputEventKind.ImeCommit });
                }

                await Task.Delay(TimeSpan.FromMilliseconds(8), cancellationToken)
                    .ConfigureAwait(false);
            }
        }, cancellationToken);
    }
}
