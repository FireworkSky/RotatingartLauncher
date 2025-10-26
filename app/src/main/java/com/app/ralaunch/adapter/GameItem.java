package com.app.ralaunch.adapter;

public class GameItem {
    private String gameName;
    private String gameDescription;
    private String gamePath;
    private String iconPath; // 图标路径（从exe提取）
    private String engineType; // 引擎类型
    private int iconResId; // 图标资源ID

    // 默认构造函数（Gson需要）
    public GameItem() {
    }

    public GameItem(String gameName, String gamePath, String iconPath) {
        this.gameName = gameName;
        this.gamePath = gamePath;
        this.iconPath = iconPath;
        this.iconResId = 0; // 默认为0，表示使用iconPath
    }

    // 新增构造函数，支持直接使用资源ID
    public GameItem(String gameName,  String gamePath, int iconResId) {
        this.gameName = gameName;
        this.gamePath = gamePath;
        this.iconResId = iconResId;
        this.iconPath = ""; // 空路径，表示使用资源ID
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getGameDescription() {
        return gameDescription;
    }

    public void setGameDescription(String gameDescription) {
        this.gameDescription = gameDescription;
    }

    public String getGamePath() {
        return gamePath;
    }

    public void setGamePath(String gamePath) {
        this.gamePath = gamePath;
    }

    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }
}