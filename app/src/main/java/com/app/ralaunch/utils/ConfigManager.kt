package com.app.ralaunch.utils

import com.app.ralaunch.ConfigurationState
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/*******************************************************************************
 * RotatingArtLauncher - ConfigManager
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/4)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

class ConfigManager private constructor() {
    private val json = Json {
        prettyPrint = true // 保存时格式化，便于阅读
        ignoreUnknownKeys = true // 忽略 JSON 中未知的键，提高兼容性
    }

    private var configFile: File? = null
    var currentConfig: ConfigurationState.AppConfig? = null
        private set // 限制外部直接修改

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(): ConfigManager {
            return instance ?: ConfigManager().also { instance = it }
        }
    }

    /**
     * 初始化配置管理器
     * @param configDir 配置目录
     * @param fileName 配置文件名（可选，默认为 app_config.json）
     */
    fun initialize(configDir: String, fileName: String = "app_config.json") {
        configFile = File(configDir, fileName)
        configFile?.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        try {
            configFile?.let { file ->
                if (file.exists()) {
                    FileReader(file).use { reader ->
                        val jsonString = reader.readText()
                        currentConfig = json.decodeFromString<ConfigurationState.AppConfig>(jsonString)
                    }
                } else {
                    currentConfig = ConfigurationState.AppConfig()
                    saveConfig() // 保存默认配置
                }
            } ?: run {
                currentConfig = ConfigurationState.AppConfig()
            }
        } catch (_: Exception) {
            currentConfig = ConfigurationState.AppConfig()
        }
    }

    /**
     * 保存配置
     */
    fun saveConfig(): Boolean {
        return try {
            currentConfig?.let { config ->
                configFile?.let { file ->
                    val jsonString = json.encodeToString(ConfigurationState.AppConfig.serializer(), config)

                    file.parentFile?.let { parent ->
                        if (!parent.exists()) {
                            parent.mkdirs()
                        }
                    }

                    FileWriter(file).use { writer ->
                        writer.write(jsonString)
                        writer.flush()
                    }
                    // Timber.d("Config saved: ${jsonString.length} bytes to ${file.absolutePath}")
                    true
                } ?: false
            } ?: false
        } catch (e: Exception) {
            // Timber.e(e, "Failed to save config")
            false
        }
    }

    fun getConfig(): ConfigurationState.AppConfig {
        return currentConfig?.copy() ?: ConfigurationState.AppConfig()
    }

    fun updateConfig(updates: (ConfigurationState.AppConfig) -> Unit): Boolean {
        return try {
            currentConfig?.let { config ->
                updates(config)
                saveConfig()
            } ?: false
        } catch (e: Exception) {
            // Timber.e(e, "Failed to update config")
            false
        }
    }
}