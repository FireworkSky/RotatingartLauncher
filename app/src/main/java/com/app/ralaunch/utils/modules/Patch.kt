package com.app.ralaunch.utils.modules

import com.app.ralaunch.models.ModuleItem
import com.app.ralaunch.utils.ModuleManager
import java.nio.file.Path

/**
 * 已安装的补丁模块：模块登记项 + 解码后的补丁专属清单。
 * 公共元数据（name/description/version/author）经 [module] 读取。
 */
data class Patch(val module: ModuleItem, val manifest: PatchManifest) {
    val id: String get() = module.id

    /** 补丁入口程序集（dllFileName）的绝对路径。 */
    fun getEntryAssemblyAbsolutePath(): Path =
        ModuleManager.directory(module.id).resolve(manifest.dllFileName).toAbsolutePath().normalize()

    companion object {
        /** 由模块登记项构造；meta 无法解码为补丁清单时返回 null。 */
        fun fromModule(module: ModuleItem): Patch? =
            PatchManifest.fromMeta(module.meta)?.let { Patch(module, it) }
    }
}
