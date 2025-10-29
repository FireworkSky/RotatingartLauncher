package com.app.ralaunch.model;

import android.graphics.Color;
import org.json.JSONException;
import org.json.JSONObject;

public class ControlElement {
    public enum ElementType {
        BUTTON,          // 普通按钮
        JOYSTICK,        // 虚拟摇杆
        CROSS_KEY,       // 十字键
        TRIGGER_BUTTON,  // 扳机键（L/R）
        TOUCHPAD,        // 触摸板（模拟鼠标）
        MOUSE_AREA,      // 鼠标区域
        MACRO_BUTTON,    // 宏按钮
        GROUP            // 元素组
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

    // 外观设置
    private int backgroundColor;
    private int pressedColor;     // 按下时颜色
    private int borderColor;
    private int textColor;
    private float borderWidth;
    private float opacity;
    private float textSize;

    // 显示设置
    private Visibility visibility;
    private boolean dynamicPosition; // 动态跟随手指
    private boolean snapToEdge;      // 吸附到边缘

    // 摇杆特有属性
    private float deadzone;          // 死区
    private float sensitivity;       // 灵敏度
    private boolean lockDirection;   // 锁定方向（4/8方向）

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
        this.cornerRadius = 10;

        this.backgroundColor = Color.argb(128, 100, 100, 100);
        this.pressedColor = Color.argb(180, 150, 150, 150);
        this.borderColor = Color.WHITE;
        this.textColor = Color.WHITE;
        this.borderWidth = 2.0f;
        this.opacity = 0.7f;
        this.textSize = 16f;

        this.visibility = Visibility.ALWAYS;
        this.repeatDelay = 100;

        // 摇杆默认值
        this.deadzone = 0.1f;
        this.sensitivity = 1.0f;
        this.scrollSensitivity = 1.0f;
    }

    // 从 JSON 创建
    public static ControlElement fromJSON(JSONObject json) throws JSONException {
        String id = json.getString("id");
        ElementType type = ElementType.valueOf(json.getString("type"));
        String name = json.getString("name");

        ControlElement element = new ControlElement(id, type, name);

        // 基本属性
        if (json.has("displayText")) element.displayText = json.getString("displayText");
        if (json.has("iconPath")) element.iconPath = json.getString("iconPath");

        // 位置和大小
        if (json.has("x")) element.x = (float) json.getDouble("x");
        if (json.has("y")) element.y = (float) json.getDouble("y");
        if (json.has("width")) element.width = (float) json.getDouble("width");
        if (json.has("height")) element.height = (float) json.getDouble("height");
        if (json.has("rotation")) element.rotation = (float) json.getDouble("rotation");
        if (json.has("cornerRadius")) element.cornerRadius = (float) json.getDouble("cornerRadius");

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

        // 外观设置
        if (json.has("backgroundColor")) element.backgroundColor = json.getInt("backgroundColor");
        if (json.has("pressedColor")) element.pressedColor = json.getInt("pressedColor");
        if (json.has("borderColor")) element.borderColor = json.getInt("borderColor");
        if (json.has("textColor")) element.textColor = json.getInt("textColor");
        if (json.has("borderWidth")) element.borderWidth = (float) json.getDouble("borderWidth");
        if (json.has("opacity")) element.opacity = (float) json.getDouble("opacity");
        if (json.has("textSize")) element.textSize = (float) json.getDouble("textSize");

        // 显示设置
        if (json.has("visibility")) element.visibility = Visibility.valueOf(json.getString("visibility"));
        if (json.has("dynamicPosition")) element.dynamicPosition = json.getBoolean("dynamicPosition");
        if (json.has("snapToEdge")) element.snapToEdge = json.getBoolean("snapToEdge");

        // 摇杆特有
        if (json.has("deadzone")) element.deadzone = (float) json.getDouble("deadzone");
        if (json.has("sensitivity")) element.sensitivity = (float) json.getDouble("sensitivity");
        if (json.has("lockDirection")) element.lockDirection = json.getBoolean("lockDirection");

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
        json.put("displayText", displayText);
        if (iconPath != null) json.put("iconPath", iconPath);

        // 位置和大小
        json.put("x", x);
        json.put("y", y);
        json.put("width", width);
        json.put("height", height);
        json.put("rotation", rotation);
        json.put("cornerRadius", cornerRadius);

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

        // 外观设置
        json.put("backgroundColor", backgroundColor);
        json.put("pressedColor", pressedColor);
        json.put("borderColor", borderColor);
        json.put("textColor", textColor);
        json.put("borderWidth", borderWidth);
        json.put("opacity", opacity);
        json.put("textSize", textSize);

        // 显示设置
        json.put("visibility", visibility.name());
        json.put("dynamicPosition", dynamicPosition);
        json.put("snapToEdge", snapToEdge);

        // 摇杆特有
        json.put("deadzone", deadzone);
        json.put("sensitivity", sensitivity);
        json.put("lockDirection", lockDirection);

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
}