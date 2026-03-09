package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log

object DeviceOptimizationEngine {

    private const val TAG = "OptimizationEngine"

    fun prepareGameEnvironment(context: Context) {
        Log.i(TAG, "⚙️ Initializing Device Optimization Engine for API ${Build.VERSION.SDK_INT}...")

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Log.w(TAG, "📱 Legacy Device Detected. Applying aggressive survival hacks!")
                SDLOptimizer.applyAudioFixes(context)
            }

            // ... FIX STRETCHED GRAPHICS ...
            // (Bạn có thể bỏ qua dòng này nếu đang tự set env trong file khác)
            // forceCorrectAspectRatio(context) 

            // =======================================================
            // KÍCH HOẠT TURBO PATCH LOADER (NÉM CONTEXT VÀO ĐÂY)
            // =======================================================
            TurboPatchLoader.injectTurboWrapper(context)

            Log.i(TAG, "✅ All systems GO! Game environment is ready.")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical failure in Optimization Engine: ${e.message}")
        }
    }
}
