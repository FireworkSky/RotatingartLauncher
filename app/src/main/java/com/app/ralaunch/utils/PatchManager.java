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
     * 只从外部存储扫描补丁文件夹
     */
    private void loadPatchesFromJson() {
        try {
            availablePatches.clear();

            // 只扫描外部存储的补丁文件夹
            File externalPatchesDir = getExternalPatchesDirectory();
            loadPatchesFromDirectory(externalPatchesDir, "external");

            Log.d(TAG, "Successfully loaded " + availablePatches.size() + " patches total");
        } catch (Exception e) {
            Log.e(TAG, "Error loading patches", e);
            throw new RuntimeException("Failed to load patches", e);
        }
    }

    /**
     * 从指定目录加载补丁
     * 每个子文件夹代表一个补丁，包含 patch.json 和 DLL 文件
     */
    private void loadPatchesFromDirectory(File directory, String source) {
        if (!directory.exists() || !directory.isDirectory()) {
            Log.d(TAG, "Patches directory does not exist: " + directory.getAbsolutePath());
            return;
        }

        File[] patchFolders = directory.listFiles(File::isDirectory);
        if (patchFolders == null || patchFolders.length == 0) {
            Log.d(TAG, "No patch folders found in: " + directory.getAbsolutePath());
            return;
        }

        for (File patchFolder : patchFolders) {
            try {
                File patchJsonFile = new File(patchFolder, "patch.json");
                if (!patchJsonFile.exists()) {
                    Log.d(TAG, "Skipping folder (no patch.json): " + patchFolder.getName());
                    continue;
                }

                // 读取 patch.json
                byte[] jsonBytes = new byte[(int) patchJsonFile.length()];
                try (FileInputStream fis = new FileInputStream(patchJsonFile)) {
                    fis.read(jsonBytes);
                }
                String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
                JSONObject patchJson = new JSONObject(jsonString);

                // 解析补丁信息
                PatchInfo patch = PatchInfo.fromJson(patchJson);

                // 检查 DLL 文件是否存在
                File dllFile = new File(patchFolder, patch.getDllFileName());
                if (!dllFile.exists()) {
                    Log.w(TAG, "Skipping patch (DLL not found): " + patch.getPatchName() +
                          " (" + patch.getDllFileName() + ")");
                    continue;
                }

                // 设置补丁的完整路径
                patch.setFullPath(patchFolder.getAbsolutePath());

                availablePatches.add(patch);
                Log.d(TAG, "Loaded patch from " + source + ": " + patch.getPatchName() +
                      " v" + patch.getVersion() + " at " + patchFolder.getAbsolutePath());
                Log.d(TAG, "  Entry point: " + (patch.hasEntryPoint() ?
                      patch.getEntryTypeName() + "." + patch.getEntryMethodName() : "none"));

            } catch (Exception e) {
                Log.w(TAG, "Failed to load patch from folder: " + patchFolder.getName(), e);
            }
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
     * 首次运行时，将 assets/patches 中的所有补丁文件夹复制到外部存储
     */
    private void initializeExternalPatchesDirectory() {
        try {
            File externalPatchesDir = getExternalPatchesDirectory();
            File flagFile = new File(externalPatchesDir, ".initialized");

            // 检查是否需要初始化：标志文件存在且至少有一个补丁文件夹存在
            if (flagFile.exists()) {
                // 检查是否真的有补丁文件夹
                File[] existingPatches = externalPatchesDir.listFiles(File::isDirectory);
                if (existingPatches != null && existingPatches.length > 0) {
                    Log.d(TAG, "External patches directory already initialized with " + existingPatches.length + " patch(es)");
                    return;
                }
                // 标志文件存在但没有补丁文件夹，需要重新初始化
                Log.w(TAG, "Flag file exists but no patch folders found, re-initializing...");
            }

            Log.d(TAG, "Initializing external patches directory: " + externalPatchesDir.getAbsolutePath());

            // 列出 assets/patches/ 中的所有文件夹
            try {
                String[] patchFolders = context.getAssets().list("patches");
                if (patchFolders != null) {
                    for (String folderName : patchFolders) {
                        // 跳过文件（只处理文件夹）
                        if (folderName.contains(".")) {
                            continue;
                        }

                        try {
                            // 创建对应的外部文件夹
                            File patchFolder = new File(externalPatchesDir, folderName);
                            if (!patchFolder.exists()) {
                                patchFolder.mkdirs();
                            }

                            // 列出文件夹中的所有文件
                            String[] files = context.getAssets().list("patches/" + folderName);
                            if (files != null) {
                                for (String fileName : files) {
                                    try {
                                        String assetPath = "patches/" + folderName + "/" + fileName;
                                        java.io.InputStream fileStream = context.getAssets().open(assetPath);
                                        File targetFile = new File(patchFolder, fileName);
                                        copyStream(fileStream, new FileOutputStream(targetFile));
                                        fileStream.close();
                                        Log.d(TAG, "Copied " + assetPath + " to " + targetFile.getAbsolutePath());
                                    } catch (IOException e) {
                                        Log.w(TAG, "Failed to copy file: " + fileName + " - " + e.getMessage());
                                    }
                                }
                            }

                            Log.d(TAG, "Copied patch folder: " + folderName);

                        } catch (IOException e) {
                            Log.w(TAG, "Failed to copy patch folder: " + folderName + " - " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to list patch folders: " + e.getMessage());
            }

            // 从 MonoMod_Patch.zip 中提取共享依赖（0Harmony.dll）到所有补丁文件夹
            extractSharedDependencies(externalPatchesDir);

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
     * 从 MonoMod_Patch.zip 中提取共享依赖库到补丁目录
     * 目前提取: 0Harmony.dll
     */
    private void extractSharedDependencies(File patchesDir) {
        String[] zipNames = {"MonoMod_Patch.zip"};
        String targetDll = "0Harmony.dll";

        for (String zipName : zipNames) {
            try {
                java.io.InputStream zipStream = context.getAssets().open(zipName);
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(zipStream);
                java.util.zip.ZipEntry entry;

                byte[] harmonyData = null;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.endsWith(targetDll) || entryName.equals(targetDll)) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        harmonyData = baos.toByteArray();
                        baos.close();
                        Log.d(TAG, "Found " + targetDll + " in " + zipName + " (" + harmonyData.length + " bytes)");
                        break;
                    }
                    zis.closeEntry();
                }

                zis.close();
                zipStream.close();

                if (harmonyData != null) {
                    // 复制到 patches 根目录
                    File targetFile = new File(patchesDir, targetDll);
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(harmonyData);
                        Log.d(TAG, "Extracted " + targetDll + " to patches directory");
                    }
                    return;
                }
            } catch (IOException e) {
                Log.d(TAG, "Could not find " + zipName + " (trying next variant)");
            }
        }

        Log.w(TAG, "Could not extract " + targetDll + " from MonoMod_Patch.zip");
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
     * 根据补丁信息查找对应的 DLL 文件路径（仅从外部存储）
     */
    public String getPatchLibraryPath(PatchInfo patchInfo) {
        String fullPath = patchInfo.getFullPath();
        String dllFileName = patchInfo.getDllFileName();

        // 直接使用外部存储路径
        if (fullPath != null) {
            File dllFile = new File(fullPath, dllFileName);
            if (dllFile.exists()) {
                Log.d(TAG, "Using patch: " + dllFile.getAbsolutePath());
                return dllFile.getAbsolutePath();
            } else {
                Log.w(TAG, "Patch DLL not found at expected path: " + dllFile.getAbsolutePath());
            }
        }

        // 后备方案：在外部补丁目录中查找
        File externalPatchesDir = getExternalPatchesDirectory();
        File[] patchFolders = externalPatchesDir.listFiles(File::isDirectory);
        if (patchFolders != null) {
            for (File patchFolder : patchFolders) {
                File dllFile = new File(patchFolder, dllFileName);
                if (dllFile.exists()) {
                    Log.d(TAG, "Found patch in external storage: " + dllFile.getAbsolutePath());
                    return dllFile.getAbsolutePath();
                }
            }
        }

        Log.w(TAG, "Patch DLL not found: " + dllFileName);
        return null;
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
