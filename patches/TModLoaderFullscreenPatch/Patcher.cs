using HarmonyLib;
using Microsoft.Xna.Framework.Graphics;
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

            var reset = typeof(GraphicsDevice).GetMethod(
                nameof(GraphicsDevice.Reset),
                new[] { typeof(PresentationParameters), typeof(GraphicsAdapter) }
            ) ?? throw new MissingMethodException(typeof(GraphicsDevice).FullName, nameof(GraphicsDevice.Reset));
            _harmony.Patch(
                reset,
                prefix: new HarmonyMethod(typeof(FullscreenPresentPatch), nameof(FullscreenPresentPatch.ResetPrefix))
            );

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
