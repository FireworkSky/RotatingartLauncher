using System;
using System.Runtime.InteropServices;

namespace TModLoaderRenderScalePatch;

internal static class EglSurfaceSize
{
    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr eglGetCurrentDisplay();

    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr eglGetCurrentSurface(int readDraw);

    [DllImport("libEGL.so", CallingConvention = CallingConvention.Cdecl)]
    private static extern int eglQuerySurface(IntPtr display, IntPtr surface, int attribute, out int value);

    public static bool TryGetCurrentSurfaceSize(out int width, out int height)
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
}
