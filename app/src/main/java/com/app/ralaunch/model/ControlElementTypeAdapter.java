package com.app.ralaunch.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * ControlElement 自定义序列化/反序列化器
 * 处理 joystickComboKeys 从 int[][] 到 int[] 的兼容性
 */
public class ControlElementTypeAdapter implements JsonSerializer<ControlElement>, JsonDeserializer<ControlElement> {
    
    @Override
    public JsonElement serialize(ControlElement src, Type typeOfSrc, JsonSerializationContext context) {
        // 使用 ControlElement 的 toJSON 方法，然后转换为 Gson 的 JsonElement
        try {
            org.json.JSONObject jsonObj = src.toJSON();
            // 将 org.json.JSONObject 转换为 Gson 的 JsonElement
            return com.google.gson.JsonParser.parseString(jsonObj.toString());
        } catch (org.json.JSONException e) {
            throw new JsonParseException("Failed to serialize ControlElement", e);
        }
    }
    
    @Override
    public ControlElement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // 先使用 ControlElement 的 fromJSON 方法
        try {
            org.json.JSONObject jsonObj = new org.json.JSONObject(jsonObject.toString());
            ControlElement element = ControlElement.fromJSON(jsonObj);
            
            // 特殊处理 joystickComboKeys 的兼容性（如果 fromJSON 没有正确处理）
            if (element != null && element.getType() == ControlElement.ElementType.JOYSTICK) {
                if (jsonObject.has("joystickComboKeys")) {
                    JsonElement comboKeysElement = jsonObject.get("joystickComboKeys");
                    if (comboKeysElement.isJsonArray()) {
                        JsonArray comboKeysArray = comboKeysElement.getAsJsonArray();
                        if (comboKeysArray.size() > 0) {
                            // 检查是否是二维数组（旧格式）
                            JsonElement firstItem = comboKeysArray.get(0);
                            if (firstItem.isJsonArray()) {
                                // 旧格式：二维数组，转换为统一数组（使用第一个非空的组合键）
                                int[] firstNonEmpty = null;
                                for (int i = 0; i < comboKeysArray.size(); i++) {
                                    JsonArray directionArray = comboKeysArray.get(i).getAsJsonArray();
                                    if (directionArray != null && directionArray.size() > 0) {
                                        firstNonEmpty = new int[directionArray.size()];
                                        for (int j = 0; j < directionArray.size(); j++) {
                                            firstNonEmpty[j] = directionArray.get(j).getAsInt();
                                        }
                                        break;
                                    }
                                }
                                // joystickComboKeys field removed
                            } else {
                                // 新格式：一维数组
                                int[] comboKeys = new int[comboKeysArray.size()];
                                for (int i = 0; i < comboKeysArray.size(); i++) {
                                    comboKeys[i] = comboKeysArray.get(i).getAsInt();
                                }
                                // joystickComboKeys field removed
                            }
                        } else {
                              // joystickComboKeys field removed
                        }
                    }
                }
            }
            
            return element;
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize ControlElement", e);
        }
    }
}

