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

    // 摇杆模式常量
    public static final int JOYSTICK_MODE_KEYBOARD = 0;    // 键盘按键模式（WASD等）
    public static final int JOYSTICK_MODE_MOUSE = 1;       // 鼠标移动模式（瞄准）
    public static final int JOYSTICK_MODE_SDL_CONTROLLER = 2; // SDL虚拟控制器模式（真实摇杆）

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
}
