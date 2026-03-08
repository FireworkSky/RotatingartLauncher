package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Device Optimization Engine (The Central Hub)
 * 
 * Intelligently applies aggressive hacks only on older Android devices 
 * (like Android 7.1.1) to prevent SDL Audio crashes.
 */
object DeviceOptimizationEngine {

    private const val TAG = "OptimizationEngine"

    // ===================================================================
    // ... MAIN TRIGGER: Call this ONCE before game launch ...
    // ===================================================================
    fun prepareGameEnvironment(context: Context) {
        Log.i(TAG, "⚙️ Initializing Device Optimization Engine for API ${Build.VERSION.SDK_INT}...")

        try {
            // ... LEGACY DEVICE HACKS (Android 8.0 and below) ...
            // Build.VERSION_CODES.O = API 26 (Android 8.0)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Log.w(TAG, "📱 Legacy Device Detected. Applying aggressive survival hacks!")
                
                // ... Apply SDL Audio crash fixes (The OPPO CPH1723 Float Bug) ...
                SDLOptimizer.applyAudioFixes(context)
            } else {
                Log.i(TAG, "📱 Modern Device Detected. Skipping legacy audio hacks.")
            }

            Log.i(TAG, "✅ All systems GO! Game environment is ready.")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical failure in Optimization Engine: ${e.message}")
        }
    }
}
