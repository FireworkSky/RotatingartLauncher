package com.app.ralaunch.utils.modules

import com.app.ralaunch.models.ModuleItem

/**
 * 已安装的静态补丁模块：模块登记项 + 解码后的静态补丁专属清单。
 * 公共元数据（name/description/version/author）经 [module] 读取。
 */
data class StaticPatch(val module: ModuleItem, val manifest: StaticPatchManifest) {
    val id: String get() = module.id

    companion object {
        /** 由模块登记项构造；meta 无法解码为静态补丁清单时返回 null。 */
        fun fromModule(module: ModuleItem): StaticPatch? =
            StaticPatchManifest.fromMeta(module.meta)?.let { StaticPatch(module, it) }
    }
}
