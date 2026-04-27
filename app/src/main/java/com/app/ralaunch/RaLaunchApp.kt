package com.app.ralaunch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.system.Os
import com.app.ralaunch.core.logging.AppLog
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.di.KoinInitializer
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import com.app.ralaunch.core.logging.service.AndroidFileLogger
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.model.ThemeMode
import com.app.ralaunch.feature.patch.data.PatchManager
import com.kyant.fishnet.Fishnet
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.io.File
import java.io.InvalidObjectException

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
    private val _vibrationManager: VibrationManagerServiceV1 by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()
    private val _runtimeManager: IRuntimeManagerServiceV2 by inject()
    private val _fileLogger: AndroidFileLogger by inject()
    private val _storagePathsProvider: StoragePathsProviderServiceV1 by inject()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. 初始化密度适配（必须最先）
        DensityAdapter.init(this)

        // 2. 初始化 Koin DI（必须在使用 inject 之前）
        KoinInitializer.init(this)

        // 3. 初始化进程级文件日志捕获
        initFileLogger()

        // 4. 启动时迁移旧运行时布局，仅在主进程执行一次
        runRuntimeMigrationOnAppLaunch()

        // 5. 应用主题设置
        applyThemeFromSettings()

        // 6. 初始化崩溃捕获
        initCrashHandler()

        // 7. 后台安装补丁
        installPatchesInBackground()

        // 8. 设置环境变量
        setupEnvironmentVariables()
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
                ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to apply theme: ${e.message}")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun runRuntimeMigrationOnAppLaunch() {
        if (!isMainAppProcess()) return

        try {
            _runtimeManager.migrateLegacyInstallations()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to migrate legacy runtimes on app launch: ${e.message}", e)
        }
    }

    private fun isMainAppProcess(): Boolean = getProcessName() == packageName

    private fun initFileLogger() {
        try {
            val logDir = File(_storagePathsProvider.logsDirPathFull())
            _fileLogger.start(
                logDirectory = logDir,
                clearExistingLogs = isMainAppProcess()
            )
            AppLog.i(TAG, "File log capture initialized for process=${getProcessName()}, pid=${android.os.Process.myPid()}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to initialize file log capture", e)
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
                    AppLog.e(TAG, "Failed to install patches: ${e.message}")
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
                AppLog.d(TAG, "EXTERNAL_STORAGE_DIRECTORY: ${it.absolutePath}")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to set environment variables: ${e.message}")
        }
    }

    // ==================== 兼容旧代码的访问方法 ====================

    /**
     * 获取 VibrationManagerServiceV1
     */
    fun getVibrationManager(): VibrationManagerServiceV1 = _vibrationManager

    /**
     * 获取 ControlPackManager
     */
    fun getControlPackManager(): ControlPackManager = _controlPackManager

    /**
     * 获取 PatchManager
     */
    fun getPatchManager(): PatchManager? = _patchManager
}
