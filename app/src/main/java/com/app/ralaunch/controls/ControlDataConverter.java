package com.app.ralaunch.controls;

import android.util.DisplayMetrics;
import com.app.ralaunch.model.ControlElement;

/**
 * 控件数据转换器
 * 
 * 统一管理 ControlElement 和 ControlData 之间的转换逻辑
 * 消除代码重复，确保游戏内和编辑器使用一致的数据模型
 */
public class ControlDataConverter {
    
    /**
     * 将 ControlElement 转换为 ControlData
     * 
     * @param element 控制元素（使用相对坐标 0-1）
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return ControlData 对象（使用绝对坐标）
     */
    public static ControlData elementToData(ControlElement element, int screenWidth, int screenHeight) {
        if (element == null) {
            return null;
        }
        
        ControlData data = new ControlData();
        
        // 基本属性
        data.name = element.getName() != null ? element.getName() : "控件";
        data.x = element.getX() * screenWidth;  // 相对坐标转绝对坐标
        data.y = element.getY() * screenHeight;
        data.width = element.getWidth();
        data.height = element.getHeight();
        data.rotation = element.getRotation();
        data.opacity = element.getOpacity();
        data.stickOpacity = element.getStickOpacity() > 0 ? element.getStickOpacity() : 1.0f;
        data.stickKnobSize = element.getStickKnobSize() > 0 ? element.getStickKnobSize() : 0.4f;
        data.visible = element.getVisibility() != ControlElement.Visibility.HIDDEN;
        data.shape = element.getShape(); // 控件形状（所有类型都支持）
        
        // 根据类型设置
        ControlElement.ElementType elementType = element.getType();
        if (elementType == null) {
            // 如果类型为 null（旧数据兼容），默认为按钮
            elementType = ControlElement.ElementType.BUTTON;
        }
        
        switch (elementType) {
            case BUTTON:
                data.type = ControlData.TYPE_BUTTON;
                data.keycode = element.getKeyCode();
                data.isToggle = element.isToggle();
                
                // 使用保存的 buttonMode
                int buttonMode = element.getButtonMode();
                if (buttonMode == 0 || buttonMode == 1) {
                    data.buttonMode = buttonMode;
                } else {
                    // 兼容旧数据：根据 keycode 范围判断
                    if (data.keycode <= -200 && data.keycode >= -221) {
                        data.buttonMode = ControlData.BUTTON_MODE_GAMEPAD;
                    } else {
                        data.buttonMode = ControlData.BUTTON_MODE_KEYBOARD;
                    }
                }
                break;
                
            case JOYSTICK:
                data.type = ControlData.TYPE_JOYSTICK;
                int keyCode = element.getKeyCode();
                
                // 根据keycode判断摇杆模式
                if (keyCode == -300) {
                    // 左摇杆（SDL控制器模式）
                    data.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                    data.xboxUseRightStick = false;
                    data.joystickKeys = null; // SDL控制器模式不需要joystickKeys
                } else if (keyCode == -301) {
                    // 右摇杆（SDL控制器模式）
                    data.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                    data.xboxUseRightStick = true;
                    data.joystickKeys = null; // SDL控制器模式不需要joystickKeys
                } else {
                    // 键盘模式 - 根据keyCode推断方向键（如果keyCode是WASD之一）
                    data.joystickMode = ControlData.JOYSTICK_MODE_KEYBOARD;
                    // 默认WASD映射，如果keyCode是W/A/S/D之一，则使用它作为上键
                    int upKey = ControlData.SDL_SCANCODE_W;
                    if (keyCode == ControlData.SDL_SCANCODE_W || 
                        keyCode == ControlData.SDL_SCANCODE_A ||
                        keyCode == ControlData.SDL_SCANCODE_S ||
                        keyCode == ControlData.SDL_SCANCODE_D) {
                        upKey = keyCode;
                    }
                    // 根据上键推断其他方向（顺时针：W->D->S->A）
                    int rightKey = ControlData.SDL_SCANCODE_D;
                    int downKey = ControlData.SDL_SCANCODE_S;
                    int leftKey = ControlData.SDL_SCANCODE_A;
                    if (upKey == ControlData.SDL_SCANCODE_W) {
                        rightKey = ControlData.SDL_SCANCODE_D;
                        downKey = ControlData.SDL_SCANCODE_S;
                        leftKey = ControlData.SDL_SCANCODE_A;
                    } else if (upKey == ControlData.SDL_SCANCODE_D) {
                        rightKey = ControlData.SDL_SCANCODE_S;
                        downKey = ControlData.SDL_SCANCODE_A;
                        leftKey = ControlData.SDL_SCANCODE_W;
                    } else if (upKey == ControlData.SDL_SCANCODE_S) {
                        rightKey = ControlData.SDL_SCANCODE_A;
                        downKey = ControlData.SDL_SCANCODE_W;
                        leftKey = ControlData.SDL_SCANCODE_D;
                    } else if (upKey == ControlData.SDL_SCANCODE_A) {
                        rightKey = ControlData.SDL_SCANCODE_W;
                        downKey = ControlData.SDL_SCANCODE_D;
                        leftKey = ControlData.SDL_SCANCODE_S;
                    }
                    data.joystickKeys = new int[]{upKey, rightKey, downKey, leftKey};
                }
                break;
                
            case TEXT:
                // 文本控件（TYPE_TEXT）
                data.type = ControlData.TYPE_TEXT;
                // 确保 displayText 正确转换（即使是空字符串）
                String displayText = element.getDisplayText();
                data.displayText = displayText != null ? displayText : "";
                data.keycode = ControlData.SDL_SCANCODE_UNKNOWN; // 文本控件不支持按键映射
                // shape 会在后面的外观属性中设置，这里不需要硬编码
                break;
                
            case CROSS_KEY:
            case TRIGGER_BUTTON:
            case TOUCHPAD:
            case MOUSE_AREA:
            case MACRO_BUTTON:
            default:
                // 不支持的类型，返回 null
                return null;
        }
        
        // 外观属性
        data.bgColor = element.getBackgroundColor();
        data.strokeColor = element.getBorderColor();
        data.strokeWidth = element.getBorderWidth();
        data.cornerRadius = element.getCornerRadius();
        // shape 已经在基本属性中设置，这里不需要重复设置
        
        // 确保所有字段都有合理的默认值
        if (data.name == null) {
            data.name = "控件";
        }
        if (data.width <= 0) {
            data.width = 80;
        }
        if (data.height <= 0) {
            data.height = 80;
        }
        // 透明度：允许透明度为0（完全透明），只有当透明度未初始化（小于0）时才设置默认值
        // 注意：element.getOpacity() 的默认值是 0.7f，如果返回的值 < 0，说明可能是未初始化的值
        if (data.opacity < 0) {
            data.opacity = 0.7f;
        }
        // 如果 data.opacity >= 0（包括0），则保留原值，允许完全透明
        
        // 根据控件类型清理不需要的字段，确保导出的JSON只包含相关字段
        if (data.type == ControlData.TYPE_BUTTON || data.type == ControlData.TYPE_TEXT) {
            // 按钮和文本控件：清除摇杆特有字段
            data.stickOpacity = 0; // 设置为0，表示不使用（Gson会序列化为0，但我们可以用自定义序列化器排除）
            data.stickKnobSize = 0; // 设置为0，表示不使用
            data.joystickKeys = null; // 设置为null
            data.joystickMode = 0; // 设置为0
            data.xboxUseRightStick = false; // 设置为false
        }
        // 对于摇杆（TYPE_JOYSTICK），保留所有摇杆特有字段
        
        return data;
    }
    
    /**
     * 将 ControlData 转换为 ControlElement
     * 
     * @param data ControlData 对象（使用绝对坐标）
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return ControlElement 对象（使用相对坐标 0-1）
     */
    public static ControlElement dataToElement(ControlData data, int screenWidth, int screenHeight) {
        if (data == null || screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        
        ControlElement.ElementType type;
        
        if (data.type == ControlData.TYPE_BUTTON) {
            type = ControlElement.ElementType.BUTTON;
        } else if (data.type == ControlData.TYPE_JOYSTICK) {
            type = ControlElement.ElementType.JOYSTICK;
        } else if (data.type == ControlData.TYPE_TEXT) {
            type = ControlElement.ElementType.TEXT; // 文本控件
        } else {
            type = ControlElement.ElementType.BUTTON;
        }
        
        ControlElement element = new ControlElement(
            data.name != null ? data.name : "控件",
            type,
            data.name != null ? data.name : "控件"
        );
        
        // 位置和大小（绝对坐标转相对坐标）
        element.setX(data.x / screenWidth);
        element.setY(data.y / screenHeight);
        element.setWidth(data.width);
        element.setHeight(data.height);
        element.setRotation(data.rotation);
        element.setOpacity(data.opacity);
        // 摇杆圆心透明度只使用 stickOpacity，如果没有设置则使用默认值 1.0（完全不透明），不受 opacity 影响
        element.setStickOpacity(data.stickOpacity != 0 ? data.stickOpacity : 1.0f);
        // 摇杆圆心大小，如果没有设置则使用默认值 0.4（40%半径）
        element.setStickKnobSize(data.stickKnobSize != 0 ? data.stickKnobSize : 0.4f);
        element.setVisibility(data.visible ? ControlElement.Visibility.ALWAYS : ControlElement.Visibility.HIDDEN);
        element.setShape(data.shape); // 控件形状（所有类型都支持）
        
        // 文本控件特有属性
        if (type == ControlElement.ElementType.TEXT) {
            // 确保 displayText 正确转换（即使是空字符串）
            // 注意：必须在这里设置，因为 ControlElement 构造函数会将 displayText 初始化为 name
            String displayText = data.displayText;
            // 如果 data.displayText 为 null，说明可能是旧数据或未设置，使用空字符串
            // 如果 data.displayText 不为 null（包括空字符串），使用实际值
            element.setDisplayText(displayText != null ? displayText : "");
        }
        
        // 按键设置
        if (type == ControlElement.ElementType.BUTTON) {
            element.setKeyCode(data.keycode);
            element.setToggle(data.isToggle);
            element.setButtonMode(data.buttonMode); // 保存按钮模式
        } else if (type == ControlElement.ElementType.JOYSTICK) {
            // 摇杆模式转换
            if (data.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
                element.setKeyCode(data.xboxUseRightStick ? -301 : -300);
            } else {
                element.setKeyCode(data.joystickKeys != null && data.joystickKeys.length > 0 ? data.joystickKeys[0] : 0);
            }
        }
        
        // 外观属性
        element.setBackgroundColor(data.bgColor);
        element.setBorderColor(data.strokeColor);
        element.setBorderWidth(data.strokeWidth);
        element.setCornerRadius(data.cornerRadius);
        // shape 已经在基本属性中设置，这里不需要重复设置
        
        return element;
    }
    
    /**
     * 使用 DisplayMetrics 进行转换（便捷方法）
     */
    public static ControlData elementToData(ControlElement element, DisplayMetrics metrics) {
        return elementToData(element, metrics.widthPixels, metrics.heightPixels);
    }
    
    /**
     * 使用 DisplayMetrics 进行转换（便捷方法）
     */
    public static ControlElement dataToElement(ControlData data, DisplayMetrics metrics) {
        return dataToElement(data, metrics.widthPixels, metrics.heightPixels);
    }
}

