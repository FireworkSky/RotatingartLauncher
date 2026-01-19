package com.app.ralaunch.controls.editors

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.app.ralaunch.R
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.utils.OpacityHelper
import com.google.android.material.R as MaterialR

/**
 * 统一的MD3风格编辑器设置对话框（侧边滑动弹窗）
 * 支持两种模式：
 * 1. 编辑器模式（ControlEditorActivity）：最后一项为"退出"
 * 2. 游戏模式（GameActivity）：最后一项为"退出编辑"
 */
class UnifiedEditorSettingsDialog(
    private val mContext: Context,
    private val mParent: ViewGroup,
    private val mScreenWidth: Int,
    private val mMode: DialogMode
) {
    private var mDialogLayout: ViewGroup? = null
    private var mOverlay: View? = null
    private var mAnimator: ValueAnimator? = null

    // UI元素
    private var mTvDialogTitle: TextView? = null
    private var mItemToggleEditMode: View? = null
    private var mIvToggleEditModeIcon: ImageView? = null
    private var mTvToggleEditModeText: TextView? = null
    private var mItemAddButton: View? = null
    private var mItemAddJoystick: View? = null
    private var mItemAddTouchPad: View? = null
    private var mItemAddMouseWheel: View? = null
    private var mItemAddText: View? = null
    private var mItemAddDPad: View? = null
    private val mItemAddTextGroup: View? = null
    private var mAddControlsSection: ViewGroup? = null // 添加控件区域
    private var mItemSaveLayout: View? = null // 保存布局
    private var mItemFPSDisplay: View? = null
    private var mSwitchFPSDisplay: SwitchCompat? = null
    private var mItemHideControls: View? = null // 隐藏控件
    private var mItemToggleTouch: View? = null // 切换触摸模式
    private var mSwitchToggleTouch: SwitchCompat? = null
    private var mItemExitGame: View? = null // 退出游戏

    // 编辑模式状态
    private var mIsEditModeEnabled = false

    // 监听器
    private var mListener: OnMenuItemClickListener? = null

    /**
     * 对话框模式
     */
    enum class DialogMode {
        /** 编辑器模式：独立的编辑器界面  */
        EDITOR,

        /** 游戏模式：游戏内编辑  */
        GAME;

        fun getTitle(context: Context): String {
            return context.getString(R.string.editor_game_settings)
        }
    }

    /**
     * 菜单项点击监听器
     */
    interface OnMenuItemClickListener {
        fun onToggleEditMode() // 切换编辑模式
        fun onAddButton()
        fun onAddJoystick()
        fun onAddTouchPad()
        fun onAddMouseWheel()
        fun onAddText()
        fun onAddDPad()
        fun onSaveLayout() // 保存布局
        fun onFPSDisplayChanged(enabled: Boolean) // FPS 显示选项变化
        fun onHideControls() // 隐藏控件
        fun onToggleTouchEventChanged(enabled: Boolean) // 切换触摸模式选项变化
        fun onExitGame() // 退出游戏
    }

    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?) {
        mListener = listener
    }

    /**
     * 显示对话框
     */
    fun show() {
        // 如果对话框布局不存在或已被移除，重新创建
        if (mDialogLayout == null || mDialogLayout!!.getParent() == null) {
            if (mDialogLayout == null) {
                inflateLayout()
                // 恢复编辑模式状态（在布局创建后立即更新UI）
                updateEditModeUI()
            }

            // 先添加遮罩层（底层）
            // 如果遮罩层不存在或已被移除，重新创建
            if (mOverlay == null || mOverlay!!.getParent() == null) {
                if (mOverlay != null && mOverlay!!.getParent() != null) {
                    mParent.removeView(mOverlay)
                }
                mOverlay = View(mContext)
                mOverlay!!.setBackgroundColor(Color.parseColor("#80000000"))
                mOverlay!!.setAlpha(0f)
                mOverlay!!.setClickable(true)
                mOverlay!!.setFocusable(true)
                mOverlay!!.setOnClickListener { v: View? -> hide() }

                val overlayParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                mParent.addView(mOverlay, overlayParams)
            }

            // 后添加对话框布局（上层，在遮罩层之上）- 使用固定宽度，避免占用过多空间
            val dialogWidth =
                (mContext.getResources().getDisplayMetrics().density * 320).toInt() // 320dp
            val dialogParams = FrameLayout.LayoutParams(
                dialogWidth,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            dialogParams.gravity = Gravity.END
            // 确保对话框在遮罩层之上
            mParent.addView(mDialogLayout, dialogParams)

            // 设置对话框布局的触摸监听，检测点击外部区域时关闭对话框
            mDialogLayout!!.setOnTouchListener { v: View?, event: MotionEvent? ->
                // 如果触摸事件在对话框外部（遮罩层区域），关闭对话框
                if (event!!.getAction() == MotionEvent.ACTION_DOWN) {
                    val x = event.getX()
                    val y = event.getY()
                    // 如果点击在对话框外部（左侧、上方、下方），关闭对话框
                    if (x < 0 || y < 0 || y > mDialogLayout!!.getHeight()) {
                        hide()
                        return@setOnTouchListener true
                    }
                }
                false // 让对话框内容正常处理触摸事件
            }

            // 等待布局测量完成后再执行动画
            mDialogLayout!!.post {
                // 确保编辑模式状态正确（在布局创建后）
                updateEditModeUI()
                animateShow()
            }
        } else {
            // 如果已经添加，确保遮罩层也存在
            if (mOverlay == null || mOverlay!!.getParent() == null) {
                if (mOverlay != null && mOverlay!!.getParent() != null) {
                    mParent.removeView(mOverlay)
                }
                mOverlay = View(mContext)
                mOverlay!!.setBackgroundColor(Color.parseColor("#80000000"))
                mOverlay!!.setAlpha(0f)
                mOverlay!!.setClickable(true)
                mOverlay!!.setFocusable(true)
                mOverlay!!.setOnClickListener { v: View? -> hide() }

                val overlayParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // 将遮罩层插入到对话框之前，确保遮罩层在底层
                val dialogIndex = mParent.indexOfChild(mDialogLayout)
                mParent.addView(mOverlay, dialogIndex, overlayParams)
            }
            // 直接显示动画
            animateShow()
        }
    }

    /**
     * 隐藏对话框
     */
    fun hide() {
        if (mDialogLayout != null && mDialogLayout!!.getParent() != null) {
            animateHide()
        }
    }

    val isDisplaying: Boolean
        get() = mDialogLayout != null && mDialogLayout!!.getParent() != null && mDialogLayout!!.getVisibility() == View.VISIBLE

    /**
     * 显示动画
     */
    private fun animateShow() {
        if (mAnimator != null && mAnimator!!.isRunning()) {
            mAnimator!!.cancel()
        }

        // 检查必要的视图是否存在
        if (mDialogLayout == null) {
            return
        }

        var dialogWidth = mDialogLayout!!.getWidth().toFloat()
        if (dialogWidth == 0f) {
            dialogWidth = mContext.getResources().getDisplayMetrics().density * 320 // 320dp
        }
        val finalDialogWidth = dialogWidth

        mDialogLayout!!.setVisibility(View.VISIBLE)
        mDialogLayout!!.setTranslationX(finalDialogWidth)

        // 确保遮罩层存在
        if (mOverlay != null) {
            mOverlay!!.setVisibility(View.VISIBLE)
            mOverlay!!.setAlpha(0f)
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f)
        mAnimator!!.setDuration(300)
        mAnimator!!.setInterpolator(DecelerateInterpolator())
        mAnimator!!.addUpdateListener { animation: ValueAnimator? ->
            val progress = animation!!.getAnimatedFraction()
            mDialogLayout!!.setTranslationX(finalDialogWidth * (1f - progress))
            if (mOverlay != null) {
                mOverlay!!.setAlpha(progress * 0.5f)
            }
        }
        mAnimator!!.start()
    }

    /**
     * 隐藏动画
     */
    private fun animateHide() {
        if (mAnimator != null && mAnimator!!.isRunning()) {
            mAnimator!!.cancel()
        }

        var dialogWidth = mDialogLayout!!.getWidth().toFloat()
        if (dialogWidth == 0f) {
            dialogWidth = mContext.getResources().getDisplayMetrics().density * 320 // 320dp
        }
        val finalDialogWidth = dialogWidth

        mAnimator = ValueAnimator.ofFloat(0f, 1f)
        mAnimator!!.setDuration(300)
        mAnimator!!.setInterpolator(AccelerateInterpolator())
        mAnimator!!.addUpdateListener { animation: ValueAnimator? ->
            val progress = animation!!.getAnimatedFraction()
            mDialogLayout!!.setTranslationX(finalDialogWidth * progress)
            if (mOverlay != null) {
                mOverlay!!.setAlpha(0.5f * (1f - progress))
            }
        }
        mAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (mDialogLayout != null && mDialogLayout!!.getParent() != null) {
                    mParent.removeView(mDialogLayout)
                }
                if (mOverlay != null && mOverlay!!.getParent() != null) {
                    mParent.removeView(mOverlay)
                }
                // 清理引用，确保下次打开时重新创建
                mOverlay = null
            }
        })
        mAnimator!!.start()
    }

    /**
     * 初始化布局
     */
    private fun inflateLayout() {
        val inflater = LayoutInflater.from(mContext)

        // 加载对话框布局
        mDialogLayout =
            inflater.inflate(R.layout.dialog_unified_editor_settings, mParent, false) as ViewGroup?

        // 设置背景和阴影 - 使用主题颜色，支持暗色模式
        val typedValue = TypedValue()
        mContext.getTheme()
            .resolveAttribute(MaterialR.attr.colorSurface, typedValue, true)
        val surfaceColor = typedValue.data
        // 添加 90% 不透明度 (0xE6 = 230 = 90%)
        val backgroundColorWithAlpha = (surfaceColor and 0x00FFFFFF) or -0x1a000000
        mDialogLayout!!.setBackgroundColor(backgroundColorWithAlpha)
        mDialogLayout!!.setElevation(16f)

        // 应用背景透明度（使用统一工具类）
        try {
            val dialogAlpha = OpacityHelper.getDialogAlphaFromSettings(mContext)
            mDialogLayout!!.setAlpha(dialogAlpha)
        } catch (e: Exception) {
        }

        // 绑定UI元素
        mTvDialogTitle = mDialogLayout!!.findViewById<TextView>(R.id.tv_dialog_title)
        mItemToggleEditMode = mDialogLayout!!.findViewById<View?>(R.id.item_toggle_edit_mode)
        mIvToggleEditModeIcon =
            mItemToggleEditMode!!.findViewById<ImageView?>(R.id.iv_toggle_edit_mode_icon)
        mTvToggleEditModeText =
            mItemToggleEditMode!!.findViewById<TextView?>(R.id.tv_toggle_edit_mode_text)
        mItemAddButton = mDialogLayout!!.findViewById<View>(R.id.item_add_button)
        mItemAddJoystick = mDialogLayout!!.findViewById<View>(R.id.item_add_joystick)
        mItemAddTouchPad = mDialogLayout!!.findViewById<View>(R.id.item_add_touchpad)
        mItemAddMouseWheel = mDialogLayout!!.findViewById<View>(R.id.item_add_mousewheel)
        mItemAddText = mDialogLayout!!.findViewById<View>(R.id.item_add_text)
        mItemAddDPad = mDialogLayout!!.findViewById<View>(R.id.item_add_dpad)
        mAddControlsSection = mDialogLayout!!.findViewById<ViewGroup?>(R.id.section_add_controls)
        mItemSaveLayout = mDialogLayout!!.findViewById<View?>(R.id.item_save_layout)
        mItemFPSDisplay = mDialogLayout!!.findViewById<View?>(R.id.item_fps_display)
        mSwitchFPSDisplay = mDialogLayout!!.findViewById<SwitchCompat?>(R.id.switch_fps_display)
        mItemHideControls = mDialogLayout!!.findViewById<View?>(R.id.item_hide_controls)
        mItemToggleTouch = mDialogLayout!!.findViewById<View?>(R.id.item_toggle_touch)
        mSwitchToggleTouch = mDialogLayout!!.findViewById<SwitchCompat?>(R.id.switch_toggle_touch)
        mItemExitGame = mDialogLayout!!.findViewById<View?>(R.id.item_exit_game)

        // 初始状态：编辑模式关闭，添加控件区域隐藏
        if (mAddControlsSection != null) {
            mAddControlsSection!!.setVisibility(View.GONE)
        }

        // 配置UI元素
        configureUIForMode()

        // 设置监听器
        setupListeners()
    }

    /**
     * 根据模式配置UI元素
     */
    private fun configureUIForMode() {
        // 设置标题
        mTvDialogTitle!!.setText(mMode.getTitle(mContext))

        Log.i(
            TAG,
            "configureUIForMode: mode=" + mMode + ", isEditor=" + (mMode == DialogMode.EDITOR)
        )


        if (mMode == DialogMode.EDITOR) {
            Log.i(TAG, "Hiding settings for EDITOR mode")

            // 隐藏切换编辑模式按钮（独立编辑器始终处于编辑模式）
            if (mItemToggleEditMode != null) {
                mItemToggleEditMode!!.setVisibility(View.GONE)
                Log.i(TAG, "Hidden: ToggleEditMode")
            }
            // 隐藏 FPS 显示
            if (mItemFPSDisplay != null) {
                mItemFPSDisplay!!.setVisibility(View.GONE)
                Log.i(TAG, "Hidden: FPSDisplay")
            }
            // 隐藏隐藏控件
            if (mItemHideControls != null) {
                mItemHideControls!!.setVisibility(View.GONE)
                Log.i(TAG, "Hidden: HideControls")
            }
            // 隐藏切换触摸模式
            if (mItemToggleTouch != null) {
                mItemToggleTouch!!.setVisibility(View.GONE)
                Log.i(TAG, "Hidden: ToggleTouch")
            }
            // 隐藏退出游戏
            if (mItemExitGame != null) {
                mItemExitGame!!.setVisibility(View.GONE)
                Log.i(TAG, "Hidden: ExitGame")
            }
        } else {
            Log.i(TAG, "GAME mode - showing all settings")
        }
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 关闭按钮
        mDialogLayout!!.findViewById<View?>(R.id.btn_close_settings)!!
            .setOnClickListener { v: View? ->
                hide()
            }

        // 菜单项点击
        if (mListener != null) {
            // 切换编辑模式
            if (mItemToggleEditMode != null) {
                mItemToggleEditMode!!.setOnClickListener { v: View? ->
                    // handled in onToggleEditMode
//                    mIsEditModeEnabled = !mIsEditModeEnabled
//                    updateEditModeUI()
                    mListener!!.onToggleEditMode()
                }
            }

            mItemAddButton!!.setOnClickListener { v: View? ->
                mListener!!.onAddButton()
                hide()
            }

            mItemAddJoystick!!.setOnClickListener { v: View? ->
                mListener!!.onAddJoystick()
                hide()
            }

            mItemAddTouchPad!!.setOnClickListener { v: View? ->
                mListener!!.onAddTouchPad()
                hide()
            }

            mItemAddMouseWheel!!.setOnClickListener { v: View? ->
                mListener!!.onAddMouseWheel()
                hide()
            }

            mItemAddText!!.setOnClickListener { v: View? ->
                mListener!!.onAddText()
                hide()
            }

            mItemAddDPad!!.setOnClickListener { v: View? ->
                mListener!!.onAddDPad()
                hide()
            }

            // 保存布局
            if (mItemSaveLayout != null) {
                mItemSaveLayout!!.setOnClickListener { v: View? ->
                    mListener!!.onSaveLayout()
                }
            }

            // FPS 显示开关
            if (mSwitchFPSDisplay != null) {
                // 加载当前设置
                val settingsManager =
                    SettingsManager.getInstance()
                val fpsDisplayEnabled = settingsManager.isFPSDisplayEnabled()
                mSwitchFPSDisplay!!.setChecked(fpsDisplayEnabled)

                mSwitchFPSDisplay!!.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                    settingsManager.setFPSDisplayEnabled(isChecked)
                    if (mListener != null) {
                        mListener!!.onFPSDisplayChanged(isChecked)
                    }
                }
            }

            // 隐藏控件
            if (mItemHideControls != null) {
                mItemHideControls!!.setOnClickListener { v: View? ->
                    if (mListener != null) {
                        mListener!!.onHideControls()
                    }
                    hide()
                }
            }

            // 触摸控制开关
            if (mSwitchToggleTouch != null) {
                // 加载当前设置
                val settingsManager =
                    SettingsManager.getInstance()
                val isTouchEventEnabled = settingsManager.isTouchEventEnabled()
                mSwitchToggleTouch!!.setChecked(isTouchEventEnabled)

                mSwitchToggleTouch!!.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                    settingsManager.setTouchEventEnabled(isChecked)
                    if (mListener != null) {
                        mListener!!.onToggleTouchEventChanged(isChecked)
                    }
                }
            }

            // 退出游戏
            if (mItemExitGame != null) {
                mItemExitGame!!.setOnClickListener { v: View? ->
                    if (mListener != null) {
                        mListener!!.onExitGame()
                    }
                    hide()
                }
            }
        }
    }

    /**
     * 更新编辑模式UI（包括按钮文本、图标和添加控件区域的可见性）
     */
    private fun updateEditModeUI() {
        // 更新添加控件区域的可见性：编辑模式下显示，非编辑模式下隐藏
        if (mAddControlsSection != null) {
            mAddControlsSection!!.setVisibility(if (mIsEditModeEnabled) View.VISIBLE else View.GONE)
        }

        // 更新"FPS 显示"选项的可见性：编辑模式下隐藏，非编辑模式下显示
        if (mItemFPSDisplay != null) {
            mItemFPSDisplay!!.setVisibility(if (mIsEditModeEnabled) View.GONE else View.VISIBLE)
        }

        // 更新"隐藏控件"选项的可见性：编辑模式下隐藏，非编辑模式下显示
        if (mItemHideControls != null) {
            mItemHideControls!!.setVisibility(if (mIsEditModeEnabled) View.GONE else View.VISIBLE)
        }

        // 更新"切换触摸模式"选项的可见性：编辑模式下隐藏，非编辑模式下显示
        if (mItemToggleTouch != null) {
            mItemToggleTouch!!.setVisibility(if (mIsEditModeEnabled) View.GONE else View.VISIBLE)
        }

        // 更新"退出游戏"选项的可见性：编辑模式下隐藏，非编辑模式下显示
        if (mItemExitGame != null) {
            mItemExitGame!!.setVisibility(if (mIsEditModeEnabled) View.GONE else View.VISIBLE)
        }

        // 更新"切换编辑模式"按钮的文本和图标
        if (mTvToggleEditModeText != null && mIvToggleEditModeIcon != null) {
            if (mIsEditModeEnabled) {
                // 已进入编辑模式，显示"退出编辑模式"
                mTvToggleEditModeText!!.setText(mContext.getString(R.string.editor_exit_edit_mode))
                mIvToggleEditModeIcon!!.setImageResource(R.drawable.ic_close)
                mIvToggleEditModeIcon!!.setColorFilter(Color.parseColor("#F44336")) // 红色
            } else {
                // 未进入编辑模式，显示"进入编辑模式"
                mTvToggleEditModeText!!.setText(mContext.getString(R.string.editor_enter_edit_mode))
                mIvToggleEditModeIcon!!.setImageResource(R.drawable.ic_edit)
                // 恢复默认颜色
                mIvToggleEditModeIcon!!.setColorFilter(null)
            }
        }
    }

    /**
     * 设置编辑模式状态
     */
    fun setEditModeEnabled(enabled: Boolean) {
        mIsEditModeEnabled = enabled
        if (mDialogLayout != null) {
            updateEditModeUI()
        }
    }

    companion object {
        private const val TAG = "UnifiedEditorSettings"
    }
}
