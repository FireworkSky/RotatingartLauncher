package com.app.ralaunch.controls.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.manager.VibrationManager
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 轮盘菜单控件
 * 
 * 点击后展开为圆形菜单，拖动选择方向触发对应按键
 */
class VirtualRadialMenu(
    context: Context?,
    data: ControlData,
    private val mInputBridge: ControlInputBridge
) : View(context), ControlView {

    companion object {
        private const val TAG = "VirtualRadialMenu"
    }

    // 震动管理器
    private val vibrationManager: VibrationManager? by lazy {
        try {
            KoinJavaComponent.get(VibrationManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "VibrationManager not available: ${e.message}")
            null
        }
    }

    override var controlData: ControlData = data
        set(value) {
            field = value
            // 编辑器预览展开状态变化时，自动切换展开/收起
            val newData = value as? ControlData.RadialMenu
            if (newData != null) {
                if (newData.editorPreviewExpanded && mExpandProgress == 0f) {
                    mExpandProgress = 1f
                    mIsExpanded = true
                } else if (!newData.editorPreviewExpanded && mExpandProgress == 1f && mActivePointerId < 0) {
                    mExpandProgress = 0f
                    mIsExpanded = false
                }
            }
            initPaints()
            invalidate()
        }

    private val castedData: ControlData.RadialMenu
        get() = controlData as ControlData.RadialMenu

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
    private lateinit var mBackgroundPaint: Paint
    private lateinit var mSectorPaint: Paint
    private lateinit var mSelectedPaint: Paint
    private lateinit var mSelectedGlowPaint: Paint
    private lateinit var mDividerPaint: Paint
    private lateinit var mTextPaint: TextPaint
    private lateinit var mStrokePaint: Paint
    private lateinit var mCenterIconPaint: Paint
    private lateinit var mEditorSelectedPaint: Paint       // 编辑器选中扇区画笔
    private lateinit var mEditorSelectedGlowPaint: Paint   // 编辑器选中扇区发光画笔
    private lateinit var mSectorIndexPaint: TextPaint       // 扇区序号画笔
    private val mRectF = RectF()
    private val mSectorPath = Path()

    // 状态
    private var mIsExpanded = false
    private var mExpandProgress = 0f // 0.0 = 收起, 1.0 = 展开
    private var mSelectedSector = -1 // 当前选中的扇区 (-1 = 无)
    private var mPrevSelectedSector = -1 // 上一个选中的扇区（用于过渡动画）
    private var mActivePointerId = -1
    private var mTouchStartX = 0f
    private var mTouchStartY = 0f
    private var mCurrentTouchX = 0f
    private var mCurrentTouchY = 0f

    // 图标缓存
    private val mIconCache = mutableMapOf<String, Bitmap?>()

    // 动画
    private var mExpandAnimator: ValueAnimator? = null
    private var mSectorHighlightProgress = 0f // 选中扇区高亮过渡
    private var mSectorHighlightAnimator: ValueAnimator? = null

    init {
        initPaints()
    }

    private fun initPaints() {
        val data = castedData

        // 背景画笔
        mBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(
                (data.opacity * 255).toInt(),
                Color.red(data.bgColor),
                Color.green(data.bgColor),
                Color.blue(data.bgColor)
            )
        }

        // 扇区画笔
        mSectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(
                (data.opacity * 0.8f * 255).toInt(),
                Color.red(data.bgColor),
                Color.green(data.bgColor),
                Color.blue(data.bgColor)
            )
        }

        // 选中扇区画笔
        mSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = data.selectedColor
        }

        // 选中扇区发光画笔
        mSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60,
                Color.red(data.selectedColor),
                Color.green(data.selectedColor),
                Color.blue(data.selectedColor)
            )
        }

        // 分隔线画笔
        mDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = data.dividerColor
            strokeWidth = dpToPx(1f)
        }

        // 边框画笔
        mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(
                (data.borderOpacity * 255).toInt(),
                Color.red(data.strokeColor),
                Color.green(data.strokeColor),
                Color.blue(data.strokeColor)
            )
            strokeWidth = dpToPx(1.5f)
        }

        // 文字画笔
        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = data.textColor
            alpha = (data.textOpacity * 255).toInt()
            textSize = dpToPx(14f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // 中心图标画笔（绘制 ◎ 标志）
        mCenterIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = data.textColor
            alpha = (data.textOpacity * 200).toInt()
            strokeWidth = dpToPx(1.5f)
        }

        // 编辑器选中扇区画笔（蓝色高亮，区别于游戏中的白色选中）
        mEditorSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(100, 66, 165, 245) // Material Blue 400, 40% 不透明度
        }
        mEditorSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(50, 66, 165, 245) // 外层发光
        }
        // 扇区序号画笔
        mSectorIndexPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = dpToPx(8f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
    }

    /**
     * 加载扇区图标（带缓存）
     */
    private fun loadSectorIcon(iconPath: String, size: Int): Bitmap? {
        if (iconPath.isEmpty()) return null
        val cacheKey = "${iconPath}_${size}"
        return mIconCache.getOrPut(cacheKey) {
            val loader = textureLoader ?: return@getOrPut null
            val dir = assetsDir ?: return@getOrPut null
            val fullPath = File(dir, iconPath).absolutePath
            loader.loadTexture(fullPath, size, size)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f

        // 编辑器预览模式下强制展开
        val shouldShowExpanded = mExpandProgress > 0f || castedData.editorPreviewExpanded

        if (shouldShowExpanded) {
            // 预览模式直接以100%展开绘制
            val effectiveProgress = if (castedData.editorPreviewExpanded && mExpandProgress == 0f) 1f else mExpandProgress
            val savedProgress = mExpandProgress
            mExpandProgress = effectiveProgress
            drawExpandedState(canvas, centerX, centerY, baseRadius)
            mExpandProgress = savedProgress
        } else {
            // 绘制收起状态
            drawCollapsedState(canvas, centerX, centerY, baseRadius)
        }
    }

    private fun drawCollapsedState(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val data = castedData

        // 绘制渐变背景圆
        val bgGradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.argb((data.opacity * 255).toInt(), Color.red(data.bgColor), Color.green(data.bgColor), Color.blue(data.bgColor)),
                Color.argb((data.opacity * 200).toInt(), Color.red(data.bgColor), Color.green(data.bgColor), Color.blue(data.bgColor))
            ),
            floatArrayOf(0.3f, 1f),
            Shader.TileMode.CLAMP
        )
        mBackgroundPaint.shader = bgGradient
        canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint)
        mBackgroundPaint.shader = null

        // 绘制扇区暗示线（收起时提示这是轮盘）
        val hintAlpha = (data.opacity * 80).toInt()
        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(hintAlpha, 255, 255, 255)
            strokeWidth = dpToPx(0.5f)
        }
        val hintRadius = radius * 0.6f
        val sectorCount = data.sectorCount
        for (i in 0 until sectorCount) {
            val angle = Math.toRadians((-90.0 + i * 360.0 / sectorCount))
            val x1 = centerX + (hintRadius * 0.3f * cos(angle)).toFloat()
            val y1 = centerY + (hintRadius * 0.3f * sin(angle)).toFloat()
            val x2 = centerX + (hintRadius * cos(angle)).toFloat()
            val y2 = centerY + (hintRadius * sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, hintPaint)
        }

        // 绘制边框
        if (data.borderOpacity > 0) {
            canvas.drawCircle(centerX, centerY, radius - dpToPx(1f), mStrokePaint)
        }

        // 绘制中心文本
        val text = data.name.ifEmpty { "◎" }
        mTextPaint.textSize = dpToPx(14f)
        mTextPaint.alpha = (data.textOpacity * 255).toInt()
        val textY = centerY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, mTextPaint)
    }

    private fun drawExpandedState(canvas: Canvas, centerX: Float, centerY: Float, baseRadius: Float) {
        val data = castedData
        val expandedRadius = baseRadius * data.expandedScale * mExpandProgress
        val deadZoneRadius = expandedRadius * data.deadZoneRatio
        val sectorCount = data.sectorCount
        val sectorAngle = 360f / sectorCount

        // 外圈半透明背景
        mBackgroundPaint.shader = null
        mBackgroundPaint.alpha = (data.opacity * 0.7f * 255 * mExpandProgress).toInt()
        canvas.drawCircle(centerX, centerY, expandedRadius, mBackgroundPaint)

        // 编辑器选中扇区索引
        val editorSelSector = data.editorSelectedSector
        val isEditorPreview = data.editorPreviewExpanded

        // 绘制各扇区
        for (i in 0 until sectorCount) {
            val startAngle = -90f + i * sectorAngle - sectorAngle / 2
            val isSelected = i == mSelectedSector
            val isEditorSelected = isEditorPreview && i == editorSelSector

            // 编辑器选中高亮（蓝色，优先级低于游戏中的选中高亮）
            if (isEditorSelected && !isSelected) {
                // 外层发光
                mSectorPath.reset()
                mSectorPath.moveTo(centerX, centerY)
                mRectF.set(
                    centerX - expandedRadius,
                    centerY - expandedRadius,
                    centerX + expandedRadius,
                    centerY + expandedRadius
                )
                mSectorPath.arcTo(mRectF, startAngle, sectorAngle)
                mSectorPath.close()
                canvas.drawPath(mSectorPath, mEditorSelectedGlowPaint)

                // 内层高亮
                mSectorPath.reset()
                val highlightRadius = expandedRadius * 0.97f
                mSectorPath.moveTo(centerX, centerY)
                mRectF.set(
                    centerX - highlightRadius,
                    centerY - highlightRadius,
                    centerX + highlightRadius,
                    centerY + highlightRadius
                )
                mSectorPath.arcTo(mRectF, startAngle, sectorAngle)
                mSectorPath.close()
                canvas.drawPath(mSectorPath, mEditorSelectedPaint)
            }

            // 游戏中选中高亮（带发光效果）
            if (isSelected) {
                // 外层发光
                mSectorPath.reset()
                mSectorPath.moveTo(centerX, centerY)
                mRectF.set(
                    centerX - expandedRadius,
                    centerY - expandedRadius,
                    centerX + expandedRadius,
                    centerY + expandedRadius
                )
                mSectorPath.arcTo(mRectF, startAngle, sectorAngle)
                mSectorPath.close()
                canvas.drawPath(mSectorPath, mSelectedGlowPaint)

                // 内层高亮
                mSectorPath.reset()
                val highlightRadius = expandedRadius * 0.97f
                mSectorPath.moveTo(centerX, centerY)
                mRectF.set(
                    centerX - highlightRadius,
                    centerY - highlightRadius,
                    centerX + highlightRadius,
                    centerY + highlightRadius
                )
                mSectorPath.arcTo(mRectF, startAngle, sectorAngle)
                mSectorPath.close()
                canvas.drawPath(mSectorPath, mSelectedPaint)
            }

            // 扇区分隔线
            if (data.showDividers) {
                val angleRad = Math.toRadians(startAngle.toDouble())
                val lineStartX = centerX + (deadZoneRadius * cos(angleRad)).toFloat()
                val lineStartY = centerY + (deadZoneRadius * sin(angleRad)).toFloat()
                val lineEndX = centerX + (expandedRadius * 0.95f * cos(angleRad)).toFloat()
                val lineEndY = centerY + (expandedRadius * 0.95f * sin(angleRad)).toFloat()
                mDividerPaint.alpha = (255 * mExpandProgress).toInt()
                canvas.drawLine(lineStartX, lineStartY, lineEndX, lineEndY, mDividerPaint)
            }

            // 扇区内容（图标或文本）
            if (i < data.sectors.size) {
                val sector = data.sectors[i]
                val midAngle = startAngle + sectorAngle / 2
                val midAngleRad = Math.toRadians(midAngle.toDouble())
                val labelRadius = (deadZoneRadius + expandedRadius) / 2
                val labelX = centerX + (labelRadius * cos(midAngleRad)).toFloat()
                val labelY = centerY + (labelRadius * sin(midAngleRad)).toFloat()

                // 尝试加载图标
                val iconSize = (dpToPx(20f) * mExpandProgress).toInt().coerceAtLeast(1)
                val icon = if (sector.iconPath.isNotEmpty()) loadSectorIcon(sector.iconPath, iconSize) else null

                val isHighlighted = isSelected || isEditorSelected

                if (icon != null && !icon.isRecycled) {
                    // 绘制图标
                    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = (255 * mExpandProgress).toInt()
                    }
                    canvas.drawBitmap(
                        icon,
                        labelX - icon.width / 2f,
                        labelY - icon.height / 2f - dpToPx(4f),
                        iconPaint
                    )
                    // 图标下方绘制标签
                    val label = sector.label
                    if (label.isNotEmpty()) {
                        mTextPaint.textSize = dpToPx(9f) * mExpandProgress
                        mTextPaint.alpha = (data.textOpacity * 255 * mExpandProgress).toInt()
                        if (isHighlighted) {
                            mTextPaint.typeface = Typeface.DEFAULT_BOLD
                        }
                        val textYPos = labelY + icon.height / 2f + dpToPx(2f)
                        canvas.drawText(label, labelX, textYPos, mTextPaint)
                        mTextPaint.typeface = Typeface.DEFAULT_BOLD
                    }
                } else {
                    // 纯文本模式
                    val label = sector.label.ifEmpty {
                        sector.keycode.name
                            .removePrefix("KEYBOARD_")
                            .removePrefix("MOUSE_")
                            .removePrefix("XBOX_BUTTON_")
                    }

                    val fontSize = if (isHighlighted) dpToPx(13f) else dpToPx(11f)
                    mTextPaint.textSize = fontSize * mExpandProgress
                    mTextPaint.alpha = if (isHighlighted) {
                        (255 * mExpandProgress).toInt()
                    } else {
                        (data.textOpacity * 220 * mExpandProgress).toInt()
                    }
                    mTextPaint.typeface = if (isHighlighted) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

                    val textYPos = labelY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
                    canvas.drawText(label, labelX, textYPos, mTextPaint)
                }

                // 编辑器预览模式下绘制扇区序号（靠近外圈边缘）
                if (isEditorPreview) {
                    val indexRadius = expandedRadius * 0.88f
                    val indexX = centerX + (indexRadius * cos(midAngleRad)).toFloat()
                    val indexY = centerY + (indexRadius * sin(midAngleRad)).toFloat()
                    
                    // 序号背景圆
                    val indexBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = if (isEditorSelected) {
                            Color.argb(200, 66, 165, 245) // 选中时蓝色
                        } else {
                            Color.argb(120, 0, 0, 0) // 未选中时黑色半透明
                        }
                    }
                    val indexCircleRadius = dpToPx(7f)
                    canvas.drawCircle(indexX, indexY, indexCircleRadius, indexBgPaint)
                    
                    // 序号文字
                    mSectorIndexPaint.textSize = dpToPx(8f)
                    mSectorIndexPaint.color = Color.WHITE
                    val indexTextY = indexY - (mSectorIndexPaint.descent() + mSectorIndexPaint.ascent()) / 2
                    canvas.drawText("${i + 1}", indexX, indexTextY, mSectorIndexPaint)
                }
            }
        }

        // 中心死区（渐变填充）
        val deadZoneGradient = RadialGradient(
            centerX, centerY, deadZoneRadius,
            intArrayOf(
                Color.argb((data.opacity * 255).toInt(), Color.red(data.bgColor), Color.green(data.bgColor), Color.blue(data.bgColor)),
                Color.argb((data.opacity * 220).toInt(), Color.red(data.bgColor), Color.green(data.bgColor), Color.blue(data.bgColor))
            ),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        mBackgroundPaint.shader = deadZoneGradient
        mBackgroundPaint.alpha = 255
        canvas.drawCircle(centerX, centerY, deadZoneRadius, mBackgroundPaint)
        mBackgroundPaint.shader = null

        // 中心文字 (选中扇区的标签或轮盘名)
        // 优先显示游戏选中扇区，其次编辑器选中扇区
        val displaySector = when {
            mSelectedSector >= 0 && mSelectedSector < data.sectors.size -> mSelectedSector
            isEditorPreview && editorSelSector >= 0 && editorSelSector < data.sectors.size -> editorSelSector
            else -> -1
        }
        
        if (displaySector >= 0) {
            val selectedLabel = data.sectors[displaySector].label.ifEmpty {
                data.sectors[displaySector].keycode.name
                    .removePrefix("KEYBOARD_").removePrefix("MOUSE_").removePrefix("XBOX_BUTTON_")
            }
            mTextPaint.textSize = dpToPx(11f) * mExpandProgress
            mTextPaint.alpha = (255 * mExpandProgress).toInt()
            mTextPaint.typeface = Typeface.DEFAULT_BOLD
            
            // 编辑器模式下显示 "扇区N: 标签"
            val centerLabel = if (isEditorPreview && mSelectedSector < 0) {
                "#${displaySector + 1} $selectedLabel"
            } else {
                selectedLabel
            }
            val textY = centerY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
            canvas.drawText(centerLabel, centerX, textY, mTextPaint)
        } else {
            // 未选中时显示轮盘名
            val centerText = data.name.ifEmpty { "◎" }
            mTextPaint.textSize = dpToPx(10f) * mExpandProgress
            mTextPaint.alpha = (data.textOpacity * 180 * mExpandProgress).toInt()
            mTextPaint.typeface = Typeface.DEFAULT
            val textY = centerY - (mTextPaint.descent() + mTextPaint.ascent()) / 2
            canvas.drawText(centerText, centerX, textY, mTextPaint)
        }

        // 边框
        if (data.borderOpacity > 0) {
            mStrokePaint.alpha = (data.borderOpacity * 255 * mExpandProgress).toInt()
            canvas.drawCircle(centerX, centerY, expandedRadius - dpToPx(1f), mStrokePaint)
            canvas.drawCircle(centerX, centerY, deadZoneRadius, mStrokePaint)
        }

        // 触摸位置指示（柔和光点）
        if (mActivePointerId >= 0 && mSelectedSector >= 0) {
            val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    mCurrentTouchX, mCurrentTouchY, dpToPx(12f),
                    intArrayOf(Color.argb(180, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(mCurrentTouchX, mCurrentTouchY, dpToPx(12f), indicatorPaint)
        }
    }

    // 展开/收起动画
    private fun animateExpand(expand: Boolean) {
        mExpandAnimator?.cancel()
        
        val targetProgress = if (expand) 1f else 0f
        mExpandAnimator = ValueAnimator.ofFloat(mExpandProgress, targetProgress).apply {
            duration = castedData.expandDuration.toLong()
            interpolator = if (expand) OvershootInterpolator(0.8f) else DecelerateInterpolator()
            addUpdateListener { animator ->
                mExpandProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        mIsExpanded = expand
    }

    // 计算当前选中的扇区
    private fun calculateSelectedSector(touchX: Float, touchY: Float): Int {
        val data = castedData
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f
        val expandedRadius = baseRadius * data.expandedScale
        val deadZoneRadius = expandedRadius * data.deadZoneRatio

        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt(dx * dx + dy * dy)

        // 在死区内不选中任何扇区
        if (distance < deadZoneRadius) {
            return -1
        }

        // 计算角度 (0° = 上方, 顺时针增加)
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f

        // 计算扇区索引
        val sectorAngle = 360f / data.sectorCount
        return ((angle + sectorAngle / 2) % 360 / sectorAngle).toInt()
    }

    // 触发按键
    private fun triggerSectorKey(sectorIndex: Int, pressed: Boolean) {
        if (sectorIndex < 0 || sectorIndex >= castedData.sectors.size) return
        
        val sector = castedData.sectors[sectorIndex]
        val keycode = sector.keycode
        
        if (keycode != ControlData.KeyCode.UNKNOWN) {
            if (pressed) {
                vibrationManager?.vibrateOneShot(30, 50)
            }
            
            when (keycode.type) {
                ControlData.KeyType.KEYBOARD -> {
                    mInputBridge.sendKey(keycode, pressed)
                }
                ControlData.KeyType.MOUSE -> {
                    mInputBridge.sendMouseButton(keycode, pressed, width / 2f, height / 2f)
                }
                ControlData.KeyType.GAMEPAD -> {
                    mInputBridge.sendXboxButton(keycode, pressed)
                }
                else -> {}
            }
        }
    }

    // ControlView 接口实现
    override fun isTouchInBounds(x: Float, y: Float): Boolean {
        val localX = x - left
        val localY = y - top
        val centerX = width / 2f
        val centerY = height / 2f
        
        val radius = if (mIsExpanded) {
            min(width, height) / 2f * castedData.expandedScale
        } else {
            min(width, height) / 2f
        }
        
        val dx = localX - centerX
        val dy = localY - centerY
        return sqrt(dx * dx + dy * dy) <= radius
    }

    override fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean {
        if (mActivePointerId >= 0) return false

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) / 2f
        
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)

        // 检查是否在收起状态的圆内
        if (!mIsExpanded && distance <= baseRadius) {
            mActivePointerId = pointerId
            mTouchStartX = x
            mTouchStartY = y
            mCurrentTouchX = x
            mCurrentTouchY = y
            
            // 展开轮盘
            animateExpand(true)
            return true
        }
        
        // 如果已展开，检查是否在展开范围内
        if (mIsExpanded && distance <= baseRadius * castedData.expandedScale) {
            mActivePointerId = pointerId
            mCurrentTouchX = x
            mCurrentTouchY = y
            mSelectedSector = calculateSelectedSector(x, y)
            invalidate()
            return true
        }

        return false
    }

    override fun handleTouchMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId != mActivePointerId) return
        
        mCurrentTouchX = x
        mCurrentTouchY = y
        
        val newSector = calculateSelectedSector(x, y)
        if (newSector != mSelectedSector) {
            mPrevSelectedSector = mSelectedSector
            mSelectedSector = newSector
            // 扇区变化时震动提示
            if (newSector >= 0) {
                vibrationManager?.vibrateOneShot(15, 30)
            }
        }
        
        invalidate()
    }

    override fun releaseTouch(pointerId: Int) {
        if (pointerId != mActivePointerId) return
        
        // 触发选中扇区的按键
        if (mSelectedSector >= 0) {
            triggerSectorKey(mSelectedSector, true)
            // 短暂延迟后释放按键（使用安全方式）
            val sectorToRelease = mSelectedSector
            postDelayed({
                triggerSectorKey(sectorToRelease, false)
            }, 60)
            // 选中反馈震动
            vibrationManager?.vibrateOneShot(30, 80)
        }
        
        // 收起轮盘
        animateExpand(false)
        
        mActivePointerId = -1
        mPrevSelectedSector = -1
        mSelectedSector = -1
        invalidate()
    }

    override fun cancelAllTouches() {
        if (mActivePointerId >= 0) {
            animateExpand(false)
            mActivePointerId = -1
            mSelectedSector = -1
            invalidate()
        }
    }
}
