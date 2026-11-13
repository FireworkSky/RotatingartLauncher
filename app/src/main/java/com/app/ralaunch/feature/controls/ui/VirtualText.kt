package com.app.ralaunch.feature.controls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.ui.ControlView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 虚拟文本控件View
 * 显示文本内容，不支持按键映射，使用按钮的所有外观功能
 */
class VirtualText(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge?
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualText"
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.Text
        get() = controlData as ControlData.Text

    // 绘制相关
    private lateinit var mBackgroundPaint: Paint
    private lateinit var mStrokePaint: Paint
    private lateinit var mTextPaint: TextPaint
    private val mRectF: RectF
    private val mTextBounds = Rect()

    init {
        mRectF = RectF()
        initPaints()
    }

    private fun initPaints() {
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            style = Paint.Style.FILL
            alpha = (controlData.opacity * 255).toInt()
        }

        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(controlData.strokeWidth)
            alpha = (controlData.borderOpacity * 255).toInt()
        }

        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x1
            textSize = dpToPx(16f)
            textAlign = Paint.Align.CENTER
            alpha = (controlData.textOpacity * 255).toInt()
        }
    }

    private fun dpToPx(dp: Float) = dp * context.resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        // 将父视图坐标转换为本地坐标
        val childRect = Rect()
        getHitRect(childRect)
        val localX = x - childRect.left
        val localY = y - childRect.top
        
        when (castedData.shape) {
            ControlData.Text.Shape.CIRCLE -> {
                val centerX = width / 2f
                val centerY = height / 2f
                val radius = min(width, height) / 2f
                val dx = localX - centerX
                val dy = localY - centerY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                return distance <= radius
            }
            ControlData.Text.Shape.RECTANGLE -> {
                // 使用圆角矩形路径检查触摸点
                val cornerRadius = dpToPx(controlData.cornerRadius)
                val path = Path()
                path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
                val region = Region()
                region.setPath(path, Region(0, 0, width, height))
                return region.contains(localX.toInt(), localY.toInt())
            }
        }
    }

    // ==================== ControlView 接口方法 ====================

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        // 文本控件不处理触摸
        return false
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        // 文本控件不处理触摸
    }

    override fun releaseTouch(pointerId: Int) {
        // 文本控件不处理触摸
    }

    override fun cancelAllTouches() {
        // 文本控件不处理触摸
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!controlData.isVisible) return

        val centerX = width / 2f
        val centerY = height / 2f

        // 应用旋转
        if (controlData.rotation != 0f) {
            canvas.save()
            canvas.rotate(controlData.rotation, centerX, centerY)
        }

        val radius = min(mRectF.width(), mRectF.height()) / 2f
        val displayText = castedData.displayText

        // 绘制背景
        if (displayText.isNotEmpty()) {
            when (castedData.shape) {
                ControlData.Text.Shape.CIRCLE -> {
                    canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint)
                    canvas.drawCircle(centerX, centerY, radius, mStrokePaint)
                }
                ControlData.Text.Shape.RECTANGLE -> {
                    val cornerRadius = dpToPx(controlData.cornerRadius)
                    canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint)
                    canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint)
                }
            }
        }

        // 自动计算文字大小以适应区域
        mTextPaint.textSize = 20f // 临时设置用于测量
        mTextPaint.getTextBounds(displayText, 0, displayText.length, mTextBounds)
        val textAspectRatio = mTextBounds.width() / max(mTextBounds.height(), 1).toFloat()

        mTextPaint.textSize = min(height / 2f, width / max(textAspectRatio, 1f))

        // 居中显示文本
        val textY = height / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2)
        canvas.drawText(displayText, width / 2f, textY, mTextPaint)

        // 恢复旋转
        if (controlData.rotation != 0f) {
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent?) = false
}
