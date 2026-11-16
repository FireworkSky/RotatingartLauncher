using System;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Input;
using Microsoft.Xna.Framework.Input.Touch;
using Microsoft.Xna.Framework.Graphics;
using Terraria;
using Terraria.GameInput;

namespace TModLoaderTouchPatch;

/// <summary>
/// Feeds FNA's native touch state into Terraria's existing cursor input path.
/// SDL touch-to-mouse synthesis stays disabled, so this is the only touch input
/// adapter for tModLoader.
/// </summary>
internal static class TouchInputPatch
{
    private const int NO_FINGER = -1;

    private static int _primaryFingerId = NO_FINGER;
    private static Vector2 _lastPosition;
    private static bool _touchPressed;
    private static bool _touchInputActive;
    private static long _activeTouchDevice;
    private static TouchLocation[] _touches = Array.Empty<TouchLocation>();
    private static TouchLocation[] _previousTouches = Array.Empty<TouchLocation>();
    private static readonly FieldInfo _touchDeviceExists = typeof(TouchPanel).GetField(
        "TouchDeviceExists", BindingFlags.Static | BindingFlags.NonPublic)
        ?? throw new MissingFieldException("FNA TouchPanel.TouchDeviceExists not found");
    private static long _pollCount, _mouseInputCalls;
    private static long _nextDiagnostic, _nextError;
    private static int _lastDeviceCount = -1;
    private static bool _reportedFingers;
    private static bool _lastDiagnosticPressed;
    private static bool _lastFinalPressed;
    private static long _pressedSamples, _releasedSamples, _finalPressedSamples;

    public static void Apply(Harmony harmony, Assembly tModLoaderAssembly)
    {
        try
        {
            var platform = typeof(TouchPanel).Assembly.GetType("Microsoft.Xna.Framework.SDL2_FNAPlatform");
            var updateTouchPanel = platform?.GetMethod("UpdateTouchPanelState", BindingFlags.Static | BindingFlags.Public);
            if (updateTouchPanel == null)
                throw new MissingMethodException("FNA SDL2 touch polling entry not found");
            _touches = (TouchLocation[]?)AccessTools.Field(typeof(TouchPanel), "touches")?.GetValue(null)
                ?? throw new MissingFieldException("FNA TouchPanel touch storage not found");
            _previousTouches = new TouchLocation[_touches.Length];
            harmony.Patch(updateTouchPanel, prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(UpdateTouchPanelStatePrefix)));
            harmony.Patch(
                AccessTools.Method(typeof(FrameworkDispatcher), nameof(FrameworkDispatcher.Update)),
                prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(FrameworkDispatcherUpdatePrefix))
            );

            var cursorVisibilityChanged = platform!.GetMethod(
                "OnIsMouseVisibleChanged",
                BindingFlags.Static | BindingFlags.Public,
                null,
                new[] { typeof(bool) },
                null
            );
            if (cursorVisibilityChanged == null)
                throw new MissingMethodException("FNA mouse cursor visibility callback not found");
            harmony.Patch(
                cursorVisibilityChanged,
                prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(HideNativeCursorPrefix))
            );
            var playerInputType = tModLoaderAssembly.GetType("Terraria.GameInput.PlayerInput");
            var mouseInput = playerInputType?.GetMethod(
                "MouseInput",
                BindingFlags.Static | BindingFlags.NonPublic
            );

            if (mouseInput == null)
            {
                throw new MissingMethodException("PlayerInput.MouseInput not found");
            }

            harmony.Patch(
                mouseInput,
                prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(PlayerInputMouseInputPrefix))
            );

            // Optional observation only: failure here must not uninstall the working input adapter.
            try
            {
                harmony.Patch(AccessTools.Method(typeof(PlayerInput), "UpdateInput"),
                    postfix: new HarmonyMethod(typeof(TouchInputPatch), nameof(FinalInputDiagnosticPostfix)));
                Patcher.Log("Final PlayerInput.UpdateInput diagnostic hook installed.");
            }
            catch (Exception diagnosticError)
            {
                Patcher.Log($"Final input diagnostic unavailable: {diagnosticError}");
            }

            var mouseGetState = typeof(Mouse).GetMethod(
                nameof(Mouse.GetState),
                BindingFlags.Static | BindingFlags.Public,
                null,
                Type.EmptyTypes,
                null
            );

            if (mouseGetState != null)
            {
                harmony.Patch(
                    mouseGetState,
                    postfix: new HarmonyMethod(typeof(TouchInputPatch), nameof(MouseGetStatePostfix))
                );
            }

            var mainType = tModLoaderAssembly.GetType("Terraria.Main");
            var drawCursor = mainType?.GetMethod(
                "DrawCursor",
                BindingFlags.Static | BindingFlags.Public,
                null,
                new[] { typeof(Vector2), typeof(bool) },
                null
            );
            if (drawCursor == null)
                throw new MissingMethodException("Terraria.Main.DrawCursor not found");

            harmony.Patch(
                drawCursor,
                prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(HideGameCursorPrefix))
            );

            var utilsType = tModLoaderAssembly.GetType("Terraria.Utils");
            var drawCursorSingle = utilsType?.GetMethod(
                "DrawCursorSingle",
                BindingFlags.Static | BindingFlags.Public,
                null,
                new[] {
                    typeof(SpriteBatch),
                    typeof(Color),
                    typeof(float),
                    typeof(float),
                    typeof(Vector2),
                    typeof(int),
                    typeof(int)
                },
                null
            );
            if (drawCursorSingle != null)
            {
                harmony.Patch(
                    drawCursorSingle,
                    prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(SkipDirectCursorTextureDraw))
                );
                Console.WriteLine("[TModLoaderTouchPatch] Terraria direct cursor texture draw disabled.");
            }
            else
            {
                Console.WriteLine("[TModLoaderTouchPatch] Terraria.Utils.DrawCursorSingle not found; direct cursor texture may remain visible.");
            }

            var drawCursorInterface = mainType!.GetMethod(
                "DrawInterface_36_Cursor",
                BindingFlags.Static | BindingFlags.NonPublic
            );
            if (drawCursorInterface != null)
            {
                harmony.Patch(
                    drawCursorInterface,
                    prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(SkipGameCursorLayer))
                );
                Console.WriteLine("[TModLoaderTouchPatch] Terraria cursor UI layer disabled.");
            }
            else
            {
                Console.WriteLine("[TModLoaderTouchPatch] Main.DrawInterface_36_Cursor not found; cursor override may remain visible.");
            }

            Patcher.Log($"Adapter applied. FNA={typeof(TouchPanel).Assembly.FullName}; touch capacity={_touches.Length}. Waiting for runtime polling and input diagnostics.");
            Patcher.StartHeartbeat();
        }
        catch (Exception ex)
        {
            Patcher.Log($"Native touch adapter failed: {ex}");
            harmony.UnpatchAll(harmony.Id);
            throw;
        }
    }

    private static bool UpdateTouchPanelStatePrefix()
    {
        try
        {
            PollTouchState();
        }
        catch (Exception ex)
        {
            Array.Clear(_touches, 0, _touches.Length);
            ResetTouchState();
            ReportError("Native touch state polling failed", ex);
        }
        return false;
    }

    // FNA otherwise skips TouchPanel.Update until a device is detected at game
    // creation or a FINGERDOWN is dispatched. Keep its normal update path (and
    // mod hooks) running, including devices first discovered after startup.
    public static void FrameworkDispatcherUpdatePrefix()
    {
        _touchDeviceExists.SetValue(null, true);
    }

    private static void PollTouchState()
    {
        _pollCount++;
        Array.Copy(_touches, _previousTouches, _touches.Length);
        Array.Clear(_touches, 0, _touches.Length);
        long device = GetActiveTouchDevice();
        int count = device == 0 ? 0 : Math.Max(0, Math.Min(SDL2.SDL.SDL_GetNumTouchFingers(device), _touches.Length));
        ReportPolling(device, count);
        int written = 0;
        for (int i = 0; i < count; i++)
        {
            var pointer = SDL2.SDL.SDL_GetTouchFinger(device, i);
            if (pointer == IntPtr.Zero) continue;
            var finger = Marshal.PtrToStructure<SDL2.SDL.SDL_Finger>(pointer);
            int id = (int)finger.id;
            var position = new Vector2(
                (float)Math.Round(finger.x * TouchPanel.DisplayWidth),
                (float)Math.Round(finger.y * TouchPanel.DisplayHeight));
            var previous = default(TouchLocation);
            foreach (var touch in _previousTouches)
            {
                if (touch.Id == id && (touch.State == TouchLocationState.Pressed || touch.State == TouchLocationState.Moved))
                {
                    previous = touch;
                    break;
                }
            }
            // SDL compacts its finger list on release; only the ID is stable.
            _touches[written++] = previous.State == TouchLocationState.Invalid
                ? new TouchLocation(id, TouchLocationState.Pressed, position)
                : new TouchLocation(id, TouchLocationState.Moved, position, previous.State, previous.Position);
        }

        int activeCount = written;
        foreach (var previous in _previousTouches)
        {
            if (written == _touches.Length) break;
            if (previous.State != TouchLocationState.Pressed && previous.State != TouchLocationState.Moved) continue;
            bool stillDown = false;
            for (int i = 0; i < activeCount; i++)
            {
                if (_touches[i].Id == previous.Id)
                {
                    stillDown = true;
                    break;
                }
            }
            if (!stillDown)
                _touches[written++] = new TouchLocation(previous.Id, TouchLocationState.Released, previous.Position, previous.State, previous.Position);
        }
        bool edge = false;
        for (int i = 0; i < written; i++)
            edge |= _touches[i].State == TouchLocationState.Pressed || _touches[i].State == TouchLocationState.Released;
        Patcher.Trace("fna-touch", () =>
        {
            string contacts = "";
            for (int i = 0; i < written; i++)
                contacts += $" {_touches[i].Id}:{_touches[i].State}@{_touches[i].Position}";
            return $"polls={_pollCount}, device={device}, active={activeCount}, written={written}, " +
                $"display={TouchPanel.DisplayWidth}x{TouchPanel.DisplayHeight}, contacts=[{contacts.Trim()}]";
        }, edge);
    }

    private static long GetActiveTouchDevice()
    {
        if (_activeTouchDevice != 0 && SDL2.SDL.SDL_GetNumTouchFingers(_activeTouchDevice) > 0)
            return _activeTouchDevice;
        var deviceCount = SDL2.SDL.SDL_GetNumTouchDevices();
        for (int i = 0; i < deviceCount; i++)
        {
            long device = SDL2.SDL.SDL_GetTouchDevice(i);
            // SDL_MOUSE_TOUCHID (-1) represents mouse-to-touch synthesis.
            if (device != 0 && device != -1 && SDL2.SDL.SDL_GetNumTouchFingers(device) > 0)
            {
                _activeTouchDevice = device;
                return device;
            }
        }
        _activeTouchDevice = 0;
        return 0;
    }

    public static bool PlayerInputMouseInputPrefix()
    {
        try
        {
            _mouseInputCalls++;
            if (_mouseInputCalls == 1) Patcher.Log("Terraria input hook running.");
            _touchInputActive = TryReadTouch(out var position, out var pressed);
            SDL2.SDL.SDL_ShowCursor(0);
            ApplyTouchToPlayerInput(position, pressed);
            if (pressed) _pressedSamples++; else _releasedSamples++;
            bool edge = pressed != _lastDiagnosticPressed;
            _lastDiagnosticPressed = pressed;
            Patcher.Trace("adapter", () =>
                $"calls={_mouseInputCalls}, pressSamples={_pressedSamples}, releaseSamples={_releasedSamples}, " +
                $"selected={_primaryFingerId}, active={_touchInputActive}, pressed={pressed}, touch={position}, " +
                $"rawScale={PlayerInput.RawMouseScale}, mouse={PlayerInput.MouseInfo.X},{PlayerInput.MouseInfo.Y}, " +
                $"keys=[{string.Join(",", PlayerInput.MouseKeys)}], gameActive={Main.instance?.IsActive}, " +
                $"menu={Main.gameMenu}/{Main.menuMode}, screen={Main.screenWidth}x{Main.screenHeight}", edge);
            return false;
        }
        catch (Exception ex)
        {
            ResetTouchState();
            PlayerInput.MouseKeys.Clear();
            PlayerInput.MouseInfoOld = PlayerInput.MouseInfo;
            PlayerInput.MouseInfo = default;
            ReportError("Touch adapter failed; mouse fallback is disabled", ex);
            return false;
        }
    }

    public static void FinalInputDiagnosticPostfix()
    {
        bool pressed = Main.mouseLeft;
        if (pressed) _finalPressedSamples++;
        bool edge = pressed != _lastFinalPressed;
        _lastFinalPressed = pressed;
        Patcher.Trace("terraria-input", () =>
            $"left={Main.mouseLeft}, leftRelease={Main.mouseLeftRelease}, " +
            $"triggerLeft={PlayerInput.Triggers.Current.MouseLeft}, pressedSamples={_finalPressedSamples}, " +
            $"mouse={Main.mouseX},{Main.mouseY}, inputMode={PlayerInput.CurrentInputMode}, " +
            $"gameActive={Main.instance?.IsActive}, menu={Main.gameMenu}/{Main.menuMode}", edge);
    }

    public static void MouseGetStatePostfix(ref MouseState __result)
    {
        // Mod touch mode stays in control even on idle/error frames. SDL mouse
        // input is available only after explicitly selecting SDL touch mode.
        __result = new MouseState(
            (int)_lastPosition.X,
            (int)_lastPosition.Y,
            0,
            _touchPressed ? ButtonState.Pressed : ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released
        );
    }

    public static bool HideGameCursorPrefix()
    {
        SDL2.SDL.SDL_ShowCursor(0);
        return false;
    }

    public static bool SkipGameCursorLayer()
    {
        return false;
    }

    public static bool SkipDirectCursorTextureDraw()
    {
        return false;
    }

    public static bool HideNativeCursorPrefix()
    {
        SDL2.SDL.SDL_ShowCursor(0);
        return false;
    }

    private static bool TryReadTouch(out Vector2 position, out bool pressed)
    {
        var touches = TouchPanel.GetState();
        TouchLocation selected = default;
        var found = false;

        if (_primaryFingerId != NO_FINGER)
        {
            foreach (var touch in touches)
            {
                if (touch.Id == _primaryFingerId)
                {
                    selected = touch;
                    found = true;
                    break;
                }
            }
        }

        if (!found)
        {
            foreach (var touch in touches)
            {
                if (touch.State == TouchLocationState.Pressed || touch.State == TouchLocationState.Moved)
                {
                    selected = touch;
                    _primaryFingerId = touch.Id;
                    found = true;
                    break;
                }
            }
        }

        if (!found)
        {
            _primaryFingerId = NO_FINGER;
            _touchPressed = false;
            position = _lastPosition;
            pressed = false;
            return false;
        }

        position = selected.Position;
        pressed = selected.State != TouchLocationState.Released;
        _lastPosition = position;
        _touchPressed = pressed;

        if (!pressed)
        {
            _primaryFingerId = NO_FINGER;
        }

        return true;
    }

    private static void ApplyTouchToPlayerInput(Vector2 position, bool pressed)
    {
        var scaledPosition = ScaleTouchPosition(position);
        var scaledX = (int)scaledPosition.X;
        var scaledY = (int)scaledPosition.Y;
        var oldMouse = PlayerInput.MouseInfo;
        var mouseState = new MouseState(
            scaledX,
            scaledY,
            0,
            pressed ? ButtonState.Pressed : ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released,
            ButtonState.Released
        );

        PlayerInput.MouseInfoOld = oldMouse;
        PlayerInput.MouseInfo = mouseState;

        var changed = scaledX != oldMouse.X || scaledY != oldMouse.Y || mouseState.LeftButton != oldMouse.LeftButton;
        if (changed)
        {
            PlayerInput.MouseX = scaledX;
            PlayerInput.MouseY = scaledY;
            if (!PlayerInput.PreventFirstMousePositionGrab)
            {
                PlayerInput.SettingsForUI.SetCursorMode(CursorMode.Mouse);
            }

            PlayerInput.PreventFirstMousePositionGrab = false;
        }

        PlayerInput.MouseKeys.Clear();
        if (Main.instance != null && Main.instance.IsActive && pressed)
        {
            PlayerInput.MouseKeys.Add("Mouse1");
        }

        if (pressed || changed)
        {
            PlayerInput.CurrentInputMode = InputMode.Mouse;
        }
        PlayerInput.Triggers.Current.UsedMovementKey = false;
    }

    private static Vector2 ScaleTouchPosition(Vector2 position)
    {
        return new Vector2(
            position.X * PlayerInput.RawMouseScale.X,
            position.Y * PlayerInput.RawMouseScale.Y
        );
    }

    private static void ResetTouchState()
    {
        _primaryFingerId = NO_FINGER;
        _lastPosition = default;
        _touchPressed = false;
        _touchInputActive = false;
    }

    private static void ReportPolling(long device, int fingers)
    {
        int devices = SDL2.SDL.SDL_GetNumTouchDevices();
        long now = Environment.TickCount64;
        if (devices == _lastDeviceCount && now < _nextDiagnostic && (fingers == 0 || _reportedFingers)) return;
        _lastDeviceCount = devices;
        _nextDiagnostic = now + 30000;
        _reportedFingers |= fingers > 0;
        string deviceList = "";
        for (int i = 0; i < devices; i++)
        {
            long id = SDL2.SDL.SDL_GetTouchDevice(i);
            deviceList += $" {id}:{SDL2.SDL.SDL_GetNumTouchFingers(id)}";
        }
        Patcher.Log($"Runtime: mode=mod, mouseFallback=disabled, polls={_pollCount}, mouseInputCalls={_mouseInputCalls}, devices={devices} [{deviceList.Trim()}], activeDevice={device}, fingers={fingers}, display={TouchPanel.DisplayWidth}x{TouchPanel.DisplayHeight}, adapterActive={_touchInputActive}.");
    }

    internal static void ReportHeartbeat()
    {
        // Read counters only. SDL polling and game state must stay on the game thread.
        Patcher.Log($"Heartbeat: tick={Environment.TickCount64}, polls={System.Threading.Volatile.Read(ref _pollCount)}, " +
            $"mouseInputCalls={System.Threading.Volatile.Read(ref _mouseInputCalls)}, " +
            $"pressSamples={System.Threading.Volatile.Read(ref _pressedSamples)}, " +
            $"releaseSamples={System.Threading.Volatile.Read(ref _releasedSamples)}, " +
            $"finalPressedSamples={System.Threading.Volatile.Read(ref _finalPressedSamples)}.");
    }

    private static void ReportError(string message, Exception error)
    {
        long now = Environment.TickCount64;
        if (now < _nextError) return;
        _nextError = now + 5000;
        Patcher.Log($"{message}: {error}");
    }
}
