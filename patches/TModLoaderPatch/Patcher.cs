using HarmonyLib;
using System;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;
using System.Security.Cryptography;
using Terraria;

namespace TModLoaderPatch;

/// <summary>
/// tModLoader 补丁程序集
/// 修复 InstallVerifier 在 Android/ARM64 平台上的 vanillaSteamAPI 为 null 导致的异常
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;

    // tModLoader 程序集引用 (通过 public 类型 Terraria.Utils 获取)
    private static Assembly TmlAssembly => typeof(Utils).Assembly;

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
        LoggingHooksHarmonyPatch();
        TMLContentManagerPatch();
        SetResolveNativeLibraryHandler();
        OpenToURLPatch();
    }

    public static void LoggingHooksHarmonyPatch()
    {
        var method = TmlAssembly.GetType("Terraria.ModLoader.Engine.LoggingHooks")
            ?.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic);
        if (method == null)
        {
            Console.WriteLine("[TModLoaderPatch] LoggingHooks.Init method not found");
            return;
        }

        _harmony!.Patch(method, prefix: new HarmonyMethod(typeof(Patcher), nameof(LoggingPatch_Prefix)));
        Console.WriteLine("[TModLoaderPatch] LoggingHooks patch applied successfully!");
    }

    public static bool LoggingPatch_Prefix()
    {
        Console.WriteLine("[TModLoaderPatch] LoggingHooks.Init method is now a no-op.");
        return false;
    }

    public static void TMLContentManagerPatch()
    {
        var method = TmlAssembly.GetType("Terraria.ModLoader.Engine.TMLContentManager")
            ?.GetMethod("TryFixFileCasings", BindingFlags.Static | BindingFlags.NonPublic);
        if (method == null)
        {
            Console.WriteLine("[TModLoaderPatch] TMLContentManager.TryFixFileCasings method not found");
            return;
        }

        _harmony!.Patch(method, prefix: new HarmonyMethod(typeof(Patcher), nameof(TMLContentManagerPatch_Prefix)));
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
    
    // GOG Linux 已知的多个版本哈希 (不同 GOG 安装包/版本的 Terraria 二进制文件)
    private static readonly string[] GogLinuxHashes = {
        "9db40ef7cd4b37794cfe29e8866bb6b4",
        "c60b2ab7b63114be09765227e12875b0",
        "64bb377414ecb61fc44b85ccc5de5de8",
        "2e07a044a8f16a508c5360b37dffe893",
        "c35b269396a5f83722bd94d45163a735",
        "95d8e8b92868b301eeeef0518edab2ce"
    };

    public static void InstallVerifierBugMitigation(Assembly assembly)
    {
        var t = TmlAssembly.GetType("Terraria.ModLoader.Engine.InstallVerifier");
        if (t == null)
        {
            Console.WriteLine("[TModLoaderPatch] InstallVerifier class not found");
            return;
        }
        var flags = BindingFlags.Static | BindingFlags.NonPublic;

        // Get the fields which are not properly initialized on Linux ARM64
        var steamAPIPathField = t.GetField("steamAPIPath", flags);
        var steamAPIHashField = t.GetField("steamAPIHash", flags);
        var vanillaSteamAPIField = t.GetField("vanillaSteamAPI", flags);
        var gogHashField = t.GetField("gogHash", flags);
        var steamHashField = t.GetField("steamHash", flags);
        var isSteamUnsupportedField = t.GetField("IsSteamUnsupported", flags);

        Console.WriteLine($"[TModLoaderPatch] Found fields: steamAPIPath={steamAPIPathField != null}, steamAPIHash={steamAPIHashField != null}, " +
                          $"vanillaSteamAPI={vanillaSteamAPIField != null}, gogHash={gogHashField != null}, steamHash={steamHashField != null}, " +
                          $"IsSteamUnsupported={isSteamUnsupportedField != null}");

        // Set basic field values for Android/ARM64
        steamAPIPathField?.SetValue(null, "libsteam_api.so");
        steamAPIHashField?.SetValue(null, Convert.FromHexString("4b7a8cabaa354fcd25743aabfb4b1366"));
        vanillaSteamAPIField?.SetValue(null, "libsteam_api.so");
        gogHashField?.SetValue(null, Convert.FromHexString(GogLinuxHashes[0]));
        steamHashField?.SetValue(null, Convert.FromHexString("2ff21c600897a9485ca5ae645a06202d"));
        isSteamUnsupportedField?.SetValue(null, false);

        Console.WriteLine($"[TModLoaderPatch] Set default gogHash={GogLinuxHashes[0]}");

        // Harmony patch CheckGoG 方法, 使其支持多个 GOG 哈希
        var checkGoGMethod = t.GetMethod("CheckGoG", flags);
        if (checkGoGMethod != null)
        {
            _harmony!.Patch(checkGoGMethod, prefix: new HarmonyMethod(typeof(Patcher), nameof(CheckGoG_Prefix)));
            Console.WriteLine("[TModLoaderPatch] CheckGoG patched for multi-hash support!");
        }
        else
        {
            Console.WriteLine("[TModLoaderPatch] WARNING: CheckGoG method not found");
        }

        Console.WriteLine("[TModLoaderPatch] InstallVerifier mitigations applied!");
    }

    /// <summary>
    /// CheckGoG 前缀补丁 - 替换原始的单哈希检查为多哈希检查
    /// 原始逻辑: if (!HashMatchesFile(vanillaExePath, gogHash) &amp;&amp; !HashMatchesFile(vanillaExePath, steamHash)) → FatalExit
    /// 补丁逻辑: 检查文件哈希是否匹配任意一个已知的 GOG 哈希或 Steam 哈希
    /// </summary>
    public static bool CheckGoG_Prefix()
    {
        try
        {
            Console.WriteLine("[TModLoaderPatch] CheckGoG: Multi-hash verification...");

            var flags = BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public;
            var t = TmlAssembly.GetType("Terraria.ModLoader.Engine.InstallVerifier");
            var vanillaExePathField = t.GetField("vanillaExePath", flags);
            var steamHashField = t.GetField("steamHash", flags);
            var gogHashField = t.GetField("gogHash", flags);

            string? vanillaExePath = vanillaExePathField?.GetValue(null) as string;
            byte[]? steamHash = steamHashField?.GetValue(null) as byte[];

            if (string.IsNullOrEmpty(vanillaExePath) || !File.Exists(vanillaExePath))
            {
                Console.WriteLine($"[TModLoaderPatch] CheckGoG: vanillaExePath missing, skipping check");
                return false;
            }

            using var md5 = MD5.Create();
            using var stream = File.OpenRead(vanillaExePath);
            byte[] fileHash = md5.ComputeHash(stream);
            string fileHashHex = Convert.ToHexString(fileHash).ToLower();

            Console.WriteLine($"[TModLoaderPatch] CheckGoG: File={Path.GetFileName(vanillaExePath)}, Hash={fileHashHex}");

            foreach (var knownHash in GogLinuxHashes)
            {
                if (fileHashHex == knownHash)
                {
                    Console.WriteLine($"[TModLoaderPatch] CheckGoG: Matched GOG hash: {knownHash}");
                    gogHashField?.SetValue(null, Convert.FromHexString(knownHash));
                    return false;
                }
            }

            if (steamHash != null && fileHash.SequenceEqual(steamHash))
            {
                Console.WriteLine("[TModLoaderPatch] CheckGoG: Matched Steam hash");
                return false;
            }

            Console.WriteLine($"[TModLoaderPatch] CheckGoG WARNING: Unknown hash {fileHashHex}, allowing anyway");
            return false;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] CheckGoG error: {ex.Message}");
            return false;
        }
    }
    
    public static void SetResolveNativeLibraryHandler()
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

    // ============================================
    // OpenToURL 补丁 - 修复 Android 上 xdg-open 不存在导致的崩溃
    // ============================================

    /// <summary>
    /// 补丁所有使用 xdg-open 的方法 (Android 上不存在此命令)
    /// - Utils.OpenToURL: 打开 URL
    /// - Utils.OpenFolder: 打开文件夹
    /// </summary>
    public static void OpenToURLPatch()
    {
        try
        {
            // 补丁 OpenToURL (public)
            var openToURL = typeof(Utils).GetMethod("OpenToURL", BindingFlags.Static | BindingFlags.Public);
            if (openToURL != null)
            {
                _harmony!.Patch(openToURL, prefix: new HarmonyMethod(typeof(Patcher), nameof(OpenToURL_Prefix)));
                Console.WriteLine("[TModLoaderPatch] Utils.OpenToURL patched!");
            }

            // 补丁 OpenFolder (public) - 也使用 xdg-open
            var openFolder = typeof(Utils).GetMethod("OpenFolder", BindingFlags.Static | BindingFlags.Public);
            if (openFolder != null)
            {
                _harmony!.Patch(openFolder, prefix: new HarmonyMethod(typeof(Patcher), nameof(OpenFolder_Prefix)));
                Console.WriteLine("[TModLoaderPatch] Utils.OpenFolder patched!");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] xdg-open patch failed: {ex.Message}");
        }
    }

    public static bool OpenToURL_Prefix(string url)
    {
        try
        {
            Console.WriteLine($"[TModLoaderPatch] OpenToURL intercepted: {url}");
            int result = SDL2.SDL.SDL_OpenURL(url);
            Console.WriteLine($"[TModLoaderPatch] SDL_OpenURL result: {result}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] OpenToURL error: {ex.Message}");
        }
        return false; // 始终跳过原始方法
    }

    public static bool OpenFolder_Prefix(string folderPath)
    {
        try
        {
            Console.WriteLine($"[TModLoaderPatch] OpenFolder intercepted: {folderPath}");
            if (!Directory.Exists(folderPath))
                Directory.CreateDirectory(folderPath);

            // SDLActivity.openURL() 已修改为支持 file:// 文件夹路径
            // Java 层会自动转换为 content:// URI 并用 DocumentsProvider 打开
            int result = SDL2.SDL.SDL_OpenURL("file://" + folderPath);
            Console.WriteLine($"[TModLoaderPatch] SDL_OpenURL folder result: {result}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderPatch] OpenFolder error: {ex.Message}");
        }
        return false;
    }
}