using System;
using Microsoft.Xna.Framework;
using Terraria;

namespace TModLoaderFullscreenPatch;

/// <summary>
/// Keeps TML's UI at the same physical size after the half-resolution backbuffer
/// is upscaled to the full screen.
///
/// UIScale is applied once per UI frame instead of intercepting the property setter:
/// TML and UI mods (SilkyUI etc.) save/restore UIScale themselves, and a setter hook
/// would scale those restored values repeatedly.
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

        // _lastAppliedScale is what we wrote last frame. A different wanted value
        // means the user/settings changed UI scale, so update the base.
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

        if (!float.TryParse(raw, System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out float parsed))
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
