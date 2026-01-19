package com.app.ralaunch.controls.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟D-Pad控件View
 * 3x3网格布局，包含4个方向按钮（上、右、下、左）
 * 对角位置按下时会同时触发两个相邻的方向键
 */
class VirtualDPad(
    context: Context,
    data: ControlData,
    private val mInputBridge: ControlInputBridge,
) : View(context), ControlView {

    companion object {
        // D-Pad方向索引（仅4个主方向）
        private const val DIR_UP = 0
        private const val DIR_RIGHT = 1
        private const val DIR_DOWN = 2
        private const val DIR_LEFT = 3

        private fun triggerVibration() {
            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30)
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

    // 绘制相关
    private lateinit var backgroundPaint: Paint
    private lateinit var strokePaint: Paint
    private lateinit var buttonPaint: Paint
    private lateinit var buttonPressedPaint: Paint
    private lateinit var buttonStrokePaint: Paint
    private val paintRect: RectF = RectF()
    private val cornerPath: android.graphics.Path = android.graphics.Path() // 预分配路径对象

    // 按钮状态 - 4个方向
    private val buttonPressed = BooleanArray(4) { false }
    private var activePointerId = -1 // 跟踪的触摸点 ID

    // 纹理相关
    private var packAssetsDir: File? = null

    init {
        initPaints()
    }

    private fun initPaints() {
        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.bgColor
            style = Paint.Style.FILL
            alpha = (controlData.opacity * 255).toInt()
        }

        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(controlData.strokeWidth)
            alpha = (controlData.borderOpacity * 255).toInt()
        }

        buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x7D7D7D7D // 半透明灰色
            style = Paint.Style.FILL
            alpha = (controlData.opacity * 255).toInt()
        }

        buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x828283 // 按下时更亮
            style = Paint.Style.FILL
            alpha = (controlData.opacity * 255).toInt()
        }

        buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = controlData.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(controlData.strokeWidth)
            alpha = (controlData.borderOpacity * 255).toInt()
        }
    }

    override fun setPackAssetsDir(dir: File?) {
        packAssetsDir = dir
        // TODO: 实现纹理加载
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paintRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        // x, y 是父视图坐标，需要转换为本地坐标
        val childRect = android.graphics.Rect()
        getHitRect(childRect)
        val localX = x - childRect.left
        val localY = y - childRect.top

        // D-Pad 是矩形，直接检查边界
        return localX >= 0 && localX <= width && localY >= 0 && localY <= height
    }

    // ==================== ControlView 接口方法 ====================

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        if (!controlData.isVisible || controlData.isPassThrough) return false
        if (activePointerId != -1) return false // 已经有一个触摸点

        // x, y 已经是本地坐标，直接检查边界
        if (x < 0 || x > width || y < 0 || y > height) return false

        activePointerId = pointerId
        handleTouchAtPosition(x, y)
        triggerVibration()
        return true
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != activePointerId) return
        // x, y 已经是本地坐标
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
        // x, y 已经是本地坐标

        // 确定触摸在3x3网格中的哪个单元格
        val cellWidth = width / 3f
        val cellHeight = height / 3f
        val col = min(2, max(0, (x / cellWidth).toInt()))
        val row = min(2, max(0, (y / cellHeight).toInt()))

        // 映射到方向（对角方向会激活两个方向键）
        val activeDirections = getCellDirections(row, col)

        // 更新按钮状态
        val anyChanged = updateButtonStates(activeDirections)

        invalidate()
    }

    private fun getCellDirections(row: Int, col: Int): Set<Int> {
        return when {
            row == 0 && col == 0 -> setOf(DIR_UP, DIR_LEFT) // 左上
            row == 0 && col == 1 -> setOf(DIR_UP) // 上
            row == 0 && col == 2 -> setOf(DIR_UP, DIR_RIGHT) // 右上
            row == 1 && col == 0 -> setOf(DIR_LEFT) // 左
            row == 1 && col == 1 -> emptySet() // 中心，无方向
            row == 1 && col == 2 -> setOf(DIR_RIGHT) // 右
            row == 2 && col == 0 -> setOf(DIR_DOWN, DIR_LEFT) // 左下
            row == 2 && col == 1 -> setOf(DIR_DOWN) // 下
            row == 2 && col == 2 -> setOf(DIR_DOWN, DIR_RIGHT) // 右下
            else -> emptySet()
        }
    }

    private fun updateButtonStates(activeDirections: Set<Int>): Boolean {
        var changed = false

        for (i in buttonPressed.indices) {
            val shouldBePressed = i in activeDirections
            if (buttonPressed[i] != shouldBePressed) {
                buttonPressed[i] = shouldBePressed
                changed = true

                // 发送按键事件
                if (shouldBePressed) {
                    sendKeyDown(i)
                } else {
                    sendKeyUp(i)
                }
            }
        }

        return changed
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

    private fun sendKeyDown(direction: Int) {
        val keycode = castedData.dpadKeys.getOrNull(direction) ?: return
        if (keycode == ControlData.KeyCode.UNKNOWN) return

        // 直接根据keycode类型发送到相应的输入桥接
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
            else -> {} // SPECIAL类型不处理
        }
    }

    private fun sendKeyUp(direction: Int) {
        val keycode = castedData.dpadKeys.getOrNull(direction) ?: return
        if (keycode == ControlData.KeyCode.UNKNOWN) return

        // 直接根据keycode类型发送到相应的输入桥接
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
            else -> {} // SPECIAL类型不处理
        }
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

        val cornerRadius = dpToPx(controlData.cornerRadius)

        // 绘制3x3网格的9个单元格（包括中心）
        val cellWidth = width / 3f
        val cellHeight = height / 3f

        for (row in 0..2) {
            for (col in 0..2) {
                val left = col * cellWidth
                val top = row * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight

                // 判断该单元格是否应该高亮（对角单元格需要两个方向都按下才高亮）
                val directions = getCellDirections(row, col)
                val isPressed = directions.isNotEmpty() && directions.all { buttonPressed[it] }

                // 绘制所有单元格（包括中心）- 使用预分配的Paint对象
                val btnPaint = if (isPressed) buttonPressedPaint else buttonPaint

                // 根据位置决定是否使用圆角
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

        // 绘制内部网格线（不包括外边框）
        if (controlData.strokeWidth > 0) {
            // 绘制两条垂直分隔线（在 col=1 和 col=2 的左边界）
            canvas.drawLine(cellWidth, 0f, cellWidth, height.toFloat(), buttonStrokePaint)
            canvas.drawLine(cellWidth * 2, 0f, cellWidth * 2, height.toFloat(), buttonStrokePaint)

            // 绘制两条水平分隔线（在 row=1 和 row=2 的上边界）
            canvas.drawLine(0f, cellHeight, width.toFloat(), cellHeight, buttonStrokePaint)
            canvas.drawLine(0f, cellHeight * 2, width.toFloat(), cellHeight * 2, buttonStrokePaint)

            // 绘制外边框（带圆角）
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
