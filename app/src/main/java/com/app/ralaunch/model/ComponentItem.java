package com.app.ralaunch.model;

/**
 * 组件安装项数据模型

 * 用于在初始化过程中跟踪组件安装状态
 */
public class ComponentItem {
    private String name;
    private String description;
    private String fileName;
    private boolean installed;
    private int progress;
    private boolean needsExtraction;  // 是否需要解压

    public ComponentItem(String name, String description, String fileName) {
        this(name, description, fileName, true);  // 默认需要解压
    }

    public ComponentItem(String name, String description, String fileName, boolean needsExtraction) {
        this.name = name;
        this.description = description;
        this.fileName = fileName;
        this.installed = false;
        this.progress = 0;
        this.needsExtraction = needsExtraction;
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getFileName() { return fileName; }
    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean needsExtraction() { return needsExtraction; }
    public void setNeedsExtraction(boolean needsExtraction) { this.needsExtraction = needsExtraction; }
}