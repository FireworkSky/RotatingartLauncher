package com.app.ralaunch.manager;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.AppLogger;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;

/**
 * 主题管理器
 * 负责管理主题应用（主题模式、背景设置、动态颜色等）
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private final AppCompatActivity activity;
    private final SettingsManager settingsManager;
    private final DynamicColorManager dynamicColorManager;
    
    public ThemeManager(AppCompatActivity activity) {
        this.activity = activity;
        this.settingsManager = SettingsManager.getInstance();
        this.dynamicColorManager = DynamicColorManager.getInstance();
    }
    
    /**
     * 从设置中应用主题（包括深色/浅色模式和动态颜色）
     */
    public void applyThemeFromSettings() {
        // 1. 应用深色/浅色模式
        applyNightMode();
        
        // 2. 应用动态颜色主题
        applyDynamicColors();
    }
    
    /**
     * 应用深色/浅色模式
     */
    private void applyNightMode() {
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
     * 应用动态颜色主题
     */
    public void applyDynamicColors() {
        try {
            dynamicColorManager.applyDynamicColors(activity);
            AppLogger.info(TAG, "动态颜色主题已应用");
        } catch (Exception e) {
            AppLogger.error(TAG, "应用动态颜色失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 应用自定义主题颜色
     */
    public void applyCustomThemeColor(int color) {
        try {
            settingsManager.setThemeColor(color);
            dynamicColorManager.applyCustomThemeColor(activity, color);
            AppLogger.info(TAG, "自定义主题颜色已应用: " + String.format("#%06X", (0xFFFFFF & color)));
        } catch (Exception e) {
            AppLogger.error(TAG, "应用自定义主题颜色失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 应用背景设置
     */
    public void applyBackgroundFromSettings() {
        String type = settingsManager.getBackgroundType();
        android.widget.ImageView backgroundImageView = activity.findViewById(com.app.ralaunch.R.id.backgroundImageView);
        android.view.View foregroundOverlay = activity.findViewById(com.app.ralaunch.R.id.foregroundOverlay);
        
        AppLogger.info("ThemeManager", "applyBackgroundFromSettings - type: " + type + 
            ", backgroundImageView: " + (backgroundImageView != null ? "found" : "NULL") +
            ", foregroundOverlay: " + (foregroundOverlay != null ? "found" : "NULL"));
        
        // 先隐藏背景图片和遮罩
        if (backgroundImageView != null) {
            backgroundImageView.setVisibility(android.view.View.GONE);
        }
        if (foregroundOverlay != null) {
            foregroundOverlay.setVisibility(android.view.View.GONE);
        }

        switch (type) {
            case "video":
                // 使用视频背景 - 先设置占位背景，避免黑屏
                AppLogger.info("ThemeManager", "背景类型: video，设置占位背景");
                Configuration videoConfig = activity.getResources().getConfiguration();
                int videoNightMode = videoConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                Drawable videoPlaceholder;
                if (videoNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    // 深色模式：深灰色占位
                    videoPlaceholder = new ColorDrawable(0xFF1A1A1A);
                } else {
                    // 浅色模式：浅灰色占位
                    videoPlaceholder = new ColorDrawable(0xFFEEEEEE);
                }
                if (activity.getWindow() != null) {
                    activity.getWindow().setBackgroundDrawable(videoPlaceholder);
                }
                AppLogger.info("ThemeManager", "视频占位背景已应用");
                return; // 视频背景由 MainUiDelegate 处理
                
            case "image":
                // 使用图片背景
                String imagePath = settingsManager.getBackgroundImagePath();
                AppLogger.info("ThemeManager", "背景类型: image, 路径: " + imagePath);
                
                if (imagePath != null && !imagePath.isEmpty()) {
                    File imageFile = new File(imagePath);
                    AppLogger.info("ThemeManager", "图片文件存在: " + imageFile.exists() + 
                        ", ImageView存在: " + (backgroundImageView != null));
                    
                    if (imageFile.exists()) {
                        if (backgroundImageView == null) {
                            AppLogger.error("ThemeManager", "backgroundImageView 为 null，无法显示背景图片！");
                            return;
                        }
                        
                        try {
                            // 清除 Window 背景，让 ImageView 可见
                            if (activity.getWindow() != null) {
                                activity.getWindow().setBackgroundDrawable(null);
                            }
                            
                            // 清除 DecorView 背景
                            android.view.View decorView = activity.getWindow().getDecorView();
                            if (decorView != null) {
                                decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                            }
                            
                            // 清除根 FrameLayout 背景（关键！）
                            android.view.View rootFrameLayout = activity.findViewById(com.app.ralaunch.R.id.rootFrameLayout);
                            if (rootFrameLayout != null) {
                                rootFrameLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                                AppLogger.info("ThemeManager", "rootFrameLayout 背景已清除");
                            }
                            
                            // 使用 Glide 加载图片（参考 ZalithLauncher）
                            final ImageView imageView = backgroundImageView;
                            final android.view.View overlay = foregroundOverlay;
                            
                            Glide.with(activity)
                                .load(imageFile)
                                .centerCrop()  // 填充整个 ImageView
                                .transition(DrawableTransitionOptions.withCrossFade(200))  // 淡入淡出动画
                                .into(new com.bumptech.glide.request.target.CustomTarget<Drawable>() {
                                    @Override
                                    public void onResourceReady(@androidx.annotation.NonNull Drawable resource, 
                                        @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super Drawable> transition) {
                                        imageView.setImageDrawable(resource);
                                        imageView.setVisibility(android.view.View.VISIBLE);
                                        
                                        // 显示半透明遮罩，让前景内容更清晰
                                        if (overlay != null) {
                                            // 使用统一工具类计算遮罩透明度
                                            int overlayAlpha = com.app.ralaunch.utils.OpacityHelper.getOverlayAlphaFromSettings(activity);
                                            
                                            Configuration config = activity.getResources().getConfiguration();
                                            int nightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                                                // 深色模式：使用半透明黑色
                                                overlay.setBackgroundColor((overlayAlpha << 24) | 0x000000);
                                            } else {
                                                // 浅色模式：使用半透明白色
                                                overlay.setBackgroundColor((overlayAlpha << 24) | 0xFFFFFF);
                                            }
                                            overlay.setVisibility(android.view.View.VISIBLE);
                                            
                                            AppLogger.info("ThemeManager", "应用背景遮罩透明度: overlayAlpha=" + overlayAlpha);
                                        }
                                        
                                        AppLogger.info("ThemeManager", "背景图片已通过 Glide 加载并应用: " + imagePath);
                                    }
                                    
                                    @Override
                                    public void onLoadCleared(@androidx.annotation.Nullable Drawable placeholder) {
                                        AppLogger.info("ThemeManager", "Glide 清除了背景图片");
                                    }
                                    
                                    @Override
                                    public void onLoadFailed(@androidx.annotation.Nullable Drawable errorDrawable) {
                                        AppLogger.error("ThemeManager", "Glide 加载背景图片失败");
                                    }
                                });
                            
                            return;
                        } catch (Exception e) {
                            AppLogger.error("ThemeManager", "无法加载背景图片: " + e.getMessage(), e);
                        }
                    }
                }
                break;
            case "color":
                // 使用纯色背景（应用到窗口）
                int color = settingsManager.getBackgroundColor();
                if (activity.getWindow() != null) {
                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(color));
                }
                AppLogger.info("ThemeManager", "纯色背景已应用");
                return;
            case "default":
            default:
                // 使用默认纯色背景（根据主题模式）
                Configuration config = activity.getResources().getConfiguration();
                int nightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                Drawable background;
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                    background = new ColorDrawable(0xFF121212); // 深色模式默认背景
                } else {
                    background = new ColorDrawable(0xFFF5F5F5); // 浅色模式默认背景（浅灰色，更柔和）
                }
                if (activity.getWindow() != null) {
                    activity.getWindow().setBackgroundDrawable(background);
                }
                AppLogger.info("ThemeManager", "默认纯色背景已应用");
                break;
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

