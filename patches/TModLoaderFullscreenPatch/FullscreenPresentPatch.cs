using System;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;

namespace TModLoaderFullscreenPatch;

internal static class FullscreenPresentPatch
{
    private static bool _loggedPresent;
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

    public static bool PresentPrefix(GraphicsDevice __instance)
    {
        try
        {
            if (SDL2.SDL.SDL_GetCurrentVideoDriver() != "Android" || GraphicsDeviceHandle == null)
            {
                return true;
            }

            IntPtr display = eglGetCurrentDisplay();
            IntPtr surface = eglGetCurrentSurface(0x3059); // EGL_DRAW
            if (display == IntPtr.Zero || surface == IntPtr.Zero ||
                eglQuerySurface(display, surface, 0x3057, out int width) == 0 ||
                eglQuerySurface(display, surface, 0x3056, out int height) == 0 ||
                width <= 0 || height <= 0)
            {
                return true;
            }

            var device = (IntPtr)(GraphicsDeviceHandle.GetValue(__instance) ?? IntPtr.Zero);
            if (device == IntPtr.Zero)
            {
                return true;
            }

            if (!_loggedPresent)
            {
                _loggedPresent = true;
                Console.WriteLine($"[TModLoaderFullscreenPatch] Present surface={width}x{height}, backbuffer={__instance.PresentationParameters.BackBufferWidth}x{__instance.PresentationParameters.BackBufferHeight}.");
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
