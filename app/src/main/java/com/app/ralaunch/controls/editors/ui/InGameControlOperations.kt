package com.app.ralaunch.controls.editors.ui

import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout

/**
 * 游戏内控件操作辅助对象
 * 直接操作布局添加控件
 */
internal object InGameControlOperations {
    fun addButton(layout: ControlLayout) {
        val button = ControlData.Button().apply {
            name = "按钮_${System.currentTimeMillis()}"
            x = 0.1f
            y = 0.3f
            width = 0.08f
            height = 0.08f
        }
        layout.controls.add(button)
    }
    
    fun addJoystick(layout: ControlLayout, mode: ControlData.Joystick.Mode, isRightStick: Boolean) {
        val joystick = ControlData.Joystick().apply {
            name = if (isRightStick) "右摇杆_${System.currentTimeMillis()}" else "左摇杆_${System.currentTimeMillis()}"
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
    
    fun addTouchPad(layout: ControlLayout) {
        val touchPad = ControlData.TouchPad().apply {
            name = "触控板_${System.currentTimeMillis()}"
            x = 0.3f
            y = 0.3f
            width = 0.4f
            height = 0.4f
        }
        layout.controls.add(touchPad)
    }
    
    fun addMouseWheel(layout: ControlLayout) {
        val mouseWheel = ControlData.MouseWheel().apply {
            name = "滚轮_${System.currentTimeMillis()}"
            x = 0.9f
            y = 0.5f
            width = 0.06f
            height = 0.15f
        }
        layout.controls.add(mouseWheel)
    }
    
    fun addText(layout: ControlLayout) {
        val text = ControlData.Text().apply {
            name = "文本_${System.currentTimeMillis()}"
            x = 0.5f
            y = 0.1f
            width = 0.1f
            height = 0.05f
            displayText = "文本内容"
        }
        layout.controls.add(text)
    }
    
    fun addRadialMenu(layout: ControlLayout) {
        val radialMenu = ControlData.RadialMenu().apply {
            name = "轮盘_${System.currentTimeMillis()}"
            x = 0.5f
            y = 0.5f
            width = 0.12f
            height = 0.12f
        }
        layout.controls.add(radialMenu)
    }
    
    fun addDPad(layout: ControlLayout) {
        val dpad = ControlData.DPad().apply {
            name = "十字键_${System.currentTimeMillis()}"
            x = 0.15f
            y = 0.65f
            width = 0.25f
            height = 0.25f
        }
        layout.controls.add(dpad)
    }
}
