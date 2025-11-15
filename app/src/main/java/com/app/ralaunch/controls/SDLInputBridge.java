package com.app.ralaunch.controls;

import android.util.Log;
import org.libsdl.app.SDLActivity;

/**
 * SDL输入桥接实现
 * 将虚拟控制器输入转发到SDL
 */
public class SDLInputBridge implements ControlInputBridge {
    private static final String TAG = "SDLInputBridge";
    
    // Mouse actions
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_MOVE = 2;
    
    // Mouse buttons
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 3;
    private static final int BUTTON_MIDDLE = 2;
    
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
            
            // 调用SDLActivity的静态native方法
            if (isDown) {
                SDLActivity.onNativeKeyDown(keycode);

            } else {
                SDLActivity.onNativeKeyUp(keycode);

            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending key: " + scancode, e);
        }
    }
    
    @Override
    public void sendMouseButton(int button, boolean isDown, float x, float y) {
        try {
            int sdlButton;
            switch (button) {
                case ControlData.MOUSE_LEFT:
                    sdlButton = BUTTON_LEFT;
                    break;
                case ControlData.MOUSE_RIGHT:
                    sdlButton = BUTTON_RIGHT;
                    break;
                case ControlData.MOUSE_MIDDLE:
                    sdlButton = BUTTON_MIDDLE;
                    break;
                default:
                    Log.w(TAG, "Unknown mouse button: " + button);
                    return;
            }
            
            int action = isDown ? ACTION_DOWN : ACTION_UP;
            // 调用SDLActivity的静态native方法，传递按钮中心坐标
            SDLActivity.onNativeMouse(sdlButton, action, x, y, false);

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
