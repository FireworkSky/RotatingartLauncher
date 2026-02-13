package com.app.ralaunch.manager

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.utils.AppLogger
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * 动态颜色管理器
 * 基于 Material You 和 Material 3 动态颜色系统
 */
class DynamicColorManager private constructor() {

    companion object {
        private const val TAG = "DynamicColorManager"
        private const val DEFAULT_COLOR = 0xFF6750A4.toInt()

        @Volatile
        private var instance: DynamicColorManager? = null

        @JvmStatic
        fun getInstance(): DynamicColorManager =
            instance ?: synchronized(this) {
                instance ?: DynamicColorManager().also { instance = it }
            }
    }

    /**
     * 应用动态颜色到 Activity
     */
    fun applyDynamicColors(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (shouldUseSystemDynamicColors()) {
                    DynamicColors.applyToActivityIfAvailable(activity)
                    AppLogger.info(TAG, "应用系统动态颜色（Android 12+）")
                    return
                }
            }

            val themeColor = SettingsAccess.themeColor
            applyCustomThemeColor(activity, themeColor)
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用动态颜色失败: ${e.message}", e)
        }
    }

    /**
     * 应用自定义主题颜色
     */
    fun applyCustomThemeColor(activity: Activity, @ColorInt seedColor: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val options = DynamicColorsOptions.Builder()
                        .setContentBasedSource(seedColor)
                        .build()
                    DynamicColors.applyToActivityIfAvailable(activity, options)
                    AppLogger.info(TAG, "✓ Material 3 动态主题准备就绪: ${String.format("#%08X", seedColor)}")
                } catch (e: NoSuchMethodError) {
                    AppLogger.warn(TAG, "setContentBasedSource 不可用，使用默认动态颜色")
                    DynamicColors.applyToActivityIfAvailable(activity)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "动态颜色应用失败: ${e.message}")
                    applyLegacyThemeColor(activity, seedColor)
                }
            } else {
                applyLegacyThemeColor(activity, seedColor)
                AppLogger.info(TAG, "Android 11 及以下，主题颜色已设置")
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用自定义主题颜色失败: ${e.message}", e)
            applyLegacyThemeColor(activity, seedColor)
        }
    }

    private fun applyLegacyThemeColor(activity: Activity, @ColorInt color: Int) {
        try {
            activity.window?.apply {
                statusBarColor = adjustColorBrightness(color, 0.8f)
                navigationBarColor = adjustColorBrightness(color, 0.9f)
            }
            AppLogger.info(TAG, "应用传统主题颜色（Android < 12）")
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用传统主题颜色失败: ${e.message}", e)
        }
    }

    private fun shouldUseSystemDynamicColors(): Boolean {
        val themeColor = SettingsAccess.themeColor
        return themeColor == DEFAULT_COLOR
    }

    private fun adjustColorBrightness(@ColorInt color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceAtMost(255)
        val g = (Color.green(color) * factor).toInt().coerceAtMost(255)
        val b = (Color.blue(color) * factor).toInt().coerceAtMost(255)
        return Color.argb(a, r, g, b)
    }

    /**
     * 检查是否支持动态颜色
     */
    fun isDynamicColorAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()

    /**
     * 根据当前模式获取合适的颜色
     */
    @ColorInt
    fun getColorForCurrentMode(context: Context, @ColorInt lightColor: Int, @ColorInt darkColor: Int): Int {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor
    }
}
