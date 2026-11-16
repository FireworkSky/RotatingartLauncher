using System;
using System.Diagnostics;
using System.Threading;

namespace TModLoaderFrameRatePatch;

internal sealed class FramePacer
{
    private readonly long interval;
    private long deadline;

    internal FramePacer(int framesPerSecond) => interval = (long)Math.Ceiling((double)Stopwatch.Frequency / framesPerSecond);

    internal void Reset() => deadline = 0;

    internal void WaitForNextFrame()
    {
        long now = Stopwatch.GetTimestamp();
        if (deadline == 0 || now - deadline >= interval) deadline = now;
        while (now < deadline) {
            double remainingMs = (deadline - now) * 1000.0 / Stopwatch.Frequency;
            if (remainingMs >= 2) Thread.Sleep(Math.Max(1, (int)remainingMs - 1));
            else Thread.SpinWait(64);
            now = Stopwatch.GetTimestamp();
        }
        deadline += interval;
    }
}
