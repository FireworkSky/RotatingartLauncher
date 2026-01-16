using HarmonyLib;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;

namespace EverestMiniInstallerPatch;

/// <summary>
/// MiniInstaller 补丁程序集
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
            Console.WriteLine("[EverestMiniInstallerPatch] Initializing Android fix patch...");
            Console.WriteLine("========================================");


            // 应用 Harmony 补丁
            ApplyHarmonyPatches();

            Console.WriteLine("[EverestMiniInstallerPatch] Patch initialized successfully");
            Console.WriteLine("========================================");

            return 0; // 成功
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EverestMiniInstallerPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[EverestMiniInstallerPatch] Stack: {ex.StackTrace}");
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
            _harmony = new Harmony("com.ralaunch.everest.miniinstaller.patch");
            
            // 从已加载的程序集中查找 MiniInstaller
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var miniInstallerAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "MiniInstaller");

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
            Console.WriteLine($"[EverestMiniInstallerPatch] Failed to apply Harmony patches: {ex.Message}");
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

            // 检查是否是 MiniInstaller 程序集
            if (assemblyName == "MiniInstaller")
            {
                ApplyPatchesInternal(args.LoadedAssembly);
                
                // 移除事件监听器，避免重复处理
                AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EverestMiniInstallerPatch] Error in OnAssemblyLoaded: {ex.Message}");
            Console.WriteLine($"[EverestMiniInstallerPatch] Stack: {ex.StackTrace}");
        }
    }
    
    /// <summary>
    /// 内部补丁应用方法
    /// </summary>
    private static void ApplyPatchesInternal(Assembly assembly)
    {
        DirectoryCreateSymbolicLinkHarmonyPatch(assembly);
        FileCreateSymbolicLinkHarmonyPatch(assembly);
    }

    public static void DirectoryCreateSymbolicLinkHarmonyPatch(Assembly assembly)
    {
        // Get the MethodInfo for the method you want to patch
        var originalMethod = typeof(Directory).GetMethod("CreateSymbolicLink", BindingFlags.Static | BindingFlags.Public);
        
        if (originalMethod == null)
        {
            Console.WriteLine("[EverestMiniInstallerPatch] Directory.CreateSymbolicLink method not found");
            return;
        }
        
        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the prefix
        HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(DirectoryCreateSymbolicLink_Prefix));

        // Apply the patch
        harmony.Patch(originalMethod, prefix: prefix);

        Console.WriteLine("[EverestMiniInstallerPatch] Directory.CreateSymbolicLink patch applied successfully!");
    }

    public static bool DirectoryCreateSymbolicLink_Prefix(ref string path, ref string pathToTarget)
    {
        Console.WriteLine("[EverestMiniInstallerPatch] Directory.CreateSymbolicLink method is now a no-op.");
        return false; // Skip the original method
    }
    
    public static void FileCreateSymbolicLinkHarmonyPatch(Assembly assembly)
    {
        // Get the MethodInfo for the method you want to patch
        var originalMethod = typeof(File).GetMethod("CreateSymbolicLink", BindingFlags.Static | BindingFlags.Public);
        
        if (originalMethod == null)
        {
            Console.WriteLine("[EverestMiniInstallerPatch] File.CreateSymbolicLink method not found");
            return;
        }
        
        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the prefix
        HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(FileCreateSymbolicLink_Prefix));

        // Apply the patch
        harmony.Patch(originalMethod, prefix: prefix);

        Console.WriteLine("[EverestMiniInstallerPatch] File.CreateSymbolicLink patch applied successfully!");
    }

    public static bool FileCreateSymbolicLink_Prefix(ref string path, ref string pathToTarget)
    {
        Console.WriteLine("[EverestMiniInstallerPatch] File.CreateSymbolicLink method is now a no-op.");
        return false; // Skip the original method
    }
}