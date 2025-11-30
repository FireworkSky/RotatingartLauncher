package com.app.ralaunch.model;

/**
 * 游戏项数据模型
 *
 * 表示游戏列表中的一个游戏项,包含:
 * - 游戏基本信息(名称、描述、图标)
 * - 游戏路径(主程序集和游戏本体)
 * - ModLoader 启用状态
 * - 引擎类型
 *
 * 支持序列化和反序列化(Gson)
 */
public class GameItem {
    private String gameName;
    private String gameDescription;
    private String gameBasePath; // 存放单一游戏相关资源的根目录
    private String gamePath; // 主程序集路径(对于 modloader 就是 ModLoader.dll)
    private String gameBodyPath; // 游戏本体路径(对于 modloader 就是 Terraria.exe)
    private String iconPath; // 图标路径(从exe提取)
    private String engineType; // 引擎类型
    private int iconResId; // 图标资源ID
    private boolean modLoaderEnabled = true; // ModLoader 是否启用(默认启用)
    private boolean isShortcut = false; // 是否为快捷方式（从文件浏览器添加的 DLL/EXE，不删除实际文件）

    // 默认构造函数(Gson需要)
    public GameItem() {
    }

    public GameItem(String gameName, String gamePath, String iconPath) {
        this.gameName = gameName;
        this.gamePath = gamePath;
        this.iconPath = iconPath;
        this.iconResId = 0; // 默认为0,表示使用iconPath
    }

    // 新增构造函数,支持直接使用资源ID
    public GameItem(String gameName,  String gamePath, int iconResId) {
        this.gameName = gameName;
        this.gamePath = gamePath;
        this.iconResId = iconResId;
        this.iconPath = ""; // 空路径,表示使用资源ID
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
    public String getGameBasePath() {
        return gameBasePath;
    }

    public void setGameBasePath(String gameBasePath) {
        this.gameBasePath = gameBasePath;
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

    public String getGameBodyPath() {
        return gameBodyPath;
    }

    public void setGameBodyPath(String gameBodyPath) {
        this.gameBodyPath = gameBodyPath;
    }

    public boolean isModLoaderEnabled() {
        return modLoaderEnabled;
    }

    public void setModLoaderEnabled(boolean modLoaderEnabled) {
        this.modLoaderEnabled = modLoaderEnabled;
    }

    public boolean isShortcut() {
        return isShortcut;
    }

    public void setShortcut(boolean shortcut) {
        this.isShortcut = shortcut;
    }
}
