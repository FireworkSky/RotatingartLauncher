package com.app.ralaunch.manager;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.AppLogger;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;

/**
 * 动态颜色管理器
 * 基于 Material You 和 Material 3 动态颜色系统
 * 支持 Android 12+ 的系统级动态取色和自定义主题颜色
 */
public class DynamicColorManager {
    
    private static final String TAG = "DynamicColorManager";
    private static DynamicColorManager instance;
    
    private DynamicColorManager() {
    }
    
    public static DynamicColorManager getInstance() {
        if (instance == null) {
            synchronized (DynamicColorManager.class) {
                if (instance == null) {
                    instance = new DynamicColorManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 应用动态颜色到 Activity
     * 如果是 Android 12+ 且用户选择使用系统壁纸颜色，则使用系统动态颜色
     * 否则使用用户自定义的主题颜色
     * 
     * 注意：此方法在运行时立即生效（Android 12+），无需 recreate Activity
     */
    public void applyDynamicColors(@NonNull Activity activity) {
        try {
            SettingsManager settingsManager = SettingsManager.getInstance();
            
            // Android 12+ 支持系统级动态颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 检查是否使用系统壁纸颜色（可以添加一个设置选项）
                boolean useSystemColors = shouldUseSystemDynamicColors(activity);
                
                if (useSystemColors) {
                    // 使用系统壁纸提取的颜色
                    DynamicColors.applyToActivityIfAvailable(activity);
                    AppLogger.info(TAG, "应用系统动态颜色（Android 12+）");
                    return;
                }
            }
            
            // 使用用户自定义主题颜色
            int themeColor = settingsManager.getThemeColor();
            applyCustomThemeColor(activity, themeColor);
            
        } catch (Exception e) {
            AppLogger.error(TAG, "应用动态颜色失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 应用自定义主题颜色
     * 基于用户选择的颜色生成完整的 Material 3 调色板
     * 
     * Android 12+ (API 31+): 使用 Material You 动态颜色系统
     * Android 12 以下: 通过 recreate Activity 应用主题
     */
    public void applyCustomThemeColor(@NonNull Activity activity, @ColorInt int seedColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 使用 DynamicColorsOptions 应用自定义种子颜色
                // 这会生成完整的 Material 3 调色板（primary, secondary, tertiary 等）
                try {
                    DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                        .setContentBasedSource(seedColor)  // 基于内容颜色（用户选择的颜色）
                        .build();
                        
                    DynamicColors.applyToActivityIfAvailable(activity, options);
                    AppLogger.info(TAG, "✓ Material 3 动态主题准备就绪: " + String.format("#%08X", seedColor));
                } catch (NoSuchMethodError e) {
                    // 如果 setContentBasedSource 不可用，尝试使用旧版本 API
                    AppLogger.warn(TAG, "setContentBasedSource 不可用，使用默认动态颜色");
                    DynamicColors.applyToActivityIfAvailable(activity);
                } catch (Exception e) {
                    AppLogger.error(TAG, "动态颜色应用失败: " + e.getMessage());
                    // 降级到传统方式
                    applyLegacyThemeColor(activity, seedColor);
                }
            } else {
                // Android 12 以下使用传统的主题颜色方式
                applyLegacyThemeColor(activity, seedColor);
                AppLogger.info(TAG, "Android 11 及以下，主题颜色已设置");
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "应用自定义主题颜色失败: " + e.getMessage(), e);
            applyLegacyThemeColor(activity, seedColor);
        }
    }
    
    /**
     * 为 Android 12 以下版本应用主题颜色
     * 通过修改主题属性实现
     */
    private void applyLegacyThemeColor(@NonNull Activity activity, @ColorInt int color) {
        try {
            // 对于旧版本，我们可以通过设置 colorPrimary 等属性
            // 但这需要 recreate Activity 才能生效
            // 这里只设置窗口装饰颜色作为临时方案
            if (activity.getWindow() != null) {
                activity.getWindow().setStatusBarColor(adjustColorBrightness(color, 0.8f));
                activity.getWindow().setNavigationBarColor(adjustColorBrightness(color, 0.9f));
            }
            AppLogger.info(TAG, "应用传统主题颜色（Android < 12）");
        } catch (Exception e) {
            AppLogger.error(TAG, "应用传统主题颜色失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 判断是否应该使用系统动态颜色
     * 可以根据用户设置决定
     */
    private boolean shouldUseSystemDynamicColors(@NonNull Context context) {
        // 这里可以添加一个用户设置选项
        // 目前默认使用自定义颜色
        SettingsManager settingsManager = SettingsManager.getInstance();
        // 如果主题颜色是默认值（例如 0xFF6750A4 Material 3 默认紫色），则使用系统颜色
        int themeColor = settingsManager.getThemeColor();
        int defaultColor = 0xFF6750A4; // Material 3 默认主色
        
        return themeColor == defaultColor;
    }
    
    /**
     * 调整颜色亮度
     */
    private int adjustColorBrightness(@ColorInt int color, float factor) {
        int a = android.graphics.Color.alpha(color);
        int r = Math.round(android.graphics.Color.red(color) * factor);
        int g = Math.round(android.graphics.Color.green(color) * factor);
        int b = Math.round(android.graphics.Color.blue(color) * factor);
        return android.graphics.Color.argb(a,
            Math.min(r, 255),
            Math.min(g, 255),
            Math.min(b, 255));
    }
    
    /**
     * 检查是否支持动态颜色
     */
    public boolean isDynamicColorAvailable(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
               DynamicColors.isDynamicColorAvailable();
    }
    
    /**
     * 根据当前模式（深色/浅色）获取合适的颜色
     */
    @ColorInt
    public int getColorForCurrentMode(@NonNull Context context, @ColorInt int lightColor, @ColorInt int darkColor) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return (nightMode == Configuration.UI_MODE_NIGHT_YES) ? darkColor : lightColor;
    }
}

