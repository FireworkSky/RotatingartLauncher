using System;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;

namespace MonoGamePatch;

/// <summary>
/// MonoGame Android 兼容性修复补丁
/// 
/// 问题: MonoGame.Framework 的 FuncLoader 使用 Linux 风格库名
/// - P/Invoke 使用 [DllImport("libdl.so.2")]
/// - LoadLibraryExt 参数传递 "libSDL2-2.0.so.0"
/// 
/// 解决方案: 
/// 1. NativeLibrary.SetDllImportResolver 处理 P/Invoke 库名
/// 2. Harmony 补丁处理 LoadLibraryExt 方法参数
/// </summary>
public static class MonoGamePatcher
{
    private static bool _initialized = false;
    private static Harmony? _harmony;

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (_initialized) return 0;

        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[MonoGamePatch] Native Library Name Fix");
            Console.WriteLine("========================================");

            _harmony = new Harmony("com.ralaunch.monogamepatch");

            // 为所有已加载和将要加载的程序集设置解析器和补丁
            AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;

            // 处理已加载的程序集
            foreach (var assembly in AppDomain.CurrentDomain.GetAssemblies())
            {
                ProcessAssembly(assembly);
            }

            _initialized = true;
            Console.WriteLine("[MonoGamePatch] Native library resolver installed");
            Console.WriteLine("========================================");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGamePatch] ERROR: {ex.Message}");
            Console.WriteLine($"[MonoGamePatch] Stack: {ex.StackTrace}");
            return -1;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        ProcessAssembly(args.LoadedAssembly);
    }

    private static void ProcessAssembly(Assembly assembly)
    {
        var assemblyName = assembly.GetName().Name;

        // 先设置 Harmony 补丁 (处理 LoadLibraryExt 参数) - 必须在静态构造函数执行前
        TryApplyHarmonyPatches(assembly, assemblyName);

        // 再设置 DLL 导入解析器 (处理 P/Invoke)
        TrySetDllImportResolver(assembly, assemblyName);
    }

    private static void TrySetDllImportResolver(Assembly assembly, string? assemblyName)
    {
        try
        {
            if (assemblyName == "MonoGame.Framework" || 
                assemblyName == "StardewValley" ||
                assemblyName == "StardewModdingAPI")
            {
                NativeLibrary.SetDllImportResolver(assembly, DllImportResolver);
                Console.WriteLine($"[MonoGamePatch] DllImportResolver set for: {assemblyName}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGamePatch] Could not set DllImportResolver: {ex.Message}");
        }
    }

    private static void TryApplyHarmonyPatches(Assembly assembly, string? assemblyName)
    {
        if (assemblyName != "MonoGame.Framework") return;

        try
        {
            var funcLoaderType = assembly.GetType("MonoGame.Framework.Utilities.FuncLoader");
            if (funcLoaderType == null)
            {
                Console.WriteLine("[MonoGamePatch] FuncLoader type not found");
                return;
            }

            // 补丁 LoadLibraryExt 方法
            var loadLibraryExtMethod = funcLoaderType.GetMethod(
                "LoadLibraryExt",
                BindingFlags.Public | BindingFlags.Static,
                null,
                new[] { typeof(string) },
                null
            );

            if (loadLibraryExtMethod != null)
            {
                _harmony!.Patch(
                    loadLibraryExtMethod,
                    prefix: new HarmonyMethod(typeof(MonoGamePatcher), nameof(LoadLibraryExt_Prefix))
                );
                Console.WriteLine("[MonoGamePatch] FuncLoader.LoadLibraryExt patched!");
            }

            // 补丁 LoadLibrary 方法
            var loadLibraryMethod = funcLoaderType.GetMethod(
                "LoadLibrary",
                BindingFlags.Public | BindingFlags.Static,
                null,
                new[] { typeof(string) },
                null
            );

            if (loadLibraryMethod != null)
            {
                _harmony!.Patch(
                    loadLibraryMethod,
                    prefix: new HarmonyMethod(typeof(MonoGamePatcher), nameof(LoadLibrary_Prefix))
                );
                Console.WriteLine("[MonoGamePatch] FuncLoader.LoadLibrary patched!");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGamePatch] Harmony patch failed: {ex.Message}");
        }
    }

    /// <summary>
    /// DLL 导入解析器 - 处理 P/Invoke 的 [DllImport] 库名
    /// </summary>
    private static IntPtr DllImportResolver(string libraryName, Assembly assembly, DllImportSearchPath? searchPath)
    {
        string resolvedName = ConvertLibraryName(libraryName);

        if (resolvedName != libraryName)
        {
            Console.WriteLine($"[MonoGamePatch] Resolving: {libraryName} -> {resolvedName}");
        }

        if (NativeLibrary.TryLoad(resolvedName, assembly, searchPath, out IntPtr handle))
        {
            return handle;
        }

        return IntPtr.Zero;
    }

    /// <summary>
    /// Harmony 前缀补丁 - 修改 LoadLibraryExt 的库名参数
    /// </summary>
    public static void LoadLibraryExt_Prefix(ref string libname)
    {
        string original = libname;
        libname = ConvertLibraryName(libname);
        
        if (libname != original)
        {
            Console.WriteLine($"[MonoGamePatch] LoadLibraryExt: {original} -> {libname}");
        }
    }

    /// <summary>
    /// Harmony 前缀补丁 - 修改 LoadLibrary 的库名参数
    /// </summary>
    public static void LoadLibrary_Prefix(ref string libname)
    {
        string original = libname;
        libname = ConvertLibraryName(libname);
        
        if (libname != original)
        {
            Console.WriteLine($"[MonoGamePatch] LoadLibrary: {original} -> {libname}");
        }
    }

    /// <summary>
    /// 将 Linux 库名转换为 Android 兼容格式
    /// </summary>
    private static string ConvertLibraryName(string libraryName)
    {
        // 提取文件名（如果包含路径）
        string fileName = System.IO.Path.GetFileName(libraryName);
        string? directory = System.IO.Path.GetDirectoryName(libraryName);
        string convertedFileName = fileName;

        // libdl.so.2 -> libdl.so
        if (fileName == "libdl.so.2")
        {
            convertedFileName = "libdl.so";
        }
        // libSDL2-2.0.so.0 -> libSDL2.so
        else if (fileName == "libSDL2-2.0.so.0" || fileName.Contains("SDL2"))
        {
            convertedFileName = "libSDL2.so";
        }
        // libopenal.so.1 或其他 OpenAL 变体 -> libopenal32.so (Android 版本)
        else if (fileName.Contains("openal") || fileName.Contains("OpenAL"))
        {
            convertedFileName = "libopenal32.so";
        }
        // 通用处理: libXXX.so.N -> libXXX.so
        else if (fileName.Contains(".so."))
        {
            int idx = fileName.IndexOf(".so.");
            if (idx > 0)
            {
                convertedFileName = fileName.Substring(0, idx + 3);
            }
        }

        // 如果文件名没变，返回原始值
        if (convertedFileName == fileName)
        {
            return libraryName;
        }

        // 返回转换后的文件名（不保留路径，让系统去搜索）
        return convertedFileName;
    }
}
