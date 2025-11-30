package com.app.ralaunch

import android.app.Application
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.utils.ConfigManager
import com.app.ralaunch.utils.Logger
import com.kyant.fishnet.Fishnet
import java.io.File

/**
 * 应用程序全局 Application 类
 *
 */
class RaLaunchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Logger.init(File(getExternalFilesDir("logs") ?: File(filesDir, "logs"), "app.log"))

        val logDir = File(getExternalFilesDir(null), "crash_logs.log").also { file -> file.parentFile?.mkdirs() }
        Fishnet.init(this, logDir.absolutePath)

        ConfigManager.getInstance().initialize(getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath,
            "app_config.json")

        LocaleManager.setLanguage(ConfigManager.getInstance().getConfig().language)
    }
}