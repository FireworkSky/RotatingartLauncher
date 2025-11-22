using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;

namespace PerformanceProfiler
{
    /// <summary>
    /// 性能监控器 - 跟踪FPS、帧时间、GC等关键指标
    /// </summary>
    public class PerformanceMonitor
    {
        private static PerformanceMonitor? _instance;
        public static PerformanceMonitor Instance => _instance ??= new PerformanceMonitor();

        // FPS 计数器
        private int _frameCount = 0;
        private double _elapsedTime = 0;
        private double _currentFPS = 60.0;
        private readonly List<double> _fpsHistory = new List<double>();
        private const int FPS_HISTORY_SIZE = 300; // 保留5秒历史（60FPS）

        // 帧时间监控
        private readonly Stopwatch _frameTimer = new Stopwatch();
        private double _lastFrameTime = 0;
        private double _maxFrameTime = 0;
        private double _totalFrameTime = 0;
        private int _totalFrames = 0;

        // GC 监控
        private int _lastGen0Collections = 0;
        private int _lastGen1Collections = 0;
        private int _lastGen2Collections = 0;
        private long _lastTotalMemory = 0;

        // 方法性能监控
        private readonly Dictionary<string, MethodProfile> _methodProfiles = new Dictionary<string, MethodProfile>();
        private readonly Stack<MethodProfile> _callStack = new Stack<MethodProfile>();

        // 性能警告
        private readonly List<PerformanceWarning> _warnings = new List<PerformanceWarning>();
        private const double LOW_FPS_THRESHOLD = 30.0;
        private const double HIGH_FRAME_TIME_THRESHOLD = 33.3; // 30 FPS = 33.3ms

        // 日志开关
        public bool EnableDetailedLogging { get; set; } = false;
        public bool EnableMethodProfiling { get; set; } = true;

        private PerformanceMonitor()
        {
            _frameTimer.Start();
        }

        /// <summary>
        /// 在每帧开始时调用
        /// </summary>
        public void OnFrameBegin()
        {
            _frameTimer.Restart();
        }

        /// <summary>
        /// 在每帧结束时调用
        /// </summary>
        public void OnFrameEnd(double deltaTime)
        {
            _frameTimer.Stop();
            double frameTime = _frameTimer.Elapsed.TotalMilliseconds;

            // 更新帧计数
            _frameCount++;
            _elapsedTime += deltaTime;
            _lastFrameTime = frameTime;
            _totalFrameTime += frameTime;
            _totalFrames++;

            if (frameTime > _maxFrameTime)
            {
                _maxFrameTime = frameTime;
            }

            // 每秒更新一次 FPS
            if (_elapsedTime >= 1.0)
            {
                _currentFPS = _frameCount / _elapsedTime;
                _fpsHistory.Add(_currentFPS);

                if (_fpsHistory.Count > FPS_HISTORY_SIZE)
                {
                    _fpsHistory.RemoveAt(0);
                }

                // 检查性能警告
                if (_currentFPS < LOW_FPS_THRESHOLD)
                {
                    AddWarning($"Low FPS: {_currentFPS:F1} FPS (frame time: {_lastFrameTime:F2}ms)");
                }

                // 输出性能报告
                if (EnableDetailedLogging || _currentFPS < LOW_FPS_THRESHOLD)
                {
                    LogPerformanceReport();
                }

                _frameCount = 0;
                _elapsedTime = 0;
            }

            // 检查 GC
            CheckGarbageCollection();
        }

        /// <summary>
        /// 开始分析方法
        /// </summary>
        public void BeginMethodProfile(string methodName)
        {
            if (!EnableMethodProfiling) return;

            if (!_methodProfiles.TryGetValue(methodName, out var profile))
            {
                profile = new MethodProfile(methodName);
                _methodProfiles[methodName] = profile;
            }

            profile.Begin();
            _callStack.Push(profile);
        }

        /// <summary>
        /// 结束分析方法
        /// </summary>
        public void EndMethodProfile()
        {
            if (!EnableMethodProfiling || _callStack.Count == 0) return;

            var profile = _callStack.Pop();
            profile.End();

            // 如果方法耗时超过阈值，记录警告
            if (profile.LastCallTime > 16.0) // 大于一帧时间
            {
                AddWarning($"Slow method: {profile.MethodName} took {profile.LastCallTime:F2}ms");
            }
        }

        /// <summary>
        /// 检查垃圾回收
        /// </summary>
        private void CheckGarbageCollection()
        {
            int gen0 = GC.CollectionCount(0);
            int gen1 = GC.CollectionCount(1);
            int gen2 = GC.CollectionCount(2);
            long totalMemory = GC.GetTotalMemory(false);

            if (gen0 > _lastGen0Collections || gen1 > _lastGen1Collections || gen2 > _lastGen2Collections)
            {
                string gcInfo = $"GC occurred - Gen0: {gen0 - _lastGen0Collections}, Gen1: {gen1 - _lastGen1Collections}, Gen2: {gen2 - _lastGen2Collections}";

                if (gen2 > _lastGen2Collections)
                {
                    AddWarning($"Gen2 GC detected! {gcInfo}, Memory: {totalMemory / 1024 / 1024}MB");
                }
                else if (EnableDetailedLogging)
                {
                    Console.WriteLine($"[PerformanceMonitor] {gcInfo}");
                }
            }

            _lastGen0Collections = gen0;
            _lastGen1Collections = gen1;
            _lastGen2Collections = gen2;
            _lastTotalMemory = totalMemory;
        }

        /// <summary>
        /// 添加性能警告
        /// </summary>
        private void AddWarning(string message)
        {
            var warning = new PerformanceWarning
            {
                Timestamp = DateTime.Now,
                Message = message
            };

            _warnings.Add(warning);
            Console.WriteLine($"[PerformanceMonitor] WARNING: {message}");

            // 只保留最近100条警告
            if (_warnings.Count > 100)
            {
                _warnings.RemoveAt(0);
            }
        }

        /// <summary>
        /// 输出性能报告
        /// </summary>
        private void LogPerformanceReport()
        {
            var sb = new StringBuilder();
            sb.AppendLine("========== Performance Report ==========");
            sb.AppendLine($"FPS: {_currentFPS:F1} (Min: {GetMinFPS():F1}, Max: {GetMaxFPS():F1}, Avg: {GetAverageFPS():F1})");
            sb.AppendLine($"Frame Time: {_lastFrameTime:F2}ms (Max: {_maxFrameTime:F2}ms, Avg: {GetAverageFrameTime():F2}ms)");
            sb.AppendLine($"Memory: {_lastTotalMemory / 1024 / 1024}MB");
            sb.AppendLine($"GC Collections: Gen0={_lastGen0Collections}, Gen1={_lastGen1Collections}, Gen2={_lastGen2Collections}");

            // 输出最慢的方法
            var slowestMethods = _methodProfiles.Values
                .OrderByDescending(p => p.AverageCallTime)
                .Take(5)
                .ToList();

            if (slowestMethods.Count > 0)
            {
                sb.AppendLine("\nTop 5 Slowest Methods:");
                foreach (var method in slowestMethods)
                {
                    sb.AppendLine($"  {method.MethodName}: {method.AverageCallTime:F3}ms avg ({method.CallCount} calls, {method.TotalTime:F1}ms total)");
                }
            }

            sb.AppendLine("========================================");
            Console.WriteLine(sb.ToString());
        }

        /// <summary>
        /// 获取详细的性能统计
        /// </summary>
        public string GetDetailedStats()
        {
            var sb = new StringBuilder();
            sb.AppendLine("========== Detailed Performance Statistics ==========");
            sb.AppendLine($"Current FPS: {_currentFPS:F1}");
            sb.AppendLine($"Average FPS: {GetAverageFPS():F1}");
            sb.AppendLine($"Min FPS: {GetMinFPS():F1}");
            sb.AppendLine($"Max FPS: {GetMaxFPS():F1}");
            sb.AppendLine($"Frame Time: {_lastFrameTime:F2}ms");
            sb.AppendLine($"Average Frame Time: {GetAverageFrameTime():F2}ms");
            sb.AppendLine($"Max Frame Time: {_maxFrameTime:F2}ms");
            sb.AppendLine($"Total Frames: {_totalFrames}");
            sb.AppendLine($"Total Memory: {_lastTotalMemory / 1024 / 1024}MB");
            sb.AppendLine($"GC Collections: Gen0={_lastGen0Collections}, Gen1={_lastGen1Collections}, Gen2={_lastGen2Collections}");

            sb.AppendLine($"\nRecent Warnings ({_warnings.Count}):");
            foreach (var warning in _warnings.TakeLast(10))
            {
                sb.AppendLine($"  [{warning.Timestamp:HH:mm:ss}] {warning.Message}");
            }

            sb.AppendLine("\nAll Method Profiles:");
            foreach (var method in _methodProfiles.Values.OrderByDescending(p => p.TotalTime))
            {
                sb.AppendLine($"  {method.MethodName}:");
                sb.AppendLine($"    Calls: {method.CallCount}");
                sb.AppendLine($"    Total: {method.TotalTime:F2}ms");
                sb.AppendLine($"    Average: {method.AverageCallTime:F3}ms");
                sb.AppendLine($"    Max: {method.MaxCallTime:F3}ms");
            }

            sb.AppendLine("====================================================");
            return sb.ToString();
        }

        private double GetMinFPS() => _fpsHistory.Count > 0 ? _fpsHistory.Min() : _currentFPS;
        private double GetMaxFPS() => _fpsHistory.Count > 0 ? _fpsHistory.Max() : _currentFPS;
        private double GetAverageFPS() => _fpsHistory.Count > 0 ? _fpsHistory.Average() : _currentFPS;
        private double GetAverageFrameTime() => _totalFrames > 0 ? _totalFrameTime / _totalFrames : 0;

        /// <summary>
        /// 获取当前性能统计数据（用于叠加层显示）
        /// </summary>
        public PerformanceStats GetCurrentStats()
        {
            var stats = new PerformanceStats
            {
                CurrentFPS = _currentFPS,
                AverageFPS = GetAverageFPS(),
                MinFPS = GetMinFPS(),
                MaxFPS = GetMaxFPS(),
                LastFrameTime = _lastFrameTime,
                MaxFrameTime = _maxFrameTime,
                AverageFrameTime = GetAverageFrameTime(),
                MemoryMB = _lastTotalMemory / 1024 / 1024,
                Gen0Collections = _lastGen0Collections,
                Gen1Collections = _lastGen1Collections,
                Gen2Collections = _lastGen2Collections
            };

            // 获取最慢的5个方法
            var topMethods = _methodProfiles.Values
                .OrderByDescending(p => p.AverageCallTime)
                .Take(5)
                .Select(p => new MethodStats
                {
                    Name = p.MethodName,
                    AverageTimeMs = p.AverageCallTime,
                    CallCount = p.CallCount,
                    TotalTimeMs = p.TotalTime
                })
                .ToArray();

            stats.TopMethods = topMethods;
            return stats;
        }

        private class MethodProfile
        {
            public string MethodName { get; }
            public int CallCount { get; private set; }
            public double TotalTime { get; private set; }
            public double MaxCallTime { get; private set; }
            public double LastCallTime { get; private set; }
            public double AverageCallTime => CallCount > 0 ? TotalTime / CallCount : 0;

            private readonly Stopwatch _sw = new Stopwatch();

            public MethodProfile(string methodName)
            {
                MethodName = methodName;
            }

            public void Begin()
            {
                _sw.Restart();
            }

            public void End()
            {
                _sw.Stop();
                double elapsed = _sw.Elapsed.TotalMilliseconds;

                CallCount++;
                TotalTime += elapsed;
                LastCallTime = elapsed;

                if (elapsed > MaxCallTime)
                {
                    MaxCallTime = elapsed;
                }
            }
        }

        private class PerformanceWarning
        {
            public DateTime Timestamp { get; set; }
            public string Message { get; set; } = "";
        }
    }
}
