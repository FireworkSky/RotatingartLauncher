namespace PerformanceProfiler
{
    /// <summary>
    /// 性能统计数据
    /// </summary>
    public class PerformanceStats
    {
        public double CurrentFPS { get; set; }
        public double AverageFPS { get; set; }
        public double MinFPS { get; set; }
        public double MaxFPS { get; set; }
        public double LastFrameTime { get; set; }
        public double MaxFrameTime { get; set; }
        public double AverageFrameTime { get; set; }
        public long MemoryMB { get; set; }
        public int Gen0Collections { get; set; }
        public int Gen1Collections { get; set; }
        public int Gen2Collections { get; set; }
        public MethodStats[]? TopMethods { get; set; }
    }

    /// <summary>
    /// 方法性能统计
    /// </summary>
    public class MethodStats
    {
        public string Name { get; set; } = "";
        public double AverageTimeMs { get; set; }
        public int CallCount { get; set; }
        public double TotalTimeMs { get; set; }
    }
}
