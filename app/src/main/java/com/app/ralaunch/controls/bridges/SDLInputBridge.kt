package com.app.ralaunch.controls.bridges

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.app.ralaunch.controls.data.ControlData
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
                Log.w(TAG, "Unknown scancode: " + scancode + ", passing through")
                return scancode // 未知的直接传递
            }
        }
    }

    override fun sendKey(scancode: ControlData.KeyCode, isDown: Boolean) {
        try {
            // 将Scancode转换为Keycode
            val keycode = scancodeToKeycode(scancode.code)


//            Log.d(TAG, "sendKey: scancode=" + scancode + " -> keycode=" + keycode +
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
                        //                        Log.d(TAG, "SDLActivity.onNativeKeyDown(" + finalKeycode + ") called successfully");
                    } else {
                        SDLActivity.onNativeKeyUp(finalKeycode)
                        //                        Log.d(TAG, "SDLActivity.onNativeKeyUp(" + finalKeycode + ") called successfully");
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in SDL native key method: keycode=" + finalKeycode, e)
                    e.printStackTrace()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sending key: scancode=" + scancode, e)
            e.printStackTrace()
        }
    }

    override fun sendMouseButton(button: ControlData.KeyCode, isDown: Boolean, x: Float, y: Float) {
        try {
            // 使用 SDL 按钮常量（1=左键, 2=中键, 3=右键）
            // 使用新的 onNativeMouseButtonOnly 方法
            val sdlButton: Int = when (button) {
                ControlData.KeyCode.MOUSE_LEFT -> 1 // SDL_BUTTON_LEFT
                ControlData.KeyCode.MOUSE_RIGHT -> 3 // SDL_BUTTON_RIGHT
                ControlData.KeyCode.MOUSE_MIDDLE -> 2 // SDL_BUTTON_MIDDLE
                else -> {
                    Log.w(TAG, "Unknown mouse button: $button")
                    return
                }
            }


            // 添加日志查看发送的值
//            Log.d(TAG, "Sending mouse button (no cursor move): button=" + button + " -> sdlButton=" + sdlButton +
//                  ", isDown=" + isDown);

            // 使用新的 onNativeMouseButtonOnly 方法，只发送按钮状态，不移动光标
            // pressed: 1=按下, 0=释放
            SDLActivity.onNativeMouseButtonOnly(sdlButton, if (isDown) 1 else 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse button: $button", e)
        }
    }

    override fun sendMouseMove(deltaX: Float, deltaY: Float) {
        try {
            // 更新虚拟鼠标位置（会应用范围限制，并在必要时重置到中心点）
            nativeUpdateVirtualMouseDeltaSDL(deltaX, deltaY)


            // 获取限制后的虚拟鼠标位置
            val newX: Float = nativeGetVirtualMouseXSDL()
            val newY: Float = nativeGetVirtualMouseYSDL()


            // 直接发送绝对位置到SDL，确保SDL的鼠标位置与虚拟鼠标位置同步
            // 这样SDL的鼠标位置就不会超出范围
            SDLActivity.onNativeMouse(0, ACTION_MOVE, newX, newY, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse move", e)
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

    override fun sendMouseWheel(scrollY: Float) {
        try {
            // 调用native方法发送鼠标滚轮事件
            nativeSendMouseWheelSDL(scrollY)
            //            Log.d(TAG, "Sending mouse wheel: scrollY=" + scrollY);
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse wheel", e)
        }
    }

    /**
     * 初始化虚拟鼠标（使用实际屏幕尺寸）
     * @param screenWidth 实际屏幕宽度（像素）
     * @param screenHeight 实际屏幕高度（像素）
     */
    fun initVirtualMouse(screenWidth: Int, screenHeight: Int) {
        try {
            nativeInitVirtualMouseSDL(screenWidth, screenHeight)
            Log.i(TAG, "Virtual mouse initialized with screen: " + screenWidth + "x" + screenHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing virtual mouse", e)
        }
    }

    val virtualMousePosition: FloatArray?
        /**
         * 获取虚拟鼠标当前位置
         * @return float数组，[0]=x, [1]=y，如果未初始化则返回屏幕中心
         */
        get() {
            try {
                val pos: FloatArray? =
                    nativeGetVirtualMousePositionSDL()
                if (pos != null && pos.size >= 2) {
                    return pos
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Error getting virtual mouse position, using screen center",
                    e
                )
            }
            // 如果获取失败，返回屏幕中心
            return floatArrayOf(960.0f, 540.0f) // 默认 1920x1080 的中心
        }

    /**
     * 设置虚拟鼠标移动范围
     * @param left 左边距（屏幕百分比 0.0-1.0）
     * @param top 上边距（屏幕百分比 0.0-1.0）
     * @param right 右边界（屏幕百分比 0.0-1.0）
     * @param bottom 下边界（屏幕百分比 0.0-1.0）
     */
    fun setVirtualMouseRange(left: Float, top: Float, right: Float, bottom: Float) {
        try {
            nativeSetVirtualMouseRangeSDL(left, top, right, bottom)
            Log.i(
                TAG,
                "Virtual mouse range set: left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting virtual mouse range", e)
        }
    }

    /**
     * 更新虚拟鼠标位置（相对移动，用于右摇杆）
     * @param deltaX X轴相对移动量
     * @param deltaY Y轴相对移动量
     * @return 返回实际移动的量（经过范围限制后的）作为 float[2] {actualDeltaX, actualDeltaY}
     */
    fun updateVirtualMouseDelta(deltaX: Float, deltaY: Float): FloatArray? {
        try {
            // 获取移动前的位置
            val oldX: Float = nativeGetVirtualMouseXSDL()
            val oldY: Float = nativeGetVirtualMouseYSDL()


            // 更新位置（native 代码会应用范围限制）
            nativeUpdateVirtualMouseDeltaSDL(deltaX, deltaY)


            // 获取移动后的位置
            val newX: Float = nativeGetVirtualMouseXSDL()
            val newY: Float = nativeGetVirtualMouseYSDL()


            // 计算实际移动的量（可能因范围限制而小于请求的移动量）
            val actualDeltaX = newX - oldX
            val actualDeltaY = newY - oldY

            return floatArrayOf(actualDeltaX, actualDeltaY)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating virtual mouse delta", e)
            // 出错时返回原始 delta
            return floatArrayOf(deltaX, deltaY)
        }
    }

    /**
     * 设置虚拟鼠标绝对位置
     * @param x 绝对X坐标
     * @param y 绝对Y坐标
     */
    fun setVirtualMousePosition(x: Float, y: Float) {
        try {
            nativeSetVirtualMousePositionSDL(x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting virtual mouse position", e)
        }
    }

    val virtualMouseX: Float
        /**
         * 获取当前虚拟鼠标X位置
         */
        get() {
            try {
                return nativeGetVirtualMouseXSDL()
            } catch (e: Exception) {
                return screenWidth / 2.0f
            }
        }

    val virtualMouseY: Float
        /**
         * 获取当前虚拟鼠标Y位置
         */
        get() {
            try {
                return nativeGetVirtualMouseYSDL()
            } catch (e: Exception) {
                return screenHeight / 2.0f
            }
        }

    /**
     * 发送绝对鼠标位置（用于右摇杆八方向攻击）
     * @param x 绝对X坐标（屏幕坐标）
     * @param y 绝对Y坐标（屏幕坐标）
     */
    override fun sendMousePosition(x: Float, y: Float) {
        try {
            // 调用SDLActivity的静态native方法，使用绝对位置（relative = false）
            SDLActivity.onNativeMouse(0, ACTION_MOVE, x, y, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse position", e)
        }
    }

    /**
     * 发送虚拟触屏点击（用于右摇杆八方向攻击）
     * 使用虚拟触屏点而不是鼠标，不会干扰真实触屏
     * @param index 虚拟触屏点索引
     * @param x 屏幕X坐标
     * @param y 屏幕Y坐标
     * @param isDown true=按下，false=释放
     */
    fun sendVirtualTouch(index: Int, x: Float, y: Float, isDown: Boolean) {
        try {
            if (isDown) {
                nativeSetVirtualTouch(index, x, y, screenWidth, screenHeight)
                //                Log.d(TAG, "Virtual touch DOWN: index=" + index + ", pos=(" + x + "," + y + ")");
            } else {
                nativeClearVirtualTouch(index)
                //                Log.d(TAG, "Virtual touch UP: index=" + index);
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not loaded for sendVirtualTouch", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending virtual touch", e)
        }
    }

    /**
     * 清除右摇杆虚拟触屏点
     */
    fun clearRightStickTouch() {
        try {
            nativeClearVirtualTouch(VIRTUAL_TOUCH_RIGHT_STICK)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing right stick virtual touch", e)
        }
    }

    override fun sendXboxLeftStick(x: Float, y: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller != null) {
                controller.setLeftStick(x, y)
            } else {
                Log.w(TAG, "Virtual Xbox controller not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Xbox left stick", e)
        }
    }

    override fun sendXboxRightStick(x: Float, y: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller != null) {
                controller.setRightStick(x, y)
            } else {
                Log.w(TAG, "Virtual Xbox controller not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Xbox right stick", e)
        }
    }

    override fun sendXboxButton(xboxButton: ControlData.KeyCode, isDown: Boolean) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller == null) {
                Log.w(TAG, "Virtual Xbox controller not available")
                return
            }

            // Map ControlData button codes to VirtualXboxController button indices
            val buttonIndex = mapXboxButtonCode(xboxButton)
            if (buttonIndex >= 0) {
                controller.setButton(buttonIndex, isDown)
            } else {
                Log.w(TAG, "Unknown Xbox button code: " + xboxButton)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Xbox button", e)
        }
    }

    override fun sendXboxTrigger(xboxTrigger: ControlData.KeyCode, value: Float) {
        try {
            val controller =
                SDLControllerManager.getVirtualController()
            if (controller == null) {
                Log.w(TAG, "Virtual Xbox controller not available")
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
                    Log.w(TAG, "Unknown Xbox trigger code: " + xboxTrigger)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Xbox trigger", e)
        }
    }

    fun startTextInput() {
        try {
            nativeStartTextInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting text input", e)
        }
    }

    fun stopTextInput() {
        try {
            nativeStopTextInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping text input", e)
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

        // Mouse actions
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1
        private const val ACTION_MOVE = 2

        // 虚拟触屏点索引（用于右摇杆八方向攻击和虚拟鼠标点击）
        // 索引 0-2 保留给普通鼠标按键，索引 3+ 用于右摇杆和虚拟鼠标
        const val VIRTUAL_TOUCH_LEFT_CLICK: Int = 0
        const val VIRTUAL_TOUCH_RIGHT_CLICK: Int = 1
        const val VIRTUAL_TOUCH_MIDDLE_CLICK: Int = 2
        const val VIRTUAL_TOUCH_RIGHT_STICK: Int = 3 // 右摇杆八方向攻击
        const val VIRTUAL_TOUCH_VIRTUAL_MOUSE: Int = 4 // 虚拟鼠标点击

        // 屏幕尺寸（用于虚拟触屏）
        private var screenWidth = 1920
        private var screenHeight = 1080

        // 虚拟触屏 Native 方法（在 touch_bridge.c 中实现，用于虚拟摇杆发送触屏事件）
        @JvmStatic
        private external fun nativeSetVirtualTouch(
            index: Int,
            x: Float,
            y: Float,
            screenWidth: Int,
            screenHeight: Int
        )

        @JvmStatic
        private external fun nativeClearVirtualTouch(index: Int)

        // SDL 原生虚拟鼠标方法（在 virtual_mouse_sdl.c 中实现）
        @JvmStatic
        private external fun nativeInitVirtualMouseSDL(screenWidth: Int, screenHeight: Int)
        @JvmStatic
        private external fun nativeUpdateVirtualMouseDeltaSDL(deltaX: Float, deltaY: Float)
        @JvmStatic
        private external fun nativeSetVirtualMousePositionSDL(x: Float, y: Float)
        @JvmStatic
        private external fun nativeGetVirtualMouseXSDL(): Float
        @JvmStatic
        private external fun nativeGetVirtualMouseYSDL(): Float
        @JvmStatic
        private external fun nativeGetVirtualMousePositionSDL(): FloatArray? // 获取虚拟鼠标位置 {x, y}
        @JvmStatic
        private external fun nativeSetVirtualMouseRangeSDL(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        )

        @JvmStatic
        private external fun nativeIsVirtualMouseActiveSDL(): Boolean
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
                Log.w(TAG, "Native library not loaded yet")
            }
        }

        /**
         * 设置屏幕尺寸（用于虚拟触屏坐标转换）
         */
        @JvmStatic
        fun setScreenSize(width: Int, height: Int) {
            screenWidth = width
            screenHeight = height
            Log.i(TAG, "Screen size set: " + width + "x" + height)
        }

        // Android mouse button states (bit masks, not SDL button values!)
        // See Android's MotionEvent class for constants
        private const val BUTTON_PRIMARY = 1 // Left button
        private const val BUTTON_SECONDARY = 2 // Right button
        private const val BUTTON_TERTIARY = 4 // Middle button
    }
}
