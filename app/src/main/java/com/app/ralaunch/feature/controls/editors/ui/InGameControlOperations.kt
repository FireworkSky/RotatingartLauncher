package com.app.ralaunch.feature.controls.editors.ui

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.packs.ControlLayout

/**
 * 游戏内控件操作辅助对象
 * 直接操作布局添加控件
 */
internal object InGameControlOperations {
    fun addButton(layout: ControlLayout, context: Context) {
        val button = ControlData.Button().apply {
            name = "${context.getString(R.string.editor_default_button_name)}_${System.currentTimeMillis()}"
            x = 0.1f
            y = 0.3f
            width = 0.08f
            height = 0.08f
        }
        layout.controls.add(button)
    }
    
    fun addJoystick(layout: ControlLayout, mode: ControlData.Joystick.Mode, isRightStick: Boolean, context: Context) {
        val joystick = ControlData.Joystick().apply {
            name = if (isRightStick) {
                "${context.getString(R.string.joystick_right)}_${System.currentTimeMillis()}"
            } else {
                "${context.getString(R.string.joystick_left)}_${System.currentTimeMillis()}"
            }
            x = if (isRightStick) 0.75f else 0.05f
            y = 0.4f
            width = 0.2f
            height = 0.35f
            this.mode = mode
            this.isRightStick = isRightStick
            if (mode == ControlData.Joystick.Mode.KEYBOARD) {
                joystickKeys = arrayOf(
                    ControlData.KeyCode.KEYBOARD_W,
                    ControlData.KeyCode.KEYBOARD_D,
                    ControlData.KeyCode.KEYBOARD_S,
                    ControlData.KeyCode.KEYBOARD_A
                )
            }
        }
        layout.controls.add(joystick)
    }
    
    fun addTouchPad(layout: ControlLayout, context: Context) {
        val touchPad = ControlData.TouchPad().apply {
            name = "${context.getString(R.string.editor_default_touchpad_name)}_${System.currentTimeMillis()}"
            x = 0.3f
            y = 0.3f
            width = 0.4f
            height = 0.4f
        }
        layout.controls.add(touchPad)
    }
    
    fun addMouseWheel(layout: ControlLayout, context: Context) {
        val mouseWheel = ControlData.MouseWheel().apply {
            name = "${context.getString(R.string.editor_default_mousewheel_name)}_${System.currentTimeMillis()}"
            x = 0.9f
            y = 0.5f
            width = 0.06f
            height = 0.15f
        }
        layout.controls.add(mouseWheel)
    }
    
    fun addText(layout: ControlLayout, context: Context) {
        val text = ControlData.Text().apply {
            name = "${context.getString(R.string.editor_default_text_name)}_${System.currentTimeMillis()}"
            x = 0.5f
            y = 0.1f
            width = 0.1f
            height = 0.05f
            displayText = context.getString(R.string.editor_text_content)
        }
        layout.controls.add(text)
    }
    
    fun addRadialMenu(layout: ControlLayout, context: Context) {
        val radialMenu = ControlData.RadialMenu().apply {
            name = "${context.getString(R.string.control_editor_radial_menu_label)}_${System.currentTimeMillis()}"
            x = 0.5f
            y = 0.5f
            width = 0.12f
            height = 0.12f
        }
        layout.controls.add(radialMenu)
    }
    
    fun addDPad(layout: ControlLayout, context: Context) {
        val dpad = ControlData.DPad().apply {
            name = "${context.getString(R.string.control_editor_dpad_label)}_${System.currentTimeMillis()}"
            x = 0.15f
            y = 0.65f
            width = 0.25f
            height = 0.25f
        }
        layout.controls.add(dpad)
    }
}
