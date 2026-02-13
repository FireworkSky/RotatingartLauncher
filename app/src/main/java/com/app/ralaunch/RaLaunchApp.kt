package com.app.ralaunch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.system.Os
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.data.migration.SettingsDataMigrator
import com.app.ralaunch.di.KoinInitializer
import com.app.ralaunch.manager.VibrationManager
import com.app.ralaunch.utils.DensityAdapter
import com.app.ralaunch.utils.LocaleManager
import com.app.ralaunch.patch.PatchManager
import com.kyant.fishnet.Fishnet
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.io.File

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
    private val _vibrationManager: VibrationManager by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()
    private val _settingsDataMigrator: SettingsDataMigrator by inject()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. 初始化密度适配（必须最先）
        DensityAdapter.init(this)

        // 2. 初始化 Koin DI（必须在使用 inject 之前）
        KoinInitializer.init(this)

        // 3. 启动前设置迁移（旧键名 -> 规范 JSON）
        migrateSettingsIfNeeded()

        // 4. 应用主题设置
        applyThemeFromSettings()

        // 5. 初始化崩溃捕获
        initCrashHandler()

        // 6. 后台安装补丁
        installPatchesInBackground()

        // 7. 设置环境变量
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

    private fun migrateSettingsIfNeeded() {
        runBlocking {
            if (!_settingsDataMigrator.needsMigration()) return@runBlocking
            _settingsDataMigrator.migrate()
                .onFailure { error ->
                    Log.e(TAG, "Settings migration failed: ${error.message}", error)
                }
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
                    com.app.ralaunch.utils.PatchExtractor.extractPatchesIfNeeded(applicationContext)
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
