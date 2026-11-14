using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using System;
using System.Reflection;
using Terraria;

namespace TModLoaderRenderScalePatch;

public static class Patcher
{
    private static Harmony? _harmony;

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            _harmony = new Harmony("com.ralaunch.tmodloader.renderscale");

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

            var drawInterface = typeof(Main).GetMethod(
                "DrawInterface",
                BindingFlags.Instance | BindingFlags.NonPublic,
                null,
                new[] { typeof(GameTime) },
                null
            );
            if (drawInterface != null)
            {
                _harmony.Patch(
                    drawInterface,
                    prefix: new HarmonyMethod(typeof(UIScaleCompatPatch), nameof(UIScaleCompatPatch.ApplyForFrame))
                );
            }

            var drawMenu = typeof(Main).GetMethod(
                "DrawMenu",
                BindingFlags.Instance | BindingFlags.NonPublic,
                null,
                new[] { typeof(GameTime) },
                null
            );
            if (drawMenu != null)
            {
                _harmony.Patch(
                    drawMenu,
                    prefix: new HarmonyMethod(typeof(UIScaleCompatPatch), nameof(UIScaleCompatPatch.ApplyForFrame))
                );
            }

            Console.WriteLine("[TModLoaderRenderScalePatch] Half-resolution render scale enabled.");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderRenderScalePatch] Initialization failed: {ex}");
            return -1;
        }
    }
}
