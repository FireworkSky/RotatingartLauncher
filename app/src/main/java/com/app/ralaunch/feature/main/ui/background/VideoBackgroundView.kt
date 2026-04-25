package com.app.ralaunch.feature.main.ui.background

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.app.ralaunch.core.logging.AppLog
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 视频背景视图
 */
class VideoBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnVideoSizeChangedListener,
    MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var videoPath: String? = null
    private var isPrepared = false
    private var shouldPlay = false
    private var opacity = 100
    private var videoWidth = 0
    private var videoHeight = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var playbackSpeed = 1.0f

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
    }

    fun setVideoPath(path: String?) {
        videoPath = path
        releaseMediaPlayer()
        if (!path.isNullOrEmpty() && File(path).exists()) {
            prepareMediaPlayer()
        }
    }

    private fun prepareMediaPlayer() {
        try {
            val h = holder
            if (h.surface == null || !h.surface.isValid) {
                AppLog.w("VideoBackgroundView", "Surface 不可用，延迟准备视频")
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(videoPath!!)
                isLooping = true
                setOnPreparedListener(this@VideoBackgroundView)
                setOnVideoSizeChangedListener(this@VideoBackgroundView)
                setOnErrorListener(this@VideoBackgroundView)
                setDisplay(h)
                prepareAsync()
            }
            AppLog.i("VideoBackgroundView", "开始准备视频: $videoPath")
        } catch (e: Exception) {
            AppLog.e("VideoBackgroundView", "准备视频失败: ${e.message}")
            releaseMediaPlayer()
        }
    }

    fun setOpacity(value: Int) {
        opacity = max(0, min(100, value))
        val alphaValue = opacity / 100.0f
        if (mediaPlayer != null && isPrepared && mediaPlayer?.isPlaying == true) {
            animate().alpha(alphaValue).setDuration(200).start()
        } else {
            alpha = alphaValue
        }
        AppLog.i("VideoBackgroundView", "视频透明度已设置: $opacity% (alpha: $alphaValue)")
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = max(0.5f, min(2.0f, speed))
        if (mediaPlayer != null && isPrepared) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mediaPlayer?.playbackParams = mediaPlayer!!.playbackParams.setSpeed(playbackSpeed)
                    AppLog.i("VideoBackgroundView", "视频播放速度已设置: ${playbackSpeed}x")
                }
            } catch (e: Exception) {
                AppLog.e("VideoBackgroundView", "设置播放速度失败: ${e.message}")
            }
        }
    }

    fun start() {
        shouldPlay = true
        mediaPlayer?.let { mp ->
            if (isPrepared) {
                try {
                    if (!mp.isPlaying) {
                        mp.start()
                        AppLog.i("VideoBackgroundView", "视频播放已开始")
                    }
                } catch (e: IllegalStateException) {
                    AppLog.e("VideoBackgroundView", "播放失败，尝试重新准备: ${e.message}")
                    releaseMediaPlayer()
                    if (!videoPath.isNullOrEmpty()) prepareMediaPlayer()
                }
            }
        } ?: run {
            if (!videoPath.isNullOrEmpty()) {
                AppLog.i("VideoBackgroundView", "MediaPlayer 为 null，重新创建")
                prepareMediaPlayer()
            }
        }
    }

    fun pause() {
        shouldPlay = false
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    AppLog.i("VideoBackgroundView", "视频播放已暂停")
                }
            } catch (e: IllegalStateException) {
                AppLog.e("VideoBackgroundView", "暂停失败: ${e.message}")
            }
        }
    }

    fun stop() {
        shouldPlay = false
        mediaPlayer?.let { if (it.isPlaying) it.stop() }
    }

    fun release() {
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {
                AppLog.e("VideoBackgroundView", "释放 MediaPlayer 失败: ${e.message}")
            }
        }
        mediaPlayer = null
        isPrepared = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AppLog.i("VideoBackgroundView", "Surface 已创建，shouldPlay=$shouldPlay")
        mediaPlayer?.let { mp ->
            try {
                mp.setDisplay(holder)
                if (shouldPlay && isPrepared) {
                    mp.start()
                    AppLog.i("VideoBackgroundView", "Surface 创建后恢复播放")
                }
            } catch (e: Exception) {
                AppLog.e("VideoBackgroundView", "设置 Surface 失败: ${e.message}")
                releaseMediaPlayer()
                if (shouldPlay && !videoPath.isNullOrEmpty()) prepareMediaPlayer()
            }
        } ?: run {
            if (shouldPlay && !videoPath.isNullOrEmpty()) {
                AppLog.i("VideoBackgroundView", "Surface 创建时重新准备 MediaPlayer")
                prepareMediaPlayer()
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        updateVideoSize()
    }

    override fun onVideoSizeChanged(mp: MediaPlayer?, width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        updateVideoSize()
    }

    private fun updateVideoSize() {
        if (videoWidth == 0 || videoHeight == 0 || surfaceWidth == 0 || surfaceHeight == 0) return

        val videoAspect = videoWidth.toFloat() / videoHeight
        val screenAspect = surfaceWidth.toFloat() / surfaceHeight

        // 使用 scale 实现 centerCrop 效果，保持 layoutParams 为 MATCH_PARENT
        // 这样视频可以覆盖整个屏幕包括系统导航栏区域
        val scale = if (videoAspect > screenAspect) {
            // 视频更宽，按高度适配，宽度会超出
            surfaceHeight.toFloat() / videoHeight
        } else {
            // 视频更高，按宽度适配，高度会超出
            surfaceWidth.toFloat() / videoWidth
        }

        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale

        scaleX = scaledWidth / surfaceWidth
        scaleY = scaledHeight / surfaceHeight

        // 居中 - 使用 pivot 点默认就是中心，无需额外偏移
        translationX = 0f
        translationY = 0f

        AppLog.i("VideoBackgroundView", "视频缩放（centerCrop）- 原始: ${videoWidth}x$videoHeight, 屏幕: ${surfaceWidth}x$surfaceHeight, scale: $scale, scaleX: $scaleX, scaleY: $scaleY")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AppLog.i("VideoBackgroundView", "Surface 已销毁")
    }

    override fun onPrepared(mp: MediaPlayer) {
        isPrepared = true
        AppLog.i("VideoBackgroundView", "视频已准备完成")
        if (playbackSpeed != 1.0f) setPlaybackSpeed(playbackSpeed)
        videoWidth = mp.videoWidth
        videoHeight = mp.videoHeight
        updateVideoSize()

        if (shouldPlay) {
            try {
                mp.start()
                AppLog.i("VideoBackgroundView", "视频开始播放")
                alpha = 0f
                animate().alpha(opacity / 100f).setDuration(300).start()
            } catch (e: Exception) {
                AppLog.e("VideoBackgroundView", "启动播放失败: ${e.message}")
            }
        } else {
            setOpacity(opacity)
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        val isSurfaceError = what == 1 && extra == Int.MIN_VALUE
        if (isSurfaceError) {
            AppLog.i("VideoBackgroundView", "MediaPlayer 因 Surface 销毁而停止")
        } else {
            AppLog.e("VideoBackgroundView", "MediaPlayer 错误 - what: $what, extra: $extra")
        }
        releaseMediaPlayer()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
