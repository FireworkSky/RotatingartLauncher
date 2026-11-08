using HarmonyLib;
using System;
using System.Linq;
using System.Reflection;

namespace TModLoaderTouchPatch;

/// <summary>
/// 独立的 tModLoader 原生触屏输入补丁入口。
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("[TModLoaderTouchPatch] Initializing native touch input patch...");
            _harmony = new Harmony("com.ralaunch.tmodloader.touch");

            var loadedAssembly = AppDomain.CurrentDomain.GetAssemblies()
                .FirstOrDefault(assembly => assembly.GetName().Name == "tModLoader");

            if (loadedAssembly != null)
            {
                TouchInputPatch.Apply(_harmony, loadedAssembly);
            }
            else
            {
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
            }

            Console.WriteLine("[TModLoaderTouchPatch] Patch initialized successfully");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderTouchPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[TModLoaderTouchPatch] Stack: {ex.StackTrace}");
            return -1;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        if (args.LoadedAssembly.GetName().Name != "tModLoader" || _harmony == null)
        {
            return;
        }

        TouchInputPatch.Apply(_harmony, args.LoadedAssembly);
        AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
    }
}
