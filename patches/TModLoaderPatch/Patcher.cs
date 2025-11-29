using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using System.Runtime.InteropServices;
using HarmonyLib;
using static System.Net.WebRequestMethods;

namespace TModLoaderPatch;

/// <summary>
/// tModLoader 补丁程序集
/// 修复 InstallVerifier 在 Android/ARM64 平台上的 vanillaSteamAPI 为 null 导致的异常
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;

    /// <summary>
    /// 补丁初始化方法
    /// 会在游戏程序集加载前被自动调用
    /// </summary>
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[TModLoaderPatch] Initializing Android/ARM64 fix patch...");
            Console.WriteLine("========================================");

            // 打印补丁信息
            PrintPatchInfo();

            // 应用 Harmony 补丁
            ApplyHarmonyPatches();

            Console.WriteLine("[TModLoaderPatch] Patch initialized successfully");
            Console.WriteLine("========================================");

            return 0; // 成功
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[TModLoaderPatch] Stack: {ex.StackTrace}");
            return -1; // 失败
        }
    }

    /// <summary>
    /// 打印补丁信息
    /// </summary>
    private static void PrintPatchInfo()
    {
        var assembly = Assembly.GetExecutingAssembly();
        var version = assembly.GetName().Version;

        Console.WriteLine($"Patch Assembly: {assembly.GetName().Name}");
        Console.WriteLine($"Version: {version}");
        Console.WriteLine($"Location: {assembly.Location}");
        Console.WriteLine($".NET Version: {Environment.Version}");
        Console.WriteLine($"Harmony Version: {typeof(Harmony).Assembly.GetName().Version}");
    }

    /// <summary>
    /// 应用 Harmony 补丁
    /// </summary>
    private static void ApplyHarmonyPatches()
    {
        try
        {
            _harmony = new Harmony("com.ralaunch.tmlpatch");
            
            // 从已加载的程序集中查找 tModLoader
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");

            if (tModLoaderAssembly != null)
            {
                Console.WriteLine("[TModLoaderPatch] tModLoader assembly already loaded, patching directly...");
                ApplyPatchesInternal(tModLoaderAssembly);
                Console.WriteLine("[TModLoaderPatch] tModLoader assembly already loaded, patched!");
            }
            else
            {
                Console.WriteLine("[TModLoaderPatch] tModLoader not loaded yet, will patch on AssemblyLoad event");
                // 监听程序集加载事件，在 tModLoader 程序集加载后应用补丁
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
                Console.WriteLine("[TModLoaderPatch] Harmony patches registered");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] Failed to apply Harmony patches: {ex.Message}");
            throw;
        }
    }

    /// <summary>
    /// 当程序集加载时触发
    /// </summary>
    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        try
        {
            var assemblyName = args.LoadedAssembly.GetName().Name;

            // 检查是否是 tModLoader 程序集
            if (assemblyName == "tModLoader")
            {
                ApplyPatchesInternal(args.LoadedAssembly);
                
                // 移除事件监听器，避免重复处理
                AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] Error in OnAssemblyLoaded: {ex.Message}");
            Console.WriteLine($"[TModLoaderPatch] Stack: {ex.StackTrace}");
        }
    }
    
    /// <summary>
    /// 内部补丁应用方法
    /// </summary>
    private static void ApplyPatchesInternal(Assembly assembly)
    {
        Console.WriteLine("[TModLoaderPatch] Applying patches and mitigations...");
        
        InstallVerifierBugMitigation(assembly);
        LoggingHooksHarmonyPatch(assembly);
        TMLContentManagerPatch(assembly);
        FullScreenPatch(assembly);
        HideCursorPatch(assembly);

        Console.WriteLine("[TModLoaderPatch] All patches applied successfully!");
    }

    /// <summary>
    /// 全屏补丁 - 使用 Harmony Prefix 在 Game.Run 方法执行前设置全屏
    /// </summary>
    public static void FullScreenPatch(Assembly assembly)
    {
        try
        {
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var FNAAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "FNA");

            if (FNAAssembly == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] FNA assembly not found.");
                return;
            }

            // 从 FNA 程序集中获取 Game 类型（FNA 使用 Microsoft.Xna.Framework 命名空间）
            Type? gameType = FNAAssembly.GetType("Microsoft.Xna.Framework.Game");

            if (gameType == null)
            {
                Console.WriteLine("[TModLoaderPatch] Microsoft.Xna.Framework.Game class not found in FNA assembly.");
                return;
            }

            // Harmony instance lazy loading
            Harmony harmony = _harmony!;

            // 获取原始 Run 方法
            MethodInfo? original = AccessTools.Method(gameType, "Run");

            if (original == null)
            {
                Console.WriteLine("[TModLoaderPatch] Game.Run method not found.");
                return;
            }

            // 创建一个前置钩子
            HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(IsFullScreenPatch));

            // 应用 Patch
            harmony.Patch(original, prefix: prefix);

            Console.WriteLine("[TModLoaderPatch] Run Prefix applied!");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] Error applying FullScreen patch: {ex.Message}");
            Console.WriteLine($"[TModLoaderPatch] Stack: {ex.StackTrace}");
        }
    }

    /// <summary>
    /// Prefix 补丁 - 在 Game.Run 方法执行前设置全屏
    /// </summary>
    public static void IsFullScreenPatch()
    {
        try
        {
            // 获取 Terraria.Main 类型
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");
            
            if (tModLoaderAssembly == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] tModLoader assembly not found, cannot apply fullscreen settings.");
                return;
            }

            Type? mainType = tModLoaderAssembly.GetType("Terraria.Main");

            if (mainType == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] Terraria.Main class not found.");
                return;
            }

            // 获取 screenMaximized 静态字段
            var screenMaximizedField = mainType.GetField("screenMaximized", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (screenMaximizedField != null)
            {
                screenMaximizedField.SetValue(null, true);
                Console.WriteLine("[TModLoaderPatch] Terraria.Main.screenMaximized set to true");
            }
            else
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] screenMaximized field not found.");
            }

            // 获取 graphics 静态字段
            var graphicsField = mainType.GetField("graphics", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (graphicsField == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] graphics field not found.");
                return;
            }

            object? graphicsManager = graphicsField.GetValue(null);
            if (graphicsManager == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] graphics field is null.");
                return;
            }

            Type graphicsType = graphicsManager.GetType();
            Console.WriteLine($"[TModLoaderPatch] Graphics type: {graphicsType.FullName}");

            // 尝试获取 IsFullScreen 属性
            var isFullScreenProperty = graphicsType.GetProperty("IsFullScreen", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            if (isFullScreenProperty != null)
            {
                isFullScreenProperty.SetValue(graphicsManager, true);
                Console.WriteLine("[TModLoaderPatch] Terraria.Main.graphics.IsFullScreen set to true");
            }
            else
            {
                // 如果属性不存在，尝试作为字段
                var isFullScreenField = graphicsType.GetField("IsFullScreen", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
                if (isFullScreenField != null)
                {
                    isFullScreenField.SetValue(graphicsManager, true);
                    Console.WriteLine("[TModLoaderPatch] Terraria.Main.graphics.IsFullScreen (field) set to true");
                }
                else
                {
                    Console.WriteLine("[TModLoaderPatch] [WARN] IsFullScreen property/field not found in GraphicsDeviceManager.");
                    Console.WriteLine($"[TModLoaderPatch] Available properties: {string.Join(", ", graphicsType.GetProperties(BindingFlags.Instance | BindingFlags.Public).Select(p => p.Name))}");
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] Error in IsFullScreenPatch: {ex.Message}");
            Console.WriteLine($"[TModLoaderPatch] Stack: {ex.StackTrace}");
        }
    }
    public static void LoggingHooksHarmonyPatch(Assembly assembly)
    {
        // Get the type for LoggingHooks from the external assembly
        Type? loggingHooksType = assembly.GetType("Terraria.ModLoader.Engine.LoggingHooks");

        if (loggingHooksType == null)
        {
            Console.WriteLine("[TModLoaderPatch] LoggingHooks class not found in the external assembly.");
            return;
        }

        // Get the MethodInfo for the method you want to patch
        MethodInfo? originalMethod = loggingHooksType.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic);

        if (originalMethod == null)
        {
            Console.WriteLine("[TModLoaderPatch] Init method not found in LoggingHooks.");
            return;
        }
        
        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the prefix
        HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(LoggingPatch_Prefix));

        // Apply the patch
        harmony.Patch(originalMethod, prefix: prefix);

        Console.WriteLine("[TModLoaderPatch] LoggingHooks patch applied successfully!");
    }

    public static bool LoggingPatch_Prefix()
    {
        Console.WriteLine("[TModLoaderPatch] LoggingHooks.Init method is now a no-op.");
        return false; // Skip the original method
    }

    public static void TMLContentManagerPatch(Assembly assembly)
    {
        // Get the type for TMLContentManager from the external assembly
        Type? tmlContentManagerType = assembly.GetType("Terraria.ModLoader.Engine.TMLContentManager");

        if (tmlContentManagerType == null)
        {
            Console.WriteLine("[TModLoaderPatch] TMLContentManager class not found in the external assembly.");
            return;
        }

        // Get the MethodInfo for the method you want to patch
        MethodInfo? originalMethod = tmlContentManagerType.GetMethod("TryFixFileCasings", BindingFlags.Static | BindingFlags.NonPublic);

        if (originalMethod == null)
        {
            Console.WriteLine("[TModLoaderPatch] TryFixFileCasings method not found in TMLContentManager.");
            return;
        }

        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the transpiler
        HarmonyMethod transpiler = new HarmonyMethod(typeof(Patcher), nameof(TMLContentManagerPatch_Transpiler));

        // Apply the patch
        harmony.Patch(originalMethod, transpiler: transpiler);

        Console.WriteLine("[TModLoaderPatch] TMLContentManager patch applied successfully!");
    }
    
    public static IEnumerable<CodeInstruction> TMLContentManagerPatch_Transpiler(IEnumerable<CodeInstruction> instructions)
    {
        Console.WriteLine("[TModLoaderPatch] TMLContentManager.TryFixFileCasings modifying IL...");
        var codeMatcher = new CodeMatcher(instructions);

        codeMatcher
            .MatchStartForward(
                new CodeMatch(OpCodes.Ldloc_S),
                new CodeMatch(OpCodes.Callvirt, AccessTools.Method(typeof(FileSystemInfo), "get_Exists")),
                new CodeMatch(OpCodes.Brfalse_S))
            .ThrowIfInvalid("Could not find pattern")
            .RemoveInstructions(2)
            .InsertAndAdvance(new CodeInstruction(OpCodes.Ldc_I4_0));

        Console.WriteLine("[TModLoaderPatch] TMLContentManager.TryFixFileCasings modified IL!");
        
        return codeMatcher.InstructionEnumeration();
    }
    
    public static void InstallVerifierBugMitigation(Assembly assembly)
    {
        // Get the type for InstallVerifier from the external assembly
        Type? installVerifierType = assembly.GetType("Terraria.ModLoader.Engine.InstallVerifier");

        if (installVerifierType == null)
        {
            Console.WriteLine("[TModLoaderPatch] InstallVerifier class not found in the external assembly.");
            return;
        }

        // Get the fields which are not properly initialized
        var steamAPIPath = installVerifierType.GetField("steamAPIPath", BindingFlags.Static |  BindingFlags.NonPublic);
        var steamAPIHash = installVerifierType.GetField("steamAPIHash", BindingFlags.Static |  BindingFlags.NonPublic);
        var vanillaSteamAPI = installVerifierType.GetField("vanillaSteamAPI", BindingFlags.Static | BindingFlags.NonPublic);
        var gogHash =  installVerifierType.GetField("gogHash", BindingFlags.Static | BindingFlags.NonPublic);
        var steamHash =  installVerifierType.GetField("steamHash", BindingFlags.Static | BindingFlags.NonPublic);

        var IsSteamUnsupported =
            installVerifierType.GetProperty("IsSteamUnsupported", BindingFlags.Static | BindingFlags.NonPublic);

        if (steamAPIPath == null ||
            steamAPIHash == null ||
            vanillaSteamAPI == null ||
            gogHash == null ||
            steamHash == null)
        {
            Console.WriteLine("[TModLoaderPatch] [WARN] some fields not found in InstallVerifier class.");
        }

        if (IsSteamUnsupported == null)
        {
            Console.WriteLine("[TModLoaderPatch] [WARN] IsSteamUnsupported not found in InstallVerifier class. It is normal when lower version of tModLoader is loaded");
        }
        
        steamAPIPath?.SetValue(null, "libsteam_api.so");
        steamAPIHash?.SetValue(null, Convert.FromHexString("4b7a8cabaa354fcd25743aabfb4b1366"));
        vanillaSteamAPI?.SetValue(null, "libsteam_api.so");
        gogHash?.SetValue(null, Convert.FromHexString("9db40ef7cd4b37794cfe29e8866bb6b4"));
        steamHash?.SetValue(null, Convert.FromHexString("2ff21c600897a9485ca5ae645a06202d"));
        
        IsSteamUnsupported?.SetValue(null, false);
        
        Console.WriteLine("[TModLoaderPatch] InstallVerifier class mitigations applied successfully!");
    }

    /// <summary>
    /// 隐藏鼠标光标补丁 - 根据环境变量或设置隐藏鼠标光标
    /// 参考 PojavLauncher：PojavLauncher 使用 LWJGL，通过 glfwSetInputMode 处理光标状态
    /// 对于 FNA/XNA 游戏，直接设置 Game.IsMouseVisible = false
    /// </summary>
    public static void HideCursorPatch(Assembly assembly)
    {
        try
        {
            // 检查环境变量
            string? hideCursorEnv = Environment.GetEnvironmentVariable("RALCORE_HIDE_CURSOR");
            bool shouldHide = hideCursorEnv == "1" || hideCursorEnv == "true";

            if (!shouldHide)
            {
                Console.WriteLine("[TModLoaderPatch] Hide cursor not enabled (RALCORE_HIDE_CURSOR not set)");
                return;
            }

            Console.WriteLine("[TModLoaderPatch] Hide cursor enabled, applying patch...");

            // 获取 FNA 程序集
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var FNAAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "FNA");

            if (FNAAssembly == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] FNA assembly not found for hide cursor patch.");
                return;
            }

            // 从 FNA 程序集中获取 Game 类型
            Type? gameType = FNAAssembly.GetType("Microsoft.Xna.Framework.Game");

            if (gameType == null)
            {
                Console.WriteLine("[TModLoaderPatch] Microsoft.Xna.Framework.Game class not found in FNA assembly.");
                return;
            }

            // 获取 IsMouseVisible 属性
            var isMouseVisibleProperty = gameType.GetProperty("IsMouseVisible", BindingFlags.Instance | BindingFlags.Public);

            if (isMouseVisibleProperty == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] Game.IsMouseVisible property not found.");
                return;
            }

            // 获取 Terraria.Main 类型
            Type? mainType = assembly.GetType("Terraria.Main");

            if (mainType == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] Terraria.Main class not found.");
                return;
            }

            // 获取 game 静态字段
            var gameField = mainType.GetField("game", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);

            if (gameField == null)
            {
                Console.WriteLine("[TModLoaderPatch] [WARN] Terraria.Main.game field not found.");
                return;
            }

            // 方法1：尝试在 Main.Initialize 后设置
            MethodInfo? initializeMethod = mainType.GetMethod("Initialize", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (initializeMethod != null)
            {
                Harmony harmony = _harmony!;
                HarmonyMethod postfix = new HarmonyMethod(typeof(Patcher), nameof(HideCursorPatch_Postfix));
                harmony.Patch(initializeMethod, postfix: postfix);
                Console.WriteLine("[TModLoaderPatch] Hide cursor patch applied to Main.Initialize!");
            }
            
            // 方法2：尝试在 Main.LoadContent 后设置（作为备用）
            MethodInfo? loadContentMethod = mainType.GetMethod("LoadContent", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (loadContentMethod != null)
            {
                Harmony harmony = _harmony!;
                HarmonyMethod postfix = new HarmonyMethod(typeof(Patcher), nameof(HideCursorPatch_Postfix));
                harmony.Patch(loadContentMethod, postfix: postfix);
                Console.WriteLine("[TModLoaderPatch] Hide cursor patch applied to Main.LoadContent!");
            }

            // 方法3：尝试在 Main.DoUpdate 中持续设置（确保即使游戏重新设置也能保持隐藏）
            MethodInfo? doUpdateMethod = mainType.GetMethod("DoUpdate", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (doUpdateMethod != null)
            {
                Harmony harmony = _harmony!;
                HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(HideCursorPatch_DoUpdatePrefix));
                harmony.Patch(doUpdateMethod, prefix: prefix);
                Console.WriteLine("[TModLoaderPatch] Hide cursor patch applied to Main.DoUpdate!");
            }

            Console.WriteLine("[TModLoaderPatch] Hide cursor patch applied successfully!");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] Error applying hide cursor patch: {ex.Message}");
            Console.WriteLine($"[TModLoaderPatch] Stack: {ex.StackTrace}");
        }
    }

    /// <summary>
    /// Postfix 补丁 - 在 Main.Initialize 或 LoadContent 后隐藏鼠标光标
    /// </summary>
    public static void HideCursorPatch_Postfix()
    {
        SetMouseVisible(false);
    }

    /// <summary>
    /// Prefix 补丁 - 在 Main.DoUpdate 中持续确保鼠标光标隐藏
    /// </summary>
    public static void HideCursorPatch_DoUpdatePrefix()
    {
        SetMouseVisible(false);
    }

    /// <summary>
    /// 设置鼠标光标可见性的辅助方法
    /// </summary>
    private static void SetMouseVisible(bool visible)
    {
        try
        {
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");
            var FNAAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "FNA");

            if (tModLoaderAssembly == null || FNAAssembly == null)
            {
                return;
            }

            Type? mainType = tModLoaderAssembly.GetType("Terraria.Main");
            Type? gameType = FNAAssembly.GetType("Microsoft.Xna.Framework.Game");

            if (mainType == null || gameType == null)
            {
                return;
            }

            var gameField = mainType.GetField("game", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            var isMouseVisibleProperty = gameType.GetProperty("IsMouseVisible", BindingFlags.Instance | BindingFlags.Public);

            if (gameField != null && isMouseVisibleProperty != null)
            {
                object? gameInstance = gameField.GetValue(null);
                if (gameInstance != null)
                {
                    bool currentValue = (bool)(isMouseVisibleProperty.GetValue(gameInstance) ?? true);
                    if (currentValue != !visible) // 如果需要隐藏但当前可见，或需要显示但当前隐藏
                    {
                        isMouseVisibleProperty.SetValue(gameInstance, !visible);
                        if (!visible)
                        {
                            Console.WriteLine("[TModLoaderPatch] Game.IsMouseVisible set to false");
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // 静默失败，避免日志刷屏
        }
    }
}