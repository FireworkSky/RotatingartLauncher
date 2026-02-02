using HarmonyLib;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Runtime.Loader;

namespace SDL3Patch;

/// <summary>
/// SDL3 补丁 - 将 tModLoader 的 SDL2 调用适配到 SDL3
/// 
/// SDL2 → SDL3 主要 API 变化：
/// 1. SDL_VideoInit() → 已移除，不再需要
/// 2. SDL_GetDisplayDPI() → 已移除
/// 3. SDL_GetNumVideoDisplays() → SDL_GetDisplays()
/// 4. SDL_GetDisplayMode() → SDL_GetFullscreenDisplayModes()
/// 5. SDL_GetVersion() → 返回 int 而非结构体
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;
    private static readonly Dictionary<string, IntPtr> _loadedLibraries = new();
    private static IntPtr _sdlHandle = IntPtr.Zero;
    private static bool _initialized = false;
    private static bool _patchesApplied = false;

    // Android libc
    [DllImport("libc", EntryPoint = "dlopen")]
    private static extern IntPtr dlopen(string? filename, int flags);

    [DllImport("libc", EntryPoint = "dlsym")]
    private static extern IntPtr dlsym(IntPtr handle, string symbol);

    private const int RTLD_NOW = 2;
    private const int RTLD_NOLOAD = 4;

    /// <summary>
    /// 补丁初始化方法 - 由 StartupHook 调用
    /// </summary>
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (_initialized)
            return 0;

        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[SDL3Patch] Initializing SDL2 -> SDL3 API adapter...");
            Console.WriteLine("========================================");

            // 注册原生库解析处理器（最重要的部分）
            AssemblyLoadContext.Default.ResolvingUnmanagedDll += ResolveNativeLibrary;
            Console.WriteLine("[SDL3Patch] Native library resolver registered");

            // 尝试获取已加载的 SDL 库
            TryGetLoadedSDL();

            // 初始化 Harmony
            _harmony = new Harmony("com.ralaunch.sdl3patch");
            Console.WriteLine("[SDL3Patch] Harmony initialized");

            // 尝试立即应用补丁
            TryApplyPatches();

            // 注册程序集加载事件，以防程序集还未加载
            AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;

            _initialized = true;
            Console.WriteLine("[SDL3Patch] Initialization complete");
            Console.WriteLine("========================================");

            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SDL3Patch] ERROR during initialization: {ex.Message}");
            Console.WriteLine($"[SDL3Patch] Stack: {ex.StackTrace}");
            return -1;
        }
    }

    #region Harmony Patches

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        var name = args.LoadedAssembly.GetName().Name;
        Console.WriteLine($"[SDL3Patch] Assembly loaded: {name}");
        
        if (name == "tModLoader" || name == "FNA")
        {
            TryApplyPatches();
        }
    }

    private static void TryApplyPatches()
    {
        if (_patchesApplied) return;

        var assemblies = AppDomain.CurrentDomain.GetAssemblies();
        Console.WriteLine($"[SDL3Patch] Checking {assemblies.Length} loaded assemblies...");

        Assembly? tmodLoader = null;
        Assembly? fna = null;

        foreach (var asm in assemblies)
        {
            var name = asm.GetName().Name;
            if (name == "tModLoader") tmodLoader = asm;
            if (name == "FNA") fna = asm;
        }

        // 补丁 tModLoader
        if (tmodLoader != null)
        {
            Console.WriteLine("[SDL3Patch] Found tModLoader assembly, applying patches...");
            ApplyTModLoaderPatches(tmodLoader);
        }

        // 补丁 FNA 的 SDL2 P/Invoke
        if (fna != null)
        {
            Console.WriteLine("[SDL3Patch] Found FNA assembly, applying SDL2 compatibility patches...");
            ApplyFNAPatches(fna);
        }
    }

    private static void ApplyTModLoaderPatches(Assembly assembly)
    {
        try
        {
            // 补丁 Program.AttemptSupportHighDPI (包含 SDL_VideoInit 和 SDL_GetDisplayDPI)
            var programType = assembly.GetType("Terraria.Program");
            if (programType != null)
            {
                // 尝试多种可能的方法名
                string[] methodNames = { "AttemptSupportHighDPI", "SetHighDPIAware", "InitHighDPI" };
                foreach (var methodName in methodNames)
                {
                    var method = programType.GetMethod(methodName, 
                        BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
                    if (method != null)
                    {
                        var prefix = new HarmonyMethod(typeof(Patcher), nameof(AttemptSupportHighDPI_Prefix));
                        _harmony!.Patch(method, prefix: prefix);
                        Console.WriteLine($"[SDL3Patch] Patched {programType.Name}.{methodName}");
                        break;
                    }
                }
            }

            // 补丁 FNALogging.RedirectLogs (访问 internal FNA3D 类导致 MethodAccessException)
            var fnaLoggingType = assembly.GetType("Terraria.ModLoader.Engine.FNALogging");
            if (fnaLoggingType != null)
            {
                var redirectLogsMethod = fnaLoggingType.GetMethod("RedirectLogs", 
                    BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
                if (redirectLogsMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(Patcher), nameof(FNALogging_RedirectLogs_Prefix));
                    _harmony!.Patch(redirectLogsMethod, prefix: prefix);
                    Console.WriteLine("[SDL3Patch] Patched FNALogging.RedirectLogs");
                }

                // 也补丁 PostAudioInit (包含 SDL_GetCurrentAudioDriver)
                var postAudioInitMethod = fnaLoggingType.GetMethod("PostAudioInit", 
                    BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
                if (postAudioInitMethod != null)
                {
                    var prefix = new HarmonyMethod(typeof(Patcher), nameof(FNALogging_PostAudioInit_Prefix));
                    _harmony!.Patch(postAudioInitMethod, prefix: prefix);
                    Console.WriteLine("[SDL3Patch] Patched FNALogging.PostAudioInit");
                }
            }

            _patchesApplied = true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SDL3Patch] Error applying tModLoader patches: {ex.Message}");
        }
    }

    private static void ApplyFNAPatches(Assembly fnaAssembly)
    {
        try
        {
            // 补丁 SDL2.SDL 类中的关键方法
            var sdlType = fnaAssembly.GetType("SDL2.SDL");
            if (sdlType == null)
            {
                Console.WriteLine("[SDL3Patch] SDL2.SDL type not found in FNA");
                return;
            }

            // SDL_VideoInit - SDL3 中已移除
            PatchMethod(sdlType, "SDL_VideoInit", nameof(SDL_VideoInit_Prefix));

            // SDL_GetDisplayDPI - SDL3 中已移除
            PatchMethod(sdlType, "SDL_GetDisplayDPI", nameof(SDL_GetDisplayDPI_Prefix));

            // SDL_GetNumVideoDisplays - SDL3 中 API 变化
            PatchMethod(sdlType, "SDL_GetNumVideoDisplays", nameof(SDL_GetNumVideoDisplays_Prefix));

            // SDL_GetVersion - SDL3 中返回类型变化
            PatchMethod(sdlType, "SDL_GetVersion", nameof(SDL_GetVersion_Prefix));
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SDL3Patch] Error applying FNA patches: {ex.Message}");
        }
    }

    private static void PatchMethod(Type type, string methodName, string prefixMethodName)
    {
        try
        {
            var methods = type.GetMethods(BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic)
                .Where(m => m.Name == methodName);

            foreach (var method in methods)
            {
                var prefix = new HarmonyMethod(typeof(Patcher), prefixMethodName);
                _harmony!.Patch(method, prefix: prefix);
                Console.WriteLine($"[SDL3Patch] Patched {type.Name}.{methodName}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SDL3Patch] Failed to patch {methodName}: {ex.Message}");
        }
    }

    #endregion

    #region Harmony Prefix Methods

    /// <summary>
    /// 替换 AttemptSupportHighDPI - SDL3 不需要 SDL_VideoInit，SDL_GetDisplayDPI 已移除
    /// </summary>
    public static bool AttemptSupportHighDPI_Prefix()
    {
        Console.WriteLine("[SDL3Patch] AttemptSupportHighDPI intercepted - using SDL3 compatible implementation");

        try
        {
            // Android 设备默认启用高 DPI
            Environment.SetEnvironmentVariable("FNA_GRAPHICS_ENABLE_HIGHDPI", "1");
            Console.WriteLine("[SDL3Patch] High DPI mode enabled for Android");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SDL3Patch] AttemptSupportHighDPI error: {ex.Message}");
        }

        return false; // 跳过原方法，避免调用 SDL_VideoInit
    }

    /// <summary>
    /// SDL_VideoInit - SDL3 中已移除，直接返回成功
    /// </summary>
    public static bool SDL_VideoInit_Prefix(ref int __result)
    {
        Console.WriteLine("[SDL3Patch] SDL_VideoInit intercepted - returning success (SDL3 doesn't need this)");
        __result = 0; // 返回成功
        return false; // 跳过原方法
    }

    /// <summary>
    /// SDL_GetDisplayDPI - SDL3 中已移除，返回默认 DPI
    /// </summary>
    public static bool SDL_GetDisplayDPI_Prefix(int displayIndex, ref float ddpi, ref float hdpi, ref float vdpi, ref int __result)
    {
        Console.WriteLine("[SDL3Patch] SDL_GetDisplayDPI intercepted - returning default DPI values");
        ddpi = 160f; // Android 默认 DPI
        hdpi = 160f;
        vdpi = 160f;
        __result = 0; // 成功
        return false;
    }

    /// <summary>
    /// SDL_GetNumVideoDisplays - SDL3 中 API 变化
    /// </summary>
    public static bool SDL_GetNumVideoDisplays_Prefix(ref int __result)
    {
        Console.WriteLine("[SDL3Patch] SDL_GetNumVideoDisplays intercepted");
        __result = 1; // Android 通常只有一个显示器
        return false;
    }

    /// <summary>
    /// SDL_GetVersion - SDL3 中返回 int 而非填充结构体
    /// </summary>
    public static bool SDL_GetVersion_Prefix(ref object version)
    {
        Console.WriteLine("[SDL3Patch] SDL_GetVersion intercepted - faking SDL2 version");
        // 由于结构体类型可能不同，让原方法继续执行
        // 如果失败会被 catch 住
        return true;
    }

    /// <summary>
    /// FNALogging.Init - 处理 SDL_GetVersion 等调用
    /// </summary>
    public static bool FNALogging_Init_Prefix()
    {
        Console.WriteLine("[SDL3Patch] FNALogging.Init intercepted");
        // 继续执行，但内部的 SDL 调用会被其他补丁拦截
        return true;
    }

    #endregion

    #region Native Library Resolution

    private static void TryGetLoadedSDL()
    {
        string[] sdlNames = { "libSDL3.so", "SDL3", "libSDL2.so", "SDL2" };

        foreach (var name in sdlNames)
        {
            try
            {
                var handle = dlopen(name, RTLD_NOW | RTLD_NOLOAD);
                if (handle != IntPtr.Zero)
                {
                    var initSym = dlsym(handle, "SDL_Init");
                    if (initSym != IntPtr.Zero)
                    {
                        _sdlHandle = handle;
                        Console.WriteLine($"[SDL3Patch] Found preloaded SDL: {name}, handle: 0x{handle:X}");
                        return;
                    }
                }
            }
            catch { }
        }

        // 尝试直接加载
        Console.WriteLine("[SDL3Patch] No preloaded SDL found, trying direct load...");
        if (NativeLibrary.TryLoad("SDL3", out _sdlHandle) && _sdlHandle != IntPtr.Zero)
        {
            Console.WriteLine($"[SDL3Patch] Loaded SDL3 directly, handle: 0x{_sdlHandle:X}");
        }
        else if (NativeLibrary.TryLoad("SDL2", out _sdlHandle) && _sdlHandle != IntPtr.Zero)
        {
            Console.WriteLine($"[SDL3Patch] Loaded SDL2 directly, handle: 0x{_sdlHandle:X}");
        }
        else
        {
            Console.WriteLine("[SDL3Patch] WARNING: Could not load any SDL library!");
        }
    }

    private static IntPtr ResolveNativeLibrary(Assembly assembly, string name)
    {
        // 检查缓存
        if (_loadedLibraries.TryGetValue(name, out var cached))
        {
            return cached;
        }

        Console.WriteLine($"[SDL3Patch] Resolving: {name} (requested by {assembly?.GetName().Name})");

        IntPtr handle = IntPtr.Zero;

        // SDL 相关库
        if (name == "SDL2" || name == "SDL3" || name.Contains("SDL"))
        {
            handle = _sdlHandle;
            if (handle == IntPtr.Zero)
            {
                // 再次尝试加载
                NativeLibrary.TryLoad("SDL3", out handle);
                if (handle == IntPtr.Zero)
                    NativeLibrary.TryLoad("SDL2", out handle);
                
                if (handle != IntPtr.Zero)
                    _sdlHandle = handle;
            }
        }
        // FNA3D
        else if (name.Contains("FNA3D"))
        {
            NativeLibrary.TryLoad("FNA3D", out handle);
        }
        // FAudio
        else if (name.Contains("FAudio"))
        {
            NativeLibrary.TryLoad("FAudio", out handle);
        }

        if (handle != IntPtr.Zero)
        {
            _loadedLibraries[name] = handle;
            Console.WriteLine($"[SDL3Patch] Resolved {name} -> 0x{handle:X}");
        }
        else
        {
            Console.WriteLine($"[SDL3Patch] Failed to resolve: {name}");
        }

        return handle;
    }

    #endregion
}
