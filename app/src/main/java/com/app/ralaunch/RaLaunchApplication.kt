package com.app.ralaunch

import android.app.Application
import android.content.pm.ApplicationInfo
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.utils.ConfigManager
import com.app.ralaunch.utils.RALaunchLogger
import com.kyant.fishnet.Fishnet
import java.io.File

/**
 * 应用程序全局 Application 类
 *
 */
class RaLaunchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val logDirectory = getExternalFilesDir("logs")?.absoluteFile ?: File(filesDir, "logs");

        val isDebuggable = BuildConfig.DEBUG

        RALaunchLogger.init(logDirectory = logDirectory, isDebugBuild = isDebuggable)

        val logDir = File(logDirectory, "crash_logs.log").also { file -> file.parentFile?.mkdirs() }
        Fishnet.init(this, logDir.absolutePath)

        ConfigManager.getInstance().initialize(getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath,
            "app_config.json")

        RALaunchLogger.i(ConfigManager.getInstance().getConfig().toString())

        LocaleManager.setLanguage(ConfigManager.getInstance().getConfig().language)
    }
}