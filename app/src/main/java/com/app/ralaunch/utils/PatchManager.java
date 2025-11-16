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

        // 初始化外部补丁文件夹（首次运行时复制 assets 中的补丁）
        initializeExternalPatchesDirectory();

        // 初始化可用补丁列表
        initializeAvailablePatches();
        loadConfigs();
    }

    /**
     * 初始化可用的补丁列表
     * 从 assets/patches/patch_metadata.json 加载
     */
    private void initializeAvailablePatches() {
        try {
            // 尝试从JSON文件加载补丁元数据
            loadPatchesFromJson();
        } catch (Exception e) {
            Log.w(TAG, "Failed to load patches from JSON, using hardcoded patches: " + e.getMessage());

            // 如果加载失败，使用硬编码的补丁列表作为后备
            availablePatches.add(new PatchInfo(
                "tmodloader_patch",
                "tModLoader 补丁",
                "修复 tModLoader 在 Android 上的兼容性问题",
                "assemblypatch.dll",
                "tmodloader"
            ));
        }
    }

    /**
     * 从 JSON 文件加载补丁元数据
     * 优先从外部存储读取，如果不存在则从 assets 读取
     */
    private void loadPatchesFromJson() {
        try {
            String jsonString = null;

            // 1. 尝试从外部存储读取（用户自定义补丁）
            File externalPatchMetadata = getExternalPatchMetadataFile();
            if (externalPatchMetadata.exists()) {
                Log.d(TAG, "Loading patches from external storage: " + externalPatchMetadata.getAbsolutePath());
                try (FileInputStream fis = new FileInputStream(externalPatchMetadata)) {
                    byte[] buffer = new byte[fis.available()];
                    fis.read(buffer);
                    jsonString = new String(buffer, StandardCharsets.UTF_8);
                    Log.d(TAG, "Loaded external patch metadata");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load external patch metadata, falling back to assets", e);
                    jsonString = null;
                }
            }

            // 2. 如果外部存储不存在，从 assets 读取（内置补丁）
            if (jsonString == null) {
                Log.d(TAG, "Loading patches from assets");
                java.io.InputStream is = context.getAssets().open("patches/patch_metadata.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                jsonString = new String(buffer, StandardCharsets.UTF_8);
            }

            // 3. 解析 JSON
            JSONObject jsonRoot = new JSONObject(jsonString);
            JSONArray patchesArray = jsonRoot.getJSONArray("patches");

            availablePatches.clear();
            for (int i = 0; i < patchesArray.length(); i++) {
                JSONObject patchJson = patchesArray.getJSONObject(i);
                PatchInfo patch = PatchInfo.fromJson(patchJson);

                // 检查补丁程序集文件是否存在
                if (isPatchAssemblyAvailable(patch.getDllFileName())) {
                    availablePatches.add(patch);
                    Log.d(TAG, "Loaded patch: " + patch.getPatchName() + " v" + patch.getVersion());
                } else {
                    Log.w(TAG, "Skipping patch (assembly not found): " + patch.getPatchName() + " (" + patch.getDllFileName() + ")");
                }
            }

            Log.d(TAG, "Successfully loaded " + availablePatches.size() + " patches from JSON");
        } catch (Exception e) {
            Log.e(TAG, "Error loading patches from JSON", e);
            throw new RuntimeException("Failed to load patch metadata", e);
        }
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
     * 检查补丁程序集文件是否可用
     * 优先检查外部存储，如果不存在则检查 assets
     */
    private boolean isPatchAssemblyAvailable(String dllFileName) {
        // 1. 检查外部存储
        File externalPatchesDir = getExternalPatchesDirectory();
        File externalPatchFile = new File(externalPatchesDir, dllFileName);
        if (externalPatchFile.exists()) {
            return true;
        }

        // 2. 检查 assets
        try {
            String[] assetFiles = context.getAssets().list("patches");
            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    if (fileName.equals(dllFileName)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to list assets: " + e.getMessage());
        }

        return false;
    }

    /**
     * 初始化外部补丁目录
     * 首次运行时，将 assets 中的补丁程序集和配置文件复制到外部存储
     */
    private void initializeExternalPatchesDirectory() {
        try {
            File externalPatchesDir = getExternalPatchesDirectory();
            File flagFile = new File(externalPatchesDir, ".initialized");

            // 如果已经初始化过，跳过
            if (flagFile.exists()) {
                Log.d(TAG, "External patches directory already initialized");
                return;
            }

            Log.d(TAG, "Initializing external patches directory: " + externalPatchesDir.getAbsolutePath());

            // 复制 patch_metadata.json
            try {
                java.io.InputStream metadataStream = context.getAssets().open("patches/patch_metadata.json");
                File metadataFile = new File(externalPatchesDir, "patch_metadata.json");
                copyStream(metadataStream, new FileOutputStream(metadataFile));
                metadataStream.close();
                Log.d(TAG, "Copied patch_metadata.json to external storage");
            } catch (IOException e) {
                Log.w(TAG, "Failed to copy patch_metadata.json: " + e.getMessage());
            }

            // 列出 assets/patches/ 中的所有 DLL 文件并复制
            try {
                String[] patchFiles = context.getAssets().list("patches");
                if (patchFiles != null) {
                    for (String fileName : patchFiles) {
                        if (fileName.endsWith(".dll")) {
                            try {
                                java.io.InputStream dllStream = context.getAssets().open("patches/" + fileName);
                                File dllFile = new File(externalPatchesDir, fileName);
                                copyStream(dllStream, new FileOutputStream(dllFile));
                                dllStream.close();
                                Log.d(TAG, "Copied " + fileName + " to external storage");
                            } catch (IOException e) {
                                Log.w(TAG, "Failed to copy " + fileName + ": " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to list patch files: " + e.getMessage());
            }

            // 创建初始化标记文件
            try {
                flagFile.createNewFile();
                Log.d(TAG, "External patches directory initialization complete");
            } catch (IOException e) {
                Log.w(TAG, "Failed to create flag file: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing external patches directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 复制输入流到输出流
     */
    private void copyStream(java.io.InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
        output.close();
    }

    /**
     * 获取外部存储补丁元数据文件
     * 路径: /sdcard/Android/data/com.app.ralaunch/files/patches/patch_metadata.json
     */
    public File getExternalPatchMetadataFile() {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            // 如果外部存储不可用，使用内部存储
            externalFilesDir = context.getFilesDir();
        }
        File patchesDir = new File(externalFilesDir, "patches");
        return new File(patchesDir, "patch_metadata.json");
    }

    /**
     * 获取外部存储补丁目录
     * 路径: /sdcard/Android/data/com.app.ralaunch/files/patches/
     */
    public File getExternalPatchesDirectory() {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            externalFilesDir = context.getFilesDir();
        }
        File patchesDir = new File(externalFilesDir, "patches");
        if (!patchesDir.exists()) {
            patchesDir.mkdirs();
            Log.d(TAG, "Created external patches directory: " + patchesDir.getAbsolutePath());
        }
        return patchesDir;
    }

    /**
     * 获取补丁库路径
     * 优先从外部存储查找，如果不存在则使用内部存储
     */
    public String getPatchLibraryPath(String dllFileName) {
        // 1. 尝试从外部存储获取（用户自定义补丁）
        File externalPatchesDir = getExternalPatchesDirectory();
        File externalPatchFile = new File(externalPatchesDir, dllFileName);
        if (externalPatchFile.exists()) {
            Log.d(TAG, "Using external patch: " + externalPatchFile.getAbsolutePath());
            return externalPatchFile.getAbsolutePath();
        }

        // 2. 使用内部存储（从 assets 复制的补丁）
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
