package com.app.ralaunch.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout as PackControlLayout
import com.app.ralaunch.utils.AppLogger
import kotlin.math.sqrt

/**
 * 虚拟控制布局管理器
 * 负责管理所有虚拟控制元素的布局和显示
 *
 * 重要：所有触摸事件都会转发给 SDLSurface，确保游戏能收到完整的多点触控输入
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
     * 获取当前布局
     */
    var currentLayout: PackControlLayout? = null
        private set
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
    }

    /**
     * 设置 SDLSurface 引用，用于转发触摸事件
     */
    fun setSDLSurface(sdlSurface: View?) {
        mSDLSurface = sdlSurface
    }

    /**
     * 重写 dispatchTouchEvent 确保 SDLSurface 能收到所有触摸事件
     */
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val handled = super.dispatchTouchEvent(event)

        if (mSDLSurface != null) {
            mSDLSurface!!.dispatchTouchEvent(event)
        }

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

        // 创建虚拟控制元素
        val controlViews = layout.controls
            .map { Pair(createControlView(it), it) }
            .filter { it.first != null }
            .toTypedArray()

        // 清除现有控件视图
        clearControls()

        if (controlViews.isEmpty()) {
            AppLogger.warn(TAG, "No visible controls were added, layout may appear empty")
            return false
        }

        // 添加新的控件视图
        controlViews.forEach { (controlView, data) ->
            addControlView(controlView, data)
        }
        AppLogger.debug(TAG, "Loaded " + controlViews.size + " controls from layout: " + layout.name)
        return true
    }

    /**
     * 从 ControlPackManager 加载当前选中的控制布局
     * @return 是否成功加载布局
     */
    fun loadLayoutFromPackManager(): Boolean {
        val packManager = RaLaunchApplication.getControlPackManager()
        val layout = packManager.getCurrentLayout()
        
        if (layout == null) {
            AppLogger.warn(TAG, "No current layout selected in pack manager")
            return false
        }
        
        return loadLayout(layout)
    }

    /**
     * 创建控制View
     */
    private fun createControlView(data: ControlData?): ControlView? {
        when (data) {
            is ControlData.Button -> {
                return VirtualButton(context, data, inputBridge!!)
            }
            is ControlData.Joystick -> {
                return VirtualJoystick(context, data, inputBridge!!)
            }
            is ControlData.TouchPad -> {
                return VirtualTouchPad(context, data, inputBridge!!)
            }
            is ControlData.Text -> {
                return VirtualText(context, data, inputBridge!!)
            }
            else -> {
                AppLogger.warn(TAG, "Unknown control type")
                return null
            }
        }
    }

    /**
     * 添加控制View到布局
     */
    private fun addControlView(controlView: ControlView?, data: ControlData) {
        val view = controlView as View

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

        view.setOnTouchListener { v: View, event: MotionEvent ->
            if (!mModifiable) {
                return@setOnTouchListener false
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
                    return@setOnTouchListener true
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
                    return@setOnTouchListener true
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
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    /**
     * 清除所有控制元素
     */
    fun clearControls() {
        removeAllViews()
        mControls.clear()
    }

    var isControlsVisible: Boolean
        get() = mVisible
        set(visible) {
            mVisible = visible
            setVisibility(if (visible) VISIBLE else GONE)
        }

    /**
     * 切换控制布局显示状态
     */
    fun toggleControlsVisible() {
        this.isControlsVisible = !mVisible
    }

    /**
     * 重置所有切换按钮状态
     */
    fun resetAllToggles() {
        for (control in mControls) {
            if (control is VirtualButton) {
                control.resetToggle()
            }
        }
    }

    var isModifiable: Boolean
        get() = mModifiable
        set(modifiable) {
            mModifiable = modifiable

            if (this.currentLayout != null) {
                for (i in mControls.indices) {
                    val controlView = mControls[i]
                    val data = currentLayout!!.controls[i]
                    val view = controlView as View
                    setupEditModeListeners(view, controlView, data)
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

    private fun xToPx(value: Float): Int {
        return (value * resources.displayMetrics.widthPixels).toInt()
    }

    private fun yToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun widthToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun heightToPx(value: Float): Int {
        return (value * resources.displayMetrics.heightPixels).toInt()
    }

    private fun xFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.widthPixels
    }

    private fun yFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.heightPixels
    }

    companion object {
        private const val TAG = "ControlLayout"
    }
}
