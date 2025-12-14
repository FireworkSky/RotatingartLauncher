package com.app.ralaunch.utils

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.app.ralaunch.locales.AppLanguage
import com.app.ralaunch.locales.LocaleManager.strings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 配置管理器
 * 支持应用设置的保存、加载和管理
 *
 * 注意：所有需要序列化/反序列化的类都需要标记 @Serializable
 */
class ConfigManager private constructor() {

    // 使用 Kotlinx.Serialization 的 Json 实例
    private val json = Json {
        prettyPrint = true // 保存时格式化，便于阅读
        ignoreUnknownKeys = true // 忽略 JSON 中未知的键，提高兼容性
    }

    private var configFile: File? = null
    var currentConfig: AppConfig? = null
        private set // 限制外部直接修改

    // 应用配置数据类 - 添加 @Serializable 注解
    @Serializable
    data class AppConfig(
        // 应用设置
        var language: AppLanguage = AppLanguage.SYSTEM,
        var themeMode: ThemeMode = ThemeMode.SYSTEM,
        var dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        var themeSeedColor: Long = Color(0xFF2196F3).value.toLong(),

        var controlSettings: ControlSettings = ControlSettings(),
        var advancedSettings: AdvancedSettings = AdvancedSettings()
    ) {
        // 控制设置子对象 - 添加 @Serializable 注解
        @Serializable
        data class ControlSettings(
            var virtualJoystickOpacity: Float = 0.8f,
            var vibration: Boolean = true
        )

        // 高级设置子对象 - 添加 @Serializable 注解
        @Serializable
        data class AdvancedSettings(
            var renderer: Renderer = Renderer.AUTO,
            var serverGc : Boolean = false,
            var concurrentGc : Boolean = true,
            var tieredCompilation : Boolean = true,
            var coreClrDebugLog : Boolean = false,
            var threadAffinity : Boolean = false
        )
    }

    // 枚举定义 - 添加 @Serializable 注解
    @Serializable
    enum class ThemeMode {
        LIGHT, DARK, SYSTEM;

        fun getName(): String {
            return when (this) {
                LIGHT -> strings.settingsLight
                DARK -> strings.settingsDark
                SYSTEM -> strings.settingsFollowSystem
            }
        }
    }

    // 枚举定义 - 添加 @Serializable 注解
    @Serializable
    enum class Renderer {
        AUTO,
        NATIVE_OPENGL_ES,
        GL4ES,
        GL4ES_ANGLE,
        MOBILE_GL,
        ANGLE_VULKAN,
        ZINK_MESA,
        ZINK_MESA_25,
        VIRGL,
        FREEDRENO;

        // 注意：displayName 不再存储在枚举本身，而是通过序列化名称或自定义序列化器处理
        // 如果需要保留原始显示名称，可以考虑自定义序列化器或者在 JSON 中单独处理
        // 这里简化处理，序列化名称即为枚举常量名
        val displayName: String
            get() = when (this) {
                AUTO -> "AUTO"
                NATIVE_OPENGL_ES -> "Native OpenGL ES"
                GL4ES -> "GL4ES"
                GL4ES_ANGLE -> "GL4ES + ANGLE"
                MOBILE_GL -> "MobileGl"
                ANGLE_VULKAN -> "ANGLE (Vulkan)"
                ZINK_MESA -> "Zink (Mesa)"
                ZINK_MESA_25 -> "Zink (Mesa 25)"
                VIRGL -> "VirGL Renderer"
                FREEDRENO -> "Freedreno(Adreno)"
            }

        companion object {
            // 根据序列化后的名称（即枚举常量名）查找
            fun fromDisplayName(displayName: String): Renderer {
                return entries.find { it.displayName == displayName }
                    ?: AUTO // 默认改为自动选择
            }
        }
    }


    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager().also { instance = it }
            }
        }
    }

    /**
     * 初始化配置管理器
     * @param configDir 配置目录
     * @param fileName 配置文件名（可选，默认为 app_config.json）
     */
    fun initialize(configDir: String, fileName: String = "app_config.json") {
        configFile = File(configDir, fileName).also { file -> file.parentFile?.mkdirs() }
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        try {
            configFile?.let { file ->
                if (file.exists()) {
                    val jsonString = file.readText()
                    currentConfig = json.decodeFromString<AppConfig>(jsonString)
                } else {
                    RALaunchLogger.d("Config file does not exist, creating default.")
                    currentConfig = AppConfig()
                    saveConfig() // 保存默认配置
                }
            } ?: run {
                RALaunchLogger.w("Config directory/file not initialized, using default config.")
                currentConfig = AppConfig()
            }
        } catch (e: Exception) { // 捕获 SerializationException 等
            RALaunchLogger.e(message = "Failed to load or parse config, using default", throwable = e)
            currentConfig = AppConfig()
        }
    }

    /**
     * 保存配置
     */
    fun saveConfig(): Boolean {
        return try {
            currentConfig?.let { config ->
                configFile?.let { file ->
                    val jsonString = json.encodeToString(config)
                    file.writeText(jsonString)
                    true
                } ?: false
            } ?: false
        } catch (e: Exception) { // 捕获 SerializationException 等
            RALaunchLogger.e(message = "Failed to save config", throwable = e)
            false
        }
    }

    /**
     * 获取当前配置（深拷贝副本）
     * 注意：Kotlinx.Serialization decode 本身返回的就是新对象，但为了语义清晰，这里仍用 copy()
     */
    fun getConfig(): AppConfig {
        return currentConfig?.copy() ?: AppConfig()
    }

    /**
     * 更新配置
     */
    fun updateConfig(updates: (AppConfig) -> Unit): Boolean {
        return try {
            currentConfig?.let { config ->
                updates(config)
                saveConfig()
            } ?: false
        } catch (e: Exception) {
            RALaunchLogger.e(message = "Failed to update config", throwable = e)
            false
        }
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefault(): Boolean {
        currentConfig = AppConfig()
        return saveConfig()
    }

    /**
     * 导出配置到文件
     */
    fun exportConfig(exportFile: File): Boolean {
        return try {
            currentConfig?.let { config ->
                val jsonString = json.encodeToString(config)
                exportFile.writeText(jsonString)
                true
            } ?: false
        } catch (e: Exception) {
            RALaunchLogger.e(message = "Failed to export config", throwable = e)
            false
        }
    }

    /**
     * 从文件导入配置
     */
    fun importConfig(importFile: File): Boolean {
        return try {
            if (!importFile.exists()) {
                RALaunchLogger.w("Import file does not exist: ${importFile.absolutePath}")
                return false
            }

            val jsonString = importFile.readText()
            val importedConfig = json.decodeFromString<AppConfig>(jsonString)
            currentConfig = importedConfig
            saveConfig() // 保存到主配置文件
            true
        } catch (e: Exception) { // 捕获 SerializationException 等
            RALaunchLogger.e(message = "Failed to import or parse config from ${importFile.absolutePath}", throwable = e)
            false
        }
    }

    /**
     * 获取配置JSON字符串（用于调试或备份）
     */
    fun getConfigAsJson(): String {
        return currentConfig?.let { config ->
            // 使用 prettyPrint = true 的 Json 实例进行编码
            json.encodeToString(config)
        } ?: "{}"
    }

    /**
     * 监听配置变化
     */
    interface ConfigChangeListener {
        fun onConfigChanged(changedKeys: List<String>)
    }

    private val listeners = mutableSetOf<ConfigChangeListener>()

    fun addConfigListener(listener: ConfigChangeListener) {
        listeners.add(listener)
    }

    fun removeConfigListener(listener: ConfigChangeListener) {
        listeners.remove(listener)
    }

    // 注意：notifyConfigChanged 需要你知道哪些键改变了才能有效调用
    private fun notifyConfigChanged(changedKeys: List<String>) {
        listeners.forEach { it.onConfigChanged(changedKeys) }
    }
}