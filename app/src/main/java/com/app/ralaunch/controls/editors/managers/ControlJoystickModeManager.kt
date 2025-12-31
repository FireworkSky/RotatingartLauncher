package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 摇杆模式管理器
 * 统一管理摇杆模式的选择和显示逻辑
 */
object ControlJoystickModeManager {
    /**
     * 获取摇杆模式显示名称
     */
    fun getModeDisplayName(context: Context, mode: ControlData.Joystick.Mode): String {
        return when (mode) {
            ControlData.Joystick.Mode.KEYBOARD -> context.getString(R.string.editor_mode_keyboard)
            ControlData.Joystick.Mode.MOUSE -> context.getString(R.string.editor_mode_mouse)
            ControlData.Joystick.Mode.GAMEPAD -> context.getString(R.string.editor_mode_xbox_controller)
        }
    }

    /**
     * 更新模式显示
     */
    fun updateModeDisplay(context: Context, data: ControlData?, textView: TextView?) {
        if (data !is ControlData.Joystick || textView == null) {
            return
        }

        val modeName = getModeDisplayName(context, data.mode)
        textView.text = modeName
    }

    /**
     * 显示摇杆模式选择对话框
     */
    fun showModeSelectDialog(
        context: Context,
        data: ControlData?,
        listener: OnModeSelectedListener?
    ) {
        if (data !is ControlData.Joystick) {
            return
        }

        val modes = arrayOf(
            context.getString(R.string.editor_mode_keyboard),
            context.getString(R.string.editor_mode_mouse),
            context.getString(R.string.editor_mode_xbox_controller)
        )

        // 获取当前选中的索引
        val currentMode = data.mode
        val currentIndex = when (currentMode) {
            ControlData.Joystick.Mode.KEYBOARD -> 0
            ControlData.Joystick.Mode.MOUSE -> 1
            ControlData.Joystick.Mode.GAMEPAD -> 2
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.editor_joystick_mode)
            .setSingleChoiceItems(modes, currentIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> ControlData.Joystick.Mode.KEYBOARD
                    1 -> ControlData.Joystick.Mode.MOUSE
                    2 -> ControlData.Joystick.Mode.GAMEPAD
                    else -> ControlData.Joystick.Mode.KEYBOARD
                }

                data.mode = newMode

                // 如果切换到键盘模式且键值未设置，设置默认键值
                if (newMode == ControlData.Joystick.Mode.KEYBOARD) {
                    // 如果键值数组为空，初始化为WASD
                    if (data.joystickKeys.isEmpty()) {
                        data.joystickKeys = arrayOf(
                            ControlData.KeyCode.KEYBOARD_W,  // up
                            ControlData.KeyCode.KEYBOARD_D,  // right
                            ControlData.KeyCode.KEYBOARD_S,  // down
                            ControlData.KeyCode.KEYBOARD_A   // left
                        )
                    }
                } else if (newMode == ControlData.Joystick.Mode.MOUSE) {
                    // 鼠标模式下不需要键值
                    data.joystickKeys = emptyArray()
                } else if (newMode == ControlData.Joystick.Mode.GAMEPAD) {
                    // 手柄模式下不需要键值
                    data.joystickKeys = emptyArray()
                }

                listener?.onModeSelected(data)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 模式选择监听器
     */
    interface OnModeSelectedListener {
        fun onModeSelected(data: ControlData?)
    }
}

