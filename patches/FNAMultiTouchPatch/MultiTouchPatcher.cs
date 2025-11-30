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
    
    // 屏幕尺寸（Android 物理分辨率）
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern int RAL_GetScreenWidth();
    
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern int RAL_GetScreenHeight();
    
    // 虚拟鼠标位置（右摇杆鼠标移动模式）
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern int RAL_IsVirtualMouseActive();
    
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern float RAL_GetVirtualMouseX();
    
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern float RAL_GetVirtualMouseY();
    
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
            
            // 帧率诊断和优化
            TryOptimizeFrameRate();
            
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
            
            // 检查虚拟鼠标是否启用（右摇杆鼠标移动模式）
            bool virtualMouseActive = RAL_IsVirtualMouseActive() != 0;
            
            if (shouldLog)
            {
                Console.WriteLine($"[MultiTouchPatch] Frame {_frameCount}: touchCount={touchCount}, virtualMouse={virtualMouseActive}");
            }
            
            // 保存旧状态
            PlayerInput.MouseInfoOld = PlayerInput.MouseInfo;
            
            // 主触摸点（触摸点 0）
            bool isTouching = touchCount > 0;
            int screenX = _lastTouchX;
            int screenY = _lastTouchY;
            
            // 获取 Android 物理分辨率（touch_bridge 存储的）
            int androidWidth = RAL_GetScreenWidth();
            int androidHeight = RAL_GetScreenHeight();
            
            // 计算缩放比例（Android 物理分辨率 -> 游戏分辨率）
            float scaleX = (androidWidth > 0) ? (float)Main.screenWidth / androidWidth : 1.0f;
            float scaleY = (androidHeight > 0) ? (float)Main.screenHeight / androidHeight : 1.0f;
            
            // 如果虚拟鼠标启用，使用虚拟鼠标位置（右摇杆控制）
            if (virtualMouseActive)
            {
                // 虚拟鼠标位置是 Android 像素坐标，需要转换为游戏坐标
                screenX = (int)(RAL_GetVirtualMouseX() * scaleX);
                screenY = (int)(RAL_GetVirtualMouseY() * scaleY);
                // 虚拟鼠标模式下，鼠标位置由右摇杆控制，触屏控制是否按下
                // 始终更新最后触摸位置，即使没有触摸也要更新虚拟鼠标位置
                _lastTouchX = screenX;
                _lastTouchY = screenY;
            }
            else if (isTouching)
            {
                // 触屏坐标是归一化的（0-1），转换为游戏坐标
                screenX = (int)(RAL_GetTouchX(0) * Main.screenWidth);
                screenY = (int)(RAL_GetTouchY(0) * Main.screenHeight);
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
                    // 额外触摸点也使用归一化坐标
                    extraX = (int)(RAL_GetTouchX(i) * Main.screenWidth);
                    extraY = (int)(RAL_GetTouchY(i) * Main.screenHeight);
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
            // 在虚拟鼠标模式下，即使位置没有变化也要更新鼠标位置，确保鼠标移动生效
            bool positionChanged = finalX != PlayerInput.MouseInfoOld.X || finalY != PlayerInput.MouseInfoOld.Y || finalPressed != _wasTouching;
            if (positionChanged || virtualMouseActive)
            {
                PlayerInput.MouseX = (int)(finalX * PlayerInput.RawMouseScale.X);
                PlayerInput.MouseY = (int)(finalY * PlayerInput.RawMouseScale.Y);
                
                // 在虚拟鼠标模式下，如果位置改变，强制标记输入检测
                if (virtualMouseActive && positionChanged)
                {
                    inputDetected = true;
                    PlayerInput.SettingsForUI.SetCursorMode(CursorMode.Mouse);
                }
                else if (!PlayerInput.PreventFirstMousePositionGrab)
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
            // 在虚拟鼠标模式下，已经在上面更新了 _lastTouchX 和 _lastTouchY
            if (!virtualMouseActive && isTouching)
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
    
    /// <summary>
    /// 尝试优化帧率 - 诊断和解除帧率限制
    /// </summary>
    private static void TryOptimizeFrameRate()
    {
        try
        {
            Console.WriteLine("[MultiTouchPatch] ===== Frame Rate Diagnostics =====");
            
            // 获取 Main 类（Terraria 的主类）
            var mainType = typeof(Main);
            var mainInstance = Main.instance;
            
            if (mainInstance == null)
            {
                Console.WriteLine("[MultiTouchPatch] Main.instance is null, skipping frame rate optimization");
                return;
            }
            
            // Game 类的属性
            var gameType = typeof(Game);
            
            // 检查 IsFixedTimeStep
            var isFixedProp = gameType.GetProperty("IsFixedTimeStep");
            if (isFixedProp != null)
            {
                bool isFixed = (bool)isFixedProp.GetValue(mainInstance);
                Console.WriteLine($"[MultiTouchPatch] IsFixedTimeStep: {isFixed}");
            }
            
            // 检查 TargetElapsedTime
            var targetProp = gameType.GetProperty("TargetElapsedTime");
            if (targetProp != null)
            {
                TimeSpan target = (TimeSpan)targetProp.GetValue(mainInstance);
                double fps = 1000.0 / target.TotalMilliseconds;
                Console.WriteLine($"[MultiTouchPatch] TargetElapsedTime: {target.TotalMilliseconds:F2}ms ({fps:F1} FPS)");
            }
            
            // 检查环境变量是否要求解除帧率限制
            string unlockFps = Environment.GetEnvironmentVariable("FNA3D_UNLOCK_FPS");
            if (unlockFps == "1")
            {
                Console.WriteLine("[MultiTouchPatch] FNA3D_UNLOCK_FPS=1 detected, unlocking frame rate...");
                
                // 禁用固定时间步
                if (isFixedProp != null)
                {
                    isFixedProp.SetValue(mainInstance, false);
                    Console.WriteLine("[MultiTouchPatch] Set IsFixedTimeStep = false");
                }
                
                // 设置高帧率目标（120fps）
                if (targetProp != null)
                {
                    targetProp.SetValue(mainInstance, TimeSpan.FromMilliseconds(1000.0 / 120.0));
                    Console.WriteLine("[MultiTouchPatch] Set TargetElapsedTime = 8.33ms (120 FPS)");
                }
            }
            
            Console.WriteLine("[MultiTouchPatch] ===================================");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MultiTouchPatch] Frame rate optimization error: {ex.Message}");
        }
    }
}
