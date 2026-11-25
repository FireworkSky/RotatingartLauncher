package com.app.ralaunch.feature.terraria

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects an already-installed official Terraria Android app.
 *
 * This only discovers and opens the installed app. It does not copy its APK,
 * access private app data, inject code, or provide tModLoader compatibility.
 */
object TerrariaAndroidDetector {
    const val OFFICIAL_PACKAGE = "com.and.games505.TerrariaPaid"

    sealed interface Detection {
        data class Installed(
            val packageName: String,
            val versionName: String?,
            val versionCode: Long,
            val launchIntent: Intent?
        ) : Detection

        data object NotInstalled : Detection
        data class Error(val message: String) : Detection
    }

    fun detect(context: Context): Detection {
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    OFFICIAL_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(OFFICIAL_PACKAGE, 0)
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

            Detection.Installed(
                packageName = OFFICIAL_PACKAGE,
                versionName = info.versionName,
                versionCode = versionCode,
                launchIntent = pm.getLaunchIntentForPackage(OFFICIAL_PACKAGE)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Detection.NotInstalled
        } catch (e: Exception) {
            Detection.Error(e.message ?: "Não foi possível verificar o Terraria.")
        }
    }

    /** Opens the installed Terraria app through Android's normal app launcher. */
    fun launch(context: Context): Boolean {
        val intent = (detect(context) as? Detection.Installed)?.launchIntent ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
