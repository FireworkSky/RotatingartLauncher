package com.app.ralaunch.utils

import android.content.Context
import com.app.ralaunch.data.SettingsAccess
import kotlin.math.sqrt

/**
 * 透明度辅助工具类
 * 统一管理应用中所有透明度计算逻辑
 */
object OpacityHelper {

    /**
     * 获取背景透明度（视频/图片背景）
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0.0-1.0)
     */
    @JvmStatic
    fun getBackgroundAlpha(opacity: Int): Float = opacity / 100.0f

    /**
     * 获取UI透明度（主布局、所有UI元素）
     * @param opacity 用户设置的透明度值 (0-100)
     * @param hasBackground 是否设置了背景
     * @return alpha值 (0.0-1.0)
     */
    @JvmStatic
    fun getUiAlpha(opacity: Int, hasBackground: Boolean): Float {
        var uiAlpha = sqrt((100 - opacity) / 100.0f)
        
        // 确保UI最小透明度为0.5，让文字更清晰可读
        if (hasBackground && opacity > 50) {
            uiAlpha = maxOf(0.5f, uiAlpha)
        }
        
        return uiAlpha
    }

    /**
     * 获取对话框透明度
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0.85-1.0)，对话框需要保持高可见性
     */
    @JvmStatic
    fun getDialogAlpha(opacity: Int): Float {
        val uiAlpha = sqrt((100 - opacity) / 100.0f)
        return 0.85f + uiAlpha * 0.15f
    }

    /**
     * 获取遮罩层透明度（用于图片/视频背景上的半透明遮罩）
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0-255)，用于setBackgroundColor
     */
    @JvmStatic
    fun getOverlayAlpha(opacity: Int): Int {
        val backgroundAlpha = opacity / 100.0f
        return (backgroundAlpha * 0.5f * 255).toInt()
    }

    /**
     * 从设置获取UI透明度
     */
    @JvmStatic
    fun getUiAlphaFromSettings(context: Context?, hasBackground: Boolean): Float {
        val opacity = SettingsAccess.backgroundOpacity
        return getUiAlpha(opacity, hasBackground)
    }

    /**
     * 从设置获取对话框透明度
     */
    @JvmStatic
    fun getDialogAlphaFromSettings(context: Context?): Float {
        val opacity = SettingsAccess.backgroundOpacity
        return getDialogAlpha(opacity)
    }

    /**
     * 从设置获取背景透明度
     */
    @JvmStatic
    fun getBackgroundAlphaFromSettings(context: Context?): Float {
        val opacity = SettingsAccess.backgroundOpacity
        return getBackgroundAlpha(opacity)
    }

    /**
     * 从设置获取遮罩透明度
     */
    @JvmStatic
    fun getOverlayAlphaFromSettings(context: Context?): Int {
        val opacity = SettingsAccess.backgroundOpacity
        return getOverlayAlpha(opacity)
    }
}
