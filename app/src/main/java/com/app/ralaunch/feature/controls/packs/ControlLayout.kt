package com.app.ralaunch.feature.controls.packs

import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.core.common.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * 控件布局配置
 *
 * 包含布局的所有控件数据，支持 JSON 序列化/反序列化
 * 仅保留单一 controls 列表。
 */
@Serializable
data class ControlLayout(
    /** 布局名称 */
    var name: String = "Custom Layout",
    
    /** 布局版本（用于兼容性检查） */
    var version: Int = 3,

    /** 控件列表 */
    var controls: MutableList<ControlData> = mutableListOf()
) {
    /** 布局 ID（运行时使用，不序列化） */
    @Transient
    var id: String = "pack_${System.currentTimeMillis()}"

    fun toJson(): String = json.encodeToString(serializer(), this)

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
                val layout = json.decodeFromString<ControlLayout>(jsonString)
                sanitizeControls(layout)
                layout
            } catch (e: Exception) {
                AppLogger.error("ControlLayout", "Failed to parse layout JSON", e)
                null
            }
        }

        /**
         * 修复控件可见性问题 (兼容旧版布局)
         * 
         * 旧版布局使用颜色自带的 alpha 通道控制透明度 (如 #4D000000 = 30% 黑色),
         * 新版代码新增了独立的 opacity 属性, 渲染时 paint.alpha = (opacity * 255)
         * 会完全覆盖颜色的 alpha。
         * 
         * 仅当 opacity / borderOpacity / textOpacity **全部**接近 0 时才执行修复,
         * 这表示是旧格式迁移问题。如果其中任意一个有意义的值 (>0.02),
         * 说明用户已经有意设置了各自的透明度 (例如 opacity=0 + borderOpacity=1
         * 表示"无背景只有边框"), 不应被篡改。
         */
        private fun sanitizeControls(layout: ControlLayout) {
            val minThreshold = 0.02f
            var fixedCount = 0

            val allControls = layout.controls.toMutableList()

            for (control in allControls) {
                // 仅当所有三个透明度都接近 0 时才认为是旧格式需要迁移
                // 如果任意一个 > threshold，说明用户已有意设置，跳过
                val bgNearZero = control.opacity < minThreshold
                val borderNearZero = control.borderOpacity < minThreshold
                val textNearZero = control.textOpacity < minThreshold

                if (!bgNearZero && !borderNearZero && !textNearZero) {
                    continue // 所有值都正常，跳过
                }

                // 如果有任何一个值是有意义的，说明用户主动设置了透明度，不应修改
                if (!bgNearZero || !borderNearZero || !textNearZero) {
                    continue // 部分有值，说明是用户有意设计（如透明背景+可见边框），跳过
                }

                // 到这里: 三个透明度全部接近 0，属于旧格式迁移
                var fixed = false

                // 修复背景透明度: opacity ≈ 0 → 从 bgColor alpha 提取
                val bgAlpha = (control.bgColor ushr 24) / 255f
                if (bgAlpha > minThreshold) {
                    control.opacity = bgAlpha
                    AppLogger.debug("ControlLayout",
                        "  '${control.name}': opacity 0 → ${bgAlpha} (from bgColor alpha #${Integer.toHexString(control.bgColor ushr 24).uppercase()})")
                } else {
                    control.opacity = 0.35f
                    AppLogger.debug("ControlLayout",
                        "  '${control.name}': opacity 0 → 0.35 (default, bgColor alpha also 0)")
                }
                fixed = true

                // 修复边框透明度: borderOpacity ≈ 0 → 从 strokeColor alpha 提取
                val borderAlpha = (control.strokeColor ushr 24) / 255f
                if (borderAlpha > minThreshold) {
                    control.borderOpacity = borderAlpha
                } else {
                    control.borderOpacity = 0.5f
                }

                // 修复文本透明度: textOpacity ≈ 0 → 从 textColor alpha 提取
                val textAlpha = (control.textColor ushr 24) / 255f
                if (textAlpha > minThreshold) {
                    control.textOpacity = textAlpha
                } else {
                    control.textOpacity = 0.9f
                }

                if (fixed) fixedCount++
            }

            if (fixedCount > 0) {
                AppLogger.info("ControlLayout",
                    "Fixed $fixedCount controls with invisible opacity (in-memory only) in layout '${layout.name}'")
            }
        }
    }
}
