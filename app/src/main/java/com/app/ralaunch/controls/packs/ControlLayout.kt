package com.app.ralaunch.controls.packs

import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.utils.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * 控件布局配置
 * 
 * 包含布局的所有控件数据，支持 JSON 序列化/反序列化
 * 替代原有的 ControlConfig
 */
@Serializable
data class ControlLayout(
    /** 布局名称 */
    var name: String = "Custom Layout",
    
    /** 布局版本（用于兼容性检查） */
    var version: Int = 2,
    
    /** 控件列表 */
    var controls: MutableList<ControlData> = mutableListOf()
) {
    /** 布局 ID（运行时使用，不序列化） */
    @Transient
    var id: String = System.currentTimeMillis().toString()

    fun toJson(): String = json.encodeToString(this)

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJson())
    }

    fun saveTo(path: String) = saveTo(File(path))

    /**
     * 创建当前布局的深拷贝
     */
    fun deepCopy(): ControlLayout {
        val jsonString = toJson()
        return loadFromJson(jsonString)?.copy()?.also { it.id = this.id } ?: this
    }

    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun loadFrom(file: File): ControlLayout? = loadFrom(file.path)

        fun loadFrom(path: String): ControlLayout? {
            val file = File(path)
            if (!file.exists()) {
                throw IOException("Layout file not found: $path")
            }
            return loadFromJson(file.readText())
        }

        fun loadFromJson(jsonString: String): ControlLayout? {
            return try {
                json.decodeFromString<ControlLayout>(jsonString)
            } catch (e: Exception) {
                AppLogger.error("ControlLayout", "Failed to parse layout JSON", e)
                null
            }
        }
    }
}

