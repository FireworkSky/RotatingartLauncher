package com.app.ralaunch.core.di.service

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import com.app.ralaunch.core.common.DynamicColorManager
import com.app.ralaunch.core.config.AppConfig
import timber.log.Timber
import com.app.ralaunch.core.config.ThemeConfig
import com.app.ralaunch.core.di.contract.IThemeManagerServiceV1
import com.app.ralaunch.core.model.BackgroundType
import com.app.ralaunch.core.model.ThemeMode

/**
 * 主题管理器 - Android 实现
 * 负责管理主题应用（主题模式、背景设置、动态颜色等）
 * 
 * 实现核心层的 IThemeManagerServiceV1 接口
 */
class ThemeManagerServiceV1(private val activity: AppCompatActivity) : IThemeManagerServiceV1 {

    companion object {
    }

    private val dynamicColorManager: DynamicColorManager = DynamicColorManager.getInstance()

    /**
     * 从设置中应用主题（包括深色/浅色模式和动态颜色）
     */
    fun applyThemeFromSettings() {
        applyNightMode()
        applyDynamicColors()
    }

    /**
     * 应用深色/浅色模式
     */
    private fun applyNightMode() {
        when (AppConfig.c.themeMode) {
            ThemeMode.FOLLOW_SYSTEM ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            ThemeMode.DARK ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.LIGHT ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /**
     * 应用动态颜色主题
     */
    fun applyDynamicColors() {
        try {
            dynamicColorManager.applyDynamicColors(activity)
            Timber.i("动态颜色主题已应用")
        } catch (e: Exception) {
            Timber.e(e, "应用动态颜色失败: ${e.message}")
        }
    }

    /**
     * 应用自定义主题颜色
     */
    fun applyCustomThemeColor(color: Int) {
        try {
            AppConfig.s.themeColor = color
            dynamicColorManager.applyCustomThemeColor(activity, color)
            Timber.i("自定义主题颜色已应用: ${String.format("#%06X", 0xFFFFFF and color)}")
        } catch (e: Exception) {
            Timber.e(e, "应用自定义主题颜色失败: ${e.message}")
        }
    }

    /**
     * 应用背景设置
     * 
     * 注：背景图片和视频由 Compose 层处理（AppThemeState + BackgroundLayer）
     * 此方法仅设置 window 级别的背景色作为底层
     */
    fun applyBackgroundFromSettings() {
        val type = AppConfig.c.backgroundType
        Timber.i("applyBackgroundFromSettings - type: $type")

        when (type) {
            BackgroundType.VIDEO -> applyVideoBackground()
            BackgroundType.IMAGE -> applyImageBackground()
            BackgroundType.COLOR -> applyColorBackground()
            BackgroundType.DEFAULT -> applyDefaultBackground()
        }
    }

    private fun applyVideoBackground() {
        Timber.i("背景类型: video，设置透明底层（视频由 Compose 渲染）")
        // 视频背景由 Compose 的 VideoBackground 组件处理
        // 这里设置透明背景，让 Compose 层的视频可见
        activity.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun applyImageBackground() {
        val imagePath = AppConfig.c.backgroundImagePath
        Timber.i("背景类型: image，路径: $imagePath（图片由 Compose 渲染）")
        // 图片背景由 Compose 的 BackgroundLayer 组件处理
        // 这里设置透明背景，让 Compose 层的图片可见
        activity.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun applyColorBackground() {
        val color = AppConfig.c.backgroundColor
        activity.window?.setBackgroundDrawable(ColorDrawable(color))
        Timber.i("纯色背景已应用")
    }

    private fun applyDefaultBackground() {
        val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val background = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            ColorDrawable(0xFF121212.toInt())
        } else {
            ColorDrawable(0xFFF5F5F5.toInt())
        }
        activity.window?.setBackgroundDrawable(background)
        Timber.i("默认纯色背景已应用")
    }

    /**
     * 检查是否使用视频背景
     */
    val isVideoBackground: Boolean
        get() = AppConfig.c.backgroundType == BackgroundType.VIDEO

    /**
     * 获取视频背景路径
     */
    val videoBackgroundPath: String?
        get() = AppConfig.c.backgroundVideoPath

    /**
     * 处理配置变化（主题切换）
     */
    fun handleConfigurationChanged(newConfig: Configuration) {
        if (AppConfig.c.themeMode != ThemeMode.FOLLOW_SYSTEM) return

        // 先关闭所有对话框
        activity.supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is DialogFragment) {
                fragment.dismissAllowingStateLoss()
            }
        }

        // 延迟重建 Activity
        Handler(Looper.getMainLooper()).postDelayed({
            activity.recreate()
        }, 50)
    }

    // ==================== IThemeManagerServiceV1 接口实现 ====================

    override fun getThemeConfig(): ThemeConfig {
        return ThemeConfig(
            mode = AppConfig.c.themeMode,
            primaryColor = AppConfig.c.themeColor,
            backgroundType = AppConfig.c.backgroundType,
            backgroundColor = AppConfig.c.backgroundColor,
            backgroundImagePath = AppConfig.c.backgroundImagePath,
            backgroundVideoPath = AppConfig.c.backgroundVideoPath,
            backgroundOpacity = AppConfig.c.backgroundOpacity
        )
    }

    override fun setThemeMode(mode: ThemeMode) {
        AppConfig.s.themeMode = mode
        applyNightMode()
    }

    override fun setPrimaryColor(color: Int) {
        applyCustomThemeColor(color)
    }

    override fun setBackgroundType(type: BackgroundType) {
        AppConfig.s.backgroundType = type
        applyBackgroundFromSettings()
    }

    override fun setBackgroundImagePath(path: String?) {
        AppConfig.s.backgroundImagePath = path ?: ""
    }

    override fun setBackgroundVideoPath(path: String?) {
        AppConfig.s.backgroundVideoPath = path ?: ""
    }

    override fun setBackgroundOpacity(opacity: Int) {
        AppConfig.s.backgroundOpacity = opacity
    }

    override fun applyTheme() {
        applyThemeFromSettings()
    }

    override fun applyBackground() {
        applyBackgroundFromSettings()
    }
}
