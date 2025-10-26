package com.app.ralaunch.model;

import android.graphics.Color;

public class ControlElement {
    public enum ElementType {
        BUTTON, JOYSTICK, CROSS_KEY, SPECIAL
    }

    private String id;
    private ElementType type;
    private String name;
    private float x;
    private float y;
    private float width;
    private float height;
    private int keyCode;
    private int keyCode2; // 组合键
    private boolean toggle;
    private boolean passthrough;
    private boolean swipeClick;
    private int backgroundColor;
    private int borderColor;
    private float borderWidth;
    private float opacity;
    private int visibility; // 0:游戏中, 1:始终, 2:隐藏

    public ControlElement(String id, ElementType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.backgroundColor = Color.argb(128, 255, 255, 255);
        this.borderColor = Color.WHITE;
        this.borderWidth = 2.0f;
        this.opacity = 0.7f;
        this.visibility = 0;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ElementType getType() { return type; }
    public void setType(ElementType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }

    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }

    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public int getKeyCode2() { return keyCode2; }
    public void setKeyCode2(int keyCode2) { this.keyCode2 = keyCode2; }

    public boolean isToggle() { return toggle; }
    public void setToggle(boolean toggle) { this.toggle = toggle; }

    public boolean isPassthrough() { return passthrough; }
    public void setPassthrough(boolean passthrough) { this.passthrough = passthrough; }

    public boolean isSwipeClick() { return swipeClick; }
    public void setSwipeClick(boolean swipeClick) { this.swipeClick = swipeClick; }

    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { this.backgroundColor = backgroundColor; }

    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int borderColor) { this.borderColor = borderColor; }

    public float getBorderWidth() { return borderWidth; }
    public void setBorderWidth(float borderWidth) { this.borderWidth = borderWidth; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = opacity; }

    public int getVisibility() { return visibility; }
    public void setVisibility(int visibility) { this.visibility = visibility; }
}