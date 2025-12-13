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
            
            // 组合键已移除，忽略旧数据中的 joystickComboKeys 字段
            
            return element;
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize ControlElement", e);
        }
    }
}

