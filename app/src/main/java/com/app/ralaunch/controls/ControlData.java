package com.app.ralaunch.controls;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟控制数据模型
 * 存储单个虚拟按钮或摇杆的配置信息
 */
@Keep
public class ControlData {
    // 特殊按钮类型
    public static final int TYPE_BUTTON = 0;
    public static final int TYPE_JOYSTICK = 1;
    public static final int TYPE_TEXT = 3; // 文本控件（显示文本，不支持按键映射）
    
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
    public static final int SDL_SCANCODE_RSHIFT = 229;
    public static final int SDL_SCANCODE_LCTRL = 224;
    public static final int SDL_SCANCODE_RCTRL = 228;
    
    // 鼠标按键常量
    public static final int MOUSE_LEFT = -1;
    public static final int MOUSE_RIGHT = -2;
    public static final int MOUSE_MIDDLE = -3;
    
    // 特殊功能按键
    public static final int SPECIAL_KEYBOARD = -100; // 弹出Android键盘
    
    // 手柄按钮常量（负数范围 -200 ~ -214）
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

    // 手柄触发器常量（作为按钮使用，负数范围 -220 ~ -221）
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
    
    @SerializedName("rotation")
    public float rotation; // 旋转角度（度）
    
    @SerializedName("keycode")
    public int keycode; // SDL按键码或鼠标按键
    
    @SerializedName("opacity")
    public float opacity; // 0.0 - 1.0 (背景透明度)
    
    @SerializedName("borderOpacity")
    public float borderOpacity; // 0.0 - 1.0 (边框透明度，默认1.0)
    
    @SerializedName("textOpacity")
    public float textOpacity; // 0.0 - 1.0 (文本透明度，默认1.0)
    
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
    
    @SerializedName("passThrough")
    public boolean passThrough; // 触摸穿透：是否将触摸传递给游戏（默认 false）
    
    // 摇杆特有属性
    @SerializedName("stickOpacity")
    public float stickOpacity; // 摇杆圆心透明度 0.0 - 1.0（与背景透明度独立）
    @SerializedName("joystickKeys")
    public int[] joystickKeys; // [up, right, down, left] 的键码
    
    @SerializedName("joystickMode")
    public int joystickMode; // 0=键盘模式, 1=鼠标模式, 2=SDL控制器模式

    @SerializedName("xboxUseRightStick")
    public boolean xboxUseRightStick; // 手柄模式：true=右摇杆, false=左摇杆

    @SerializedName("rightStickContinuous")
    public boolean rightStickContinuous = true; // 右摇杆攻击模式：true=持续攻击, false=点击攻击

    // 右摇杆鼠标移动范围（屏幕百分比 0.0-1.0）
    @SerializedName("mouseRangeLeft")
    public float mouseRangeLeft = 0.0f;   // 左边距百分比
    @SerializedName("mouseRangeTop")
    public float mouseRangeTop = 0.0f;    // 上边距百分比
    @SerializedName("mouseRangeRight")
    public float mouseRangeRight = 1.0f;  // 右边界百分比
    @SerializedName("mouseRangeBottom")
    public float mouseRangeBottom = 1.0f; // 下边界百分比
    
    // 右摇杆鼠标移动速度（1-100，默认30）
    @SerializedName("mouseSpeed")
    public float mouseSpeed = 30.0f;

    // 按钮模式
    @SerializedName("buttonMode")
    public int buttonMode; // 0=键盘/鼠标模式, 1=手柄模式

    // 控件形状
    @SerializedName("shape")
    public int shape; // 0=矩形, 1=圆形（其他形状已废弃，仅用于兼容旧数据）
    
    // 形状常量
    public static final int SHAPE_RECTANGLE = 0;  // 矩形
    public static final int SHAPE_CIRCLE = 1;     // 圆形
    // 以下形状已废弃，仅用于兼容旧数据，不在选择对话框中显示
    public static final int SHAPE_CROSS = 2;      // 十字键（矩形十字）
    public static final int SHAPE_BACKGROUND_CIRCLE = 3; // 背景圈（仅背景，无内容）
    public static final int SHAPE_DOUBLE_CIRCLE = 4; // 双层圆形（RadialGamePad风格，外圈+内圈）
    public static final int SHAPE_ARROW_CROSS = 5; // 箭头十字键（RadialGamePad风格，箭头形状）
    
    @SerializedName("stickKnobSize")
    public float stickKnobSize; // 摇杆圆心大小比例 (0.0-1.0)，默认0.4，不同风格可以设置不同值
    
    // 文本控件特有属性
    @SerializedName("displayText")
    public String displayText; // 显示的文本内容

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
        this.rotation = 0; // 默认不旋转
        this.keycode = SDL_SCANCODE_UNKNOWN;
        this.opacity = 0.5f;
        this.borderOpacity = 1.0f; // 默认边框完全不透明
        this.textOpacity = 1.0f; // 默认文本完全不透明
        this.bgColor = 0xFF808080; // 灰色背景（更清晰可见）
        this.strokeColor = 0x00000000; // 透明边框（无边框）
        this.strokeWidth = 0; // 无边框宽度
        this.cornerRadius = 2; // 矩形只有一点点圆角
        this.isToggle = false;
        this.visible = true;
        this.passThrough = false; // 默认不穿透（不触发游戏点击）
        this.buttonMode = BUTTON_MODE_KEYBOARD; // 默认键盘/鼠标模式
        this.shape = SHAPE_RECTANGLE; // 默认矩形
        this.stickOpacity = 1.0f; // 默认摇杆圆心透明度（与背景透明度独立），默认完全不透明
        this.stickKnobSize = 0.4f; // 默认摇杆圆心大小（40%半径）
        this.displayText = ""; // 文本控件显示的文本内容

        if (type == TYPE_TEXT) {
            // 文本控件：默认方形（矩形），不支持按键映射
            this.shape = SHAPE_RECTANGLE; // 默认方形
            this.displayText = "文本"; // 默认文本
            this.keycode = SDL_SCANCODE_UNKNOWN; // 文本控件不支持按键映射
        } else if (type == TYPE_JOYSTICK) {
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
        this.rotation = other.rotation;
        this.keycode = other.keycode;
        this.opacity = other.opacity;
        this.borderOpacity = other.borderOpacity != 0 ? other.borderOpacity : 1.0f; // 兼容旧数据，默认1.0
        this.textOpacity = other.textOpacity != 0 ? other.textOpacity : 1.0f; // 兼容旧数据，默认1.0
        this.bgColor = other.bgColor;
        this.strokeColor = other.strokeColor;
        this.strokeWidth = other.strokeWidth;
        this.cornerRadius = other.cornerRadius;
        this.isToggle = other.isToggle;
        this.visible = other.visible;
        this.buttonMode = other.buttonMode;
        this.shape = other.shape;
        this.stickOpacity = other.stickOpacity != 0 ? other.stickOpacity : 1.0f; // 兼容旧数据，默认1.0（完全不透明）
        this.stickKnobSize = other.stickKnobSize != 0 ? other.stickKnobSize : 0.4f; // 兼容旧数据，默认0.4
        this.displayText = other.displayText != null ? other.displayText : ""; // 文本控件显示的文本
        
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
        joystick.opacity = 0.5f;
        joystick.bgColor = 0xFF808080; // 灰色背景（更清晰可见）
        joystick.strokeColor = 0x00000000; // 无边框
        joystick.strokeWidth = 0; // 无边框宽度
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
        button.strokeColor = 0x00000000; // 无边框
        button.strokeWidth = 0; // 无边框宽度
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
        button.strokeColor = 0x00000000; // 无边框
        button.strokeWidth = 0; // 无边框宽度
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
        joystick.opacity = 0.5f;
        joystick.joystickMode = JOYSTICK_MODE_MOUSE; // 鼠标移动模式
        joystick.xboxUseRightStick = true; // 右摇杆模式
        joystick.joystickKeys = null; // 鼠标模式不需要按键映射
        joystick.strokeColor = 0x00000000; // 无边框
        joystick.strokeWidth = 0; // 无边框宽度
        // 鼠标移动范围：默认全屏 (0%, 0%, 100%, 100%)
        joystick.mouseRangeLeft = 0.0f;
        joystick.mouseRangeTop = 0.0f;
        joystick.mouseRangeRight = 1.0f;
        joystick.mouseRangeBottom = 1.0f;
        return joystick;
    }

    /**
     * 创建默认手柄布局（完整的手柄按钮配置）
     * 包括：左右摇杆 + A/B/X/Y按钮 + LB/RB + LT/RT + 完整D-Pad + Start/Back
     * 适合使用手柄玩游戏的用户
     */
    public static ControlData[] createDefaultGamepadLayout() {
        ControlData[] controls = new ControlData[16];

        // 左摇杆（移动）- 使用SDL控制器模式，径向布局风格
        ControlData leftStick = new ControlData("左摇杆", TYPE_JOYSTICK);
        leftStick.x = 200;
        leftStick.y = 700;
        leftStick.width = 400;
        leftStick.height = 400;
        leftStick.opacity = 0.7f;
        leftStick.stickOpacity = 1.0f; // 摇杆圆心默认完全不透明
        leftStick.joystickMode = JOYSTICK_MODE_SDL_CONTROLLER;
        leftStick.xboxUseRightStick = false; // 使用左摇杆
        leftStick.strokeColor = 0x00000000; // 无边框
        leftStick.strokeWidth = 0; // 无边框宽度
        controls[0] = leftStick;

        // 右摇杆（瞄准）- 使用SDL控制器模式，径向布局风格
        ControlData rightStick = new ControlData("右摇杆", TYPE_JOYSTICK);
        rightStick.x = 1500;
        rightStick.y = 700;
        rightStick.width = 400;
        rightStick.height = 400;
        rightStick.opacity = 0.7f;
        rightStick.stickOpacity = 1.0f; // 摇杆圆心默认完全不透明
        rightStick.joystickMode = JOYSTICK_MODE_SDL_CONTROLLER;
        rightStick.xboxUseRightStick = true; // 使用右摇杆
        rightStick.strokeColor = 0x00000000; // 无边框
        rightStick.strokeWidth = 0; // 无边框宽度
        controls[1] = rightStick;

        // A按钮（跳跃）- 径向布局右下角，使用圆形按钮
        ControlData btnA = new ControlData("A", TYPE_BUTTON);
        btnA.x = 1920;
        btnA.y = 920;
        btnA.width = 130;
        btnA.height = 130;
        btnA.keycode = XBOX_BUTTON_A;
        btnA.buttonMode = BUTTON_MODE_GAMEPAD;
        btnA.shape = SHAPE_CIRCLE; // 圆形按钮
        btnA.strokeColor = 0x00000000; // 无边框
        btnA.strokeWidth = 0; // 无边框宽度
        controls[2] = btnA;

        // B按钮（返回/取消）- 径向布局右侧，使用圆形按钮
        ControlData btnB = new ControlData("B", TYPE_BUTTON);
        btnB.x = 2030;
        btnB.y = 820;
        btnB.width = 130;
        btnB.height = 130;
        btnB.keycode = XBOX_BUTTON_B;
        btnB.buttonMode = BUTTON_MODE_GAMEPAD;
        btnB.shape = SHAPE_CIRCLE; // 圆形按钮
        btnB.strokeColor = 0x00000000; // 无边框
        btnB.strokeWidth = 0; // 无边框宽度
        controls[3] = btnB;

        // X按钮（攻击）- 径向布局左侧，使用圆形按钮
        ControlData btnX = new ControlData("X", TYPE_BUTTON);
        btnX.x = 1810;
        btnX.y = 820;
        btnX.width = 130;
        btnX.height = 130;
        btnX.keycode = XBOX_BUTTON_X;
        btnX.buttonMode = BUTTON_MODE_GAMEPAD;
        btnX.shape = SHAPE_CIRCLE; // 圆形按钮
        btnX.strokeColor = 0x00000000; // 无边框
        btnX.strokeWidth = 0; // 无边框宽度
        controls[4] = btnX;

        // Y按钮（使用/交互）- 径向布局上方，使用圆形按钮
        ControlData btnY = new ControlData("Y", TYPE_BUTTON);
        btnY.x = 1920;
        btnY.y = 710;
        btnY.width = 130;
        btnY.height = 130;
        btnY.keycode = XBOX_BUTTON_Y;
        btnY.buttonMode = BUTTON_MODE_GAMEPAD;
        btnY.shape = SHAPE_CIRCLE; // 圆形按钮
        btnY.strokeColor = 0x00000000; // 无边框
        btnY.strokeWidth = 0; // 无边框宽度
        controls[5] = btnY;

        // LB按钮（左肩键）- 左上角
        ControlData btnLB = new ControlData("LB", TYPE_BUTTON);
        btnLB.x = 50;
        btnLB.y = 50;
        btnLB.width = 120;
        btnLB.height = 60;
        btnLB.keycode = XBOX_BUTTON_LB;
        btnLB.buttonMode = BUTTON_MODE_GAMEPAD;
        btnLB.strokeColor = 0x00000000; // 无边框
        btnLB.strokeWidth = 0; // 无边框宽度
        controls[6] = btnLB;

        // RB按钮（右肩键）- 右上角
        ControlData btnRB = new ControlData("RB", TYPE_BUTTON);
        btnRB.x = 1950;
        btnRB.y = 50;
        btnRB.width = 120;
        btnRB.height = 60;
        btnRB.keycode = XBOX_BUTTON_RB;
        btnRB.buttonMode = BUTTON_MODE_GAMEPAD;
        btnRB.strokeColor = 0x00000000; // 无边框
        btnRB.strokeWidth = 0; // 无边框宽度
        controls[7] = btnRB;

        // LT按钮（左扳机）
        ControlData btnLT = new ControlData("LT", TYPE_BUTTON);
        btnLT.x = 200;
        btnLT.y = 50;
        btnLT.width = 100;
        btnLT.height = 60;
        btnLT.keycode = XBOX_TRIGGER_LEFT;
        btnLT.buttonMode = BUTTON_MODE_GAMEPAD;
        btnLT.strokeColor = 0x00000000; // 无边框
        btnLT.strokeWidth = 0; // 无边框宽度
        controls[8] = btnLT;

        // RT按钮（右扳机）
        ControlData btnRT = new ControlData("RT", TYPE_BUTTON);
        btnRT.x = 1820;
        btnRT.y = 50;
        btnRT.width = 100;
        btnRT.height = 60;
        btnRT.keycode = XBOX_TRIGGER_RIGHT;
        btnRT.buttonMode = BUTTON_MODE_GAMEPAD;
        btnRT.strokeColor = 0x00000000; // 无边框
        btnRT.strokeWidth = 0; // 无边框宽度
        controls[9] = btnRT;

        // Start按钮（菜单）- 中上部
        ControlData btnStart = new ControlData("Start", TYPE_BUTTON);
        btnStart.x = 1100;
        btnStart.y = 50;
        btnStart.width = 80;
        btnStart.height = 60;
        btnStart.keycode = XBOX_BUTTON_START;
        btnStart.buttonMode = BUTTON_MODE_GAMEPAD;
        btnStart.strokeColor = 0x00000000; // 无边框
        btnStart.strokeWidth = 0; // 无边框宽度
        controls[10] = btnStart;

        // Back按钮 - 中上部
        ControlData btnBack = new ControlData("Back", TYPE_BUTTON);
        btnBack.x = 950;
        btnBack.y = 50;
        btnBack.width = 80;
        btnBack.height = 60;
        btnBack.keycode = XBOX_BUTTON_BACK;
        btnBack.buttonMode = BUTTON_MODE_GAMEPAD;
        btnBack.strokeColor = 0x00000000; // 无边框
        btnBack.strokeWidth = 0; // 无边框宽度
        controls[11] = btnBack;

        // D-Pad上按钮 - 径向布局风格，圆形按钮
        ControlData btnDPadUp = new ControlData("D-Up", TYPE_BUTTON);
        btnDPadUp.x = 650;
        btnDPadUp.y = 700;
        btnDPadUp.width = 90;
        btnDPadUp.height = 90;
        btnDPadUp.keycode = XBOX_BUTTON_DPAD_UP;
        btnDPadUp.buttonMode = BUTTON_MODE_GAMEPAD;
        btnDPadUp.shape = SHAPE_CIRCLE; // 圆形按钮
        btnDPadUp.strokeColor = 0x00000000; // 无边框
        btnDPadUp.strokeWidth = 0; // 无边框宽度
        controls[12] = btnDPadUp;

        // D-Pad下按钮 - 径向布局风格，圆形按钮
        ControlData btnDPadDown = new ControlData("D-Down", TYPE_BUTTON);
        btnDPadDown.x = 650;
        btnDPadDown.y = 860;
        btnDPadDown.width = 90;
        btnDPadDown.height = 90;
        btnDPadDown.keycode = XBOX_BUTTON_DPAD_DOWN;
        btnDPadDown.buttonMode = BUTTON_MODE_GAMEPAD;
        btnDPadDown.shape = SHAPE_CIRCLE; // 圆形按钮
        btnDPadDown.strokeColor = 0x00000000; // 无边框
        btnDPadDown.strokeWidth = 0; // 无边框宽度
        controls[13] = btnDPadDown;

        // D-Pad左按钮 - 径向布局风格，圆形按钮
        ControlData btnDPadLeft = new ControlData("D-Left", TYPE_BUTTON);
        btnDPadLeft.x = 560;
        btnDPadLeft.y = 780;
        btnDPadLeft.width = 90;
        btnDPadLeft.height = 90;
        btnDPadLeft.keycode = XBOX_BUTTON_DPAD_LEFT;
        btnDPadLeft.buttonMode = BUTTON_MODE_GAMEPAD;
        btnDPadLeft.shape = SHAPE_CIRCLE; // 圆形按钮
        btnDPadLeft.strokeColor = 0x00000000; // 无边框
        btnDPadLeft.strokeWidth = 0; // 无边框宽度
        controls[14] = btnDPadLeft;

        // D-Pad右按钮 - 径向布局风格，圆形按钮
        ControlData btnDPadRight = new ControlData("D-Right", TYPE_BUTTON);
        btnDPadRight.x = 740;
        btnDPadRight.y = 780;
        btnDPadRight.width = 90;
        btnDPadRight.height = 90;
        btnDPadRight.keycode = XBOX_BUTTON_DPAD_RIGHT;
        btnDPadRight.buttonMode = BUTTON_MODE_GAMEPAD;
        btnDPadRight.shape = SHAPE_CIRCLE; // 圆形按钮
        btnDPadRight.strokeColor = 0x00000000; // 无边框
        btnDPadRight.strokeWidth = 0; // 无边框宽度
        controls[15] = btnDPadRight;

        return controls;
    }
}
