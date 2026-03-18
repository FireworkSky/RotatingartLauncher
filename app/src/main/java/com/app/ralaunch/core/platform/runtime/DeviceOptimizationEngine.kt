package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

object DeviceOptimizationEngine {

    private const val TAG = "OptimizationEngine"

    fun prepareGameEnvironment(context: Context, gameDirString: String?, rendererId: String) {
        Log.i(TAG, "Initializing Device Optimization Engine for API ${Build.VERSION.SDK_INT}...")

        try {
            SDLOptimizer.applyRenderer(rendererId)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Log.w(TAG, "Legacy Device Detected. Applying aggressive survival hacks!")
                SDLOptimizer.applyAudioFixes(context)
            } else {
                Log.i(TAG, "Modern Device Detected. Skipping legacy audio hacks.")
            }

            if (!gameDirString.isNullOrEmpty()) {
                val gameDir = File(gameDirString)
                if (gameDir.exists()) {
                    val dirName = gameDir.name
                    val gameName = dirName.substringBefore("_") 
                    val configFile = File(gameDir, "$gameName.runtimeconfig.json")
                    
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

            TurboPatchLoader.injectTurboWrapper(context)

            Log.i(TAG, "All systems GO! Game environment is highly optimized and ready.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure in Optimization Engine: ${e.message}")
        }
    }
}
