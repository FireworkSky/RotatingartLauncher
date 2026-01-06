package com.app.ralaunch.core

import android.system.Os
import android.util.Log

object EnvVarsManager {
    const val TAG = "EnvVarsManager"

    fun quickSetEnvVars(vararg envVars: Pair<String, String?>) = quickSetEnvVars(envVars.toMap())

    fun quickSetEnvVars(envVars: Map<String, String?>) {
        for ((key, value) in envVars) {
            try {
                if (value != null) {
                    Os.setenv(key, value, true)
                    Log.d(TAG, "Set env var $key=$value")
                } else {
                    Os.unsetenv(key)
                    Log.d(TAG, "Set env var $key=(null)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set env var $key: ${e.message}")
            }
        }
    }

    fun quickSetEnvVar(key: String, value: String?) {
        try {
            if (value != null) {
                Os.setenv(key, value, true)
                Log.d(TAG, "Set env var $key=$value")
            } else {
                Os.unsetenv(key)
                Log.d(TAG, "Set env var $key=(null)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set env var $key: ${e.message}")
        }
    }
}