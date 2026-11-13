using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Terraria;
using System;
using System.Reflection;

namespace TModLoaderFullscreenPatch;

public static class Patcher
{
    private static Harmony? _harmony;

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            _harmony = new Harmony("com.ralaunch.tmodloader.fullscreen");
            var present = typeof(GraphicsDevice).GetMethod(
                nameof(GraphicsDevice.Present),
                BindingFlags.Instance | BindingFlags.Public,
                null,
                Type.EmptyTypes,
                null
            );
            if (present == null)
            {
                throw new MissingMethodException(typeof(GraphicsDevice).FullName, nameof(GraphicsDevice.Present));
            }

            _harmony.Patch(
                present,
                prefix: new HarmonyMethod(typeof(FullscreenPresentPatch), nameof(FullscreenPresentPatch.PresentPrefix))
            );

            var resetWithParams = typeof(GraphicsDevice).GetMethod(
                nameof(GraphicsDevice.Reset),
                BindingFlags.Instance | BindingFlags.Public,
                null,
                new[] { typeof(PresentationParameters) },
                null
            );
            if (resetWithParams != null)
            {
                _harmony.Patch(
                    resetWithParams,
                    prefix: new HarmonyMethod(typeof(RenderScalePatch), nameof(RenderScalePatch.ResetPrefix)),
                    postfix: new HarmonyMethod(typeof(RenderScalePatch), nameof(RenderScalePatch.ResetPostfix))
                );
            }

            var resetWithAdapter = typeof(GraphicsDevice).GetMethod(
                nameof(GraphicsDevice.Reset),
                BindingFlags.Instance | BindingFlags.Public,
                null,
                new[] { typeof(PresentationParameters), typeof(GraphicsAdapter) },
                null
            );
            if (resetWithAdapter != null)
            {
                _harmony.Patch(
                    resetWithAdapter,
                    prefix: new HarmonyMethod(typeof(RenderScalePatch), nameof(RenderScalePatch.ResetPrefix)),
                    postfix: new HarmonyMethod(typeof(RenderScalePatch), nameof(RenderScalePatch.ResetPostfix))
                );
            }

            if (resetWithParams == null && resetWithAdapter == null)
            {
                throw new MissingMethodException(typeof(GraphicsDevice).FullName, nameof(GraphicsDevice.Reset));
            }

            var viewportSetter = typeof(GraphicsDevice)
                .GetProperty(nameof(GraphicsDevice.Viewport))
                ?.GetSetMethod(nonPublic: false);
            if (viewportSetter != null)
            {
                _harmony.Patch(
                    viewportSetter,
                    prefix: new HarmonyMethod(typeof(RenderScalePatch), nameof(RenderScalePatch.ViewportPrefix))
                );
            }

            var updateDisplaySettings = typeof(Main).GetMethod(
                nameof(Main.UpdateDisplaySettings),
                BindingFlags.Instance | BindingFlags.Public,
                null,
                Type.EmptyTypes,
                null
            );
            if (updateDisplaySettings != null)
            {
                _harmony.Patch(
                    updateDisplaySettings,
                    postfix: new HarmonyMethod(typeof(DisplaySettingsPatch), nameof(DisplaySettingsPatch.UpdateDisplaySettingsPostfix))
                );
            }

            var setDisplayMode = typeof(Main).GetMethod(
                nameof(Main.SetDisplayMode),
                BindingFlags.Static | BindingFlags.Public,
                null,
                new[] { typeof(int), typeof(int), typeof(bool) },
                null
            );
            if (setDisplayMode != null)
            {
                _harmony.Patch(
                    setDisplayMode,
                    prefix: new HarmonyMethod(typeof(DisplaySettingsPatch), nameof(DisplaySettingsPatch.SetDisplayModePrefix)),
                    postfix: new HarmonyMethod(typeof(DisplaySettingsPatch), nameof(DisplaySettingsPatch.SetDisplayModePostfix))
                );
            }

            var uiScaleSetter = typeof(Main)
                .GetProperty(nameof(Main.UIScale))
                ?.GetSetMethod(nonPublic: false);
            if (uiScaleSetter != null)
            {
                _harmony.Patch(
                    uiScaleSetter,
                    prefix: new HarmonyMethod(typeof(DisplaySettingsPatch), nameof(DisplaySettingsPatch.UIScalePrefix))
                );
            }

            Console.WriteLine("[TModLoaderFullscreenPatch] Android EGL fullscreen present enabled.");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderFullscreenPatch] Initialization failed: {ex}");
            return -1;
        }
    }
}
