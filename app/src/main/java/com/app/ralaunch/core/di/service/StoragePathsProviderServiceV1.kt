package com.app.ralaunch.core.di.service

import android.content.Context
import com.app.ralaunch.core.platform.AppConstants
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * 存储路径提供器。
 */
class StoragePathsProviderServiceV1(
    private val context: Context
) {
    fun gamesDirPathFull(): String =
        externalFilesDirPath().resolve(AppConstants.Dirs.GAMES).absolutePathString()

    fun logsDirPathFull(): String =
        externalFilesDirPath().resolve(AppConstants.Dirs.LOGS).absolutePathString()

    fun settingsFilePathFull(): String =
        Path(context.filesDir.absolutePath).resolve(AppConstants.Files.SETTINGS).absolutePathString()

    fun filesDirPathFull(): String = Path(context.filesDir.absolutePath).absolutePathString()

    fun runtimesDirPathFull(): String =
        Path(context.filesDir.absolutePath).resolve(AppConstants.Dirs.RUNTIMES).absolutePathString()

    fun legacyDotnetDirPathFull(): String =
        Path(context.filesDir.absolutePath).resolve(AppConstants.Dirs.LEGACY_DOTNET).absolutePathString()

    private fun externalFilesDirPath() = run {
        val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
            "External files directory is unavailable"
        }
        Path(externalFilesDir.absolutePath)
    }
}
