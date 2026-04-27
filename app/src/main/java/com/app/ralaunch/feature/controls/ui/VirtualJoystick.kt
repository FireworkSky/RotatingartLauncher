package com.app.ralaunch.feature.controls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.app.ralaunch.core.logging.AppLog
import android.view.View
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import com.app.ralaunch.feature.controls.ControlData
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.textures.TextureLoader
import com.app.ralaunch.feature.controls.textures.TextureRenderer
import com.app.ralaunch.core.common.SettingsAccess
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 虚拟摇杆View
 * 支持8方向移动，触摸拖拽控制
 */
class VirtualJoystick(
    context: Context,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            // 重新计算摇杆圆心大小（因为 stickKnobSize 可能已改变）
            if (mRadius > 0) {
                // 直接使用 stickKnobSize，0是有效值（可以让摇杆圆心不可见）
                mStickRadius = mRadius * castedData.stickKnobSize
            }
            invalidate()
        }

    private val castedData: ControlData.Joystick
        get() = controlData as ControlData.Joystick
    
    // 纹理相关
    private var textureLoader: TextureLoader? = null
    private var assetsDir: File? = null
    private val bgBoundsRectF = RectF()
    private val knobBoundsRectF = RectF()
    private val bgClipPath = Path()
    private val knobClipPath = Path()
    
    /** 设置控件包资源目录（用于加载纹理） */
    override fun setPackAssetsDir(dir: File?) {
        assetsDir = dir
        if (dir != null && textureLoader == null) {
            textureLoader = TextureLoader.getInstance(context)
        }
        invalidate()
    }

    companion object {
        private const val TAG = "VirtualJoystick"

        // 8个方向常量（对应游戏中的实际方向）
        const val DIR_NONE: Int = -1
        const val DIR_UP: Int = 0 // 上 (W)
        const val DIR_UP_RIGHT: Int = 1 // 右上 (W+D)
        const val DIR_RIGHT: Int = 2 // 右 (D)
        const val DIR_DOWN_RIGHT: Int = 3 // 右下 (S+D)
        const val DIR_DOWN: Int = 4 // 下 (S)
        const val DIR_DOWN_LEFT: Int = 5 // 左下 (S+A)
        const val DIR_LEFT: Int = 6 // 左 (A)
        const val DIR_UP_LEFT: Int = 7 // 左上 (W+A)

        private const val STICK_BACKGROUND_SIZE = 0.75f // 背景圆占摇杆半径的比例

        // 死区（防止漂移）- 改为较小值以提高触摸灵敏度
        private const val DEADZONE_PERCENT = 0.05f
        private const val DEADZONE_KEYBOARD_PERCENT = 0.4f

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

    private fun triggerVibration(isPress: Boolean) {
        if (isPress) {
            vibrationManager?.vibrateOneShot(50, 30)
        }
        // 释放时不振动
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

    // 运行时从全局设置读取的鼠标速度和范围
    private var mGlobalMouseSpeed = 80.0f
    private var mGlobalMouseRangeLeft = 0.0f
    private var mGlobalMouseRangeTop = 0.0f
    private var mGlobalMouseRangeRight = 1.0f
    private var mGlobalMouseRangeBottom = 1.0f

    init {
        // 获取屏幕尺寸（用于右摇杆绝对位置计算）
        val metrics = context.resources.displayMetrics
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels


        // 读取全局设置（攻击模式、鼠标速度、鼠标范围）
        try {
            val settingsManager = SettingsAccess
            mGlobalMouseSpeed = settingsManager.mouseRightStickSpeed.toFloat()
            mGlobalMouseRangeLeft = settingsManager.mouseRightStickRangeLeft
            mGlobalMouseRangeTop = settingsManager.mouseRightStickRangeTop
            mGlobalMouseRangeRight = settingsManager.mouseRightStickRangeRight
            mGlobalMouseRangeBottom = settingsManager.mouseRightStickRangeBottom


            // 验证范围有效性（从中心扩展模式）
            // 阈值范围 0.0-1.0：0.0=中心点, 1.0=全屏（最大）
            // 实际扩展距离 = 阈值 * 50%（因为从中心到边缘是屏幕的50%）
            var needsReset = false


            // 检查是否有无效值（负数或超过最大值1.0）
            if (mGlobalMouseRangeLeft < 0 || mGlobalMouseRangeLeft > 1.0 || mGlobalMouseRangeTop < 0 || mGlobalMouseRangeTop > 1.0 || mGlobalMouseRangeRight < 0 || mGlobalMouseRangeRight > 1.0 || mGlobalMouseRangeBottom < 0 || mGlobalMouseRangeBottom > 1.0) {
                AppLog.w(
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
                settingsManager.mouseRightStickRangeLeft = mGlobalMouseRangeLeft
                settingsManager.mouseRightStickRangeTop = mGlobalMouseRangeTop
                settingsManager.mouseRightStickRangeRight = mGlobalMouseRangeRight
                settingsManager.mouseRightStickRangeBottom = mGlobalMouseRangeBottom
            }

            AppLog.i(
                TAG, "Global settings loaded: speed=" + mGlobalMouseSpeed +
                        ", range=(" + mGlobalMouseRangeLeft + "," + mGlobalMouseRangeTop +
                        "," + mGlobalMouseRangeRight + "," + mGlobalMouseRangeBottom + ")"
            )
        } catch (_: Exception) {
            mGlobalMouseSpeed = 80.0f // 默认速度80（范围60-200）
            mGlobalMouseRangeLeft = 0.0f
            mGlobalMouseRangeTop = 0.0f
            mGlobalMouseRangeRight = 1.0f
            mGlobalMouseRangeBottom = 1.0f
        }

        // 设置透明背景，确保只显示绘制的圆形
        setBackgroundColor(Color.TRANSPARENT)

        // 禁用裁剪，让方向指示线可以完整显示
        clipToOutline = false
        clipBounds = null

        initPaints()
    }

    private fun initPaints() {
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x828283 // 不透明的灰色（RGB: 125, 125, 125）
            style = Paint.Style.FILL
            alpha = (castedData.opacity * 255).toInt()
        }

        mStickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x828283
            style = Paint.Style.FILL
            alpha = (castedData.stickOpacity * 255).toInt()
        }

        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x00000000 // 透明
            style = Paint.Style.STROKE
            strokeWidth = 0f
            alpha = (castedData.borderOpacity * 255).toInt()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // 强制保持正方形（取最小边）
        val size = min(measuredWidth, measuredHeight)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 由于已经强制为正方形，w 和 h 应该相等
        mCenterX = w / 2f
        mCenterY = h / 2f
        mRadius = min(w, h) / 2f


        // RadialGamePad 风格：摇杆圆心是半径的 50%（0.5f * radius）
        // 直接使用 stickKnobSize，0是有效值（可以让摇杆圆心不可见）
        mStickRadius = mRadius * castedData.stickKnobSize
        resetStick()
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

        // RadialGamePad 风格：背景圆使用 75% 半径
        val backgroundRadius = mRadius * STICK_BACKGROUND_SIZE
        
        // 检查是否有纹理
        val hasTexture = castedData.texture.hasAnyTexture && assetsDir != null && textureLoader != null
        
        if (hasTexture) {
            // 更新背景边界和裁剪路径
            bgBoundsRectF.set(
                mCenterX - backgroundRadius,
                mCenterY - backgroundRadius,
                mCenterX + backgroundRadius,
                mCenterY + backgroundRadius
            )
            bgClipPath.reset()
            bgClipPath.addCircle(mCenterX, mCenterY, backgroundRadius, Path.Direction.CW)
            
            // 更新摇杆头边界和裁剪路径
            knobBoundsRectF.set(
                mStickX - mStickRadius,
                mStickY - mStickRadius,
                mStickX + mStickRadius,
                mStickY + mStickRadius
            )
            knobClipPath.reset()
            knobClipPath.addCircle(mStickX, mStickY, mStickRadius, Path.Direction.CW)
            
            // 使用纹理渲染
            TextureRenderer.renderJoystick(
                canvas = canvas,
                textureLoader = textureLoader!!,
                assetsDir = assetsDir,
                textureConfig = castedData.texture,
                backgroundBounds = bgBoundsRectF,
                knobBounds = knobBoundsRectF,
                isPressed = mIsTouching,
                backgroundClipPath = bgClipPath,
                knobClipPath = knobClipPath,
                backgroundOpacityMultiplier = castedData.opacity,
                knobOpacityMultiplier = castedData.stickOpacity
            )
            
            // 如果纹理没有完全覆盖，仍然绘制默认形状作为fallback
            if (!castedData.texture.background.enabled) {
                mBackgroundPaint.alpha = (castedData.opacity * 255).toInt()
                canvas.drawCircle(mCenterX, mCenterY, backgroundRadius, mBackgroundPaint)
            }
            if (!castedData.texture.knob.enabled) {
                mStickPaint.alpha = (castedData.stickOpacity * 255).toInt()
                canvas.drawCircle(mStickX, mStickY, mStickRadius, mStickPaint)
            }
        } else {
            // 绘制具有深度感的背景 (自动适配深浅主题)
            val bgAlpha = (castedData.opacity * 255).toInt()
            val knobAlpha = (castedData.stickOpacity * 255).toInt()
            
            // 自动检测深浅色主题 (根据背景亮度)
            val isDarkTheme = Color.luminance(castedData.bgColor) < 0.5f
            // 如果用户设置了非透明的边框颜色，则使用用户设置的；否则自动计算
            val userStrokeColor = castedData.strokeColor
            val hasUserStrokeColor = (userStrokeColor ushr 24) > 0 // alpha > 0
            val baseStrokeColor = if (hasUserStrokeColor) userStrokeColor else (if (isDarkTheme) Color.WHITE else Color.BLACK)
            val baseKnobColor = if (isDarkTheme) -0x828283 else Color.LTGRAY.toInt()

            // 绘制底座阴影/发光
            mBackgroundPaint.alpha = (bgAlpha * 0.8f).toInt()
            canvas.drawCircle(mCenterX, mCenterY, backgroundRadius, mBackgroundPaint)
            
            // 绘制底座边框 (用户设置颜色或自动计算)
            mStrokePaint.apply {
                color = baseStrokeColor
                alpha = if (hasUserStrokeColor) {
                    (castedData.borderOpacity * 255).toInt()
                } else {
                    (bgAlpha * 0.3f).toInt()
                }
                strokeWidth = if (hasUserStrokeColor) dpToPx(castedData.strokeWidth) else dpToPx(1.5f)
                style = Paint.Style.STROKE
            }
            if (castedData.strokeWidth > 0 || !hasUserStrokeColor) {
                canvas.drawCircle(mCenterX, mCenterY, backgroundRadius, mStrokePaint)
            }

            // 绘制摇杆头 (增加按下时的发光感)
            val knobColor = if (mIsTouching) 0xFF6200EE.toInt() else baseKnobColor
            mStickPaint.apply {
                color = knobColor
                alpha = knobAlpha
            }
            canvas.drawCircle(mStickX, mStickY, mStickRadius, mStickPaint)
            
            // 摇杆头高光 (保持白色，增加通透感)
            mStrokePaint.apply {
                color = Color.WHITE
                alpha = (knobAlpha * 0.4f).toInt()
                strokeWidth = dpToPx(1f)
            }
            canvas.drawCircle(mStickX, mStickY, mStickRadius * 0.8f, mStrokePaint)
        }

        // 恢复旋转
        if (castedData.rotation != 0f) {
            canvas.restore()
        }
    }

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        // 将父视图坐标转换为本地坐标
        val childRect = android.graphics.Rect()
        getHitRect(childRect)
        val localX = x - childRect.left
        val localY = y - childRect.top
        
        // 检查触摸点是否在圆形区域内
        // 注意：虽然绘制时背景圆使用75%半径（backgroundRadius），但触摸区域使用100%半径（mRadius）
        // 这是为了提供更大的触摸区域，提升用户体验。onTouchEvent中也使用mRadius进行检查。
        val dx = localX - mCenterX
        val dy = localY - mCenterY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        
        return distance <= mRadius
    }

    // ==================== ControlView 接口方法 ====================

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        // 如果已经在跟踪一个触摸点，拒绝新的
        if (mIsTouching) {
            return false
        }

        // 检查触摸点是否在圆形区域内
        val dx = x - mCenterX
        val dy = y - mCenterY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // 只响应圆形区域内的触摸
        if (distance > mRadius) {
            return false
        }

        // 记录触摸点
        mActivePointerId = pointerId

        handleMove(x, y)
        mIsTouching = true
        triggerVibration(true)
        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (!mIsTouching || mActivePointerId != pointerId) {
            return
        }
        handleMove(x, y)
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId == mActivePointerId && mIsTouching) {
            mActivePointerId = -1
            handleRelease()
            mIsTouching = false
            triggerVibration(false)
        }
    }

    override fun cancelAllTouches() {
        mActivePointerId = -1

        // 强制调用 handleRelease 并重置所有状态
        if (mIsTouching || mCurrentDirection != DIR_NONE) {
            handleRelease()
        }

        mIsTouching = false
        mCurrentDirection = DIR_NONE
        resetStick()
        triggerVibration(false)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 如果还在触摸状态，执行释放逻辑
        if (mIsTouching) {
            handleRelease()
        }

        // 重置所有状态
        mIsTouching = false
        mActivePointerId = -1
        mCurrentDirection = DIR_NONE
        resetStick()
    }

    /**
     * 获取方向对应的角度（用于绘制指示线）
     */
    private fun getAngleForDirection(direction: Int): Float {
        return when (direction) {
            DIR_RIGHT -> 0f
            DIR_UP_RIGHT -> 45f
            DIR_UP -> 90f
            DIR_UP_LEFT -> 135f
            DIR_LEFT -> 180f
            DIR_DOWN_LEFT -> 225f
            DIR_DOWN -> 270f
            DIR_DOWN_RIGHT -> 315f
            else -> -1f
        }
    }

    private fun handleMove(touchX: Float, touchY: Float) {
        // 输入方向使用原始触摸坐标（不受旋转影响）
        val dx = touchX - mCenterX
        val dy = touchY - mCenterY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // 计算视觉位置：如果画布有旋转，需要对触摸坐标做反向旋转
        // 这样在旋转画布上绘制时，摇杆圆心能准确出现在手指位置
        var visDx = dx
        var visDy = dy
        if (castedData.rotation != 0f) {
            val rad = Math.toRadians(-castedData.rotation.toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            visDx = dx * cos - dy * sin
            visDy = dx * sin + dy * cos
        }

        // 计算摇杆位置（限制在背景圆范围内，与视觉边界一致）
        val maxDistance = mRadius * STICK_BACKGROUND_SIZE
        if (distance > maxDistance) {
            // 触摸点超出摇杆圆，限制在边缘
            val ratio = maxDistance / distance
            mStickX = mCenterX + visDx * ratio
            mStickY = mCenterY + visDy * ratio
        } else {
            // 触摸点在摇杆圆内，摇杆小圆点跟随触摸点（提供视觉反馈）
            // 注意：即使在死区内，小圆点也会移动，但不会触发输入事件（由后续的死区检测处理）
            mStickX = mCenterX + visDx
            mStickY = mCenterY + visDy
        }


        // 根据模式发送不同的输入事件
        when (castedData.mode) {
            ControlData.Joystick.Mode.MOUSE -> {
                // 直接判断是否在死区外（有效区域），而不是通过方向变化判断
                val inActiveZone = (distance >= mRadius * DEADZONE_PERCENT)

                // 检测攻击状态变化：进入/离开死区时启动/停止攻击
                if (inActiveZone) {
                    sendMouseClickDown()
                } else {
                    sendMouseClickUp()
                }

                // 更新方向（用于其他逻辑，如UI显示）
                val newDirection = calculateDirection(dx, dy, distance)
                mCurrentDirection = newDirection

                // 发送鼠标绝对移动（只有当摇杆位置变化时才移动）
                sendMousePos(dx, dy, distance)
            }
            ControlData.Joystick.Mode.GAMEPAD -> {
                // SDL控制器模式：发送模拟摇杆输入
                sendSDLStick(dx, dy, distance, maxDistance)
            }
            ControlData.Joystick.Mode.KEYBOARD -> {
                // 键盘模式：沿用原有输入路径，只将视觉位置改为离散方向
                val newDirection = calculateDirection(dx, dy, distance)
                updateKeyboardStickPosition(newDirection)
                if (newDirection != mCurrentDirection) {
                    releaseDirection(mCurrentDirection)
                    pressDirection(newDirection)
                    mCurrentDirection = newDirection
                }
            }
        }

        invalidate()
    }

    private fun handleRelease() {
        resetStick()

        // 根据模式执行不同的释放操作
        when (castedData.mode) {
            ControlData.Joystick.Mode.MOUSE -> {
                // 鼠标模式
                sendMouseClickUp()
                mCurrentDirection = DIR_NONE
            }
            ControlData.Joystick.Mode.KEYBOARD -> {
                // 键盘模式：释放按键和组合键
                releaseDirection(mCurrentDirection)
                mCurrentDirection = DIR_NONE
            }
            ControlData.Joystick.Mode.GAMEPAD -> {
                // SDL控制器模式：摇杆回中
                if (castedData.isRightStick) {
                    mInputBridge.sendXboxRightStick(0.0f, 0.0f)
                } else {
                    mInputBridge.sendXboxLeftStick(0.0f, 0.0f)
                }
                mCurrentDirection = DIR_NONE
            }
        }

        invalidate()
    }

    private fun resetStick() {
        mStickX = mCenterX
        mStickY = mCenterY
    }

    private fun updateKeyboardStickPosition(direction: Int) {
        if (direction == DIR_NONE) {
            resetStick()
            return
        }

        val angle = Math.toRadians(getAngleForDirection(direction).toDouble())
        val snapDistance = mRadius * STICK_BACKGROUND_SIZE
        val screenDx = cos(angle).toFloat() * snapDistance
        val screenDy = -sin(angle).toFloat() * snapDistance

        var localDx = screenDx
        var localDy = screenDy
        if (castedData.rotation != 0f) {
            val rotation = Math.toRadians(-castedData.rotation.toDouble())
            val cosRotation = cos(rotation).toFloat()
            val sinRotation = sin(rotation).toFloat()
            localDx = screenDx * cosRotation - screenDy * sinRotation
            localDy = screenDx * sinRotation + screenDy * cosRotation
        }

        mStickX = mCenterX + localDx
        mStickY = mCenterY + localDy
    }

    /**
     * 计算摇杆方向（8方向）
     * 优化算法：使用角度映射表确保方向准确
     */
    private fun calculateDirection(dx: Float, dy: Float, distance: Float): Int {
        // 死区检测 - 小范围内不触发方向
        if (distance < mRadius * DEADZONE_KEYBOARD_PERCENT) {
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

        // 发送方向键
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

    /**
     * 释放方向对应的按键
     */
    private fun releaseDirection(direction: Int) {
        if (direction == DIR_NONE) return
        
        // 释放方向键
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

    private fun sendMouseClickDown() {
        mInputBridge.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, true)
    }

    private fun sendMouseClickUp() {
        mInputBridge.sendMouseButton(ControlData.KeyCode.MOUSE_LEFT, false)
    }

    /**
     * 发送虚拟鼠标移动事件（用于右摇杆鼠标移动模式）
     *
     * 功能：
     * - 摇杆静止不动时 → 鼠标不移动
     * - 摇杆位置变化时 → 鼠标跟随移动
     * - 使用绝对位置计算，类似 VirtualTouchPad
     */
    private fun sendMousePos(dx: Float, dy: Float, distance: Float) {
        // 死区检测：在死区内时，不移动鼠标
        val deadzone: Float = mRadius * DEADZONE_PERCENT
        if (distance < deadzone) {
            return
        }

        // 获取设置管理器以读取鼠标速度
        val settingsManager = SettingsAccess
        val mouseMoveRatio = settingsManager.mouseRightStickSpeed.toFloat() / 100f

        // 计算绝对鼠标位置（基于屏幕中心 + 摇杆偏移）
        var onScreenMouseX: Float = (mScreenWidth / 2) + (dx * mouseMoveRatio)
        var onScreenMouseY: Float = (mScreenHeight / 2) + (dy * mouseMoveRatio)

        // 计算用户设置的 range 边界（从中心扩展模式）
        var minRangeX = (0.5f - settingsManager.mouseRightStickRangeLeft / 2) * mScreenWidth
        var maxRangeX = (0.5f + settingsManager.mouseRightStickRangeRight / 2) * mScreenWidth
        var minRangeY = (0.5f - settingsManager.mouseRightStickRangeTop / 2) * mScreenHeight
        var maxRangeY = (0.5f + settingsManager.mouseRightStickRangeBottom / 2) * mScreenHeight

        // 验证范围有效性
        if (minRangeX >= maxRangeX || minRangeY >= maxRangeY) {
            minRangeX = mScreenWidth * 0.5f
            maxRangeX = mScreenWidth * 0.5f
            minRangeY = mScreenHeight * 0.5f
            maxRangeY = mScreenHeight * 0.5f
        }

        // 限制到用户设置的范围
        onScreenMouseX = Math.clamp(onScreenMouseX, minRangeX, maxRangeX)
        onScreenMouseY = Math.clamp(onScreenMouseY, minRangeY, maxRangeY)

        // 限制到屏幕边界
        onScreenMouseX = Math.clamp(onScreenMouseX, 0f, mScreenWidth.toFloat() - 1)
        onScreenMouseY = Math.clamp(onScreenMouseY, 0f, mScreenHeight.toFloat() - 1)

        // 发送绝对鼠标位置
        mInputBridge.sendMousePosition(onScreenMouseX, onScreenMouseY)
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

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
