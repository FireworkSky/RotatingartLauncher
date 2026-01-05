using HarmonyLib;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;

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
            
                ApplyPatchesInternal(tModLoaderAssembly);
                
            }
            else
            {
               
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
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
        InstallVerifierBugMitigation(assembly);
        LoggingHooksHarmonyPatch(assembly);
        TMLContentManagerPatch(assembly);
        SetResolveNativeLibraryHandler(assembly);
    }

    public static void LoggingHooksHarmonyPatch(Assembly assembly)
    {
        // Get the type for LoggingHooks from the external assembly
        Type? loggingHooksType = assembly.GetType("Terraria.ModLoader.Engine.LoggingHooks");

        // Get the MethodInfo for the method you want to patch
        MethodInfo? originalMethod = loggingHooksType?.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic);
        
        if (originalMethod == null)
        {
            Console.WriteLine("[TModLoaderPatch] LoggingHooks.Init method not found");
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

       
        // Get the MethodInfo for the method you want to patch
        MethodInfo? originalMethod = tmlContentManagerType?.GetMethod("TryFixFileCasings", BindingFlags.Static | BindingFlags.NonPublic);

        if (originalMethod == null)
        {
            Console.WriteLine("[TModLoaderPatch] TMLContentManager.TryFixFileCasings method not found");
            return;
        }

        // Harmony instance lazy loading
        Harmony harmony = _harmony!;

        // Create the HarmonyMethod for the prefix
        HarmonyMethod prefix = new HarmonyMethod(typeof(Patcher), nameof(TMLContentManagerPatch_Prefix));

        // Apply the patch
        harmony.Patch(originalMethod, prefix: prefix);

        Console.WriteLine("[TModLoaderPatch] TMLContentManager patch applied successfully!");
    }
    
    public static void TMLContentManagerPatch_Prefix(string rootDirectory)
    {
	    // The file listed below will be checked and fixed for case on disk
		// this method does not work on UNC paths (don't think remote path Terraria
		// installs will be present in a long time, but good to keep this logged)
		// and will only find/change FILE case, not all the directory tree.
		// A full implementation for search of actual name can be found at:
		// https://stackoverflow.com/questions/325931/getting-actual-file-name-with-proper-casing-on-windows-with-net
		string[] problematicAssets = {
			"Images/NPC_517.xnb",
			"Images/Gore_240.xnb",
			"Images/Projectile_179.xnb",
			"Images/Projectile_189.xnb",
			"Images/Projectile_618.xnb",
			"Images/Tiles_650.xnb",
			"Images/Item_2648.xnb"
		};

		foreach (string problematicAsset in problematicAssets)
		{
			string expectedName = Path.GetFileName(problematicAsset);
			string expectedFullPath = Path.Combine(rootDirectory, problematicAsset);
			var faultyAssetInfo = new FileInfo(Path.Combine(rootDirectory, problematicAsset));

			string actualFullPath;

			// // If the file exists - double-check its returned path, we may be in a case-insensitive filesystem.
			// if (faultyAssetInfo.Exists) {
			// 	// This assetInfo is correct cased (but only the name, need recursive if you want full case,
			// 	// nothing more is needed in this case though
			// 	var assetInfo = faultyAssetInfo.Directory.EnumerateFileSystemInfos(faultyAssetInfo.Name).First();
			//
			// 	if (expectedName == assetInfo.Name) {
			// 		continue;
			// 	}
			//
			// 	actualFullPath = assetInfo.FullName;
			// }
			// // If it's missing - search for it while ignoring case, we're likely in a case-sensitive filesystem.
			// else {
			// 	var assetInfo = faultyAssetInfo.Directory.EnumerateFileSystemInfos().FirstOrDefault(p => p.Name.Equals(expectedName, StringComparison.InvariantCultureIgnoreCase));
			//
			// 	if (assetInfo == null) {
			// 		Console.WriteLine($"An expected vanilla asset is missing: (from {rootDirectory}) {problematicAsset}");
			// 		continue;
			// 	}
			//
			// 	actualFullPath = assetInfo.FullName;
			// }

			// Android is case-sensitive while reading, but case-insensitive while writing, dang.....
			{
				var assetInfo = faultyAssetInfo.Directory.EnumerateFileSystemInfos().FirstOrDefault(p =>
					p.Name.Equals(expectedName, StringComparison.InvariantCultureIgnoreCase));

				if (assetInfo == null)
				{
					Console.WriteLine(
						$"An expected vanilla asset is missing: (from {rootDirectory}) {problematicAsset}");
					continue;
				}

				actualFullPath = assetInfo.FullName;
			}

			// The asset is wrongfully cased, fix that,
			// changing a vanilla file name is something to log for sure
			string relativeActualPath = Path.GetRelativePath(rootDirectory, actualFullPath);

			Console.WriteLine(
				$"Found vanilla asset with wrong case, renaming: (from {rootDirectory}) {relativeActualPath} -> {problematicAsset}");
			// // Programmatically move with different case works
			// File.Move(actualFullPath, expectedFullPath);
			// Dang android
			File.Move(actualFullPath, actualFullPath + ".1");
			File.Move(actualFullPath + ".1", expectedFullPath);
		}
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

        steamAPIPath?.SetValue(null, "libsteam_api.so");
        steamAPIHash?.SetValue(null, Convert.FromHexString("4b7a8cabaa354fcd25743aabfb4b1366"));
        vanillaSteamAPI?.SetValue(null, "libsteam_api.so");
        gogHash?.SetValue(null, Convert.FromHexString("9db40ef7cd4b37794cfe29e8866bb6b4"));
        steamHash?.SetValue(null, Convert.FromHexString("2ff21c600897a9485ca5ae645a06202d"));
        
        IsSteamUnsupported?.SetValue(null, false);
        
        Console.WriteLine("[TModLoaderPatch] InstallVerifier class mitigations applied successfully!");
    }
    
    public static void SetResolveNativeLibraryHandler(Assembly assembly)
    {
	    AssemblyLoadContext.Default.ResolvingUnmanagedDll += ResolveNativeLibrary;
    }

    private static IntPtr ResolveNativeLibrary(Assembly assembly, string name)
    {
        // On some devices, dlopen cant be caught?????
	    if (name.StartsWith("steam_api")) {
		    Console.WriteLine("Handling steam_api.so loading, fast throw DllNotFoundException");
		    throw new DllNotFoundException(
			    $"Unable to load shared library \"{name}\" or one of its dependencies. Handled by TModLoaderPatch of RotatingArtLauncher");
	    }

	    return IntPtr.Zero;
    }
}