using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Threading;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using MonoMod.RuntimeDetour;
using Terraria.Enums;
using Terraria.ModLoader;
using TModLoaderFrameRatePatch;
using Main = Terraria.Main;

internal static class Program
{
    private delegate void Update(Main self, ref GameTime time);
    private static int assertions, simulationSteps;
    private static readonly FieldInfo active = typeof(Game).GetField("INTERNAL_isActive", BindingFlags.Instance | BindingFlags.NonPublic);
    private static readonly FieldInfo suppressed = typeof(Game).GetField("suppressDraw", BindingFlags.Instance | BindingFlags.NonPublic);

    private static void Check(bool condition, string message)
    {
        if (!condition) throw new Exception(message);
        assertions++;
        Console.WriteLine("PASS " + message);
    }

    private static void MainEntry(string[] args)
    {
        Environment.SetEnvironmentVariable("PATH", Path.Combine(AppContext.BaseDirectory, "Native") + ";" + Environment.GetEnvironmentVariable("PATH"));
        Terraria.Program.SavePath = Path.Combine(Path.GetTempPath(), "RAL-FrameRateHarness-" + Environment.ProcessId);
        Directory.CreateDirectory(Terraria.Program.SavePath);
        Run(args);
        Console.WriteLine($"PASS {assertions} assertions");
    }

    private static void Main(string[] args) => MainEntry(args);

    [MethodImpl(MethodImplOptions.NoInlining)]
    private static void Run(string[] args)
    {
        var game = (Main)RuntimeHelpers.GetUninitializedObject(typeof(Main));
        GC.SuppressFinalize(game);
        Terraria.Main.instance = game;
        Terraria.Main.dedServ = false;
        Terraria.Main.superFast = false;
        active.SetValue(game, true);

        bool startup = args.Contains("--startup-smoke");
        using var noNativeTick = new ILHook(typeof(Game).GetMethod("Tick"), il => {
            il.Body.Instructions.Clear();
            il.Body.ExceptionHandlers.Clear();
            new ILCursor(il).Emit(OpCodes.Ret);
        });
        var load = typeof(ModContent).GetMethod("Load", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic, null, new[] { typeof(CancellationToken) }, null);
        using var noNativeContent = new ILHook(load, il => {
            il.Body.Instructions.Clear();
            il.Body.ExceptionHandlers.Clear();
            new ILCursor(il).Emit(OpCodes.Ret);
        });
        var patch = startup
            ? AppDomain.CurrentDomain.GetAssemblies().Single(assembly => assembly.GetName().Name == "TModLoaderFrameRatePatch").GetType("TModLoaderFrameRatePatch.Patcher")
            : typeof(Patcher);
        var init = patch.GetMethod("Initialize");
        var shutdown = patch.GetMethod("Shutdown", BindingFlags.Static | BindingFlags.NonPublic);
        Check((int)init.Invoke(null, new object[] { IntPtr.Zero, 0 }) == 0, "real-runtime hooks install and repeated initialization is harmless");
        if (startup) Check(patch.Assembly != typeof(Program).Assembly, "startup hook loaded the packaged production assembly");
        var lastError = patch.GetProperty("LastError", BindingFlags.Static | BindingFlags.NonPublic);
        load.Invoke(null, new object[] { CancellationToken.None });
        Check(lastError.GetValue(null) == null, "content lifecycle installs timing after mod loading");

        var updateMethod = typeof(Main).GetMethod("DoUpdate", BindingFlags.Instance | BindingFlags.NonPublic);
        // Run the real method's timing block, including the production IL edits.
        // Bypass unrelated content/audio/world initialization and replace only
        // the continuation into simulation with a counter.
        using var timingOnly = new ILHook(updateMethod, il => {
            var reads = il.Body.Instructions.Where(instruction =>
                (instruction.OpCode == OpCodes.Ldsfld && instruction.Operand is FieldReference field && field.DeclaringType.FullName == typeof(Main).FullName && field.Name == "FrameSkipMode") ||
                (instruction.OpCode == OpCodes.Call && instruction.Operand is MethodReference method && method.DeclaringType.FullName == typeof(Patcher).FullName && method.Name == "ReadFrameSkip")).ToArray();
            int lastThreshold = il.Body.Instructions.ToList().FindLastIndex(instruction => instruction.OpCode == OpCodes.Ldc_R8 && instruction.Operand is double value && Math.Abs(value - 0.01666666753590107) < 1e-12);
            var continuation = il.Body.Instructions.Skip(lastThreshold).First(instruction => instruction.OpCode == OpCodes.Ldsfld && instruction.Operand is FieldReference field && field.Name == "uCount");
            var cursor = new ILCursor(il);
            cursor.Goto(continuation, MoveType.AfterLabel);
            cursor.EmitDelegate<Action>(() => simulationSteps++);
            cursor.Emit(OpCodes.Ret);
            cursor.Index = 0;
            cursor.Emit(OpCodes.Br, reads[2]);
        });
        var update = (Update)updateMethod.CreateDelegate(typeof(Update));
        foreach (FrameSkipMode mode in Enum.GetValues<FrameSkipMode>()) {
            foreach (int fps in new[] { 60, 120, 144, 240 }) {
                Terraria.Main.FrameSkipMode = mode;
                Terraria.Main.UpdateTimeAccumulator = 0;
                simulationSteps = 0;
                suppressed.SetValue(game, false);
                for (int frame = 0; frame < fps * 10; frame++) {
                    var time = new GameTime(TimeSpan.FromSeconds((double)(frame + 1) / fps), TimeSpan.FromSeconds(1.0 / fps));
                    update(game, ref time);
                }
                Check(simulationSteps >= 599 && simulationSteps <= 600, $"{fps} FPS/{mode}: native accumulator executes {simulationSteps} world ticks per ten seconds");
                Check(Terraria.Main.FrameSkipMode == mode && !Terraria.Main.superFast, "user frame-skip setting and simulation speed flag preserved");
                Check(!(bool)suppressed.GetValue(game), "extra foreground frames are not suppressed");
            }
        }

        active.SetValue(game, false);
        Terraria.Main.FrameSkipMode = FrameSkipMode.Subtle;
        Terraria.Main.UpdateTimeAccumulator = 0;
        suppressed.SetValue(game, false);
        var halfTick = new GameTime(TimeSpan.Zero, TimeSpan.FromSeconds(1.0 / 120));
        update(game, ref halfTick);
        Check((bool)suppressed.GetValue(game), "inactive game's original subtle-frame suppression preserved");
        active.SetValue(game, true);
        Terraria.Main.dedServ = true;
        Terraria.Main.UpdateTimeAccumulator = 0;
        suppressed.SetValue(game, false);
        update(game, ref halfTick);
        Check((bool)suppressed.GetValue(game), "dedicated-server timing reads remain unchanged");
        Terraria.Main.dedServ = false;

        var manager = (GraphicsDeviceManager)RuntimeHelpers.GetUninitializedObject(typeof(GraphicsDeviceManager));
        GC.SuppressFinalize(manager);
        var prepare = typeof(GraphicsDeviceManager).GetMethod("OnPreparingDeviceSettings", BindingFlags.Instance | BindingFlags.NonPublic);
        var info = new GraphicsDeviceInformation { PresentationParameters = new PresentationParameters { PresentationInterval = PresentInterval.One } };
        prepare.Invoke(manager, new object[] { manager, new PreparingDeviceSettingsEventArgs(info) });
        Check(info.PresentationParameters.PresentationInterval == PresentInterval.Immediate, "device creation requests immediate presentation instead of 60 Hz vsync");

        var resetMethod = typeof(GraphicsDevice).GetMethod("Reset", new[] { typeof(PresentationParameters), typeof(GraphicsAdapter) });
        using var noNativeReset = new ILHook(resetMethod, il => {
            il.Body.Instructions.Clear();
            il.Body.ExceptionHandlers.Clear();
            new ILCursor(il).Emit(OpCodes.Ret);
        });
        var device = (GraphicsDevice)RuntimeHelpers.GetUninitializedObject(typeof(GraphicsDevice));
        GC.SuppressFinalize(device);
        var parameters = new PresentationParameters { PresentationInterval = PresentInterval.One, BackBufferWidth = 1280, BackBufferHeight = 720 };
        device.Reset(parameters, null);
        Check(parameters.PresentationInterval == PresentInterval.Immediate && parameters.BackBufferWidth == 1280 && parameters.BackBufferHeight == 720,
            "device reset disables vsync while preserving render dimensions");

        game.IsFixedTimeStep = true;
        var watch = Stopwatch.StartNew();
        for (int i = 0; i < 121; i++) game.Tick();
        Check(!game.IsFixedTimeStep, "actual Game.Tick hook selects variable stepping for foreground tML");
        Check(watch.Elapsed.TotalSeconds >= 0.99 && watch.Elapsed.TotalSeconds < 2, "actual Game.Tick hook paces 120 foreground frames per second");
        Thread.Sleep(30);
        game.Tick();
        watch.Restart();
        game.Tick();
        Check(watch.Elapsed.TotalMilliseconds >= 6, "slow frames do not cause a catch-up burst");
        active.SetValue(game, false);
        game.IsFixedTimeStep = true;
        game.Tick();
        Check(game.IsFixedTimeStep, "inactive Tick keeps the original fixed-step setting");
        active.SetValue(game, true);
        watch.Restart();
        game.Tick();
        Check(watch.Elapsed.TotalMilliseconds < 5, "resume resets deadline without an old-frame backlog");

        shutdown.Invoke(null, null);
        Terraria.Main.FrameSkipMode = FrameSkipMode.On;
        Terraria.Main.UpdateTimeAccumulator = 0;
        simulationSteps = 0;
        update(game, ref halfTick);
        Check(simulationSteps == 1, "shutdown restores original frame-skip control flow");
        info.PresentationParameters.PresentationInterval = PresentInterval.One;
        prepare.Invoke(manager, new object[] { manager, new PreparingDeviceSettingsEventArgs(info) });
        Check(info.PresentationParameters.PresentationInterval == PresentInterval.One, "shutdown removes presentation hook");
        parameters.PresentationInterval = PresentInterval.One;
        device.Reset(parameters, null);
        Check(parameters.PresentationInterval == PresentInterval.One, "shutdown removes device reset hook");

        using (var unsupportedLayout = new ILHook(updateMethod, il => {
            var threshold = il.Body.Instructions.First(instruction => instruction.OpCode == OpCodes.Ldc_R8 && instruction.Operand is double value && Math.Abs(value - 0.01666666753590107) < 1e-12);
            threshold.Operand = 0.02;
        })) {
            init.Invoke(null, new object[] { IntPtr.Zero, 0 });
            load.Invoke(null, new object[] { CancellationToken.None });
            Check(lastError.GetValue(null) != null, "changed accumulator layout rejects timing installation without breaking content load");
            game.IsFixedTimeStep = true;
            game.Tick();
            Check(game.IsFixedTimeStep, "failed installation leaves no foreground Tick hook");
        }
        load.Invoke(null, new object[] { CancellationToken.None });
        Check(lastError.GetValue(null) == null, "timing can install again on content reload after rollback");
        shutdown.Invoke(null, null);
    }
}
