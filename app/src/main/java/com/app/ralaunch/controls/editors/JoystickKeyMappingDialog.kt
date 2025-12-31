package com.app.ralaunch.controls.editors

import android.content.Context
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.controls.KeyMapper
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.utils.LocalizedAlertDialog

/**
 * 摇杆键值映射设置对话框
 * 允许用户为摇杆的四个方向（上、右、下、左）分别设置键值
 */
class JoystickKeyMappingDialog(
    context: Context,
    controlData: ControlData?,
    listener: OnSaveListener?
) : LocalizedAlertDialog(context) {
    private val mControlData: ControlData.Joystick
    private val mSaveListener: OnSaveListener?

    private var mUpSpinner: Spinner? = null
    private var mRightSpinner: Spinner? = null
    private var mDownSpinner: Spinner? = null
    private var mLeftSpinner: Spinner? = null

    interface OnSaveListener {
        fun onSave(data: ControlData?)
    }

    init {
        // 确保传入的是Joystick类型
        if (controlData !is ControlData.Joystick) {
            throw IllegalArgumentException("ControlData must be of type Joystick")
        }
        mControlData = controlData
        mSaveListener = listener

        initDialog()
    }

    private fun initDialog() {
        val localizedContext = getLocalizedContext()

        // 创建布局
        val layout = LinearLayout(getContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)

        // 标题说明
        val titleDesc = TextView(getContext())
        titleDesc.text = localizedContext.getString(R.string.editor_joystick_key_mapping_desc)
        titleDesc.textSize = 14f
        // 使用主题颜色，支持暗色模式
        val typedValue = TypedValue()
        getContext().theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        titleDesc.setTextColor(typedValue.data)
        titleDesc.setPadding(0, 0, 0, 20)
        layout.addView(titleDesc)

        // 获取所有可用按键
        val keyMapper = KeyMapper
        val allKeysMap: Map<ControlData.KeyCode, String> = keyMapper.allKeys
        val keyNames = mutableListOf<String>()
        val keyCodes = mutableListOf<ControlData.KeyCode>()
        for ((keyCode, keyName) in allKeysMap) {
            keyNames.add(keyName)
            keyCodes.add(keyCode)
        }

        // 创建适配器
        val adapter = ArrayAdapter(
            getContext(),
            android.R.layout.simple_spinner_item, keyNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val joystickKeys = mControlData.joystickKeys

        // 上方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_up)))
        mUpSpinner = createSpinner(
            adapter, keyCodes,
            if (joystickKeys.isNotEmpty()) joystickKeys[0] else ControlData.KeyCode.KEYBOARD_W
        )
        layout.addView(mUpSpinner)

        // 右方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_right)))
        mRightSpinner = createSpinner(
            adapter, keyCodes,
            if (joystickKeys.size > 1) joystickKeys[1] else ControlData.KeyCode.KEYBOARD_D
        )
        layout.addView(mRightSpinner)

        // 下方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_down)))
        mDownSpinner = createSpinner(
            adapter, keyCodes,
            if (joystickKeys.size > 2) joystickKeys[2] else ControlData.KeyCode.KEYBOARD_S
        )
        layout.addView(mDownSpinner)

        // 左方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_left)))
        mLeftSpinner = createSpinner(
            adapter, keyCodes,
            if (joystickKeys.size > 3) joystickKeys[3] else ControlData.KeyCode.KEYBOARD_A
        )
        layout.addView(mLeftSpinner)

        // 快速设置按钮（WASD）
        val btnWASD = Button(getContext())
        btnWASD.text = localizedContext.getString(R.string.editor_joystick_key_reset_wasd)
        btnWASD.setOnClickListener {
            setKeyCode(mUpSpinner!!, keyCodes, ControlData.KeyCode.KEYBOARD_W)
            setKeyCode(mRightSpinner!!, keyCodes, ControlData.KeyCode.KEYBOARD_D)
            setKeyCode(mDownSpinner!!, keyCodes, ControlData.KeyCode.KEYBOARD_S)
            setKeyCode(mLeftSpinner!!, keyCodes, ControlData.KeyCode.KEYBOARD_A)
        }
        layout.addView(btnWASD)

        setView(layout)
        setTitle(localizedContext.getString(R.string.editor_joystick_key_mapping))

        setButton(
            BUTTON_POSITIVE,
            localizedContext.getString(R.string.editor_save_button_label)
        ) { _, _ ->
            saveChanges()
        }
        setButton(
            BUTTON_NEGATIVE,
            localizedContext.getString(R.string.cancel)
        ) { _, _ ->
            dismiss()
        }
    }

    private fun createDirectionLabel(text: String?): TextView {
        val tv = TextView(getContext())
        tv.text = text
        tv.textSize = 14f
        tv.setPadding(0, 20, 0, 5)
        return tv
    }

    private fun createSpinner(
        adapter: ArrayAdapter<String>,
        keyCodes: List<ControlData.KeyCode>,
        defaultKey: ControlData.KeyCode
    ): Spinner {
        val spinner = Spinner(getContext())
        spinner.adapter = adapter
        setKeyCode(spinner, keyCodes, defaultKey)
        return spinner
    }

    private fun setKeyCode(
        spinner: Spinner,
        keyCodes: List<ControlData.KeyCode>,
        keyCode: ControlData.KeyCode
    ) {
        val index = keyCodes.indexOf(keyCode)
        if (index >= 0) {
            spinner.setSelection(index)
        }
    }

    private fun saveChanges() {
        val keyMapper = KeyMapper
        val allKeysMap: Map<ControlData.KeyCode, String> = keyMapper.allKeys
        val keyCodes = allKeysMap.keys.toList()

        val upKey = keyCodes[mUpSpinner!!.selectedItemPosition]
        val rightKey = keyCodes[mRightSpinner!!.selectedItemPosition]
        val downKey = keyCodes[mDownSpinner!!.selectedItemPosition]
        val leftKey = keyCodes[mLeftSpinner!!.selectedItemPosition]

        // 更新摇杆键值
        mControlData.joystickKeys = arrayOf(upKey, rightKey, downKey, leftKey)

        // 确保摇杆模式为键盘模式
        if (mControlData.mode != ControlData.Joystick.Mode.KEYBOARD) {
            mControlData.mode = ControlData.Joystick.Mode.KEYBOARD
        }

        // 回调保存监听器
        mSaveListener?.onSave(mControlData)
        dismiss()
    }
}
