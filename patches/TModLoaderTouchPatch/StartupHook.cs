using System;
using System.IO;
using System.Reflection;
using System.Runtime.CompilerServices;

/// <summary>
/// .NET 要求全局命名空间中的 StartupHook.Initialize() 作为启动钩子入口。
/// </summary>
internal static class StartupHook
{
    public static void Initialize()
    {
        Console.WriteLine("[StartupHook] TModLoaderTouchPatch DOTNET_STARTUP_HOOKS executing...");
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;
        InitializePatch();
    }

    // 在注册依赖解析器后再解析 Harmony 等补丁依赖。
    [MethodImpl(MethodImplOptions.NoInlining)]
    private static void InitializePatch()
    {
        int result = TModLoaderTouchPatch.Patcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] TModLoaderTouchPatch.Patcher.Initialize returned: {result}");
    }

    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            string assemblyName = new AssemblyName(args.Name).Name ?? "";
            string? monoModPath = Environment.GetEnvironmentVariable("MONOMOD_PATH");
            if (!string.IsNullOrEmpty(monoModPath))
            {
                string dependencyPath = Path.Combine(monoModPath, assemblyName + ".dll");
                if (File.Exists(dependencyPath))
                {
                    return Assembly.LoadFrom(dependencyPath);
                }
            }

            string patchDirectory = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string localPath = Path.Combine(patchDirectory, assemblyName + ".dll");
            if (File.Exists(localPath))
            {
                return Assembly.LoadFrom(localPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderTouchPatch] Failed to resolve {args.Name}: {ex.Message}");
        }

        return null;
    }
}
