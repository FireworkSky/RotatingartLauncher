package com.app.ralaunch.feature.controls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.app.ralaunch.core.logging.AppLog
import android.view.View
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.textures.TextureLoader
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟D-Pad控件View
 * 3x3网格布局，包含4个方向按钮（上、右、下、左）
 * 对角位置按下时会同时触发两个相邻的方向键
 */
class VirtualDPad(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualDPad"

        // D-Pad方向索引（仅4个主方向）
        private const val DIR_UP = 0
        private const val DIR_RIGHT = 1
        private const val DIR_DOWN = 2
        private const val DIR_LEFT = 3
    }

    // 震动管理器
    private val vibrationManager: VibrationManagerServiceV1? by lazy {
        try {
            KoinJavaComponent.get(VibrationManagerServiceV1::class.java)
        } catch (e: Exception) {
            AppLog.w(TAG, "VibrationManagerServiceV1 not available: ${e.message}")
            null
        }
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.DPad
        get() = controlData as ControlData.DPad

    // 纹理相关
    private var textureLoader: TextureLoader? = null
    private var assetsDir: File? = null

    override fun setPackAssetsDir(dir: File?) {
        assetsDir = dir
        if (dir != null && textureLoader == null) {
            textureLoader = TextureLoader.getInstance(context)
        }
        invalidate()
    }

    // 绘制相关
    private lateinit var backgroundPaint: Paint
    private lateinit var strokePaint: Paint
    private lateinit var buttonPaint: Paint
    private lateinit var buttonPressedPaint: Paint
    private lateinit var buttonStrokePaint: Paint
    private val paintRect: RectF = RectF()
    private val cornerPath: android.graphics.Path = android.graphics.Path()

    // 按钮状态 - 4个方向
    private val buttonPressed = BooleanArray(4) { false }
    private var activePointerId = -1

    init {
        initPaints()
    }

    private fun initPaints() {
        val opacity = controlData.opacity
        val borderOpacity = controlData.borderOpacity

        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            style = Paint.Style.FILL
            alpha = (opacity * 255).toInt()
        }

        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(controlData.strokeWidth)
            alpha = (borderOpacity * 255).toInt()
        }

        buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            style = Paint.Style.FILL
            alpha = (opacity * 255).toInt()
        }

        buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = castedData.activeColor
            style = Paint.Style.FILL
            alpha = (opacity * 255).toInt().coerceAtLeast(100)
        }

        buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(controlData.strokeWidth)
            alpha = (borderOpacity * 255).toInt()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paintRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    // ==================== ControlView 接口方法 ====================

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        return x >= 0 && x <= width && y >= 0 && y <= height
    }

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        if (activePointerId != -1) return false
        if (!isTouchInBounds(x, y)) return false

        activePointerId = pointerId
        handleTouchAtPosition(x, y)
        vibrationManager?.vibrateOneShot(50, 30)
        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != activePointerId) return
        handleTouchAtPosition(x, y)
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId != activePointerId) return
        activePointerId = -1
        releaseAllButtons()
    }

    override fun cancelAllTouches() {
        if (activePointerId != -1) {
            activePointerId = -1
            releaseAllButtons()
        }
    }

    private fun handleTouchAtPosition(x: Float, y: Float) {
        // 确定触摸在3x3网格中的哪个单元格
        val cellWidth = width / 3f
        val cellHeight = height / 3f
        val col = min(2, max(0, (x / cellWidth).toInt()))
        val row = min(2, max(0, (y / cellHeight).toInt()))

        // 映射到方向（对角方向会激活两个方向键）
        val activeDirections = getCellDirections(row, col)

        // 更新按钮状态
        updateButtonStates(activeDirections)

        invalidate()
    }

    private fun getCellDirections(row: Int, col: Int): Set<Int> {
        return when {
            row == 0 && col == 0 -> setOf(DIR_UP, DIR_LEFT)     // 左上
            row == 0 && col == 1 -> setOf(DIR_UP)                // 上
            row == 0 && col == 2 -> setOf(DIR_UP, DIR_RIGHT)     // 右上
            row == 1 && col == 0 -> setOf(DIR_LEFT)              // 左
            row == 1 && col == 1 -> emptySet()                   // 中心，无方向
            row == 1 && col == 2 -> setOf(DIR_RIGHT)             // 右
            row == 2 && col == 0 -> setOf(DIR_DOWN, DIR_LEFT)    // 左下
            row == 2 && col == 1 -> setOf(DIR_DOWN)              // 下
            row == 2 && col == 2 -> setOf(DIR_DOWN, DIR_RIGHT)   // 右下
            else -> emptySet()
        }
    }

    private fun updateButtonStates(activeDirections: Set<Int>) {
        for (i in buttonPressed.indices) {
            val shouldBePressed = i in activeDirections
            if (buttonPressed[i] != shouldBePressed) {
                buttonPressed[i] = shouldBePressed

                if (shouldBePressed) {
                    sendKeyDown(i)
                    vibrationManager?.vibrateOneShot(30, 20)
                } else {
                    sendKeyUp(i)
                }
            }
        }
    }

    private fun releaseAllButtons() {
        for (i in buttonPressed.indices) {
            if (buttonPressed[i]) {
                buttonPressed[i] = false
                sendKeyUp(i)
            }
        }
        invalidate()
    }

    /**
     * 根据方向索引获取对应的按键码
     */
    private fun getKeycode(direction: Int): ControlData.KeyCode {
        return when (direction) {
            DIR_UP -> castedData.upKeycode
            DIR_RIGHT -> castedData.rightKeycode
            DIR_DOWN -> castedData.downKeycode
            DIR_LEFT -> castedData.leftKeycode
            else -> ControlData.KeyCode.UNKNOWN
        }
    }

    private fun sendKeyDown(direction: Int) {
        val keycode = getKeycode(direction)
        if (keycode == ControlData.KeyCode.UNKNOWN) return

        when (keycode.type) {
            ControlData.KeyType.KEYBOARD -> {
                mInputBridge.sendKey(keycode, true)
            }
            ControlData.KeyType.MOUSE -> {
                val centerX = this.x + width / 2f
                val centerY = this.y + height / 2f
                mInputBridge.sendMouseButton(keycode, true, centerX, centerY)
            }
            ControlData.KeyType.GAMEPAD -> {
                mInputBridge.sendXboxButton(keycode, true)
            }
            else -> {}
        }
    }

    private fun sendKeyUp(direction: Int) {
        val keycode = getKeycode(direction)
        if (keycode == ControlData.KeyCode.UNKNOWN) return

        when (keycode.type) {
            ControlData.KeyType.KEYBOARD -> {
                mInputBridge.sendKey(keycode, false)
            }
            ControlData.KeyType.MOUSE -> {
                val centerX = this.x + width / 2f
                val centerY = this.y + height / 2f
                mInputBridge.sendMouseButton(keycode, false, centerX, centerY)
            }
            ControlData.KeyType.GAMEPAD -> {
                mInputBridge.sendXboxButton(keycode, false)
            }
            else -> {}
        }
    }

    // ==================== 绘制 ====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // 应用旋转
        if (controlData.rotation != 0f) {
            canvas.save()
            canvas.rotate(controlData.rotation, centerX, centerY)
        }

        val cornerRadius = dpToPx(controlData.cornerRadius)

        // 绘制3x3网格的9个单元格
        val cellWidth = width / 3f
        val cellHeight = height / 3f

        for (row in 0..2) {
            for (col in 0..2) {
                val left = col * cellWidth
                val top = row * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight

                // 判断该单元格是否应该高亮
                val directions = getCellDirections(row, col)
                val isPressed = directions.isNotEmpty() && directions.all { buttonPressed[it] }

                val btnPaint = if (isPressed) buttonPressedPaint else buttonPaint

                paintRect.set(left, top, right, bottom)

                // 角落单元格使用Path绘制，只圆角化外侧角
                when {
                    row == 0 && col == 0 -> { // 左上角
                        cornerPath.reset()
                        cornerPath.moveTo(left + cornerRadius, top)
                        cornerPath.lineTo(right, top)
                        cornerPath.lineTo(right, bottom)
                        cornerPath.lineTo(left, bottom)
                        cornerPath.lineTo(left, top + cornerRadius)
                        cornerPath.arcTo(left, top, left + cornerRadius * 2, top + cornerRadius * 2, 180f, 90f, false)
                        cornerPath.close()
                        canvas.drawPath(cornerPath, btnPaint)
                    }
                    row == 0 && col == 2 -> { // 右上角
                        cornerPath.reset()
                        cornerPath.moveTo(left, top)
                        cornerPath.lineTo(right - cornerRadius, top)
                        cornerPath.arcTo(right - cornerRadius * 2, top, right, top + cornerRadius * 2, 270f, 90f, false)
                        cornerPath.lineTo(right, bottom)
                        cornerPath.lineTo(left, bottom)
                        cornerPath.close()
                        canvas.drawPath(cornerPath, btnPaint)
                    }
                    row == 2 && col == 0 -> { // 左下角
                        cornerPath.reset()
                        cornerPath.moveTo(left, top)
                        cornerPath.lineTo(right, top)
                        cornerPath.lineTo(right, bottom)
                        cornerPath.lineTo(left + cornerRadius, bottom)
                        cornerPath.arcTo(left, bottom - cornerRadius * 2, left + cornerRadius * 2, bottom, 90f, 90f, false)
                        cornerPath.lineTo(left, top)
                        cornerPath.close()
                        canvas.drawPath(cornerPath, btnPaint)
                    }
                    row == 2 && col == 2 -> { // 右下角
                        cornerPath.reset()
                        cornerPath.moveTo(left, top)
                        cornerPath.lineTo(right, top)
                        cornerPath.lineTo(right, bottom - cornerRadius)
                        cornerPath.arcTo(right - cornerRadius * 2, bottom - cornerRadius * 2, right, bottom, 0f, 90f, false)
                        cornerPath.lineTo(left, bottom)
                        cornerPath.close()
                        canvas.drawPath(cornerPath, btnPaint)
                    }
                    else -> { // 其他单元格：矩形
                        canvas.drawRect(paintRect, btnPaint)
                    }
                }
            }
        }

        // 绘制内部网格线
        if (controlData.strokeWidth > 0) {
            // 垂直分隔线
            canvas.drawLine(cellWidth, 0f, cellWidth, height.toFloat(), buttonStrokePaint)
            canvas.drawLine(cellWidth * 2, 0f, cellWidth * 2, height.toFloat(), buttonStrokePaint)

            // 水平分隔线
            canvas.drawLine(0f, cellHeight, width.toFloat(), cellHeight, buttonStrokePaint)
            canvas.drawLine(0f, cellHeight * 2, width.toFloat(), cellHeight * 2, buttonStrokePaint)

            // 外边框（带圆角）
            val halfStroke = dpToPx(controlData.strokeWidth) / 2f
            paintRect.set(
                halfStroke,
                halfStroke,
                width.toFloat() - halfStroke,
                height.toFloat() - halfStroke
            )
            canvas.drawRoundRect(paintRect, cornerRadius, cornerRadius, strokePaint)
        }

        if (controlData.rotation != 0f) {
            canvas.restore()
        }
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}
