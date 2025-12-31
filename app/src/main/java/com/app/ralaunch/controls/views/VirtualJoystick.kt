package com.app.ralaunch.controls.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.controls.TouchPointerTracker
import com.app.ralaunch.data.SettingsManager
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 虚拟摇杆View
 * 支持8方向移动，触摸拖拽控制
 */
class VirtualJoystick(
    context: Context,
    data: ControlData,
    private var mInputBridge: ControlInputBridge
) : View(context), ControlView {

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            // 重新计算摇杆圆心大小（因为 stickKnobSize 可能已改变）
            if (mRadius > 0) {
                val knobSizeRatio = if (castedData.stickKnobSize != 0f) castedData.stickKnobSize else 0.5f
                mStickRadius = mRadius * knobSizeRatio
            }
            invalidate()
        }

    private val castedData: ControlData.Joystick
        get() = controlData as ControlData.Joystick

    companion object {
        private const val TAG = "VirtualJoystick"

        // 8个方向常量（对应游戏中的实际方向）
        val DIR_NONE: Int = -1
        const val DIR_UP: Int = 0 // 上 (W)
        const val DIR_UP_RIGHT: Int = 1 // 右上 (W+D)
        const val DIR_RIGHT: Int = 2 // 右 (D)
        const val DIR_DOWN_RIGHT: Int = 3 // 右下 (S+D)
        const val DIR_DOWN: Int = 4 // 下 (S)
        const val DIR_DOWN_LEFT: Int = 5 // 左下 (S+A)
        const val DIR_LEFT: Int = 6 // 左 (A)
        const val DIR_UP_LEFT: Int = 7 // 左上 (W+A)

        private const val CLICK_ATTACK_INTERVAL_MS = 150 // 点击攻击间隔（毫秒）

        private const val MOUSE_CLICK_INTERVAL_MS = 100 // 鼠标点击间隔（毫秒）
        private const val MOUSE_MOVE_INTERVAL_MS = 16 // 鼠标移动更新间隔（约60fps）

        // 右摇杆移动阈值（像素，只有当摇杆位置变化超过此值时才移动鼠标）
        // 改为极小值以确保丝滑移动，死区已经处理了静止状态
        private const val JOYSTICK_MOVE_THRESHOLD = 0.1f

        // 死区（防止漂移）- 改为较小值以提高触摸灵敏度
        private const val DEADZONE_PERCENT = 0.05f

        // 8方向角度映射表（从角度计算结果映射到实际方向）
        // 角度计算：0度=正右, 90度=正上, 180度=正左, 270度=正下
        private val ANGLE_TO_DIR = intArrayOf(
            DIR_RIGHT,  // 0: 正右 (0度)
            DIR_UP_RIGHT,  // 1: 右上 (45度)
            DIR_UP,  // 2: 正上 (90度)
            DIR_UP_LEFT,  // 3: 左上 (135度)
            DIR_LEFT,  // 4: 正左 (180度)
            DIR_DOWN_LEFT,  // 5: 左下 (225度)
            DIR_DOWN,  // 6: 正下 (270度)
            DIR_DOWN_RIGHT // 7: 右下 (315度)
        )

        private fun triggerVibration(isPress: Boolean) {
            if (isPress) {
                RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30)
            } else {
                // 释放时不振动
//            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
            }
        }
    }

    // 绘制相关
    private lateinit var mBackgroundPaint: Paint
    private lateinit var mStickPaint: Paint
    private lateinit var mStrokePaint: Paint

    // 摇杆状态
    private var mCenterX = 0f
    private var mCenterY = 0f
    private var mStickX = 0f
    private var mStickY = 0f
    private var mRadius = 0f
    private var mStickRadius = 0f
    private var mCurrentDirection: Int = DIR_NONE
    private var mIsTouching = false
    private var mActivePointerId = -1 // 跟踪的触摸点 ID

    // 屏幕尺寸（用于右摇杆绝对位置计算）
    private var mScreenWidth = 0
    private var mScreenHeight = 0

    // 右摇杆攻击状态
    private var mIsAttacking = false
    private var mClickAttackHandler: Handler
    private val mClickAttackRunnable: Runnable

    // 鼠标模式右摇杆攻击状态
    private var mMouseLeftPressed = false // 鼠标左键是否按下
    private var mMouseClickRunnable: Runnable // 点击模式 Runnable
    private var mAttackMode = 0 // 攻击模式：0=长按, 1=点击, 2=持续

    // 鼠标移动状态
    private val mMouseMoveRunnable: Runnable? = null // 鼠标移动 Runnable
    private val mCurrentMouseDx = 0f // 当前摇杆 X 方向偏移
    private val mCurrentMouseDy = 0f // 当前摇杆 Y 方向偏移
    private val mMouseMoveActive = false // 鼠标移动是否激活

    // 右摇杆上一帧位置（用于计算位置变化量）
    private var mLastJoystickDx = 0f
    private var mLastJoystickDy = 0f

    // 运行时从全局设置读取的鼠标速度和范围
    private var mGlobalMouseSpeed = 80.0f
    private var mGlobalMouseRangeLeft = 0.0f
    private var mGlobalMouseRangeTop = 0.0f
    private var mGlobalMouseRangeRight = 1.0f
    private var mGlobalMouseRangeBottom = 1.0f

    init {
        // 获取屏幕尺寸（用于右摇杆绝对位置计算）
        val metrics = context.getResources().getDisplayMetrics()
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels


        // 读取全局设置（攻击模式、鼠标速度、鼠标范围）
        try {
            val settingsManager =
                SettingsManager.getInstance(context)
            mAttackMode = settingsManager.getMouseRightStickAttackMode()
            mGlobalMouseSpeed = settingsManager.getMouseRightStickSpeed().toFloat()
            mGlobalMouseRangeLeft = settingsManager.getMouseRightStickRangeLeft()
            mGlobalMouseRangeTop = settingsManager.getMouseRightStickRangeTop()
            mGlobalMouseRangeRight = settingsManager.getMouseRightStickRangeRight()
            mGlobalMouseRangeBottom = settingsManager.getMouseRightStickRangeBottom()


            // 验证范围有效性（从中心扩展模式）
            // 阈值范围 0.0-1.0：0.0=中心点, 1.0=全屏（最大）
            // 实际扩展距离 = 阈值 * 50%（因为从中心到边缘是屏幕的50%）
            var needsReset = false


            // 检查是否有无效值（负数或超过最大值1.0）
            if (mGlobalMouseRangeLeft < 0 || mGlobalMouseRangeLeft > 1.0 || mGlobalMouseRangeTop < 0 || mGlobalMouseRangeTop > 1.0 || mGlobalMouseRangeRight < 0 || mGlobalMouseRangeRight > 1.0 || mGlobalMouseRangeBottom < 0 || mGlobalMouseRangeBottom > 1.0) {
                Log.w(
                    TAG,
                    "Invalid mouse range detected (must be 0.0-1.0), resetting to full screen. Current: (" +
                            mGlobalMouseRangeLeft + "," + mGlobalMouseRangeTop + "," +
                            mGlobalMouseRangeRight + "," + mGlobalMouseRangeBottom + ")"
                )


                // 重置为全屏：100%
                mGlobalMouseRangeLeft = 1.0f
                mGlobalMouseRangeTop = 1.0f
                mGlobalMouseRangeRight = 1.0f
                mGlobalMouseRangeBottom = 1.0f
                needsReset = true
            }


            // 保存修正后的值（只在检测到无效值时才保存）
            if (needsReset) {
                settingsManager.setMouseRightStickRangeLeft(mGlobalMouseRangeLeft)
                settingsManager.setMouseRightStickRangeTop(mGlobalMouseRangeTop)
                settingsManager.setMouseRightStickRangeRight(mGlobalMouseRangeRight)
                settingsManager.setMouseRightStickRangeBottom(mGlobalMouseRangeBottom)
            }

            Log.i(
                TAG, "Global settings loaded: speed=" + mGlobalMouseSpeed +
                        ", range=(" + mGlobalMouseRangeLeft + "," + mGlobalMouseRangeTop +
                        "," + mGlobalMouseRangeRight + "," + mGlobalMouseRangeBottom + ")"
            )
        } catch (e: Exception) {
            mAttackMode = 0 // 默认长按模式
            mGlobalMouseSpeed = 80.0f // 默认速度80（范围60-200）
            mGlobalMouseRangeLeft = 0.0f
            mGlobalMouseRangeTop = 0.0f
            mGlobalMouseRangeRight = 1.0f
            mGlobalMouseRangeBottom = 1.0f
        }

        // 设置透明背景，确保只显示绘制的圆形
        setBackgroundColor(Color.TRANSPARENT)

        // 禁用裁剪，让方向指示线可以完整显示
        setClipToOutline(false)
        setClipBounds(null)

        // 初始化点击攻击 Handler（仅用于非鼠标移动模式）
        mClickAttackHandler = Handler(Looper.getMainLooper())
        mClickAttackRunnable = object : Runnable {
            override fun run() {
                if (mIsAttacking && mCurrentDirection != DIR_NONE && !false) {
                    // 点击模式：触发一次点击（释放然后按下）
                    performClickAttack(mCurrentDirection)
                    // 继续下一次点击
                    mClickAttackHandler.postDelayed(this, CLICK_ATTACK_INTERVAL_MS.toLong())
                }
            }
        }


        // 初始化鼠标模式点击 Runnable（仅点击模式使用）
        mMouseClickRunnable = Runnable {
            // 仅在点击模式下执行连续点击
            if (mIsAttacking && mMouseLeftPressed && mAttackMode == 1 && mInputBridge is SDLInputBridge) {
                val bridge1 = mInputBridge as SDLInputBridge
                // 使用屏幕中心作为点击位置
                val mouseX = mScreenWidth / 2.0f
                val mouseY = mScreenHeight / 2.0f

                // 发送鼠标左键点击（按下-释放）
                bridge1.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, true, mouseX, mouseY)
                // 短暂延迟后释放
                mClickAttackHandler.postDelayed(Runnable {
                    if (mIsAttacking && mMouseLeftPressed && mAttackMode == 1) {
                        bridge1.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, false, mouseX, mouseY)
                    }
                }, 50) // 50ms 按下时间

                // 继续下一次点击
                mClickAttackHandler.postDelayed(
                    mMouseClickRunnable,
                    MOUSE_CLICK_INTERVAL_MS.toLong()
                )
            }
        }

        initPaints()
    }

    private fun initPaints() {
        // RadialGamePad 风格的颜色系统
        // 背景圆：使用不透明的颜色值，通过 setAlpha 控制透明度，避免颜色值本身的透明度影响
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBackgroundPaint!!.setColor(-0x828283) // 不透明的灰色（RGB: 125, 125, 125）
        mBackgroundPaint!!.setStyle(Paint.Style.FILL)
        // 背景透明度只使用 opacity，不受 stickOpacity 影响
        // 直接使用用户设置的 opacity，让变化更明显
        mBackgroundPaint!!.setAlpha((castedData.opacity * 255).toInt())


        // 摇杆圆心：使用不透明的颜色值，通过 setAlpha 控制透明度
        mStickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mStickPaint!!.setColor(-0x828283) // 不透明的灰色（RGB: 125, 125, 125）
        mStickPaint!!.setStyle(Paint.Style.FILL)
        // 摇杆圆心透明度只使用 stickOpacity，如果没有设置则使用默认值 1.0（完全不透明），不受 opacity 影响
        // 直接使用用户设置的 stickOpacity，让变化更明显（0.0-1.0 全范围）
        val stickKnobAlpha = if (castedData.stickOpacity != 0f) castedData.stickOpacity else 1.0f
        mStickPaint!!.setAlpha((stickKnobAlpha * 255).toInt())


        // 描边默认透明（RadialGamePad 风格）
        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mStrokePaint!!.setColor(0x00000000) // 透明
        mStrokePaint!!.setStyle(Paint.Style.STROKE)
        mStrokePaint!!.setStrokeWidth(0f)
        // 边框透明度完全独立，默认1.0（完全不透明）
        val borderOpacity = if (castedData.borderOpacity != 0f) castedData.borderOpacity else 1.0f
        mStrokePaint!!.setAlpha((borderOpacity * 255).toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // 强制保持正方形（取最小边）
        val size = min(getMeasuredWidth(), getMeasuredHeight())
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 由于已经强制为正方形，w 和 h 应该相等
        mCenterX = w / 2f
        mCenterY = h / 2f
        mRadius = min(w, h) / 2f


        // RadialGamePad 风格：摇杆圆心是半径的 50%（0.5f * radius）
        // 如果配置了 stickKnobSize，则使用配置值，否则使用 RadialGamePad 默认值 0.5
        val knobSizeRatio = if (castedData.stickKnobSize != 0f) castedData.stickKnobSize else 0.5f
        mStickRadius = mRadius * knobSizeRatio
        resetStick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = getWidth() / 2f
        val centerY = getHeight() / 2f


        // 应用旋转
        if (castedData.rotation != 0f) {
            canvas.save()
            canvas.rotate(castedData.rotation, centerX, centerY)
        }


        // RadialGamePad 风格：背景圆使用 75% 半径（STICK_BACKGROUND_SIZE = 0.75f）
        val backgroundRadius = mRadius * 0.75f
        // 背景透明度只使用 opacity，不受 stickOpacity 影响
        // 直接使用用户设置的 opacity，让变化更明显
        mBackgroundPaint!!.setAlpha((castedData.opacity * 255).toInt())
        canvas.drawCircle(mCenterX, mCenterY, backgroundRadius, mBackgroundPaint!!)


        // 更新摇杆圆心透明度（如果数据已更新）
        // 摇杆圆心透明度只使用 stickOpacity，如果没有设置则使用默认值 1.0（完全不透明），不受 opacity 影响
        // 直接使用用户设置的 stickOpacity，让变化更明显（0.0-1.0 全范围）
        val stickKnobAlpha = if (castedData.stickOpacity != 0f) castedData.stickOpacity else 1.0f
        mStickPaint!!.setAlpha((stickKnobAlpha * 255).toInt())


        // 绘制摇杆圆心（前景圆，根据触摸位置移动）
        // RadialGamePad 风格：摇杆圆心是背景半径的 50%（0.5f * radius）
        // 但我们已经根据 mStickRadius 计算了，这里直接使用
        canvas.drawCircle(mStickX, mStickY, mStickRadius, mStickPaint!!)


        // 恢复旋转
        if (castedData.rotation != 0f) {
            canvas.restore()
        }
    }

    /**
     * 获取方向对应的角度（用于绘制指示线）
     */
    private fun getAngleForDirection(direction: Int): Float {
        when (direction) {
            DIR_RIGHT -> return 0f
            DIR_UP_RIGHT -> return 45f
            DIR_UP -> return 90f
            DIR_UP_LEFT -> return 135f
            DIR_LEFT -> return 180f
            DIR_DOWN_LEFT -> return 225f
            DIR_DOWN -> return 270f
            DIR_DOWN_RIGHT -> return 315f
            else -> return -1f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.getActionMasked()
        val pointerId = event.getPointerId(event.getActionIndex())

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 如果已经在跟踪一个触摸点，忽略新的
                if (mIsTouching) {
                    return false
                }

                val pointerIndex = event.getActionIndex()
                val touchX = event.getX(pointerIndex)
                val touchY = event.getY(pointerIndex)


                // 检查触摸点是否在圆形区域内
                val dx = touchX - mCenterX
                val dy = touchY - mCenterY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                // 只响应圆形区域内的触摸
                if (distance > mRadius) {
                    return false
                }

                // 记录触摸点
                mActivePointerId = pointerId
                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!controlData.isPassThrough) {
                    TouchPointerTracker.consumePointer(pointerId)
                }

                // 如果是右摇杆鼠标模式，初始化并设置虚拟鼠标范围
                if (castedData.mode == ControlData.Joystick.Mode.MOUSE && castedData.isRightStick) {
                    // 初始化虚拟鼠标（使用实际屏幕尺寸）
                    if (mInputBridge is SDLInputBridge) {
                        (mInputBridge as SDLInputBridge).initVirtualMouse(
                            mScreenWidth,
                            mScreenHeight
                        )
                    }
                    setVirtualMouseRange()
                }

                handleMove(touchX, touchY)
                mIsTouching = true
                triggerVibration(true)
                // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                return !controlData.isPassThrough
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsTouching || mActivePointerId == -1) {
                    return false
                }


                // 找到我们跟踪的触摸点
                val pointerIndex = event.findPointerIndex(mActivePointerId)
                if (pointerIndex == -1) {
                    return false
                }

                val touchX = event.getX(pointerIndex)
                val touchY = event.getY(pointerIndex)
                handleMove(touchX, touchY)
                // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                return !controlData.isPassThrough
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                // 检查是否是我们跟踪的触摸点
                if (pointerId == mActivePointerId && mIsTouching) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!controlData.isPassThrough) {
                        TouchPointerTracker.releasePointer(mActivePointerId)
                    }
                    mActivePointerId = -1

                    handleRelease()
                    mIsTouching = false
                    triggerVibration(false)
                    // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                    return !controlData.isPassThrough
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleMove(touchX: Float, touchY: Float) {
        val dx = touchX - mCenterX
        val dy = touchY - mCenterY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()


        // 计算摇杆位置（限制在圆内）
        val maxDistance = mRadius - mStickRadius
        if (distance > maxDistance) {
            // 触摸点超出摇杆圆，限制在边缘
            val ratio = maxDistance / distance
            mStickX = mCenterX + dx * ratio
            mStickY = mCenterY + dy * ratio
        } else {
            // 触摸点在摇杆圆内，摇杆小圆点跟随触摸点（提供视觉反馈）
            // 注意：即使在死区内，小圆点也会移动，但不会触发输入事件（由后续的死区检测处理）
            mStickX = touchX
            mStickY = touchY
        }


        // 根据模式发送不同的输入事件
        if (castedData.mode == ControlData.Joystick.Mode.MOUSE) {
            // 鼠标模式：根据xboxUseRightStick区分左右摇杆
            if (castedData.isRightStick) {
                // 右摇杆：鼠标移动模式 + 持续点击攻击
                // 直接判断是否在死区外（有效区域），而不是通过方向变化判断
                val inActiveZone = (distance >= mRadius * DEADZONE_PERCENT)


                // 检测攻击状态变化：进入/离开死区时启动/停止攻击
                if (!mIsAttacking && inActiveZone) {
                    // 从死区进入有效区域：开始持续鼠标左键点击
                    startMouseClick()
                    mIsAttacking = true
                } else if (mIsAttacking && !inActiveZone) {
                    // 从有效区域返回死区：停止持续点击
                    stopMouseClick()
                    mIsAttacking = false
                }


                // 更新方向（用于其他逻辑，如UI显示）
                val newDirection = calculateDirection(dx, dy, distance)
                mCurrentDirection = newDirection


                // 发送鼠标相对移动（只有当摇杆位置变化时才移动）
                sendVirtualMouseMove(dx, dy, distance)
            } else {
                // 左摇杆：将摇杆偏移量转换为鼠标移动
                sendMouseMove(dx, dy, distance)
            }
        } else if (castedData.mode == ControlData.Joystick.Mode.GAMEPAD) {
            // SDL控制器模式：发送模拟摇杆输入
            sendSDLStick(dx, dy, distance, maxDistance)
        } else {
            // 键盘模式：计算方向并发送按键事件
            val newDirection = calculateDirection(dx, dy, distance)
            if (newDirection != mCurrentDirection) {
                // 释放旧方向的按键
                releaseDirection(mCurrentDirection)
                // 按下新方向的按键
                pressDirection(newDirection)
                mCurrentDirection = newDirection
            }
        }

        invalidate()
    }

    private fun handleRelease() {
        resetStick()


        // 重置右摇杆上一帧位置
        mLastJoystickDx = 0f
        mLastJoystickDy = 0f


        // 根据模式执行不同的释放操作
        if (castedData.mode == ControlData.Joystick.Mode.MOUSE) {
            // 鼠标模式
            if (castedData.isRightStick) {
                // 右摇杆：停止持续点击
                stopMouseClick()
                mIsAttacking = false


                // 重要：保存当前虚拟鼠标位置，防止松开时鼠标位置被重置
                // 因为触摸事件可能会被SDL转换为鼠标事件，导致鼠标位置跳回触摸位置
                // 使用延迟执行确保在SDL处理完触摸事件后再设置鼠标位置
                if (mInputBridge is SDLInputBridge) {
                    val bridge = mInputBridge as SDLInputBridge
                    val currentX = bridge.virtualMouseX
                    val currentY = bridge.virtualMouseY


                    // 延迟50ms执行，确保SDL处理完触摸事件
                    postDelayed(object : Runnable {
                        override fun run() {
                            bridge.setVirtualMousePosition(currentX, currentY)
                            Log.d(
                                TAG,
                                "Restored mouse position after release: (" + currentX + ", " + currentY + ")"
                            )
                        }
                    }, 50)
                }
            }
            mCurrentDirection = DIR_NONE
        } else if (castedData.mode == ControlData.Joystick.Mode.KEYBOARD) {
            // 键盘模式：释放按键和组合键
            releaseDirection(mCurrentDirection)
            mCurrentDirection = DIR_NONE
        } else if (castedData.mode == ControlData.Joystick.Mode.GAMEPAD) {
            // SDL控制器模式：摇杆回中
            if (castedData.isRightStick) {
                mInputBridge.sendXboxRightStick(0.0f, 0.0f)
            } else {
                mInputBridge.sendXboxLeftStick(0.0f, 0.0f)
            }
            mCurrentDirection = DIR_NONE
        }

        invalidate()
    }

    private fun resetStick() {
        mStickX = mCenterX
        mStickY = mCenterY
    }

    /**
     * 计算摇杆方向（8方向）
     * 优化算法：使用角度映射表确保方向准确
     */
    private fun calculateDirection(dx: Float, dy: Float, distance: Float): Int {
        // 死区检测 - 小范围内不触发方向
        if (distance < mRadius * DEADZONE_PERCENT) {
            return DIR_NONE
        }


        // 计算角度（0度为正右方，逆时针）
        // 注意：屏幕坐标系y轴向下为正，所以dy取负使得向上为负角度
        var angle = atan2(-dy.toDouble(), dx.toDouble()) * 180 / Math.PI


        // 标准化角度到0-360度
        if (angle < 0) angle += 360.0


        // 将360度分成8个扇区（每个45度）
        // 添加22.5度偏移使得每个方向占据45度的中心区域
        // 例如：正右方(0度)占据[-22.5, 22.5]度，右上方(45度)占据[22.5, 67.5]度
        val angleIndex = ((angle + 22.5) / 45.0).toInt() % 8


        // 使用映射表转换为实际方向
        val direction: Int = ANGLE_TO_DIR[angleIndex]

        return direction
    }

    /**
     * 按下方向对应的按键
     * joystickKeys数组：[上W, 右D, 下S, 左A]（可选，如果为null则不发送键盘按键）
     */
    private fun pressDirection(direction: Int) {
        if (direction == DIR_NONE) return


        // 发送方向键（如果joystickKeys不为null）
        if (castedData.joystickKeys != null) {
            when (direction) {
                DIR_UP -> mInputBridge.sendKey(castedData.joystickKeys[0], true)
                DIR_UP_RIGHT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[0], true) // W (up)
                    mInputBridge.sendKey(castedData.joystickKeys[1], true) // D (right)
                }

                DIR_RIGHT -> mInputBridge.sendKey(castedData.joystickKeys[1], true)
                DIR_DOWN_RIGHT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[2], true) // S (down)
                    mInputBridge.sendKey(castedData.joystickKeys[1], true) // D (right)
                }

                DIR_DOWN -> mInputBridge.sendKey(castedData.joystickKeys[2], true)
                DIR_DOWN_LEFT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[2], true) // S (down)
                    mInputBridge.sendKey(castedData.joystickKeys[3], true) // A (left)
                }

                DIR_LEFT -> mInputBridge.sendKey(castedData.joystickKeys[3], true)
                DIR_UP_LEFT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[0], true) // W (up)
                    mInputBridge.sendKey(castedData.joystickKeys[3], true) // A (left)
                }
            }
        }
    }

    /**
     * 释放方向对应的按键
     */
    private fun releaseDirection(direction: Int) {
        if (direction == DIR_NONE) return


        // 释放方向键（如果joystickKeys不为null）
        if (castedData.joystickKeys != null) {
            when (direction) {
                DIR_UP -> mInputBridge.sendKey(castedData.joystickKeys[0], false)
                DIR_UP_RIGHT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[0], false)
                    mInputBridge.sendKey(castedData.joystickKeys[1], false)
                }

                DIR_RIGHT -> mInputBridge.sendKey(castedData.joystickKeys[1], false)
                DIR_DOWN_RIGHT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[2], false)
                    mInputBridge.sendKey(castedData.joystickKeys[1], false)
                }

                DIR_DOWN -> mInputBridge.sendKey(castedData.joystickKeys[2], false)
                DIR_DOWN_LEFT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[2], false)
                    mInputBridge.sendKey(castedData.joystickKeys[3], false)
                }

                DIR_LEFT -> mInputBridge.sendKey(castedData.joystickKeys[3], false)
                DIR_UP_LEFT -> {
                    mInputBridge.sendKey(castedData.joystickKeys[0], false)
                    mInputBridge.sendKey(castedData.joystickKeys[3], false)
                }
            }
        }
    }

    /**
     * 停止持续攻击 - 清除虚拟触屏点
     */
    private fun stopContinuousAttack() {
        if (!mIsAttacking) return
        mIsAttacking = false
        // 释放攻击状态
        // 组合键已移除
    }

    /**
     * 更新攻击方向（方向改变时调用，用于持续攻击模式）
     * 会自动释放旧方向的虚拟触屏点并按下新方向
     */
    private fun updateAttackDirection(oldDirection: Int, newDirection: Int) {
        // 释放旧方向
        if (oldDirection != DIR_NONE) {
            // 组合键已移除
        }
        // 按下新方向
        if (newDirection != DIR_NONE) {
            // 组合键已移除
        }
    }

    /**
     * 启动点击攻击模式
     */
    private fun startClickAttack() {
        if (mIsAttacking) return
        mIsAttacking = true
        // 立即触发第一次点击
        performClickAttack(mCurrentDirection)
        // 启动持续点击循环
        mClickAttackHandler.postDelayed(mClickAttackRunnable, CLICK_ATTACK_INTERVAL_MS.toLong())
    }

    /**
     * 停止点击攻击模式
     */
    private fun stopClickAttack() {
        if (!mIsAttacking) return
        mIsAttacking = false
        mClickAttackHandler.removeCallbacks(mClickAttackRunnable)
        // 确保释放最后的虚拟触屏点
        // 组合键已移除
    }

    /**
     * 启动鼠标攻击（鼠标模式右摇杆）
     *
     * 根据攻击模式执行不同行为：
     * - 长按模式 (0)：按下鼠标左键，保持按住
     * - 点击模式 (1)：快速连续点击
     * - 持续模式 (2)：按下鼠标左键，保持按住（同长按）
     */
    private fun startMouseClick() {
        if (mMouseLeftPressed) return
        mMouseLeftPressed = true

        if (mInputBridge is SDLInputBridge) {
            val bridge = mInputBridge as SDLInputBridge
            // 使用当前虚拟鼠标位置作为点击位置，避免鼠标跳动
            val mouseX = bridge.virtualMouseX
            val mouseY = bridge.virtualMouseY


            // Log.v(TAG, "Mouse attack started at (" + mouseX + "," + mouseY + "), mode=" + mAttackMode);
            when (mAttackMode) {
                0, 2 -> bridge.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, true, mouseX, mouseY)
                1 -> {
                    // 立即发送第一次点击
                    bridge.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, true, mouseX, mouseY)
                    // 启动连续点击循环
                    mClickAttackHandler.postDelayed(
                        mMouseClickRunnable,
                        MOUSE_CLICK_INTERVAL_MS.toLong()
                    )
                }
            }
        }
    }

    /**
     * 停止鼠标攻击
     */
    private fun stopMouseClick() {
        if (!mMouseLeftPressed) return
        mMouseLeftPressed = false


        // 停止点击循环（点击模式）
        mClickAttackHandler.removeCallbacks(mMouseClickRunnable)


        // 释放鼠标左键（所有模式都需要）
        if (mInputBridge is SDLInputBridge) {
            val bridge = mInputBridge as SDLInputBridge
            // 使用当前虚拟鼠标位置作为释放位置
            val mouseX = bridge.virtualMouseX
            val mouseY = bridge.virtualMouseY
            bridge.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, false, mouseX, mouseY)


            // Log.v(TAG, "Mouse attack stopped at (" + mouseX + "," + mouseY + ")");
        }
    }

    /**
     * 执行一次点击攻击（按下然后短暂后释放）
     */
    private fun performClickAttack(direction: Int) {
        if (direction == DIR_NONE) return


        // 组合键已移除，直接使用鼠标左键点击
        val pos = calculateDirectionPosition(direction)
        if (mInputBridge is SDLInputBridge) {
            val bridge = mInputBridge as SDLInputBridge
            // 按下
            bridge.sendVirtualTouch(SDLInputBridge.Companion.VIRTUAL_TOUCH_RIGHT_STICK, pos[0], pos[1], true)
            // 短暂延迟后释放
            mClickAttackHandler.postDelayed(Runnable {
                if (mIsAttacking) {
                    bridge.sendVirtualTouch(
                        SDLInputBridge.Companion.VIRTUAL_TOUCH_RIGHT_STICK,
                        pos[0],
                        pos[1],
                        false
                    )
                }
            }, 50) // 50ms 按下时间
        }
    }

    /**
     * 计算八方向对应的屏幕位置（用于右摇杆八方向攻击）
     * @param direction 方向常量
     * @return float[] {x, y} 屏幕坐标
     */
    private fun calculateDirectionPosition(direction: Int): FloatArray {
        // 计算瞄准距离（200像素）
        val aimDistance = 200f
        val centerX = mScreenWidth / 2.0f
        val centerY = mScreenHeight / 2.0f

        var targetX = centerX
        var targetY = centerY


        // 计算对角线距离（45度方向使用 0.707 = cos(45°)）
        val diagonalOffset = aimDistance * 0.707f

        when (direction) {
            DIR_UP -> targetY = centerY - aimDistance
            DIR_DOWN -> targetY = centerY + aimDistance
            DIR_LEFT -> targetX = centerX - aimDistance
            DIR_RIGHT -> targetX = centerX + aimDistance
            DIR_UP_LEFT -> {
                targetX = centerX - diagonalOffset
                targetY = centerY - diagonalOffset
            }

            DIR_UP_RIGHT -> {
                targetX = centerX + diagonalOffset
                targetY = centerY - diagonalOffset
            }

            DIR_DOWN_LEFT -> {
                targetX = centerX - diagonalOffset
                targetY = centerY + diagonalOffset
            }

            DIR_DOWN_RIGHT -> {
                targetX = centerX + diagonalOffset
                targetY = centerY + diagonalOffset
            }
        }


        // 限制在屏幕范围内
        targetX = max(0f, min(mScreenWidth.toFloat(), targetX))
        targetY = max(0f, min(mScreenHeight.toFloat(), targetY))

        return floatArrayOf(targetX, targetY)
    }

    /**
     * 发送鼠标移动事件（鼠标模式，用于左摇杆）
     * 将摇杆偏移量转换为鼠标移动增量
     */
    private fun sendMouseMove(dx: Float, dy: Float, distance: Float) {
        // 死区检测
        if (distance < mRadius * DEADZONE_PERCENT) {
            return
        }


        // 标准化偏移量到 [-1, 1] 范围
        val maxDistance = mRadius - mStickRadius
        val normalizedX = dx / maxDistance
        val normalizedY = dy / maxDistance


        // 应用灵敏度系数（可调整）
        val sensitivity = 15.0f // 鼠标移动速度倍数
        val mouseX = normalizedX * sensitivity
        val mouseY = normalizedY * sensitivity


        // 发送鼠标移动事件
        mInputBridge.sendMouseMove(mouseX, mouseY)
    }

    /**
     * 发送虚拟鼠标移动事件（用于右摇杆鼠标移动模式）
     *
     * 功能：
     * - 摇杆静止不动时 → 鼠标不移动
     * - 摇杆位置变化时 → 鼠标跟随移动
     * - 使用 sendMouseMove 发送真正的鼠标相对移动事件
     * - 同时更新虚拟鼠标位置追踪，以便松开时恢复位置
     */
    private fun sendVirtualMouseMove(dx: Float, dy: Float, distance: Float) {
        // 死区检测：在死区内时，重置上一帧位置
        val deadzone: Float = mRadius * DEADZONE_PERCENT
        if (distance < deadzone) {
            mLastJoystickDx = 0f
            mLastJoystickDy = 0f
            return
        }


        // 计算摇杆位置的变化量（当前位置 - 上一帧位置）
        val deltaDx = dx - mLastJoystickDx
        val deltaDy = dy - mLastJoystickDy


        // 始终更新上一帧位置，防止delta累积导致的跳动
        mLastJoystickDx = dx
        mLastJoystickDy = dy


        // 计算变化距离
        val deltaDistance = sqrt((deltaDx * deltaDx + deltaDy * deltaDy).toDouble()).toFloat()


        // 如果摇杆位置变化极小，不移动鼠标（过滤微小抖动）
        if (deltaDistance < JOYSTICK_MOVE_THRESHOLD) {
            // 位置变化太小，不移动鼠标，但已更新mLast值避免累积
            return
        }


        // 死区平滑映射：将死区外的范围重新映射到 [0, 1]，避免死区边缘突变
        // 公式：adjusted = (actual - deadzone) / (max - deadzone)
        // 这样死区边缘输出为0，最大距离输出为1，中间平滑过渡
        val maxDistance = mRadius - mStickRadius
        val deadzoneAdjustedMax = maxDistance - deadzone


        // 对变化量应用死区映射（基于当前位置的距离）
        val currentDistance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        var mappingRatio = 1.0f
        if (currentDistance > deadzone && deadzoneAdjustedMax > 0) {
            // 计算映射比例，让死区边缘的移动更平滑
            val adjustedDistance = currentDistance - deadzone
            mappingRatio = adjustedDistance / deadzoneAdjustedMax
            // 限制在合理范围
            if (mappingRatio > 1.0f) mappingRatio = 1.0f
        }


        // 标准化变化量，并应用死区映射比例
        val normalizedDeltaX = (deltaDx / maxDistance) * mappingRatio
        val normalizedDeltaY = (deltaDy / maxDistance) * mappingRatio


        // 应用灵敏度系数（使用全局设置的速度）
        val sensitivity = mGlobalMouseSpeed
        val mouseX = normalizedDeltaX * sensitivity
        val mouseY = normalizedDeltaY * sensitivity


        // 更新虚拟鼠标位置追踪（用于松开时恢复位置，并应用范围限制）
        var actualMouseX = mouseX
        var actualMouseY = mouseY

        if (mInputBridge is SDLInputBridge) {
            // 更新虚拟鼠标位置，获取实际移动的量（经过范围限制）
            val actualDelta =
                (mInputBridge as SDLInputBridge).updateVirtualMouseDelta(mouseX, mouseY) ?:
                floatArrayOf(mouseX, mouseY)
            actualMouseX = actualDelta[0]
            actualMouseY = actualDelta[1]
        }


        // 发送真正的鼠标相对移动事件（使用实际移动量，而不是请求的移动量）
        mInputBridge.sendMouseMove(actualMouseX, actualMouseY)


        // Log.v(TAG, "Mouse move: requested=(" + mouseX + ", " + mouseY + "), actual=(" + actualMouseX + ", " + actualMouseY + ")");
    }

    /**
     * 设置虚拟鼠标范围（右摇杆鼠标移动模式）
     */
    private fun setVirtualMouseRange() {
        if (mInputBridge is SDLInputBridge) {
            val bridge = mInputBridge as SDLInputBridge
            // 从全局设置实时读取最新的范围值（而不是使用缓存的变量）
            try {
                val settingsManager =
                    SettingsManager.getInstance(getContext())
                var left = settingsManager.getMouseRightStickRangeLeft()
                var top = settingsManager.getMouseRightStickRangeTop()
                var right = settingsManager.getMouseRightStickRangeRight()
                var bottom = settingsManager.getMouseRightStickRangeBottom()

                Log.i(
                    TAG, "setVirtualMouseRange: Read from settings: left=" + left + ", top=" + top +
                            ", right=" + right + ", bottom=" + bottom
                )


                // 验证范围有效性（0.0-1.0）
                if (left < 0 || left > 1.0) {
                    Log.w(TAG, "Invalid left range: " + left + ", resetting to 1.0")
                    left = 1.0f
                }
                if (top < 0 || top > 1.0) {
                    Log.w(TAG, "Invalid top range: " + top + ", resetting to 1.0")
                    top = 1.0f
                }
                if (right < 0 || right > 1.0) {
                    Log.w(TAG, "Invalid right range: " + right + ", resetting to 1.0")
                    right = 1.0f
                }
                if (bottom < 0 || bottom > 1.0) {
                    Log.w(TAG, "Invalid bottom range: " + bottom + ", resetting to 1.0")
                    bottom = 1.0f
                }

                Log.i(
                    TAG,
                    "setVirtualMouseRange: Applying range to native: left=" + left + ", top=" + top +
                            ", right=" + right + ", bottom=" + bottom + " (in percentage: " + (left * 100).toInt() + "%, " + (top * 100).toInt() + "%, " + (right * 100).toInt() + "%, " + (bottom * 100).toInt() + "%)"
                )

                bridge.setVirtualMouseRange(left, top, right, bottom)
            } catch (e: Exception) {
                // 如果读取失败，使用缓存的默认值
                Log.w(TAG, "Failed to read mouse range settings, using cached values", e)
                bridge.setVirtualMouseRange(
                    mGlobalMouseRangeLeft, mGlobalMouseRangeTop,
                    mGlobalMouseRangeRight, mGlobalMouseRangeBottom
                )
            }
        }
    }

    /**
     * 发送SDL控制器摇杆输入（SDL控制器模式）
     * 将摇杆偏移量转换为标准化的模拟摇杆值
     */
    private fun sendSDLStick(dx: Float, dy: Float, distance: Float, maxDistance: Float) {
        // 标准化偏移量到 [-1, 1] 范围
        var normalizedX = 0.0f
        var normalizedY = 0.0f

        // 死区处理：在死区内返回0，死区外进行平滑映射
        val deadzone: Float = mRadius * DEADZONE_PERCENT
        if (distance > deadzone) {
            // 计算超出死区的距离比例
            val adjustedDistance = distance - deadzone
            val adjustedMax = maxDistance - deadzone
            var ratio = adjustedDistance / adjustedMax

            // 限制在 [0, 1] 范围
            if (ratio > 1.0f) ratio = 1.0f

            // 应用方向
            normalizedX = (dx / distance) * ratio
            normalizedY = (dy / distance) * ratio
        }

        // 发送到对应的Xbox摇杆（左或右）
        if (castedData.isRightStick) {
            mInputBridge.sendXboxRightStick(normalizedX, normalizedY)
        } else {
            mInputBridge.sendXboxLeftStick(normalizedX, normalizedY)
        }
    }

    /**
     * 获取方向名称（用于日志）
     */
    private fun getDirectionName(direction: Int): String {
        when (direction) {
            DIR_UP -> return "UP"
            DIR_UP_RIGHT -> return "UP_RIGHT"
            DIR_RIGHT -> return "RIGHT"
            DIR_DOWN_RIGHT -> return "DOWN_RIGHT"
            DIR_DOWN -> return "DOWN"
            DIR_DOWN_LEFT -> return "DOWN_LEFT"
            DIR_LEFT -> return "LEFT"
            DIR_UP_LEFT -> return "UP_LEFT"
            else -> return "NONE"
        }
    }


    /**
     * 设置Xbox控制器模式下控制哪个摇杆
     * @param useRightStick true=右摇杆, false=左摇杆（默认）
     */
    fun setXboxStickMode(useRightStick: Boolean) {
        castedData.isRightStick = useRightStick
    }

    val isXboxRightStick: Boolean
        /**
         * 获取当前控制的Xbox摇杆类型
         * @return true=右摇杆, false=左摇杆
         */
        get() = castedData.isRightStick

    private fun dpToPx(dp: Float): Float {
        return dp * getResources().getDisplayMetrics().density
    }
}

