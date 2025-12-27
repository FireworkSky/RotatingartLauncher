package com.app.ralaunch.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.bridges.ControlInputBridge
import com.app.ralaunch.controls.configs.ControlConfig
import com.app.ralaunch.controls.configs.ControlData
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
     * 获取当前配置
     */
    var config: ControlConfig? = null
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
        // 在硬件加速的 Activity 上，硬件加速层可以进一步提升性能
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * 设置 SDLSurface 引用，用于转发触摸事件
     */
    fun setSDLSurface(sdlSurface: View?) {
        mSDLSurface = sdlSurface

        // 初始化触摸分发管理器的屏幕尺寸
        if (sdlSurface != null) {
            val metrics = resources.displayMetrics
        }
    }

    /**
     * 重写 dispatchTouchEvent 确保 SDLSurface 能收到所有触摸事件
     * 这解决了虚拟控件消费事件后 SDLSurface 收不到多点触控的问题
     *
     * 事件处理顺序（使用规则系统）：
     * 1. 通过 TouchDispatchManager 处理触摸事件
     * 2. 让虚拟控件处理（会通过规则系统判断是否允许处理）
     * 3. 转发给 SDLSurface（SDL 层会根据规则系统过滤）
     *
     * 规则系统确保：
     * - 虚拟鼠标与虚拟控件互不干扰
     * - 虚拟摇杆与虚拟按钮基于优先级处理
     * - 游戏原生触摸只接收未被虚拟控件占用的触摸点
     */
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // 2. 先让虚拟控件处理事件
        // 虚拟控件会通过 TouchDispatchManager 请求处理触摸点
        val handled = super.dispatchTouchEvent(event)

        // 3. 再转发给 SDLSurface（如果存在）
        // SDLSurface 会处理触屏转鼠标的逻辑，SDL 层会根据规则系统过滤
        if (mSDLSurface != null) {
            mSDLSurface!!.dispatchTouchEvent(event)
        }

        // 重要：始终返回 true 表示事件已被消费
        // 这样 Android 系统就不会再次把事件传递给 SDLSurface
        // 避免重复处理导致的问题（如鼠标点击需要多次才能触发）
        return true
    }

    /**
     * 加载控制布局配置, 创建并添加控制元素
     * 不做多余的布局调整，完全基于配置文件
     * @return 是否成功加载布局
     */
    fun loadLayout(config: ControlConfig?): Boolean {
        if (inputBridge == null) {
            AppLogger.error(TAG, "InputBridge not set! Call setInputBridge() first.")
            return false
        }

        this.config = config

        if (config == null) {
            AppLogger.warn(TAG, "Config is null")
            return false
        }

        // 创建虚拟控制元素
        val controlViews = config.controls
            .map { Pair(createControlView(it), it) }
            .filter { it.first != null } // 过滤掉创建失败的控件
            .toTypedArray()

        // 清除现有控件视图
        clearControls()

        if (controlViews.isEmpty()) {
            AppLogger.warn(TAG, "No visible controls were added, layout may appear empty")
            return false
        }

        // 添加新的控件视图（不修改config.controls，它已经是正确的列表）
        controlViews.forEach { (controlView, data) ->
            addControlView(controlView, data)
        }
        AppLogger.debug(TAG, "Loaded " + controlViews.size + " controls from layout: " + config.name)
        return true
    }

    /**
     * 从 ControlConfigManager 加载当前选中的控制布局
     * @return 是否成功加载布局
     */
    fun loadLayoutFromManager(): Boolean {
        val manager = RaLaunchApplication.getControlConfigManager()
        val config = manager.loadCurrentConfig()

        if (config == null) {
            AppLogger.warn(TAG, "No current config selected in manager")
            return false
        }

        return loadLayout(config)
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
     *
     * - data.x: 0-1相对值，相对于屏幕宽度
     * - data.y: 0-1相对值，相对于屏幕高度
     * - data.width: 0-1相对值，相对于屏幕高度
     * - data.height: 0-1相对值，相对于屏幕高度
     */
    private fun addControlView(controlView: ControlView?, data: ControlData) {
        val view = controlView as View

        // 设置布局参数（使用相对值转换为像素）
        val params = LayoutParams(
            widthToPx(data.width),
            heightToPx(data.height)
        )
        params.leftMargin = xToPx(data.x)
        params.topMargin = yToPx(data.y)

        view.layoutParams = params


        // 在编辑模式下添加触摸监听器
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
        // 记录按下时的初始位置（用于判断是点击还是拖动）
        val state = object {
            var downPosX = 0f
            var downPosY = 0f
            var isDragging = false
        }

        view.setOnTouchListener { v: View, event: MotionEvent ->
            if (!mModifiable) {
                // 非编辑模式，让控件自己处理触摸事件
                return@setOnTouchListener false
            }
            // 编辑模式下处理触摸事件
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mSelectedControl = controlView
                    // 记录按下时的初始位置
                    state.downPosX = event.rawX
                    state.downPosY = event.rawY
                    mLastTouchX = event.rawX
                    mLastTouchY = event.rawY
                    state.isDragging = false


                    // 高亮选中的控件
                    v.alpha = 0.5f
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mSelectedControl === controlView) {
                        val deltaX = event.rawX - mLastTouchX
                        val deltaY = event.rawY - mLastTouchY


                        // 计算从按下位置到当前位置的总移动距离
                        val totalDx = event.rawX - state.downPosX
                        val totalDy = event.rawY - state.downPosY
                        val totalDistance = sqrt(totalDx * totalDx + totalDy * totalDy)

                        // 如果总移动距离超过阈值，标记为拖动
                        if (totalDistance > DRAG_THRESHOLD) {
                            state.isDragging = true
                        }

                        if (state.isDragging) {
                            // 拖动单个控件
                            val params = v.layoutParams as LayoutParams
                            params.leftMargin += deltaX.toInt()
                            params.topMargin += deltaY.toInt()
                            v.layoutParams = params


                            // 更新数据
                            data.x = xFromPx(params.leftMargin)
                            data.y = yFromPx(params.topMargin)

                            mLastTouchX = event.rawX
                            mLastTouchY = event.rawY
                        }
                    }
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复透明度
                    v.alpha = 1.0f


                    // 清除控件的按下状态
                    if (controlView is VirtualButton) {
                        controlView.isPressedState = false
                    }


                    // 计算从按下到释放的总移动距离
                    val finalDx = event.rawX - state.downPosX
                    val finalDy = event.rawY - state.downPosY
                    val finalDistance =
                        sqrt((finalDx * finalDx + finalDy * finalDy).toDouble()).toFloat()


                    // 只有在没有拖动（移动距离小于阈值）时才打开编辑对话框
                    if (finalDistance <= DRAG_THRESHOLD && !state.isDragging) {
                        // 点击事件 - 通知监听器显示编辑对话框
                        mEditControlListener?.onEditControl(data)
                    } else {
                        // 拖动事件 - 通知控件已修改
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
        /**
         * 显示/隐藏控制布局
         */
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
        /**
         * 是否处于编辑模式
         */
        get() = mModifiable
        /**
         * 设置编辑模式
         */
        set(modifiable) {
            mModifiable = modifiable


            // 重新设置所有控件的触摸监听器
            if (this.config != null) {
                for (i in mControls.indices) {
                    val controlView = mControls[i]
                    val data = config!!.controls[i]
                    val view = controlView as View
                    setupEditModeListeners(view, controlView, data)
                }
            }
        }

    /**
     * 设置编辑控件监听器
     */
    fun setEditControlListener(listener: EditControlListener?) {
        mEditControlListener = listener
    }

    /**
     * 设置控件修改监听器
     */
    fun setOnControlChangedListener(listener: OnControlChangedListener?) {
        mOnControlChangedListener = listener
    }

    /**
     * 编辑控件监听器接口
     */
    fun interface EditControlListener {
        fun onEditControl(data: ControlData?)
    }

    /**
     * 控件修改监听器接口
     */
    fun interface OnControlChangedListener {
        fun onControlChanged()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
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

    private fun widthFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.heightPixels
    }

    private fun heightFromPx(px: Int): Float {
        return px.toFloat() / resources.displayMetrics.heightPixels
    }

    companion object {
        private const val TAG = "ControlLayout"
    }
}