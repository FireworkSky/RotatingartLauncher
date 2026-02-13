package com.app.ralaunch.shared.data.local

import android.content.Context
import com.app.ralaunch.shared.AppConstants
import java.io.File

actual class StoragePathsProvider(
    private val context: Context
) {
    actual fun gamesDirPathFull(): String {
        return File(context.getExternalFilesDir(null), AppConstants.Dirs.GAMES).also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath
    }

    actual fun controlLayoutsDirPathFull(): String {
        return File(context.filesDir, DataStoreConstants.CONTROL_LAYOUTS_DIR).also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath
    }

    actual fun settingsFilePathFull(): String {
        return File(context.filesDir, AppConstants.Files.SETTINGS).absolutePath
    }
}
