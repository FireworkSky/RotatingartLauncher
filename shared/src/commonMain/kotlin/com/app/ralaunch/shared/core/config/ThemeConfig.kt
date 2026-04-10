package com.app.ralaunch.shared.core.config

import com.app.ralaunch.shared.core.model.domain.BackgroundType
import com.app.ralaunch.shared.core.model.domain.ThemeMode

/**
 * 主题配置数据类 - 跨平台
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val primaryColor: Int = DEFAULT_PRIMARY_COLOR,
    val backgroundType: BackgroundType = BackgroundType.DEFAULT,
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val backgroundImagePath: String? = null,
    val backgroundVideoPath: String? = null,
    val backgroundOpacity: Int = 0,
    val useDynamicColors: Boolean = true
) {
    companion object {
        const val DEFAULT_PRIMARY_COLOR = 0xFF6750A4.toInt()
        const val DEFAULT_BACKGROUND_COLOR = 0xFFF5F5F5.toInt()
        const val DEFAULT_DARK_BACKGROUND_COLOR = 0xFF121212.toInt()

        val Default = ThemeConfig()
    }

    /**
     * 是否为深色模式
     */
    fun isDarkMode(systemIsDark: Boolean): Boolean {
        return when (mode) {
            ThemeMode.FOLLOW_SYSTEM -> systemIsDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    }
}

/**
 * 主题管理接口 - 跨平台
 */
interface IThemeManager {
    /**
     * 获取当前主题配置
     */
    fun getThemeConfig(): ThemeConfig

    /**
     * 设置主题模式
     */
    fun setThemeMode(mode: ThemeMode)

    /**
     * 设置主题颜色
     */
    fun setPrimaryColor(color: Int)

    /**
     * 设置背景类型
     */
    fun setBackgroundType(type: BackgroundType)

    /**
     * 设置背景图片路径
     */
    fun setBackgroundImagePath(path: String?)

    /**
     * 设置背景视频路径
     */
    fun setBackgroundVideoPath(path: String?)

    /**
     * 设置背景透明度
     */
    fun setBackgroundOpacity(opacity: Int)

    /**
     * 应用主题（平台特定实现）
     */
    fun applyTheme()

    /**
     * 应用背景（平台特定实现）
     */
    fun applyBackground()
}

/**
 * 颜色工具
 */
object ColorUtils {
    /**
     * 调整颜色亮度
     */
    fun adjustBrightness(color: Int, factor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (a shl 24) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
    }

    /**
     * 计算颜色的亮度
     */
    fun luminance(color: Int): Float {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * 判断颜色是否为深色
     */
    fun isDarkColor(color: Int): Boolean {
        return luminance(color) < 0.5f
    }

    /**
     * 获取对比文字颜色（黑色或白色）
     */
    fun getContrastTextColor(backgroundColor: Int): Int {
        return if (isDarkColor(backgroundColor)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    /**
     * 颜色转 HEX 字符串
     */
    fun toHexString(color: Int): String {
        return String.format("#%08X", color)
    }

    /**
     * HEX 字符串转颜色
     */
    fun fromHexString(hex: String): Int {
        val cleanHex = hex.removePrefix("#")
        return when (cleanHex.length) {
            6 -> (0xFF shl 24) or cleanHex.toLong(16).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> 0xFF000000.toInt()
        }
    }
}
