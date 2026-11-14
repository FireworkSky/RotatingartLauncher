using System;
using System.Globalization;
using System.Runtime.CompilerServices;
using Microsoft.Xna.Framework.Graphics;

namespace TModLoaderRenderScalePatch;

internal static class RenderScalePatch
{
    private static readonly ConditionalWeakTable<GraphicsDevice, int[]> BaseSizes = new();
    private static int _logCount;

    private static void LogReset(string stage, GraphicsDevice device, PresentationParameters parameters)
    {
        if (_logCount >= 12)
        {
            return;
        }
        _logCount++;
        var viewport = device.Viewport;
        Console.WriteLine(
            $"[TModLoaderRenderScalePatch] Reset {stage}: " +
            $"params={parameters.BackBufferWidth}x{parameters.BackBufferHeight}, " +
            $"viewport={viewport.X},{viewport.Y} {viewport.Width}x{viewport.Height}"
        );
    }

    // FNA 最终会用这里传入的 PresentationParameters 创建/重置 backbuffer。
    // Reset 可能被调用多次，而且传入值可能已经被前一次修改过，所以不能基于
    // 当前值反复乘 scale。这里记住每个 GraphicsDevice 的原始全尺寸，之后
    // 每次 Reset 都强制使用 base * scale。
    public static void ResetPrefix(
        GraphicsDevice __instance,
        PresentationParameters presentationParameters
    )
    {
        if (!TryReadScale(out float scale))
        {
            return;
        }

        int[] baseSize = BaseSizes.GetValue(
            __instance,
            _ => new[] { presentationParameters.BackBufferWidth, presentationParameters.BackBufferHeight }
        );

        int currentWidth = presentationParameters.BackBufferWidth;
        int currentHeight = presentationParameters.BackBufferHeight;

        // 用实际 EGL Surface 尺寸作为基准，而不是游戏传入的 2340x1080。
        // 在部分设备上窗口/系统栏会让真实 Surface 小于物理屏（例如 2204x937），
        // 用 2340x1080 推半分辨率会和最终 Present 的目标尺寸不匹配。
        if (EglSurfaceSize.TryGetCurrentSurfaceSize(out int surfaceWidth, out int surfaceHeight))
        {
            baseSize[0] = surfaceWidth;
            baseSize[1] = surfaceHeight;
        }
        else if (currentWidth > baseSize[0] || currentHeight > baseSize[1])
        {
            baseSize[0] = currentWidth;
            baseSize[1] = currentHeight;
        }

        presentationParameters.BackBufferWidth = Math.Max(1, (int)(baseSize[0] * scale));
        presentationParameters.BackBufferHeight = Math.Max(1, (int)(baseSize[1] * scale));

        LogReset("prefix", __instance, presentationParameters);
    }

    public static void ResetPostfix(
        GraphicsDevice __instance,
        PresentationParameters presentationParameters
    )
    {
        LogReset("postfix", __instance, presentationParameters);
    }

    private static bool TryReadScale(out float scale)
    {
        scale = 1.0f;
        string? raw = Environment.GetEnvironmentVariable("RAL_RENDER_SCALE");
        if (string.IsNullOrWhiteSpace(raw))
        {
            return false;
        }

        if (!float.TryParse(raw, NumberStyles.Float, CultureInfo.InvariantCulture, out float parsed))
        {
            return false;
        }

        if (parsed <= 0.1f || parsed >= 1.0f)
        {
            return false;
        }

        scale = parsed;
        return true;
    }
}
