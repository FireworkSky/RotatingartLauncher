package com.app.ralaunch.view;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * 视频背景视图
 * 用于在后台播放视频作为背景
 */
public class VideoBackgroundView extends SurfaceView implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener {
    private MediaPlayer mediaPlayer;
    private String videoPath;
    private boolean isPrepared = false;
    private boolean shouldPlay = false;
    private int opacity = 100; // 0-100

    public VideoBackgroundView(Context context) {
        super(context);
        init();
    }

    public VideoBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setZOrderOnTop(false);
        setZOrderMediaOverlay(false);
    }

    /**
     * 设置视频路径
     */
    public void setVideoPath(String path) {
        this.videoPath = path;
        releaseMediaPlayer();
        
        if (path != null && !path.isEmpty()) {
            File videoFile = new File(path);
            if (videoFile.exists()) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(path);
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setOnPreparedListener(this);
                    mediaPlayer.prepareAsync();
                } catch (Exception e) {
                    AppLogger.error("VideoBackgroundView", "设置视频路径失败: " + e.getMessage());
                    releaseMediaPlayer();
                }
            }
        }
    }

    /**
     * 设置透明度 (0-100)
     */
    public void setOpacity(int opacity) {
        this.opacity = Math.max(0, Math.min(100, opacity));
        // 背景始终完全不透明
        setAlpha(1.0f);
    }

    /**
     * 开始播放
     */
    public void start() {
        shouldPlay = true;
        if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * 暂停播放
     */
    public void pause() {
        shouldPlay = false;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /**
     * 停止播放
     */
    public void stop() {
        shouldPlay = false;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                AppLogger.error("VideoBackgroundView", "释放 MediaPlayer 失败: " + e.getMessage());
            }
            mediaPlayer = null;
            isPrepared = false;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(holder);
                if (shouldPlay && isPrepared) {
                    mediaPlayer.start();
                }
            } catch (Exception e) {
                AppLogger.error("VideoBackgroundView", "设置 Surface 失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Surface 尺寸改变时不需要特殊处理
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        setOpacity(opacity);
        
        if (shouldPlay) {
            mp.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}

