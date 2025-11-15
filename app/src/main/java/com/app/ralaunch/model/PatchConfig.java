package com.app.ralaunch.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 补丁配置数据模型
 * 存储每个游戏的补丁启用状态
 */
public class PatchConfig {
    private String gameId;
    private String patchName;
    private boolean enabled;

    public PatchConfig(String gameId, String patchName, boolean enabled) {
        this.gameId = gameId;
        this.patchName = patchName;
        this.enabled = enabled;
    }

    /**
     * 从 JSON 创建 PatchConfig
     */
    public static PatchConfig fromJson(JSONObject json) throws JSONException {
        return new PatchConfig(
            json.getString("gameId"),
            json.getString("patchName"),
            json.optBoolean("enabled", true) // 默认启用
        );
    }

    /**
     * 转换为 JSON
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("gameId", gameId);
        json.put("patchName", patchName);
        json.put("enabled", enabled);
        return json;
    }

    // Getters and Setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPatchName() {
        return patchName;
    }

    public void setPatchName(String patchName) {
        this.patchName = patchName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "PatchConfig{" +
                "gameId='" + gameId + '\'' +
                ", patchName='" + patchName + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
