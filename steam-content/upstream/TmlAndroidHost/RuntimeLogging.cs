using System.Text;

namespace TmlAndroidHost;

internal static class RuntimeLogging
{
    private static readonly object Sync = new();
    private static bool _initialized;

    public static void Initialize(string workingDirectory)
    {
        lock (Sync)
        {
            if (_initialized)
                return;

            var path = Path.Combine(workingDirectory, "dotnet-runtime.log");
            try
            {
                var fileStream = new FileStream(
                    path,
                    FileMode.Append,
                    FileAccess.Write,
                    FileShare.ReadWrite,
                    bufferSize: 16 * 1024,
                    options: FileOptions.Asynchronous);
                var fileWriter = new StreamWriter(fileStream, new UTF8Encoding(false))
                {
                    AutoFlush = true
                };
                var tee = new TeeTextWriter(Console.Out, fileWriter);
                Console.SetOut(tee);
                Console.SetError(tee);
                AppDomain.CurrentDomain.UnhandledException += (_, eventArgs) =>
                    Console.Error.WriteLine(
                        $"[dotnet] UnhandledException terminating={eventArgs.IsTerminating}:\n{eventArgs.ExceptionObject}");
                TaskScheduler.UnobservedTaskException += (_, eventArgs) =>
                {
                    Console.Error.WriteLine($"[dotnet] UnobservedTaskException:\n{eventArgs.Exception}");
                    eventArgs.SetObserved();
                };
                _initialized = true;
                Console.WriteLine($"[dotnet] Runtime log started: {path}");
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine($"[dotnet] Could not initialize runtime log at {path}: {exception}");
            }
        }
    }

    private sealed class TeeTextWriter(TextWriter consoleWriter, TextWriter fileWriter) : TextWriter
    {
        public override Encoding Encoding => Encoding.UTF8;

        public override void Write(char value)
        {
            consoleWriter.Write(value);
            fileWriter.Write(value);
        }

        public override void Write(string? value)
        {
            consoleWriter.Write(value);
            fileWriter.Write(value);
        }

        public override void WriteLine(string? value)
        {
            consoleWriter.WriteLine(value);
            fileWriter.WriteLine(value);
        }

        public override void Flush()
        {
            consoleWriter.Flush();
            fileWriter.Flush();
        }
    }
}
