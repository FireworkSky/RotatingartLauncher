package com.app.ralaunch.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.RaLaunchApplication
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 虚拟触控板控件View
 * 支持触摸滑动操作，使用按钮的所有外观功能
 */
class VirtualTouchPad(
    context: Context,
    private var mData: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualTouchPad"

        private const val TOUCHPAD_STATE_IDLE_TIMEOUT = 200L // 毫秒
        private const val TOUCHPAD_CLICK_TIMEOUT = 50L // 毫秒
        private const val TOUCHPAD_MOVE_THRESHOLD = 5 // dp, 移动超过这个距离视为移动操作, 应该用dpToPx转换
        private const val TOUCHPAD_MOVE_RATIO = 2.0f // 移动距离放大倍数

        private fun triggerVibration(isPress: Boolean) {
            if (isPress) {
                RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30)
            } else {
                // 释放时不振动
//            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
            }
        }
    }

    enum class TouchPadState {
        IDLE,
        PENDING,
        DOUBLE_CLICK,
        MOVING,
        PRESS_MOVING
    }

    private val mScreenWidth: Float
    private val mScreenHeight: Float

    private val mIdleDelayHandler = Handler()
    private val mClickDelayHandler = Handler()
    private var mCurrentState = TouchPadState.IDLE

    // 绘制相关
    private var mBackgroundPaint: Paint? = null
    private var mStrokePaint: Paint? = null
    private var mTextPaint: TextPaint? = null
    private val mRectF: RectF

    // 按钮状态
    private var mIsPressed = false
    private var mActivePointerId = -1 // 跟踪的触摸点 ID
    private val mCenterX: Float
    private val mCenterY: Float
    private var mLastX = 0f
    private var mLastY = 0f
    private var mCurrentX: Float
    private var mCurrentY: Float
    private var mDeltaX: Float
    private var mDeltaY: Float
    private var mCenteredDeltaX = 0f
    private var mCenteredDeltaY = 0f
    private var mInitialTouchX = 0f
    private var mInitialTouchY = 0f

    init {
        mRectF = RectF()
        mCenterX = mData.width / 2
        mCenterY = mData.height / 2
        mCurrentX = mCenterX
        mCurrentY = mCenterY
        mDeltaX = 0f
        mDeltaY = 0f

        // 获取屏幕尺寸（用于右摇杆绝对位置计算）
        val metrics = context.getResources().getDisplayMetrics()
        mScreenWidth = metrics.widthPixels.toFloat()
        mScreenHeight = metrics.heightPixels.toFloat()

        initPaints()
    }

    private fun initPaints() {
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBackgroundPaint!!.setColor(mData.bgColor)
        mBackgroundPaint!!.setStyle(Paint.Style.FILL)
        mBackgroundPaint!!.setAlpha((mData.opacity * 255).toInt())

        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mStrokePaint!!.setColor(mData.strokeColor)
        mStrokePaint!!.setStyle(Paint.Style.STROKE)
        mStrokePaint!!.setStrokeWidth(dpToPx(mData.strokeWidth))
        // 边框透明度完全独立，默认1.0（完全不透明）
        val borderOpacity = if (mData.borderOpacity != 0f) mData.borderOpacity else 1.0f
        mStrokePaint!!.setAlpha((borderOpacity * 255).toInt())

        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        mTextPaint!!.setColor(-0x1)
        mTextPaint!!.setTextSize(dpToPx(16f))
        mTextPaint!!.setTextAlign(Paint.Align.CENTER)
        // 文本透明度完全独立，默认1.0（完全不透明）
        val textOpacity = if (mData.textOpacity != 0f) mData.textOpacity else 1.0f
        mTextPaint!!.setAlpha((textOpacity * 255).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制矩形（圆角矩形）
        val cornerRadius = dpToPx(mData.cornerRadius)
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint!!)
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint!!)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.getActionMasked()
        val pointerId = event.getPointerId(event.getActionIndex())

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 如果已经在跟踪一个触摸点，忽略新的
                if (mActivePointerId != -1) {
                    return false
                }
                // 记录触摸点
                mActivePointerId = pointerId

                mLastX = event.getX()
                mLastY = event.getY()
                mCurrentX = mLastX
                mCurrentY = mLastY
                mCenteredDeltaX = mCurrentX - mCenterX
                mCenteredDeltaY = mCurrentY - mCenterY
                mInitialTouchX = mCurrentX
                mInitialTouchY = mCurrentY

                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!mData.passThrough) {
                    TouchPointerTracker.consumePointer(pointerId)
                }

                // Trigger Press!
                handlePress()
                triggerVibration(true)

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerId != mActivePointerId) {
                    return false
                }

                mCurrentX = event.getX()
                mCurrentY = event.getY()
                mDeltaX = mCurrentX - mLastX
                mDeltaY = mCurrentY - mLastY
                mLastX = mCurrentX
                mLastY = mCurrentY
                mCenteredDeltaX = mCurrentX - mCenterX
                mCenteredDeltaY = mCurrentY - mCenterY

                // Trigger Move!
                handleMove()

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP -> {
                // 检查是否是我们跟踪的触摸点
                if (pointerId == mActivePointerId) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!mData.passThrough) {
                        TouchPointerTracker.releasePointer(mActivePointerId)
                    }
                    mActivePointerId = -1

                    // Trigger Release!
                    handleRelease()
                    triggerVibration(false)

                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleMove() {
        // 处理触摸移动逻辑

        when (mCurrentState) {
            TouchPadState.IDLE -> {
                // Do nothing
            }

            TouchPadState.PENDING -> {
                // Check if movement exceeds threshold
                val moveDistance = sqrt(
                    (mCurrentX - mInitialTouchX).toDouble().pow(2.0) +
                            (mCurrentY - mInitialTouchY).toDouble().pow(2.0)
                ).toFloat()
                if (moveDistance > dpToPx(TOUCHPAD_MOVE_THRESHOLD.toFloat())) {
                    mCurrentState = TouchPadState.MOVING
                    mIdleDelayHandler.removeCallbacksAndMessages(null)
                    // Send this move so that MOVE_THRESHOLD is not skipped
                    val multipliedDeltaX: Float = (mCurrentX - mInitialTouchX) * TOUCHPAD_MOVE_RATIO
                    val multipliedDeltaY: Float = (mCurrentY - mInitialTouchY) * TOUCHPAD_MOVE_RATIO
                    sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
                }
            }

            TouchPadState.DOUBLE_CLICK -> {
                // Double Click! Trigger centered movement and click!
                // Calculate on-screen centered position
                val onScreenMouseX: Float = (mScreenWidth / 2) + (mCenteredDeltaX * TOUCHPAD_MOVE_RATIO)
                val onScreenMouseY: Float = (mScreenHeight / 2) + (mCenteredDeltaY * TOUCHPAD_MOVE_RATIO)
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, onScreenMouseX, onScreenMouseY, false) // in ACTION_MOVE, button value doesn't matter
            }

            TouchPadState.MOVING -> {
                // Send movement data
                val multipliedDeltaX: Float = mDeltaX * TOUCHPAD_MOVE_RATIO
                val multipliedDeltaY: Float = mDeltaY * TOUCHPAD_MOVE_RATIO
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
            }

            TouchPadState.PRESS_MOVING -> {
                // Send movement data
                val multipliedDeltaX: Float = mDeltaX * TOUCHPAD_MOVE_RATIO
                val multipliedDeltaY: Float = mDeltaY * TOUCHPAD_MOVE_RATIO
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
            }
        }

        invalidate()
    }

    private fun handlePress() {
        mIsPressed = true

        when (mCurrentState) {
            TouchPadState.IDLE -> {
                mCurrentState = TouchPadState.PENDING // proceed to pending state
                mIdleDelayHandler.postDelayed({
                    if (mCurrentState == TouchPadState.PENDING) { // No double click detected, no movement detected
                        mCurrentState = TouchPadState.IDLE
                        if (mIsPressed) {
                            // Long Press! Trigger press movement!
                            mCurrentState = TouchPadState.PRESS_MOVING
                            // notify the user press movement start
                            triggerVibration(true)
                            // Press down left mouse button
                            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_DOWN, 0f, 0f, true)
                            // the rest of the movements would be handled by handleMove()
                        } else {
                            // Single Press! Trigger left click!
                            mClickDelayHandler.removeCallbacksAndMessages(null)
                            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0f, 0f, true)
                            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY,MotionEvent.ACTION_DOWN,0f,0f,true)
                            mClickDelayHandler.postDelayed({
                                sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0f, 0f, true)
                            }, TOUCHPAD_CLICK_TIMEOUT)
                        }
                    }
                    mIdleDelayHandler.removeCallbacksAndMessages(null)
                }, TOUCHPAD_STATE_IDLE_TIMEOUT)
            }

            TouchPadState.PENDING -> {
                mCurrentState = TouchPadState.DOUBLE_CLICK
                mIdleDelayHandler.removeCallbacksAndMessages(null)
                // Double Click! Trigger centered movement and click!
                // Calculate on-screen centered position
                val onScreenMouseX: Float = (mScreenWidth / 2) + (mCenteredDeltaX * TOUCHPAD_MOVE_RATIO)
                val onScreenMouseY: Float = (mScreenHeight / 2) + (mCenteredDeltaY * TOUCHPAD_MOVE_RATIO)
                // click left mouse button and send centered movement
                sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_DOWN, onScreenMouseX, onScreenMouseY, false)
                // The rest of the movements would be handled by handleMove()
            }

            TouchPadState.DOUBLE_CLICK -> {
                // Already in double click, ignore
            }

            TouchPadState.MOVING -> {
                // Already moving, ignore
            }

            TouchPadState.PRESS_MOVING -> {
                // Already moving, ignore
            }
        }

        invalidate()
    }

    private fun handleRelease() {
        mIsPressed = false

        when (mCurrentState) {
            TouchPadState.IDLE -> {
                // Do nothing
            }

            TouchPadState.PENDING -> {
                // Still pending, wait for timeout to confirm single click
            }

            TouchPadState.DOUBLE_CLICK -> {
                // After double click, go back to idle
                mCurrentState = TouchPadState.IDLE
                // Release mouse button
                sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0f, 0f, true)
            }

            TouchPadState.MOVING -> {
                // After moving, go back to idle
                mCurrentState = TouchPadState.IDLE
            }

            TouchPadState.PRESS_MOVING -> {
                // After press moving, go back to idle
                mCurrentState = TouchPadState.IDLE
                // Release mouse button
                sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0f, 0f, true)
            }
        }

        invalidate()
    }

    private fun sdlOnNativeMouseDirect(
        button: Int,
        action: Int,
        x: Float,
        y: Float,
        relative: Boolean
    ) {
        if (mInputBridge is SDLInputBridge) {
            mInputBridge.sdlOnNativeMouseDirect(button, action, x, y, relative)
        }
    }

    override fun getData(): ControlData {
        return mData
    }

    override fun updateData(data: ControlData) {
        mData = data
        initPaints()
        invalidate()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
