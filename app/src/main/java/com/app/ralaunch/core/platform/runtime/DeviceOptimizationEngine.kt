package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Device Optimization Engine (The Central Hub)
 * 
 * Orchestrates all survival hacks, audio fixes, and .NET configurations.
 */
object DeviceOptimizationEngine {

    private const val TAG = "OptimizationEngine"

    // ===================================================================
    // ... MAIN TRIGGER: Call this ONCE before game launch ...
    // ===================================================================
    fun prepareGameEnvironment(context: Context, gameDirString: String?) {
        Log.i(TAG, "⚙️ Initializing Device Optimization Engine for API ${Build.VERSION.SDK_INT}...")

        try {
            // --- 1. LEGACY DEVICE HACKS (Android 8.0 and below) ---
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Log.w(TAG, "📱 Legacy Device Detected. Applying aggressive survival hacks!")
                SDLOptimizer.applyAudioFixes(context)
            } else {
                Log.i(TAG, "📱 Modern Device Detected. Skipping legacy audio hacks.")
            }

            // --- 2. AUTO-INJECT .NET RUNTIME CONFIG (Fixes 'libhostpolicy.so' crash) ---
            if (!gameDirString.isNullOrEmpty()) {
                val gameDir = File(gameDirString)
                if (gameDir.exists()) {
                    // Guess the game name from the directory (e.g., "Celeste_a00f" -> "Celeste")
                    val dirName = gameDir.name
                    val gameName = dirName.substringBefore("_") 
                    
                    val configFile = File(gameDir, "$gameName.runtimeconfig.json")
                    
                    // ONLY generate if the file is completely missing
                    if (!configFile.exists()) {
                        Log.i(TAG, "Injecting missing .NET config file for: $gameName")
                        val jsonContent = """
                        {
                          "runtimeOptions": {
                            "tfm": "net6.0",
                            "framework": {
                              "name": "Microsoft.NETCore.App",
                              "version": "6.0.0"
                            },
                            "configProperties": {
                              "System.GC.Server": false,
                              "System.GC.Concurrent": true,
                              "System.Runtime.TieredCompilation": true
                            }
                          }
                        }
                        """.trimIndent()
                        configFile.writeText(jsonContent)
                    }
                }
            }

            // --- 3. ACTIVATE TURBO RAM CLEANER ---
            TurboPatchLoader.injectTurboWrapper(context)

            Log.i(TAG, "✅ All systems GO! Game environment is highly optimized and ready.")
            
        } catch (e: Exception) {
            // ONLY ONE catch block to rule them all!
            Log.e(TAG, "❌ Critical failure in Optimization Engine: ${e.message}")
        }
    }
}
