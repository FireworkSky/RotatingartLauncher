package com.app.ralaunch.feature.controls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.text.TextPaint
import com.app.ralaunch.core.logging.AppLog
import android.view.View
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.ControlData
import kotlin.math.abs

/**
 * 虚拟鼠标滚轮控件View
 * 支持滚轮滑动操作，通过垂直滑动发送鼠标滚轮事件
 * 向上滑动 = 向上滚动（正值），向下滑动 = 向下滚动（负值）
 */
class VirtualMouseWheel(
    context: Context,
    data: ControlData,
    private val mInputBridge: ControlInputBridge,
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualMouseWheel"
    }

    // 使用 Koin 延迟获取 VibrationManagerServiceV1
    private val vibrationManager: VibrationManagerServiceV1? by lazy {
        try {
            KoinJavaComponent.get(VibrationManagerServiceV1::class.java)
        } catch (e: Exception) {
            AppLog.w(TAG, "VibrationManagerServiceV1 not available: ${e.message}")
            null
        }
    }

        private fun triggerVibration() {
        vibrationManager?.vibrateOneShot(10, 20)
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.MouseWheel
        get() = controlData as ControlData.MouseWheel

    // 绘制相关
    private lateinit var backgroundPaint: Paint
    private lateinit var strokePaint: Paint
    private lateinit var textPaint: TextPaint
    private val paintRect: RectF = RectF()

    // 触摸状态
    private var activePointerId = -1 // 跟踪的触摸点 ID
    private var lastTouchX = 0f // 上一次触摸的X坐标（用于水平模式）
    private var lastTouchY = 0f // 上一次触摸的Y坐标（用于垂直模式）
    private var accumulatedScrollDelta = 0f // 累积的滚动增量

    init {
        initPaints()
    }

    private fun initPaints() {
        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = castedData.bgColor
            style = Paint.Style.FILL
            alpha = (castedData.opacity * 255).toInt()
        }

        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = castedData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(castedData.strokeWidth)
            alpha = (castedData.borderOpacity * 255).toInt()
        }

        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x1
            textSize = dpToPx(16f)
            textAlign = Paint.Align.CENTER
            alpha = (castedData.textOpacity * 255).toInt()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paintRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        // 将父视图坐标转换为本地坐标
        val childRect = android.graphics.Rect()
        getHitRect(childRect)
        val localX = x - childRect.left
        val localY = y - childRect.top

        return isLocalTouchInBounds(localX, localY)
    }

    /**
     * 检查本地坐标是否在控件形状内
     * @param localX 本地 X 坐标
     * @param localY 本地 Y 坐标
     */
    private fun isLocalTouchInBounds(localX: Float, localY: Float): Boolean {
        // 使用圆角矩形路径检查触摸点
        val cornerRadius = dpToPx(castedData.cornerRadius)
        val path = Path()
        path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        val region = Region()
        region.setPath(path, Region(0, 0, width, height))
        return region.contains(localX.toInt(), localY.toInt())
    }

    // ==================== ControlView 接口方法 ====================

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        // 如果已经在跟踪一个触摸点，拒绝新的
        if (activePointerId != -1) {
            return false
        }

        // 验证触摸点是否在控件的实际形状内
        if (!isLocalTouchInBounds(x, y)) {
            return false
        }

        // 记录触摸点
        activePointerId = pointerId
        lastTouchX = x
        lastTouchY = y
        accumulatedScrollDelta = 0f

        // 触摸按下时提供轻微反馈
        triggerVibration()

        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (activePointerId != pointerId) {
            return
        }

        // 根据方向计算移动增量
        val delta = when (castedData.orientation) {
            ControlData.MouseWheel.Orientation.VERTICAL -> {
                // 垂直模式：上滑为正值，下滑为负值
                lastTouchY - y
            }
            ControlData.MouseWheel.Orientation.HORIZONTAL -> {
                // 水平模式：左滑为正值，右滑为负值（更符合触摸板习惯）
                lastTouchX - x
            }
        }

        // 如果启用了反转方向，则反转delta
        val finalDelta = if (castedData.reverseDirection) -delta else delta

        // 累积滚动增量
        accumulatedScrollDelta += finalDelta

        // 获取配置的灵敏度阈值
        val scrollThreshold = castedData.scrollSensitivity

        // 当累积的增量超过阈值时，发送滚轮事件
        if (abs(accumulatedScrollDelta) >= scrollThreshold) {
            // 计算滚轮滚动方向和次数
            val scrollAmount = (accumulatedScrollDelta / scrollThreshold).toInt()

            // 发送滚轮事件
            // 正值 = 向上滚动，负值 = 向下滚动
            val scrollY = scrollAmount.toFloat() * castedData.scrollRatio
            if (scrollY != 0f) {
                mInputBridge.sendMouseWheel(scrollY)

                // 提供触觉反馈
                triggerVibration()

                // 减去已处理的增量
                accumulatedScrollDelta -= scrollAmount * scrollThreshold
            }
        }

        // 更新上一次的触摸位置
        lastTouchX = x
        lastTouchY = y
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId == activePointerId) {
            activePointerId = -1
            accumulatedScrollDelta = 0f
        }
    }

    override fun cancelAllTouches() {
        activePointerId = -1
        accumulatedScrollDelta = 0f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 重置状态
        activePointerId = -1
        accumulatedScrollDelta = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // 应用旋转
        if (castedData.rotation != 0f) {
            canvas.save()
            canvas.rotate(castedData.rotation, centerX, centerY)
        }

        // 绘制矩形（圆角矩形）
        val cornerRadius = dpToPx(castedData.cornerRadius)
        canvas.drawRoundRect(paintRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(paintRect, cornerRadius, cornerRadius, strokePaint)

        // 恢复旋转
        if (castedData.rotation != 0f) {
            canvas.restore()
        }
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}
