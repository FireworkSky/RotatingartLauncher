using System;
using System.IO;
using System.Text;

namespace PerformanceProfiler
{
    /// <summary>
    /// Console 输出重定向器 - 将所有 Console.WriteLine 输出重定向到日志文件
    /// </summary>
    public class ConsoleLogger : TextWriter
    {
        private readonly TextWriter _originalOut;
        private readonly string _logFilePath;
        private StreamWriter? _fileWriter;
        private readonly object _lockObj = new object();

        public override Encoding Encoding => Encoding.UTF8;

        public ConsoleLogger(string logFilePath)
        {
            _originalOut = Console.Out;
            _logFilePath = logFilePath;

            try
            {
                // 确保日志目录存在
                string? logDir = Path.GetDirectoryName(_logFilePath);
                if (!string.IsNullOrEmpty(logDir) && !Directory.Exists(logDir))
                {
                    Directory.CreateDirectory(logDir);
                }

                // 创建日志文件写入器（追加模式）
                _fileWriter = new StreamWriter(_logFilePath, append: true, Encoding.UTF8)
                {
                    AutoFlush = true // 立即刷新，确保数据不丢失
                };

                // 写入会话分隔符
                _fileWriter.WriteLine();
                _fileWriter.WriteLine("========================================");
                _fileWriter.WriteLine($"Performance Profiler Session Started: {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
                _fileWriter.WriteLine("========================================");
                _fileWriter.WriteLine();
            }
            catch (Exception ex)
            {
                _originalOut.WriteLine($"[ConsoleLogger] Failed to create log file: {ex.Message}");
            }
        }

        public override void Write(char value)
        {
            lock (_lockObj)
            {
                _originalOut.Write(value);
                _fileWriter?.Write(value);
            }
        }

        public override void Write(string? value)
        {
            if (value == null) return;

            lock (_lockObj)
            {
                _originalOut.Write(value);
                _fileWriter?.Write(value);
            }
        }

        public override void WriteLine(string? value)
        {
            if (value == null)
            {
                WriteLine();
                return;
            }

            lock (_lockObj)
            {
                string timestampedValue = $"[{DateTime.Now:HH:mm:ss.fff}] {value}";
                _originalOut.WriteLine(timestampedValue);
                _fileWriter?.WriteLine(timestampedValue);
            }
        }

        public override void WriteLine()
        {
            lock (_lockObj)
            {
                _originalOut.WriteLine();
                _fileWriter?.WriteLine();
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                lock (_lockObj)
                {
                    if (_fileWriter != null)
                    {
                        _fileWriter.WriteLine();
                        _fileWriter.WriteLine("========================================");
                        _fileWriter.WriteLine($"Performance Profiler Session Ended: {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
                        _fileWriter.WriteLine("========================================");
                        _fileWriter.Flush();
                        _fileWriter.Dispose();
                        _fileWriter = null;
                    }
                }
            }
            base.Dispose(disposing);
        }

        /// <summary>
        /// 安装 Console 重定向
        /// </summary>
        public static ConsoleLogger? Install(string logFilePath)
        {
            try
            {
                var logger = new ConsoleLogger(logFilePath);
                Console.SetOut(logger);
                Console.WriteLine($"[ConsoleLogger] Logging to: {logFilePath}");
                return logger;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ConsoleLogger] Failed to install: {ex.Message}");
                return null;
            }
        }

        /// <summary>
        /// 获取日志文件路径（基于游戏目录）
        /// </summary>
        public static string GetLogFilePath()
        {
            try
            {
                string baseDir;

                // 尝试多个可能的 Android 数据目录
                string[] possibleDirs = new[]
                {
                    "/storage/emulated/0/Android/data/com.app.ralaunch/files",
                    "/sdcard/Android/data/com.app.ralaunch/files",
                    Environment.GetFolderPath(Environment.SpecialFolder.Personal),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Personal), "files"),
                };

                baseDir = null!;
                foreach (var dir in possibleDirs)
                {
                    try
                    {
                        if (!string.IsNullOrEmpty(dir) && Directory.Exists(dir))
                        {
                            // 测试是否可写
                            string testFile = Path.Combine(dir, ".test_write");
                            File.WriteAllText(testFile, "test");
                            File.Delete(testFile);

                            baseDir = dir;
                            break;
                        }
                    }
                    catch
                    {
                        // 继续尝试下一个目录
                        continue;
                    }
                }

                // 如果所有目录都失败，使用临时目录
                if (string.IsNullOrEmpty(baseDir))
                {
                    baseDir = Path.GetTempPath();
                }

                // 创建日志目录
                string logDir = Path.Combine(baseDir, "logs");
                if (!Directory.Exists(logDir))
                {
                    Directory.CreateDirectory(logDir);
                }

                // 生成日志文件名（每天一个文件）
                string logFileName = $"performance_{DateTime.Now:yyyy-MM-dd}.log";
                return Path.Combine(logDir, logFileName);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ConsoleLogger] Failed to get log path: {ex.Message}");
                // 最终回退到临时目录
                string fallbackPath = Path.Combine(Path.GetTempPath(), "performance.log");
                Console.WriteLine($"[ConsoleLogger] Using fallback path: {fallbackPath}");
                return fallbackPath;
            }
        }
    }
}
