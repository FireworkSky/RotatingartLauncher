package com.app.ralaunch.controls;

import android.util.Log;
import org.libsdl.app.SDLActivity;

/**
 * SDL输入桥接实现
 * 将虚拟控制器输入转发到SDL
 * 
 * 注意：游戏使用触屏控制，鼠标按键通过虚拟触屏点实现
 */
public class SDLInputBridge implements ControlInputBridge {
    private static final String TAG = "SDLInputBridge";
    
    // Mouse actions
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_MOVE = 2;
    
    // 虚拟触屏点索引（用于右摇杆八方向攻击和虚拟鼠标点击）
    // 索引 0-2 保留给普通鼠标按键，索引 3+ 用于右摇杆和虚拟鼠标
    public static final int VIRTUAL_TOUCH_LEFT_CLICK = 0;
    public static final int VIRTUAL_TOUCH_RIGHT_CLICK = 1;
    public static final int VIRTUAL_TOUCH_MIDDLE_CLICK = 2;
    public static final int VIRTUAL_TOUCH_RIGHT_STICK = 3;  // 右摇杆八方向攻击
    public static final int VIRTUAL_TOUCH_VIRTUAL_MOUSE = 4;  // 虚拟鼠标点击
    
    // 屏幕尺寸（用于虚拟触屏）
    private static int screenWidth = 1920;
    private static int screenHeight = 1080;
    
    // 虚拟触屏 Native 方法（在 touch_bridge.c 中实现，用于虚拟摇杆发送触屏事件）
    private static native void nativeSetVirtualTouch(int index, float x, float y, int screenWidth, int screenHeight);
    private static native void nativeClearVirtualTouch(int index);
    
    // SDL 原生虚拟鼠标方法（在 virtual_mouse_sdl.c 中实现）
    private static native void nativeInitVirtualMouseSDL(int screenWidth, int screenHeight);
    private static native void nativeUpdateVirtualMouseDeltaSDL(float deltaX, float deltaY);
    private static native void nativeSetVirtualMousePositionSDL(float x, float y);
    private static native float nativeGetVirtualMouseXSDL();
    private static native float nativeGetVirtualMouseYSDL();
    private static native float[] nativeGetVirtualMousePositionSDL();  // 获取虚拟鼠标位置 {x, y}
    private static native void nativeSetVirtualMouseRangeSDL(float left, float top, float right, float bottom);
    private static native boolean nativeIsVirtualMouseActiveSDL();
    private static native void nativeSendMouseWheelSDL(float scrollY);
    
    // 静态初始化
    static {
        try {
            System.loadLibrary("main");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library not loaded yet");
        }
    }
    
    /**
     * 设置屏幕尺寸（用于虚拟触屏坐标转换）
     */
    public static void setScreenSize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        Log.i(TAG, "Screen size set: " + width + "x" + height);
    }
    
    // Android mouse button states (bit masks, not SDL button values!)
    // See Android's MotionEvent class for constants
    private static final int BUTTON_PRIMARY = 1;    // Left button
    private static final int BUTTON_SECONDARY = 2;  // Right button
    private static final int BUTTON_TERTIARY = 4;   // Middle button
    
    /**
     * 将SDL Scancode转换为Android KeyCode
     * SDLActivity.onNativeKeyDown期望接收Android KeyCode（如KEYCODE_A=29），不是ASCII！
     */
    private int scancodeToKeycode(int scancode) {
        switch (scancode) {
            // 字母键 (SDL Scancode -> Android KeyCode)
            case 4: return android.view.KeyEvent.KEYCODE_A;    // 29
            case 5: return android.view.KeyEvent.KEYCODE_B;    // 30
            case 6: return android.view.KeyEvent.KEYCODE_C;    // 31
            case 7: return android.view.KeyEvent.KEYCODE_D;    // 32
            case 8: return android.view.KeyEvent.KEYCODE_E;    // 33
            case 9: return android.view.KeyEvent.KEYCODE_F;    // 34
            case 10: return android.view.KeyEvent.KEYCODE_G;   // 35
            case 11: return android.view.KeyEvent.KEYCODE_H;   // 36
            case 12: return android.view.KeyEvent.KEYCODE_I;   // 37
            case 13: return android.view.KeyEvent.KEYCODE_J;   // 38
            case 14: return android.view.KeyEvent.KEYCODE_K;   // 39
            case 15: return android.view.KeyEvent.KEYCODE_L;   // 40
            case 16: return android.view.KeyEvent.KEYCODE_M;   // 41
            case 17: return android.view.KeyEvent.KEYCODE_N;   // 42
            case 18: return android.view.KeyEvent.KEYCODE_O;   // 43
            case 19: return android.view.KeyEvent.KEYCODE_P;   // 44
            case 20: return android.view.KeyEvent.KEYCODE_Q;   // 45
            case 21: return android.view.KeyEvent.KEYCODE_R;   // 46
            case 22: return android.view.KeyEvent.KEYCODE_S;   // 47
            case 23: return android.view.KeyEvent.KEYCODE_T;   // 48
            case 24: return android.view.KeyEvent.KEYCODE_U;   // 49
            case 25: return android.view.KeyEvent.KEYCODE_V;   // 50
            case 26: return android.view.KeyEvent.KEYCODE_W;   // 51
            case 27: return android.view.KeyEvent.KEYCODE_X;   // 52
            case 28: return android.view.KeyEvent.KEYCODE_Y;   // 53
            case 29: return android.view.KeyEvent.KEYCODE_Z;   // 54
            
            // 数字键 (SDL Scancode -> Android KeyCode)
            case 30: return android.view.KeyEvent.KEYCODE_1;   // 8
            case 31: return android.view.KeyEvent.KEYCODE_2;   // 9
            case 32: return android.view.KeyEvent.KEYCODE_3;   // 10
            case 33: return android.view.KeyEvent.KEYCODE_4;   // 11
            case 34: return android.view.KeyEvent.KEYCODE_5;   // 12
            case 35: return android.view.KeyEvent.KEYCODE_6;   // 13
            case 36: return android.view.KeyEvent.KEYCODE_7;   // 14
            case 37: return android.view.KeyEvent.KEYCODE_8;   // 15
            case 38: return android.view.KeyEvent.KEYCODE_9;   // 16
            case 39: return android.view.KeyEvent.KEYCODE_0;   // 7
            
            // 特殊键
            case 40: return android.view.KeyEvent.KEYCODE_ENTER;       // 66
            case 41: return android.view.KeyEvent.KEYCODE_ESCAPE;      // 111
            case 42: return android.view.KeyEvent.KEYCODE_DEL;         // 67 (Backspace)
            case 43: return android.view.KeyEvent.KEYCODE_TAB;         // 61
            case 44: return android.view.KeyEvent.KEYCODE_SPACE;       // 62
            
            // 符号键
            case 45: return android.view.KeyEvent.KEYCODE_MINUS;       // 69
            case 46: return android.view.KeyEvent.KEYCODE_EQUALS;      // 70
            case 47: return android.view.KeyEvent.KEYCODE_LEFT_BRACKET;  // 71
            case 48: return android.view.KeyEvent.KEYCODE_RIGHT_BRACKET; // 72
            case 49: return android.view.KeyEvent.KEYCODE_BACKSLASH;   // 73
            case 51: return android.view.KeyEvent.KEYCODE_SEMICOLON;   // 74
            case 52: return android.view.KeyEvent.KEYCODE_APOSTROPHE;  // 75
            case 53: return android.view.KeyEvent.KEYCODE_GRAVE;       // 68
            case 54: return android.view.KeyEvent.KEYCODE_COMMA;       // 55
            case 55: return android.view.KeyEvent.KEYCODE_PERIOD;      // 56
            case 56: return android.view.KeyEvent.KEYCODE_SLASH;       // 76

            // 锁定键
            case 57: return android.view.KeyEvent.KEYCODE_CAPS_LOCK;   // 115

            // 功能键 F1-F12
            case 58: return android.view.KeyEvent.KEYCODE_F1;          // 131
            case 59: return android.view.KeyEvent.KEYCODE_F2;          // 132
            case 60: return android.view.KeyEvent.KEYCODE_F3;          // 133
            case 61: return android.view.KeyEvent.KEYCODE_F4;          // 134
            case 62: return android.view.KeyEvent.KEYCODE_F5;          // 135
            case 63: return android.view.KeyEvent.KEYCODE_F6;          // 136
            case 64: return android.view.KeyEvent.KEYCODE_F7;          // 137
            case 65: return android.view.KeyEvent.KEYCODE_F8;          // 138
            case 66: return android.view.KeyEvent.KEYCODE_F9;          // 139
            case 67: return android.view.KeyEvent.KEYCODE_F10;         // 140
            case 68: return android.view.KeyEvent.KEYCODE_F11;         // 141
            case 69: return android.view.KeyEvent.KEYCODE_F12;         // 142

            // 导航和编辑键
            case 70: return android.view.KeyEvent.KEYCODE_SYSRQ;       // 120 (PrintScreen)
            case 71: return android.view.KeyEvent.KEYCODE_SCROLL_LOCK; // 116
            case 72: return android.view.KeyEvent.KEYCODE_BREAK;       // 121 (Pause)
            case 73: return android.view.KeyEvent.KEYCODE_INSERT;      // 124
            case 74: return android.view.KeyEvent.KEYCODE_HOME;        // 122
            case 75: return android.view.KeyEvent.KEYCODE_PAGE_UP;     // 92
            case 76: return android.view.KeyEvent.KEYCODE_FORWARD_DEL; // 112 (Delete)
            case 77: return android.view.KeyEvent.KEYCODE_MOVE_END;    // 123
            case 78: return android.view.KeyEvent.KEYCODE_PAGE_DOWN;   // 93

            // 方向键
            case 79: return android.view.KeyEvent.KEYCODE_DPAD_RIGHT;  // 22
            case 80: return android.view.KeyEvent.KEYCODE_DPAD_LEFT;   // 21
            case 81: return android.view.KeyEvent.KEYCODE_DPAD_DOWN;   // 20
            case 82: return android.view.KeyEvent.KEYCODE_DPAD_UP;     // 19
            
            // 小键盘
            case 83: return android.view.KeyEvent.KEYCODE_NUM_LOCK;    // 143
            case 84: return android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE;   // 154
            case 85: return android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY; // 155
            case 86: return android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT; // 156
            case 87: return android.view.KeyEvent.KEYCODE_NUMPAD_ADD;      // 157
            case 88: return android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;    // 160
            case 89: return android.view.KeyEvent.KEYCODE_NUMPAD_1;        // 145
            case 90: return android.view.KeyEvent.KEYCODE_NUMPAD_2;        // 146
            case 91: return android.view.KeyEvent.KEYCODE_NUMPAD_3;        // 147
            case 92: return android.view.KeyEvent.KEYCODE_NUMPAD_4;        // 148
            case 93: return android.view.KeyEvent.KEYCODE_NUMPAD_5;        // 149
            case 94: return android.view.KeyEvent.KEYCODE_NUMPAD_6;        // 150
            case 95: return android.view.KeyEvent.KEYCODE_NUMPAD_7;        // 151
            case 96: return android.view.KeyEvent.KEYCODE_NUMPAD_8;        // 152
            case 97: return android.view.KeyEvent.KEYCODE_NUMPAD_9;        // 153
            case 98: return android.view.KeyEvent.KEYCODE_NUMPAD_0;        // 144
            case 99: return android.view.KeyEvent.KEYCODE_NUMPAD_DOT;      // 158

            // 额外的小键盘键
            case 103: return android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS;  // 161

            // 修饰键 (Modifier keys)
            case 224: return android.view.KeyEvent.KEYCODE_CTRL_LEFT;   // 113
            case 225: return android.view.KeyEvent.KEYCODE_SHIFT_LEFT;  // 59
            case 226: return android.view.KeyEvent.KEYCODE_ALT_LEFT;    // 57
            case 227: return android.view.KeyEvent.KEYCODE_META_LEFT;   // 117
            case 228: return android.view.KeyEvent.KEYCODE_CTRL_RIGHT;  // 114
            case 229: return android.view.KeyEvent.KEYCODE_SHIFT_RIGHT; // 60
            case 230: return android.view.KeyEvent.KEYCODE_ALT_RIGHT;   // 58
            case 231: return android.view.KeyEvent.KEYCODE_META_RIGHT;  // 118

            default:
                Log.w(TAG, "Unknown scancode: " + scancode + ", passing through");
                return scancode; // 未知的直接传递
        }
    }
    
    @Override
    public void sendKey(int scancode, boolean isDown) {
        try {
            // 将Scancode转换为Keycode
            int keycode = scancodeToKeycode(scancode);
            
            Log.d(TAG, "sendKey: scancode=" + scancode + " -> keycode=" + keycode + 
                  ", isDown=" + isDown + ", calling SDLActivity.onNativeKey" + (isDown ? "Down" : "Up"));
            
            // 确保在主线程上调用SDL方法（SDL的native方法需要在主线程调用）
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            final int finalKeycode = keycode;
            final boolean finalIsDown = isDown;
            
            mainHandler.post(() -> {
                try {
                    // 调用SDLActivity的静态native方法
                    if (finalIsDown) {
                        SDLActivity.onNativeKeyDown(finalKeycode);
                        Log.d(TAG, "SDLActivity.onNativeKeyDown(" + finalKeycode + ") called successfully");
                    } else {
                        SDLActivity.onNativeKeyUp(finalKeycode);
                        Log.d(TAG, "SDLActivity.onNativeKeyUp(" + finalKeycode + ") called successfully");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in SDL native key method: keycode=" + finalKeycode, e);
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending key: scancode=" + scancode, e);
            e.printStackTrace();
        }
    }
    
    @Override
    public void sendMouseButton(int button, boolean isDown, float x, float y) {
        try {
            // 使用 SDL 按钮常量（1=左键, 2=中键, 3=右键）
            // 使用新的 onNativeMouseButtonOnly 方法
           
            int sdlButton;
            switch (button) {
                case ControlData.MOUSE_LEFT:
                    sdlButton = 1; // SDL_BUTTON_LEFT
                    break;
                case ControlData.MOUSE_RIGHT:
                    sdlButton = 3; // SDL_BUTTON_RIGHT
                    break;
                case ControlData.MOUSE_MIDDLE:
                    sdlButton = 2; // SDL_BUTTON_MIDDLE
                    break;
                default:
                    Log.w(TAG, "Unknown mouse button: " + button);
                    return;
            }
            
            // 添加日志查看发送的值
            Log.d(TAG, "Sending mouse button (no cursor move): button=" + button + " -> sdlButton=" + sdlButton + 
                  ", isDown=" + isDown);
            
            // 使用新的 onNativeMouseButtonOnly 方法，只发送按钮状态，不移动光标
            // pressed: 1=按下, 0=释放
            SDLActivity.onNativeMouseButtonOnly(sdlButton, isDown ? 1 : 0);

        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse button: " + button, e);
        }
    }
    
    @Override
    public void sendMouseMove(float deltaX, float deltaY) {
        try {
            // 调用SDLActivity的静态native方法
            SDLActivity.onNativeMouse(0, ACTION_MOVE, deltaX, deltaY, true);
        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse move", e);
        }
    }
    
    @Override
    public void sendMouseWheel(float scrollY) {
        try {
            // 调用native方法发送鼠标滚轮事件
            nativeSendMouseWheelSDL(scrollY);
            Log.d(TAG, "Sending mouse wheel: scrollY=" + scrollY);
        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse wheel", e);
        }
    }
    
    /**
     * 初始化虚拟鼠标（使用实际屏幕尺寸）
     * @param screenWidth 实际屏幕宽度（像素）
     * @param screenHeight 实际屏幕高度（像素）
     */
    public void initVirtualMouse(int screenWidth, int screenHeight) {
        try {
            nativeInitVirtualMouseSDL(screenWidth, screenHeight);
            Log.i(TAG, "Virtual mouse initialized with screen: " + screenWidth + "x" + screenHeight);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing virtual mouse", e);
        }
    }
    
    /**
     * 获取虚拟鼠标当前位置
     * @return float数组，[0]=x, [1]=y，如果未初始化则返回屏幕中心
     */
    public float[] getVirtualMousePosition() {
        try {
            float[] pos = nativeGetVirtualMousePositionSDL();
            if (pos != null && pos.length >= 2) {
                return pos;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting virtual mouse position, using screen center", e);
        }
        // 如果获取失败，返回屏幕中心
        return new float[]{960.0f, 540.0f}; // 默认 1920x1080 的中心
    }
    
    /**
     * 设置虚拟鼠标移动范围
     * @param left 左边距（屏幕百分比 0.0-1.0）
     * @param top 上边距（屏幕百分比 0.0-1.0）
     * @param right 右边界（屏幕百分比 0.0-1.0）
     * @param bottom 下边界（屏幕百分比 0.0-1.0）
     */
    public void setVirtualMouseRange(float left, float top, float right, float bottom) {
        try {
            nativeSetVirtualMouseRangeSDL(left, top, right, bottom);
            Log.i(TAG, "Virtual mouse range set: left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
        } catch (Exception e) {
            Log.e(TAG, "Error setting virtual mouse range", e);
        }
    }
    
    /**
     * 更新虚拟鼠标位置（相对移动，用于右摇杆）
     * @param deltaX X轴相对移动量
     * @param deltaY Y轴相对移动量
     * @return 返回实际移动的量（经过范围限制后的）作为 float[2] {actualDeltaX, actualDeltaY}
     */
    public float[] updateVirtualMouseDelta(float deltaX, float deltaY) {
        try {
            // 获取移动前的位置
            float oldX = nativeGetVirtualMouseXSDL();
            float oldY = nativeGetVirtualMouseYSDL();
            
            // 更新位置（native 代码会应用范围限制）
            nativeUpdateVirtualMouseDeltaSDL(deltaX, deltaY);
            
            // 获取移动后的位置
            float newX = nativeGetVirtualMouseXSDL();
            float newY = nativeGetVirtualMouseYSDL();
            
            // 计算实际移动的量（可能因范围限制而小于请求的移动量）
            float actualDeltaX = newX - oldX;
            float actualDeltaY = newY - oldY;
            
            return new float[]{actualDeltaX, actualDeltaY};
        } catch (Exception e) {
            Log.e(TAG, "Error updating virtual mouse delta", e);
            // 出错时返回原始 delta
            return new float[]{deltaX, deltaY};
        }
    }
    
    /**
     * 设置虚拟鼠标绝对位置
     * @param x 绝对X坐标
     * @param y 绝对Y坐标
     */
    public void setVirtualMousePosition(float x, float y) {
        try {
            nativeSetVirtualMousePositionSDL(x, y);
        } catch (Exception e) {
            Log.e(TAG, "Error setting virtual mouse position", e);
        }
    }
    
    /**
     * 获取当前虚拟鼠标X位置
     */
    public float getVirtualMouseX() {
        try {
            return nativeGetVirtualMouseXSDL();
        } catch (Exception e) {
            return screenWidth / 2.0f;
        }
    }
    
    /**
     * 获取当前虚拟鼠标Y位置
     */
    public float getVirtualMouseY() {
        try {
            return nativeGetVirtualMouseYSDL();
        } catch (Exception e) {
            return screenHeight / 2.0f;
        }
    }
    
    /**
     * 发送绝对鼠标位置（用于右摇杆八方向攻击）
     * @param x 绝对X坐标（屏幕坐标）
     * @param y 绝对Y坐标（屏幕坐标）
     */
    public void sendMousePosition(float x, float y) {
        try {
            // 调用SDLActivity的静态native方法，使用绝对位置（relative = false）
            SDLActivity.onNativeMouse(0, ACTION_MOVE, x, y, false);
        } catch (Exception e) {
            Log.e(TAG, "Error sending mouse position", e);
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
    public void sendVirtualTouch(int index, float x, float y, boolean isDown) {
        try {
            if (isDown) {
                nativeSetVirtualTouch(index, x, y, screenWidth, screenHeight);
                Log.d(TAG, "Virtual touch DOWN: index=" + index + ", pos=(" + x + "," + y + ")");
            } else {
                nativeClearVirtualTouch(index);
                Log.d(TAG, "Virtual touch UP: index=" + index);
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library not loaded for sendVirtualTouch", e);
        } catch (Exception e) {
            Log.e(TAG, "Error sending virtual touch", e);
        }
    }
    
    /**
     * 清除右摇杆虚拟触屏点
     */
    public void clearRightStickTouch() {
        try {
            nativeClearVirtualTouch(VIRTUAL_TOUCH_RIGHT_STICK);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing right stick virtual touch", e);
        }
    }

    @Override
    public void sendXboxLeftStick(float x, float y) {
        try {
            org.libsdl.app.VirtualXboxController controller =
                org.libsdl.app.SDLControllerManager.getVirtualController();
            if (controller != null) {
                controller.setLeftStick(x, y);
            } else {
                Log.w(TAG, "Virtual Xbox controller not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending Xbox left stick", e);
        }
    }

    @Override
    public void sendXboxRightStick(float x, float y) {
        try {
            org.libsdl.app.VirtualXboxController controller =
                org.libsdl.app.SDLControllerManager.getVirtualController();
            if (controller != null) {
                controller.setRightStick(x, y);
            } else {
                Log.w(TAG, "Virtual Xbox controller not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending Xbox right stick", e);
        }
    }

    @Override
    public void sendXboxButton(int xboxButton, boolean isDown) {
        try {
            org.libsdl.app.VirtualXboxController controller =
                org.libsdl.app.SDLControllerManager.getVirtualController();
            if (controller == null) {
                Log.w(TAG, "Virtual Xbox controller not available");
                return;
            }

            // Map ControlData button codes to VirtualXboxController button indices
            int buttonIndex = mapXboxButtonCode(xboxButton);
            if (buttonIndex >= 0) {
                controller.setButton(buttonIndex, isDown);
            } else {
                Log.w(TAG, "Unknown Xbox button code: " + xboxButton);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending Xbox button", e);
        }
    }

    @Override
    public void sendXboxTrigger(int xboxTrigger, float value) {
        try {
            org.libsdl.app.VirtualXboxController controller =
                org.libsdl.app.SDLControllerManager.getVirtualController();
            if (controller == null) {
                Log.w(TAG, "Virtual Xbox controller not available");
                return;
            }

            if (xboxTrigger == ControlData.XBOX_TRIGGER_LEFT) {
                controller.setAxis(org.libsdl.app.VirtualXboxController.AXIS_LEFT_TRIGGER, value);
            } else if (xboxTrigger == ControlData.XBOX_TRIGGER_RIGHT) {
                controller.setAxis(org.libsdl.app.VirtualXboxController.AXIS_RIGHT_TRIGGER, value);
            } else {
                Log.w(TAG, "Unknown Xbox trigger code: " + xboxTrigger);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending Xbox trigger", e);
        }
    }

    /**
     * Map ControlData Xbox button codes to VirtualXboxController button indices
     */
    private int mapXboxButtonCode(int xboxButton) {
        switch (xboxButton) {
            case ControlData.XBOX_BUTTON_A: return org.libsdl.app.VirtualXboxController.BUTTON_A;
            case ControlData.XBOX_BUTTON_B: return org.libsdl.app.VirtualXboxController.BUTTON_B;
            case ControlData.XBOX_BUTTON_X: return org.libsdl.app.VirtualXboxController.BUTTON_X;
            case ControlData.XBOX_BUTTON_Y: return org.libsdl.app.VirtualXboxController.BUTTON_Y;
            case ControlData.XBOX_BUTTON_BACK: return org.libsdl.app.VirtualXboxController.BUTTON_BACK;
            case ControlData.XBOX_BUTTON_GUIDE: return org.libsdl.app.VirtualXboxController.BUTTON_GUIDE;
            case ControlData.XBOX_BUTTON_START: return org.libsdl.app.VirtualXboxController.BUTTON_START;
            case ControlData.XBOX_BUTTON_LEFT_STICK: return org.libsdl.app.VirtualXboxController.BUTTON_LEFT_STICK;
            case ControlData.XBOX_BUTTON_RIGHT_STICK: return org.libsdl.app.VirtualXboxController.BUTTON_RIGHT_STICK;
            case ControlData.XBOX_BUTTON_LB: return org.libsdl.app.VirtualXboxController.BUTTON_LEFT_SHOULDER;
            case ControlData.XBOX_BUTTON_RB: return org.libsdl.app.VirtualXboxController.BUTTON_RIGHT_SHOULDER;
            case ControlData.XBOX_BUTTON_DPAD_UP: return org.libsdl.app.VirtualXboxController.BUTTON_DPAD_UP;
            case ControlData.XBOX_BUTTON_DPAD_DOWN: return org.libsdl.app.VirtualXboxController.BUTTON_DPAD_DOWN;
            case ControlData.XBOX_BUTTON_DPAD_LEFT: return org.libsdl.app.VirtualXboxController.BUTTON_DPAD_LEFT;
            case ControlData.XBOX_BUTTON_DPAD_RIGHT: return org.libsdl.app.VirtualXboxController.BUTTON_DPAD_RIGHT;
            default: return -1;
        }
    }
    
}
