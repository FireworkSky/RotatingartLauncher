package com.app.ralaunch.core.config

import android.content.Context
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.jsonconfig.AppSettingsFlowJsonConfigGenerated
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * 应用配置（settings.json 唯一持久化来源），直接引用本对象，不经 Koin 注入。
 *
 * 继承 [AppSettingsFlowJsonConfigGenerated]（FlowJsonConfig）：
 * - `AppConfig.c.x`：字段读写（仅内存状态，不落盘）；
 * - `AppConfig.s.x`：字段读写，写后自动落盘（s = save）；
 * - [value]：当前配置快照；[state] / [flowOf]：监听配置变化；
 * - [update]：多字段原子更新（不落盘）；[updateSave]：多字段原子更新并落盘；[load]：启动时加载磁盘配置。
 */
object AppConfig : AppSettingsFlowJsonConfigGenerated() {

    override val configPath: String by lazy {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        File(context.filesDir, AppConstants.Files.SETTINGS).absolutePath
    }
}
