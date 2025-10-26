// ControlLayout.java
package com.app.ralaunch.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ControlLayout {
    private String name;
    private String description;
    private String author;
    private int version;
    private List<ControlElement> elements;
    
    // 布局元数据
    private boolean isDefault;
    private long createdTime;
    private long modifiedTime;
    private String screenshotPath;

    public ControlLayout(String name) {
        this.name = name;
        this.elements = new ArrayList<>();
        this.version = 1;
        this.createdTime = System.currentTimeMillis();
        this.modifiedTime = this.createdTime;
    }

    // 从 JSON 文件加载
    public static ControlLayout fromFile(File file) throws IOException, JSONException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder jsonString = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            jsonString.append(line);
        }
        reader.close();
        
        return fromJSON(new JSONObject(jsonString.toString()));
    }

    // 从 JSON 对象创建
    public static ControlLayout fromJSON(JSONObject json) throws JSONException {
        String name = json.getString("name");
        ControlLayout layout = new ControlLayout(name);
        
        if (json.has("description")) layout.description = json.getString("description");
        if (json.has("author")) layout.author = json.getString("author");
        if (json.has("version")) layout.version = json.getInt("version");
        if (json.has("isDefault")) layout.isDefault = json.getBoolean("isDefault");
        if (json.has("createdTime")) layout.createdTime = json.getLong("createdTime");
        if (json.has("modifiedTime")) layout.modifiedTime = json.getLong("modifiedTime");
        if (json.has("screenshotPath")) layout.screenshotPath = json.getString("screenshotPath");
        
        // 加载元素
        if (json.has("elements")) {
            JSONArray elementsArray = json.getJSONArray("elements");
            for (int i = 0; i < elementsArray.length(); i++) {
                JSONObject elementJson = elementsArray.getJSONObject(i);
                ControlElement element = ControlElement.fromJSON(elementJson);
                layout.addElement(element);
            }
        }
        
        return layout;
    }

    // 转换为 JSON 对象
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        
        json.put("name", name);
        if (description != null) json.put("description", description);
        if (author != null) json.put("author", author);
        json.put("version", version);
        json.put("isDefault", isDefault);
        json.put("createdTime", createdTime);
        json.put("modifiedTime", modifiedTime);
        if (screenshotPath != null) json.put("screenshotPath", screenshotPath);
        
        // 保存元素
        JSONArray elementsArray = new JSONArray();
        for (ControlElement element : elements) {
            elementsArray.put(element.toJSON());
        }
        json.put("elements", elementsArray);
        
        return json;
    }

    // 保存到文件
    public void saveToFile(File file) throws IOException, JSONException {
        this.modifiedTime = System.currentTimeMillis();
        
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(toJSON().toString(2)); // 缩进2个空格
        writer.close();
    }

    // 创建副本
    public ControlLayout copy(String newName) {
        try {
            ControlLayout copy = fromJSON(toJSON());
            copy.setName(newName);
            copy.setCreatedTime(System.currentTimeMillis());
            copy.setModifiedTime(copy.getCreatedTime());
            copy.setDefault(false);
            return copy;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    
    public List<ControlElement> getElements() { return elements; }
    public void setElements(List<ControlElement> elements) { this.elements = elements; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    
    public long getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(long modifiedTime) { this.modifiedTime = modifiedTime; }
    
    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }

    public void addElement(ControlElement element) {
        elements.add(element);
        this.modifiedTime = System.currentTimeMillis();
    }

    public void removeElement(ControlElement element) {
        elements.remove(element);
        this.modifiedTime = System.currentTimeMillis();
    }
    
    public void removeElementById(String id) {
        elements.removeIf(element -> element.getId().equals(id));
        this.modifiedTime = System.currentTimeMillis();
    }
    
    public ControlElement getElementById(String id) {
        for (ControlElement element : elements) {
            if (element.getId().equals(id)) {
                return element;
            }
        }
        return null;
    }
    
    public void clear() {
        elements.clear();
        this.modifiedTime = System.currentTimeMillis();
    }
}