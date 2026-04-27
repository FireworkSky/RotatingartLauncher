package com.app.ralaunch.feature.settings.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.core.platform.runtime.AndroidRendererRegistry
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.service.LogExportHelper
import com.app.ralaunch.core.platform.android.provider.RaLaunchFileProvider
import com.app.ralaunch.core.ui.dialog.RendererOption
import com.app.ralaunch.core.model.BackgroundType
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.feature.settings.vm.SettingsEvent
import com.app.ralaunch.feature.settings.vm.SettingsViewModel
import com.app.ralaunch.core.theme.AppThemeState
import com.app.ralaunch.feature.sponsor.ui.SponsorsActivity
import com.app.ralaunch.core.common.util.LocaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream

internal const val RESTORE_SETTINGS_AFTER_RECREATE_KEY = "restore_settings_after_recreate"

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
            
            val settingsRepository: ISettingsRepositoryServiceV2 =
                KoinJavaComponent.get(ISettingsRepositoryServiceV2::class.java)

            val oldPath = settingsRepository.getSettingsSnapshot().backgroundImagePath
            if (!oldPath.isNullOrEmpty()) {
                val oldFile = File(oldPath)
                if (oldFile.exists()) {
                    FileUtils.deleteFileWithinRoot(oldFile, backgroundDir)
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
                AppThemeState.updateBackgroundType(BackgroundType.IMAGE)
                AppThemeState.updateBackgroundImagePath(newPath)
                AppThemeState.updateBackgroundVideoPath("")
                AppThemeState.updateBackgroundOpacity(90)

                viewModel.onEvent(SettingsEvent.SetBackgroundType(BackgroundType.IMAGE))
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
            val settingsRepository: ISettingsRepositoryServiceV2 =
                KoinJavaComponent.get(ISettingsRepositoryServiceV2::class.java)

            settingsRepository.update {
                backgroundVideoPath = newPath
                backgroundType = BackgroundType.VIDEO
                backgroundImagePath = ""
                backgroundOpacity = 90
            }

            withContext(Dispatchers.Main) {
                AppThemeState.updateBackgroundType(BackgroundType.VIDEO)
                AppThemeState.updateBackgroundVideoPath(newPath)
                AppThemeState.updateBackgroundImagePath("")
                AppThemeState.updateBackgroundOpacity(90)

                viewModel.onEvent(SettingsEvent.SetBackgroundType(BackgroundType.VIDEO))
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
        logExportHelper().getAppLogFiles().firstOrNull()?.readLines()?.takeLast(LOG_VIEW_LIMIT) ?: emptyList()
    } catch (e: Exception) {
        listOf(context.getString(R.string.settings_logs_read_failed, e.message ?: ""))
    }
}

internal suspend fun exportLogs(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val logs = logExportHelper().buildExportContent()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(logs.toByteArray(Charsets.UTF_8))
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

internal suspend fun shareLogs(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val logs = logExportHelper().buildExportContent()

            val shareDir = File(context.cacheDir, "shared_logs").apply {
                mkdirs()
            }
            shareDir.listFiles { file ->
                LogFilePolicy.isManagedLogFile(file)
            }?.forEach(File::delete)

            val logFile = File(shareDir, LogFilePolicy.appLogFileName())
            logFile.writeText(logs, Charsets.UTF_8)

            val fileUri = FileProvider.getUriForFile(
                context,
                RaLaunchFileProvider.AUTHORITY,
                logFile
            )

            val shareTitle = context.getString(R.string.export_share_log)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "${context.getString(R.string.app_name)} ${context.getString(R.string.settings_developer_export_logs_title)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(shareIntent, shareTitle).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.log_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private const val LOG_VIEW_LIMIT = 500

private fun logExportHelper(): LogExportHelper =
    KoinJavaComponent.get(LogExportHelper::class.java)

internal fun clearAppCache(context: Context) {
    try {
        FileUtils.clearDirectory(context.cacheDir)
        context.externalCacheDir?.let(FileUtils::clearDirectory)
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
    val settingsRepository: ISettingsRepositoryServiceV2 =
        KoinJavaComponent.get(ISettingsRepositoryServiceV2::class.java)
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
    val settingsRepository: ISettingsRepositoryServiceV2 =
        KoinJavaComponent.get(ISettingsRepositoryServiceV2::class.java)
    kotlinx.coroutines.runBlocking {
        settingsRepository.update {
            themeColor = colorId
        }
    }
    AppThemeState.updateThemeColor(colorId)
    Toast.makeText(context, context.getString(R.string.theme_color_changed), Toast.LENGTH_SHORT).show()
}

internal fun recreateActivityForUiRefresh(activity: Activity) {
    if (activity.isFinishing || activity.isDestroyed) return

    activity.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(RESTORE_SETTINGS_AFTER_RECREATE_KEY, true)
        .apply()

    activity.recreate()
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
        AndroidRendererRegistry.getCompatibleRenderers().forEach { info ->
            add(
                RendererOption(
                    renderer = info.id,
                    name = AndroidRendererRegistry.getRendererDisplayName(info.id),
                    description = AndroidRendererRegistry.getRendererDescription(info.id)
                )
            )
        }
    }
}
