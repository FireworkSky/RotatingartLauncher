using System;
using System.Globalization;
using Terraria;

namespace TModLoaderRenderScalePatch;

/// <summary>
/// Scales UI once per UI frame so the half-resolution backbuffer, when upscaled
/// to the full screen, does not make the in-game UI appear oversized.
/// </summary>
internal static class UIScaleCompatPatch
{
    private static float _baseScale;
    private static float _lastAppliedScale;

    public static void ApplyForFrame()
    {
        if (!TryReadRenderScale(out float renderScale))
        {
            return;
        }

        float wanted = Main.UIScaleWanted;

        // If UIScaleWanted changed, it was an explicit change from settings.
        // Otherwise it is the value we wrote last frame.
        if (_baseScale <= 0f || Math.Abs(wanted - _lastAppliedScale) > 0.001f)
        {
            _baseScale = wanted;
        }

        float target = Math.Max(0.1f, _baseScale * renderScale);
        if (Math.Abs(Main.UIScale - target) > 0.001f)
        {
            Main.UIScale = target;
        }
        _lastAppliedScale = target;
    }

    private static bool TryReadRenderScale(out float scale)
    {
        scale = 1f;
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
