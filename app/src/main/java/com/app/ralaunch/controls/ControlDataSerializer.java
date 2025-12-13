package com.app.ralaunch.controls;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * ControlData 自定义序列化器
 * 根据控件类型决定是否序列化摇杆特有字段
 */
public class ControlDataSerializer implements JsonSerializer<ControlData> {
    @Override
    public JsonElement serialize(ControlData src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        // 基本字段（所有类型都包含）
        jsonObject.addProperty("name", src.name);
        jsonObject.addProperty("type", src.type);
        jsonObject.addProperty("x", src.x);
        jsonObject.addProperty("y", src.y);
        jsonObject.addProperty("width", src.width);
        jsonObject.addProperty("height", src.height);
        jsonObject.addProperty("rotation", src.rotation);
        jsonObject.addProperty("opacity", src.opacity);
        jsonObject.addProperty("borderOpacity", src.borderOpacity);
        jsonObject.addProperty("textOpacity", src.textOpacity);
        jsonObject.addProperty("bgColor", src.bgColor);
        jsonObject.addProperty("strokeColor", src.strokeColor);
        jsonObject.addProperty("strokeWidth", src.strokeWidth);
        jsonObject.addProperty("cornerRadius", src.cornerRadius);
        jsonObject.addProperty("visible", src.visible);
        jsonObject.addProperty("passThrough", src.passThrough);
        jsonObject.addProperty("shape", src.shape);
        
        // 根据类型添加特定字段
        if (src.type == ControlData.TYPE_BUTTON) {
            // 按钮特有字段
            jsonObject.addProperty("keycode", src.keycode);
            jsonObject.addProperty("isToggle", src.isToggle);
            jsonObject.addProperty("buttonMode", src.buttonMode);
        } else if (src.type == ControlData.TYPE_JOYSTICK) {
            // 摇杆特有字段
            jsonObject.addProperty("stickOpacity", src.stickOpacity);
            jsonObject.addProperty("stickKnobSize", src.stickKnobSize);
            jsonObject.addProperty("joystickMode", src.joystickMode);
            jsonObject.addProperty("xboxUseRightStick", src.xboxUseRightStick);
            jsonObject.addProperty("rightStickContinuous", src.rightStickContinuous);
            jsonObject.addProperty("mouseRangeLeft", src.mouseRangeLeft);
            jsonObject.addProperty("mouseRangeTop", src.mouseRangeTop);
            jsonObject.addProperty("mouseRangeRight", src.mouseRangeRight);
            jsonObject.addProperty("mouseRangeBottom", src.mouseRangeBottom);
            jsonObject.addProperty("mouseSpeed", src.mouseSpeed);
            if (src.joystickKeys != null) {
                jsonObject.add("joystickKeys", context.serialize(src.joystickKeys));
            }
            // joystickComboKeys field removed
        } else if (src.type == ControlData.TYPE_TEXT) {
            // 文本控件特有字段
            jsonObject.addProperty("displayText", src.displayText != null ? src.displayText : "");
        }
        
        return jsonObject;
    }
}

