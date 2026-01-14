package com.app.ralaunch.activity;

import android.view.View;

import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.manager.ThemeManager;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.view.VideoBackgroundView;
import com.app.ralaunch.R;

/**
 * 负责主题背景和视频背景的应用与释放。
 */
public class MainUiDelegate {

    public void applyBackground(MainActivity activity, ThemeManager themeManager) {
        if (themeManager != null) {
            themeManager.applyBackgroundFromSettings();
        }
    }

    public void updateVideoBackground(MainActivity activity, ThemeManager themeManager) {
        try {
            VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView == null) {
                return;
            }
            
            SettingsManager settingsManager = SettingsManager.getInstance();

            if (themeManager != null && themeManager.isVideoBackground()) {
                String videoPath = themeManager.getVideoBackgroundPath();
                if (videoPath != null && !videoPath.isEmpty()) {
                    videoBackgroundView.setVisibility(View.VISIBLE);
                    videoBackgroundView.setVideoPath(videoPath);
                    
                    // 应用透明度设置
                    // opacity值直接代表背景的透明度（100=完全显示背景）
                    int opacity = settingsManager.getBackgroundOpacity();
                    videoBackgroundView.setOpacity(opacity);
                    
                    // 应用播放速度设置
                    float speed = settingsManager.getVideoPlaybackSpeed();
                    videoBackgroundView.setPlaybackSpeed(speed);
                    
                    videoBackgroundView.start();
                    
                    AppLogger.info("MainUiDelegate", "视频背景已应用 - 透明度: " + opacity + "%, 速度: " + speed + "x");
                } else {
                    videoBackgroundView.setVisibility(View.GONE);
                    videoBackgroundView.release();
                }
            } else {
                videoBackgroundView.setVisibility(View.GONE);
                videoBackgroundView.release();
            }
        } catch (Exception e) {
            AppLogger.error("MainUiDelegate", "更新视频背景失败: " + e.getMessage());
        }
    }

    public void updateVideoBackgroundSpeed(MainActivity activity, float speed) {
        try {
            VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView != null && videoBackgroundView.getVisibility() == View.VISIBLE) {
                videoBackgroundView.setPlaybackSpeed(speed);
                AppLogger.info("MainUiDelegate", "视频播放速度已更新: " + speed + "x");
            }
        } catch (Exception e) {
            AppLogger.error("MainUiDelegate", "更新视频播放速度失败: " + e.getMessage());
        }
    }

    public void updateVideoBackgroundOpacity(MainActivity activity, int opacity) {
        try {
            VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView != null && videoBackgroundView.getVisibility() == View.VISIBLE) {
                videoBackgroundView.setOpacity(opacity);
                AppLogger.info("MainUiDelegate", "视频背景透明度已更新: " + opacity + "%");
            }
        } catch (Exception e) {
            AppLogger.error("MainUiDelegate", "更新视频背景透明度失败: " + e.getMessage());
        }
    }

    public void releaseVideoBackground(MainActivity activity) {
        VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
        if (videoBackgroundView != null) {
            videoBackgroundView.release();
        }
    }
}

