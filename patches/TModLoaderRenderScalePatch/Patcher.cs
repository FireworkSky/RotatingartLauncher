using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using System;
using System.Reflection;

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
