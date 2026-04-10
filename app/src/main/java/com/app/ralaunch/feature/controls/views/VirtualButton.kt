package com.app.ralaunch.feature.controls.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.view.View
import com.app.ralaunch.core.common.VibrationManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.ControlSpecialActionHandler
import com.app.ralaunch.feature.controls.ControlsSharedState
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.textures.TextureLoader
import com.app.ralaunch.feature.controls.textures.TextureRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 虚拟按钮View
 * 支持普通按钮和切换按钮（Toggle）
 */
class VirtualButton(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualButton"
    }

    // 使用 Koin 延迟获取 VibrationManager
    private val vibrationManager: VibrationManager? by lazy {
        try {
            KoinJavaComponent.get(VibrationManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "VibrationManager not available: ${e.message}")
            null
        }
    }

        private fun triggerVibration(isPress: Boolean) {
            if (isPress) {
            vibrationManager?.vibrateOneShot(50, 30)
        }
                // 释放时不振动
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }
    
    private val castedData: ControlData.Button
        get() = controlData as ControlData.Button

    // 纹理相关
    private var textureLoader: TextureLoader? = null
    private var assetsDir: File? = null
    
    /** 设置控件包资源目录（用于加载纹理） */
    override fun setPackAssetsDir(dir: File?) {
        assetsDir = dir
        if (dir != null && textureLoader == null) {
            textureLoader = TextureLoader.getInstance(context)
        }
        invalidate()
    }

    // 绘制相关
    private lateinit var mBackgroundPaint: Paint
    private lateinit var mStrokePaint: Paint
    private lateinit var mTextPaint: TextPaint
    private val mRectF = RectF()
    private val mClipPath = Path()

    // 按钮状态
    private var mIsPressed = false
    private var mIsToggled = false
    private var mActivePointerId = -1 // 跟踪的触摸点 ID

    var isPressedState: Boolean
        get() = mIsPressed
        set(pressed) {
            if (mIsPressed != pressed) {
                mIsPressed = pressed
                invalidate()
            }
        }

    init {
        initPaints()
    }

    private fun initPaints() {
        if (castedData.mode == ControlData.Button.Mode.GAMEPAD) {
            mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x7D7D7D7D // 半透明灰色
                style = Paint.Style.FILL
                alpha = (castedData.opacity * 255).toInt()
            }

            mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x00000000 // 透明
                style = Paint.Style.STROKE
                strokeWidth = 0f
            }

            mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x7DFFFFFF // 半透明白色（文字）
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                alpha = (castedData.textOpacity * 255).toInt()
            }
        } else {
            // 键盘模式
            mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = castedData.bgColor
                style = Paint.Style.FILL
                alpha = (castedData.opacity * 255).toInt()
            }

            mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = castedData.strokeColor
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(castedData.strokeWidth)
                alpha = (castedData.borderOpacity * 255).toInt()
            }

            mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = -0x1
                textSize = dpToPx(16f)
                textAlign = Paint.Align.CENTER
                alpha = (castedData.textOpacity * 255).toInt()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 如果还在按下状态，执行释放逻辑
        if (mActivePointerId != -1 || mIsPressed) {
            handleRelease()
        }
        // 重置所有状态
        mActivePointerId = -1
        mIsPressed = false
    }

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        // 将父视图坐标转换为本地坐标
        val childRect = Rect()
        getHitRect(childRect)
        val localX = x - childRect.left
        val localY = y - childRect.top
        
        return isLocalTouchInBounds(localX, localY)
    }

    /**
     * 检查纹理透明度
     * 当启用纹理透明点击检测时，检查触摸点对应的纹理像素是否为透明
     * @return true 如果点击位置不透明（可点击），false 如果透明（穿透）
     */
    private fun checkTextureAlpha(localX: Float, localY: Float): Boolean {
        // 如果没有启用透明检测，直接返回 true
        if (!castedData.useTextureAlphaHitTest) return true
        
        // 检查是否有纹理
        val texturePath = castedData.texture.normal.path
        if (texturePath.isEmpty() || !castedData.texture.normal.enabled) return true
        
        // 获取纹理 Bitmap
        val loader = textureLoader ?: return true
        val assetsPath = assetsDir ?: return true
        val fullPath = File(assetsPath, texturePath).absolutePath
        val bitmap = loader.loadTexture(fullPath, width, height) ?: return true
        
        // 将触摸坐标映射到纹理坐标
        val texX = ((localX / width) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val texY = ((localY / height) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        
        // 检查像素 Alpha 值 (阈值 50，避免边缘问题)
        val pixel = bitmap.getPixel(texX, texY)
        val alpha = (pixel shr 24) and 0xFF
        return alpha > 50
    }
    
    /**
     * 检查本地坐标是否在控件形状内
     * @param localX 本地 X 坐标
     * @param localY 本地 Y 坐标
     */
    private fun isLocalTouchInBounds(localX: Float, localY: Float): Boolean {
        // 首先检查纹理透明度（如果启用）
        if (!checkTextureAlpha(localX, localY)) {
            return false
        }
        
        return when (castedData.shape) {
            ControlData.Button.Shape.CIRCLE -> {
                val centerX = width / 2f
                val centerY = height / 2f
                val radius = min(width, height) / 2f
                val dx = localX - centerX
                val dy = localY - centerY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                distance <= radius
            }
            ControlData.Button.Shape.RECTANGLE -> {
                // 使用圆角矩形路径检查触摸点
                val cornerRadius = dpToPx(castedData.cornerRadius)
                val path = Path()
                path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
                val region = Region()
                region.setPath(path, Region(0, 0, width, height))
                region.contains(localX.toInt(), localY.toInt())
            }
            ControlData.Button.Shape.POLYGON -> {
                // 检查多边形触摸区域
                if (castedData.polygonPoints.size >= 3) {
                    val path = Path()
                    val w = width.toFloat()
                    val h = height.toFloat()
                    val points = castedData.polygonPoints
                    path.moveTo(points[0].x * w, points[0].y * h)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x * w, points[i].y * h)
                    }
                    path.close()
                    val region = Region()
                    region.setPath(path, Region(0, 0, width, height))
                    region.contains(localX.toInt(), localY.toInt())
                } else {
                    // 默认矩形检查
                    localX >= 0 && localX <= width && localY >= 0 && localY <= height
                }
            }
        }
    }

    // ==================== ControlView 接口方法 ====================

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        // 如果已经在跟踪一个触摸点，拒绝新的
        if (mActivePointerId != -1) {
            return false
        }

        // 验证触摸点是否在控件的实际形状内
        if (!isLocalTouchInBounds(x, y)) {
            return false
        }

        // 记录触摸点
        mActivePointerId = pointerId
        handlePress()
        triggerVibration(true)
        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        // 按钮不需要处理移动事件
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId == mActivePointerId) {
            mActivePointerId = -1
            triggerVibration(false)
            handleRelease()
        }
    }

    override fun cancelAllTouches() {
        if (mActivePointerId != -1) {
            mActivePointerId = -1
        }
        // 强制释放按下状态
        if (mIsPressed) {
            triggerVibration(false)
            handleRelease()
        }
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

        val shape = castedData.shape
        val centerXDraw = mRectF.centerX()
        val centerYDraw = mRectF.centerY()
        val radius = min(mRectF.width(), mRectF.height()) / 2f
        val hasTexture = castedData.texture.hasAnyTexture && assetsDir != null && textureLoader != null

        // 动态计算阴影和发光效果 (适配深浅主题)
        val elevation = if (mIsPressed || mIsToggled) dpToPx(2f) else dpToPx(4f)
        val alphaMultiplier = if (mIsPressed || mIsToggled) 1.2f else 1.0f
        
        // 自动检测深浅色主题 (根据背景亮度)
        val isDarkTheme = Color.luminance(castedData.bgColor) < 0.5f
        // 如果用户设置了非透明的边框颜色，则使用用户设置的；否则自动计算
        val userStrokeColor = castedData.strokeColor
        val hasUserStrokeColor = (userStrokeColor ushr 24) > 0 // alpha > 0
        val strokeColorValue = if (hasUserStrokeColor) userStrokeColor else (if (isDarkTheme) Color.WHITE else Color.BLACK)
        // 使用用户设置的文本颜色
        val textColorValue = castedData.textColor

        // 更新裁剪路径
        mClipPath.reset()
        when (shape) {
            ControlData.Button.Shape.CIRCLE ->
                mClipPath.addCircle(centerXDraw, centerYDraw, radius, Path.Direction.CW)
            ControlData.Button.Shape.RECTANGLE -> {
                val cornerRadius = dpToPx(castedData.cornerRadius)
                mClipPath.addRoundRect(mRectF, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            ControlData.Button.Shape.POLYGON -> {
                if (castedData.polygonPoints.size >= 3) {
                    val points = castedData.polygonPoints
                    mClipPath.moveTo(
                        mRectF.left + points[0].x * mRectF.width(),
                        mRectF.top + points[0].y * mRectF.height()
                    )
                    for (i in 1 until points.size) {
                        mClipPath.lineTo(
                            mRectF.left + points[i].x * mRectF.width(),
                            mRectF.top + points[i].y * mRectF.height()
                        )
                    }
                    mClipPath.close()
                } else {
                    // 默认矩形
                    mClipPath.addRect(mRectF, Path.Direction.CW)
                }
            }
        }

        when (shape) {
            ControlData.Button.Shape.RECTANGLE -> {
                val cornerRadius = dpToPx(castedData.cornerRadius)
                if (hasTexture) {
                    TextureRenderer.renderButton(
                        canvas = canvas,
                        textureLoader = textureLoader!!,
                        assetsDir = assetsDir,
                        textureConfig = castedData.texture,
                        bounds = mRectF,
                        isPressed = mIsPressed,
                        isToggled = mIsToggled,
                        clipPath = mClipPath,
                        opacityMultiplier = castedData.opacity
                    )
                } else {
                    // 绘制具有深度感的背景
                    mBackgroundPaint.alpha = min(255, (castedData.opacity * 255 * alphaMultiplier).toInt())
                    canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint)
                }
                
                // 绘制描边 (用户设置颜色或自动计算)
                mStrokePaint.apply {
                    color = strokeColorValue
                    strokeWidth = if (hasUserStrokeColor) dpToPx(castedData.strokeWidth) else dpToPx(1f)
                    alpha = if (hasUserStrokeColor) {
                        min(255, (castedData.borderOpacity * 255 * alphaMultiplier).toInt())
                    } else {
                        min(255, (castedData.borderOpacity * 255 * 0.6f * alphaMultiplier).toInt())
                    }
                }
                if (castedData.strokeWidth > 0 || !hasUserStrokeColor) {
                    canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint)
                }
            }
            ControlData.Button.Shape.CIRCLE -> {
                if (hasTexture) {
                    TextureRenderer.renderButton(
                        canvas = canvas,
                        textureLoader = textureLoader!!,
                        assetsDir = assetsDir,
                        textureConfig = castedData.texture,
                        bounds = mRectF,
                        isPressed = mIsPressed,
                        isToggled = mIsToggled,
                        clipPath = mClipPath,
                        opacityMultiplier = castedData.opacity
                    )
                    canvas.drawCircle(centerXDraw, centerYDraw, radius, mStrokePaint)
                } else {
                    when (castedData.mode) {
                        ControlData.Button.Mode.KEYBOARD -> {
                            mBackgroundPaint.alpha = min(255, (castedData.opacity * 255 * alphaMultiplier).toInt())
                            canvas.drawCircle(centerXDraw, centerYDraw, radius, mBackgroundPaint)
                            
                            mStrokePaint.apply {
                                color = strokeColorValue
                                strokeWidth = if (hasUserStrokeColor) dpToPx(castedData.strokeWidth) else dpToPx(1f)
                                alpha = if (hasUserStrokeColor) {
                                    min(255, (castedData.borderOpacity * 255 * alphaMultiplier).toInt())
                                } else {
                                    min(255, (castedData.borderOpacity * 255 * 0.6f * alphaMultiplier).toInt())
                                }
                            }
                            if (castedData.strokeWidth > 0 || !hasUserStrokeColor) {
                                canvas.drawCircle(centerXDraw, centerYDraw, radius, mStrokePaint)
                            }
                        }
                        ControlData.Button.Mode.GAMEPAD -> {
                            val margin = 0.12f
                            val outerRadius = radius * (1.0f - margin)
                            val innerRadius = radius * (1.0f - 2.5f * margin)

                            // 绘制外圈发光感
                            val glowPaint = Paint(mBackgroundPaint).apply {
                                color = if (mIsPressed || mIsToggled) castedData.bgColor else 0x327D7D7D
                                alpha = (castedData.opacity * 255 * 0.4f).toInt()
                            }
                            canvas.drawCircle(centerXDraw, centerYDraw, outerRadius, glowPaint)

                            // 绘制内层核心
                            val corePaint = Paint(mBackgroundPaint).apply {
                                color = if (mIsPressed || mIsToggled) -0x1 else -0x828283
                                alpha = (castedData.opacity * 255).toInt()
                            }
                            canvas.drawCircle(centerXDraw, centerYDraw, innerRadius, corePaint)
                        }
                    }
                }
            }
            ControlData.Button.Shape.POLYGON -> {
                if (hasTexture) {
                    TextureRenderer.renderButton(
                        canvas = canvas,
                        textureLoader = textureLoader!!,
                        assetsDir = assetsDir,
                        textureConfig = castedData.texture,
                        bounds = mRectF,
                        isPressed = mIsPressed,
                        isToggled = mIsToggled,
                        clipPath = mClipPath,
                        opacityMultiplier = castedData.opacity
                    )
                } else {
                    // 绘制多边形背景
                    mBackgroundPaint.alpha = min(255, (castedData.opacity * 255 * alphaMultiplier).toInt())
                    canvas.drawPath(mClipPath, mBackgroundPaint)
                }
                
                // 绘制多边形边框
                mStrokePaint.apply {
                    color = strokeColorValue
                    strokeWidth = if (hasUserStrokeColor) dpToPx(castedData.strokeWidth) else dpToPx(1f)
                    alpha = if (hasUserStrokeColor) {
                        min(255, (castedData.borderOpacity * 255 * alphaMultiplier).toInt())
                    } else {
                        min(255, (castedData.borderOpacity * 255 * 0.6f * alphaMultiplier).toInt())
                    }
                }
                if (castedData.strokeWidth > 0 || !hasUserStrokeColor) {
                    canvas.drawPath(mClipPath, mStrokePaint)
                }
            }
        }

        // 当有纹理背景时，隐藏文字
        if (hasTexture) {
            // 恢复旋转
            if (castedData.rotation != 0f) {
                canvas.restore()
            }
            return
        }

        // 为特殊按键显示特殊符号，否则显示控件名称
        val displayText = when {
            castedData.keycode == ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON ->
                if (ControlsSharedState.isTouchPadRightButton) "◑" else "◐"
            else -> castedData.name
        }

        if (displayText.isNotEmpty()) {
            canvas.save()

            // 根据控件形状设置裁剪区域
            if (shape == ControlData.Button.Shape.CIRCLE) {
                canvas.clipPath(Path().apply {
                    addCircle(centerXDraw, centerYDraw, radius, Path.Direction.CW)
                })
            } else {
                val padding = dpToPx(2f)
                canvas.clipRect(padding, padding, width - padding, height - padding)
            }

            // 更新文字颜色适配深浅主题
            mTextPaint.color = textColorValue
            mTextPaint.alpha = (castedData.textOpacity * 255).toInt()

            // 计算文字大小
            when {
                castedData.mode == ControlData.Button.Mode.GAMEPAD -> {
                    mTextPaint.textSize = 20f
                    val textBounds = Rect()
                    mTextPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
                    val textAspectRatio = textBounds.width() / max(textBounds.height(), 1).toFloat()
                    mTextPaint.textSize = min(height / 2f, width / max(textAspectRatio, 1f))
                }
                castedData.keycode == ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON -> {
                    mTextPaint.textSize = dpToPx(32f)
                }
                else -> {
                    mTextPaint.textSize = dpToPx(16f)
                    val textWidth = mTextPaint.measureText(displayText)
                    val availableWidth = width - dpToPx(4f)
                    if (textWidth > availableWidth) {
                        mTextPaint.textSize = mTextPaint.textSize * (availableWidth / textWidth)
                    }
                }
            }

            val textY = height / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2)
            canvas.drawText(displayText, width / 2f, textY, mTextPaint)
            canvas.restore()
        }

        // 恢复旋转
        if (castedData.rotation != 0f) {
            canvas.restore()
        }
    }

    private fun handlePress() {
        mIsPressed = true

        if (ControlSpecialActionHandler.handlePress(context, castedData.keycode, mInputBridge)) {
            invalidate()
            return
        }

        if (castedData.isToggle) {
            // 切换按钮：切换状态
            mIsToggled = !mIsToggled
            sendInput(mIsToggled)
        } else {
            // 普通按钮：按下
            sendInput(true)
        }

        invalidate()
    }

    private fun handleRelease() {
        mIsPressed = false

        if (!castedData.isToggle) {
            // 普通按钮：释放
            sendInput(false)
        }

        // 切换按钮不在释放时发送事件
        invalidate()
    }

    private fun sendInput(isDown: Boolean) {

        when {
            castedData.keycode.type == ControlData.KeyType.KEYBOARD -> {
                // 键盘按键
                mInputBridge.sendKey(castedData.keycode, isDown)
            }
            castedData.keycode.code >= ControlData.KeyCode.XBOX_TRIGGER_RIGHT.code && castedData.keycode.code <= ControlData.KeyCode.XBOX_TRIGGER_LEFT.code -> {
                // Xbox控制器触发器 (范围: -220 到 -221)
                val triggerValue = if (isDown) 1.0f else -1.0f
                mInputBridge.sendXboxTrigger(castedData.keycode, triggerValue)
            }
            castedData.keycode.code >= ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT.code && castedData.keycode.code <= ControlData.KeyCode.XBOX_BUTTON_A.code -> {
                // Xbox控制器按钮 (范围: -200 到 -214)
                mInputBridge.sendXboxButton(castedData.keycode, isDown)
            }
            castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_UP || castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_DOWN -> {
                // 鼠标滚轮 (MOUSE_WHEEL_UP=-4, MOUSE_WHEEL_DOWN=-5)
                // 只在按下时发送滚轮事件，释放时不发送
                if (isDown) {
                    val scrollY = if (castedData.keycode == ControlData.KeyCode.MOUSE_WHEEL_UP) 1.0f else -1.0f
                    mInputBridge.sendMouseWheel(scrollY)
                }
            }
            castedData.keycode.code >= ControlData.KeyCode.MOUSE_MIDDLE.code && castedData.keycode.code <= ControlData.KeyCode.MOUSE_LEFT.code -> {
                // 鼠标按键 (范围: -1 到 -3)
                // 计算按钮中心点的屏幕坐标
                val location = IntArray(2)
                getLocationOnScreen(location)
                val centerX = location[0] + width / 2.0f
                val centerY = location[1] + height / 2.0f

                mInputBridge.sendMouseButton(castedData.keycode, isDown, centerX, centerY)
            }
            // SPECIAL_KEYBOARD (-100) 不在这里处理，在handlePress中特殊处理
        }
    }

    /**
     * 重置切换按钮状态
     */
    fun resetToggle() {
        if (mIsToggled) {
            mIsToggled = false
            sendInput(false)
            invalidate()
        }
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}
