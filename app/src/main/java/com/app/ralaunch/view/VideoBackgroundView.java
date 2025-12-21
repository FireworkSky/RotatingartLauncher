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
public class VideoBackgroundView extends SurfaceView implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnErrorListener {
    private MediaPlayer mediaPlayer;
    private String videoPath;
    private boolean isPrepared = false;
    private boolean shouldPlay = false;
    private int opacity = 100; // 0-100
    private int videoWidth = 0;
    private int videoHeight = 0;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;
    private float playbackSpeed = 1.0f; // 保存播放速度

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
                prepareMediaPlayer();
            }
        }
    }
    
    /**
     * 准备 MediaPlayer
     */
    private void prepareMediaPlayer() {
        try {
            // 检查 Surface 是否可用
            SurfaceHolder holder = getHolder();
            if (holder == null || holder.getSurface() == null || !holder.getSurface().isValid()) {
                AppLogger.warn("VideoBackgroundView", "Surface 不可用，延迟准备视频");
                return;
            }
            
                    mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(videoPath);
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setOnPreparedListener(this);
                    mediaPlayer.setOnVideoSizeChangedListener(this);
            mediaPlayer.setOnErrorListener(this);
            
            // 设置 Surface
            mediaPlayer.setDisplay(holder);
            
                    mediaPlayer.prepareAsync();
            AppLogger.info("VideoBackgroundView", "开始准备视频: " + videoPath);
                } catch (Exception e) {
            AppLogger.error("VideoBackgroundView", "准备视频失败: " + e.getMessage());
                    releaseMediaPlayer();
        }
    }

    /**
     * 设置透明度 (0-100)
     */
    public void setOpacity(int opacity) {
        this.opacity = Math.max(0, Math.min(100, opacity));
        // 将透明度转换为 alpha 值 (0-100 -> 0.0-1.0)
        float alpha = opacity / 100.0f;
        
        // 如果正在播放，使用动画过渡；否则直接设置
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            animate()
                .alpha(alpha)
                .setDuration(200)
                .start();
        } else {
        setAlpha(alpha);
        }
        
        AppLogger.info("VideoBackgroundView", "视频透明度已设置: " + opacity + "% (alpha: " + alpha + ")");
    }

    /**
     * 设置播放速度 (0.5x - 2.0x)
     * 仅支持 Android 6.0 (API 23) 及以上
     */
    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        
        if (mediaPlayer != null && isPrepared) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.media.PlaybackParams params = mediaPlayer.getPlaybackParams();
                    params.setSpeed(playbackSpeed);
                    mediaPlayer.setPlaybackParams(params);
                    AppLogger.info("VideoBackgroundView", "视频播放速度已设置: " + playbackSpeed + "x");
                } else {
                    AppLogger.warn("VideoBackgroundView", "当前设备不支持设置播放速度（需要 Android 6.0+）");
                }
            } catch (Exception e) {
                AppLogger.error("VideoBackgroundView", "设置播放速度失败: " + e.getMessage());
            }
        }
    }

    /**
     * 开始播放
     */
    public void start() {
        shouldPlay = true;
        if (mediaPlayer != null && isPrepared) {
            try {
                if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
                    AppLogger.info("VideoBackgroundView", "视频播放已开始");
                }
            } catch (IllegalStateException e) {
                AppLogger.error("VideoBackgroundView", "播放失败，尝试重新准备: " + e.getMessage());
                // MediaPlayer 状态异常，重新准备
                if (videoPath != null && !videoPath.isEmpty()) {
                    releaseMediaPlayer();
                    prepareMediaPlayer();
                }
            }
        } else if (mediaPlayer == null && videoPath != null && !videoPath.isEmpty()) {
            // MediaPlayer 为 null，重新创建
            AppLogger.info("VideoBackgroundView", "MediaPlayer 为 null，重新创建");
            prepareMediaPlayer();
        }
    }

    /**
     * 暂停播放
     */
    public void pause() {
        shouldPlay = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
                    AppLogger.info("VideoBackgroundView", "视频播放已暂停");
                }
            } catch (IllegalStateException e) {
                AppLogger.error("VideoBackgroundView", "暂停失败: " + e.getMessage());
            }
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
        AppLogger.info("VideoBackgroundView", "Surface 已创建，shouldPlay=" + shouldPlay);
        
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(holder);
                if (shouldPlay && isPrepared) {
                    mediaPlayer.start();
                    AppLogger.info("VideoBackgroundView", "Surface 创建后恢复播放");
                }
            } catch (Exception e) {
                AppLogger.error("VideoBackgroundView", "设置 Surface 失败: " + e.getMessage());
                // Surface 设置失败，释放并重新准备
                releaseMediaPlayer();
                if (shouldPlay && videoPath != null && !videoPath.isEmpty()) {
                    prepareMediaPlayer();
            }
            }
        } else if (shouldPlay && videoPath != null && !videoPath.isEmpty()) {
            // Surface 创建时如果 MediaPlayer 为 null 且需要播放，重新创建
            AppLogger.info("VideoBackgroundView", "Surface 创建时重新准备 MediaPlayer");
            prepareMediaPlayer();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        updateVideoSize();
    }
    
    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        videoWidth = width;
        videoHeight = height;
        updateVideoSize();
    }
    
    /**
     * 更新视频尺寸以实现 centerCrop 效果
     * 类似 Glide 的 centerCrop，让视频完全填满屏幕
     */
    private void updateVideoSize() {
        if (videoWidth == 0 || videoHeight == 0 || surfaceWidth == 0 || surfaceHeight == 0) {
            return;
        }
        
        // 计算视频和屏幕的宽高比
        float videoAspect = (float) videoWidth / videoHeight;
        float screenAspect = (float) surfaceWidth / surfaceHeight;
        
        // centerCrop效果：让视频填满整个屏幕
        // 不使用 setFixedSize，而是直接设置 SurfaceView 的尺寸
        int newWidth, newHeight;
        
        if (videoAspect > screenAspect) {
            // 视频更宽，按高度缩放（类似 Glide 的 override + centerCrop）
            newHeight = surfaceHeight;
            newWidth = (int) (surfaceHeight * videoAspect);
        } else {
            // 视频更高，按宽度缩放
            newWidth = surfaceWidth;
            newHeight = (int) (surfaceWidth / videoAspect);
        }
        
        // 设置 SurfaceView 布局参数（关键：不用 MATCH_PARENT，用计算的尺寸）
        android.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            params.width = newWidth;
            params.height = newHeight;
            setLayoutParams(params);
        }
        
        // 居中显示（超出部分会被裁剪）
        int xOffset = (surfaceWidth - newWidth) / 2;
        int yOffset = (surfaceHeight - newHeight) / 2;
        setTranslationX(xOffset);
        setTranslationY(yOffset);
        
        AppLogger.info("VideoBackgroundView", "视频缩放（类似Glide centerCrop）- 原始: " + videoWidth + "x" + videoHeight + 
            ", 屏幕: " + surfaceWidth + "x" + surfaceHeight + 
            ", 缩放后: " + newWidth + "x" + newHeight + 
            ", 偏移: (" + xOffset + ", " + yOffset + ")");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        AppLogger.info("VideoBackgroundView", "Surface 已销毁（Activity 进入后台）");
        
      
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        AppLogger.info("VideoBackgroundView", "视频已准备完成");
        
        // 应用播放速度
        if (playbackSpeed != 1.0f) {
            setPlaybackSpeed(playbackSpeed);
        }
        
        // 获取视频尺寸
        videoWidth = mp.getVideoWidth();
        videoHeight = mp.getVideoHeight();
        updateVideoSize();
        
        // 如果需要播放，开始播放
        if (shouldPlay) {
            try {
            mp.start();
                AppLogger.info("VideoBackgroundView", "视频开始播放");
                
                // 添加淡入动画，避免突然出现
                setAlpha(0f);
                animate()
                    .alpha(opacity / 100.0f)
                    .setDuration(300) // 300ms 淡入动画
                    .setStartDelay(0)
                    .start();
                
                AppLogger.info("VideoBackgroundView", "视频淡入动画已启动");
            } catch (Exception e) {
                AppLogger.error("VideoBackgroundView", "启动播放失败: " + e.getMessage());
            }
        } else {
            // 即使不播放，也应用透明度
            setOpacity(opacity);
        }
    }
    
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // 错误代码说明：
        // what: 1 = MEDIA_ERROR_UNKNOWN, 100 = MEDIA_ERROR_SERVER_DIED
        // extra: -2147483648 = MEDIA_ERROR_SYSTEM (Surface 销毁导致)
        
        // Surface 销毁导致的错误是正常的（Activity 进入后台）
        boolean isSurfaceError = (what == 1 && extra == Integer.MIN_VALUE);
        
        if (isSurfaceError) {
            AppLogger.info("VideoBackgroundView", "MediaPlayer 因 Surface 销毁而停止（正常行为）");
        } else {
            AppLogger.error("VideoBackgroundView", "MediaPlayer 错误 - what: " + what + ", extra: " + extra);
        }
        
        // 释放出错的 MediaPlayer
        releaseMediaPlayer();
        
        // 注意：不在这里重新准备，等待 surfaceCreated 或 start() 时自动恢复
        
        return true; // 返回 true 表示错误已处理
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}

