package com.app.ralaunch.utils

import android.os.Build
import com.app.ralaunch.locales.AppLanguage
import com.app.ralaunch.locales.LocaleManager.strings
import java.io.File

/**
 * 配置管理器
 * 支持应用设置的保存、加载和管理
 */
class ConfigManager private constructor() {

    private val serializer = JsonSerializer()
    private var configFile: File? = null
    var currentConfig: AppConfig? = null

    // 应用配置数据类
    // 在 ConfigManager 中修改 AppConfig 数据类
    data class AppConfig(
        // 应用设置
        var language: AppLanguage = AppLanguage.EN,
        var themeMode: ThemeMode = ThemeMode.SYSTEM,
        var dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        var themeSeedColor : Long = 0xFF2196F3,

        // 嵌套对象 - 控制设置
        var controlSettings: ControlSettings = ControlSettings(),
        var advancedSettings: AdvancedSettings = AdvancedSettings()
    ) {
        // 控制设置子对象
        data class ControlSettings(
            var virtualJoystickOpacity: Float = 0.8f,
            var vibrationEnabled: Boolean = true
        )

        data class AdvancedSettings(
            var renderer: Renderer = Renderer.AUTO
        )
    }

    // 枚举定义
    enum class ThemeMode {
        LIGHT, DARK, SYSTEM;

        fun getName() : String {
            return when(this) {
                LIGHT -> strings.settingsLight
                DARK -> strings.settingsDark
                SYSTEM -> strings.settingsFollowSystem
            }
        }
    }

    enum class Renderer(val displayName: String) {
        AUTO("AUTO"),
        NATIVE_OPENGL_ES("Native OpenGL ES"),
        GL4ES("GL4ES"),
        GL4ES_ANGLE("GL4ES + ANGLE"),
        MOBILE_GL("MobileGl"),
        ANGLE_VULKAN("ANGLE (Vulkan)"),
        ZINK_MESA("Zink (Mesa)"),
        ZINK_MESA_25("Zink (Mesa 25)"),
        VIRGL("VirGL Renderer"),
        FREEDRENO("Freedreno(Adreno)");

        companion object {
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
                currentConfig = serializer.loadFromFile<AppConfig>(file) ?: AppConfig()
            } ?: run {
                currentConfig = AppConfig()
            }
        } catch (e: Exception) {
            Logger.error(message = "Failed to load config, using default", throwable = e)
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
                    serializer.saveToFile(config, file)
                } ?: false
            } ?: false
        } catch (e: Exception) {
            Logger.error(message = "Failed to save config", throwable = e)
            false
        }
    }

    /**
     * 获取当前配置（只读）
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
            Logger.error(message = "Failed to update config", throwable = e)
            false
        }
    }

    /**
     * 获取配置值
     */
    inline fun <reified T> getValue(key: String, defaultValue: T): T {
        return try {
            currentConfig?.let { config ->
                val field = config.javaClass.getDeclaredField(key)
                field.isAccessible = true
                (field.get(config) as? T) ?: defaultValue
            } ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    /**
     * 设置配置值
     */
    fun <T> setValue(key: String, value: T): Boolean {
        return updateConfig { config ->
            try {
                val field = config.javaClass.getDeclaredField(key)
                field.isAccessible = true
                field.set(config, value)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid config key: $key", e)
            }
        }
    }

    /**
     * 获取嵌套对象的值
     */
    inline fun <reified T> getNestedValue(path: String, defaultValue: T): T {
        return try {
            currentConfig?.let { config ->
                val pathParts = path.split(".")
                var currentObj: Any = config

                for (part in pathParts) {
                    val field = currentObj.javaClass.getDeclaredField(part)
                    field.isAccessible = true
                    currentObj = field.get(currentObj) ?: return defaultValue
                }

                currentObj as? T ?: defaultValue
            } ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    /**
     * 设置嵌套对象的值
     */
    fun <T> setNestedValue(path: String, value: T): Boolean {
        return updateConfig { config ->
            try {
                val pathParts = path.split(".")
                var currentObj: Any = config

                // 遍历到倒数第二个对象（父对象）
                for (i in 0 until pathParts.size - 1) {
                    val field = currentObj.javaClass.getDeclaredField(pathParts[i])
                    field.isAccessible = true
                    currentObj = field.get(currentObj) ?: throw IllegalArgumentException("Path not found: $path")
                }

                // 设置最终字段的值
                val finalField = currentObj.javaClass.getDeclaredField(pathParts.last())
                finalField.isAccessible = true
                finalField.set(currentObj, value)

            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid config path: $path", e)
            }
        }
    }

    /**
     * 获取整个嵌套对象
     */
    inline fun <reified T> getNestedObject(path: String): T? {
        return try {
            currentConfig?.let { config ->
                val pathParts = path.split(".")
                var currentObj: Any = config

                for (part in pathParts) {
                    val field = currentObj.javaClass.getDeclaredField(part)
                    field.isAccessible = true
                    currentObj = field.get(currentObj) ?: return null
                }

                currentObj as? T
            }
        } catch (_: Exception) {
            null
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
                serializer.saveToFile(config, exportFile)
            } ?: false
        } catch (e: Exception) {
            Logger.error("Failed to export config", e)
            false
        }
    }

    /**
     * 从文件导入配置
     */
    fun importConfig(importFile: File): Boolean {
        return try {
            if (!importFile.exists()) return false

            val importedConfig = serializer.loadFromFile<AppConfig>(importFile)
            importedConfig?.let { config ->
                currentConfig = config
                saveConfig()
                true
            } ?: false
        } catch (e: Exception) {
            Logger.error("Failed to import config", e)
            false
        }
    }

    /**
     * 获取配置JSON字符串（用于调试或备份）
     */
    fun getConfigAsJson(): String {
        return currentConfig?.let { config ->
            serializer.toJson(config).toString(2)
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

    private fun notifyConfigChanged(changedKeys: List<String>) {
        listeners.forEach { it.onConfigChanged(changedKeys) }
    }
}