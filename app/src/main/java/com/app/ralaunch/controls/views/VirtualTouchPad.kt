package com.app.ralaunch.controls.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.ControlsSharedState
import com.app.ralaunch.controls.TouchPointerTracker
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.data.SettingsManager
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 虚拟触控板控件View
 * 支持触摸滑动操作，使用按钮的所有外观功能
 */
class VirtualTouchPad(
    context: Context,
    data: ControlData,
    private val mInputBridge: ControlInputBridge,
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

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.TouchPad
        get() = controlData as ControlData.TouchPad

    private val screenWidth: Float = context.resources.displayMetrics.widthPixels.toFloat()
    private val screenHeight: Float = context.resources.displayMetrics.heightPixels.toFloat()

    private val idleDelayHandler = Handler(context.mainLooper)
    private val clickDelayHandler = Handler(context.mainLooper)
    private var currentState = TouchPadState.IDLE

    // 绘制相关
    private lateinit var backgroundPaint: Paint
    private lateinit var strokePaint: Paint
    private lateinit var textPaint: TextPaint
    private val paintRect: RectF = RectF()

    // 按钮状态
    private var mIsPressed = false
    private var activePointerId = -1 // 跟踪的触摸点 ID

    // Center position of the touchpad (in local view coordinates)
    // Note: width and height are fractions (0-1) relative to screen height
    private val centerX: Float
        get() = (castedData.width * screenHeight) / 2
    private val centerY: Float
        get() = (castedData.height * screenHeight) / 2

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var currentX: Float = centerX
    private var currentY: Float = centerY

    private val deltaX: Float
        get() = currentX - lastX
    private val deltaY: Float
        get() = currentY - lastY
    private val centeredDeltaX
        get() = currentX - centerX
    private val centeredDeltaY
        get() = currentY - centerY

    private var currentMouseButton = if (ControlsSharedState.isTouchPadRightButton) MotionEvent.BUTTON_SECONDARY else MotionEvent.BUTTON_PRIMARY

    private val settingsManager = SettingsManager.getInstance(null)

    init {
        initPaints()
    }

    private fun initPaints() {
        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint.color = castedData.bgColor
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.alpha = (castedData.opacity * 255).toInt()

        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        strokePaint.color = castedData.strokeColor
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = dpToPx(castedData.strokeWidth)
        // 边框透明度完全独立，默认1.0（完全不透明）
        val borderOpacity = if (castedData.borderOpacity != 0f) castedData.borderOpacity else 1.0f
        strokePaint.alpha = (borderOpacity * 255).toInt()

        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = -0x1
        textPaint.textSize = dpToPx(16f)
        textPaint.textAlign = Paint.Align.CENTER
        // 文本透明度完全独立，默认1.0（完全不透明）
        val textOpacity = if (castedData.textOpacity != 0f) castedData.textOpacity else 1.0f
        textPaint.alpha = (textOpacity * 255).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paintRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制矩形（圆角矩形）
        val cornerRadius = dpToPx(castedData.cornerRadius)
        canvas.drawRoundRect(paintRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(paintRect, cornerRadius, cornerRadius, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerId = event.getPointerId(event.actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 如果已经在跟踪一个触摸点，忽略新的
                if (activePointerId != -1) {
                    return false
                }
                // 记录触摸点
                activePointerId = pointerId

                lastX = event.x
                lastY = event.y
                currentX = lastX
                currentY = lastY
                initialTouchX = currentX
                initialTouchY = currentY

                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!castedData.isPassThrough) {
                    TouchPointerTracker.consumePointer(pointerId)
                }

                // Trigger Press!
                handlePress()
                triggerVibration(true)

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerId != activePointerId) {
                    return false
                }

                currentX = event.x
                currentY = event.y

                // Trigger Move!
                handleMove()

                // Update last position AFTER handleMove() so delta calculation works
                lastX = currentX
                lastY = currentY

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP -> {
                // 检查是否是我们跟踪的触摸点
                if (pointerId == activePointerId) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!castedData.isPassThrough) {
                        TouchPointerTracker.releasePointer(activePointerId)
                    }
                    activePointerId = -1

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

        when (currentState) {
            TouchPadState.IDLE -> {
                // Do nothing
            }

            TouchPadState.PENDING -> {
                // Check if movement exceeds threshold
                val moveDistance = sqrt(
                    (currentX - initialTouchX).toDouble().pow(2.0) +
                            (currentY - initialTouchY).toDouble().pow(2.0)
                ).toFloat()
                if (moveDistance > dpToPx(TOUCHPAD_MOVE_THRESHOLD.toFloat())) {
                    currentState = TouchPadState.MOVING
                    idleDelayHandler.removeCallbacksAndMessages(null)
                    // Send this move so that MOVE_THRESHOLD is not skipped
                    val multipliedDeltaX: Float = (currentX - initialTouchX) * TOUCHPAD_MOVE_RATIO
                    val multipliedDeltaY: Float = (currentY - initialTouchY) * TOUCHPAD_MOVE_RATIO
                    sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
                }
            }

            TouchPadState.DOUBLE_CLICK -> {
                // Double Click! Trigger centered movement and click!
                // Calculate on-screen centered position
                var onScreenMouseX: Float = (screenWidth / 2) + (centeredDeltaX * TOUCHPAD_MOVE_RATIO)
                var onScreenMouseY: Float = (screenHeight / 2) + (centeredDeltaY * TOUCHPAD_MOVE_RATIO)
                // Sanity check
                var minRangeX = (0.5f - settingsManager.mouseRightStickRangeLeft / 2) * screenWidth
                var maxRangeX = (0.5f + settingsManager.mouseRightStickRangeRight / 2) * screenWidth
                var minRangeY = (0.5f - settingsManager.mouseRightStickRangeTop / 2) * screenHeight
                var maxRangeY = (0.5f + settingsManager.mouseRightStickRangeBottom / 2) * screenHeight
                if (minRangeX >= maxRangeX || minRangeY >= maxRangeY) {
                    minRangeX = screenWidth * 0.5f
                    maxRangeX = screenWidth * 0.5f
                    minRangeY = screenHeight * 0.5f
                    maxRangeY = screenHeight * 0.5f
                }
                // Clamp to user settings bounds
                onScreenMouseX = Math.clamp(onScreenMouseX, minRangeX, maxRangeX)
                onScreenMouseY = Math.clamp(onScreenMouseY, minRangeY, maxRangeY)
                // Clamp to screen bounds
                onScreenMouseX = Math.clamp(onScreenMouseX, 0f, screenWidth - 1)
                onScreenMouseY = Math.clamp(onScreenMouseY, 0f, screenHeight - 1)
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, onScreenMouseX, onScreenMouseY, false) // in ACTION_MOVE, button value doesn't matter
            }

            TouchPadState.MOVING -> {
                // Send movement data
                val multipliedDeltaX: Float = deltaX * TOUCHPAD_MOVE_RATIO
                val multipliedDeltaY: Float = deltaY * TOUCHPAD_MOVE_RATIO
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
            }

            TouchPadState.PRESS_MOVING -> {
                // Send movement data
                val multipliedDeltaX: Float = deltaX * TOUCHPAD_MOVE_RATIO
                val multipliedDeltaY: Float = deltaY * TOUCHPAD_MOVE_RATIO
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true) // in ACTION_MOVE, button value doesn't matter
            }
        }

        invalidate()
    }

    private fun handlePress() {
        mIsPressed = true

        when (currentState) {
            TouchPadState.IDLE -> {
                // proceed to pending state
                currentState = TouchPadState.PENDING
                // we set currentMouseButton here so it wont change in the delayed handlers
                currentMouseButton = if (ControlsSharedState.isTouchPadRightButton)
                    MotionEvent.BUTTON_SECONDARY
                else
                    MotionEvent.BUTTON_PRIMARY

                idleDelayHandler.postDelayed({
                    if (currentState == TouchPadState.PENDING) { // No double click detected, no movement detected
                        currentState = TouchPadState.IDLE
                        if (mIsPressed) {
                            // Long Press! Trigger press movement!
                            currentState = TouchPadState.PRESS_MOVING
                            // notify the user press movement start
                            triggerVibration(true)
                            // Press down left mouse button
                            sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_DOWN, 0f, 0f, true)
                            // the rest of the movements would be handled by handleMove()
                        } else {
                            // Single Press! Trigger left click!
                            clickDelayHandler.removeCallbacksAndMessages(null)
                            sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_UP, 0f, 0f, true)
                            sdlOnNativeMouseDirect(currentMouseButton,MotionEvent.ACTION_DOWN,0f,0f,true)
                            clickDelayHandler.postDelayed({
                                sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_UP, 0f, 0f, true)
                            }, TOUCHPAD_CLICK_TIMEOUT)
                        }
                    }
                    idleDelayHandler.removeCallbacksAndMessages(null)
                }, TOUCHPAD_STATE_IDLE_TIMEOUT)
            }

            TouchPadState.PENDING -> {
                currentState = TouchPadState.DOUBLE_CLICK
                idleDelayHandler.removeCallbacksAndMessages(null)
                // Double Click! Trigger centered movement and click!
                // Calculate on-screen centered position
                var onScreenMouseX: Float = (screenWidth / 2) + (centeredDeltaX * TOUCHPAD_MOVE_RATIO)
                var onScreenMouseY: Float = (screenHeight / 2) + (centeredDeltaY * TOUCHPAD_MOVE_RATIO)
                // Sanity check
                var minRangeX = (0.5f - settingsManager.mouseRightStickRangeLeft / 2) * screenWidth
                var maxRangeX = (0.5f + settingsManager.mouseRightStickRangeRight / 2) * screenWidth
                var minRangeY = (0.5f - settingsManager.mouseRightStickRangeTop / 2) * screenHeight
                var maxRangeY = (0.5f + settingsManager.mouseRightStickRangeBottom / 2) * screenHeight
                if (minRangeX >= maxRangeX || minRangeY >= maxRangeY) {
                    minRangeX = screenWidth * 0.5f
                    maxRangeX = screenWidth * 0.5f
                    minRangeY = screenHeight * 0.5f
                    maxRangeY = screenHeight * 0.5f
                }
                // Clamp to user settings bounds
                onScreenMouseX = Math.clamp(onScreenMouseX, minRangeX, maxRangeX)
                onScreenMouseY = Math.clamp(onScreenMouseY, minRangeY, maxRangeY)
                // Clamp to screen bounds
                onScreenMouseX = Math.clamp(onScreenMouseX, 0f, screenWidth - 1)
                onScreenMouseY = Math.clamp(onScreenMouseY, 0f, screenHeight - 1)
                // click left mouse button and send centered movement
                sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_DOWN, onScreenMouseX, onScreenMouseY, false)
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

        when (currentState) {
            TouchPadState.IDLE -> {
                // Do nothing
            }

            TouchPadState.PENDING -> {
                // Still pending, wait for timeout to confirm single click
            }

            TouchPadState.DOUBLE_CLICK -> {
                // After double click, go back to idle
                currentState = TouchPadState.IDLE
                // Release mouse button
                sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_UP, 0f, 0f, true)
            }

            TouchPadState.MOVING -> {
                // After moving, go back to idle
                currentState = TouchPadState.IDLE
            }

            TouchPadState.PRESS_MOVING -> {
                // After press moving, go back to idle
                currentState = TouchPadState.IDLE
                // Release mouse button
                sdlOnNativeMouseDirect(currentMouseButton, MotionEvent.ACTION_UP, 0f, 0f, true)
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

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
