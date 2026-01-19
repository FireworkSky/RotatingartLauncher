package com.app.ralaunch.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.TouchPointerTracker
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout as PackControlLayout
import com.app.ralaunch.utils.AppLogger
import java.io.File
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
                mPointerToControl[pointerId] = controlView

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

        mPointerToControl.forEach { (pointerId, controlView) ->
            if (!controlView.controlData.isPassThrough) {
                TouchPointerTracker.releasePointer(pointerId)
            }
            controlView.cancelAllTouches()
        }
        mPointerToControl.clear()

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

        // 创建并添加虚拟控制元素
        val addedCount = layout.controls.mapNotNull { data ->
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
        val packManager = RaLaunchApplication.getControlPackManager()
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
        }

        val touchListener = View.OnTouchListener { v: View, event: MotionEvent ->
            if (!mModifiable) {
                // 在非编辑模式下，不应该有这个 listener，但为了安全，返回 false
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

                    v.alpha = 0.5f
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
                            val params = v.layoutParams as LayoutParams
                            params.leftMargin += deltaX.toInt()
                            params.topMargin += deltaY.toInt()
                            v.layoutParams = params

                            data.x = xFromPx(params.leftMargin)
                            data.y = yFromPx(params.topMargin)

                            mLastTouchX = event.rawX
                            mLastTouchY = event.rawY
                        }
                    }
                    return@OnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f

                    if (controlView is VirtualButton) {
                        controlView.isPressedState = false
                    }

                    val finalDx = event.rawX - state.downPosX
                    val finalDy = event.rawY - state.downPosY
                    val finalDistance =
                        sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()

                    if (finalDistance <= DRAG_THRESHOLD && !state.isDragging) {
                        mEditControlListener?.onEditControl(data)
                    } else {
                        mOnControlChangedListener?.onControlChanged()
                    }

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

    fun interface OnControlChangedListener {
        fun onControlChanged()
    }

    private fun xToPx(value: Float) = (value * resources.displayMetrics.widthPixels).toInt()
    private fun yToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun widthToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun heightToPx(value: Float) = (value * resources.displayMetrics.heightPixels).toInt()
    private fun xFromPx(px: Int) = px.toFloat() / resources.displayMetrics.widthPixels
    private fun yFromPx(px: Int) = px.toFloat() / resources.displayMetrics.heightPixels

    companion object {
        private const val TAG = "ControlLayout"
    }
}
