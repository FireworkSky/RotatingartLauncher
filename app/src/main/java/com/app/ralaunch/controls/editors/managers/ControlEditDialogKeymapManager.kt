package com.app.ralaunch.controls.editors.managers

import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.app.ralaunch.R
import com.app.ralaunch.controls.KeyMapper
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.data.ControlData.Joystick
import com.app.ralaunch.controls.data.ControlData.KeyCode
import com.app.ralaunch.controls.editors.ControlEditDialogMD
import com.app.ralaunch.controls.editors.DPadKeyMappingDialog
import com.app.ralaunch.controls.editors.JoystickKeyMappingDialog
import com.app.ralaunch.controls.editors.KeySelectorDialog

/**
 * 键值设置管理器
 * 统一管理普通按钮和摇杆的键值设置逻辑
 */
object ControlEditDialogKeymapManager {
    /**
     * 绑定键值设置视图
     */
    fun bindKeymapViews(
        view: View,
        refs: UIReferences,
        dialog: ControlEditDialogMD
    ) {
        val itemKeyMapping = view.findViewById<View?>(R.id.item_key_mapping)
        val tvKeyName = view.findViewById<TextView?>(R.id.tv_key_name)
        val switchToggleMode = view.findViewById<SwitchCompat?>(R.id.switch_toggle_mode)

        // 普通按钮的键值映射
        itemKeyMapping?.setOnClickListener {
            showKeySelectDialog(
                dialog,
                refs,
                tvKeyName
            )
        }

        // 摇杆的键值映射
        val itemJoystickKeyMapping = view.findViewById<View?>(R.id.item_joystick_key_mapping)
        val tvJoystickKeyMappingValue =
            view.findViewById<TextView?>(R.id.tv_joystick_key_mapping_value)
        itemJoystickKeyMapping?.setOnClickListener {
            showJoystickKeyMappingDialog(
                dialog,
                refs,
                tvJoystickKeyMappingValue
            )
        }

        // DPad的键值映射
        val itemDPadKeyMapping = view.findViewById<View?>(R.id.item_dpad_key_mapping)
        val tvDPadKeyMappingValue =
            view.findViewById<TextView?>(R.id.tv_dpad_key_mapping_value)
        itemDPadKeyMapping?.setOnClickListener {
            showDPadKeyMappingDialog(
                dialog,
                refs,
                tvDPadKeyMappingValue
            )
        }

        switchToggleMode?.setOnCheckedChangeListener { _, isChecked ->
            if (refs.isUpdating) return@setOnCheckedChangeListener
            val data = refs.currentData
            if (data is ControlData.Button) {
                data.isToggle = isChecked
                refs.notifyUpdate()
            }
        }
    }

    /**
     * 显示摇杆键值映射对话框
     */
    private fun showJoystickKeyMappingDialog(
        dialog: ControlEditDialogMD,
        refs: UIReferences,
        tvJoystickKeyMappingValue: TextView?
    ) {
        val data = refs.currentData

        if (data !is Joystick) {
            return
        }

        val joystickData = data

        // 确保摇杆键值数组已初始化
        if (joystickData.joystickKeys.isEmpty()) {
            joystickData.joystickKeys = arrayOf(
                KeyCode.KEYBOARD_W,  // up
                KeyCode.KEYBOARD_D,  // right
                KeyCode.KEYBOARD_S,  // down
                KeyCode.KEYBOARD_A // left
            )
        }

        val keyMappingDialog = JoystickKeyMappingDialog(
            dialog.requireContext(), joystickData,
            object : JoystickKeyMappingDialog.OnSaveListener {
                override fun onSave(data: ControlData?) {
                    // 更新键值数据
                    if (data is Joystick) {
                        joystickData.joystickKeys = data.joystickKeys

                        // 更新显示
                        updateJoystickKeyMappingDisplay(tvJoystickKeyMappingValue, joystickData)
                        refs.notifyUpdate()
                    }
                }
            })
        keyMappingDialog.show()
    }

    /**
     * 显示DPad键值映射对话框
     */
    private fun showDPadKeyMappingDialog(
        dialog: ControlEditDialogMD,
        refs: UIReferences,
        tvDPadKeyMappingValue: TextView?
    ) {
        val data = refs.currentData

        if (data !is ControlData.DPad) {
            return
        }

        val dpadData = data

        // 确保DPad键值数组已初始化
        if (dpadData.dpadKeys.isEmpty()) {
            dpadData.dpadKeys = arrayOf(
                KeyCode.KEYBOARD_W,  // up
                KeyCode.KEYBOARD_D,  // right
                KeyCode.KEYBOARD_S,  // down
                KeyCode.KEYBOARD_A   // left
            )
        }

        val keyMappingDialog = DPadKeyMappingDialog(
            dialog.requireContext(), dpadData,
            object : DPadKeyMappingDialog.OnSaveListener {
                override fun onSave(data: ControlData?) {
                    // 更新键值数据
                    if (data is ControlData.DPad) {
                        dpadData.dpadKeys = data.dpadKeys

                        // 更新显示
                        updateDPadKeyMappingDisplay(tvDPadKeyMappingValue, dpadData)
                        refs.notifyUpdate()
                    }
                }
            })
        keyMappingDialog.show()
    }

    /**
     * 更新摇杆键值映射显示
     */
    private fun updateJoystickKeyMappingDisplay(tv: TextView?, data: ControlData?) {
        if (tv == null || data !is Joystick) {
            return
        }

        val joystickData = data
        val keys: Array<KeyCode> = joystickData.joystickKeys

        if (keys.size < 4) {
            return
        }

        val keyMapper = KeyMapper
        val up = keyMapper.getKeyName(keys[0])
        val right = keyMapper.getKeyName(keys[1])
        val down = keyMapper.getKeyName(keys[2])
        val left = keyMapper.getKeyName(keys[3])
        tv.text = "↑$up ↓$down ←$left →$right"
    }

    /**
     * 更新DPad键值映射显示
     */
    private fun updateDPadKeyMappingDisplay(tv: TextView?, data: ControlData?) {
        if (tv == null || data !is ControlData.DPad) {
            return
        }

        val dpadData = data
        val keys: Array<KeyCode> = dpadData.dpadKeys

        if (keys.size < 4) {
            return
        }

        val keyMapper = KeyMapper
        val up = keyMapper.getKeyName(keys[0])
        val right = keyMapper.getKeyName(keys[1])
        val down = keyMapper.getKeyName(keys[2])
        val left = keyMapper.getKeyName(keys[3])
        tv.text = "↑$up ↓$down ←$left →$right"
    }

    /**
     * 显示按键选择对话框
     */
    private fun showKeySelectDialog(
        dialog: ControlEditDialogMD,
        refs: UIReferences,
        tvKeyName: TextView?
    ) {
        val data = refs.currentData
        if (data == null) {
            return
        }

        var isGamepadMode = false
        if (data is ControlData.Button) {
            isGamepadMode = data.mode == ControlData.Button.Mode.GAMEPAD
        }

        val keyDialog = KeySelectorDialog(dialog.requireContext(), isGamepadMode)

        keyDialog.setOnKeySelectedListener(object : KeySelectorDialog.OnKeySelectedListener {
            override fun onKeySelected(keyCode: KeyCode, keyName: String?) {
                if (data is ControlData.Button) {
                    val buttonData = data
                    buttonData.keycode = keyCode

                    val keyMapper = KeyMapper
                    val fullKeyName = keyMapper.getKeyName(keyCode)
                    if (tvKeyName != null) {
                        tvKeyName.text = fullKeyName
                    }
                    refs.notifyUpdate()
                }
            }
        })
        keyDialog.show()
    }


    /**
     * 更新键值设置选项的可见性（根据控件类型）
     */
    fun updateKeymapVisibility(keymapView: View, data: ControlData) {
        val itemKeyMapping = keymapView.findViewById<View?>(R.id.item_key_mapping)
        val itemJoystickKeyMapping = keymapView.findViewById<View?>(R.id.item_joystick_key_mapping)
        val itemDPadKeyMapping = keymapView.findViewById<View?>(R.id.item_dpad_key_mapping)
        val itemToggleMode = keymapView.findViewById<View?>(R.id.item_toggle_mode)

        // 按钮和文本控件显示普通键值设置
        val isButton = data is ControlData.Button
        val isText = data is ControlData.Text
        val isJoystick = data is Joystick
        val isDPad = data is ControlData.DPad

        // 普通键值设置（仅按钮和文本控件显示）
        itemKeyMapping?.visibility = if (isButton || isText) View.VISIBLE else View.GONE

        // 摇杆键值设置（仅摇杆显示，且为键盘模式）
        var showJoystickKeyMapping = false
        if (isJoystick) {
            val joystickData = data
            showJoystickKeyMapping = joystickData.mode == Joystick.Mode.KEYBOARD
        }

        itemJoystickKeyMapping?.visibility = if (showJoystickKeyMapping) View.VISIBLE else View.GONE

        // DPad键值设置（仅DPad显示）
        itemDPadKeyMapping?.visibility = if (isDPad) View.VISIBLE else View.GONE

        // 切换模式（仅按钮显示）
        itemToggleMode?.visibility = if (isButton) View.VISIBLE else View.GONE
    }

    /**
     * UI元素引用接口
     */
    interface UIReferences {
        val currentData: ControlData?
        val isUpdating: Boolean
        fun notifyUpdate()
    }
}

