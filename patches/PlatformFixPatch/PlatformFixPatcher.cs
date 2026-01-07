using System;
using System.Linq;
using System.Reflection;
using HarmonyLib;

namespace PlatformFixPatch;

/// <summary>
/// Android 平台修复补丁
/// 补丁 SMAPI 的 LogManager.PressAnyKeyToExit 方法，阻止平台验证退出
/// </summary>
public static class PlatformFixPatcher
{
    private static Harmony? _harmony;
    private static bool _patched = false;

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[PlatformFixPatch] Android Platform Fix");
            Console.WriteLine("========================================");

            _harmony = new Harmony("com.ralaunch.platformfixpatch");

            // 先从已加载的程序集中查找
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var smapiAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "StardewModdingAPI");

            if (smapiAssembly != null)
            {
                Console.WriteLine("[PlatformFixPatch] Found StardewModdingAPI in loaded assemblies");
                ApplyPatches(smapiAssembly);
            }
            else
            {
                Console.WriteLine("[PlatformFixPatch] Waiting for StardewModdingAPI to load...");
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
            }

            Console.WriteLine("[PlatformFixPatch] Ready");
            Console.WriteLine("========================================");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[PlatformFixPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[PlatformFixPatch] Stack: {ex.StackTrace}");
            return -1;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        try
        {
            var assemblyName = args.LoadedAssembly.GetName().Name;

            if (assemblyName == "StardewModdingAPI" && !_patched)
            {
                Console.WriteLine("[PlatformFixPatch] StardewModdingAPI loaded");
                ApplyPatches(args.LoadedAssembly);
                AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[PlatformFixPatch] Error in OnAssemblyLoaded: {ex.Message}");
        }
    }

    private static void ApplyPatches(Assembly smapiAssembly)
    {
        if (_patched) return;
        
        try
        {
            // 补丁 LogManager.PressAnyKeyToExit(bool)
            var logManagerType = smapiAssembly.GetType("StardewModdingAPI.Framework.Logging.LogManager");
            
            if (logManagerType == null)
            {
                Console.WriteLine("[PlatformFixPatch] LogManager type not found");
                return;
            }

            // 补丁有参数的版本 PressAnyKeyToExit(bool showMessage)
            var pressAnyKeyMethod = logManagerType.GetMethod(
                "PressAnyKeyToExit",
                BindingFlags.Public | BindingFlags.Instance,
                null,
                new[] { typeof(bool) },
                null
            );

            if (pressAnyKeyMethod != null)
            {
                _harmony!.Patch(
                    pressAnyKeyMethod,
                    prefix: new HarmonyMethod(typeof(PlatformFixPatcher), nameof(PressAnyKeyToExit_Prefix))
                );
                Console.WriteLine("[PlatformFixPatch] LogManager.PressAnyKeyToExit(bool) patched!");
            }

            // 也补丁无参数版本 PressAnyKeyToExit()
            var pressAnyKeyMethodNoParam = logManagerType.GetMethod(
                "PressAnyKeyToExit",
                BindingFlags.Public | BindingFlags.Instance,
                null,
                Type.EmptyTypes,
                null
            );

            if (pressAnyKeyMethodNoParam != null)
            {
                _harmony!.Patch(
                    pressAnyKeyMethodNoParam,
                    prefix: new HarmonyMethod(typeof(PlatformFixPatcher), nameof(PressAnyKeyToExit_Prefix))
                );
                Console.WriteLine("[PlatformFixPatch] LogManager.PressAnyKeyToExit() patched!");
            }

            _patched = true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[PlatformFixPatch] Patch failed: {ex.Message}");
            Console.WriteLine($"[PlatformFixPatch] Stack: {ex.StackTrace}");
        }
    }

    /// <summary>
    /// 阻止 PressAnyKeyToExit 执行
    /// </summary>
    public static bool PressAnyKeyToExit_Prefix()
    {
        Console.WriteLine("[PlatformFixPatch] PressAnyKeyToExit blocked! Continuing...");
        return false; // 跳过原方法，不退出
    }
}
