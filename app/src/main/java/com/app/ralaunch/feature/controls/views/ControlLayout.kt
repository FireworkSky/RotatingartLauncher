package com.app.ralaunch.feature.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.controls.TouchPointerTracker
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.packs.ControlLayout as PackControlLayout
import com.app.ralaunch.core.common.util.AppLogger
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 虚拟控制布局管理器
 * 负责管理所有虚拟控制元素的布局和显示
 *
 * 重要：触摸事件优先由控件处理，未处理的触摸事件会转发给 SDLSurface
 *
 * 触摸点跟踪：ControlLayout 集中管理所有触摸点的归属
 * - 每个触摸点 ID 只会分配给一个控件
 * - 未被任何控件接受的触摸点会转发给 SDLSurface
 *
 * - 设计图基准：2560x1080px
 * - density 会在 Activity.onCreate() 中动态调整
 * - JSON 中的 px 值会自动通过 density 适配到不同屏幕
 * - 无需手动进行尺寸转换，系统会自动处理
 */
class ControlLayout : FrameLayout {
    // SDLSurface 引用，用于转发触摸事件
    private var mSDLSurface: View? = null

    private var mControls: MutableList<ControlView> = ArrayList()
    var inputBridge: ControlInputBridge? = null

    /**
     * 触摸点 ID 到控件的映射
     * 用于集中管理哪些触摸点被哪些控件占用
     */
    private val mPointerToControl: MutableMap<Int, ControlView> = HashMap()

    /**
     * 获取当前布局
     */
    var currentLayout: PackControlLayout? = null
        private set
    
    /**
     * 当前控件包的资源目录（用于加载纹理）
     */
    private var currentAssetsDir: File? = null
    
    private var mVisible = true
    private var mModifiable = false // 是否可编辑模式
    private var mSelectedControl: ControlView? = null // 当前选中的控件
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f // 上次触摸位置
    private var mEditControlListener: EditControlListener? = null // 编辑监听器
    private var mOnControlChangedListener: OnControlChangedListener? = null // 控件修改监听器

    // ===== 拖拽吸附辅助线系统 =====
    companion object SnapGuide {
        private const val TAG = "ControlLayout"
        private const val GRID_SIZE = 50
        private const val SNAP_THRESHOLD = 12
    }

    /** 当前活跃的吸附参考线（屏幕像素坐标） */
    private val mActiveSnapLinesX = mutableListOf<Float>() // 垂直辅助线
    private val mActiveSnapLinesY = mutableListOf<Float>() // 水平辅助线
    private var mIsDraggingAny = false // 是否有控件正在被拖拽

    /** 拖拽开始时缓存的其他控件吸附目标 */
    private var mSnapTargetsX = intArrayOf()
    private var mSnapTargetsY = intArrayOf()

    private val mSnapLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC00E5FF.toInt() // 青色，高对比度
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val mGridPaint = Paint().apply {
        color = 0x28FFFFFF
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val mCenterAxisPaint = Paint().apply {
        color = 0x44FFFFFF
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 拖拽时绘制网格和辅助线（在子 View 下方）
        if (mIsDraggingAny && mModifiable) {
            drawGrid(canvas)
        }
        super.dispatchDraw(canvas)
        // 拖拽时绘制吸附参考线（在子 View 上方）
        if (mIsDraggingAny && mModifiable) {
            drawSnapGuides(canvas)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 网格
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, mGridPaint)
            x += GRID_SIZE
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, mGridPaint)
            y += GRID_SIZE
        }

        // 中心十字
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawLine(cx, 0f, cx, h, mCenterAxisPaint)
        canvas.drawLine(0f, cy, w, cy, mCenterAxisPaint)
    }

    private fun drawSnapGuides(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (x in mActiveSnapLinesX) {
            canvas.drawLine(x, 0f, x, h, mSnapLinePaint)
        }
        for (y in mActiveSnapLinesY) {
            canvas.drawLine(0f, y, w, y, mSnapLinePaint)
        }
    }

    /**
     * 缓存除被拖拽控件外的所有控件吸附目标（左/中/右，上/中/下）
     */
    private fun cacheSnapTargets(excludeView: View) {
        val xTargets = mutableListOf<Int>()
        val yTargets = mutableListOf<Int>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === excludeView || child.visibility != VISIBLE) continue
            val p = child.layoutParams as LayoutParams
            val left = p.leftMargin
            val top = p.topMargin
            val right = left + child.width
            val bottom = top + child.height
            val cx = left + child.width / 2
            val cy = top + child.height / 2
            xTargets.addAll(listOf(left, cx, right))
            yTargets.addAll(listOf(top, cy, bottom))
        }
        mSnapTargetsX = xTargets.toIntArray()
        mSnapTargetsY = yTargets.toIntArray()
    }

    /**
     * 对给定位置执行吸附计算，返回吸附后的值和活跃的参考线
     */
    private fun computeSnap(
        rawLeft: Int, rawTop: Int, viewWidth: Int, viewHeight: Int
    ): Pair<IntArray, Pair<List<Float>, List<Float>>> {
        var snapLeft = rawLeft
        var snapTop = rawTop
        val linesX = mutableListOf<Float>()
        val linesY = mutableListOf<Float>()

        val rawRight = rawLeft + viewWidth
        val rawCx = rawLeft + viewWidth / 2
        val rawBottom = rawTop + viewHeight
        val rawCy = rawTop + viewHeight / 2

        val screenCx = resources.displayMetrics.widthPixels / 2
        val screenCy = resources.displayMetrics.heightPixels / 2

        // --- X 轴吸附 ---
        var bestDx = SNAP_THRESHOLD + 1
        var bestSnapX = rawLeft
        var bestLineX = -1f

        // 网格吸附（左边缘）
        val gridSnapLeft = Math.round(rawLeft.toFloat() / GRID_SIZE) * GRID_SIZE
        val dxGrid = abs(rawLeft - gridSnapLeft)
        if (dxGrid < bestDx) { bestDx = dxGrid; bestSnapX = gridSnapLeft; bestLineX = gridSnapLeft.toFloat() }

        // 屏幕中心吸附（控件中心）
        val dxCenter = abs(rawCx - screenCx)
        if (dxCenter < bestDx) { bestDx = dxCenter; bestSnapX = screenCx - viewWidth / 2; bestLineX = screenCx.toFloat() }

        // 其他控件吸附
        for (target in mSnapTargetsX) {
            // 左边缘对齐
            val dLeft = abs(rawLeft - target)
            if (dLeft < bestDx) { bestDx = dLeft; bestSnapX = target; bestLineX = target.toFloat() }
            // 右边缘对齐
            val dRight = abs(rawRight - target)
            if (dRight < bestDx) { bestDx = dRight; bestSnapX = target - viewWidth; bestLineX = target.toFloat() }
            // 中心对齐
            val dCx = abs(rawCx - target)
            if (dCx < bestDx) { bestDx = dCx; bestSnapX = target - viewWidth / 2; bestLineX = target.toFloat() }
        }

        if (bestDx <= SNAP_THRESHOLD) {
            snapLeft = bestSnapX
            if (bestLineX >= 0f) linesX.add(bestLineX)
        }

        // --- Y 轴吸附 ---
        var bestDy = SNAP_THRESHOLD + 1
        var bestSnapY = rawTop
        var bestLineY = -1f

        // 网格吸附（上边缘）
        val gridSnapTop = Math.round(rawTop.toFloat() / GRID_SIZE) * GRID_SIZE
        val dyGrid = abs(rawTop - gridSnapTop)
        if (dyGrid < bestDy) { bestDy = dyGrid; bestSnapY = gridSnapTop; bestLineY = gridSnapTop.toFloat() }

        // 屏幕中心吸附（控件中心）
        val dyCenter = abs(rawCy - screenCy)
        if (dyCenter < bestDy) { bestDy = dyCenter; bestSnapY = screenCy - viewHeight / 2; bestLineY = screenCy.toFloat() }

        // 其他控件吸附
        for (target in mSnapTargetsY) {
            val dTop = abs(rawTop - target)
            if (dTop < bestDy) { bestDy = dTop; bestSnapY = target; bestLineY = target.toFloat() }
            val dBottom = abs(rawBottom - target)
            if (dBottom < bestDy) { bestDy = dBottom; bestSnapY = target - viewHeight; bestLineY = target.toFloat() }
            val dCy = abs(rawCy - target)
            if (dCy < bestDy) { bestDy = dCy; bestSnapY = target - viewHeight / 2; bestLineY = target.toFloat() }
        }

        if (bestDy <= SNAP_THRESHOLD) {
            snapTop = bestSnapY
            if (bestLineY >= 0f) linesY.add(bestLineY)
        }

        return intArrayOf(snapLeft, snapTop) to (linesX to linesY)
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        setWillNotDraw(false)

        // 禁用子View裁剪，让控件的绘制效果（如摇杆方向线）完整显示
        clipChildren = false
        clipToPadding = false

        // 启用硬件加速层，以支持 RippleDrawable 等需要硬件加速的动画
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // 不设置 isClickable - ControlLayout 不应该消费触摸事件
        // 它只是一个容器，用于转发事件给子控件和 SDLSurface
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /**
     * 设置 SDLSurface 引用，用于转发触摸事件
     */
    fun setSDLSurface(sdlSurface: View?) {
        mSDLSurface = sdlSurface
    }

    /**
     * 拦截所有触摸事件
     * ControlLayout 完全接管触摸事件的分发，手动将事件路由到子控件
     */
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        // 编辑模式下，使用默认的拦截行为
        if (mModifiable) {
            return super.onInterceptTouchEvent(event)
        }
        // 非编辑模式下，拦截所有触摸事件，由 onTouchEvent 手动分发
        return true
    }

    /**
     * 处理所有触摸事件
     * 手动将事件分发给虚拟控件，未处理的事件转发给 SDLSurface
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        // 编辑模式下，使用默认处理
        if (mModifiable) {
            return super.onTouchEvent(event)
        }

        return handleTouchEvent(event)
    }

    /**
     * 核心触摸事件处理逻辑
     *
     * - DOWN/POINTER_DOWN: 检查控件是否接受触摸点，如果接受则记录映射
     * - MOVE: 根据映射将移动事件分发给对应控件
     * - UP/POINTER_UP: 释放控件的触摸点并清除映射
     * - 未被控件处理的触摸点转发给 SDLSurface
     */
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        // 检查是否是真实鼠标事件（而非触摸事件）
        // 真实鼠标事件应该直接传递给 SDLSurface，不被虚拟控件拦截
        val source = event.source
        val isMouseEvent = source == android.view.InputDevice.SOURCE_MOUSE ||
                source == (android.view.InputDevice.SOURCE_MOUSE or android.view.InputDevice.SOURCE_TOUCHSCREEN) ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        if (isMouseEvent) {
            return mSDLSurface?.dispatchTouchEvent(event) ?: false
        }

        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        return when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                handlePointerDown(event, actionIndex, pointerId)
            MotionEvent.ACTION_MOVE ->
                handlePointerMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                handlePointerUp(event, pointerId)
            MotionEvent.ACTION_CANCEL ->
                handleCancel(event)
            else -> {
                // 对于其他事件，转发给 SDLSurface
                mSDLSurface?.dispatchTouchEvent(event)
                true
            }
        }
    }

    /**
     * 处理触摸点按下事件
     * 尝试将触摸点分配给控件，如果没有控件接受则转发给 SDL
     */
    private fun handlePointerDown(event: MotionEvent, actionIndex: Int, pointerId: Int): Boolean {
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)

        AppLogger.debug(TAG, "handlePointerDown: pointerId=$pointerId x=$x y=$y")

        // 从后往前遍历控件（后添加的在上层），找到第一个接受触摸的控件
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            val controlView = child as? ControlView ?: continue
            if (child.visibility != VISIBLE) continue

            // 获取控件边界并检查触摸点是否在内
            val childRect = android.graphics.Rect()
            child.getHitRect(childRect)
            if (!childRect.contains(x.toInt(), y.toInt())) continue

            // 转换为本地坐标并尝试让控件接受触摸
            val localX = x - childRect.left
            val localY = y - childRect.top

            if (controlView.tryAcquireTouch(pointerId, localX, localY)) {
                AppLogger.debug(TAG, "  Control ${controlView.javaClass.simpleName} accepted pointer $pointerId")
                val wasEmpty = mPointerToControl.isEmpty()
                mPointerToControl[pointerId] = controlView

                // 通知控件正在被使用
                if (wasEmpty) {
                    mOnControlChangedListener?.onControlInUse(true)
                }

                if (!controlView.controlData.isPassThrough) {
                    TouchPointerTracker.consumePointer(pointerId)
                }
                return true
            }
        }

        // 没有控件接受，转发给 SDLSurface
        AppLogger.debug(TAG, "  No control accepted pointer $pointerId, forwarding to SDL")
        mSDLSurface?.dispatchTouchEvent(event)
        return true
    }

    /**
     * 处理触摸点移动事件
     * 将移动事件分发给拥有对应触摸点的控件
     */
    private fun handlePointerMove(event: MotionEvent): Boolean {
        // 遍历所有指针，分发给对应的控件
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            mPointerToControl[pointerId]?.let { controlView ->
                val view = controlView as View
                val childRect = android.graphics.Rect()
                view.getHitRect(childRect)
                controlView.handleTouchMove(
                    pointerId,
                    event.getX(i) - childRect.left,
                    event.getY(i) - childRect.top
                )
            }
        }

        // 转发给 SDL（SDL 会过滤已消费的指针）
        mSDLSurface?.dispatchTouchEvent(event)
        return true
    }

    /**
     * 处理触摸点抬起事件
     * 释放控件的触摸点并清除映射
     */
    private fun handlePointerUp(event: MotionEvent, pointerId: Int): Boolean {
        AppLogger.debug(TAG, "handlePointerUp: pointerId=$pointerId")

        mPointerToControl.remove(pointerId)?.let { controlView ->
            AppLogger.debug(TAG, "  Releasing pointer $pointerId from ${controlView.javaClass.simpleName}")
            if (!controlView.controlData.isPassThrough) {
                TouchPointerTracker.releasePointer(pointerId)
            }
            controlView.releaseTouch(pointerId)
            
            // 通知控件不再被使用
            if (mPointerToControl.isEmpty()) {
                mOnControlChangedListener?.onControlInUse(false)
            }
        }

        mSDLSurface?.dispatchTouchEvent(event)
        return true
    }

    /**
     * 处理取消事件
     * 通知所有控件取消并清除所有映射
     */
    private fun handleCancel(event: MotionEvent): Boolean {
        AppLogger.debug(TAG, "handleCancel: clearing ${mPointerToControl.size} pointers")

        val hadPointers = mPointerToControl.isNotEmpty()
        mPointerToControl.forEach { (pointerId, controlView) ->
            if (!controlView.controlData.isPassThrough) {
                TouchPointerTracker.releasePointer(pointerId)
            }
            controlView.cancelAllTouches()
        }
        mPointerToControl.clear()
        
        // 通知控件不再被使用
        if (hadPointers) {
            mOnControlChangedListener?.onControlInUse(false)
        }

        mSDLSurface?.dispatchTouchEvent(event)
        return true
    }


    /**
     * 加载控制布局配置
     * @return 是否成功加载布局
     */
    fun loadLayout(layout: PackControlLayout?): Boolean {
        if (inputBridge == null) {
            AppLogger.error(TAG, "InputBridge not set! Call setInputBridge() first.")
            return false
        }

        this.currentLayout = layout

        if (layout == null) {
            AppLogger.warn(TAG, "Layout is null")
            return false
        }

        // 清除现有控件视图
        clearControls()

        val visibleControls = layout.controls

        // 创建并添加虚拟控制元素
        val addedCount = visibleControls.mapNotNull { data ->
            createControlView(data)?.also { addControlView(it, data) }
        }.size

        if (addedCount == 0) {
            AppLogger.warn(TAG, "No visible controls were added, layout may appear empty")
            return false
        }

        AppLogger.debug(TAG, "Loaded $addedCount controls from layout: ${layout.name}")
        return true
    }

    /**
     * 从 ControlPackManager 加载当前选中的控制布局
     * @return 是否成功加载布局
     */
    fun loadLayoutFromPackManager(): Boolean {
        val packManager: ControlPackManager = try {
            KoinJavaComponent.get(ControlPackManager::class.java)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to get ControlPackManager: ${e.message}")
            return false
        }
        
        val packId = packManager.getSelectedPackId()
        val layout = packManager.getCurrentLayout()
        
        if (layout == null || packId == null) {
            AppLogger.warn(TAG, "No current layout selected in pack manager")
            return false
        }
        
        // 获取控件包的资源目录
        currentAssetsDir = packManager.getPackAssetsDir(packId)
        
        return loadLayout(layout)
    }
    
    /**
     * 设置控件包资源目录（用于加载纹理）
     */
    fun setPackAssetsDir(dir: File?) {
        currentAssetsDir = dir
        // 更新所有现有控件的资源目录
        mControls.forEach { controlView ->
            controlView.setPackAssetsDir(dir)
        }
    }

    /**
     * 创建控制View
     */
    private fun createControlView(data: ControlData?): ControlView? = when (data) {
        is ControlData.Button -> VirtualButton(context, data, inputBridge!!)
        is ControlData.Joystick -> VirtualJoystick(context, data, inputBridge!!)
        is ControlData.TouchPad -> VirtualTouchPad(context, data, inputBridge!!)
        is ControlData.MouseWheel -> VirtualMouseWheel(context, data, inputBridge!!)
        is ControlData.Text -> VirtualText(context, data, inputBridge!!)
        is ControlData.RadialMenu -> VirtualRadialMenu(context, data, inputBridge!!)
        is ControlData.DPad -> VirtualDPad(context, data, inputBridge!!)
        else -> {
            AppLogger.warn(TAG, "Unknown control type")
            null
        }
    }

    /**
     * 添加控制View到布局
     */
    private fun addControlView(controlView: ControlView?, data: ControlData) {
        val view = controlView as View
        
        // 设置控件包资源目录（用于加载纹理）
        controlView.setPackAssetsDir(currentAssetsDir)

        // 不设置 isClickable - 让控件通过 onTouchEvent 自行决定是否处理触摸
        // 设置为 clickable 会导致 View 捕获整个触摸序列，即使触摸在形状外
        view.isFocusable = false
        view.isFocusableInTouchMode = false

        // 强制确保没有 OnTouchListener（调试用）
        view.setOnTouchListener(null)
        AppLogger.debug(TAG, "addControlView: ${data.name} - removed any existing OnTouchListener")

        val params = LayoutParams(
            widthToPx(data.width),
            heightToPx(data.height)
        )
        params.leftMargin = xToPx(data.x)
        params.topMargin = yToPx(data.y)

        view.layoutParams = params

        setupEditModeListeners(view, controlView, data)

        addView(view)
        mControls.add(controlView)
    }

    /**
     * 设置编辑模式的触摸监听器
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupEditModeListeners(view: View, controlView: ControlView?, data: ControlData) {
        val DRAG_THRESHOLD = 15f
        val state = object {
            var downPosX = 0f
            var downPosY = 0f
            var isDragging = false
            var dragNotified = false  // 拖拽开始通知只发一次
            var accumDx = 0f         // 累计水平位移
            var accumDy = 0f         // 累计垂直位移
        }

        val touchListener = View.OnTouchListener { v: View, event: MotionEvent ->
            if (!mModifiable) {
                return@OnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mSelectedControl = controlView
                    state.downPosX = event.rawX
                    state.downPosY = event.rawY
                    mLastTouchX = event.rawX
                    mLastTouchY = event.rawY
                    state.isDragging = false
                    state.dragNotified = false
                    state.accumDx = 0f
                    state.accumDy = 0f

                    if (controlView is VirtualButton) {
                        controlView.isPressedState = true
                    }
                    return@OnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mSelectedControl === controlView) {
                        val deltaX = event.rawX - mLastTouchX
                        val deltaY = event.rawY - mLastTouchY

                        val totalDx = event.rawX - state.downPosX
                        val totalDy = event.rawY - state.downPosY
                        val totalDistance = sqrt(totalDx * totalDx + totalDy * totalDy)

                        if (totalDistance > DRAG_THRESHOLD) {
                            state.isDragging = true
                        }

                        if (state.isDragging) {
                            // 拖拽开始时的一次性操作（不重复执行）
                            if (!state.dragNotified) {
                                state.dragNotified = true
                                if (controlView is VirtualButton) {
                                    controlView.isPressedState = false
                                }
                                v.alpha = 0.6f
                                mIsDraggingAny = true
                                cacheSnapTargets(v)
                                mOnControlChangedListener?.onControlDragging(true)
                                invalidate() // 显示网格
                            }
                            
                            // 累计原始位移
                            state.accumDx += deltaX
                            state.accumDy += deltaY

                            // 实时吸附计算
                            val params = v.layoutParams as LayoutParams
                            val rawLeft = params.leftMargin + state.accumDx.toInt()
                            val rawTop = params.topMargin + state.accumDy.toInt()
                            val (snapped, guides) = computeSnap(rawLeft, rawTop, v.width, v.height)

                            // 应用吸附后的 translation
                            v.translationX = (snapped[0] - params.leftMargin).toFloat()
                            v.translationY = (snapped[1] - params.topMargin).toFloat()

                            // 更新辅助线并刷新绘制
                            mActiveSnapLinesX.clear()
                            mActiveSnapLinesX.addAll(guides.first)
                            mActiveSnapLinesY.clear()
                            mActiveSnapLinesY.addAll(guides.second)
                            invalidate()

                            mLastTouchX = event.rawX
                            mLastTouchY = event.rawY
                        }
                    }
                    return@OnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (controlView is VirtualButton) {
                        controlView.isPressedState = false
                    }

                    if (state.isDragging && state.dragNotified) {
                        // 拖拽结束：translation 已经是吸附后的位置，直接提交
                        val params = v.layoutParams as LayoutParams
                        val newLeft = params.leftMargin + v.translationX.toInt()
                        val newTop = params.topMargin + v.translationY.toInt()

                        v.translationX = 0f
                        v.translationY = 0f
                        params.leftMargin = newLeft
                        params.topMargin = newTop
                        v.layoutParams = params

                        data.x = xFromPx(newLeft)
                        data.y = yFromPx(newTop)

                        mOnControlChangedListener?.onControlDragging(false)
                        mOnControlChangedListener?.onControlChanged()
                    } else if (!state.isDragging) {
                        mEditControlListener?.onEditControl(data)
                    }

                    // 恢复正常状态
                    v.alpha = 1.0f
                    v.translationX = 0f
                    v.translationY = 0f
                    mIsDraggingAny = false
                    mActiveSnapLinesX.clear()
                    mActiveSnapLinesY.clear()
                    invalidate() // 清除网格和辅助线
                    mSelectedControl = null
                    return@OnTouchListener true
                }
            }
            false
        }

        // 只在编辑模式下设置 OnTouchListener
        // 在非编辑模式下，OnTouchListener 会干扰正常的 onTouchEvent 分发
        if (mModifiable) {
            view.setOnTouchListener(touchListener)
        } else {
            view.setOnTouchListener(null)
        }
    }

    /**
     * 清除所有控制元素
     */
    fun clearControls() {
        // 清除所有触摸点映射并通知 SDL
        mPointerToControl.forEach { (pointerId, controlView) ->
            if (!controlView.controlData.isPassThrough) {
                TouchPointerTracker.releasePointer(pointerId)
            }
            controlView.cancelAllTouches()
        }
        mPointerToControl.clear()

        removeAllViews()
        mControls.clear()
    }

    var isControlsVisible: Boolean
        get() = mVisible
        set(visible) {
            mVisible = visible
            visibility = if (visible) VISIBLE else GONE
        }

    /**
     * 切换控制布局显示状态
     */
    fun toggleControlsVisible() {
        isControlsVisible = !mVisible
    }

    /**
     * 重置所有切换按钮状态
     */
    fun resetAllToggles() {
        mControls.filterIsInstance<VirtualButton>().forEach { it.resetToggle() }
    }

    var isModifiable: Boolean
        get() = mModifiable
        set(modifiable) {
            mModifiable = modifiable
            currentLayout?.let { layout ->
                mControls.forEachIndexed { i, controlView ->
                    setupEditModeListeners(controlView as View, controlView, layout.controls[i])
                }
            }
        }

    fun setEditControlListener(listener: EditControlListener?) {
        mEditControlListener = listener
    }

    fun setOnControlChangedListener(listener: OnControlChangedListener?) {
        mOnControlChangedListener = listener
    }

    fun interface EditControlListener {
        fun onEditControl(data: ControlData?)
    }

    interface OnControlChangedListener {
        fun onControlChanged()
        fun onControlDragging(isDragging: Boolean) {}
        /** 控件是否正在被使用（有触摸点在控件上） */
        fun onControlInUse(inUse: Boolean) {}
    }

    private fun xToPx(value: Float) = (value * resources.displayMetrics.widthPixels).toInt()
    private fun yToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun widthToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun heightToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun xFromPx(px: Int) = px.toFloat() / resources.displayMetrics.widthPixels
    private fun yFromPx(px: Int) = px.toFloat() / resources.displayMetrics.heightPixels
}
