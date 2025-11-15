package com.app.ralaunch.utils;

import android.content.Context;
import android.util.Log;

import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.model.PatchConfig;
import com.app.ralaunch.model.PatchInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 补丁管理器
 * 管理每个游戏的补丁配置
 */
public class PatchManager {
    private static final String TAG = "PatchManager";
    private static final String PATCH_CONFIG_FILE = "patch_configs.json";

    private final Context context;
    private final Map<String, PatchConfig> patchConfigs;
    private final List<PatchInfo> availablePatches;

    public PatchManager(Context context) {
        this.context = context.getApplicationContext();
        this.patchConfigs = new HashMap<>();
        this.availablePatches = new ArrayList<>();

        // 初始化可用补丁列表
        initializeAvailablePatches();
        loadConfigs();
    }

    /**
     * 初始化可用的补丁列表
     */
    private void initializeAvailablePatches() {
        // tModLoader 补丁
        availablePatches.add(new PatchInfo(
            "tmodloader_patch",
            "tModLoader 补丁",
            "修复 tModLoader 在 Android 上的兼容性问题",
            "assemblypatch.dll",
            "tmodloader"
        ));

        // 可以在这里添加更多补丁
        // availablePatches.add(new PatchInfo(
        //     "other_game_patch",
        //     "其他游戏补丁",
        //     "描述",
        //     "otherpatch.dll",
        //     "othergame"
        // ));
    }

    /**
     * 获取所有可用的补丁
     */
    public List<PatchInfo> getAvailablePatches() {
        return new ArrayList<>(availablePatches);
    }

    /**
     * 获取适用于指定游戏的补丁列表
     */
    public List<PatchInfo> getApplicablePatches(GameItem gameItem) {
        List<PatchInfo> applicable = new ArrayList<>();
        for (PatchInfo patch : availablePatches) {
            if (patch.isApplicableToGame(gameItem)) {
                applicable.add(patch);
            }
        }
        return applicable;
    }

    /**
     * 获取游戏的特定补丁配置
     * 如果不存在则创建默认配置（启用状态）
     */
    public PatchConfig getPatchConfig(String gameId, String patchId) {
        String key = generateKey(gameId, patchId);
        if (!patchConfigs.containsKey(key)) {
            // 默认启用补丁
            PatchConfig config = new PatchConfig(gameId, patchId, true);
            patchConfigs.put(key, config);
            saveConfigs();
        }
        return patchConfigs.get(key);
    }

    /**
     * 获取游戏的所有补丁配置
     */
    public List<PatchConfig> getGamePatchConfigs(String gameId) {
        List<PatchConfig> configs = new ArrayList<>();
        for (PatchConfig config : patchConfigs.values()) {
            if (config.getGameId().equals(gameId)) {
                configs.add(config);
            }
        }
        return configs;
    }

    /**
     * 设置游戏的特定补丁启用状态
     */
    public void setPatchEnabled(String gameId, String patchId, boolean enabled) {
        String key = generateKey(gameId, patchId);
        PatchConfig config = patchConfigs.get(key);
        if (config == null) {
            config = new PatchConfig(gameId, patchId, enabled);
            patchConfigs.put(key, config);
        } else {
            config.setEnabled(enabled);
        }
        saveConfigs();
        Log.d(TAG, "Set patch " + patchId + " enabled for game " + gameId + ": " + enabled);
    }

    /**
     * 检查游戏的特定补丁是否启用
     */
    public boolean isPatchEnabled(String gameId, String patchId) {
        PatchConfig config = getPatchConfig(gameId, patchId);
        return config.isEnabled();
    }

    /**
     * 获取所有补丁配置
     */
    public List<PatchConfig> getAllConfigs() {
        return new ArrayList<>(patchConfigs.values());
    }

    /**
     * 删除游戏的所有补丁配置
     */
    public void removeGamePatchConfigs(String gameId) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : patchConfigs.keySet()) {
            PatchConfig config = patchConfigs.get(key);
            if (config != null && config.getGameId().equals(gameId)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            patchConfigs.remove(key);
        }
        if (!keysToRemove.isEmpty()) {
            saveConfigs();
            Log.d(TAG, "Removed " + keysToRemove.size() + " patch configs for game: " + gameId);
        }
    }

    /**
     * 保存配置到文件
     */
    private void saveConfigs() {
        File configFile = new File(context.getFilesDir(), PATCH_CONFIG_FILE);
        try {
            JSONArray jsonArray = new JSONArray();
            for (PatchConfig config : patchConfigs.values()) {
                jsonArray.put(config.toJson());
            }

            String jsonString = jsonArray.toString(2);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(jsonString.getBytes(StandardCharsets.UTF_8));
            }

            Log.d(TAG, "Patch configs saved successfully");
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to save patch configs", e);
        }
    }

    /**
     * 从文件加载配置
     */
    private void loadConfigs() {
        File configFile = new File(context.getFilesDir(), PATCH_CONFIG_FILE);
        if (!configFile.exists()) {
            Log.d(TAG, "No patch config file found, starting fresh");
            return;
        }

        try {
            byte[] bytes = new byte[(int) configFile.length()];
            try (FileInputStream fis = new FileInputStream(configFile)) {
                fis.read(bytes);
            }

            String jsonString = new String(bytes, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(jsonString);

            patchConfigs.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                PatchConfig config = PatchConfig.fromJson(jsonObject);
                String key = generateKey(config.getGameId(), config.getPatchName());
                patchConfigs.put(key, config);
            }

            Log.d(TAG, "Loaded " + patchConfigs.size() + " patch configs");
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to load patch configs", e);
        }
    }

    /**
     * 生成配置的唯一键
     */
    private String generateKey(String gameId, String patchName) {
        return gameId + ":" + patchName;
    }

    /**
     * 获取补丁库路径
     */
    public String getPatchLibraryPath(String dllFileName) {
        // 补丁库应该放在 app 的 native library 目录或 assets 中
        File patchDir = new File(context.getFilesDir(), "patches");
        if (!patchDir.exists()) {
            patchDir.mkdirs();
        }
        return new File(patchDir, dllFileName).getAbsolutePath();
    }

    /**
     * 获取需要对指定游戏应用的补丁列表
     */
    public List<PatchInfo> getEnabledPatches(GameItem gameItem) {
        List<PatchInfo> enabledPatches = new ArrayList<>();

        if (gameItem == null) {
            return enabledPatches;
        }

        String gameId = gameItem.getGamePath();
        List<PatchInfo> applicablePatches = getApplicablePatches(gameItem);

        for (PatchInfo patch : applicablePatches) {
            if (isPatchEnabled(gameId, patch.getPatchId())) {
                enabledPatches.add(patch);
            }
        }

        return enabledPatches;
    }
}
