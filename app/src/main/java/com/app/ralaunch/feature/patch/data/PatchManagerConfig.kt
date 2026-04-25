package com.app.ralaunch.feature.patch.data

import com.app.ralaunch.core.logging.AppLog
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 补丁管理器配置
 */
class PatchManagerConfig {

    @SerializedName("enabled_patches")
    var enabledPatches: HashMap<String, ArrayList<String>> = hashMapOf()

    /**
     * 存储被禁用的补丁ID（默认所有补丁都是启用的）
     */
    @SerializedName("disabled_patches")
    var disabledPatches: HashMap<String, ArrayList<String>> = hashMapOf()

    /**
     * Get enabled patch IDs for a specific game
     * @param gameAsmPath The game assembly file path
     * @return List of enabled patch IDs, or empty list if none
     */
    fun getEnabledPatchIds(gameAsmPath: Path): ArrayList<String> {
        return enabledPatches.getOrDefault(
            gameAsmPath.toAbsolutePath().normalize().toString(),
            arrayListOf()
        )
    }

    /**
     * Set enabled patch IDs for a specific game
     * @param gameAsmPath The game assembly file path
     * @param patchIds List of patch IDs to enable
     */
    fun setEnabledPatchIds(gameAsmPath: Path, patchIds: ArrayList<String>) {
        enabledPatches[gameAsmPath.toAbsolutePath().normalize().toString()] = patchIds
    }

    /**
     * Set whether a patch is enabled for a specific game
     * 默认所有补丁都是启用的，这里只记录被禁用的补丁
     * @param gameAsmPath The game assembly file path
     * @param patchId The patch ID
     * @param enabled true to enable the patch (remove from disabled list), false to disable it (add to disabled list)
     */
    fun setPatchEnabled(gameAsmPath: Path, patchId: String, enabled: Boolean) {
        val key = gameAsmPath.toAbsolutePath().normalize().toString()
        if (enabled) {
            // 启用补丁：从禁用列表中移除
            disabledPatches[key]?.remove(patchId)
            // 同时添加到启用列表（兼容旧逻辑）
            val patches = enabledPatches.getOrPut(key) { arrayListOf() }
            if (!patches.contains(patchId)) {
                patches.add(patchId)
            }
        } else {
            // 禁用补丁：添加到禁用列表
            val disabled = disabledPatches.getOrPut(key) { arrayListOf() }
            if (!disabled.contains(patchId)) {
                disabled.add(patchId)
            }
            // 同时从启用列表中移除（兼容旧逻辑）
            enabledPatches[key]?.remove(patchId)
        }
    }

    /**
     * Check if a patch is enabled for a specific game
     * 默认所有补丁都是启用的，只有被显式禁用的才返回false
     * @param gameAsmPath The game assembly file path
     * @param patchId The patch ID
     * @return true if the patch is enabled (not in disabled list), false if explicitly disabled
     */
    fun isPatchEnabled(gameAsmPath: Path, patchId: String): Boolean {
        val key = gameAsmPath.toAbsolutePath().normalize().toString()
        // 检查是否在禁用列表中
        val disabled = disabledPatches[key]
        if (disabled != null && disabled.contains(patchId)) {
            return false
        }
        // 默认启用
        return true
    }

    /**
     * Save config to JSON file
     * @param pathToJson Path to save the config JSON file
     * @return true if save succeeds, false otherwise
     */
    fun saveToJson(pathToJson: Path): Boolean {
        AppLog.i(TAG, "Save $CONFIG_FILE_NAME, pathToJson: $pathToJson")

        return try {
            // Ensure parent directory exists
            pathToJson.parent?.let { Files.createDirectories(it) }

            FileOutputStream(pathToJson.toFile()).use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    gson.toJson(this, writer)
                    writer.flush()
                    AppLog.i(TAG, "Configuration file saved successfully")
                    true
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Save configuration file failed: ${AppLog.getStackTraceString(e)}")
            false
        }
    }

    companion object {
        private const val TAG = "PatchManagerConfig"
        const val CONFIG_FILE_NAME = "patch_manager.json"

        private val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create()

        /**
         * Load config from JSON file
         * @param pathToJson Path to the config JSON file
         * @return PatchManagerConfig instance, or null if loading fails
         */
        @JvmStatic
        fun fromJson(pathToJson: Path): PatchManagerConfig? {
            AppLog.i(TAG, "加载 $CONFIG_FILE_NAME, pathToJson: $pathToJson")

            if (!Files.exists(pathToJson) || !Files.isRegularFile(pathToJson)) {
                AppLog.w(TAG, "路径不存在 $CONFIG_FILE_NAME 文件")
                return null
            }

            return try {
                FileInputStream(pathToJson.toFile()).use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, PatchManagerConfig::class.java)
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "加载配置文件失败: ${AppLog.getStackTraceString(e)}")
                null
            }
        }
    }
}
