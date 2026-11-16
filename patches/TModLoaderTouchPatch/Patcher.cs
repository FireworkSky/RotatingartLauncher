using HarmonyLib;
using System;
using System.Linq;
using System.Reflection;
using System.IO;

namespace TModLoaderTouchPatch;

/// <summary>
/// 独立的 tModLoader 原生触屏输入补丁入口。
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;
    private static readonly object LogSync = new();
    private static string? _logPath;
    private static System.Threading.Timer? _heartbeat;
    private static readonly System.Collections.Generic.Dictionary<string, (long Count, long Last, long Next)> Samples = new();

    internal static void StartHeartbeat()
    {
        _heartbeat ??= new System.Threading.Timer(_ =>
        {
            try { TouchInputPatch.ReportHeartbeat(); }
            catch { /* Diagnostics must not terminate the game. */ }
        }, null, 10000, 10000);
    }

    internal static void Trace(string stage, Func<string> details, bool edge = false)
    {
        try
        {
            lock (LogSync)
            {
                long now = Environment.TickCount64;
                Samples.TryGetValue(stage, out var sample);
                long count = sample.Count + 1;
                bool emit = sample.Count == 0 || now >= sample.Next || (edge && now - sample.Last >= 100);
                Samples[stage] = (count, emit ? now : sample.Last, emit ? now + 5000 : sample.Next);
                if (emit) Log($"Trace {stage}: samples={count}, tick={now}, {details()}");
            }
        }
        catch { /* Diagnostic collection must not change input behavior. */ }
    }

    internal static void Log(string message)
    {
        string line = $"[TModLoaderTouchPatch] {message}";
        Console.WriteLine(line);
        lock (LogSync)
        {
            if (_logPath == null) return;
            try
            {
                // Diagnostics must remain bounded even across a long session.
                if (new FileInfo(_logPath).Length > 1024 * 1024)
                    File.Move(_logPath, _logPath + ".previous", true);
                File.AppendAllText(_logPath, $"{DateTime.UtcNow:O} {line}{Environment.NewLine}");
            }
            catch (Exception ex)
            {
                _logPath = null;
                Console.WriteLine($"[TModLoaderTouchPatch] Diagnostic file disabled: {ex}");
            }
        }
    }

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            try
            {
                _logPath = Path.Combine(Path.GetDirectoryName(typeof(Patcher).Assembly.Location)!, "touch-input.log");
                if (File.Exists(_logPath)) File.Move(_logPath, _logPath + ".previous", true);
                File.WriteAllText(_logPath, "");
            }
            catch (Exception ex)
            {
                _logPath = null;
                Log($"Cannot create diagnostic file: {ex}");
            }
            Log($"Initializing touch patch 1.0.14 (mod mode, no mouse fallback); diagnostic file={_logPath ?? "unavailable"}; " +
                $"pid={Environment.ProcessId}, tick={Environment.TickCount64}, " +
                $"SDL_TOUCH_MOUSE_EVENTS={Environment.GetEnvironmentVariable("SDL_TOUCH_MOUSE_EVENTS")}, " +
                $"SDL_MOUSE_TOUCH_EVENTS={Environment.GetEnvironmentVariable("SDL_MOUSE_TOUCH_EVENTS")}.");
            _harmony = new Harmony("com.ralaunch.tmodloader.touch");

            var loadedAssembly = AppDomain.CurrentDomain.GetAssemblies()
                .FirstOrDefault(assembly => assembly.GetName().Name == "tModLoader");

            if (loadedAssembly != null)
            {
                TouchInputPatch.Apply(_harmony, loadedAssembly);
            }
            else
            {
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
                Log("Startup hook registered; waiting for tModLoader assembly before applying hooks.");
            }

            Log("Startup hook initialized.");
            return 0;
        }
        catch (Exception ex)
        {
            Log($"ERROR: {ex}");
            return -1;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        if (args.LoadedAssembly.GetName().Name != "tModLoader" || _harmony == null)
        {
            return;
        }

        AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
        try
        {
            TouchInputPatch.Apply(_harmony, args.LoadedAssembly);
        }
        catch (Exception ex)
        {
            Log($"Deferred hook application failed: {ex}");
        }
    }
}
