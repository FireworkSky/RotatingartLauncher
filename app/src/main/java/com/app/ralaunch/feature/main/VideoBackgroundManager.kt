package com.app.ralaunch.feature.main

import com.app.ralaunch.core.di.service.ThemeManagerServiceV1
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.main.ui.background.VideoBackgroundView
import java.lang.ref.WeakReference

/**
 * 视频背景管理器
 * 
 * 管理视频背景的播放状态（播放、暂停、释放）
 * 视频 View 由 Compose 的 VideoBackground 组件创建并注册到此管理器
 */
class VideoBackgroundManager(
    private val themeManager: ThemeManagerServiceV1
) {
    companion object {
        private const val TAG = "VideoBackgroundManager"
    }

    // 使用弱引用避免内存泄漏
    private var videoViewRef: WeakReference<VideoBackgroundView>? = null

    /**
     * 注册视频 View（由 Compose 的 VideoBackground 组件调用）
     */
    fun registerVideoView(view: VideoBackgroundView?) {
        videoViewRef = view?.let { WeakReference(it) }
        AppLog.d(TAG, "视频 View ${if (view != null) "已注册" else "已注销"}")
    }

    private val videoView: VideoBackgroundView?
        get() = videoViewRef?.get()

    /**
     * 检查是否有视频 View 已注册
     */
    val hasVideoView: Boolean
        get() = videoView != null

    /**
     * 恢复视频播放
     */
    fun resume() {
        try {
            videoView?.takeIf { themeManager.isVideoBackground }?.start()
        } catch (e: Exception) {
            AppLog.e(TAG, "恢复视频背景失败: ${e.message}")
        }
    }

    /**
     * 暂停视频播放
     */
    fun pause() {
        try {
            videoView?.pause()
        } catch (e: Exception) {
            AppLog.e(TAG, "暂停视频背景失败: ${e.message}")
        }
    }

    /**
     * 释放视频资源
     */
    fun release() {
        try {
            videoView?.release()
            videoViewRef = null
        } catch (e: Exception) {
            AppLog.e(TAG, "释放视频背景失败: ${e.message}")
        }
    }

    /**
     * 设置播放速度
     */
    fun setSpeed(speed: Float) {
        videoView?.setPlaybackSpeed(speed)
    }

    /**
     * 设置不透明度
     */
    fun setOpacity(opacity: Int) {
        videoView?.setOpacity(opacity)
    }
}
