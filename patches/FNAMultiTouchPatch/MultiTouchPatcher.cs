using System;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Input;
using Terraria;
using Terraria.GameInput;

namespace FNAMultiTouchPatch;

/// <summary>
/// 多点触控补丁 v10
/// 同时 patch PlayerInput.MouseInput 和 Mouse.GetState
/// 解决模组 UI 点击问题
/// </summary>
public static class MultiTouchPatcher
{
    private static Harmony? _harmony;
    private static int _frameCount = 0;
    
    // JNI 桥接
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern int RAL_GetTouchCount();
    
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern float RAL_GetTouchX(int index);
    
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern float RAL_GetTouchY(int index);
    
    // 共享的触摸状态（供 Mouse.GetState patch 使用）
    public static int CurrentTouchX = 0;
    public static int CurrentTouchY = 0;
    public static bool CurrentTouchPressed = false;
    
    // 主触摸点状态
    private static int _lastTouchX = 0;
    private static int _lastTouchY = 0;
    private static bool _wasTouching = false;
    
    // 额外触摸点状态
    private static bool[] _touchWasActive = new bool[10];
    
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[MultiTouchPatch] Initializing multi-touch v10...");
            Console.WriteLine("========================================");
            
            try
            {
                int testCount = RAL_GetTouchCount();
                Console.WriteLine($"[MultiTouchPatch] JNI bridge OK");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[MultiTouchPatch] JNI bridge error: {ex.Message}");
            }
            
            ApplyPatches();
            
            Console.WriteLine("[MultiTouchPatch] Initialization complete");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MultiTouchPatch] ERROR: {ex.Message}");
            return -1;
        }
    }
    
    private static void ApplyPatches()
    {
        try
        {
            Console.WriteLine("[MultiTouchPatch] Applying patches...");
            
            _harmony = new Harmony("com.ralaunch.multitouch");
            
            // Patch 1: PlayerInput.MouseInput
            var mouseInputMethod = typeof(PlayerInput).GetMethod("MouseInput", 
                BindingFlags.Static | BindingFlags.NonPublic);
            
            if (mouseInputMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(MultiTouchPatcher), nameof(TouchInput));
                _harmony.Patch(mouseInputMethod, prefix: prefix);
                Console.WriteLine("[MultiTouchPatch] PlayerInput.MouseInput patched!");
            }
            
            // Patch 2: Mouse.GetState - 让模组获得正确的鼠标状态
            var mouseGetStateMethod = typeof(Mouse).GetMethod("GetState", 
                BindingFlags.Static | BindingFlags.Public);
            
            if (mouseGetStateMethod != null)
            {
                var postfix = new HarmonyMethod(typeof(MultiTouchPatcher), nameof(MouseGetState_Postfix));
                _harmony.Patch(mouseGetStateMethod, postfix: postfix);
                Console.WriteLine("[MultiTouchPatch] Mouse.GetState patched!");
            }
            
            Console.WriteLine("[MultiTouchPatch] All patches applied!");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MultiTouchPatch] Patch error: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Mouse.GetState 后缀 - 用触摸数据覆盖返回值
    /// </summary>
    public static void MouseGetState_Postfix(ref MouseState __result)
    {
        try
        {
            // 用我们的触摸数据覆盖 Mouse.GetState 的返回值
            __result = new MouseState(
                CurrentTouchX,
                CurrentTouchY,
                __result.ScrollWheelValue,
                CurrentTouchPressed ? ButtonState.Pressed : ButtonState.Released,
                __result.MiddleButton,
                __result.RightButton,
                __result.XButton1,
                __result.XButton2
            );
        }
        catch
        {
            // 忽略错误
        }
    }
    
    /// <summary>
    /// 触摸输入 - 替换 PlayerInput.MouseInput
    /// </summary>
    public static bool TouchInput()
    {
        try
        {
            _frameCount++;
            bool inputDetected = false;
            bool shouldLog = _frameCount % 60 == 0;
            
            // 从 JNI 桥获取触摸数据
            int touchCount = RAL_GetTouchCount();
            
            if (shouldLog)
            {
                Console.WriteLine($"[MultiTouchPatch] Frame {_frameCount}: touchCount={touchCount}");
            }
            
            // 保存旧状态
            PlayerInput.MouseInfoOld = PlayerInput.MouseInfo;
            
            // 主触摸点（触摸点 0）
            bool isTouching = touchCount > 0;
            int screenX = _lastTouchX;
            int screenY = _lastTouchY;
            
            if (isTouching)
            {
                screenX = (int)RAL_GetTouchX(0);
                screenY = (int)RAL_GetTouchY(0);
            }
            
            // 检测额外触摸点的按下边缘
            bool extraJustPressed = false;
            int extraX = 0, extraY = 0;
            
            for (int i = 1; i < touchCount && i < 10; i++)
            {
                bool wasActive = _touchWasActive[i];
                _touchWasActive[i] = true;
                
                if (!wasActive)
                {
                    extraJustPressed = true;
                    extraX = (int)RAL_GetTouchX(i);
                    extraY = (int)RAL_GetTouchY(i);
                    Console.WriteLine($"[MultiTouchPatch] Extra touch at ({extraX}, {extraY})");
                    break;
                }
            }
            
            // 清除已释放的额外触摸点状态
            for (int i = touchCount; i < 10; i++)
            {
                _touchWasActive[i] = false;
            }
            
            // 决定最终的鼠标位置
            int finalX, finalY;
            bool finalPressed;
            
            if (extraJustPressed)
            {
                finalX = extraX;
                finalY = extraY;
                finalPressed = true;
            }
            else
            {
                finalX = screenX;
                finalY = screenY;
                finalPressed = isTouching;
            }
            
            // 更新共享状态（供 Mouse.GetState patch 使用）
            CurrentTouchX = finalX;
            CurrentTouchY = finalY;
            CurrentTouchPressed = finalPressed;
            
            // 创建 MouseState
            ButtonState leftBtn = finalPressed ? ButtonState.Pressed : ButtonState.Released;
            
            PlayerInput.MouseInfo = new MouseState(
                finalX, finalY, 0,
                leftBtn, ButtonState.Released, ButtonState.Released,
                ButtonState.Released, ButtonState.Released
            );
            
            // 更新 PlayerInput 位置
            if (finalX != PlayerInput.MouseInfoOld.X || finalY != PlayerInput.MouseInfoOld.Y || finalPressed != _wasTouching)
            {
                PlayerInput.MouseX = (int)(finalX * PlayerInput.RawMouseScale.X);
                PlayerInput.MouseY = (int)(finalY * PlayerInput.RawMouseScale.Y);
                
                if (!PlayerInput.PreventFirstMousePositionGrab)
                {
                    inputDetected = true;
                    PlayerInput.SettingsForUI.SetCursorMode(CursorMode.Mouse);
                }
                PlayerInput.PreventFirstMousePositionGrab = false;
            }
            
            // 更新 MouseKeys
            PlayerInput.MouseKeys.Clear();
            if (Main.instance.IsActive && finalPressed)
            {
                PlayerInput.MouseKeys.Add("Mouse1");
                inputDetected = true;
            }
            
            if (inputDetected)
            {
                PlayerInput.CurrentInputMode = InputMode.Mouse;
                PlayerInput.Triggers.Current.UsedMovementKey = false;
            }
            
            // 直接设置 Main 的鼠标状态
            Main.mouseX = PlayerInput.MouseX;
            Main.mouseY = PlayerInput.MouseY;
            
            // 保存状态供下一帧使用
            if (isTouching)
            {
                _lastTouchX = screenX;
                _lastTouchY = screenY;
            }
            _wasTouching = isTouching;
            
            return false;
        }
        catch (Exception ex)
        {
            if (_frameCount % 60 == 0)
                Console.WriteLine($"[MultiTouchPatch] Error: {ex.Message}");
            return true;
        }
    }
}
