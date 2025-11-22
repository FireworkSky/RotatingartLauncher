using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Color = Microsoft.Xna.Framework.Color;
using Vector2 = Microsoft.Xna.Framework.Vector2;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// </summary>
internal class StartupHook
{
    public static void Initialize()
    {
        Console.WriteLine("[PerformanceProfiler] DOTNET_STARTUP_HOOKS executing...");

        // 注册程序集解析器
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用补丁初始化方法
        int result = PerformanceProfiler.PerformancePatcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[PerformanceProfiler] PerformancePatcher.Initialize returned: {result}");
    }

    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            string assemblyName = new AssemblyName(args.Name).Name ?? "";
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string patchesRootDir = Path.GetDirectoryName(patchDir) ?? "";

            // 从 patches 根目录加载共享依赖
            string sharedAssemblyPath = Path.Combine(patchesRootDir, assemblyName + ".dll");
            if (File.Exists(sharedAssemblyPath))
            {
                Console.WriteLine($"[PerformanceProfiler] Loading shared dependency: {assemblyName}");
                return Assembly.LoadFrom(sharedAssemblyPath);
            }

            // 从补丁目录加载
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[PerformanceProfiler] Loading local dependency: {assemblyName}");
                return Assembly.LoadFrom(localAssemblyPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[PerformanceProfiler] Failed to resolve assembly: {ex.Message}");
        }

        return null;
    }
}

namespace PerformanceProfiler
{
    /// <summary>
    /// 性能分析补丁 - 使用 Harmony 注入性能监控代码
    /// </summary>
    public class PerformancePatcher
    {
        private static Harmony? _harmony;
        private static ConsoleLogger? _consoleLogger;

        public static int Initialize(IntPtr arg, int argSize)
        {
            try
            {
                // 首先安装 Console 日志重定向
                string logFilePath = ConsoleLogger.GetLogFilePath();
                _consoleLogger = ConsoleLogger.Install(logFilePath);

                Console.WriteLine("========================================");
                Console.WriteLine("[PerformanceProfiler] Initializing performance profiler...");
                Console.WriteLine($"[PerformanceProfiler] Log file: {logFilePath}");
                Console.WriteLine("========================================");

                // 创建 Harmony 实例
                _harmony = new Harmony("com.ralaunch.performanceprofiler");

                // 应用补丁
                PatchGameLoop();
                PatchGraphicsDevice();
                PatchContentLoading();
                // PatchFPSDisplay 将在游戏完全加载后通过 Game.Draw 延迟打补丁

                Console.WriteLine("[PerformanceProfiler] All patches applied successfully!");
                Console.WriteLine("[PerformanceProfiler] Performance data will be logged automatically");
                Console.WriteLine("========================================");

                return 0;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceProfiler] Failed to initialize: {ex}");
                return -1;
            }
        }

        /// <summary>
        /// 补丁游戏主循环
        /// </summary>
        private static void PatchGameLoop()
        {
            try
            {
                var gameType = typeof(Game);

                // 补丁 Update 方法
                var updateMethod = gameType.GetMethod("Update", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (updateMethod != null)
                {
                    _harmony?.Patch(
                        updateMethod,
                        prefix: new HarmonyMethod(typeof(GameLoopPatches).GetMethod(nameof(GameLoopPatches.Update_Prefix))),
                        postfix: new HarmonyMethod(typeof(GameLoopPatches).GetMethod(nameof(GameLoopPatches.Update_Postfix)))
                    );
                    Console.WriteLine("[PerformanceProfiler] Patched Game.Update");
                }

                // 补丁 Draw 方法
                var drawMethod = gameType.GetMethod("Draw", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (drawMethod != null)
                {
                    _harmony?.Patch(
                        drawMethod,
                        prefix: new HarmonyMethod(typeof(GameLoopPatches).GetMethod(nameof(GameLoopPatches.Draw_Prefix))),
                        postfix: new HarmonyMethod(typeof(GameLoopPatches).GetMethod(nameof(GameLoopPatches.Draw_Postfix)))
                    );
                    Console.WriteLine("[PerformanceProfiler] Patched Game.Draw");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceProfiler] Failed to patch game loop: {ex.Message}");
            }
        }

        /// <summary>
        /// 补丁 GraphicsDevice 方法
        /// </summary>
        private static void PatchGraphicsDevice()
        {
            try
            {
                var gfxType = typeof(Microsoft.Xna.Framework.Graphics.GraphicsDevice);

                // 补丁 Present 方法（所有重载）
                var presentMethods = gfxType.GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .Where(m => m.Name == "Present")
                    .ToArray();

                foreach (var presentMethod in presentMethods)
                {
                    _harmony?.Patch(
                        presentMethod,
                        prefix: new HarmonyMethod(typeof(GraphicsPatches).GetMethod(nameof(GraphicsPatches.Present_Prefix))),
                        postfix: new HarmonyMethod(typeof(GraphicsPatches).GetMethod(nameof(GraphicsPatches.Present_Postfix)))
                    );
                }
                Console.WriteLine($"[PerformanceProfiler] Patched {presentMethods.Length} GraphicsDevice.Present overloads");

                // 补丁 Clear 方法
                var clearMethods = gfxType.GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .Where(m => m.Name == "Clear")
                    .ToArray();

                foreach (var clearMethod in clearMethods)
                {
                    _harmony?.Patch(
                        clearMethod,
                        prefix: new HarmonyMethod(typeof(GraphicsPatches).GetMethod(nameof(GraphicsPatches.Clear_Prefix)))
                    );
                }
                Console.WriteLine($"[PerformanceProfiler] Patched {clearMethods.Length} GraphicsDevice.Clear overloads");

                // 补丁 DrawPrimitives 方法
                var drawPrimitivesMethod = gfxType.GetMethod("DrawPrimitives", BindingFlags.Instance | BindingFlags.Public);
                if (drawPrimitivesMethod != null)
                {
                    _harmony?.Patch(
                        drawPrimitivesMethod,
                        prefix: new HarmonyMethod(typeof(GraphicsPatches).GetMethod(nameof(GraphicsPatches.DrawPrimitives_Prefix)))
                    );
                    Console.WriteLine("[PerformanceProfiler] Patched GraphicsDevice.DrawPrimitives");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceProfiler] Failed to patch graphics device: {ex.Message}");
            }
        }

        /// <summary>
        /// 补丁内容加载
        /// </summary>
        private static void PatchContentLoading()
        {
            try
            {
                var contentManagerType = typeof(Microsoft.Xna.Framework.Content.ContentManager);

                var loadMethod = contentManagerType.GetMethod("Load", BindingFlags.Instance | BindingFlags.Public);
                if (loadMethod != null && loadMethod.IsGenericMethodDefinition)
                {
                    // 获取通用方法
                    var loadTextureMethod = loadMethod.MakeGenericMethod(typeof(Microsoft.Xna.Framework.Graphics.Texture2D));

                    _harmony?.Patch(
                        loadTextureMethod,
                        prefix: new HarmonyMethod(typeof(ContentPatches).GetMethod(nameof(ContentPatches.LoadTexture_Prefix))),
                        postfix: new HarmonyMethod(typeof(ContentPatches).GetMethod(nameof(ContentPatches.LoadTexture_Postfix)))
                    );
                    Console.WriteLine("[PerformanceProfiler] Patched ContentManager.Load<Texture2D>");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceProfiler] Failed to patch content loading: {ex.Message}");
            }
        }

        /// <summary>
        /// 补丁 FPS 显示
        /// </summary>
        internal static void PatchFPSDisplay()
        {
            try
            {
                // 查找 Terraria.Main 类
                var mainType = Type.GetType("Terraria.Main, Terraria");
                if (mainType == null)
                {
                    Console.WriteLine("[PerformanceProfiler] Could not find Terraria.Main type");
                    return;
                }

                // 补丁 DrawFPS 方法
                var drawFPSMethod = mainType.GetMethod("DrawFPS", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (drawFPSMethod != null)
                {
                    _harmony?.Patch(
                        drawFPSMethod,
                        postfix: new HarmonyMethod(typeof(FPSDisplayPatches).GetMethod(nameof(FPSDisplayPatches.DrawFPS_Postfix)))
                    );
                    Console.WriteLine("[PerformanceProfiler] Patched Terraria.Main.DrawFPS");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PerformanceProfiler] Failed to patch FPS display: {ex.Message}");
            }
        }
    }

    /// <summary>
    /// 游戏主循环补丁
    /// </summary>
    public static class GameLoopPatches
    {
        private static bool _fpsDisplayPatched = false;

        public static void Update_Prefix(GameTime gameTime, Game __instance)
        {
            PerformanceMonitor.Instance.BeginMethodProfile("Game.Update");
        }

        public static void Update_Postfix(GameTime gameTime)
        {
            PerformanceMonitor.Instance.EndMethodProfile();
        }

        public static void Draw_Prefix(GameTime gameTime)
        {
            PerformanceMonitor.Instance.OnFrameBegin();
            PerformanceMonitor.Instance.BeginMethodProfile("Game.Draw");
        }

        public static void Draw_Postfix(GameTime gameTime, Game __instance)
        {
            PerformanceMonitor.Instance.EndMethodProfile();
            PerformanceMonitor.Instance.OnFrameEnd(gameTime.ElapsedGameTime.TotalSeconds);

            // 延迟打 FPS 显示补丁,等待 Terraria.Main 类型加载
            if (!_fpsDisplayPatched)
            {
                try
                {
                    var mainType = Type.GetType("Terraria.Main, Terraria");
                    if (mainType != null)
                    {
                        PerformancePatcher.PatchFPSDisplay();
                        _fpsDisplayPatched = true;
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[PerformanceProfiler] Failed to patch FPS display in Draw_Postfix: {ex.Message}");
                    _fpsDisplayPatched = true; // 防止重复尝试
                }
            }
        }
    }

    /// <summary>
    /// 图形设备补丁
    /// </summary>
    public static class GraphicsPatches
    {
        private static int _presentCallCount = 0;
        private static int _clearCallCount = 0;
        private static int _drawCallCount = 0;

        public static void Present_Prefix()
        {
            _presentCallCount++;
            PerformanceMonitor.Instance.BeginMethodProfile("GraphicsDevice.Present");
        }

        public static void Present_Postfix()
        {
            PerformanceMonitor.Instance.EndMethodProfile();
        }

        public static void Clear_Prefix()
        {
            _clearCallCount++;
            PerformanceMonitor.Instance.BeginMethodProfile("GraphicsDevice.Clear");
            PerformanceMonitor.Instance.EndMethodProfile();
        }

        public static void DrawPrimitives_Prefix()
        {
            _drawCallCount++;
        }

        public static void ResetCounters()
        {
            _presentCallCount = 0;
            _clearCallCount = 0;
            _drawCallCount = 0;
        }

        public static string GetStats()
        {
            return $"Present: {_presentCallCount}, Clear: {_clearCallCount}, DrawCalls: {_drawCallCount}";
        }
    }

    /// <summary>
    /// 内容加载补丁
    /// </summary>
    public static class ContentPatches
    {
        public static void LoadTexture_Prefix(string assetName)
        {
            PerformanceMonitor.Instance.BeginMethodProfile($"Load<Texture>: {assetName}");
        }

        public static void LoadTexture_Postfix(string assetName)
        {
            PerformanceMonitor.Instance.EndMethodProfile();
        }
    }

    /// <summary>
    /// FPS 显示补丁 - 在按 F10 时显示详细性能信息
    /// </summary>
    public static class FPSDisplayPatches
    {
        public static void DrawFPS_Postfix(object __instance)
        {
            try
            {
                // 获取 Main.showFrameRate 字段
                var mainType = __instance.GetType();
                var showFrameRateField = mainType.GetField("showFrameRate", BindingFlags.Public | BindingFlags.Static);
                if (showFrameRateField == null)
                    return;

                bool showFrameRate = (bool)(showFrameRateField.GetValue(null) ?? false);
                if (!showFrameRate)
                    return;

                // 获取性能统计数据
                var stats = PerformanceMonitor.Instance.GetCurrentStats();

                // 获取 spriteBatch 字段
                var spriteBatchField = mainType.GetField("spriteBatch", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
                if (spriteBatchField == null)
                    return;

                var spriteBatch = spriteBatchField.GetValue(__instance) as Microsoft.Xna.Framework.Graphics.SpriteBatch;
                if (spriteBatch == null)
                    return;

                // 获取字体（使用游戏内置的鼠标文本字体）
                var fontAssetsType = Type.GetType("Terraria.GameContent.FontAssets, Terraria");
                if (fontAssetsType == null)
                    return;

                var mouseTextProperty = fontAssetsType.GetProperty("MouseText", BindingFlags.Public | BindingFlags.Static);
                if (mouseTextProperty == null)
                    return;

                dynamic assetValue = mouseTextProperty.GetValue(null);
                var font = assetValue?.Value as Microsoft.Xna.Framework.Graphics.SpriteFont;
                if (font == null)
                    return;

                // 绘制详细性能信息
                int yOffset = 30; // 从 FPS 显示下方开始
                var textColor = Color.Yellow;

                // FPS 信息
                DrawText(spriteBatch, font, $"FPS: {stats.CurrentFPS:F1} (Avg: {stats.AverageFPS:F1}, Min: {stats.MinFPS:F1}, Max: {stats.MaxFPS:F1})",
                    4, yOffset, textColor);
                yOffset += 15;

                // 帧时间信息
                DrawText(spriteBatch, font, $"FrameTime: {stats.LastFrameTime:F2}ms (Avg: {stats.AverageFrameTime:F2}ms, Max: {stats.MaxFrameTime:F2}ms)",
                    4, yOffset, textColor);
                yOffset += 15;

                // 内存和 GC 信息
                DrawText(spriteBatch, font, $"Memory: {stats.MemoryMB}MB | GC: Gen0={stats.Gen0Collections}, Gen1={stats.Gen1Collections}, Gen2={stats.Gen2Collections}",
                    4, yOffset, textColor);
                yOffset += 15;

                // 最慢的方法
                if (stats.TopMethods != null && stats.TopMethods.Length > 0)
                {
                    DrawText(spriteBatch, font, "Top Slow Methods:", 4, yOffset, Color.Orange);
                    yOffset += 15;

                    for (int i = 0; i < Math.Min(3, stats.TopMethods.Length); i++)
                    {
                        var method = stats.TopMethods[i];
                        string methodName = method.Name.Length > 30 ? method.Name.Substring(0, 30) : method.Name;
                        DrawText(spriteBatch, font, $"  {methodName}: {method.AverageTimeMs:F3}ms",
                            4, yOffset, textColor);
                        yOffset += 15;
                    }
                }
            }
            catch (Exception ex)
            {
                // 静默失败，不影响游戏
                Console.WriteLine($"[PerformanceProfiler] DrawFPS_Postfix error: {ex.Message}");
            }
        }

        private static void DrawText(Microsoft.Xna.Framework.Graphics.SpriteBatch spriteBatch,
            Microsoft.Xna.Framework.Graphics.SpriteFont font, string text, int x, int y, Color color)
        {
            // 绘制阴影
            spriteBatch.DrawString(font, text, new Vector2(x + 1, y + 1), Color.Black);
            // 绘制文本
            spriteBatch.DrawString(font, text, new Vector2(x, y), color);
        }
    }
}
