package com.app.ralaunch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import java.util.Date
import com.app.ralaunch.core.platform.runtime.CrashSentinel

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
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // -----------------------------------------------------------
                // ... TYPE 1: THE ORIGINAL DEV'S REPORT (Kept 100% intact) ...
                // -----------------------------------------------------------
                val logFile = File(filesDir, "FATAL_CRASH.txt")
                logFile.appendText("\n\n=========================================\n")
                logFile.appendText("CRASH TIME: ${Date()}\n")
                logFile.appendText("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n")
                logFile.appendText("THREAD: ${thread.name}\n")
                logFile.appendText("ERROR TYPE: ${exception.javaClass.name}\n")
                logFile.appendText("MESSAGE: ${exception.message}\n")
                logFile.appendText("CAUSE: ${exception.cause?.message}\n")
                logFile.appendText("STACKTRACE:\n")
                exception.stackTrace.forEach { logFile.appendText("  at $it\n") }
                logFile.appendText("=========================================\n")

                // -----------------------------------------------------------
                // ... TYPE 2: MY NEW VIP FORENSIC REPORT (Injected here) ...
                // -----------------------------------------------------------
                try {
                    val appVersion = packageManager.getPackageInfo(packageName, 0).versionName
                    val rootCauseElement = exception.stackTrace.firstOrNull()
                    val errorFile = rootCauseElement?.fileName ?: "Unknown File"
                    val errorLine = rootCauseElement?.lineNumber?.toString() ?: "Unknown Line"
                    
                    val crashDir = File(getExternalFilesDir(null), "crashreport")
                    if (!crashDir.exists()) crashDir.mkdirs()
                    
                    val vipLogFile = File(crashDir, "APP_CRASH_${System.currentTimeMillis()}.txt")
                    
                    val vipReport = buildString {
                        appendLine("=========================================")
                        appendLine("🚨 RALAUNCHER VIP CRASH REPORT 🚨")
                        appendLine("=========================================")
                        appendLine("🕒 CRASH TIME   : ${java.util.Date()}")
                        appendLine("📱 DEVICE       : ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                        appendLine("📦 APP VERSION  : $appVersion")
                        appendLine("-----------------------------------------")
                        appendLine("📁 ERROR FILE   : $errorFile")
                        appendLine("🔢 ERROR LINE   : Line $errorLine")
                        appendLine("-----------------------------------------")
                        appendLine("💀 ERROR TYPE   : ${exception.javaClass.name}")
                        appendLine("💬 MESSAGE      : ${exception.message}")
                        appendLine("=========================================")
                        exception.stackTrace.forEach { appendLine("  at $it") }
                        
                        var cause = exception.cause
                        while (cause != null) {
                            appendLine("\n🔄 CAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                            cause.stackTrace.forEach { appendLine("  at $it") }
                            cause = cause.cause
                        }
                    }
                    vipLogFile.writeText(vipReport)
                } catch (vipException: Exception) {
                    // If VIP report fails, do nothing, let the original report survive!
                }

            } catch (e: Exception) {
                // Ignore if we can't write
            }
            // Let the app crash normally after saving the logs
            defaultHandler?.uncaughtException(thread, exception)
        }

        super.onCreate()
        instance = this

        com.app.ralaunch.core.platform.runtime.BlackBoxLogger.startRecording(this)

        val startupLogFile = File(filesDir, "startup_log.txt")
        startupLogFile.delete()
        

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
        val logDir = File(filesDir, "crash_logs").apply {
            if (!exists()) mkdirs()
        }
        Fishnet.init(applicationContext, logDir.absolutePath)
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
