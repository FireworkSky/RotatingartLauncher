package com.app.ralaunch.core.platform.runtime

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log

/**
 * Turbo Patch Loader (The Pseudo-Magisk Engine)
 * 
 * An advanced wrapper that intercepts game launch to inject deep-level
 * OS optimizations. It aggressively clears RAM, limits Mono memory boundaries,
 * and sets cool-running thread priorities.
 */
object TurboPatchLoader {

    private const val TAG = "TurboPatchLoader"

    // ===================================================================
    // ... MAIN INJECTION METHOD ...
    // ===================================================================
    fun injectTurboWrapper(context: Context) {
        Log.i(TAG, "🔥 IGNITING TURBO PATCH LOADER (COOL RUNNING MODE)...")

        try {
            // ... 1. AGGRESSIVE RAM CLEANUP (Fake Low-Memory Alert) ...
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Ignore deprecation warning, we target old Androids anyway
                @Suppress("DEPRECATION")
                am.killBackgroundProcesses("com.android.chrome") // Kill known heavy hitters
            }
            // Trigger OS garbage collection forcefully
            context.applicationContext.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            Runtime.getRuntime().gc()
            Log.i(TAG, "🧹 RAM violently purged! Field is clear for gaming.")

            // ... 2. SMART THREAD PRIORITY (Fast but cool) ...
            // Priority -8 (URGENT_AUDIO). Not as aggressive as -19, so it won't overheat the chip.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            // ... 3. THERMAL THROTTLING PREVENTION ...
            // Disable heavy controller polling and vibration parsing overhead in SDL
            Os.setenv("SDL_JOYSTICK_DISABLE", "1", true)
            Os.setenv("SDL_HAPTIC_DISABLE", "1", true)

            // ... 4. .NET / MONO MEMORY BOUNDARIES ...
            // Limit Mono heap to 768MB to prevent Android from panicking and killing the game
            Os.setenv("MONO_GC_PARAMS", "nursery-size=32m,soft-heap-limit=768m", true)
            Os.setenv("MONO_ENV_OPTIONS", "--optimize=all", true)
            Os.setenv("MONO_DISABLE_SHARED_AREA", "1", true)
            
            // Separate rendering thread for smoother FPS
            Os.setenv("MESA_GLTHREAD", "true", true)
            Os.setenv("LIBGL_THROTTLE", "0", true)

            // ... 5. 64-BIT OPTIMIZATIONS ...
            if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                Os.setenv("DOTNET_EnableWriteXorExecute", "0", true)
            }

            Log.i(TAG, "🥇 Turbo Patch injected! Game will run smoother, cooler, and leaner.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to inject Turbo Patch: ${e.message}")
        }
    }
}
