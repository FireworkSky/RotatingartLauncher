using System;
using System.Globalization;
using Terraria;

namespace TModLoaderRenderScalePatch;

internal static class DisplaySettingsPatch
{
    private static int _originalMinScreenW;
    private static int _originalMinScreenH;

    public static void SetDisplayModePrefix()
    {
        if (!TryReadScale(out _))
        {
            return;
        }

        // TML 默认 minScreenH = 720，SetDisplayMode 会把半分辨率高度
        // （468/540）强制抬回 720。这里只在 SetDisplayMode 执行期间解除下限。
        if (_originalMinScreenH <= 0)
        {
            _originalMinScreenW = Main.minScreenW > 0 ? Main.minScreenW : 800;
            _originalMinScreenH = Main.minScreenH > 0 ? Main.minScreenH : 720;
        }

        // postfix 会恢复原值；UIWorkshopHub 等界面用 minScreenH 做布局高度，
        // 不能永久改成 1。
        Main.minScreenW = 1;
        Main.minScreenH = 1;
    }

    public static void SetDisplayModePostfix()
    {
        if (!TryReadScale(out _))
        {
            return;
        }

        if (_originalMinScreenH > 0)
        {
            Main.minScreenW = _originalMinScreenW;
            Main.minScreenH = _originalMinScreenH;
        }
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
