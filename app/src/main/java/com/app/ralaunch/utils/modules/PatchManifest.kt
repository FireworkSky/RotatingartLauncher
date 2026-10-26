package com.app.ralaunch.utils.modules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * 补丁模块专属清单字段（meta.json 中除 ModuleItem 通用字段外的部分）。
 * id/name/description/version/author 由 [com.app.ralaunch.models.ModuleItem] 承载，
 * 此处只声明补丁语义；从模块 meta 原始 [JsonObject] 解码。
 */
@Serializable
data class PatchManifest(
    val targetGames: List<String> = emptyList(),
    val dllFileName: String = "",
    val entryPoint: EntryPoint? = null,
    val priority: Int = 0,
    val dependencies: Dependencies? = null
) {
    @Serializable
    data class EntryPoint(val typeName: String = "", val methodName: String = "")

    @Serializable
    data class Dependencies(val libs: List<String> = emptyList())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 从模块 meta JsonObject 解码；非法时 Timber.w 并返回 null。 */
        fun fromMeta(meta: JsonObject): PatchManifest? =
            runCatching { json.decodeFromJsonElement(serializer(), meta) }
                .onFailure { Timber.w(it, "Invalid patch meta") }
                .getOrNull()
    }
}
