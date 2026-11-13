using System;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Terraria;

namespace TModLoaderFullscreenPatch;

internal static class FullscreenPresentPatch
{
    private static string _lastPresentSignature = "";
    private static int _presentLogCount;
    private static readonly FieldInfo? GraphicsDeviceHandle = typeof(GraphicsDevice).GetField(
        "GLDevice",
        BindingFlags.Instance | BindingFlags.NonPublic
    );

    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr eglGetCurrentDisplay();

    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr eglGetCurrentSurface(int readDraw);

    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern int eglQuerySurface(IntPtr display, IntPtr surface, int attribute, out int value);

    internal static bool TryGetCurrentSurfaceSize(out int width, out int height)
    {
        width = 0;
        height = 0;
        try
        {
            IntPtr display = eglGetCurrentDisplay();
            IntPtr surface = eglGetCurrentSurface(0x3059); // EGL_DRAW
            return display != IntPtr.Zero && surface != IntPtr.Zero &&
                   eglQuerySurface(display, surface, 0x3057, out width) != 0 &&
                   eglQuerySurface(display, surface, 0x3056, out height) != 0 &&
                   width > 0 && height > 0;
        }
        catch
        {
            return false;
        }
    }

    public static bool PresentPrefix(GraphicsDevice __instance)
    {
        try
        {
            if (SDL2.SDL.SDL_GetCurrentVideoDriver() != "Android" || GraphicsDeviceHandle == null)
            {
                return true;
            }

            if (!TryGetCurrentSurfaceSize(out int width, out int height))
            {
                return true;
            }

            var device = (IntPtr)(GraphicsDeviceHandle.GetValue(__instance) ?? IntPtr.Zero);
            if (device == IntPtr.Zero)
            {
                return true;
            }

            if (_presentLogCount < 20)
            {
                var viewport = __instance.Viewport;
                var scissor = __instance.ScissorRectangle;
                var parameters = __instance.PresentationParameters;
                var manager = Main.graphics;
                int preferredWidth = manager?.PreferredBackBufferWidth ?? -1;
                int preferredHeight = manager?.PreferredBackBufferHeight ?? -1;
                string signature =
                    $"{width}x{height}|{parameters.BackBufferWidth}x{parameters.BackBufferHeight}|" +
                    $"{viewport.Width}x{viewport.Height}|{preferredWidth}x{preferredHeight}|" +
                    $"{Main.screenWidth}x{Main.screenHeight}";
                if (signature != _lastPresentSignature)
                {
                    _lastPresentSignature = signature;
                    _presentLogCount++;
                    Console.WriteLine(
                        $"[TModLoaderFullscreenPatch] Present surface={width}x{height}, " +
                        $"backbuffer={parameters.BackBufferWidth}x{parameters.BackBufferHeight}, " +
                        $"viewport={viewport.X},{viewport.Y} {viewport.Width}x{viewport.Height}, " +
                        $"scissor={scissor.Width}x{scissor.Height}, " +
                        $"preferred={preferredWidth}x{preferredHeight}, " +
                        $"screen={Main.screenWidth}x{Main.screenHeight}."
                    );
                }
            }

            var destination = new Rectangle(0, 0, width, height);
            FNA3D.FNA3D_SwapBuffers(
                device,
                IntPtr.Zero,
                ref destination,
                __instance.PresentationParameters.DeviceWindowHandle
            );
            return false;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderFullscreenPatch] Fullscreen present failed; using FNA default: {ex.Message}");
            return true;
        }
    }
}
