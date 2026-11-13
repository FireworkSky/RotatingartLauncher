using System;
using System.Linq;
using System.Reflection;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Terraria;

namespace TModLoaderFullscreenPatch;

/// <summary>
/// Compatibility with SilkyUIFramework (used by ImproveGame 更好的体验).
/// SilkyUI's PlayerInputHelper.SetZoom rewrites Main.screenWidth/screenHeight from
/// PlayerInput._originalScreenWidth/Height, which may still be a stale full-resolution
/// value. Recompute them from the actual half-resolution backbuffer after SetZoom.
/// </summary>
internal static class SilkyUICompatPatch
{
    private const string AssemblyName = "SilkyUIFramework";

    public static void TryPatch(Harmony harmony)
    {
        var assembly = AppDomain.CurrentDomain.GetAssemblies()
            .FirstOrDefault(candidate => candidate.GetName().Name == AssemblyName);
        if (assembly != null)
        {
            PatchAssembly(harmony, assembly);
        }

        AppDomain.CurrentDomain.AssemblyLoad += (_, args) =>
        {
            if (args.LoadedAssembly.GetName().Name == AssemblyName)
            {
                PatchAssembly(harmony, args.LoadedAssembly);
            }
        };
    }

    private static void PatchAssembly(Harmony harmony, Assembly assembly)
    {
        try
        {
            var helper = assembly.GetType("SilkyUIFramework.Helper.PlayerInputHelper");
            var setZoom = helper?.GetMethod(
                "SetZoom",
                BindingFlags.Static | BindingFlags.Public,
                null,
                new[] { typeof(Vector2) },
                null
            );
            if (setZoom == null || setZoom.GetCustomAttribute<HarmonyPatch>() != null)
            {
                return;
            }

            harmony.Patch(
                setZoom,
                postfix: new HarmonyMethod(typeof(SilkyUICompatPatch), nameof(SetZoomPostfix))
            );
            Console.WriteLine("[TModLoaderFullscreenPatch] SilkyUIFramework screen-size compatibility installed.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TModLoaderFullscreenPatch] SilkyUI compatibility patch failed: {ex.Message}");
        }
    }

    public static void SetZoomPostfix(Vector2 scale)
    {
        if (!IsRenderScaleActive())
        {
            return;
        }

        GraphicsDevice? device = Main.graphics?.GraphicsDevice;
        if (device == null)
        {
            return;
        }

        PresentationParameters parameters = device.PresentationParameters;
        Main.screenWidth = Math.Max(1, (int)(parameters.BackBufferWidth * scale.X));
        Main.screenHeight = Math.Max(1, (int)(parameters.BackBufferHeight * scale.Y));
    }

    private static bool IsRenderScaleActive()
    {
        return !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("RAL_RENDER_SCALE"));
    }
}
