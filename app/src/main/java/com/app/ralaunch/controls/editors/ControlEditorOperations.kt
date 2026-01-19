package com.app.ralaunch.controls.editors

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.data.ControlData.Joystick
import com.app.ralaunch.controls.data.ControlData.KeyCode
import com.app.ralaunch.controls.data.ControlData.TouchPad
import com.app.ralaunch.controls.packs.ControlLayout

/**
 * 控件编辑器操作类
 *
 * 统一管理控件编辑器的共同操作，包括：
 * - 添加按钮/摇杆/触摸板/文本
 * - 保存/加载布局
 * - 摇杆模式设置
 */
object ControlEditorOperations {
    val context: Context
        get() = RaLaunchApplication.getAppContext()

    /**
     * 添加按钮到配置
     */
    fun addButton(layout: ControlLayout): ControlData.Button {
        val button = ControlData.Button()
        button.name = context.getString(R.string.editor_default_button_name)
        button.x = 0.5f
        button.y = 0.5f
        button.width = 0.15f
        button.height = 0.15f
        button.opacity = 0.5f
        button.isVisible = true
        button.keycode = KeyCode.KEYBOARD_SPACE

        layout.controls.add(button)
        return button
    }

    /**
     * 添加摇杆到配置
     */
    fun addJoystick(
        layout: ControlLayout, joystickMode: Joystick.Mode, isRightStick: Boolean
    ): Joystick {
        val joystick = Joystick()
        joystick.name = if (isRightStick) context.getString(R.string.joystick_right) else context.getString(R.string.joystick_left)
        joystick.mode = joystickMode
        joystick.isRightStick = isRightStick

        // 设置位置
        if (isRightStick) {
            joystick.x = 0.65f
        } else {
            joystick.x = 0.15f
        }
        joystick.y = 0.5f

        // 设置尺寸和样式
        joystick.width = 0.45f
        joystick.height = 0.45f
        joystick.opacity = 0.5f
        joystick.bgColor = -0xb5b5b6
        joystick.strokeColor = 0x66FFFFFF
        joystick.strokeWidth = 2f
        joystick.cornerRadius = 225f
        joystick.stickOpacity = 0.8f
        joystick.stickKnobSize = 0.35f

        // 设置按键映射（仅键盘模式需要）
        if (joystickMode == Joystick.Mode.KEYBOARD) {
            joystick.joystickKeys = arrayOf(
                KeyCode.KEYBOARD_W, KeyCode.KEYBOARD_D, KeyCode.KEYBOARD_S, KeyCode.KEYBOARD_A
            )
        }

        layout.controls.add(joystick)
        return joystick
    }

    /**
     * 添加触控板到配置
     */
    fun addTouchPad(layout: ControlLayout): TouchPad {
        val touchpad = TouchPad()
        touchpad.name = context.getString(R.string.editor_default_touchpad_name)
        touchpad.x = 0.5f
        touchpad.y = 0.5f
        touchpad.width = 0.45f
        touchpad.height = 0.45f
        touchpad.opacity = 0.5f
        touchpad.cornerRadius = 22.0f
        touchpad.isVisible = true

        layout.controls.add(touchpad)
        return touchpad
    }

    /**
     * 添加鼠标滚轮到配置
     */
    fun addMouseWheel(layout: ControlLayout): ControlData.MouseWheel {
        val mouseWheel = ControlData.MouseWheel()
        mouseWheel.name = context.getString(R.string.editor_default_mousewheel_name)
        mouseWheel.x = 0.5f
        mouseWheel.y = 0.5f
        mouseWheel.width = 0.2f
        mouseWheel.height = 0.3f
        mouseWheel.isSizeRatioLocked = false
        mouseWheel.opacity = 0.5f
        mouseWheel.cornerRadius = 15.0f
        mouseWheel.isVisible = true

        layout.controls.add(mouseWheel)
        return mouseWheel
    }

    /**
     * 添加文本控件到配置
     */
    fun addText(layout: ControlLayout): ControlData.Text {
        val defaultTextName = context.getString(R.string.editor_default_text_name)
        val text = ControlData.Text()
        text.name = defaultTextName
        text.x = 0.5f
        text.y = 0.5f
        text.width = 0.15f
        text.height = 0.15f
        text.opacity = 0.5f
        text.bgColor = -0x7f7f80
        text.isVisible = true
        text.shape = ControlData.Text.Shape.RECTANGLE
        text.displayText = defaultTextName

        layout.controls.add(text)
        return text
    }

    /**
     * 添加D-Pad到配置
     */
    fun addDPad(layout: ControlLayout): ControlData.DPad {
        val dpad = ControlData.DPad()
        dpad.name = context.getString(R.string.editor_default_dpad_name)
        dpad.x = 0.15f
        dpad.y = 0.65f
        dpad.width = 0.35f
        dpad.height = 0.35f
        dpad.opacity = 0.5f
        dpad.bgColor = -0x7f7f80
        dpad.strokeColor = 0x66FFFFFF
        dpad.strokeWidth = 1f
        dpad.cornerRadius = 8f
        dpad.isVisible = true

        // 默认WASD键位映射
        dpad.dpadKeys = arrayOf(
            KeyCode.KEYBOARD_W,  // 上
            KeyCode.KEYBOARD_D,  // 右
            KeyCode.KEYBOARD_S,  // 下
            KeyCode.KEYBOARD_A   // 左
        )

        layout.controls.add(dpad)
        return dpad
    }

    /**
     * 显示摇杆模式批量设置对话框
     */
    fun showJoystickModeDialog(
        context: Context, layout: ControlLayout?, onLayoutUpdated: Runnable?
    ) {
        if (layout == null) return

        // 统计当前布局中的摇杆数量
        var joystickCount = 0
        for (control in layout.controls) {
            if (control is Joystick) {
                joystickCount++
            }
        }

        if (joystickCount == 0) {
            Toast.makeText(
                context, context.getString(R.string.editor_no_joystick), Toast.LENGTH_SHORT
            ).show()
            return
        }

        val modes = arrayOf<String?>(
            context.getString(R.string.editor_joystick_mode_keyboard),
            context.getString(R.string.editor_joystick_mode_mouse),
            context.getString(R.string.editor_joystick_mode_sdl)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.editor_joystick_mode_settings))
            .setMessage(context.getString(R.string.editor_joystick_mode_message, joystickCount))
            .setItems(
                modes
            ) { dialog: DialogInterface?, which: Int ->
                val newMode: Joystick.Mode?
                val modeName: String?

                when (which) {
                    0 -> {
                        newMode = Joystick.Mode.KEYBOARD
                        modeName = context.getString(R.string.editor_mode_keyboard_detailed)
                    }

                    1 -> {
                        newMode = Joystick.Mode.MOUSE
                        modeName = context.getString(R.string.editor_mode_mouse_detailed)
                    }

                    2 -> {
                        newMode = Joystick.Mode.GAMEPAD
                        modeName = context.getString(R.string.editor_mode_xbox_detailed)
                    }

                    else -> return@setItems
                }

                // 批量更新所有摇杆的模式
                val updatedCount = updateJoystickModes(layout, newMode)

                // 通知布局已更新
                onLayoutUpdated?.run()
                Toast.makeText(
                    context,
                    context.getString(R.string.editor_joysticks_set, updatedCount, modeName),
                    Toast.LENGTH_SHORT
                ).show()
            }.setNegativeButton(context.getString(R.string.cancel), null).show()
    }

    /**
     * 批量更新摇杆模式
     */
    private fun updateJoystickModes(layout: ControlLayout, newMode: Joystick.Mode): Int {
        var updatedCount = 0

        layout.controls.mapNotNull { it as? Joystick }.forEach { joystick ->
            joystick.mode = newMode
            updatedCount++
        }

        return updatedCount
    }

    /**
     * 保存布局到 ControlPackManager
     */
    fun saveLayout(context: Context, layout: ControlLayout?): Boolean {
        if (layout == null) {
            Toast.makeText(
                context, context.getString(R.string.editor_no_layout_to_save), Toast.LENGTH_SHORT
            ).show()
            return false
        }

        try {
            val packManager = RaLaunchApplication.getControlPackManager()
            val packId = packManager.getSelectedPackId()
            
            if (packId != null) {
                packManager.savePackLayout(packId, layout)
                Toast.makeText(
                    context, context.getString(R.string.editor_layout_saved), Toast.LENGTH_SHORT
                ).show()
                return true
            } else {
                Toast.makeText(
                    context, context.getString(R.string.editor_no_layout_to_save), Toast.LENGTH_SHORT
                ).show()
                return false
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_save_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    /**
     * 从 ControlPackManager 加载布局
     */
    fun loadLayout(packId: String?): ControlLayout? {
        try {
            val packManager = RaLaunchApplication.getControlPackManager()

            if (packId != null) {
                return packManager.getPackLayout(packId)
            } else {
                return packManager.getCurrentLayout()
            }
        } catch (_: Exception) {
            return null
        }
    }
}
