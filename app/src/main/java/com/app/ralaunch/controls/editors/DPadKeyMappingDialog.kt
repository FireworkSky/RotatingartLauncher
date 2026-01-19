package com.app.ralaunch.controls.editors

import android.content.Context
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.controls.KeyMapper
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.utils.LocaleManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * D-Pad键值映射设置对话框
 * 允许用户为D-Pad的四个方向（上、右、下、左）分别设置键值
 */
class DPadKeyMappingDialog(
    private val context: Context,
    controlData: ControlData?,
    private val listener: OnSaveListener?
) {
    private val mControlData: ControlData.DPad

    private var mUpKeyButton: MaterialButton? = null
    private var mRightKeyButton: MaterialButton? = null
    private var mDownKeyButton: MaterialButton? = null
    private var mLeftKeyButton: MaterialButton? = null

    private var mUpKey: ControlData.KeyCode
    private var mRightKey: ControlData.KeyCode
    private var mDownKey: ControlData.KeyCode
    private var mLeftKey: ControlData.KeyCode

    interface OnSaveListener {
        fun onSave(data: ControlData?)
    }

    init {
        // 确保传入的是DPad类型
        if (controlData !is ControlData.DPad) {
            throw IllegalArgumentException("ControlData must be of type DPad")
        }
        mControlData = controlData

        val dpadKeys = mControlData.dpadKeys
        mUpKey = if (dpadKeys.isNotEmpty()) dpadKeys[0] else ControlData.KeyCode.KEYBOARD_W
        mRightKey = if (dpadKeys.size > 1) dpadKeys[1] else ControlData.KeyCode.KEYBOARD_D
        mDownKey = if (dpadKeys.size > 2) dpadKeys[2] else ControlData.KeyCode.KEYBOARD_S
        mLeftKey = if (dpadKeys.size > 3) dpadKeys[3] else ControlData.KeyCode.KEYBOARD_A
    }

    fun show() {
        val localizedContext = LocaleManager.applyLanguage(context)

        // 创建滚动视图
        val scrollView = ScrollView(context)
        scrollView.isFillViewport = true

        // 创建布局
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)

        // 标题说明
        val titleDesc = TextView(context)
        titleDesc.text = localizedContext.getString(R.string.editor_dpad_key_mapping_desc)
        titleDesc.textSize = 14f
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        titleDesc.setTextColor(typedValue.data)
        titleDesc.setPadding(0, 0, 0, 20)
        layout.addView(titleDesc)

        // 上方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_up)))
        mUpKeyButton = createKeyButton(mUpKey)
        mUpKeyButton!!.setOnClickListener { showKeySelectorForDirection(0) }
        layout.addView(mUpKeyButton)

        // 右方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_right)))
        mRightKeyButton = createKeyButton(mRightKey)
        mRightKeyButton!!.setOnClickListener { showKeySelectorForDirection(1) }
        layout.addView(mRightKeyButton)

        // 下方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_down)))
        mDownKeyButton = createKeyButton(mDownKey)
        mDownKeyButton!!.setOnClickListener { showKeySelectorForDirection(2) }
        layout.addView(mDownKeyButton)

        // 左方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_left)))
        mLeftKeyButton = createKeyButton(mLeftKey)
        mLeftKeyButton!!.setOnClickListener { showKeySelectorForDirection(3) }
        layout.addView(mLeftKeyButton)

        // 快速设置按钮（WASD）
        val btnWASD = Button(context)
        btnWASD.text = localizedContext.getString(R.string.editor_joystick_key_reset_wasd)
        btnWASD.setOnClickListener {
            setKey(0, ControlData.KeyCode.KEYBOARD_W)
            setKey(1, ControlData.KeyCode.KEYBOARD_D)
            setKey(2, ControlData.KeyCode.KEYBOARD_S)
            setKey(3, ControlData.KeyCode.KEYBOARD_A)
        }
        layout.addView(btnWASD)

        // 快速设置按钮（方向键）
        val btnArrows = Button(context)
        btnArrows.text = localizedContext.getString(R.string.editor_dpad_key_reset_arrows)
        btnArrows.setOnClickListener {
            setKey(0, ControlData.KeyCode.KEYBOARD_UP)
            setKey(1, ControlData.KeyCode.KEYBOARD_RIGHT)
            setKey(2, ControlData.KeyCode.KEYBOARD_DOWN)
            setKey(3, ControlData.KeyCode.KEYBOARD_LEFT)
        }
        layout.addView(btnArrows)

        scrollView.addView(layout)

        MaterialAlertDialogBuilder(context)
            .setTitle(localizedContext.getString(R.string.editor_dpad_key_mapping))
            .setView(scrollView)
            .setPositiveButton(localizedContext.getString(R.string.editor_save_button_label)) { _, _ ->
                saveChanges()
            }
            .setNegativeButton(localizedContext.getString(R.string.cancel), null)
            .show()
    }

    private fun createDirectionLabel(text: String?): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.textSize = 14f
        tv.setPadding(0, 20, 0, 5)
        return tv
    }

    private fun createKeyButton(keyCode: ControlData.KeyCode): MaterialButton {
        val button = MaterialButton(context)
        val keyMapper = KeyMapper
        button.text = keyMapper.getKeyName(keyCode)
        return button
    }

    private fun showKeySelectorForDirection(direction: Int) {
        val keyDialog = KeySelectorDialog(context, false)
        keyDialog.setOnKeySelectedListener(object : KeySelectorDialog.OnKeySelectedListener {
            override fun onKeySelected(keyCode: ControlData.KeyCode, keyName: String?) {
                setKey(direction, keyCode)
            }
        })
        keyDialog.show()
    }

    private fun setKey(direction: Int, keyCode: ControlData.KeyCode) {
        val keyMapper = KeyMapper
        val keyName = keyMapper.getKeyName(keyCode)

        when (direction) {
            0 -> {
                mUpKey = keyCode
                mUpKeyButton?.text = keyName
            }
            1 -> {
                mRightKey = keyCode
                mRightKeyButton?.text = keyName
            }
            2 -> {
                mDownKey = keyCode
                mDownKeyButton?.text = keyName
            }
            3 -> {
                mLeftKey = keyCode
                mLeftKeyButton?.text = keyName
            }
        }
    }

    private fun saveChanges() {
        // 更新D-Pad键值
        mControlData.dpadKeys = arrayOf(mUpKey, mRightKey, mDownKey, mLeftKey)

        // 回调保存监听器
        listener?.onSave(mControlData)
    }
}
