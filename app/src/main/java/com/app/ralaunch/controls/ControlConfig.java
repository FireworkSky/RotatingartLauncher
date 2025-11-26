package com.app.ralaunch.controls;

import androidx.annotation.Keep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 控制布局配置
 * 支持JSON序列化/反序列化
 */
@Keep
public class ControlConfig {
    @SerializedName("name")
    public String name;
    
    @SerializedName("version")
    public int version;
    
    @SerializedName("controls")
    public List<ControlData> controls;
    
    public ControlConfig() {
        this.name = "Custom Layout";
        this.version = 1;
        this.controls = new ArrayList<>();
    }
    
    /**
     * 从JSON文件加载配置
     */
    public static ControlConfig loadFromFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Config file not found: " + file.getPath());
        }
        
        try (FileReader reader = new FileReader(file)) {
            String json = new java.util.Scanner(reader).useDelimiter("\\A").next();
            return loadFromJson(json);
        }
    }
    
    /**
     * 从InputStream加载配置
     */
    public static ControlConfig loadFromStream(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader);
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                json.append(line).append("\n");
            }
            return loadFromJson(json.toString());
        }
    }
    
    /**
     * 从JSON字符串加载配置
     */
    public static ControlConfig loadFromJson(String json) {
        Gson gson = new GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(ControlData.class, new ControlDataDeserializer())
                .create();
        ControlConfig config = gson.fromJson(json, ControlConfig.class);
        // 后处理：确保文本控件的 displayText 正确设置
        if (config != null && config.controls != null) {
            for (ControlData data : config.controls) {
                if (data.type == ControlData.TYPE_TEXT) {
                    // 如果 displayText 为 null 或等于默认值 "文本"，且 JSON 中可能没有该字段，设置为空字符串
                    // 注意：这里不能简单判断是否等于 "文本"，因为用户可能真的想显示 "文本"
                    // 所以只有在 displayText 为 null 时才设置为空字符串
                    if (data.displayText == null) {
                        data.displayText = "";
                    }
                }
            }
        }
        return config;
    }
    
    /**
     * 保存配置到JSON文件（格式化输出）
     */
    public void saveToFile(File file) throws IOException {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = createGson();
            gson.toJson(this, writer);
        }
    }
    
    /**
     * 转换为JSON字符串（格式化输出）
     */
    public String toJson() {
        Gson gson = createGson();
        return gson.toJson(this);
    }
    
    /**
     * 创建配置了自定义序列化器的 Gson 实例
     */
    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .registerTypeAdapter(ControlData.class, new ControlDataSerializer())
                .create();
    }
}
