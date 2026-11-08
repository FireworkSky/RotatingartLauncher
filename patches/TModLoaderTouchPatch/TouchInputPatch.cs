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
    private static long _activeTouchDevice;
    private static TouchLocation[] _touches = Array.Empty<TouchLocation>();
    private static TouchLocation[] _previousTouches = Array.Empty<TouchLocation>();

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
            SDL2.SDL.SDL_ShowCursor(0);

            var playerInputType = tModLoaderAssembly.GetType("Terraria.GameInput.PlayerInput");
            var mouseInput = playerInputType?.GetMethod(
                "MouseInput",
                BindingFlags.Static | BindingFlags.NonPublic
            );

            if (mouseInput == null)
            {
                Console.WriteLine("[TModLoaderTouchPatch] PlayerInput.MouseInput not found; native touch adapter disabled.");
                return;
            }

            harmony.Patch(
                mouseInput,
                prefix: new HarmonyMethod(typeof(TouchInputPatch), nameof(PlayerInputMouseInputPrefix))
            );

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

            Console.WriteLine("[TModLoaderTouchPatch] Native touch-only input applied; mouse input and game cursor disabled.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderTouchPatch] Native touch adapter failed: {ex.Message}");
        }
    }

    private static bool UpdateTouchPanelStatePrefix()
    {
        Array.Copy(_touches, _previousTouches, _touches.Length);
        Array.Clear(_touches, 0, _touches.Length);
        long device = GetActiveTouchDevice(0);
        int count = Math.Min(SDL2.SDL.SDL_GetNumTouchFingers(device), _touches.Length);
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
        return false;
    }

    private static long GetActiveTouchDevice(int fallbackIndex)
    {
        if (_activeTouchDevice != 0 && SDL2.SDL.SDL_GetNumTouchFingers(_activeTouchDevice) > 0)
            return _activeTouchDevice;
        var deviceCount = SDL2.SDL.SDL_GetNumTouchDevices();
        for (int i = 0; i < deviceCount; i++)
        {
            long device = SDL2.SDL.SDL_GetTouchDevice(i);
            int fingerCount = SDL2.SDL.SDL_GetNumTouchFingers(device);
            // SDL_MOUSE_TOUCHID (-1) represents mouse-to-touch synthesis.
            if (device != 0 && device != -1 && fingerCount > 0)
            {
                _activeTouchDevice = device;
                return device;
            }
        }
        return _activeTouchDevice != 0 ? _activeTouchDevice : SDL2.SDL.SDL_GetTouchDevice(fallbackIndex);
    }

    public static bool PlayerInputMouseInputPrefix()
    {
        try
        {
            SDL2.SDL.SDL_ShowCursor(0);
            TryReadTouch(out var position, out var pressed);
            ApplyTouchToPlayerInput(position, pressed);
            return false;
        }
        catch (Exception ex)
        {
            ResetTouchState();
            ApplyTouchToPlayerInput(default, pressed: false);
            Console.WriteLine($"[TModLoaderTouchPatch] Touch polling failed; mouse input remains disabled: {ex.Message}");
            return false;
        }
    }

    public static void MouseGetStatePostfix(ref MouseState __result)
    {
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

    private static void TryReadTouch(out Vector2 position, out bool pressed)
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
            return;
        }

        position = selected.Position;
        pressed = selected.State != TouchLocationState.Released;
        _lastPosition = position;
        _touchPressed = pressed;

        if (!pressed)
        {
            _primaryFingerId = NO_FINGER;
        }

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
    }

}
