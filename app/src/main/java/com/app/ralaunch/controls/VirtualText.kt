package com.app.ralaunch.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟文本控件View
 * 显示文本内容，不支持按键映射，使用按钮的所有外观功能
 */
class VirtualText(
    context: Context?,
    private var mData: ControlData?,
    private val mInputBridge: ControlInputBridge?
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualText"
    }

    // 绘制相关
    private var mBackgroundPaint: Paint? = null
    private var mStrokePaint: Paint? = null
    private var mTextPaint: TextPaint? = null
    private val mRectF: RectF

    init {
        mRectF = RectF()
        initPaints()
    }

    private fun initPaints() {
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBackgroundPaint!!.setColor(mData!!.bgColor)
        mBackgroundPaint!!.setStyle(Paint.Style.FILL)
        // 背景透明度完全独立
        mBackgroundPaint!!.setAlpha((mData!!.opacity * 255).toInt())

        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mStrokePaint!!.setColor(mData!!.strokeColor)
        mStrokePaint!!.setStyle(Paint.Style.STROKE)
        mStrokePaint!!.setStrokeWidth(dpToPx(mData!!.strokeWidth))
        // 边框透明度完全独立，默认1.0（完全不透明）
        val borderOpacity = if (mData!!.borderOpacity != 0f) mData!!.borderOpacity else 1.0f
        mStrokePaint!!.setAlpha((borderOpacity * 255).toInt())

        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        mTextPaint!!.setColor(-0x1)
        mTextPaint!!.setTextSize(dpToPx(16f))
        mTextPaint!!.setTextAlign(Paint.Align.CENTER)
        // 文本透明度完全独立，默认1.0（完全不透明）
        val textOpacity = if (mData!!.textOpacity != 0f) mData!!.textOpacity else 1.0f
        mTextPaint!!.setAlpha((textOpacity * 255).toInt())
    }

    private fun dpToPx(dp: Float): Float {
        return dp * getContext().getResources().getDisplayMetrics().density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun updateData(data: ControlData?) {
        mData = data
        initPaints()
        invalidate()
    }

    override fun getData(): ControlData? {
        return mData
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mData == null || !mData!!.visible) {
            return
        }

        val centerX = getWidth() / 2f
        val centerY = getHeight() / 2f


        // 应用旋转
        if (mData!!.rotation != 0f) {
            canvas.save()
            canvas.rotate(mData!!.rotation, centerX, centerY)
        }

        val shape = mData!!.shape
        val radius = min(mRectF.width(), mRectF.height()) / 2f


        // 绘制背景
        if (shape == ControlData.SHAPE_CIRCLE) {
            canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint!!)
            canvas.drawCircle(centerX, centerY, radius, mStrokePaint!!)
        } else {
            // 绘制矩形（圆角矩形）- 文本控件默认方形
            val cornerRadius = dpToPx(mData!!.cornerRadius)
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint!!)
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint!!)
        }


        // 绘制文本
        val displayText = if (mData!!.displayText != null) mData!!.displayText else ""


        // 自动计算文字大小以适应区域
        mTextPaint!!.setTextSize(20f) // 临时设置用于测量
        val textBounds = Rect()
        mTextPaint!!.getTextBounds(displayText, 0, displayText.length, textBounds)
        val textAspectRatio = textBounds.width() / max(textBounds.height(), 1).toFloat()


        // 自动计算文字大小：minOf(height / 2, width / textAspectRatio)
        val textSize = min(
            getHeight() / 2f,
            getWidth() / max(textAspectRatio, 1f)
        )
        mTextPaint!!.setTextSize(textSize)


        // 居中显示文本
        val textY = getHeight() / 2f - ((mTextPaint!!.descent() + mTextPaint!!.ascent()) / 2)
        canvas.drawText(displayText, getWidth() / 2f, textY, mTextPaint!!)


        // 恢复旋转
        if (mData!!.rotation != 0f) {
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // 文本控件不支持触摸事件（不处理按键映射）
        return false
    }
}