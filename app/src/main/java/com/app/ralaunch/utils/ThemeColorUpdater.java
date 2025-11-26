package com.app.ralaunch.utils;

import androidx.appcompat.app.AppCompatActivity;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.AppLogger;

/**
 * 主题颜色更新器
 * 负责动态更新 UI 元素的主题颜色
 */
public class ThemeColorUpdater {
    private final AppCompatActivity activity;
    private final SettingsManager settingsManager;
    
    public ThemeColorUpdater(AppCompatActivity activity) {
        this.activity = activity;
        this.settingsManager = SettingsManager.getInstance(activity);
    }
    
    /**
     * 动态应用主题颜色（不重新创建 Activity）
     * 只更新背景颜色
     */
    public void applyThemeColorDynamically() {
        int themeColor = settingsManager.getThemeColor();
        
        if (activity.getWindow() == null) {
            return;
        }
        
        // 将主题颜色设置为背景颜色
        android.graphics.drawable.ColorDrawable background = 
            new android.graphics.drawable.ColorDrawable(themeColor);
        activity.getWindow().setBackgroundDrawable(background);
        
        AppLogger.debug("ThemeColorUpdater", "动态应用主题颜色（背景）: " + String.format("#%06X", themeColor & 0xFFFFFF));
    }
}

