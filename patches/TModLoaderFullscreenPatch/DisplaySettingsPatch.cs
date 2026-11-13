using System;
using System.Globalization;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Terraria;

namespace TModLoaderFullscreenPatch;

internal static class DisplaySettingsPatch
{
    public static void UpdateDisplaySettingsPostfix(Main __instance)
    {
        SyncToBackbuffer(__instance.GraphicsDevice);
    }

    // SetDisplayMode 是 TML 写 screenWidth/screenHeight 和 PreferredBackBuffer 的入口。
    // 主菜单路径可能不走 UpdateDisplaySettings，所以这里也要同步一次。
    public static void UIScalePrefix(ref float value)
    {
        if (TryReadScale(out float scale))
        {
            value *= scale;
        }
    }

    private static int _originalMinScreenW;
    private static int _originalMinScreenH;

    public static void SetDisplayModePrefix()
    {
        if (!TryReadScale(out float scale))
        {
            return;
        }

        // TML 默认 minScreenH = 720，SetDisplayMode 会把半分辨率高度
        // （468/540）强制抬回 720。这里不能直接设成 1：UIWorkshopHub 等界面
        // 会用 minScreenH 计算布局高度。按渲染倍率缩小即可。
        if (_originalMinScreenH <= 0)
        {
            _originalMinScreenW = Main.minScreenW > 0 ? Main.minScreenW : 800;
            _originalMinScreenH = Main.minScreenH > 0 ? Main.minScreenH : 720;
        }

        Main.minScreenW = Math.Max(1, (int)(_originalMinScreenW * scale));
        Main.minScreenH = Math.Max(1, (int)(_originalMinScreenH * scale));
    }

    public static void SetDisplayModePostfix()
    {
        if (!TryReadScale(out _))
        {
            return;
        }

        GraphicsDevice? device = Main.graphics?.GraphicsDevice;
        if (device != null)
        {
            SyncToBackbuffer(device);
        }
    }

    private static void SyncToBackbuffer(GraphicsDevice? device)
    {
        if (device == null || !TryReadScale(out _))
        {
            return;
        }

        PresentationParameters parameters = device.PresentationParameters;
        if (parameters.BackBufferWidth <= 0 || parameters.BackBufferHeight <= 0)
        {
            return;
        }

        // TML 会把 screenWidth/screenHeight 写成它自己的窗口配置值（例如 1102x720），
        // 但实际 FBO 已经是半分辨率（1102x468）。这里强制同步，避免按更高的尺寸
        // 渲染后下半部分被裁掉。
        Main.screenWidth = parameters.BackBufferWidth;
        Main.screenHeight = parameters.BackBufferHeight;

        GraphicsDeviceManager? manager = Main.graphics;
        if (manager != null)
        {
            manager.PreferredBackBufferWidth = parameters.BackBufferWidth;
            manager.PreferredBackBufferHeight = parameters.BackBufferHeight;
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
