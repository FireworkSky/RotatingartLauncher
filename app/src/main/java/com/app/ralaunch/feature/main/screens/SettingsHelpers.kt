package com.app.ralaunch.feature.main.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
import com.app.ralaunch.shared.core.component.dialogs.RendererOption
import com.app.ralaunch.shared.core.model.domain.BackgroundType
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
import com.app.ralaunch.shared.feature.settings.*
import com.app.ralaunch.shared.core.theme.AppThemeState
import com.app.ralaunch.feature.sponsor.SponsorsActivity
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.common.util.LogcatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream

// ==================== 背景处理 ====================

internal suspend fun handleImageSelection(context: Context, uri: Uri, viewModel: SettingsViewModel) {
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
            
            val settingsRepository: SettingsRepositoryV2 =
                KoinJavaComponent.get(SettingsRepositoryV2::class.java)

            val oldPath = settingsRepository.getSettingsSnapshot().backgroundImagePath
            if (!oldPath.isNullOrEmpty()) {
                val oldFile = File(oldPath)
                if (oldFile.exists() && oldFile.parentFile == backgroundDir) {
                    oldFile.delete()
                }
            }

            val newPath = destFile.absolutePath
            settingsRepository.update {
                backgroundImagePath = newPath
                backgroundType = BackgroundType.IMAGE
                backgroundVideoPath = ""
                backgroundOpacity = 90
            }

            withContext(Dispatchers.Main) {
                AppThemeState.updateBackgroundType(1)
                AppThemeState.updateBackgroundImagePath(newPath)
                AppThemeState.updateBackgroundVideoPath("")
                AppThemeState.updateBackgroundOpacity(90)

                viewModel.onEvent(SettingsEvent.SetBackgroundType(1))
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, context.getString(R.string.appearance_background_image_set), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.settings_background_set_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal suspend fun handleVideoSelection(context: Context, uri: Uri, viewModel: SettingsViewModel) {
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
            val settingsRepository: SettingsRepositoryV2 =
                KoinJavaComponent.get(SettingsRepositoryV2::class.java)

            settingsRepository.update {
                backgroundVideoPath = newPath
                backgroundType = BackgroundType.VIDEO
                backgroundImagePath = ""
                backgroundOpacity = 90
            }

            withContext(Dispatchers.Main) {
                AppThemeState.updateBackgroundType(2)
                AppThemeState.updateBackgroundVideoPath(newPath)
                AppThemeState.updateBackgroundImagePath("")
                AppThemeState.updateBackgroundOpacity(90)

                viewModel.onEvent(SettingsEvent.SetBackgroundType(2))
                viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(90))
                Toast.makeText(context, context.getString(R.string.appearance_background_video_set), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.settings_background_set_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ==================== 工具函数 ====================

internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.settings_cannot_open_url), Toast.LENGTH_SHORT).show()
    }
}

internal fun openSponsorsPage(context: Context) {
    try {
        context.startActivity(Intent(context, SponsorsActivity::class.java))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.settings_cannot_open_sponsors), Toast.LENGTH_SHORT).show()
    }
}

internal fun loadLogs(context: Context): List<String> {
    return try {
        LogcatReader.getInstance()?.logFile?.readLines()?.takeLast(500) ?: emptyList()
    } catch (e: Exception) {
        listOf(context.getString(R.string.settings_logs_read_failed, e.message ?: ""))
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
                Toast.makeText(context, context.getString(R.string.log_exported), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.log_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun clearAppCache(context: Context) {
    try {
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
        Toast.makeText(context, context.getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.settings_clear_cache_failed), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, context.getString(R.string.patches_reinstalled), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.patches_reinstall_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

internal fun applyOpacityChange(opacity: Int) {
    AppThemeState.updateBackgroundOpacity(opacity)
}

internal fun applyVideoSpeedChange(speed: Float) {
    AppThemeState.updateVideoPlaybackSpeed(speed)
}

internal fun restoreDefaultBackground(context: Context) {
    val settingsRepository: SettingsRepositoryV2 = KoinJavaComponent.get(SettingsRepositoryV2::class.java)
    kotlinx.coroutines.runBlocking {
        settingsRepository.update {
            backgroundType = BackgroundType.DEFAULT
            backgroundImagePath = ""
            backgroundVideoPath = ""
            backgroundOpacity = 0
            videoPlaybackSpeed = 1.0f
        }
    }
    AppThemeState.restoreDefaultBackground()
}

internal fun applyThemeColor(context: Context, colorId: Int) {
    val settingsRepository: SettingsRepositoryV2 = KoinJavaComponent.get(SettingsRepositoryV2::class.java)
    kotlinx.coroutines.runBlocking {
        settingsRepository.update {
            themeColor = colorId
        }
    }
    AppThemeState.updateThemeColor(colorId)
    Toast.makeText(context, context.getString(R.string.theme_color_changed), Toast.LENGTH_SHORT).show()
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

internal fun isChineseLanguage(context: Context): Boolean {
    val configuredLanguage = LocaleManager.getLanguage(context).trim().lowercase()
    val normalizedConfiguredLanguage = configuredLanguage
        .substringBefore('-')
        .substringBefore('_')

    if (normalizedConfiguredLanguage == LocaleManager.LANGUAGE_ZH || configuredLanguage == "简体中文") {
        return true
    }

    val shouldFollowSystem = normalizedConfiguredLanguage == LocaleManager.LANGUAGE_AUTO ||
        configuredLanguage == "follow system" ||
        configuredLanguage == "跟随系统" ||
        configuredLanguage.isBlank()

    if (shouldFollowSystem) {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "zh"
    }
    return normalizedConfiguredLanguage == LocaleManager.LANGUAGE_ZH
}

internal fun buildRendererOptions(): List<RendererOption> {
    return buildList {
        RendererRegistry.getCompatibleRenderers().forEach { info ->
            add(
                RendererOption(
                    renderer = info.id,
                    name = RendererRegistry.getRendererDisplayName(info.id),
                    description = RendererRegistry.getRendererDescription(info.id)
                )
            )
        }
    }
}
