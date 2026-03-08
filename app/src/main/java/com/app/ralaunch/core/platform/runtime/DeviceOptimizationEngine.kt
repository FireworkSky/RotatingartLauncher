package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Device Optimization Engine
 */
object DeviceOptimizationEngine {

    private const val TAG = "OptimizationEngine"

    fun prepareGameEnvironment(context: Context) {
        Log.i(TAG, "⚙️ Initializing Device Optimization Engine for API ${Build.VERSION.SDK_INT}...")

        try {
            // ... 1. Fix Audio crashes on Old Androids ...
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                SDLOptimizer.applyAudioFixes(context)
            }

            // ... 2. FIX STRETCHED GRAPHICS (ASPECT RATIO HACK) ...
            forceCorrectAspectRatio(context)

            Log.i(TAG, "✅ All systems GO! Game environment is ready.")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical failure in Optimization Engine: ${e.message}")
        }
    }

    // ===================================================================
    // ... ANTI-STRETCH RESOLUTION FIXER ...
    // ===================================================================
    private fun forceCorrectAspectRatio(context: Context) {
        try {
            // ... 1. Tell FNA (Terraria Engine) NOT to mess with the window size ...
            Os.setenv("FNA_GRAPHICS_ENABLE_HIGHDPI", "1", true)
            
            // ... 2. Tell SDL to render in Letterbox mode (black bars on empty sides)
            // This prevents the game from being stretched/squeezed to fit long phone screens.
            Os.setenv("SDL_HINT_RENDER_LOGICAL_SIZE_MODE", "letterbox", true)

            // ... 3. CALCULATE SAFE RESOLUTION ...
            // We force the game to run at standard 16:9 HD resolution (1280x720)
            // This prevents the user from needing to change resolution in-game (which causes crashes)
            
            //Get the real size of the phone screen
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            Log.i(TAG, "📱 Phone Screen Size: ${screenWidth}x${screenHeight}")

            Os.setenv("FNA_GRAPHICS_DISPLAY_WIDTH", "1280", true)
            Os.setenv("FNA_GRAPHICS_DISPLAY_HEIGHT", "720", true)
            
            Os.setenv("SDL_VIDEO_X11_XRANDR", "0", true)
            Os.setenv("SDL_VIDEO_X11_XVIDMODE", "0", true)

            Log.i(TAG, "🖥️ Forced Game Resolution to 1280x720 to prevent crashes and stretching!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to inject resolution fixes: ${e.message}")
        }
    }
}
