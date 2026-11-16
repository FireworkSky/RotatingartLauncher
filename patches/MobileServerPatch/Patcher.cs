using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using MonoMod.RuntimeDetour;
using Terraria;
using Terraria.ModLoader;

namespace MobileServerPatch;

public static class Patcher
{
    private delegate void LoadOrig(CancellationToken token);
    private delegate void LoadHook(LoadOrig orig, CancellationToken token);
    private delegate bool UnloadOrig();
    private delegate bool UnloadHook(UnloadOrig orig);
    private static readonly List<IDisposable> bootstrapHooks = new();

    internal static void Log(string message, Exception error = null) =>
        Console.WriteLine($"[MobileServerPatch] {message}{(error == null ? "" : Environment.NewLine + error)}");

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (bootstrapHooks.Count > 0) return 0;
        try {
            bootstrapHooks.Add(new Hook(HostSession.Method(typeof(ModContent), "Load", typeof(CancellationToken)), (LoadHook)LoadContent));
            bootstrapHooks.Add(new Hook(HostSession.Method(typeof(ModLoader), "Unload"), (UnloadHook)UnloadMods));
            Log("Lifecycle hooks ready (0.1.0); hosting starts only from the menu or /mobilehost.");
            return 0;
        }
        catch (Exception ex) {
            Shutdown();
            Log("Bootstrap installation failed; rolled back.", ex);
            return -1;
        }
    }

    private static void LoadContent(LoadOrig orig, CancellationToken token)
    {
        HostSession.Unload();
        if (Main.dedServ || ModLoader.Mods.Any(mod => mod.Name == "MobileServer")) {
            Log(Main.dedServ ? "Dedicated server: integrated host skipped." : "MobileServer mod is loaded; patch yields to the mod.");
            orig(token);
            return;
        }

        try {
            HostSession.Load();
            orig(token);
        }
        catch {
            HostSession.Unload();
            throw;
        }
    }

    private static bool UnloadMods(UnloadOrig orig)
    {
        HostSession.Unload();
        return orig();
    }

    // Used for rollback and by the real-runtime harness to verify hook ownership.
    internal static void Shutdown()
    {
        try { HostSession.Unload(); }
        finally {
            foreach (IDisposable hook in bootstrapHooks.AsEnumerable().Reverse()) hook.Dispose();
            bootstrapHooks.Clear();
        }
    }
}
