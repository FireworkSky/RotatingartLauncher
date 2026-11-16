#nullable enable
using System;
using System.IO;
using System.Reflection;
using System.Runtime.CompilerServices;

internal static class StartupHook
{
    public static void Initialize()
    {
        Console.WriteLine("[StartupHook] MobileServerPatch DOTNET_STARTUP_HOOKS executing...");
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;
        InitializePatch();
    }

    [MethodImpl(MethodImplOptions.NoInlining)]
    private static void InitializePatch()
    {
        int result = MobileServerPatch.Patcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] MobileServerPatch.Patcher.Initialize returned: {result}");
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
                if (File.Exists(dependencyPath)) return Assembly.LoadFrom(dependencyPath);
            }

            string patchDirectory = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string localPath = Path.Combine(patchDirectory, assemblyName + ".dll");
            if (File.Exists(localPath)) return Assembly.LoadFrom(localPath);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MobileServerPatch] Failed to resolve {args.Name}: {ex.Message}");
        }
        return null;
    }
}
