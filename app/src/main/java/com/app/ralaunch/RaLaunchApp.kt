package com.app.ralaunch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.di.KoinInitializer
import com.app.ralaunch.core.common.VibrationManager
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.feature.patch.data.PatchManager
import com.kyant.fishnet.Fishnet
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.io.File

class RaLaunchApp : Application(), KoinComponent {

    companion object {
        private const val TAG = "RaLaunchApp"

        @Volatile
        private var instance: RaLaunchApp? = null

        @JvmStatic
        fun getInstance(): RaLaunchApp = instance
            ?: throw IllegalStateException("Application not initialized")

        @JvmStatic
        fun getAppContext(): Context = getInstance().applicationContext
    }

    private val _vibrationManager: VibrationManager by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()

    override fun onCreate() {
        super.onCreate()
        instance = this

        com.app.ralaunch.core.platform.runtime.BlackBoxLogger.startRecording(this)

        val startupLogFile = File(filesDir, "startup_log.txt")
        startupLogFile.delete()

        fun writeLog(msg: String) {
            Log.i(TAG, msg)
            try { startupLogFile.appendText("$msg\n") } catch (e: Exception) { }
        }

        fun step(name: String, block: () -> Unit) {
            writeLog("▶ $name...")
            try {
                block()
                writeLog("✅ $name OK")
            } catch (e: Throwable) {
                writeLog("❌ $name FAILED: ${e.javaClass.name} - ${e.message}")
                Log.e(TAG, "Init step failed: $name", e)
            }
        }

        writeLog("=== App Start: Android ${Build.VERSION.SDK_INT} ===")
        
        step("DensityAdapter")  { DensityAdapter.init(this) }
        step("KoinInitializer") { KoinInitializer.init(this) }
        step("Theme")           { applyThemeFromSettings() }
        step("Fishnet")         { initCrashHandler() }
        step("Patches")         { installPatchesInBackground() }
        step("EnvVars")         { setupEnvironmentVariables() }

        writeLog("=== Init Complete ===")
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.applyLanguage(this)
    }

    private fun applyThemeFromSettings() {
        try {
            val nightMode = when (SettingsAccess.themeMode) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply theme: ${e.message}")
        }
    }

    private fun initCrashHandler() {
        try {
            val logDir = File(filesDir, "crash_logs").apply {
                if (!exists()) mkdirs()
            }
            Fishnet.init(applicationContext, logDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Fishnet init failed: ${e.message}")
        }
    }

    private fun installPatchesInBackground() {
        _patchManager?.let { manager ->
            Thread({
                try {
                    com.app.ralaunch.core.common.util.PatchExtractor.extractPatchesIfNeeded(applicationContext)
                    PatchManager.installBuiltInPatches(manager, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install patches: ${e.message}")
                }
            }, "PatchInstaller").start()
        }
    }

    private fun setupEnvironmentVariables() {
        try {
            Os.setenv("PACKAGE_NAME", packageName, true)
            val externalStorage = android.os.Environment.getExternalStorageDirectory()
            externalStorage?.let {
                Os.setenv("EXTERNAL_STORAGE_DIRECTORY", it.absolutePath, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables: ${e.message}")
        }
    }

    fun getVibrationManager(): VibrationManager = _vibrationManager
    fun getControlPackManager(): ControlPackManager = _controlPackManager
    fun getPatchManager(): PatchManager? = _patchManager
}
