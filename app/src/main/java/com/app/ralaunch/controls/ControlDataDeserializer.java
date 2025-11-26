package com.app.ralaunch.controls;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;

/**
 * ControlData 自定义反序列化器
 * 确保文本控件的 displayText 字段正确反序列化（即使是空字符串）
 */
public class ControlDataDeserializer implements JsonDeserializer<ControlData> {
    @Override
    public ControlData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // 先使用默认的 Gson 反序列化
        ControlData data = new com.google.gson.Gson().fromJson(jsonObject, ControlData.class);
        
        // 对于文本控件，确保 displayText 正确设置
        if (data != null && data.type == ControlData.TYPE_TEXT) {
            // 如果 JSON 中有 displayText 字段，使用 JSON 中的值（即使是空字符串）
            if (jsonObject.has("displayText")) {
                JsonElement displayTextElement = jsonObject.get("displayText");
                if (displayTextElement.isJsonNull()) {
                    data.displayText = "";
                } else {
                    data.displayText = displayTextElement.getAsString();
                }
            } else {
                // 如果 JSON 中没有 displayText 字段，设置为空字符串（而不是构造函数中的默认值 "文本"）
                data.displayText = "";
            }
        }
        
        return data;
    }
}

