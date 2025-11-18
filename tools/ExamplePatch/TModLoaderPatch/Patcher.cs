using System;
using System.IO;
using System.Reflection;
using HarmonyLib;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// 此类必须在全局命名空间（没有命名空间）
/// 会在应用程序 Main() 方法之前自动执行
/// </summary>
internal class StartupHook
{
    /// <summary>
    /// DOTNET_STARTUP_HOOKS 要求的初始化方法
    /// 无参数,返回 void
    /// </summary>
    public static void Initialize()
    {
        Console.WriteLine("[StartupHook] DOTNET_STARTUP_HOOKS executing...");

        // 注册程序集解析器,从补丁 DLL 同目录加载依赖
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用原有的补丁初始化方法
        int result = TModLoaderPatch.Patcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] Patcher.Initialize returned: {result}");
    }

    /// <summary>
    /// 程序集解析器 - 从 patches 根目录加载共享依赖程序集
    /// </summary>
    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            // 获取请求的程序集名称(不含版本等信息)
            string assemblyName = new AssemblyName(args.Name).Name ?? "";

            // 获取当前补丁 DLL 所在目录
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";

            // 获取 patches 根目录 (补丁 DLL 的父目录)
            string patchesRootDir = Path.GetDirectoryName(patchDir) ?? "";

            // 优先从 patches 根目录加载共享依赖
            string sharedAssemblyPath = Path.Combine(patchesRootDir, assemblyName + ".dll");
            if (File.Exists(sharedAssemblyPath))
            {
                Console.WriteLine($"[StartupHook] Loading shared dependency: {assemblyName} from {sharedAssemblyPath}");
                return Assembly.LoadFrom(sharedAssemblyPath);
            }

            // 备选: 从补丁自己的目录加载
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[StartupHook] Loading local dependency: {assemblyName} from {localAssemblyPath}");
                return Assembly.LoadFrom(localAssemblyPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[StartupHook] Failed to resolve assembly {args.Name}: {ex.Message}");
        }

        return null;
    }
}

namespace TModLoaderPatch
{
    /// <summary>
    /// tModLoader 补丁程序集
    /// 修复 InstallVerifier 在 Android/ARM64 平台上的 vanillaSteamAPI 为 null 导致的异常
    /// </summary>
    public class Patcher
    {
        private static Harmony? _harmony;

        /// <summary>
        /// 补丁初始化方法
        /// 会在游戏程序集加载前被自动调用
        /// </summary>
        public static int Initialize(IntPtr arg, int argSize)
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

                // 监听程序集加载事件，在 tModLoader 程序集加载后应用补丁
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;

                Console.WriteLine("[TModLoaderPatch] Harmony patches registered");
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
                    Console.WriteLine("[TModLoaderPatch] tModLoader assembly loaded, applying patches...");

                    // 获取 InstallVerifier 类型
                    var installVerifierType = args.LoadedAssembly.GetType("Terraria.ModLoader.Engine.InstallVerifier");
                    if (installVerifierType != null)
                    {
                        // 修复 IsSteamUnsupported 字段（在旧版本中不存在，导致后续字段未初始化）
                        var isSteamUnsupportedField = installVerifierType.GetField("IsSteamUnsupported",
                            BindingFlags.NonPublic | BindingFlags.Static);

                        if (isSteamUnsupportedField == null)
                        {
                            // 旧版本没有 IsSteamUnsupported 字段，需要手动初始化其他字段
                            Console.WriteLine("[TModLoaderPatch] Old version detected (no IsSteamUnsupported field)");

                            // 修复 vanillaSteamAPI 字段为 null 的问题
                            var vanillaSteamAPIField = installVerifierType.GetField("vanillaSteamAPI",
                                BindingFlags.NonPublic | BindingFlags.Static);

                            if (vanillaSteamAPIField != null)
                            {
                                var currentValue = vanillaSteamAPIField.GetValue(null) as string;
                                if (string.IsNullOrEmpty(currentValue))
                                {
                                    // 为 Android/ARM64 设置默认值（Linux使用.so库）
                                    vanillaSteamAPIField.SetValue(null, "libsteam_api.so");
                                    Console.WriteLine("[TModLoaderPatch] ✓ Set vanillaSteamAPI = 'libsteam_api.so' (was null)");
                                }
                            }

                            // 修复其他可能为 null 的字段
                            var steamAPIPathField = installVerifierType.GetField("steamAPIPath",
                                BindingFlags.NonPublic | BindingFlags.Static);
                            if (steamAPIPathField != null)
                            {
                                var currentValue = steamAPIPathField.GetValue(null) as string;
                                if (string.IsNullOrEmpty(currentValue))
                                {
                                    steamAPIPathField.SetValue(null, "libsteam_api.so");
                                    Console.WriteLine("[TModLoaderPatch] ✓ Set steamAPIPath = 'libsteam_api.so' (was null)");
                                }
                            }
                        }
                        else
                        {
                            Console.WriteLine("[TModLoaderPatch] New version detected (has IsSteamUnsupported field)");
                        }
                    }
                    else
                    {
                        Console.WriteLine("[TModLoaderPatch] WARNING: InstallVerifier type not found");
                    }

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
    }

}
