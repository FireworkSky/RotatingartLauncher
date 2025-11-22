package com.app.ralaunch.controls;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;

/**
 * 虚拟控制数据模型
 * 存储单个虚拟按钮或摇杆的配置信息
 */
@Keep
public class ControlData {
    // 特殊按钮类型
    public static final int TYPE_BUTTON = 0;
    public static final int TYPE_JOYSTICK = 1;
    
    // SDL Scancode常量 (不是ASCII码！)
    // 参考：SDL_scancode.h
    public static final int SDL_SCANCODE_UNKNOWN = 0;
    public static final int SDL_SCANCODE_A = 4;
    public static final int SDL_SCANCODE_D = 7;
    public static final int SDL_SCANCODE_E = 8;
    public static final int SDL_SCANCODE_H = 11;
    public static final int SDL_SCANCODE_S = 22;
    public static final int SDL_SCANCODE_W = 26;
    public static final int SDL_SCANCODE_SPACE = 44;
    public static final int SDL_SCANCODE_ESCAPE = 41;
    public static final int SDL_SCANCODE_RETURN = 40;
    public static final int SDL_SCANCODE_LSHIFT = 225;
    public static final int SDL_SCANCODE_LCTRL = 224;
    
    // 鼠标按键常量
    public static final int MOUSE_LEFT = -1;
    public static final int MOUSE_RIGHT = -2;
    public static final int MOUSE_MIDDLE = -3;
    
    // 特殊功能按键
    public static final int SPECIAL_KEYBOARD = -100; // 弹出Android键盘
    
    // Xbox控制器按钮常量（负数范围 -200 ~ -214）
    public static final int XBOX_BUTTON_A = -200;
    public static final int XBOX_BUTTON_B = -201;
    public static final int XBOX_BUTTON_X = -202;
    public static final int XBOX_BUTTON_Y = -203;
    public static final int XBOX_BUTTON_BACK = -204;
    public static final int XBOX_BUTTON_GUIDE = -205;
    public static final int XBOX_BUTTON_START = -206;
    public static final int XBOX_BUTTON_LEFT_STICK = -207;
    public static final int XBOX_BUTTON_RIGHT_STICK = -208;
    public static final int XBOX_BUTTON_LB = -209;  // Left Shoulder/Bumper
    public static final int XBOX_BUTTON_RB = -210;  // Right Shoulder/Bumper
    public static final int XBOX_BUTTON_DPAD_UP = -211;
    public static final int XBOX_BUTTON_DPAD_DOWN = -212;
    public static final int XBOX_BUTTON_DPAD_LEFT = -213;
    public static final int XBOX_BUTTON_DPAD_RIGHT = -214;

    // Xbox控制器触发器常量（作为按钮使用，负数范围 -220 ~ -221）
    public static final int XBOX_TRIGGER_LEFT = -220;   // Left Trigger (0.0 = 释放, 1.0 = 按下)
    public static final int XBOX_TRIGGER_RIGHT = -221;  // Right Trigger (0.0 = 释放, 1.0 = 按下)

    @SerializedName("name")
    public String name;
    
    @SerializedName("type")
    public int type; // TYPE_BUTTON or TYPE_JOYSTICK
    
    @SerializedName("x")
    public float x; // 屏幕位置 (0-1相对值或绝对像素值)
    
    @SerializedName("y")
    public float y;
    
    @SerializedName("width")
    public float width; // dp单位
    
    @SerializedName("height")
    public float height; // dp单位
    
    @SerializedName("keycode")
    public int keycode; // SDL按键码或鼠标按键
    
    @SerializedName("opacity")
    public float opacity; // 0.0 - 1.0
    
    @SerializedName("bgColor")
    public int bgColor;
    
    @SerializedName("strokeColor")
    public int strokeColor;
    
    @SerializedName("strokeWidth")
    public float strokeWidth; // dp单位
    
    @SerializedName("cornerRadius")
    public float cornerRadius; // dp单位
    
    @SerializedName("isToggle")
    public boolean isToggle; // 是否是切换按钮（按下保持状态）
    
    @SerializedName("visible")
    public boolean visible;
    
    // 摇杆特有属性
    @SerializedName("joystickKeys")
    public int[] joystickKeys; // [up, right, down, left] 的键码
    
    @SerializedName("joystickMode")
    public int joystickMode; // 0=键盘模式, 1=鼠标模式, 2=SDL控制器模式

    @SerializedName("xboxUseRightStick")
    public boolean xboxUseRightStick; // Xbox控制器模式：true=右摇杆, false=左摇杆

    // 按钮模式
    @SerializedName("buttonMode")
    public int buttonMode; // 0=键盘/鼠标模式, 1=手柄模式

    // 摇杆模式常量
    public static final int JOYSTICK_MODE_KEYBOARD = 0;    // 键盘按键模式（WASD等）
    public static final int JOYSTICK_MODE_MOUSE = 1;       // 鼠标移动模式（瞄准）
    public static final int JOYSTICK_MODE_SDL_CONTROLLER = 2; // SDL虚拟控制器模式（真实摇杆）

    // 按钮模式常量
    public static final int BUTTON_MODE_KEYBOARD = 0;    // 键盘/鼠标按键模式
    public static final int BUTTON_MODE_GAMEPAD = 1;      // 手柄按键模式

    public ControlData() {
        this("Button", TYPE_BUTTON);
    }
    
    public ControlData(String name, int type) {
        this.name = name;
        this.type = type;
        this.x = 100;
        this.y = 100;
        this.width = 80;
        this.height = 80;
        this.keycode = SDL_SCANCODE_UNKNOWN;
        this.opacity = 0.7f;
        this.bgColor = 0x80000000; // 半透明黑色
        this.strokeColor = 0xFFFFFFFF; // 白色边框
        this.strokeWidth = 2;
        this.cornerRadius = 8;
        this.isToggle = false;
        this.visible = true;
        this.buttonMode = BUTTON_MODE_KEYBOARD; // 默认键盘/鼠标模式

        if (type == TYPE_JOYSTICK) {
            // 默认WASD映射 (使用SDL Scancode)
            this.joystickKeys = new int[]{
                SDL_SCANCODE_W,  // up
                SDL_SCANCODE_D,  // right
                SDL_SCANCODE_S,  // down
                SDL_SCANCODE_A   // left
            };
            this.joystickMode = JOYSTICK_MODE_KEYBOARD; // 默认键盘模式
            this.xboxUseRightStick = false; // 默认左摇杆
        }
    }
    
    /**
     * 深拷贝构造函数
     */
    public ControlData(ControlData other) {
        this.name = other.name;
        this.type = other.type;
        this.x = other.x;
        this.y = other.y;
        this.width = other.width;
        this.height = other.height;
        this.keycode = other.keycode;
        this.opacity = other.opacity;
        this.bgColor = other.bgColor;
        this.strokeColor = other.strokeColor;
        this.strokeWidth = other.strokeWidth;
        this.cornerRadius = other.cornerRadius;
        this.isToggle = other.isToggle;
        this.visible = other.visible;
        
        if (other.joystickKeys != null) {
            this.joystickKeys = other.joystickKeys.clone();
        }
        this.joystickMode = other.joystickMode;
        this.xboxUseRightStick = other.xboxUseRightStick;
        this.buttonMode = other.buttonMode;
    }
    
    /**
     * 创建默认摇杆配置
     * 优化尺寸：450x450 提升操作舒适度
     */
    public static ControlData createDefaultJoystick() {
        ControlData joystick = new ControlData("移动摇杆", TYPE_JOYSTICK);
        joystick.x = 80;
        joystick.y = 650;
        joystick.width = 450;
        joystick.height = 450;
        joystick.opacity = 0.7f;
        return joystick;
    }
    
    /**
     * 创建默认跳跃按钮
     */
    public static ControlData createDefaultJumpButton() {
        ControlData button = new ControlData("跳跃", TYPE_BUTTON);
        button.x = 1800;
        button.y = 900;
        button.width = 120;
        button.height = 120;
        button.keycode = SDL_SCANCODE_SPACE;
        return button;
    }
    
    /**
     * 创建默认攻击按钮（鼠标左键）
     */
    public static ControlData createDefaultAttackButton() {
        ControlData button = new ControlData("攻击", TYPE_BUTTON);
        button.x = 1950;
        button.y = 800;
        button.width = 120;
        button.height = 120;
        button.keycode = MOUSE_LEFT;
        return button;
    }
    
    /**
     * 创建默认右摇杆（瞄准/攻击方向控制）
     * 鼠标移动模式，用于控制攻击方向
     */
    public static ControlData createDefaultAttackJoystick() {
        ControlData joystick = new ControlData("瞄准摇杆", TYPE_JOYSTICK);
        joystick.x = 1650;
        joystick.y = 650;
        joystick.width = 450;
        joystick.height = 450;
        joystick.opacity = 0.7f;
        joystick.joystickMode = JOYSTICK_MODE_MOUSE; // 鼠标移动模式
        joystick.joystickKeys = null; // 鼠标模式不需要按键映射
        return joystick;
    }

    /**
     * 创建默认手柄布局（完整的Xbox手柄按钮配置）
     * 包括：左右摇杆 + A/B/X/Y按钮 + LB/RB + LT/RT + 完整D-Pad + Start/Back
     * 适合使用手柄玩游戏的用户
     */
    public static ControlData[] createDefaultGamepadLayout() {
        ControlData[] controls = new ControlData[16];

        // 左摇杆（移动）- 使用SDL控制器模式
        ControlData leftStick = new ControlData("左摇杆", TYPE_JOYSTICK);
        leftStick.x = 80;
        leftStick.y = 650;
        leftStick.width = 450;
        leftStick.height = 450;
        leftStick.opacity = 0.7f;
        leftStick.joystickMode = JOYSTICK_MODE_SDL_CONTROLLER;
        leftStick.xboxUseRightStick = false; // 使用左摇杆
        controls[0] = leftStick;

        // 右摇杆（瞄准）- 使用SDL控制器模式
        ControlData rightStick = new ControlData("右摇杆", TYPE_JOYSTICK);
        rightStick.x = 1650;
        rightStick.y = 650;
        rightStick.width = 450;
        rightStick.height = 450;
        rightStick.opacity = 0.7f;
        rightStick.joystickMode = JOYSTICK_MODE_SDL_CONTROLLER;
        rightStick.xboxUseRightStick = true; // 使用右摇杆
        controls[1] = rightStick;

        // A按钮（跳跃）- 右下角
        ControlData btnA = new ControlData("A", TYPE_BUTTON);
        btnA.x = 1900;
        btnA.y = 900;
        btnA.width = 100;
        btnA.height = 100;
        btnA.keycode = XBOX_BUTTON_A;
        btnA.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[2] = btnA;

        // B按钮（返回/取消）- 右侧
        ControlData btnB = new ControlData("B", TYPE_BUTTON);
        btnB.x = 2000;
        btnB.y = 800;
        btnB.width = 100;
        btnB.height = 100;
        btnB.keycode = XBOX_BUTTON_B;
        btnB.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[3] = btnB;

        // X按钮（攻击）- 左侧
        ControlData btnX = new ControlData("X", TYPE_BUTTON);
        btnX.x = 1800;
        btnX.y = 800;
        btnX.width = 100;
        btnX.height = 100;
        btnX.keycode = XBOX_BUTTON_X;
        btnX.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[4] = btnX;

        // Y按钮（使用/交互）- 上方
        ControlData btnY = new ControlData("Y", TYPE_BUTTON);
        btnY.x = 1900;
        btnY.y = 700;
        btnY.width = 100;
        btnY.height = 100;
        btnY.keycode = XBOX_BUTTON_Y;
        btnY.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[5] = btnY;

        // LB按钮（左肩键）- 左上角
        ControlData btnLB = new ControlData("LB", TYPE_BUTTON);
        btnLB.x = 50;
        btnLB.y = 50;
        btnLB.width = 120;
        btnLB.height = 60;
        btnLB.keycode = XBOX_BUTTON_LB;
        btnLB.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[6] = btnLB;

        // RB按钮（右肩键）- 右上角
        ControlData btnRB = new ControlData("RB", TYPE_BUTTON);
        btnRB.x = 1950;
        btnRB.y = 50;
        btnRB.width = 120;
        btnRB.height = 60;
        btnRB.keycode = XBOX_BUTTON_RB;
        btnRB.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[7] = btnRB;

        // LT按钮（左扳机）
        ControlData btnLT = new ControlData("LT", TYPE_BUTTON);
        btnLT.x = 200;
        btnLT.y = 50;
        btnLT.width = 100;
        btnLT.height = 60;
        btnLT.keycode = XBOX_TRIGGER_LEFT;
        btnLT.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[8] = btnLT;

        // RT按钮（右扳机）
        ControlData btnRT = new ControlData("RT", TYPE_BUTTON);
        btnRT.x = 1820;
        btnRT.y = 50;
        btnRT.width = 100;
        btnRT.height = 60;
        btnRT.keycode = XBOX_TRIGGER_RIGHT;
        btnRT.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[9] = btnRT;

        // Start按钮（菜单）- 中上部
        ControlData btnStart = new ControlData("Start", TYPE_BUTTON);
        btnStart.x = 1100;
        btnStart.y = 50;
        btnStart.width = 80;
        btnStart.height = 60;
        btnStart.keycode = XBOX_BUTTON_START;
        btnStart.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[10] = btnStart;

        // Back按钮 - 中上部
        ControlData btnBack = new ControlData("Back", TYPE_BUTTON);
        btnBack.x = 950;
        btnBack.y = 50;
        btnBack.width = 80;
        btnBack.height = 60;
        btnBack.keycode = XBOX_BUTTON_BACK;
        btnBack.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[11] = btnBack;

        // D-Pad上按钮
        ControlData btnDPadUp = new ControlData("D-Up", TYPE_BUTTON);
        btnDPadUp.x = 650;
        btnDPadUp.y = 700;
        btnDPadUp.width = 80;
        btnDPadUp.height = 80;
        btnDPadUp.keycode = XBOX_BUTTON_DPAD_UP;
        btnDPadUp.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[12] = btnDPadUp;

        // D-Pad下按钮
        ControlData btnDPadDown = new ControlData("D-Down", TYPE_BUTTON);
        btnDPadDown.x = 650;
        btnDPadDown.y = 860;
        btnDPadDown.width = 80;
        btnDPadDown.height = 80;
        btnDPadDown.keycode = XBOX_BUTTON_DPAD_DOWN;
        btnDPadDown.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[13] = btnDPadDown;

        // D-Pad左按钮
        ControlData btnDPadLeft = new ControlData("D-Left", TYPE_BUTTON);
        btnDPadLeft.x = 570;
        btnDPadLeft.y = 780;
        btnDPadLeft.width = 80;
        btnDPadLeft.height = 80;
        btnDPadLeft.keycode = XBOX_BUTTON_DPAD_LEFT;
        btnDPadLeft.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[14] = btnDPadLeft;

        // D-Pad右按钮
        ControlData btnDPadRight = new ControlData("D-Right", TYPE_BUTTON);
        btnDPadRight.x = 730;
        btnDPadRight.y = 780;
        btnDPadRight.width = 80;
        btnDPadRight.height = 80;
        btnDPadRight.keycode = XBOX_BUTTON_DPAD_RIGHT;
        btnDPadRight.buttonMode = BUTTON_MODE_GAMEPAD;
        controls[15] = btnDPadRight;

        return controls;
    }
}
