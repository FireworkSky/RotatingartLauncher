package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.data.SettingsManager;

/**
 * 透明度辅助工具类
 * 统一管理应用中所有透明度计算逻辑
 * 
 * 透明度规则：
 * - opacity=100 -> 背景完全显示(alpha=1.0), UI完全透明(alpha=0.0)
 * - opacity=0 -> 背景完全透明(alpha=0.0), UI完全显示(alpha=1.0)
 * - opacity=50 -> 两者各50%透明度
 */
public class OpacityHelper {
    
    /**
     * 获取背景透明度（视频/图片背景）
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0.0-1.0)
     */
    public static float getBackgroundAlpha(int opacity) {
        return opacity / 100.0f;
    }
    
    /**
     * 获取UI透明度（主布局、所有UI元素）
     * @param opacity 用户设置的透明度值 (0-100)
     * @param hasBackground 是否设置了背景
     * @return alpha值 (0.0-1.0)
     */
    public static float getUiAlpha(int opacity, boolean hasBackground) {
        float uiAlpha = (100 - opacity) / 100.0f;
        
        // 使用平方根函数让变化更平缓
        uiAlpha = (float) Math.sqrt(uiAlpha);
        
        // 确保UI最小透明度为0.5，让文字更清晰可读
        // opacity > 50 时开始限制，确保背景可见的同时文字清晰
        if (hasBackground && opacity > 50) {
            uiAlpha = Math.max(0.5f, uiAlpha);
        }
        
        return uiAlpha;
    }
    
    /**
     * 获取对话框透明度
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0.85-1.0)，对话框需要保持高可见性
     */
    public static float getDialogAlpha(int opacity) {
        float uiAlpha = (100 - opacity) / 100.0f;
        uiAlpha = (float) Math.sqrt(uiAlpha);
        // 对话框透明度范围：0.85-1.0，确保始终清晰可见
        return 0.85f + uiAlpha * 0.15f;
    }
    
    /**
     * 获取遮罩层透明度（用于图片/视频背景上的半透明遮罩）
     * @param opacity 用户设置的透明度值 (0-100)
     * @return alpha值 (0-255)，用于setBackgroundColor
     */
    public static int getOverlayAlpha(int opacity) {
        float backgroundAlpha = opacity / 100.0f;
        // 遮罩透明度：背景透明度越高，遮罩越淡（最大50%），提高文字对比度
        return (int) (backgroundAlpha * 0.5f * 255);
    }
    
    /**
     * 从Context获取当前透明度设置并计算UI Alpha
     * @param context Context
     * @param hasBackground 是否设置了背景
     * @return UI透明度
     */
    public static float getUiAlphaFromSettings(Context context, boolean hasBackground) {
        SettingsManager settingsManager = SettingsManager.getInstance(context);
        int opacity = settingsManager.getBackgroundOpacity();
        return getUiAlpha(opacity, hasBackground);
    }
    
    /**
     * 从Context获取当前透明度设置并计算对话框Alpha
     * @param context Context
     * @return 对话框透明度
     */
    public static float getDialogAlphaFromSettings(Context context) {
        SettingsManager settingsManager = SettingsManager.getInstance(context);
        int opacity = settingsManager.getBackgroundOpacity();
        return getDialogAlpha(opacity);
    }
    
    /**
     * 从Context获取当前透明度设置并计算背景Alpha
     * @param context Context
     * @return 背景透明度
     */
    public static float getBackgroundAlphaFromSettings(Context context) {
        SettingsManager settingsManager = SettingsManager.getInstance(context);
        int opacity = settingsManager.getBackgroundOpacity();
        return getBackgroundAlpha(opacity);
    }
    
    /**
     * 从Context获取当前透明度设置并计算遮罩Alpha
     * @param context Context
     * @return 遮罩透明度 (0-255)
     */
    public static int getOverlayAlphaFromSettings(Context context) {
        SettingsManager settingsManager = SettingsManager.getInstance(context);
        int opacity = settingsManager.getBackgroundOpacity();
        return getOverlayAlpha(opacity);
    }
}

