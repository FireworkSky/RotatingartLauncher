package com.app.ralib.patch;

import android.util.Log;

import androidx.annotation.Nullable;

import com.app.ralib.Shared;
import com.app.ralib.extractors.BasicSevenZipExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.utils.FileUtils;
import com.app.ralib.utils.TemporaryFileAcquirer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PatchManager {
    private static final String TAG = "PatchManager";

    private static final boolean IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL = true;
    private static final String PATCH_STORAGE_DIR = "patches";

    private final Path patchStoragePath;
    private final Path configFilePath;
    private PatchManagerConfig config;

    public PatchManager(@Nullable String customStoragePath) throws IOException {
        var isFirstTime = false;
        patchStoragePath = getDefaultPatchStorageDirectories(customStoragePath);
        if (!Files.isDirectory(patchStoragePath) || !Files.exists(patchStoragePath)) {
            Files.deleteIfExists(patchStoragePath);
            Files.createDirectories(patchStoragePath);
            isFirstTime = true;
        }
        configFilePath = patchStoragePath.resolve(PatchManagerConfig.CONFIG_FILE_NAME);
        loadConfig();

        // 每次启动都检查并安装缺失的内置补丁
        installBuiltInPatches(this);
    }

    //region Patch Querying

    /**
     * Returns all patches that are applicable to the specified game and are enabled for
     * the provided game assembly path. Results are sorted by priority in descending order
     * (higher priority first).
     * 
     * Note: By default, ALL applicable patches are enabled.
     *
     * @param gameId      the game ID to filter patches by
     * @param gameAsmPath the path to the game's assembly (used to lookup enabled patches)
     * @return a list of applicable and enabled patches, sorted by priority (highest first)
     */
    public ArrayList<Patch> getApplicableAndEnabledPatches(String gameId, Path gameAsmPath) {
        var installedPatches = getInstalledPatches();

        // 默认启用所有适用的补丁
        return installedPatches.stream()
                .filter(patch -> isPatchApplicableToGame(patch, gameId))
                .sorted(Comparator.comparingInt(patch -> -patch.manifest.priority))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns all patches that are applicable to the specified game, regardless of enabled status.
     * Results are sorted by priority in descending order (higher priority first).
     *
     * @param gameId the game ID to get applicable patches for
     * @return list of all applicable patches, sorted by priority (highest first)
     */
    public ArrayList<Patch> getApplicablePatches(String gameId) {
        var installedPatches = getInstalledPatches();

        return installedPatches.stream()
                .filter(patch -> isPatchApplicableToGame(patch, gameId))
                .sorted(Comparator.comparingInt(patch -> -patch.manifest.priority))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Check if a patch is applicable to a specific game.
     * Supports wildcard "*" in targetGames to match all games.
     *
     * @param patch  the patch to check
     * @param gameId the game ID to match against
     * @return true if the patch is applicable to the game
     */
    private boolean isPatchApplicableToGame(Patch patch, String gameId) {
        if (patch.manifest.targetGames == null || patch.manifest.targetGames.isEmpty()) {
            return false;
        }
        // Support wildcard "*" to match all games
        return patch.manifest.targetGames.contains("*") 
                || patch.manifest.targetGames.contains(gameId);
    }

    /**
     * Returns all patches that are enabled for the specified game assembly path.
     * Results are sorted by priority in descending order (higher priority first).
     * 
     * Note: By default, ALL installed patches are enabled. This ensures all patches
     * are active by default.
     *
     * @param gameAsmPath the game assembly path used to determine enabled patches
     * @return list of enabled patches, sorted by priority (highest first)
     */
    public ArrayList<Patch> getEnabledPatches(Path gameAsmPath) {
        var installedPatches = getInstalledPatches();
        
        // 默认启用所有已安装的补丁
        return installedPatches.stream()
                .sorted(Comparator.comparingInt(patch -> -patch.manifest.priority))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Get IDs of all patches that are enabled for the specified game assembly path.
     *
     * @param gameAsmPath the game assembly path used to lookup enabled patch IDs
     * @return an ArrayList containing enabled patch IDs (may be empty)
     */
    public ArrayList<String> getEnabledPatchIds(Path gameAsmPath) {
        return config.getEnabledPatchIds(gameAsmPath);
    }

    /**
     * Scans the patch storage directory and returns all currently installed (valid) patches.
     *
     * @return list of all installed patches
     * @throws RuntimeException if an I/O error occurs while listing the directory (wrapped from IOException)
     */
    public ArrayList<Patch> getInstalledPatches() {
        ArrayList<Patch> installedPatches;
        try (var pathsStream = Files.list(patchStoragePath)) {
            installedPatches = pathsStream
                    .filter(Files::isDirectory)
                    .map(Patch::fromPatchPath)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return installedPatches;
    }

    /**
     * Returns patches from the installed patches that match the provided IDs.
     * The returned list preserves the order of the supplied {@code patchIds} list.
     *
     * @param patchIds list of patch IDs to retrieve
     * @return list of patches matching the specified IDs, ordered to follow {@code patchIds}
     */
    public ArrayList<Patch> getPatchesByIds(List<String> patchIds) {
        var installedPatches = getInstalledPatches();
        return installedPatches.stream()
                .filter(patch -> patchIds.contains(patch.manifest.id))
                // sort by patchIds order
                .sorted(Comparator.comparingInt(patch -> patchIds.indexOf(patch.manifest.id)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Construct the environment variable string for startup hooks from a list of patches.
     * The absolute paths of the entry assemblies of the patches are joined with ':' as the separator.
     *
     * @param patches list of patches to construct the environment variable from
     * @return colon-separated environment variable string containing entry assembly absolute paths
     */
    public static String constructStartupHooksEnvVar(List<Patch> patches) {
        return patches
                .stream()
                .map(p -> p.getEntryAssemblyAbsolutePath().toString())
                .collect(Collectors.joining(":"));
    }

    //endregion

    //region Patch Installation

    /**
     * Install a patch archive (ZIP/7z). If a patch with the same ID already exists,
     * it will be deleted and the new archive will be extracted into the patch directory.
     * The method validates the patch manifest before installation.
     *
     * @param patchZipPath path to the patch archive file to install
     * @return true if installation succeeds, false otherwise
     */
    public boolean installPatch(Path patchZipPath) {
        if (!Files.exists(patchZipPath) || !Files.isRegularFile(patchZipPath)) {
            Log.w(TAG, "补丁安装失败: 补丁文件不存在或不是一个有效的文件, path: " + patchZipPath);
            return false;
        }

        var manifest = PatchManifest.fromZip(patchZipPath);
        if (manifest == null) {
            Log.w(TAG, "补丁安装失败: 无法读取补丁清单, path: " + patchZipPath);
            return false;
        }

        var patchPath = patchStoragePath.resolve(manifest.id);

        if (Files.exists(patchPath)) {
            Log.i(TAG, "补丁已存在, 将删除原补丁目录，重新安装, patch id: " + manifest.id);
            if (!FileUtils.deleteDirectoryRecursively(patchPath)) {
                Log.w(TAG, "删除原补丁目录时发生错误");
                return false;
            }
        } else {
            Log.i(TAG, "正在安装新补丁, patch id: " + manifest.id);
        }

        Log.i(TAG, "正在解压补丁文件到补丁目录...");
        new BasicSevenZipExtractor(patchZipPath, Paths.get(""), patchPath, new ExtractorCollection.ExtractionListener() {
            @Override
            public void onProgress(String message, float progress, HashMap<String, Object> state) {}

            @Override
            public void onComplete(String message, HashMap<String, Object> state) {}

            @Override
            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                throw new RuntimeException(message, ex);
            }
        })
                .extract();

        return true;
    }

    //endregion

    //region Config Value Setting and Getting

    /**
     * Set whether a patch is enabled for a specific game.
     *
     * @param gameAsmPath the game assembly path
     * @param patchId     the patch ID
     * @param enabled     true to enable the patch, false to disable it
     */
    public void setPatchEnabled(Path gameAsmPath, String patchId, boolean enabled) {
        config.setPatchEnabled(gameAsmPath, patchId, enabled);
        saveConfig();
    }

    /**
     * Check if a patch is enabled for a specific game.
     *
     * @param gameAsmPath the game assembly path
     * @param patchId     the patch ID
     * @return true if the patch is enabled for the provided game assembly path, false otherwise
     */
    public boolean isPatchEnabled(Path gameAsmPath, String patchId) {
        return config.isPatchEnabled(gameAsmPath, patchId);
    }

    //endregion

    //region Configuration Management

    /**
     * Load configuration from file. If file doesn't exist or loading fails, creates a new config.
     */
    private void loadConfig() {
        config = PatchManagerConfig.fromJson(configFilePath);
        if (config == null) {
            Log.i(TAG, "配置文件不存在或加载失败，创建新配置");
            config = new PatchManagerConfig();
            saveConfig();
        } else {
            Log.i(TAG, "配置文件加载成功");
        }
    }

    /**
     * Save current configuration to file.
     */
    private void saveConfig() {
        if (!config.saveToJson(configFilePath)) {
            Log.w(TAG, "保存配置文件失败");
        }
    }

    //endregion

    //region Patch Storage Management

    /**
     * Get the default patch storage directory.
     * If {@code customStoragePath} is null, uses either the external files directory
     * or the internal files directory based on {@code IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL}.
     *
     * @param customStoragePath custom storage path, or {@code null} to use the default location
     * @return path to the prepared patch storage directory
     * @throws IOException if directory creation fails
     */
    private static Path getDefaultPatchStorageDirectories(@Nullable String customStoragePath) throws IOException {
        // Implementation to prepare patch storage directories
        String baseDir;
        baseDir = Objects.requireNonNullElseGet(customStoragePath, () -> IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL
                ? Objects.requireNonNull(Shared.getContext().getExternalFilesDir(null)).getAbsolutePath()
                : Shared.getContext().getFilesDir().getAbsolutePath());

        return Paths.get(baseDir, PATCH_STORAGE_DIR).normalize();
    }

    public static void installBuiltInPatches(PatchManager patchManager) {
        Path apkPath = Paths.get(Shared.getContext().getApplicationInfo().sourceDir);
        try (var tfa = new TemporaryFileAcquirer()) {
            Path extractedPatches = tfa.acquireTempFilePath("extracted_patches");

            new BasicSevenZipExtractor(
                    apkPath,
                    Paths.get("assets/patches"),
                    extractedPatches,
                    new ExtractorCollection.ExtractionListener() {
                        @Override
                        public void onProgress(String message, float progress, HashMap<String, Object> state) {}

                        @Override
                        public void onComplete(String message, HashMap<String, Object> state) {}

                        @Override
                        public void onError(String message, Exception ex, HashMap<String, Object> state) {
                            throw new RuntimeException(message, ex);
                        }
                    }
            )
                    .extract();

            // 安装共享依赖库（如 0Harmony.dll）
            try (var pathsStream = Files.list(extractedPatches)) {
                var dlls = pathsStream
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".dll"))
                        .collect(Collectors.toList());
                for (var dll : dlls) {
                    Path targetPath = patchManager.patchStoragePath.resolve(dll.getFileName());
                    if (!Files.exists(targetPath)) {
                        Log.i(TAG, "正在安装补丁依赖库: " + dll.getFileName());
                        Files.copy(dll, targetPath);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // 获取已安装补丁的 ID 列表
            var installedPatchIds = patchManager.getInstalledPatches().stream()
                    .map(p -> p.manifest.id)
                    .collect(Collectors.toSet());

            // 安装缺失的内置补丁
            try (var pathsStream = Files.list(extractedPatches)) {
                var patchZips = pathsStream
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".zip"))
                        .collect(Collectors.toList());
                for (var patchZip : patchZips) {
                    // 读取补丁 manifest 检查是否已安装
                    PatchManifest manifest = PatchManifest.fromZip(patchZip);
                    if (manifest != null && !installedPatchIds.contains(manifest.id)) {
                        Log.i(TAG, "正在安装内置补丁: " + patchZip.getFileName() + " (id: " + manifest.id + ")");
                        patchManager.installPatch(patchZip);
                    } else if (manifest != null) {
                        Log.d(TAG, "补丁已安装，跳过: " + manifest.id);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //endregion
}
