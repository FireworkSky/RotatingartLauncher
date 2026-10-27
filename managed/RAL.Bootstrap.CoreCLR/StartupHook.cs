using System;
using System.IO;
using System.Reflection;
using RAL.Bootstrap.CoreCLR;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// 此类必须在全局命名空间（没有命名空间）
/// 会在应用程序 Main() 方法之前自动执行
///
/// Bootstrap 作为唯一启动钩子，按 RAL_PATCHES 环境变量加载补丁，
/// 替代每个补丁 DLL 各自作为 startup hook 的方式。
/// </summary>
// ReSharper disable once CheckNamespace
// ReSharper disable once UnusedType.Global
internal static class StartupHook
{
    private static bool _initialized;

    /// <summary>
    /// DOTNET_STARTUP_HOOKS 要求的初始化方法
    /// 无参数,返回 void
    /// </summary>
    // ReSharper disable once UnusedMember.Global
    public static void Initialize()
    {
        if (_initialized)
        {
            return;
        }
        _initialized = true;

        Console.WriteLine("[RAL.Bootstrap] DOTNET_STARTUP_HOOKS executing...");

        // 注册程序集解析器（先注册再加载补丁，补丁的依赖才能解析）
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 加载并执行 RAL_PATCHES 中的补丁
        PatchLoader.LoadAndRunPatches();
    }

    /// <summary>
    /// 程序集解析器 - 优先从 MONOMOD_PATH 环境变量指定的目录加载依赖，
    /// 备选从请求方程序集所在目录加载。
    /// </summary>
    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            // 获取请求的程序集名称(不含版本等信息)
            string assemblyName = new AssemblyName(args.Name).Name ?? "";

            // 优先从 MONOMOD_PATH 环境变量指定的目录加载
            string? monoModPath = Environment.GetEnvironmentVariable("MONOMOD_PATH");
            if (!string.IsNullOrEmpty(monoModPath))
            {
                string monoModAssemblyPath = Path.Combine(monoModPath, assemblyName + ".dll");
                if (File.Exists(monoModAssemblyPath))
                {
                    Console.WriteLine($"[RAL.Bootstrap] Loading dependency from MONOMOD_PATH: {assemblyName}");
                    return Assembly.LoadFrom(monoModAssemblyPath);
                }
            }

            // 备选: 从请求方程序集所在目录加载
            string? requestingDir = Path.GetDirectoryName(args.RequestingAssembly?.Location ?? "");
            if (!string.IsNullOrEmpty(requestingDir))
            {
                string localAssemblyPath = Path.Combine(requestingDir, assemblyName + ".dll");
                if (File.Exists(localAssemblyPath))
                {
                    Console.WriteLine($"[RAL.Bootstrap] Loading dependency from patch directory: {assemblyName}");
                    return Assembly.LoadFrom(localAssemblyPath);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[RAL.Bootstrap] Failed to resolve assembly {args.Name}: {ex.Message}");
        }

        return null;
    }
}
