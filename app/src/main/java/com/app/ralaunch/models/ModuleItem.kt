package com.app.ralaunch.models

import com.app.ralaunch.utils.ModuleManager
import kotlinx.serialization.json.JsonObject

/**
 * 模块运行时数据模型
 *
 * [name]/[description]/[version]/[author] 是 meta.json 的通用元数据字段（缺失或非字符串时为空字符串）；
 * [meta] 是 meta.json 的完整原始内容（含所有字段），按 type 透传给消费方解码；
 * 管理器永不回写 meta.json。[enabled] 是管理器状态，只存在于 module_manager.json。
 * 全字段不可变，Flow 分发浅拷贝列表即可。
 *
 * @property id 模块 id（安卓包名格式），等于 modules 下的目录名
 * @property type 模块类型标识符（由 meta.json 声明）
 * @property name 模块显示名称
 * @property description 模块描述
 * @property version 模块版本号（"." 分段数字，供更新比较）
 * @property author 模块作者
 * @property meta meta.json 原始内容
 * @property enabled 是否启用（module_manager.json 中的管理状态）
 */
data class ModuleItem(
    val id: String,
    val type: String,
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val meta: JsonObject,
    val enabled: Boolean
) {
    /**
     * 模块存储根目录的绝对路径
     *
     * 经 [ModuleManager.directory] 解析；目录缺失、越界或未初始化时返回 null。
     */
    val storageRootPathFull: String?
        get() = runCatching { ModuleManager.directory(id).toString() }.getOrNull()
}
