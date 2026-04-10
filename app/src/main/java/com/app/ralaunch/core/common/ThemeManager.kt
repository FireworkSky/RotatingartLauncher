package com.app.ralaunch.core.common

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import com.app.ralaunch.shared.core.config.IThemeManager
import com.app.ralaunch.shared.core.config.ThemeConfig
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.shared.core.model.domain.BackgroundType
import com.app.ralaunch.shared.core.model.domain.ThemeMode

/**
 * 主题管理器 - Android 实现
 * 负责管理主题应用（主题模式、背景设置、动态颜色等）
 * 
 * 实现 shared 模块的 IThemeManager 接口
 */
class ThemeManager(private val activity: AppCompatActivity) : IThemeManager {

    companion object {
        private const val TAG = "ThemeManager"
    }

    private val settingsManager: SettingsAccess = SettingsAccess
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
        when (settingsManager.themeMode) {
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
            AppLogger.info(TAG, "动态颜色主题已应用")
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用动态颜色失败: ${e.message}", e)
        }
    }

    /**
     * 应用自定义主题颜色
     */
    fun applyCustomThemeColor(color: Int) {
        try {
            settingsManager.themeColor = color
            dynamicColorManager.applyCustomThemeColor(activity, color)
            AppLogger.info(TAG, "自定义主题颜色已应用: ${String.format("#%06X", 0xFFFFFF and color)}")
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用自定义主题颜色失败: ${e.message}", e)
        }
    }

    /**
     * 应用背景设置
     * 
     * 注：背景图片和视频由 Compose 层处理（AppThemeState + BackgroundLayer）
     * 此方法仅设置 window 级别的背景色作为底层
     */
    fun applyBackgroundFromSettings() {
        val type = settingsManager.backgroundType
        AppLogger.info(TAG, "applyBackgroundFromSettings - type: $type")

        when (type) {
            BackgroundType.VIDEO -> applyVideoBackground()
            BackgroundType.IMAGE -> applyImageBackground()
            BackgroundType.COLOR -> applyColorBackground()
            BackgroundType.DEFAULT -> applyDefaultBackground()
        }
    }

    private fun applyVideoBackground() {
        AppLogger.info(TAG, "背景类型: video，设置透明底层（视频由 Compose 渲染）")
        // 视频背景由 Compose 的 VideoBackground 组件处理
        // 这里设置透明背景，让 Compose 层的视频可见
        activity.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun applyImageBackground() {
        val imagePath = settingsManager.backgroundImagePath
        AppLogger.info(TAG, "背景类型: image，路径: $imagePath（图片由 Compose 渲染）")
        // 图片背景由 Compose 的 BackgroundLayer 组件处理
        // 这里设置透明背景，让 Compose 层的图片可见
        activity.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun applyColorBackground() {
        val color = settingsManager.backgroundColor
        activity.window?.setBackgroundDrawable(ColorDrawable(color))
        AppLogger.info(TAG, "纯色背景已应用")
    }

    private fun applyDefaultBackground() {
        val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val background = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            ColorDrawable(0xFF121212.toInt())
        } else {
            ColorDrawable(0xFFF5F5F5.toInt())
        }
        activity.window?.setBackgroundDrawable(background)
        AppLogger.info(TAG, "默认纯色背景已应用")
    }

    /**
     * 检查是否使用视频背景
     */
    val isVideoBackground: Boolean
        get() = settingsManager.backgroundType == BackgroundType.VIDEO

    /**
     * 获取视频背景路径
     */
    val videoBackgroundPath: String?
        get() = settingsManager.backgroundVideoPath

    /**
     * 处理配置变化（主题切换）
     */
    fun handleConfigurationChanged(newConfig: Configuration) {
        if (settingsManager.themeMode != ThemeMode.FOLLOW_SYSTEM) return

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

    // ==================== IThemeManager 接口实现 ====================

    override fun getThemeConfig(): ThemeConfig {
        return ThemeConfig(
            mode = settingsManager.themeMode,
            primaryColor = settingsManager.themeColor,
            backgroundType = settingsManager.backgroundType,
            backgroundColor = settingsManager.backgroundColor,
            backgroundImagePath = settingsManager.backgroundImagePath,
            backgroundVideoPath = settingsManager.backgroundVideoPath,
            backgroundOpacity = settingsManager.backgroundOpacity
        )
    }

    override fun setThemeMode(mode: ThemeMode) {
        settingsManager.themeMode = mode
        applyNightMode()
    }

    override fun setPrimaryColor(color: Int) {
        applyCustomThemeColor(color)
    }

    override fun setBackgroundType(type: BackgroundType) {
        settingsManager.backgroundType = type
        applyBackgroundFromSettings()
    }

    override fun setBackgroundImagePath(path: String?) {
        settingsManager.backgroundImagePath = path ?: ""
    }

    override fun setBackgroundVideoPath(path: String?) {
        settingsManager.backgroundVideoPath = path ?: ""
    }

    override fun setBackgroundOpacity(opacity: Int) {
        settingsManager.backgroundOpacity = opacity
    }

    override fun applyTheme() {
        applyThemeFromSettings()
    }

    override fun applyBackground() {
        applyBackgroundFromSettings()
    }
}
