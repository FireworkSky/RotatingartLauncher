using System;

/// <summary>
/// .NET Startup Hook entry point
/// This class is automatically called by the .NET runtime when DOTNET_STARTUP_HOOKS is set
/// </summary>
internal class StartupHook
{
    /// <summary>
    /// Called by the .NET runtime before the main assembly is loaded
    /// </summary>
    public static void Initialize()
    {
        Console.WriteLine("[SDL3Patch] StartupHook.Initialize called");
        SDL3Patch.Patcher.Initialize(IntPtr.Zero, 0);
    }
}
