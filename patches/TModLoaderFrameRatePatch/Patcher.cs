using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Threading;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using MonoMod.RuntimeDetour;
using Terraria.Enums;
using Terraria.ModLoader;
using Main = Terraria.Main;

namespace TModLoaderFrameRatePatch;

public static class Patcher
{
    private delegate void TickOrig(Game game);
    private delegate void TickHook(TickOrig orig, Game game);
    private delegate void PrepareOrig(GraphicsDeviceManager manager, object sender, PreparingDeviceSettingsEventArgs args);
    private delegate void PrepareHook(PrepareOrig orig, GraphicsDeviceManager manager, object sender, PreparingDeviceSettingsEventArgs args);
    private delegate void ResetOrig(GraphicsDevice device, PresentationParameters parameters, GraphicsAdapter adapter);
    private delegate void ResetHook(ResetOrig orig, GraphicsDevice device, PresentationParameters parameters, GraphicsAdapter adapter);
    private delegate void LoadOrig(CancellationToken token);
    private delegate void LoadHook(LoadOrig orig, CancellationToken token);
    private delegate bool UnloadOrig();
    private delegate bool UnloadHook(UnloadOrig orig);
    private static readonly List<IDisposable> hooks = new();
    private static readonly FramePacer pacer = new(120);
    private static ILHook? timingHook;
    internal static string? LastError { get; private set; }

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (hooks.Count > 0) return 0;
        try {
            // Run outside mod-owned content hooks. Remove our timing edit before
            // mods install audited IL, then apply it after their Load methods.
            var lifecycleOrder = new DetourConfig("com.app.ralaunch.tmodloader.framerate.lifecycle", priority: 1000);
            hooks.Add(new Hook(Method(typeof(ModContent), "Load", typeof(CancellationToken)), (LoadHook)LoadContent, lifecycleOrder));
            hooks.Add(new Hook(Method(typeof(ModLoader), "Unload"), (UnloadHook)UnloadContent, lifecycleOrder));
            hooks.Add(new Hook(Method(typeof(Game), "Tick"), (TickHook)Tick));
            hooks.Add(new Hook(Method(typeof(GraphicsDeviceManager), "OnPreparingDeviceSettings", typeof(object), typeof(PreparingDeviceSettingsEventArgs)), (PrepareHook)Prepare));
            hooks.Add(new Hook(Method(typeof(GraphicsDevice), "Reset", typeof(PresentationParameters), typeof(GraphicsAdapter)), (ResetHook)Reset));
            Console.WriteLine("[TModLoaderFrameRatePatch] Lifecycle ready (0.1.1); 120 FPS timing installs after mod content loading.");
            return 0;
        }
        catch (Exception ex) {
            Shutdown();
            Console.WriteLine($"[TModLoaderFrameRatePatch] Installation failed; all hooks rolled back: {ex}");
            return -1;
        }
    }

    private static MethodInfo Method(Type type, string name, params Type[] args) =>
        type.GetMethod(name, BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic,
            null, args, null) ?? throw new MissingMethodException(type.FullName, name);

    private static bool IsForeground => !Main.dedServ && Main.instance?.IsActive == true;

    private static void LoadContent(LoadOrig orig, CancellationToken token)
    {
        RemoveTiming();
        LastError = null;
        orig(token);
        if (Main.dedServ) return;
        try {
            timingHook = new ILHook(Method(typeof(Main), "DoUpdate", typeof(GameTime).MakeByRefType()), UseNativeAccumulator);
            Console.WriteLine($"[TModLoaderFrameRatePatch] Ready: foreground render cap=120 FPS, native simulation=60 Hz; tML MVID={typeof(Main).Module.ModuleVersionId}.");
            Main.QueueMainThreadAction(() => {
                if (timingHook == null || Main.graphics == null) return;
                Main.graphics.SynchronizeWithVerticalRetrace = false;
                Main.graphics.ApplyChanges();
            });
        }
        catch (Exception ex) {
            RemoveTiming();
            LastError = ex.Message;
            Console.WriteLine($"[TModLoaderFrameRatePatch] Timing disabled; installation rolled back: {ex}");
        }
    }

    private static bool UnloadContent(UnloadOrig orig)
    {
        RemoveTiming();
        return orig();
    }

    private static void RemoveTiming()
    {
        timingHook?.Dispose();
        timingHook = null;
        pacer.Reset();
    }

    private static void UseNativeAccumulator(ILContext il)
    {
        var reads = il.Body.Instructions.Where(instruction => instruction.OpCode == OpCodes.Ldsfld &&
            instruction.Operand is FieldReference field && field.DeclaringType.FullName == typeof(Main).FullName &&
            field.Name == nameof(Main.FrameSkipMode)).ToArray();
        int accumulatorSteps = il.Body.Instructions.Count(instruction => instruction.OpCode == OpCodes.Ldc_R8 &&
            instruction.Operand is double value && Math.Abs(value - 0.01666666753590107) < 1e-12);
        int suppressDrawCalls = il.Body.Instructions.Count(instruction => instruction.Operand is MethodReference method &&
            method.DeclaringType.FullName == typeof(Game).FullName && method.Name == nameof(Game.SuppressDraw));
        if (reads.Length != 5 || accumulatorSteps != 3 || suppressDrawCalls != 1)
            throw new NotSupportedException($"Unsupported DoUpdate timing layout: frameSkipReads={reads.Length}, accumulatorSteps={accumulatorSteps}, suppressDrawCalls={suppressDrawCalls}.");

        var replacement = il.Import(typeof(Patcher).GetMethod(nameof(ReadFrameSkip), BindingFlags.Static | BindingFlags.NonPublic)!);
        foreach (var read in reads) {
            // Both ldsfld and call occupy five bytes; preserve branch targets.
            read.OpCode = OpCodes.Call;
            read.Operand = replacement;
        }
    }

    private static FrameSkipMode ReadFrameSkip() => IsForeground ? FrameSkipMode.Off : Main.FrameSkipMode;

    private static void Tick(TickOrig orig, Game game)
    {
        if (timingHook != null && game is Main && IsForeground) {
            pacer.WaitForNextFrame();
            game.IsFixedTimeStep = false;
        }
        else pacer.Reset();
        orig(game);
    }

    private static void Prepare(PrepareOrig orig, GraphicsDeviceManager manager, object sender, PreparingDeviceSettingsEventArgs args)
    {
        orig(manager, sender, args);
        if (timingHook != null && !Main.dedServ) args.GraphicsDeviceInformation.PresentationParameters.PresentationInterval = PresentInterval.Immediate;
    }

    private static void Reset(ResetOrig orig, GraphicsDevice device, PresentationParameters parameters, GraphicsAdapter adapter)
    {
        if (timingHook != null && !Main.dedServ) parameters.PresentationInterval = PresentInterval.Immediate;
        orig(device, parameters, adapter);
    }

    internal static void Shutdown()
    {
        RemoveTiming();
        foreach (var hook in hooks.AsEnumerable().Reverse()) hook.Dispose();
        hooks.Clear();
        pacer.Reset();
    }
}
