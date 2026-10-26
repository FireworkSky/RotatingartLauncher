package com.app.ralaunch.utils.modules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * 静态补丁模块专属清单字段（meta.json 中除 ModuleItem 通用字段外的部分）。
 * id/name/description/version/author 由 [com.app.ralaunch.models.ModuleItem] 承载，
 * 此处只声明静态补丁语义；从模块 meta 原始 [JsonObject] 解码。
 *
 * [files] 单列表统一两种操作（[FileEntry.target] 支持 glob）：
 * - target 不含通配符（无 `*`/`?`）→ 定点替换：字面相对路径，目标可以原本不存在（覆盖 = 创建）；
 * - target 含通配符 → glob 替换：递归遍历游戏目录，替换所有"相对路径匹配模式"的已存在文件（不创建）。
 */
@Serializable
data class StaticPatchManifest(
    val targetGames: List<String> = emptyList(),
    val priority: Int = 0,
    val files: List<FileEntry> = emptyList()
) {
    /** 一个待替换文件：source 为模块内相对路径，target 为游戏目录内相对路径或 glob 模式。 */
    @Serializable
    data class FileEntry(val source: String, val target: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 从模块 meta JsonObject 解码；非法时 Timber.w 并返回 null。 */
        fun fromMeta(meta: JsonObject): StaticPatchManifest? =
            runCatching { json.decodeFromJsonElement(serializer(), meta) }
                .onFailure { Timber.w(it, "Invalid static patch meta") }
                .getOrNull()
    }
}
