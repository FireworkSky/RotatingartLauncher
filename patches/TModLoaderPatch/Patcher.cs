using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using System.Runtime.InteropServices;
using HarmonyLib;

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
        
        Console.WriteLine("[TModLoaderPatch] All patches applied successfully!");
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
}