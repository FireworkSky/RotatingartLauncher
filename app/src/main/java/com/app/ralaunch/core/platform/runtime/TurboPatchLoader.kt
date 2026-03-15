package com.app.ralaunch.core.platform.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log

object TurboPatchLoader {

    private const val TAG = "TurboPatchLoader"

    fun injectTurboWrapper(context: Context) {
        Log.i(TAG, "🔥 IGNITING TURBO PATCH LOADER (COOL RUNNING MODE)...")

        // ... 1. AGGRESSIVE RAM CLEANUP ...
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                am.killBackgroundProcesses("com.android.chrome") 
            }
            // ... REMOVED INVALID onTrimMemory CALL ...
            Log.i(TAG, "🧹 System RAM aggressively purged.")
        } catch (securityEx: SecurityException) {
            Log.w(TAG, "⚠️ Missing KILL_BACKGROUND_PROCESSES permission. Skipping deep RAM purge.")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ RAM purge skipped: ${e.message}")
        }

        // ... 2. THE MAIN OPTIMIZATIONS ...
        try {
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()

            // ===================================================================
            // 🚨 CRITICAL FIX: DO NOT BOOST THE MAIN UI THREAD! 🚨
            // Spawn a detached background thread and boost its priority instead.
            // ===================================================================
            Thread {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    Log.i(TAG, "❄️ Background Thread Priority boosted to URGENT_AUDIO safely.")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set background thread priority.")
                }
            }.start()

            Os.setenv("SDL_JOYSTICK_DISABLE", "1", true)
            Os.setenv("SDL_HAPTIC_DISABLE", "1", true)

            Os.setenv("MONO_GC_PARAMS", "nursery-size=32m,soft-heap-limit=768m", true)
            Os.setenv("MONO_ENV_OPTIONS", "--optimize=all", true)
            Os.setenv("MONO_DISABLE_SHARED_AREA", "1", true)
            
            Os.setenv("MESA_GLTHREAD", "true", true)
            Os.setenv("LIBGL_THROTTLE", "0", true)

            if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                Os.setenv("DOTNET_EnableWriteXorExecute", "0", true)
            }

            Log.i(TAG, "🥇 Turbo Patch injected successfully without choking the UI!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to inject main Turbo Patch: ${e.message}")
        }
    }
}
