package com.app.ralaunch.manager;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.AppCompatActivity;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * 主题管理器
 * 负责管理主题应用（主题模式、背景设置等）
 */
public class ThemeManager {
    private final AppCompatActivity activity;
    private final SettingsManager settingsManager;
    
    public ThemeManager(AppCompatActivity activity) {
        this.activity = activity;
        this.settingsManager = SettingsManager.getInstance(activity);
    }
    
    /**
     * 从设置中应用主题
     */
    public void applyThemeFromSettings() {
        int themeMode = settingsManager.getThemeMode(); // 0=跟随系统, 1=深色, 2=浅色
        
        switch (themeMode) {
            case 0: // 跟随系统
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // 深色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2: // 浅色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }
    
    /**
     * 应用背景设置
     */
    public void applyBackgroundFromSettings() {
        String type = settingsManager.getBackgroundType();
        Drawable background = null;

        switch (type) {
            case "default":
                // 使用默认背景（从主题中获取）
                try {
                    int resId = activity.getResources().getIdentifier("bg_main", "drawable", activity.getPackageName());
                    if (resId != 0) {
                        background = activity.getResources().getDrawable(resId, activity.getTheme());
                    }
                } catch (Exception e) {
                    AppLogger.error("ThemeManager", "无法加载默认背景: " + e.getMessage());
                }
                // 如果加载失败，使用白色背景
                if (background == null) {
                    background = new ColorDrawable(0xFFFFFFFF);
                }
                break;
            case "color":
                // 使用纯色背景
                int color = settingsManager.getBackgroundColor();
                background = new ColorDrawable(color);
                break;
            case "image":
                // 使用图片背景
                String imagePath = settingsManager.getBackgroundImagePath();
                if (imagePath != null && !imagePath.isEmpty()) {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                            if (bitmap != null) {
                                background = new BitmapDrawable(activity.getResources(), bitmap);
                            }
                        } catch (Exception e) {
                            AppLogger.error("ThemeManager", "无法加载背景图片: " + e.getMessage());
                        }
                    }
                }
                // 如果图片加载失败，回退到默认背景
                if (background == null) {
                    try {
                        int resId = activity.getResources().getIdentifier("bg_main", "drawable", activity.getPackageName());
                        if (resId != 0) {
                            background = activity.getResources().getDrawable(resId, activity.getTheme());
                        }
                    } catch (Exception e) {
                        AppLogger.error("ThemeManager", "无法加载默认背景: " + e.getMessage());
                    }
                    if (background == null) {
                        background = new ColorDrawable(0xFFFFFFFF);
                    }
                }
                break;
            default:
                // 默认背景
                try {
                    int resId = activity.getResources().getIdentifier("bg_main", "drawable", activity.getPackageName());
                    if (resId != 0) {
                        background = activity.getResources().getDrawable(resId, activity.getTheme());
                    }
                } catch (Exception e) {
                    AppLogger.error("ThemeManager", "无法加载默认背景: " + e.getMessage());
                }
                if (background == null) {
                    background = new ColorDrawable(0xFFFFFFFF);
                }
                break;
        }

        // 应用背景到窗口（视频背景需要单独处理，这里不处理）
        // 背景始终完全不透明，只在启动器显示
        if (background != null && activity.getWindow() != null && !"video".equals(type)) {
            activity.getWindow().setBackgroundDrawable(background);
        }
    }
    
    /**
     * 检查是否使用视频背景
     */
    public boolean isVideoBackground() {
        return "video".equals(settingsManager.getBackgroundType());
    }
    
    /**
     * 获取视频背景路径
     */
    public String getVideoBackgroundPath() {
        return settingsManager.getBackgroundVideoPath();
    }

    /**
     * 处理配置变化（主题切换）
     */
    public void handleConfigurationChanged(Configuration newConfig) {
        // 检查是否是 UI 模式改变（深色/浅色模式）
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        
        if (settingsManager.getThemeMode() == 0) {
            
            // 先关闭所有对话框，防止recreate后被恢复
            androidx.fragment.app.FragmentManager fm = activity.getSupportFragmentManager();
            for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                if (fragment instanceof androidx.fragment.app.DialogFragment) {
                    ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                }
            }
            
            // 延迟一点点，确保对话框关闭
            new android.os.Handler().postDelayed(() -> {
                // 重建Activity以应用新主题
                activity.recreate();
            }, 50);
        }
    }
}

