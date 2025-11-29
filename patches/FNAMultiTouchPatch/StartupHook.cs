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
        Console.WriteLine("[StartupHook] FNAMultiTouchPatch DOTNET_STARTUP_HOOKS executing...");
        
        // 注册程序集解析器,从补丁 DLL 同目录加载依赖
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;

        // 调用原有的补丁初始化方法
        int result = FNAMultiTouchPatch.MultiTouchPatcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] MultiTouchPatcher.Initialize returned: {result}");
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

