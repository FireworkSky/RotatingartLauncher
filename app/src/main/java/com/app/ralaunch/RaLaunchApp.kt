package com.app.ralaunch

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
import kotlin.system.exitProcess

/**
 * 应用程序 Application 类 (Kotlin 重构版)
 *
 * 使用 Koin DI 框架管理依赖
 */
class RaLaunchApp : Application(), KoinComponent {

    companion object {
        private const val TAG = "RaLaunchApp"

        @Volatile
        private var instance: RaLaunchApp? = null

        // ... Keep track of the last active screen for advanced crash reporting ...
        var lastResumedActivityName: String = "None"

        /**
         * 获取全局 Application 实例
         */
        @JvmStatic
        fun getInstance(): RaLaunchApp = instance
            ?: throw IllegalStateException("Application not initialized")

        /**
         * 获取全局 Context（兼容旧代码）
         */
        @JvmStatic
        fun getAppContext(): Context = getInstance().applicationContext
    }

    // 延迟注入（在 Koin 初始化后才能使用）
    private val _vibrationManager: VibrationManager by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()

    override fun onCreate() {
        // ... 1. Initialize the ultimate crash catchers BEFORE anything else can crash ...
        setupGlobalCrashCatcher()
        setupComponentErrorTrackers()

        super.onCreate()
        instance = this

        // 1. 初始化密度适配（必须最先）
        DensityAdapter.init(this)

        // 2. 初始化 Koin DI（必须在使用 inject 之前）
        KoinInitializer.init(this)

        // 3. 应用主题设置
        applyThemeFromSettings()

        // 4. 初始化崩溃捕获
        initCrashHandler()

        // 5. 后台安装补丁
        installPatchesInBackground()

        // 6. 设置环境变量
        setupEnvironmentVariables()
    }

    // ... ULTIMATE COMPONENT ERROR TRACKER ...
    private fun setupComponentErrorTrackers() {
        // ... 1. Track Activity Lifecycle to know WHICH screen caused the crash ...
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                lastResumedActivityName = activity.javaClass.simpleName
                Log.d(TAG, "Current Active Screen: $lastResumedActivityName")
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // ... 2. Prevent RxJava UndeliverableExceptions from crashing the app (if RxJava is used) ...
        try {
            val rxPluginsClass = Class.forName("io.reactivex.plugins.RxJavaPlugins")
            val consumerClass = Class.forName("io.reactivex.functions.Consumer")
            val method = rxPluginsClass.getMethod("setErrorHandler", consumerClass)
            
            val handler = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.classLoader,
                arrayOf(consumerClass)
            ) { _, _, args ->
                val error = args[0] as? Throwable
                Log.e(TAG, "Unhandled RxJava Error Caught: ${error?.message}", error)
                null
            }
            method.invoke(null, handler)
        } catch (e: Exception) {
            // ... Safe to ignore if RxJava is not in the project ...
        }

        // ... 3. Main Thread (UI) lag/block warning (Simple ANR detection) ...
        Looper.getMainLooper().setMessageLogging { logMessage ->
            if (logMessage.startsWith(">>>>> Dispatching to")) {
                // ... ANR timer could be implemented here ...
            }
        }
    }

    // ... ROBUST GLOBAL CRASH CATCHER ...
    private fun setupGlobalCrashCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val appVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) { "Unknown" }

                // ... Build a highly detailed crash report ...
                val crashReport = buildString {
                    appendLine("\n=========================================")
                    appendLine("CRASH TIME: ${Date()}")
                    appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendLine("APP VERSION: $appVersion")
                    appendLine("LAST ACTIVE SCREEN: $lastResumedActivityName")
                    appendLine("THREAD: ${thread.name}")
                    appendLine("ERROR TYPE: ${exception.javaClass.name}")
                    appendLine("MESSAGE: ${exception.message}")
                    appendLine("CAUSE: ${exception.cause?.message}")
                    appendLine("STACKTRACE:")
                    exception.stackTrace.forEach { appendLine("  at $it") }
                    
                    var cause = exception.cause
                    while (cause != null) {
                        appendLine("\nCAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.forEach { appendLine("  at $it") }
                        cause = cause.cause
                    }
                    appendLine("=========================================\n")
                }

                // ... Save to internal storage (always works) ...
                val internalLogFile = File(filesDir, "FATAL_CRASH.txt")
                internalLogFile.appendText(crashReport)

                // ... Save to external storage (Android/data/.../files/) so dev can read via PC without Root ...
                val externalDir = getExternalFilesDir(null)
                if (externalDir != null) {
                    val externalLogFile = File(externalDir, "FATAL_CRASH_ACCESSIBLE.txt")
                    externalLogFile.appendText(crashReport)
                }

                Log.e(TAG, "FATAL CRASH INTERCEPTED:\n$crashReport")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            } finally {
                // ... Pass it to Fishnet or System default handler ...
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
            }
        }
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
            val settingsManager = SettingsAccess
            val nightMode = when (settingsManager.themeMode) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply theme: ${e.message}")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /**
     * 初始化崩溃捕获
     */
    private fun initCrashHandler() {
        val logDir = File(filesDir, "crash_logs").apply {
            if (!exists()) mkdirs()
        }
        Fishnet.init(applicationContext, logDir.absolutePath)
    }

    /**
     * 后台安装补丁
     */
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

    /**
     * 设置环境变量
     */
    private fun setupEnvironmentVariables() {
        try {
            Os.setenv("PACKAGE_NAME", packageName, true)

            val externalStorage = android.os.Environment.getExternalStorageDirectory()
            externalStorage?.let {
                Os.setenv("EXTERNAL_STORAGE_DIRECTORY", it.absolutePath, true)
                Log.d(TAG, "EXTERNAL_STORAGE_DIRECTORY: ${it.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables: ${e.message}")
        }
    }

    // ==================== 兼容旧代码的访问方法 ====================

    /**
     * 获取 VibrationManager
     */
    fun getVibrationManager(): VibrationManager = _vibrationManager

    /**
     * 获取 ControlPackManager
     */
    fun getControlPackManager(): ControlPackManager = _controlPackManager

    /**
     * 获取 PatchManager
     */
    fun getPatchManager(): PatchManager? = _patchManager
}
