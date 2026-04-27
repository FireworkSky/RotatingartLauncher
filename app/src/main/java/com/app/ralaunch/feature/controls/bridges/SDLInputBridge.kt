package com.app.ralaunch.feature.controls.bridges

import android.os.Handler
import android.os.Looper
import com.app.ralaunch.core.logging.AppLog
import android.view.KeyEvent
import android.view.MotionEvent
import com.app.ralaunch.feature.controls.ControlData
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager
import org.libsdl.app.VirtualXboxController

/**
 * SDL输入桥接实现
 * 将虚拟控制器输入转发到SDL
 *
 * 注意：游戏使用触屏控制，鼠标按键通过虚拟触屏点实现
 */
class SDLInputBridge : ControlInputBridge {
    /**
     * 将SDL Scancode转换为Android KeyCode
     * SDLActivity.onNativeKeyDown期望接收Android KeyCode（如KEYCODE_A=29），不是ASCII！
     */
    private fun scancodeToKeycode(scancode: Int): Int {
        when (scancode) {
            4 -> return KeyEvent.KEYCODE_A // 29
            5 -> return KeyEvent.KEYCODE_B // 30
            6 -> return KeyEvent.KEYCODE_C // 31
            7 -> return KeyEvent.KEYCODE_D // 32
            8 -> return KeyEvent.KEYCODE_E // 33
            9 -> return KeyEvent.KEYCODE_F // 34
            10 -> return KeyEvent.KEYCODE_G // 35
            11 -> return KeyEvent.KEYCODE_H // 36
            12 -> return KeyEvent.KEYCODE_I // 37
            13 -> return KeyEvent.KEYCODE_J // 38
            14 -> return KeyEvent.KEYCODE_K // 39
            15 -> return KeyEvent.KEYCODE_L // 40
            16 -> return KeyEvent.KEYCODE_M // 41
            17 -> return KeyEvent.KEYCODE_N // 42
            18 -> return KeyEvent.KEYCODE_O // 43
            19 -> return KeyEvent.KEYCODE_P // 44
            20 -> return KeyEvent.KEYCODE_Q // 45
            21 -> return KeyEvent.KEYCODE_R // 46
            22 -> return KeyEvent.KEYCODE_S // 47
            23 -> return KeyEvent.KEYCODE_T // 48
            24 -> return KeyEvent.KEYCODE_U // 49
            25 -> return KeyEvent.KEYCODE_V // 50
            26 -> return KeyEvent.KEYCODE_W // 51
            27 -> return KeyEvent.KEYCODE_X // 52
            28 -> return KeyEvent.KEYCODE_Y // 53
            29 -> return KeyEvent.KEYCODE_Z // 54

            30 -> return KeyEvent.KEYCODE_1 // 8
            31 -> return KeyEvent.KEYCODE_2 // 9
            32 -> return KeyEvent.KEYCODE_3 // 10
            33 -> return KeyEvent.KEYCODE_4 // 11
            34 -> return KeyEvent.KEYCODE_5 // 12
            35 -> return KeyEvent.KEYCODE_6 // 13
            36 -> return KeyEvent.KEYCODE_7 // 14
            37 -> return KeyEvent.KEYCODE_8 // 15
            38 -> return KeyEvent.KEYCODE_9 // 16
            39 -> return KeyEvent.KEYCODE_0 // 7

            40 -> return KeyEvent.KEYCODE_ENTER // 66
            41 -> return KeyEvent.KEYCODE_ESCAPE // 111
            42 -> return KeyEvent.KEYCODE_DEL // 67 (Backspace)
            43 -> return KeyEvent.KEYCODE_TAB // 61
            44 -> return KeyEvent.KEYCODE_SPACE // 62

            45 -> return KeyEvent.KEYCODE_MINUS // 69
            46 -> return KeyEvent.KEYCODE_EQUALS // 70
            47 -> return KeyEvent.KEYCODE_LEFT_BRACKET // 71
            48 -> return KeyEvent.KEYCODE_RIGHT_BRACKET // 72
            49 -> return KeyEvent.KEYCODE_BACKSLASH // 73
            51 -> return KeyEvent.KEYCODE_SEMICOLON // 74
            52 -> return KeyEvent.KEYCODE_APOSTROPHE // 75
            53 -> return KeyEvent.KEYCODE_GRAVE // 68
            54 -> return KeyEvent.KEYCODE_COMMA // 55
            55 -> return KeyEvent.KEYCODE_PERIOD // 56
            56 -> return KeyEvent.KEYCODE_SLASH // 76

            57 -> return KeyEvent.KEYCODE_CAPS_LOCK // 115

            58 -> return KeyEvent.KEYCODE_F1 // 131
            59 -> return KeyEvent.KEYCODE_F2 // 132
            60 -> return KeyEvent.KEYCODE_F3 // 133
            61 -> return KeyEvent.KEYCODE_F4 // 134
            62 -> return KeyEvent.KEYCODE_F5 // 135
            63 -> return KeyEvent.KEYCODE_F6 // 136
            64 -> return KeyEvent.KEYCODE_F7 // 137
            65 -> return KeyEvent.KEYCODE_F8 // 138
            66 -> return KeyEvent.KEYCODE_F9 // 139
            67 -> return KeyEvent.KEYCODE_F10 // 140
            68 -> return KeyEvent.KEYCODE_F11 // 141
            69 -> return KeyEvent.KEYCODE_F12 // 142

            70 -> return KeyEvent.KEYCODE_SYSRQ // 120 (PrintScreen)
            71 -> return KeyEvent.KEYCODE_SCROLL_LOCK // 116
            72 -> return KeyEvent.KEYCODE_BREAK // 121 (Pause)
            73 -> return KeyEvent.KEYCODE_INSERT // 124
            74 -> return KeyEvent.KEYCODE_HOME // 122
            75 -> return KeyEvent.KEYCODE_PAGE_UP // 92
            76 -> return KeyEvent.KEYCODE_FORWARD_DEL // 112 (Delete)
            77 -> return KeyEvent.KEYCODE_MOVE_END // 123
            78 -> return KeyEvent.KEYCODE_PAGE_DOWN // 93

            79 -> return KeyEvent.KEYCODE_DPAD_RIGHT // 22
            80 -> return KeyEvent.KEYCODE_DPAD_LEFT // 21
            81 -> return KeyEvent.KEYCODE_DPAD_DOWN // 20
            82 -> return KeyEvent.KEYCODE_DPAD_UP // 19

            83 -> return KeyEvent.KEYCODE_NUM_LOCK // 143
            84 -> return KeyEvent.KEYCODE_NUMPAD_DIVIDE // 154
            85 -> return KeyEvent.KEYCODE_NUMPAD_MULTIPLY // 155
            86 -> return KeyEvent.KEYCODE_NUMPAD_SUBTRACT // 156
            87 -> return KeyEvent.KEYCODE_NUMPAD_ADD // 157
            88 -> return KeyEvent.KEYCODE_NUMPAD_ENTER // 160
            89 -> return KeyEvent.KEYCODE_NUMPAD_1 // 145
            90 -> return KeyEvent.KEYCODE_NUMPAD_2 // 146
            91 -> return KeyEvent.KEYCODE_NUMPAD_3 // 147
            92 -> return KeyEvent.KEYCODE_NUMPAD_4 // 148
            93 -> return KeyEvent.KEYCODE_NUMPAD_5 // 149
            94 -> return KeyEvent.KEYCODE_NUMPAD_6 // 150
            95 -> return KeyEvent.KEYCODE_NUMPAD_7 // 151
            96 -> return KeyEvent.KEYCODE_NUMPAD_8 // 152
            97 -> return KeyEvent.KEYCODE_NUMPAD_9 // 153
            98 -> return KeyEvent.KEYCODE_NUMPAD_0 // 144
            99 -> return KeyEvent.KEYCODE_NUMPAD_DOT // 158

            103 -> return KeyEvent.KEYCODE_NUMPAD_EQUALS // 161

            224 -> return KeyEvent.KEYCODE_CTRL_LEFT // 113
            225 -> return KeyEvent.KEYCODE_SHIFT_LEFT // 59
            226 -> return KeyEvent.KEYCODE_ALT_LEFT // 57
            227 -> return KeyEvent.KEYCODE_META_LEFT // 117
            228 -> return KeyEvent.KEYCODE_CTRL_RIGHT // 114
            229 -> return KeyEvent.KEYCODE_SHIFT_RIGHT // 60
            230 -> return KeyEvent.KEYCODE_ALT_RIGHT // 58
            231 -> return KeyEvent.KEYCODE_META_RIGHT // 118

            else -> {
                AppLog.w(TAG, "Unknown scancode: " + scancode + ", passing through")
                return scancode // 未知的直接传递
            }
        }
    }

    override fun sendKey(scancode: ControlData.KeyCode, isDown: Boolean) {
        try {
            // 将Scancode转换为Keycode
            val keycode = scancodeToKeycode(scancode.code)


//            AppLog.d(TAG, "sendKey: scancode=" + scancode + " -> keycode=" + keycode +
//                  ", isDown=" + isDown + ", calling SDLActivity.onNativeKey" + (isDown ? "Down" : "Up"));

            // 确保在主线程上调用SDL方法（SDL的native方法需要在主线程调用）
            val mainHandler = Handler(Looper.getMainLooper())
            val finalKeycode = keycode
            val finalIsDown = isDown

            mainHandler.post(Runnable {
                try {
                    // 调用SDLActivity的静态native方法
                    if (finalIsDown) {
                        SDLActivity.onNativeKeyDown(finalKeycode)
                        //                        AppLog.d(TAG, "SDLActivity.onNativeKeyDown(" + finalKeycode + ") called successfully");
                    } else {
                        SDLActivity.onNativeKeyUp(finalKeycode)
                        //                        AppLog.d(TAG, "SDLActivity.onNativeKeyUp(" + finalKeycode + ") called successfully");
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in SDL native key method: keycode=" + finalKeycode, e)
                }
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending key: scancode=" + scancode, e)
        }
    }

    fun sdlOnNativeMouseDirect(button: Int, action: Int, x: Float, y: Float, relative: Boolean) {
        SDLActivity.onNativeMouseDirect(button, action, x, y, relative)
    }

    fun sdlNativeGetMouseStateX(): Int {
        return SDLActivity.nativeGetMouseStateX()
    }

    fun sdlNativeGetMouseStateY(): Int {
        return SDLActivity.nativeGetMouseStateY()
    }

    override fun sendMouseButton(button: ControlData.KeyCode, isDown: Boolean) {
        try {
            // 使用 SDL 按钮常量（1=左键, 2=中键, 3=右键）
            // 使用新的 onNativeMouseButtonOnly 方法
            val sdlButton: Int = when (button) {
                ControlData.KeyCode.MOUSE_LEFT -> MotionEvent.BUTTON_PRIMARY
                ControlData.KeyCode.MOUSE_RIGHT -> MotionEvent.BUTTON_SECONDARY
                ControlData.KeyCode.MOUSE_MIDDLE -> MotionEvent.BUTTON_TERTIARY
                else -> {
                    AppLog.w(TAG, "Unknown mouse button: $button")
                    return
                }
            }


            // 添加日志查看发送的值
//            AppLog.d(TAG, "Sending mouse button (no cursor move): button=" + button + " -> sdlButton=" + sdlButton +
//                  ", isDown=" + isDown);

            sdlOnNativeMouseDirect(
                sdlButton,
                if (isDown) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP,
                0f, 0f,
                true)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending mouse button: $button", e)
        }
    }

    override fun sendMousePosition(x: Float, y: Float) {
        try {
            // 调用SDLActivity的静态native方法，使用绝对位置（relative = false）
            SDLActivity.onNativeMouseDirect(0, MotionEvent.ACTION_MOVE, x, y, false)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending mouse position", e)
        }
    }

    override fun sendMouseMove(deltaX: Float, deltaY: Float) {
        try {
            SDLActivity.onNativeMouseDirect(0, MotionEvent.ACTION_MOVE, deltaX, deltaY, true)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending mouse move", e)
        }
    }

    override fun sendMouseWheel(scrollY: Float) {
        try {
            // 调用native方法发送鼠标滚轮事件
            nativeSendMouseWheelSDL(scrollY)
            //            AppLog.d(TAG, "Sending mouse wheel: scrollY=" + scrollY);
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending mouse wheel", e)
        }
    }

    override fun sendXboxLeftStick(x: Float, y: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller != null) {
                controller.setLeftStick(x, y)
            } else {
                AppLog.w(TAG, "Virtual Xbox controller not available")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending Xbox left stick", e)
        }
    }

    override fun sendXboxRightStick(x: Float, y: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller != null) {
                controller.setRightStick(x, y)
            } else {
                AppLog.w(TAG, "Virtual Xbox controller not available")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending Xbox right stick", e)
        }
    }

    override fun sendXboxButton(xboxButton: ControlData.KeyCode, isDown: Boolean) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller == null) {
                AppLog.w(TAG, "Virtual Xbox controller not available")
                return
            }

            // Map ControlData button codes to VirtualXboxController button indices
            val buttonIndex = mapXboxButtonCode(xboxButton)
            if (buttonIndex >= 0) {
                controller.setButton(buttonIndex, isDown)
            } else {
                AppLog.w(TAG, "Unknown Xbox button code: " + xboxButton)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending Xbox button", e)
        }
    }

    override fun sendXboxTrigger(xboxTrigger: ControlData.KeyCode, value: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller == null) {
                AppLog.w(TAG, "Virtual Xbox controller not available")
                return
            }

            when (xboxTrigger) {
                ControlData.KeyCode.XBOX_TRIGGER_LEFT -> {
                    controller.setAxis(VirtualXboxController.AXIS_LEFT_TRIGGER, value)
                }
                ControlData.KeyCode.XBOX_TRIGGER_RIGHT -> {
                    controller.setAxis(VirtualXboxController.AXIS_RIGHT_TRIGGER, value)
                }
                else -> {
                    AppLog.w(TAG, "Unknown Xbox trigger code: " + xboxTrigger)
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error sending Xbox trigger", e)
        }
    }

    override fun startTextInput() {
        try {
            nativeStartTextInput()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error starting text input", e)
        }
    }

    override fun stopTextInput() {
        try {
            nativeStopTextInput()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error stopping text input", e)
        }
    }

    /**
     * Map ControlData Xbox button codes to VirtualXboxController button indices
     */
    private fun mapXboxButtonCode(xboxButton: ControlData.KeyCode): Int {
        when (xboxButton) {
            ControlData.KeyCode.XBOX_BUTTON_A -> return VirtualXboxController.BUTTON_A
            ControlData.KeyCode.XBOX_BUTTON_B -> return VirtualXboxController.BUTTON_B
            ControlData.KeyCode.XBOX_BUTTON_X -> return VirtualXboxController.BUTTON_X
            ControlData.KeyCode.XBOX_BUTTON_Y -> return VirtualXboxController.BUTTON_Y
            ControlData.KeyCode.XBOX_BUTTON_BACK -> return VirtualXboxController.BUTTON_BACK
            ControlData.KeyCode.XBOX_BUTTON_GUIDE -> return VirtualXboxController.BUTTON_GUIDE
            ControlData.KeyCode.XBOX_BUTTON_START -> return VirtualXboxController.BUTTON_START
            ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK -> return VirtualXboxController.BUTTON_LEFT_STICK
            ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK -> return VirtualXboxController.BUTTON_RIGHT_STICK
            ControlData.KeyCode.XBOX_BUTTON_LB -> return VirtualXboxController.BUTTON_LEFT_SHOULDER
            ControlData.KeyCode.XBOX_BUTTON_RB -> return VirtualXboxController.BUTTON_RIGHT_SHOULDER
            ControlData.KeyCode.XBOX_BUTTON_DPAD_UP -> return VirtualXboxController.BUTTON_DPAD_UP
            ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN -> return VirtualXboxController.BUTTON_DPAD_DOWN
            ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT -> return VirtualXboxController.BUTTON_DPAD_LEFT
            ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT -> return VirtualXboxController.BUTTON_DPAD_RIGHT
            else -> return -1
        }
    }

    companion object {
        private const val TAG = "SDLInputBridge"

        // SDL 原生方法（在 sdl_input_bridge_extend.c 中实现）
        @JvmStatic
        private external fun nativeSendMouseWheelSDL(scrollY: Float)

        @JvmStatic
        private external fun nativeStartTextInput()

        @JvmStatic
        private external fun nativeStopTextInput()


        // 静态初始化
        init {
            try {
                System.loadLibrary("main")
            } catch (e: UnsatisfiedLinkError) {
                AppLog.w(TAG, "Native library not loaded yet")
            }
        }
    }
}
