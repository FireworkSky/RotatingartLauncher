package com.app.ralaunch.controls

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData

/**
 * 按键映射辅助类
 * 提供按键码和按键名称的映射关系
 */
object KeyMapper {
    /**
     * 获取所有可用的按键映射（KeyCode -> 显示名称）
     * @param context 用于获取本地化字符串资源
     */
    fun getAllKeys(context: Context): Map<ControlData.KeyCode, String> {
        val keys: MutableMap<ControlData.KeyCode, String> = LinkedHashMap()

        // 特殊功能
        keys[ControlData.KeyCode.SPECIAL_KEYBOARD] = context.getString(R.string.key_keyboard)
        keys[ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON] = context.getString(R.string.key_touchpad_buttons)

        // 鼠标按键
        keys[ControlData.KeyCode.MOUSE_LEFT] = context.getString(R.string.key_mouse_left)
        keys[ControlData.KeyCode.MOUSE_RIGHT] = context.getString(R.string.key_mouse_right)
        keys[ControlData.KeyCode.MOUSE_MIDDLE] = context.getString(R.string.key_mouse_middle)
        keys[ControlData.KeyCode.MOUSE_WHEEL_UP] = context.getString(R.string.key_mouse_wheel_up)
        keys[ControlData.KeyCode.MOUSE_WHEEL_DOWN] = context.getString(R.string.key_mouse_wheel_down)

        // 手柄按钮
        keys[ControlData.KeyCode.XBOX_BUTTON_A] = "A"
        keys[ControlData.KeyCode.XBOX_BUTTON_B] = "B"
        keys[ControlData.KeyCode.XBOX_BUTTON_X] = "X"
        keys[ControlData.KeyCode.XBOX_BUTTON_Y] = "Y"
        keys[ControlData.KeyCode.XBOX_BUTTON_LB] = "LB"
        keys[ControlData.KeyCode.XBOX_BUTTON_RB] = "RB"
        keys[ControlData.KeyCode.XBOX_TRIGGER_LEFT] = "LT"
        keys[ControlData.KeyCode.XBOX_TRIGGER_RIGHT] = "RT"
        keys[ControlData.KeyCode.XBOX_BUTTON_BACK] = "Back"
        keys[ControlData.KeyCode.XBOX_BUTTON_START] = "Start"
        keys[ControlData.KeyCode.XBOX_BUTTON_GUIDE] = "Guide"
        keys[ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK] = "L3"
        keys[ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK] = "R3"
        keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_UP] = "D-Pad ↑"
        keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN] = "D-Pad ↓"
        keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT] = "D-Pad ←"
        keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT] = "D-Pad →"

        // 常用键盘按键
        keys[ControlData.KeyCode.KEYBOARD_SPACE] = context.getString(R.string.key_space)
        keys[ControlData.KeyCode.KEYBOARD_RETURN] = context.getString(R.string.key_enter)
        keys[ControlData.KeyCode.KEYBOARD_ESCAPE] = "ESC"

        // 字母键 (完整的A-Z)
        keys[ControlData.KeyCode.KEYBOARD_A] = "A"
        keys[ControlData.KeyCode.KEYBOARD_B] = "B"
        keys[ControlData.KeyCode.KEYBOARD_C] = "C"
        keys[ControlData.KeyCode.KEYBOARD_D] = "D"
        keys[ControlData.KeyCode.KEYBOARD_E] = "E"
        keys[ControlData.KeyCode.KEYBOARD_F] = "F"
        keys[ControlData.KeyCode.KEYBOARD_G] = "G"
        keys[ControlData.KeyCode.KEYBOARD_H] = "H"
        keys[ControlData.KeyCode.KEYBOARD_I] = "I"
        keys[ControlData.KeyCode.KEYBOARD_J] = "J"
        keys[ControlData.KeyCode.KEYBOARD_K] = "K"
        keys[ControlData.KeyCode.KEYBOARD_L] = "L"
        keys[ControlData.KeyCode.KEYBOARD_M] = "M"
        keys[ControlData.KeyCode.KEYBOARD_N] = "N"
        keys[ControlData.KeyCode.KEYBOARD_O] = "O"
        keys[ControlData.KeyCode.KEYBOARD_P] = "P"
        keys[ControlData.KeyCode.KEYBOARD_Q] = "Q"
        keys[ControlData.KeyCode.KEYBOARD_R] = "R"
        keys[ControlData.KeyCode.KEYBOARD_S] = "S"
        keys[ControlData.KeyCode.KEYBOARD_T] = "T"
        keys[ControlData.KeyCode.KEYBOARD_U] = "U"
        keys[ControlData.KeyCode.KEYBOARD_V] = "V"
        keys[ControlData.KeyCode.KEYBOARD_W] = "W"
        keys[ControlData.KeyCode.KEYBOARD_X] = "X"
        keys[ControlData.KeyCode.KEYBOARD_Y] = "Y"
        keys[ControlData.KeyCode.KEYBOARD_Z] = "Z"

        // 数字键
        keys[ControlData.KeyCode.KEYBOARD_1] = "1"
        keys[ControlData.KeyCode.KEYBOARD_2] = "2"
        keys[ControlData.KeyCode.KEYBOARD_3] = "3"
        keys[ControlData.KeyCode.KEYBOARD_4] = "4"
        keys[ControlData.KeyCode.KEYBOARD_5] = "5"
        keys[ControlData.KeyCode.KEYBOARD_6] = "6"
        keys[ControlData.KeyCode.KEYBOARD_7] = "7"
        keys[ControlData.KeyCode.KEYBOARD_8] = "8"
        keys[ControlData.KeyCode.KEYBOARD_9] = "9"
        keys[ControlData.KeyCode.KEYBOARD_0] = "0"

        // 功能键 (F1-F12)
        keys[ControlData.KeyCode.KEYBOARD_F1] = "F1"
        keys[ControlData.KeyCode.KEYBOARD_F2] = "F2"
        keys[ControlData.KeyCode.KEYBOARD_F3] = "F3"
        keys[ControlData.KeyCode.KEYBOARD_F4] = "F4"
        keys[ControlData.KeyCode.KEYBOARD_F5] = "F5"
        keys[ControlData.KeyCode.KEYBOARD_F6] = "F6"
        keys[ControlData.KeyCode.KEYBOARD_F7] = "F7"
        keys[ControlData.KeyCode.KEYBOARD_F8] = "F8"
        keys[ControlData.KeyCode.KEYBOARD_F9] = "F9"
        keys[ControlData.KeyCode.KEYBOARD_F10] = "F10"
        keys[ControlData.KeyCode.KEYBOARD_F11] = "F11"
        keys[ControlData.KeyCode.KEYBOARD_F12] = "F12"

        // 修饰键 (左侧)
        keys[ControlData.KeyCode.KEYBOARD_LSHIFT] = context.getString(R.string.key_shift_left)
        keys[ControlData.KeyCode.KEYBOARD_LCTRL] = context.getString(R.string.key_ctrl_left)
        keys[ControlData.KeyCode.KEYBOARD_LALT] = context.getString(R.string.key_alt_left)

        // 修饰键 (右侧)
        keys[ControlData.KeyCode.KEYBOARD_RSHIFT] = context.getString(R.string.key_shift_right)
        keys[ControlData.KeyCode.KEYBOARD_RCTRL] = context.getString(R.string.key_ctrl_right)
        keys[ControlData.KeyCode.KEYBOARD_RALT] = context.getString(R.string.key_alt_right)

        // 其他常用键
        keys[ControlData.KeyCode.KEYBOARD_TAB] = "Tab"
        keys[ControlData.KeyCode.KEYBOARD_CAPSLOCK] = "Caps Lock"
        keys[ControlData.KeyCode.KEYBOARD_BACKSPACE] = "Backspace"
        keys[ControlData.KeyCode.KEYBOARD_DELETE] = "Delete"
        keys[ControlData.KeyCode.KEYBOARD_INSERT] = "Insert"
        keys[ControlData.KeyCode.KEYBOARD_HOME] = "Home"
        keys[ControlData.KeyCode.KEYBOARD_END] = "End"
        keys[ControlData.KeyCode.KEYBOARD_PAGEUP] = "Page Up"
        keys[ControlData.KeyCode.KEYBOARD_PAGEDOWN] = "Page Down"

        // 方向键
        keys[ControlData.KeyCode.KEYBOARD_UP] = context.getString(R.string.key_arrow_up)
        keys[ControlData.KeyCode.KEYBOARD_DOWN] = context.getString(R.string.key_arrow_down)
        keys[ControlData.KeyCode.KEYBOARD_LEFT] = context.getString(R.string.key_arrow_left)
        keys[ControlData.KeyCode.KEYBOARD_RIGHT] = context.getString(R.string.key_arrow_right)

        // 符号键
        keys[ControlData.KeyCode.KEYBOARD_MINUS] = "-"
        keys[ControlData.KeyCode.KEYBOARD_EQUALS] = "="
        keys[ControlData.KeyCode.KEYBOARD_LEFTBRACKET] = "["
        keys[ControlData.KeyCode.KEYBOARD_RIGHTBRACKET] = "]"
        keys[ControlData.KeyCode.KEYBOARD_BACKSLASH] = "\\"
        keys[ControlData.KeyCode.KEYBOARD_SEMICOLON] = ";"
        keys[ControlData.KeyCode.KEYBOARD_APOSTROPHE] = "'"
        keys[ControlData.KeyCode.KEYBOARD_GRAVE] = "`"
        keys[ControlData.KeyCode.KEYBOARD_COMMA] = ","
        keys[ControlData.KeyCode.KEYBOARD_PERIOD] = "."
        keys[ControlData.KeyCode.KEYBOARD_SLASH] = "/"

        // 小键盘数字键
        keys[ControlData.KeyCode.KEYBOARD_KP_0] = context.getString(R.string.key_numpad_0)
        keys[ControlData.KeyCode.KEYBOARD_KP_1] = context.getString(R.string.key_numpad_1)
        keys[ControlData.KeyCode.KEYBOARD_KP_2] = context.getString(R.string.key_numpad_2)
        keys[ControlData.KeyCode.KEYBOARD_KP_3] = context.getString(R.string.key_numpad_3)
        keys[ControlData.KeyCode.KEYBOARD_KP_4] = context.getString(R.string.key_numpad_4)
        keys[ControlData.KeyCode.KEYBOARD_KP_5] = context.getString(R.string.key_numpad_5)
        keys[ControlData.KeyCode.KEYBOARD_KP_6] = context.getString(R.string.key_numpad_6)
        keys[ControlData.KeyCode.KEYBOARD_KP_7] = context.getString(R.string.key_numpad_7)
        keys[ControlData.KeyCode.KEYBOARD_KP_8] = context.getString(R.string.key_numpad_8)
        keys[ControlData.KeyCode.KEYBOARD_KP_9] = context.getString(R.string.key_numpad_9)

        // 小键盘功能键
        keys[ControlData.KeyCode.KEYBOARD_KP_PLUS] = context.getString(R.string.key_numpad_plus)
        keys[ControlData.KeyCode.KEYBOARD_KP_MINUS] = context.getString(R.string.key_numpad_minus)
        keys[ControlData.KeyCode.KEYBOARD_KP_MULTIPLY] = context.getString(R.string.key_numpad_multiply)
        keys[ControlData.KeyCode.KEYBOARD_KP_DIVIDE] = context.getString(R.string.key_numpad_divide)
        keys[ControlData.KeyCode.KEYBOARD_KP_PERIOD] = context.getString(R.string.key_numpad_period)
        keys[ControlData.KeyCode.KEYBOARD_KP_ENTER] = context.getString(R.string.key_numpad_enter)

        return keys
    }
    
    /**
     * 保留旧的属性访问器以保持向后兼容性
     * 使用默认的中文字符串
     */
    @Deprecated("Use getAllKeys(context) instead for proper localization")
    val allKeys: Map<ControlData.KeyCode, String>
        get() {
            val keys: MutableMap<ControlData.KeyCode, String> = LinkedHashMap()

            // 特殊功能
            keys[ControlData.KeyCode.SPECIAL_KEYBOARD] = "键盘"
            keys[ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON] = "触摸板左右键"

            // 鼠标按键
            keys[ControlData.KeyCode.MOUSE_LEFT] = "鼠标左键"
            keys[ControlData.KeyCode.MOUSE_RIGHT] = "鼠标右键"
            keys[ControlData.KeyCode.MOUSE_MIDDLE] = "鼠标中键"
            keys[ControlData.KeyCode.MOUSE_WHEEL_UP] = "滚轮↑"
            keys[ControlData.KeyCode.MOUSE_WHEEL_DOWN] = "滚轮↓"

            // 手柄按钮
            keys[ControlData.KeyCode.XBOX_BUTTON_A] = "A"
            keys[ControlData.KeyCode.XBOX_BUTTON_B] = "B"
            keys[ControlData.KeyCode.XBOX_BUTTON_X] = "X"
            keys[ControlData.KeyCode.XBOX_BUTTON_Y] = "Y"
            keys[ControlData.KeyCode.XBOX_BUTTON_LB] = "LB"
            keys[ControlData.KeyCode.XBOX_BUTTON_RB] = "RB"
            keys[ControlData.KeyCode.XBOX_TRIGGER_LEFT] = "LT"
            keys[ControlData.KeyCode.XBOX_TRIGGER_RIGHT] = "RT"
            keys[ControlData.KeyCode.XBOX_BUTTON_BACK] = "Back"
            keys[ControlData.KeyCode.XBOX_BUTTON_START] = "Start"
            keys[ControlData.KeyCode.XBOX_BUTTON_GUIDE] = "Guide"
            keys[ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK] = "L3"
            keys[ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK] = "R3"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_UP] = "D-Pad ↑"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN] = "D-Pad ↓"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT] = "D-Pad ←"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT] = "D-Pad →"

            // 常用键盘按键
            keys[ControlData.KeyCode.KEYBOARD_SPACE] = "空格"
            keys[ControlData.KeyCode.KEYBOARD_RETURN] = "回车"
            keys[ControlData.KeyCode.KEYBOARD_ESCAPE] = "ESC"

            // 字母键 (完整的A-Z)
            for (c in 'A'..'Z') {
                val keyCode = ControlData.KeyCode.entries.find { it.name == "KEYBOARD_$c" }
                if (keyCode != null) keys[keyCode] = c.toString()
            }

            // 数字键
            for (i in 0..9) {
                val keyCode = ControlData.KeyCode.entries.find { it.name == "KEYBOARD_$i" }
                if (keyCode != null) keys[keyCode] = i.toString()
            }

            // 功能键 (F1-F12)
            for (i in 1..12) {
                val keyCode = ControlData.KeyCode.entries.find { it.name == "KEYBOARD_F$i" }
                if (keyCode != null) keys[keyCode] = "F$i"
            }

            // 修饰键 (左侧)
            keys[ControlData.KeyCode.KEYBOARD_LSHIFT] = "Shift (左)"
            keys[ControlData.KeyCode.KEYBOARD_LCTRL] = "Ctrl (左)"
            keys[ControlData.KeyCode.KEYBOARD_LALT] = "Alt (左)"

            // 修饰键 (右侧)
            keys[ControlData.KeyCode.KEYBOARD_RSHIFT] = "Shift (右)"
            keys[ControlData.KeyCode.KEYBOARD_RCTRL] = "Ctrl (右)"
            keys[ControlData.KeyCode.KEYBOARD_RALT] = "Alt (右)"

            // 其他常用键
            keys[ControlData.KeyCode.KEYBOARD_TAB] = "Tab"
            keys[ControlData.KeyCode.KEYBOARD_CAPSLOCK] = "Caps Lock"
            keys[ControlData.KeyCode.KEYBOARD_BACKSPACE] = "Backspace"
            keys[ControlData.KeyCode.KEYBOARD_DELETE] = "Delete"
            keys[ControlData.KeyCode.KEYBOARD_INSERT] = "Insert"
            keys[ControlData.KeyCode.KEYBOARD_HOME] = "Home"
            keys[ControlData.KeyCode.KEYBOARD_END] = "End"
            keys[ControlData.KeyCode.KEYBOARD_PAGEUP] = "Page Up"
            keys[ControlData.KeyCode.KEYBOARD_PAGEDOWN] = "Page Down"

            // 方向键
            keys[ControlData.KeyCode.KEYBOARD_UP] = "↑ 上"
            keys[ControlData.KeyCode.KEYBOARD_DOWN] = "↓ 下"
            keys[ControlData.KeyCode.KEYBOARD_LEFT] = "← 左"
            keys[ControlData.KeyCode.KEYBOARD_RIGHT] = "→ 右"

            // 符号键
            keys[ControlData.KeyCode.KEYBOARD_MINUS] = "-"
            keys[ControlData.KeyCode.KEYBOARD_EQUALS] = "="
            keys[ControlData.KeyCode.KEYBOARD_LEFTBRACKET] = "["
            keys[ControlData.KeyCode.KEYBOARD_RIGHTBRACKET] = "]"
            keys[ControlData.KeyCode.KEYBOARD_BACKSLASH] = "\\"
            keys[ControlData.KeyCode.KEYBOARD_SEMICOLON] = ";"
            keys[ControlData.KeyCode.KEYBOARD_APOSTROPHE] = "'"
            keys[ControlData.KeyCode.KEYBOARD_GRAVE] = "`"
            keys[ControlData.KeyCode.KEYBOARD_COMMA] = ","
            keys[ControlData.KeyCode.KEYBOARD_PERIOD] = "."
            keys[ControlData.KeyCode.KEYBOARD_SLASH] = "/"

            // 小键盘数字键
            keys[ControlData.KeyCode.KEYBOARD_KP_0] = "小键盘 0"
            keys[ControlData.KeyCode.KEYBOARD_KP_1] = "小键盘 1"
            keys[ControlData.KeyCode.KEYBOARD_KP_2] = "小键盘 2"
            keys[ControlData.KeyCode.KEYBOARD_KP_3] = "小键盘 3"
            keys[ControlData.KeyCode.KEYBOARD_KP_4] = "小键盘 4"
            keys[ControlData.KeyCode.KEYBOARD_KP_5] = "小键盘 5"
            keys[ControlData.KeyCode.KEYBOARD_KP_6] = "小键盘 6"
            keys[ControlData.KeyCode.KEYBOARD_KP_7] = "小键盘 7"
            keys[ControlData.KeyCode.KEYBOARD_KP_8] = "小键盘 8"
            keys[ControlData.KeyCode.KEYBOARD_KP_9] = "小键盘 9"

            // 小键盘功能键
            keys[ControlData.KeyCode.KEYBOARD_KP_PLUS] = "小键盘 +"
            keys[ControlData.KeyCode.KEYBOARD_KP_MINUS] = "小键盘 -"
            keys[ControlData.KeyCode.KEYBOARD_KP_MULTIPLY] = "小键盘 *"
            keys[ControlData.KeyCode.KEYBOARD_KP_DIVIDE] = "小键盘 /"
            keys[ControlData.KeyCode.KEYBOARD_KP_PERIOD] = "小键盘 ."
            keys[ControlData.KeyCode.KEYBOARD_KP_ENTER] = "小键盘 Enter"

            return keys
        }

    /**
     * 根据按键码获取按键名称（本地化版本）
     */
    fun getKeyName(context: Context, keycode: ControlData.KeyCode): String {
        return getAllKeys(context)[keycode] ?: context.getString(R.string.key_unknown, keycode.code.toString())
    }
    
    /**
     * 根据按键码获取按键名称（向后兼容版本）
     */
    @Deprecated("Use getKeyName(context, keycode) instead for proper localization")
    fun getKeyName(keycode: ControlData.KeyCode): String {
        return allKeys[keycode] ?: "未知 (${keycode.code})"
    }

    /**
     * 获取游戏常用按键（用于快速选择）
     * @param context 用于获取本地化字符串资源
     */
    fun getGameKeys(context: Context): Map<ControlData.KeyCode, String> {
        val keys: MutableMap<ControlData.KeyCode, String> = LinkedHashMap()
        keys[ControlData.KeyCode.MOUSE_LEFT] = context.getString(R.string.key_mouse_left)
        keys[ControlData.KeyCode.MOUSE_RIGHT] = context.getString(R.string.key_mouse_right)
        keys[ControlData.KeyCode.KEYBOARD_SPACE] = context.getString(R.string.key_space)
        keys[ControlData.KeyCode.KEYBOARD_E] = "E"
        keys[ControlData.KeyCode.KEYBOARD_H] = "H"
        keys[ControlData.KeyCode.KEYBOARD_ESCAPE] = "ESC"
        keys[ControlData.KeyCode.KEYBOARD_LSHIFT] = "Shift"
        keys[ControlData.KeyCode.KEYBOARD_LCTRL] = "Ctrl"
        return keys
    }
    
    /**
     * 保留旧的属性访问器以保持向后兼容性
     */
    @Deprecated("Use getGameKeys(context) instead for proper localization")
    val gameKeys: Map<ControlData.KeyCode, String>
        get() {
            val keys: MutableMap<ControlData.KeyCode, String> = LinkedHashMap()
            keys[ControlData.KeyCode.MOUSE_LEFT] = "鼠标左键"
            keys[ControlData.KeyCode.MOUSE_RIGHT] = "鼠标右键"
            keys[ControlData.KeyCode.KEYBOARD_SPACE] = "空格"
            keys[ControlData.KeyCode.KEYBOARD_E] = "E"
            keys[ControlData.KeyCode.KEYBOARD_H] = "H"
            keys[ControlData.KeyCode.KEYBOARD_ESCAPE] = "ESC"
            keys[ControlData.KeyCode.KEYBOARD_LSHIFT] = "Shift"
            keys[ControlData.KeyCode.KEYBOARD_LCTRL] = "Ctrl"
            return keys
        }

    val xboxButtons: Map<ControlData.KeyCode, String>
        /**
         * 获取手柄按钮映射（用于手柄模式按钮选择）
         */
        get() {
            val keys: MutableMap<ControlData.KeyCode, String> = LinkedHashMap()
            keys[ControlData.KeyCode.XBOX_BUTTON_A] = "A"
            keys[ControlData.KeyCode.XBOX_BUTTON_B] = "B"
            keys[ControlData.KeyCode.XBOX_BUTTON_X] = "X"
            keys[ControlData.KeyCode.XBOX_BUTTON_Y] = "Y"
            keys[ControlData.KeyCode.XBOX_BUTTON_LB] = "LB"
            keys[ControlData.KeyCode.XBOX_BUTTON_RB] = "RB"
            keys[ControlData.KeyCode.XBOX_TRIGGER_LEFT] = "LT"
            keys[ControlData.KeyCode.XBOX_TRIGGER_RIGHT] = "RT"
            keys[ControlData.KeyCode.XBOX_BUTTON_BACK] = "Back"
            keys[ControlData.KeyCode.XBOX_BUTTON_START] = "Start"
            keys[ControlData.KeyCode.XBOX_BUTTON_GUIDE] = "Guide"
            keys[ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK] = "L3"
            keys[ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK] = "R3"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_UP] = "D-Pad ↑"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN] = "D-Pad ↓"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT] = "D-Pad ←"
            keys[ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT] = "D-Pad →"
            return keys
        }
}
