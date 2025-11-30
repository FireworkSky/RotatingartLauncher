using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using HarmonyLib;
using Microsoft.Xna.Framework;

namespace FPSDisplayPatch;

/// <summary>
/// FPS 显示补丁
/// 使用 FNA 自己计算 FPS，通过环境变量回调 Java UI，在隐藏鼠标光标时显示
/// </summary>
public static class FPSDisplayPatcher
{
    private static Harmony? _harmony;
    
    // FPS 计算相关
    private static int _frameCount = 0;
    private static double _lastUpdateTime = 0;
    private static double _fpsUpdateInterval = 0.5; // 每 0.5 秒更新一次 FPS
    private static float _currentFPS = 0f;
    private static readonly Stopwatch _stopwatch = Stopwatch.StartNew();
    
    // 状态跟踪
    private static bool _lastCursorHidden = false;
    private static readonly object _lock = new object();
    
    // 环境变量文件路径（用于与 Java 通信）
    private static string? _fpsDataFile;
    
    // JNI 回调函数（如果可用）
    [DllImport("main", CallingConvention = CallingConvention.Cdecl)]
    private static extern int RAL_NotifyFPS(float fps);
    
    // Unix setenv 函数（直接设置进程环境变量）
    [DllImport("c", CallingConvention = CallingConvention.Cdecl)]
    private static extern int setenv(string name, string value, int overwrite);
    
    
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[FPSDisplayPatch] Initializing FPS display patch...");
            Console.WriteLine("========================================");
            
            // 初始化 FPS 数据文件路径
            // 尝试多个环境变量来获取应用数据目录
            string? appDir = Environment.GetEnvironmentVariable("RALCORE_APP_DIR");
            if (string.IsNullOrEmpty(appDir))
            {
                appDir = Environment.GetEnvironmentVariable("HOME");
            }
            if (string.IsNullOrEmpty(appDir))
            {
                appDir = Environment.GetEnvironmentVariable("XDG_DATA_HOME");
            }
            if (!string.IsNullOrEmpty(appDir))
            {
                _fpsDataFile = Path.Combine(appDir, "fps_data.txt");
                Console.WriteLine($"[FPSDisplayPatch] FPS data file: {_fpsDataFile}");
            }
            else
            {
                Console.WriteLine("[FPSDisplayPatch] WARNING: Could not determine app data directory");
            }
            
            // 初始化计时器
            _lastUpdateTime = _stopwatch.Elapsed.TotalSeconds;
            
            ApplyPatches();
            
            Console.WriteLine("[FPSDisplayPatch] Initialization complete");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[FPSDisplayPatch] ERROR: {ex.Message}");
            return -1;
        }
    }
    
    private static void ApplyPatches()
    {
        try
        {
            Console.WriteLine("[FPSDisplayPatch] Applying patches...");
            
            _harmony = new Harmony("com.ralaunch.fpsdisplay");
            
            // 延迟 patch，等待 Game 类型加载
            ThreadPool.QueueUserWorkItem(_ =>
            {
                int attempts = 0;
                while (attempts < 100)
                {
                    try
                    {
                        // Patch FNA Game.Draw 来计算和显示 FPS
                        // Game.Draw 是 protected virtual 方法，需要 NonPublic 标志
                        var drawMethod = typeof(Game).GetMethod("Draw", 
                            BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                        
                        if (drawMethod != null && _harmony != null)
                        {
                            Console.WriteLine($"[FPSDisplayPatch] Found Game.Draw method: {drawMethod}");
                            var postfix = new HarmonyMethod(typeof(FPSDisplayPatcher), nameof(Draw_Postfix));
                            _harmony.Patch(drawMethod, postfix: postfix);
                            Console.WriteLine("[FPSDisplayPatch] Game.Draw patched!");
                            break;
                        }
                        else
                        {
                            if (attempts % 10 == 0)
                                Console.WriteLine($"[FPSDisplayPatch] Game.Draw method not found yet (attempt {attempts})");
                        }
                    }
                    catch (Exception ex)
                    {
                        if (attempts % 10 == 0)
                            Console.WriteLine($"[FPSDisplayPatch] Waiting for Game.Draw... ({ex.Message})");
                    }
                    
                    Thread.Sleep(100);
                    attempts++;
                }
            });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[FPSDisplayPatch] Patch error: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Game.Draw 后置补丁 - 计算 FPS 并通过回调传递给 Java 层
    /// </summary>
    public static void Draw_Postfix(Game __instance, GameTime gameTime)
    {
        try
        {
            // 更新帧计数
            _frameCount++;
            
            // 获取当前时间
            double currentTime = _stopwatch.Elapsed.TotalSeconds;
            double elapsed = currentTime - _lastUpdateTime;
            
            // 如果超过更新间隔，计算 FPS
            if (elapsed >= _fpsUpdateInterval)
            {
                _currentFPS = (float)(_frameCount / elapsed);
                _frameCount = 0;
                _lastUpdateTime = currentTime;
            }
            
            // 检查鼠标光标是否隐藏（通过环境变量）
            bool hideCursor = false;
            string? hideCursorEnv = Environment.GetEnvironmentVariable("RALCORE_HIDE_CURSOR");
            if (!string.IsNullOrEmpty(hideCursorEnv) && hideCursorEnv == "1")
            {
                hideCursor = true;
            }
            
            // 更新 FPS 数据（仅在变化时）
            bool cursorStateChanged = hideCursor != _lastCursorHidden;
            bool shouldUpdate = cursorStateChanged || (elapsed >= _fpsUpdateInterval);
            
            if (shouldUpdate)
            {
                lock (_lock)
                {
                    _lastCursorHidden = hideCursor;
                    UpdateFPSData(_currentFPS, hideCursor);
                }
            }
        }
        catch
        {
            // 静默失败，不影响游戏
        }
    }
    
    /// <summary>
    /// 更新 FPS 数据到环境变量
    /// </summary>
    private static void UpdateFPSData(float fps, bool cursorHidden)
    {
        try
        {
            string fpsValue = fps.ToString("F1");
            string cursorValue = cursorHidden ? "1" : "0";
            
            // 使用 setenv 直接设置进程环境变量（确保 Java Os.getenv 能读取到）
            try
            {
                setenv("RALCORE_FPS", fpsValue, 1);
                setenv("RALCORE_CURSOR_HIDDEN", cursorValue, 1);
            }
            catch
            {
                // 如果 setenv 失败，尝试使用 .NET 的方式
                Environment.SetEnvironmentVariable("RALCORE_FPS", fpsValue);
                Environment.SetEnvironmentVariable("RALCORE_CURSOR_HIDDEN", cursorValue);
            }
        }
        catch
        {
            // 忽略所有错误
        }
    }
}

