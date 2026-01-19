package com.app.ralaunch.controls.editors

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.editors.managers.ControlDataSyncManager
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 统一的控件编辑管理器
 *
 * 支持两种模式：
 * 1. MODE_STANDALONE (游戏外): 直接进入编辑模式，用于独立的控件编辑器界面
 * 2. MODE_IN_GAME (游戏内): 需要手动进入编辑模式，用于游戏运行时的编辑
 */
class ControlEditorManager(
    private val mContext: Context, 
    private var mControlLayout: ControlLayoutView?,
    private val mContentFrame: ViewGroup?,
    /**
     * 获取当前模式
     */
    var mode: Mode
) {
    private val mScreenWidth: Int
    private val mScreenHeight: Int
    private val packManager = RaLaunchApplication.getControlPackManager()

    private var mCurrentEditingPackId: String? = null // Track the pack being edited
    private var mIsInEditor = false
    private var mHasUnsavedChanges = false

    private var mControlEditDialog: ControlEditDialogMD? = null
    private var mEditorSettingsDialog: UnifiedEditorSettingsDialog? = null

    private var mStateListener: OnEditorStateChangedListener? = null
    private var mLayoutChangedListener: OnLayoutChangedListener? = null
    private var mFPSDisplayListener: OnFPSDisplayChangedListener? = null
    private var mOnHideControlsListener: OnHideControlsListener? = null
    private var mToggleTouchEventListener: OnToggleTouchEventChangedListener? = null
    private var mOnExitGameListener: OnExitGameListener? = null

    enum class Mode {
        STANDALONE, /** 独立模式：直接进入编辑模式  */
        IN_GAME     /** 游戏内模式：需要手动进入编辑模式  */
    }

    /**
     * 编辑状态变化监听器
     */
    interface OnEditorStateChangedListener {
        fun onEditorEntered()
        fun onEditorExited()
    }

    /**
     * 布局变化监听器（用于 Activity 刷新显示）
     */
    interface OnLayoutChangedListener {
        fun onLayoutChanged()
    }

    /**
     * FPS 显示设置变化监听器
     */
    interface OnFPSDisplayChangedListener {
        fun onFPSDisplayChanged(enabled: Boolean)
    }

    /**
     * 隐藏控件监听器
     */
    interface OnHideControlsListener {
        fun onHideControls()
    }

    /**
     * 触摸传递事件设置变化监听器
     */
    interface OnToggleTouchEventChangedListener {
        fun onToggleTouchEventChanged(enabled: Boolean)
    }

    /**
     * 退出游戏监听器
     */
    interface OnExitGameListener {
        fun onExitGame()
    }

    /**
     * 创建控件编辑管理器
     */
    init {
        val metrics = mContext.resources.displayMetrics
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels

        // 独立模式下自动进入编辑模式
        if (this.mode == Mode.STANDALONE) {
            setupEditMode()
        }
    }

    fun setOnEditorStateChangedListener(listener: OnEditorStateChangedListener?) {
        mStateListener = listener
    }

    fun setOnLayoutChangedListener(listener: OnLayoutChangedListener?) {
        mLayoutChangedListener = listener
    }

    fun setOnFPSDisplayChangedListener(listener: OnFPSDisplayChangedListener?) {
        mFPSDisplayListener = listener
    }

    fun setOnHideControlsListener(listener: OnHideControlsListener?) {
        mOnHideControlsListener = listener
    }

    fun setOnToggleTouchEventChangedListener(listener: OnToggleTouchEventChangedListener?) {
        mToggleTouchEventListener = listener
    }

    fun setOnExitGameListener(listener: OnExitGameListener?) {
        mOnExitGameListener = listener
    }

    fun setControlLayout(controlLayout: ControlLayoutView?) {
        mControlLayout = controlLayout
        if (this.mode == Mode.STANDALONE) {
            setupEditMode()
        } else if (mIsInEditor) {
            setupEditMode()
        }
    }

    /**
     * Set the pack ID that is currently being edited.
     * This ensures saves go to the correct pack, not the selected one.
     */
    fun setCurrentEditingPackId(packId: String?) {
        mCurrentEditingPackId = packId
    }

    private fun setupEditMode() {
        if (mControlLayout == null) return

        if (this.mode == Mode.STANDALONE) {
            mIsInEditor = true
        }

        mControlLayout!!.isModifiable = true
        initControlEditDialog()

        mControlLayout!!.setEditControlListener { data: ControlData? ->
            if (mControlEditDialog != null && mContext is FragmentActivity) {
                val activity = mContext
                mControlEditDialog!!.showWithData(
                    activity.supportFragmentManager,
                    "control_edit",
                    data
                )
            }
        }

        mControlLayout!!.setOnControlChangedListener {
            mHasUnsavedChanges = true
        }

        disableClippingRecursive(mControlLayout!!)
    }

    fun enterEditMode() {
        if (mControlLayout == null) return
        if (this.mode == Mode.STANDALONE) return

        mIsInEditor = true
        mHasUnsavedChanges = false

        setupEditMode()
        initEditorSettingsDialog()
        mEditorSettingsDialog?.setEditModeEnabled(true)
        mControlLayout!!.isControlsVisible = true

        Toast.makeText(mContext, R.string.editor_mode_on, Toast.LENGTH_SHORT).show()

        if (mStateListener != null) {
            mStateListener!!.onEditorEntered()
        }
    }

    fun exitEditMode() {
        if (this.mode == Mode.STANDALONE) return

        if (mHasUnsavedChanges) {
            AlertDialog.Builder(mContext)
                .setTitle(R.string.editor_exit_confirm)
                .setMessage(R.string.editor_exit_message)
                .setPositiveButton(R.string.game_menu_yes) { dialog: DialogInterface?, which: Int ->
                    performExitEditMode()
                }
                .setNegativeButton(R.string.game_menu_no, null)
                .show()
        } else {
            performExitEditMode()
        }
    }

    private fun performExitEditMode() {
        if (mControlLayout == null) return

        mIsInEditor = false
        mControlLayout!!.isModifiable = false
        mEditorSettingsDialog?.setEditModeEnabled(false)
        mControlLayout!!.loadLayoutFromPackManager()
        disableClippingRecursive(mControlLayout!!)

        Toast.makeText(mContext, R.string.editor_mode_off, Toast.LENGTH_SHORT).show()

        if (mStateListener != null) {
            mStateListener!!.onEditorExited()
        }
    }

    fun toggleEditMode() {
        if (this.mode == Mode.STANDALONE) return

        if (mIsInEditor) {
            exitEditMode()
        } else {
            enterEditMode()
        }
    }

    private fun initControlEditDialog() {
        if (mControlEditDialog != null) return

        mControlEditDialog = ControlEditDialogMD.newInstance(mScreenWidth, mScreenHeight)

        mControlEditDialog?.setOnControlUpdatedListener(object : ControlEditDialogMD.OnControlUpdatedListener {
            override fun onControlUpdated(data: ControlData?) {
                if (mControlLayout != null && data != null) {
                    ControlDataSyncManager.syncControlDataToView(mControlLayout, data)
                    mHasUnsavedChanges = true
                }
            }
        })

        mControlEditDialog?.setOnControlDeletedListener(object : ControlEditDialogMD.OnControlDeletedListener {
            override fun onControlDeleted(data: ControlData?) {
                if (mControlLayout != null && data != null) {
                    val layout = mControlLayout!!.currentLayout
                    if (layout != null) {
                        for (i in layout.controls.indices.reversed()) {
                            if (layout.controls[i] === data) {
                                layout.controls.removeAt(i)
                                break
                            }
                        }
                        mControlLayout!!.loadLayout(layout)
                        disableClippingRecursive(mControlLayout!!)
                        mHasUnsavedChanges = true

                        mLayoutChangedListener?.onLayoutChanged()
                    }
                }
            }
        })

        mControlEditDialog?.setOnControlCopiedListener(object : ControlEditDialogMD.OnControlCopiedListener {
            override fun onControlCopied(data: ControlData?) {
                if (mControlLayout != null && data != null) {
                    val layout = mControlLayout!!.currentLayout
                    if (layout != null) {
                        layout.controls.add(data)
                        mControlLayout!!.loadLayout(layout)
                        disableClippingRecursive(mControlLayout!!)
                        mHasUnsavedChanges = true

                        mLayoutChangedListener?.onLayoutChanged()
                    }
                }
            }
        })
    }

    fun initEditorSettingsDialog() {
        if (mEditorSettingsDialog != null) return

        val dialogMode =
            if (this.mode == Mode.STANDALONE) UnifiedEditorSettingsDialog.DialogMode.EDITOR
            else UnifiedEditorSettingsDialog.DialogMode.GAME

        mEditorSettingsDialog = UnifiedEditorSettingsDialog(
            mContext, mContentFrame!!, mScreenWidth, dialogMode
        )

        mEditorSettingsDialog?.setOnMenuItemClickListener(object : UnifiedEditorSettingsDialog.OnMenuItemClickListener {
            override fun onAddButton() {
                addButton()
            }

            override fun onAddJoystick() {
                addJoystick()
            }

            override fun onAddTouchPad() {
                addTouchPad()
            }

            override fun onAddMouseWheel() {
                addMouseWheel()
            }

            override fun onAddText() {
                addText()
            }

            override fun onAddDPad() {
                addDPad()
            }

            override fun onToggleEditMode() {
                toggleEditMode()
            }

            override fun onSaveLayout() {
                saveLayout()
                Toast.makeText(
                    mContext,
                    mContext.getString(R.string.editor_layout_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFPSDisplayChanged(enabled: Boolean) {
                mFPSDisplayListener?.onFPSDisplayChanged(enabled)
            }

            override fun onHideControls() {
                mOnHideControlsListener?.onHideControls()
            }

            override fun onToggleTouchEventChanged(enabled: Boolean) {
                mToggleTouchEventListener?.onToggleTouchEventChanged(enabled)
            }

            override fun onExitGame() {
                mOnExitGameListener?.onExitGame()
            }
        })

        if (this.mode == Mode.STANDALONE) {
            mEditorSettingsDialog?.setEditModeEnabled(true)
        }
    }

    fun showSettingsDialog() {
        if (mEditorSettingsDialog == null) {
            initEditorSettingsDialog()
        }
        if (this.mode == Mode.STANDALONE) {
            mEditorSettingsDialog?.setEditModeEnabled(true)
        } else {
            mEditorSettingsDialog?.setEditModeEnabled(mIsInEditor)
        }
        mEditorSettingsDialog?.show()
        if (this.mode == Mode.STANDALONE) {
            mEditorSettingsDialog?.setEditModeEnabled(true)
        }
    }

    fun addButton() {
        if (mControlLayout == null) return

        var layout = mControlLayout!!.currentLayout
        if (layout == null) {
            layout = ControlLayout()
            layout.controls = mutableListOf()
            mControlLayout!!.loadLayout(layout)
        }

        ControlEditorOperations.addButton(layout)

        mControlLayout!!.loadLayout(layout)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_button_added),
            Toast.LENGTH_SHORT
        ).show()

        mLayoutChangedListener?.onLayoutChanged()
    }

    fun addJoystick() {
        if (mControlLayout == null) return

        val layout = mControlLayout!!.currentLayout
        val finalLayout: ControlLayout
        if (layout == null) {
            finalLayout = ControlLayout()
            finalLayout.controls = mutableListOf()
            mControlLayout!!.loadLayout(finalLayout)
        } else {
            finalLayout = layout
        }

        val joystickTypeOptions = arrayOf(
            mContext.getString(R.string.editor_joystick_type_move_aim),
            mContext.getString(R.string.editor_joystick_type_gamepad)
        )

        MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_type))
            .setItems(joystickTypeOptions) { _, which ->
                if (which == 0) {
                    ControlEditorOperations.addJoystick(
                        finalLayout,
                        ControlData.Joystick.Mode.KEYBOARD,
                        false
                    )
                    ControlEditorOperations.addJoystick(
                        finalLayout,
                        ControlData.Joystick.Mode.MOUSE,
                        true
                    )

                    mControlLayout!!.loadLayout(finalLayout)
                    disableClippingRecursive(mControlLayout!!)
                    mHasUnsavedChanges = true
                    Toast.makeText(
                        mContext,
                        mContext.getString(R.string.editor_joystick_added),
                        Toast.LENGTH_SHORT
                    ).show()

                    mLayoutChangedListener?.onLayoutChanged()
                } else {
                    val joystickMode = ControlData.Joystick.Mode.GAMEPAD
                    showStickSideDialog(finalLayout, joystickMode)
                }
            }
            .show()
    }

    fun addTouchPad() {
        if (mControlLayout == null) return

        var layout = mControlLayout!!.currentLayout
        if (layout == null) {
            layout = ControlLayout()
            layout.controls = mutableListOf()
            mControlLayout!!.loadLayout(layout)
        }

        ControlEditorOperations.addTouchPad(layout)

        mControlLayout!!.loadLayout(layout)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_touchpad_added),
            Toast.LENGTH_SHORT
        ).show()

        mLayoutChangedListener?.onLayoutChanged()
    }

    fun addMouseWheel() {
        if (mControlLayout == null) return

        var layout = mControlLayout!!.currentLayout
        if (layout == null) {
            layout = ControlLayout()
            layout.controls = mutableListOf()
            mControlLayout!!.loadLayout(layout)
        }

        ControlEditorOperations.addMouseWheel(layout)

        mControlLayout!!.loadLayout(layout)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_mousewheel_added),
            Toast.LENGTH_SHORT
        ).show()

        mLayoutChangedListener?.onLayoutChanged()
    }

    private fun showStickSideDialog(finalLayout: ControlLayout, joystickMode: ControlData.Joystick.Mode) {
        val stickSideOptions = arrayOf(
            mContext.getString(R.string.editor_joystick_side_left),
            mContext.getString(R.string.editor_joystick_side_right)
        )

        MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_side))
            .setItems(stickSideOptions) { _, which ->
                val isRightStick = (which == 1)
                ControlEditorOperations.addJoystick(
                    finalLayout, joystickMode, isRightStick
                )

                mControlLayout!!.loadLayout(finalLayout)
                disableClippingRecursive(mControlLayout!!)
                mHasUnsavedChanges = true
                Toast.makeText(
                    mContext,
                    mContext.getString(R.string.editor_joystick_added),
                    Toast.LENGTH_SHORT
                ).show()

                mLayoutChangedListener?.onLayoutChanged()
            }
            .show()
    }

    fun addText() {
        if (mControlLayout == null) return

        var layout = mControlLayout!!.currentLayout
        if (layout == null) {
            layout = ControlLayout()
            layout.controls = mutableListOf()
            mControlLayout!!.loadLayout(layout)
        }

        ControlEditorOperations.addText(layout)

        mControlLayout!!.loadLayout(layout)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_text_added),
            Toast.LENGTH_SHORT
        ).show()

        mLayoutChangedListener?.onLayoutChanged()
    }

    fun addDPad() {
        if (mControlLayout == null) return

        var layout = mControlLayout!!.currentLayout
        if (layout == null) {
            layout = ControlLayout()
            layout.controls = mutableListOf()
            mControlLayout!!.loadLayout(layout)
        }

        ControlEditorOperations.addDPad(layout)

        mControlLayout!!.loadLayout(layout)
        disableClippingRecursive(mControlLayout!!)
        mHasUnsavedChanges = true
        Toast.makeText(
            mContext,
            mContext.getString(R.string.editor_dpad_added),
            Toast.LENGTH_SHORT
        ).show()

        mLayoutChangedListener?.onLayoutChanged()
    }

    fun saveLayout() {
        if (mControlLayout == null) return

        val layout = mControlLayout!!.currentLayout
        if (layout != null) {
            // Use the currently editing pack ID, fallback to selected if not set
            val packId = mCurrentEditingPackId ?: packManager.getSelectedPackId()
            if (packId != null) {
                packManager.savePackLayout(packId, layout)
                mHasUnsavedChanges = false
            }
        }
    }

    fun saveLayout(packId: String?) {
        Log.d(TAG, "saveLayout called: packId=$packId, mControlLayout=$mControlLayout")
        if (mControlLayout == null || packId == null) {
            Log.w(TAG, "saveLayout: ABORTED - mControlLayout=$mControlLayout, packId=$packId")
            return
        }

        val layout = mControlLayout!!.currentLayout
        Log.d(TAG, "saveLayout: layout=$layout, controls=${layout?.controls?.size}")
        
        if (layout != null) {
            // 打印纹理配置用于调试
            layout.controls.filterIsInstance<com.app.ralaunch.controls.data.ControlData.Button>()
                .forEach { btn ->
                    if (btn.texture.hasAnyTexture) {
                        Log.i(TAG, "saveLayout: Button '${btn.name}' has texture: ${btn.texture.normal.path}")
                    }
                }
            
            packManager.savePackLayout(packId, layout)
            mHasUnsavedChanges = false
            Log.i(TAG, "saveLayout: SUCCESS - saved ${layout.controls.size} controls")
        } else {
            Log.w(TAG, "saveLayout: FAILED - layout is null")
        }
    }

    fun hideSettingsDialog() {
        mEditorSettingsDialog?.hide()
    }

    val isSettingsDialogShowing: Boolean
        get() = mEditorSettingsDialog?.isDisplaying ?: false

    val isEditDialogShowing: Boolean
        get() = mControlEditDialog?.isAdded ?: false

    fun dismissEditDialog() {
        mControlEditDialog?.dismiss()
    }

    val isInEditor: Boolean
        get() = this.mode == Mode.STANDALONE || mIsInEditor

    fun hasUnsavedChanges(): Boolean {
        return mHasUnsavedChanges
    }

    private fun disableClippingRecursive(view: View) {
        if (view is ViewGroup) {
            val viewGroup = view
            viewGroup.clipChildren = false
            viewGroup.clipToPadding = false

            for (i in 0..<viewGroup.childCount) {
                disableClippingRecursive(viewGroup.getChildAt(i))
            }
        }

        view.clipToOutline = false
        view.clipBounds = null
    }

    companion object {
        private const val TAG = "ControlEditorManager"
    }
}
