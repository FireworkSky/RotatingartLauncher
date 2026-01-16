using HarmonyLib;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;

namespace EverestPatch;

/// <summary>
/// Everest 补丁程序集
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
            Console.WriteLine("[EverestPatch] Initializing Android fix patch...");
            Console.WriteLine("========================================");


            // 应用 Harmony 补丁
            ApplyHarmonyPatches();

            Console.WriteLine("[EverestPatch] Patch initialized successfully");
            Console.WriteLine("========================================");

            return 0; // 成功
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EverestPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[EverestPatch] Stack: {ex.StackTrace}");
            return -1; // 失败
        }
    }

   

    /// <summary>
    /// 应用 Harmony 补丁
    /// </summary>
    private static void ApplyHarmonyPatches()
    {
        try
        {
            _harmony = new Harmony("com.ralaunch.everest.patch");
            
            // 从已加载的程序集中查找 celeste
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var miniInstallerAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "Celeste");

            if (miniInstallerAssembly != null)
            {
            
                ApplyPatchesInternal(miniInstallerAssembly);
                
            }
            else
            {
               
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EverestPatch] Failed to apply Harmony patches: {ex.Message}");
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

            // 检查是否是 Celeste 程序集
            if (assemblyName == "Celeste")
            {
                ApplyPatchesInternal(args.LoadedAssembly);
                
                // 移除事件监听器，避免重复处理
                AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EverestPatch] Error in OnAssemblyLoaded: {ex.Message}");
            Console.WriteLine($"[EverestPatch] Stack: {ex.StackTrace}");
        }
    }
    
    /// <summary>
    /// 内部补丁应用方法
    /// </summary>
    private static void ApplyPatchesInternal(Assembly assembly)
    {
        CelesteModBOOTSetupNativeLibPathsHarmonyPatch(assembly);
    }

    public static void CelesteModBOOTSetupNativeLibPathsHarmonyPatch(Assembly assembly)
    {
        var bootType = assembly.GetType("Celeste.Mod.BOOT");
        
        if (bootType == null)
        {
            Console.WriteLine("[EverestPatch] Celeste.Mod.BOOT type not found");
            return;
        }
        
        // Get the MethodInfo for the method you want to patch
        var originalMethod = bootType.GetMethod("SetupNativeLibPaths", BindingFlags.Static | BindingFlags.NonPublic);
        
        if (originalMethod == null)
        {
            Console.WriteLine("[EverestPatch] BOOT.SetupNativeLibPaths method not found");
            return;
        }
        
        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the prefix
        HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(CelesteModBOOTSetupNativeLibPaths_Prefix));

        // Apply the patch
        harmony.Patch(originalMethod, prefix: prefix);

        Console.WriteLine("[EverestPatch] BOOT.SetupNativeLibPaths patch applied successfully!");
    }

    public static bool CelesteModBOOTSetupNativeLibPaths_Prefix()
    {
        Console.WriteLine("[EverestPatch] BOOT.SetupNativeLibPaths method is now a no-op.");
        return false; // Skip the original method
    }
}