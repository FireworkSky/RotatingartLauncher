package com.app.ralaunch.core.platform.runtime

import android.system.Os
import android.util.Log

object EnvVarsManager {
    const val TAG = "EnvVarsManager"
    private val INTERPOLATION_PATTERN = Regex("\\{([A-Za-z0-9_\\-]+)\\}")

    fun getEnvVar(key: String): String? {
        return try {
            Os.getenv(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get env var $key: ${e.message}")
            null
        }
    }

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

    fun interpolateEnvVars(
        envVars: Map<String, String?>,
        availableInterpolations: Map<String, String>
    ): Map<String, String?> {
        if (envVars.isEmpty()) return emptyMap()

        return envVars.mapValues { (_, value) ->
            value?.let { interpolateValue(it, availableInterpolations) }
        }
    }

    fun interpolateValue(value: String, availableInterpolations: Map<String, String>): String {
        return INTERPOLATION_PATTERN.replace(value) { matchResult ->
            val key = matchResult.groupValues[1]
            availableInterpolations[key]
                ?: throw IllegalArgumentException("Missing interpolation value for {$key} in env value: $value")
        }
    }
}
