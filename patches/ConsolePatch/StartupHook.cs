using System;
using System.IO;
using System.Reflection;

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
        Console.WriteLine("[StartupHook] ConsolePatch DOTNET_STARTUP_HOOKS executing...");
        
        // 注册程序集解析器,从 monomod 目录加载依赖
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用补丁初始化方法
        int result = ConsolePatch.ConsolePatcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] ConsolePatcher.Initialize returned: {result}");
    }

    /// <summary>
    /// 程序集解析器 - 从 MONOMOD_PATH 环境变量指定的目录加载依赖程序集
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
                    Console.WriteLine($"[StartupHook] Loading dependency from MONOMOD_PATH: {assemblyName}");
                    return Assembly.LoadFrom(monoModAssemblyPath);
                }
            }

            // 备选: 从补丁自己的目录加载
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[StartupHook] Loading local dependency: {assemblyName}");
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
