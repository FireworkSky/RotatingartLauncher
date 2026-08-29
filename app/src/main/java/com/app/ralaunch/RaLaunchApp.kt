package com.app.ralaunch

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.system.Os
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.di.KoinInitializer
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import timber.log.Timber
import com.app.ralaunch.core.model.ThemeMode
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.RuntimeManager
import com.kyant.fishnet.Fishnet
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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. 初始化密度适配（必须最先）
        DensityAdapter.init(this)

        // 2. 初始化 Koin DI（必须在使用 inject 之前）
        KoinInitializer.init(this)

        // 3. 加载应用配置（settings.json）
        AppConfig.load()

        // 4. 初始化日志系统（Timber：Logcat + 文件日志）
        AppLogger.init(this)

        // 5. 启动时迁移旧运行时布局
        RuntimeManager.initialize(this.filesDir)

        // 6. 应用主题设置
        applyThemeFromSettings()

        // 7. 初始化崩溃捕获
        initCrashHandler()

        // 8. 后台安装补丁
        installPatchesInBackground()

        // 9. 设置环境变量
        setupEnvironmentVariables()

        applyIconAlias()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.applyLanguage(this)
        applyIconAlias()
    }


    private fun applyThemeFromSettings() {
        try {
            val nightMode = when (AppConfig.c.themeMode) {
                ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            Timber.e("Failed to apply theme: ${e.message}")
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
                    Timber.e("Failed to install patches: ${e.message}")
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
                Timber.d("EXTERNAL_STORAGE_DIRECTORY: ${it.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e("Failed to set environment variables: ${e.message}")
        }
    }

    /**
     * 根据系统主题切换应用图标
     */
    private fun applyIconAlias() {
        try {
            val pm = packageManager
            val pkg = packageName
            val isDarkMode = isSystemInDarkMode()
            val targetAlias = if (isDarkMode) {
                "$pkg.MainActivityDark"
            } else {
                "$pkg.MainActivityLight"
            }

            // 获取当前状态
            val lightState = pm.getComponentEnabledSetting(
                ComponentName(pkg, "$pkg.MainActivityLight")
            )
            val darkState = pm.getComponentEnabledSetting(
                ComponentName(pkg, "$pkg.MainActivityDark")
            )

            // 检查是否已经是正确状态
            val lightShouldBeEnabled = targetAlias == "$pkg.MainActivityLight"
            val darkShouldBeEnabled = targetAlias == "$pkg.MainActivityDark"

            val lightIsCorrect = if (lightShouldBeEnabled) {
                lightState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                lightState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            val darkIsCorrect = if (darkShouldBeEnabled) {
                darkState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                darkState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            // 如果状态都正确，直接返回
            if (lightIsCorrect && darkIsCorrect) {
                return
            }

            // 执行切换
            listOf(".MainActivityLight", ".MainActivityDark").forEach { alias ->
                val fullAlias = if (alias.startsWith(".")) pkg + alias else alias
                val shouldEnable = fullAlias == targetAlias
                val state = if (shouldEnable) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                pm.setComponentEnabledSetting(
                    ComponentName(pkg, fullAlias),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Exception) {
            // 静默处理异常
        }
    }

    /**
     * 检查系统是否处于暗色模式
     */
    private fun isSystemInDarkMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uiMode = resources.configuration.uiMode
            (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            false
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
