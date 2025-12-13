package com.app.ralaunch.model;

import android.graphics.Color;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 控制元素数据模型
 * 
 * 表示游戏控制布局中的单个控制元素，支持：
 * - 多种元素类型（按钮、摇杆、十字键、触摸板等）
 * - 位置和大小设置
 * - 按键映射和宏配置
 * - 可见性和透明度
 * - JSON 序列化和反序列化
 * 
 * 用于创建和编辑自定义游戏控制方案
 */
public class ControlElement {
    public enum ElementType {
        BUTTON,          // 普通按钮
        JOYSTICK,        // 虚拟摇杆
        CROSS_KEY,       // 十字键
        TRIGGER_BUTTON,  // 扳机键（L/R）
        TOUCHPAD,        // 触摸板（模拟鼠标）
        MOUSE_AREA,      // 鼠标区域
        MACRO_BUTTON,    // 宏按钮
        TEXT             // 文本控件
    }

    public enum Visibility {
        ALWAYS,          // 始终显示
        IN_GAME,         // 仅游戏中
        IN_MENU,         // 仅菜单中
        HIDDEN           // 隐藏
    }

    private String id;
    private ElementType type;
    private String name;
    private String displayText;  // 显示文本
    private String iconPath;     // 图标路径

    // 位置和大小（相对坐标 0-1）
    private float x;
    private float y;
    private float width;
    private float height;
    private float rotation;      // 旋转角度
    private float cornerRadius;  // 圆角半径
    private int shape;           // 控件形状 0=矩形, 1=圆形

    // 键位绑定
    private int keyCode;
    private int keyCode2;        // 组合键
    private String macroSequence; // 宏序列

    // 行为设置
    private boolean toggle;
    private boolean passthrough;
    private boolean swipeClick;
    private boolean repeatEnabled; // 连按
    private int repeatDelay;      // 连按延迟ms
    private int buttonMode;       // 按钮模式 0=键盘/鼠标模式, 1=手柄模式

    // 外观设置
    private int backgroundColor;
    private int pressedColor;     // 按下时颜色
    private int borderColor;
    private int textColor;
    private float borderWidth;
    private float opacity;
    private float borderOpacity;  // 边框透明度 0.0 - 1.0（默认1.0）
    private float textOpacity;    // 文本透明度 0.0 - 1.0（默认1.0）
    private float textSize;
    private float stickOpacity;   // 摇杆头透明度（仅摇杆类型）
    private float stickKnobSize;  // 摇杆圆心大小比例 (0.0-1.0)，默认0.4

    // 显示设置
    private Visibility visibility;
    private boolean dynamicPosition; // 动态跟随手指
    private boolean snapToEdge;      // 吸附到边缘

    // 摇杆特有属性
    private float deadzone;          // 死区
    private float sensitivity;       // 灵敏度
    private boolean lockDirection;   // 锁定方向（4/8方向）
    private int joystickMode;        // 摇杆模式 0=键盘模式, 1=鼠标模式, 2=SDL控制器模式
    private boolean xboxUseRightStick; // 手柄模式：true=右摇杆, false=左摇杆
    private boolean rightStickContinuous = true; // 右摇杆攻击模式：true=持续攻击, false=点击攻击
    
    // 鼠标移动范围（屏幕百分比 0.0-1.0）
    private float mouseRangeLeft = 0.0f;   // 左边距百分比
    private float mouseRangeTop = 0.0f;    // 上边距百分比
    private float mouseRangeRight = 1.0f;  // 右边界百分比
    private float mouseRangeBottom = 1.0f; // 下边界百分比
    
    // 鼠标移动速度（1-100，默认30）
    private float mouseSpeed = 30.0f;

    // 触摸板特有属性
    private float scrollSensitivity;
    private boolean invertX;
    private boolean invertY;

    public ControlElement(String id, ElementType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.displayText = name;

        // 默认值
        this.width = 80;
        this.height = 80;
        this.rotation = 0;
        this.cornerRadius = 2; // 矩形只有一点点圆角
        this.shape = 0; // 默认矩形 (0=矩形, 1=圆形)

        this.backgroundColor = Color.argb(180, 128, 128, 128); // 灰色背景（更清晰可见）
        this.pressedColor = Color.argb(220, 160, 160, 160);
        this.borderColor = Color.TRANSPARENT; // 默认无边框
        this.textColor = Color.WHITE;
        this.borderWidth = 0.0f; // 默认无边框宽度
        this.opacity = 0.5f;
        this.borderOpacity = 1.0f; // 默认边框完全不透明
        this.textOpacity = 1.0f; // 默认文本完全不透明
        this.textSize = 16f;
        this.stickOpacity = 0.7f; // 默认摇杆头透明度
        this.stickKnobSize = 0.4f; // 默认摇杆圆心大小（40%半径）

        this.visibility = Visibility.ALWAYS;
        this.repeatDelay = 100;
        this.buttonMode = 0; // 默认键盘/鼠标模式

        // 摇杆默认值
        this.deadzone = 0.1f;
        this.sensitivity = 1.0f;
        this.lockDirection = false;
        this.joystickMode = 0; // 默认键盘模式
        this.xboxUseRightStick = false; // 默认左摇杆
        this.scrollSensitivity = 1.0f;
    }

    // 从 JSON 创建
    public static ControlElement fromJSON(JSONObject json) throws JSONException {
        String id = json.getString("id");
        String typeString = json.getString("type");
        ElementType type;
        try {
            type = ElementType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据：如果类型是 "GROUP"（已移除的组合手柄），转换为 TEXT
            if ("GROUP".equals(typeString)) {
                type = ElementType.TEXT;
            } else {
                // 其他未知类型，默认为 BUTTON
                type = ElementType.BUTTON;
            }
        }
        String name = json.getString("name");

        ControlElement element = new ControlElement(id, type, name);

        // 基本属性
        if (json.has("displayText")) {
            // 确保 displayText 正确加载（即使是空字符串）
            // 注意：即使值为空字符串，也要正确设置，不要使用默认值
            String displayTextValue = json.getString("displayText");
            element.displayText = displayTextValue != null ? displayTextValue : "";
        } else {
            // 如果没有 displayText 字段，对于文本控件使用空字符串，其他控件使用 name
            if (type == ElementType.TEXT) {
                element.displayText = ""; // 文本控件允许空文本
            } else {
                element.displayText = name; // 其他控件使用 name 作为默认值
            }
        }
        if (json.has("iconPath")) element.iconPath = json.getString("iconPath");

        // 位置和大小
        if (json.has("x")) element.x = (float) json.getDouble("x");
        if (json.has("y")) element.y = (float) json.getDouble("y");
        if (json.has("width")) element.width = (float) json.getDouble("width");
        if (json.has("height")) element.height = (float) json.getDouble("height");
        if (json.has("rotation")) element.rotation = (float) json.getDouble("rotation");
        if (json.has("cornerRadius")) element.cornerRadius = (float) json.getDouble("cornerRadius");
        // shape 字段：如果存在则读取，否则使用默认值 0（矩形）
        if (json.has("shape")) {
            element.shape = json.getInt("shape");
        } else {
            element.shape = 0; // 默认矩形
        }

        // 键位绑定
        if (json.has("keyCode")) element.keyCode = json.getInt("keyCode");
        if (json.has("keyCode2")) element.keyCode2 = json.getInt("keyCode2");
        if (json.has("macroSequence")) element.macroSequence = json.getString("macroSequence");

        // 行为设置
        if (json.has("toggle")) element.toggle = json.getBoolean("toggle");
        if (json.has("passthrough")) element.passthrough = json.getBoolean("passthrough");
        if (json.has("swipeClick")) element.swipeClick = json.getBoolean("swipeClick");
        if (json.has("repeatEnabled")) element.repeatEnabled = json.getBoolean("repeatEnabled");
        if (json.has("repeatDelay")) element.repeatDelay = json.getInt("repeatDelay");
        if (json.has("buttonMode")) element.buttonMode = json.getInt("buttonMode");

        // 外观设置
        if (json.has("backgroundColor")) element.backgroundColor = json.getInt("backgroundColor");
        if (json.has("pressedColor")) element.pressedColor = json.getInt("pressedColor");
        if (json.has("borderColor")) element.borderColor = json.getInt("borderColor");
        if (json.has("textColor")) element.textColor = json.getInt("textColor");
        if (json.has("borderWidth")) element.borderWidth = (float) json.getDouble("borderWidth");
        if (json.has("opacity")) element.opacity = (float) json.getDouble("opacity");
        if (json.has("borderOpacity")) {
            element.borderOpacity = (float) json.getDouble("borderOpacity");
        } else {
            element.borderOpacity = 1.0f; // 兼容旧数据，默认1.0
        }
        if (json.has("textOpacity")) {
            element.textOpacity = (float) json.getDouble("textOpacity");
        } else {
            element.textOpacity = 1.0f; // 兼容旧数据，默认1.0
        }
        if (json.has("textSize")) element.textSize = (float) json.getDouble("textSize");
        if (json.has("stickOpacity")) element.stickOpacity = (float) json.getDouble("stickOpacity");
        if (json.has("stickKnobSize")) element.stickKnobSize = (float) json.getDouble("stickKnobSize");

        // 显示设置
        if (json.has("visibility")) element.visibility = Visibility.valueOf(json.getString("visibility"));
        if (json.has("dynamicPosition")) element.dynamicPosition = json.getBoolean("dynamicPosition");
        if (json.has("snapToEdge")) element.snapToEdge = json.getBoolean("snapToEdge");

        // 摇杆特有
        if (json.has("deadzone")) element.deadzone = (float) json.getDouble("deadzone");
        if (json.has("sensitivity")) element.sensitivity = (float) json.getDouble("sensitivity");
        if (json.has("lockDirection")) element.lockDirection = json.getBoolean("lockDirection");
        if (json.has("joystickMode")) element.joystickMode = json.getInt("joystickMode");
        if (json.has("xboxUseRightStick")) element.xboxUseRightStick = json.getBoolean("xboxUseRightStick");
        if (json.has("rightStickContinuous")) {
            element.rightStickContinuous = json.getBoolean("rightStickContinuous");
        } else {
            element.rightStickContinuous = true; // 默认持续攻击
        }
        
        // 鼠标移动范围
        if (json.has("mouseRangeLeft")) element.mouseRangeLeft = (float) json.getDouble("mouseRangeLeft");
        if (json.has("mouseRangeTop")) element.mouseRangeTop = (float) json.getDouble("mouseRangeTop");
        if (json.has("mouseRangeRight")) element.mouseRangeRight = (float) json.getDouble("mouseRangeRight");
        if (json.has("mouseRangeBottom")) element.mouseRangeBottom = (float) json.getDouble("mouseRangeBottom");
        if (json.has("mouseSpeed")) element.mouseSpeed = (float) json.getDouble("mouseSpeed");

        // 触摸板特有
        if (json.has("scrollSensitivity")) element.scrollSensitivity = (float) json.getDouble("scrollSensitivity");
        if (json.has("invertX")) element.invertX = json.getBoolean("invertX");
        if (json.has("invertY")) element.invertY = json.getBoolean("invertY");

        return element;
    }

    // 转换为 JSON
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("id", id);
        json.put("type", type.name());
        json.put("name", name);
        // 确保 displayText 始终保存（即使是空字符串或 null）
        json.put("displayText", displayText != null ? displayText : "");
        if (iconPath != null) json.put("iconPath", iconPath);

        // 位置和大小
        json.put("x", x);
        json.put("y", y);
        json.put("width", width);
        json.put("height", height);
        json.put("rotation", rotation);
        json.put("cornerRadius", cornerRadius);
        json.put("shape", shape);

        // 键位绑定
        json.put("keyCode", keyCode);
        json.put("keyCode2", keyCode2);
        if (macroSequence != null) json.put("macroSequence", macroSequence);

        // 行为设置
        json.put("toggle", toggle);
        json.put("passthrough", passthrough);
        json.put("swipeClick", swipeClick);
        json.put("repeatEnabled", repeatEnabled);
        json.put("repeatDelay", repeatDelay);
        json.put("buttonMode", buttonMode);

        // 外观设置
        json.put("backgroundColor", backgroundColor);
        json.put("pressedColor", pressedColor);
        json.put("borderColor", borderColor);
        json.put("textColor", textColor);
        json.put("borderWidth", borderWidth);
        json.put("opacity", opacity);
        json.put("borderOpacity", borderOpacity);
        json.put("textOpacity", textOpacity);
        json.put("textSize", textSize);
        json.put("stickOpacity", stickOpacity);
        json.put("stickKnobSize", stickKnobSize);

        // 显示设置
        json.put("visibility", visibility.name());
        json.put("dynamicPosition", dynamicPosition);
        json.put("snapToEdge", snapToEdge);

        // 摇杆特有
        json.put("deadzone", deadzone);
        json.put("sensitivity", sensitivity);
        json.put("lockDirection", lockDirection);
        if (type == ElementType.JOYSTICK) {
            json.put("joystickMode", joystickMode);
            json.put("xboxUseRightStick", xboxUseRightStick);
            json.put("rightStickContinuous", rightStickContinuous);
            // 鼠标移动范围
            json.put("mouseRangeLeft", mouseRangeLeft);
            json.put("mouseRangeTop", mouseRangeTop);
            json.put("mouseRangeRight", mouseRangeRight);
            json.put("mouseRangeBottom", mouseRangeBottom);
            json.put("mouseSpeed", mouseSpeed);
        }

        // 触摸板特有
        json.put("scrollSensitivity", scrollSensitivity);
        json.put("invertX", invertX);
        json.put("invertY", invertY);

        return json;
    }

    // 复制元素
    public ControlElement copy() {
        try {
            return fromJSON(toJSON());
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ElementType getType() { return type; }
    public void setType(ElementType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public String getIconPath() { return iconPath; }
    public void setIconPath(String iconPath) { this.iconPath = iconPath; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }

    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }

    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation; }

    public float getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(float cornerRadius) { this.cornerRadius = cornerRadius; }
    
    public int getShape() { return shape; }
    public void setShape(int shape) { this.shape = shape; }

    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public int getKeyCode2() { return keyCode2; }
    public void setKeyCode2(int keyCode2) { this.keyCode2 = keyCode2; }

    public String getMacroSequence() { return macroSequence; }
    public void setMacroSequence(String macroSequence) { this.macroSequence = macroSequence; }

    public boolean isToggle() { return toggle; }
    public void setToggle(boolean toggle) { this.toggle = toggle; }

    public boolean isPassthrough() { return passthrough; }
    public void setPassthrough(boolean passthrough) { this.passthrough = passthrough; }

    public boolean isSwipeClick() { return swipeClick; }
    public void setSwipeClick(boolean swipeClick) { this.swipeClick = swipeClick; }

    public boolean isRepeatEnabled() { return repeatEnabled; }
    public void setRepeatEnabled(boolean repeatEnabled) { this.repeatEnabled = repeatEnabled; }

    public int getRepeatDelay() { return repeatDelay; }
    public void setRepeatDelay(int repeatDelay) { this.repeatDelay = repeatDelay; }
    
    public int getButtonMode() { return buttonMode; }
    public void setButtonMode(int buttonMode) { this.buttonMode = buttonMode; }

    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { this.backgroundColor = backgroundColor; }

    public int getPressedColor() { return pressedColor; }
    public void setPressedColor(int pressedColor) { this.pressedColor = pressedColor; }

    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int borderColor) { this.borderColor = borderColor; }

    public int getTextColor() { return textColor; }
    public void setTextColor(int textColor) { this.textColor = textColor; }

    public float getBorderWidth() { return borderWidth; }
    public void setBorderWidth(float borderWidth) { this.borderWidth = borderWidth; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = opacity; }
    
    public float getBorderOpacity() { return borderOpacity; }
    public void setBorderOpacity(float borderOpacity) { this.borderOpacity = borderOpacity; }
    
    public float getTextOpacity() { return textOpacity; }
    public void setTextOpacity(float textOpacity) { this.textOpacity = textOpacity; }
    
    public float getStickOpacity() { return stickOpacity; }
    public void setStickOpacity(float stickOpacity) { this.stickOpacity = stickOpacity; }
    
    public float getStickKnobSize() { return stickKnobSize; }
    public void setStickKnobSize(float stickKnobSize) { this.stickKnobSize = stickKnobSize; }

    public float getTextSize() { return textSize; }
    public void setTextSize(float textSize) { this.textSize = textSize; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public boolean isDynamicPosition() { return dynamicPosition; }
    public void setDynamicPosition(boolean dynamicPosition) { this.dynamicPosition = dynamicPosition; }

    public boolean isSnapToEdge() { return snapToEdge; }
    public void setSnapToEdge(boolean snapToEdge) { this.snapToEdge = snapToEdge; }

    public float getDeadzone() { return deadzone; }
    public void setDeadzone(float deadzone) { this.deadzone = deadzone; }

    public float getSensitivity() { return sensitivity; }
    public void setSensitivity(float sensitivity) { this.sensitivity = sensitivity; }

    public boolean isLockDirection() { return lockDirection; }
    public void setLockDirection(boolean lockDirection) { this.lockDirection = lockDirection; }

    public float getScrollSensitivity() { return scrollSensitivity; }
    public void setScrollSensitivity(float scrollSensitivity) { this.scrollSensitivity = scrollSensitivity; }

    public boolean isInvertX() { return invertX; }
    public void setInvertX(boolean invertX) { this.invertX = invertX; }

    public boolean isInvertY() { return invertY; }
    public void setInvertY(boolean invertY) { this.invertY = invertY; }
    
    public int getJoystickMode() { return joystickMode; }
    public void setJoystickMode(int joystickMode) { this.joystickMode = joystickMode; }
    
    public boolean isXboxUseRightStick() { return xboxUseRightStick; }
    public void setXboxUseRightStick(boolean xboxUseRightStick) { this.xboxUseRightStick = xboxUseRightStick; }
    
    public boolean isRightStickContinuous() { return rightStickContinuous; }
    public void setRightStickContinuous(boolean rightStickContinuous) { this.rightStickContinuous = rightStickContinuous; }
    
    // 鼠标移动范围
    public float getMouseRangeLeft() { return mouseRangeLeft; }
    public void setMouseRangeLeft(float mouseRangeLeft) { this.mouseRangeLeft = mouseRangeLeft; }
    public float getMouseRangeTop() { return mouseRangeTop; }
    public void setMouseRangeTop(float mouseRangeTop) { this.mouseRangeTop = mouseRangeTop; }
    public float getMouseRangeRight() { return mouseRangeRight; }
    public void setMouseRangeRight(float mouseRangeRight) { this.mouseRangeRight = mouseRangeRight; }
    public float getMouseRangeBottom() { return mouseRangeBottom; }
    public void setMouseRangeBottom(float mouseRangeBottom) { this.mouseRangeBottom = mouseRangeBottom; }
    
    // 鼠标移动速度
    public float getMouseSpeed() { return mouseSpeed; }
    public void setMouseSpeed(float mouseSpeed) { this.mouseSpeed = mouseSpeed; }
}