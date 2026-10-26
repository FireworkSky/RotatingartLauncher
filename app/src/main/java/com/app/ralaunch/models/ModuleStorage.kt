package com.app.ralaunch.models

import com.app.ralaunch.jsonconfig.FlowJsonConfig
import kotlinx.serialization.Serializable

/**
 * 模块列表条目
 *
 * 存储在 modules/module_manager.json；[id] 即模块目录名（安卓包名格式）。
 */
@Serializable
data class ModuleEntry(
    /** 模块 id，等于 modules 下的目录名 */
    val id: String,
    val enabled: Boolean = true
)

/**
 * 模块列表索引
 *
 * 存储在 modules/module_manager.json，按序记录所有已登记模块及启用状态。
 */
@FlowJsonConfig
@Serializable
data class ModuleList(
    /** 有序模块条目列表 */
    val modules: List<ModuleEntry> = emptyList()
)
