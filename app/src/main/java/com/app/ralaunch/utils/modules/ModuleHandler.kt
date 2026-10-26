package com.app.ralaunch.utils.modules

/**
 * 模块类型处理器：负责一种 [ModuleManager] 模块类型的类型语义。
 *
 * handler 经 [ModuleManager.registerHandler] 按 [type] 注册；ModuleManager 只登记模块
 * 与透传 meta，不解析类型专属字段——各 handler 从 [com.app.ralaunch.models.ModuleItem.meta]
 * （meta.json 原始 JsonObject）自行解码所需内容。类型专属 API 定义在 handler 本体，
 * 消费方直接调用 handler 单例（如 [PatchModuleHandler]）。
 */
interface ModuleHandler {
    /** 负责的模块类型（meta.json "type" 字段） */
    val type: String
}
