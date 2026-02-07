package com.app.ralaunch.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.patch.PatchManager
import com.app.ralaunch.shared.ui.screens.settings.*
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.sponsor.SponsorsActivity
import com.app.ralaunch.ui.compose.settings.SettingsViewModel as AppSettingsViewModel
import com.app.ralaunch.ui.main.MainActivityCompose
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.LocaleManager
import com.app.ralaunch.utils.LogcatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream

// ==================== 背景处理 ====================

internal suspend fun handleImageSelection(context: Context, uri: Uri, viewModel: AppSettingsViewModel) {
    withContext(Dispatchers.IO) {
        try {
            val backgroundDir = File(context.filesDir, "backgrounds")
            if (!backgroundDir.exists()) backgroundDir.mkdirs()
            
            val destFile = File(backgroundDir, "background_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val oldPath = SettingsManager.getInstance().backgroundImagePath
            if (!oldPath.isNullOrEmpty()) {
                val oldFile = File(oldPath)
                if (oldFile.exists() && oldFile.parentFile == backgroundDir) {
                    oldFile.delete()
                }
            }
            
            val newPath = destFile.absolutePath
            SettingsManager.getInstance().apply {
                backgroundImagePath = newPath
                backgroundType = "image"
                backgroundVideoPath = ""
                backgroundOpacity = 90
            }
            
            withContext(Dispatchers.Main) {
                AppThemeState.updateBackgroundType(1)
                AppThemeState.updateBackgroundImagePath(newPath)
                AppThemeState.updateBackgroundVideoPath("")
                AppThemeState.updateBackgroundOpacity(90)
                
                viewModel.setBackgroundType(1)
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, "背景图片已设置", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "设置背景失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal suspend fun handleVideoSelection(context: Context, uri: Uri, viewModel: AppSettingsViewModel) {
    withContext(Dispatchers.IO) {
        try {
            val backgroundDir = File(context.filesDir, "backgrounds")
            if (!backgroundDir.exists()) backgroundDir.mkdirs()
            
            val destFile = File(backgroundDir, "background_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val newPath = destFile.absolutePath
            SettingsManager.getInstance().apply {
                backgroundVideoPath = newPath
                backgroundType = "video"
                backgroundImagePath = ""
                backgroundOpacity = 90
            }
            
            withContext(Dispatchers.Main) {
                AppThemeState.updateBackgroundType(2)
                AppThemeState.updateBackgroundVideoPath(newPath)
                AppThemeState.updateBackgroundImagePath("")
                AppThemeState.updateBackgroundOpacity(90)
                
                viewModel.setBackgroundType(2)
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, "背景视频已设置", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "设置背景失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ==================== 工具函数 ====================

internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

internal fun openSponsorsPage(context: Context) {
    try {
        context.startActivity(Intent(context, SponsorsActivity::class.java))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开赞助商页面", Toast.LENGTH_SHORT).show()
    }
}

internal fun loadLogs(context: Context): List<String> {
    return try {
        LogcatReader.getInstance()?.logFile?.readLines()?.takeLast(500) ?: emptyList()
    } catch (e: Exception) {
        listOf("无法读取日志: ${e.message}")
    }
}

internal fun clearLogs(context: Context) {
    try {
        LogcatReader.getInstance()?.logFile?.writeText("")
    } catch (e: Exception) {
        AppLogger.error("Settings", "清除日志失败", e)
    }
}

internal suspend fun exportLogs(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val logs = loadLogs(context)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(logs.joinToString("\n").toByteArray())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "日志已导出", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun clearAppCache(context: Context) {
    try {
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "清除缓存失败", Toast.LENGTH_SHORT).show()
    }
}

internal fun forceReinstallPatches(context: Context) {
    Thread {
        try {
            val patchManager: PatchManager? = try {
                KoinJavaComponent.getOrNull(PatchManager::class.java)
            } catch (e: Exception) { null }
            patchManager?.let { pm ->
                PatchManager.installBuiltInPatches(pm, true)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "补丁已重新安装", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "重装补丁失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

internal fun applyOpacityChange(context: Context, opacity: Int) {
    SettingsManager.getInstance().backgroundOpacity = opacity
    MainActivityCompose.instance?.updateVideoBackgroundOpacity(opacity)
}

internal fun applyVideoSpeedChange(context: Context, speed: Float) {
    SettingsManager.getInstance().videoPlaybackSpeed = speed
    MainActivityCompose.instance?.updateVideoBackgroundSpeed(speed)
}

internal fun restoreDefaultBackground(context: Context) {
    SettingsManager.getInstance().apply {
        backgroundType = "default"
        backgroundImagePath = ""
        backgroundVideoPath = ""
        backgroundOpacity = 0
        videoPlaybackSpeed = 1.0f
    }
    MainActivityCompose.instance?.updateVideoBackground()
}

internal fun applyThemeColor(context: Context, colorId: Int) {
    SettingsManager.getInstance().themeColor = colorId
    AppThemeState.updateThemeColor(colorId)
    Toast.makeText(context, "主题颜色已更改", Toast.LENGTH_SHORT).show()
}

internal fun getLanguageCode(languageName: String): String {
    return when (languageName) {
        "简体中文" -> LocaleManager.LANGUAGE_ZH
        "English" -> LocaleManager.LANGUAGE_EN
        "Русский" -> LocaleManager.LANGUAGE_RU
        "Español" -> LocaleManager.LANGUAGE_ES
        else -> LocaleManager.LANGUAGE_AUTO
    }
}

internal fun getRendererCode(rendererName: String): String {
    return when (rendererName) {
        "自动选择" -> "auto"
        "Native OpenGL ES 3" -> "native"
        "GL4ES" -> "gl4es"
        "GL4ES + ANGLE" -> "gl4es+angle"
        "MobileGlues" -> "mobileglues"
        "ANGLE" -> "angle"
        else -> "auto"
    }
}
