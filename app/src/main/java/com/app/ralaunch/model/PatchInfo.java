package com.app.ralaunch.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 补丁信息类
 * 定义一个可用的补丁程序集，包含元数据和入口点信息
 */
public class PatchInfo {
    private String patchId;           // 补丁唯一标识
    private String patchName;         // 补丁显示名称
    private String patchDescription;  // 补丁描述
    private String version;           // 补丁版本号
    private String author;            // 补丁作者
    private String dllFileName;       // DLL 文件名
    private String targetGamePattern; // 目标游戏路径匹配模式(正则表达式或包含字符串)
    private String entryTypeName;     // 入口点类型名称（如 "MyPatch.Entry"）
    private String entryMethodName;   // 入口点方法名称（如 "Initialize"）
    private int priority;             // 优先级（数值越大越先执行）
    private boolean enabled;          // 是否启用
    private String fullPath;          // 补丁文件夹完整路径（用于定位DLL文件）

    public PatchInfo(String patchId, String patchName, String patchDescription,
                     String dllFileName, String targetGamePattern) {
        this(patchId, patchName, patchDescription, "1.0.0", "Unknown",
             dllFileName, targetGamePattern, null, null, 0, true);
    }

    public PatchInfo(String patchId, String patchName, String patchDescription,
                     String version, String author, String dllFileName,
                     String targetGamePattern, String entryTypeName,
                     String entryMethodName, int priority, boolean enabled) {
        this.patchId = patchId;
        this.patchName = patchName;
        this.patchDescription = patchDescription;
        this.version = version;
        this.author = author;
        this.dllFileName = dllFileName;
        this.targetGamePattern = targetGamePattern;
        this.entryTypeName = entryTypeName;
        this.entryMethodName = entryMethodName;
        this.priority = priority;
        this.enabled = enabled;
    }

    /**
     * 从 JSON 创建 PatchInfo
     */
    public static PatchInfo fromJson(JSONObject json) throws JSONException {
        String patchId = json.getString("id");
        String patchName = json.getString("name");
        String description = json.optString("description", "");
        String version = json.optString("version", "1.0.0");
        String author = json.optString("author", "Unknown");
        String dllFileName = json.getString("dllFileName");
        int priority = json.optInt("priority", 0);
        boolean enabled = json.optBoolean("enabled", true);

        // 读取目标游戏列表
        JSONArray targetGamesArray = json.optJSONArray("targetGames");
        String targetGamePattern = "";
        if (targetGamesArray != null && targetGamesArray.length() > 0) {
            targetGamePattern = targetGamesArray.getString(0);
        }

        // 读取入口点信息
        String entryTypeName = null;
        String entryMethodName = null;
        if (json.has("entryPoint")) {
            JSONObject entryPoint = json.getJSONObject("entryPoint");
            entryTypeName = entryPoint.optString("typeName", null);
            entryMethodName = entryPoint.optString("methodName", null);
        }

        return new PatchInfo(patchId, patchName, description, version, author,
                           dllFileName, targetGamePattern, entryTypeName,
                           entryMethodName, priority, enabled);
    }

    /**
     * 转换为 JSON
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", patchId);
        json.put("name", patchName);
        json.put("description", patchDescription);
        json.put("version", version);
        json.put("author", author);
        json.put("dllFileName", dllFileName);
        json.put("priority", priority);
        json.put("enabled", enabled);

        if (entryTypeName != null && entryMethodName != null) {
            JSONObject entryPoint = new JSONObject();
            entryPoint.put("typeName", entryTypeName);
            entryPoint.put("methodName", entryMethodName);
            json.put("entryPoint", entryPoint);
        }

        return json;
    }

    /**
     * 检查此补丁是否适用于指定游戏
     */
    public boolean isApplicableToGame(GameItem gameItem) {
        if (gameItem == null || gameItem.getGamePath() == null) {
            return false;
        }

        String gamePath = gameItem.getGamePath().toLowerCase();
        String pattern = targetGamePattern.toLowerCase();

        return gamePath.contains(pattern);
    }

    /**
     * 检查补丁是否有入口点配置
     */
    public boolean hasEntryPoint() {
        return entryTypeName != null && !entryTypeName.isEmpty()
            && entryMethodName != null && !entryMethodName.isEmpty();
    }

    // Getters
    public String getPatchId() {
        return patchId;
    }

    public String getPatchName() {
        return patchName;
    }

    public String getPatchDescription() {
        return patchDescription;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDllFileName() {
        return dllFileName;
    }

    public String getTargetGamePattern() {
        return targetGamePattern;
    }

    public String getEntryTypeName() {
        return entryTypeName;
    }

    public String getEntryMethodName() {
        return entryMethodName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    @Override
    public String toString() {
        return "PatchInfo{" +
                "patchId='" + patchId + '\'' +
                ", patchName='" + patchName + '\'' +
                ", version='" + version + '\'' +
                ", dllFileName='" + dllFileName + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
}
