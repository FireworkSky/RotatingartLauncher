package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.FileUtils
import java.io.File

class GameDeletionManager(private val context: Context) {

    fun deleteGameFiles(game: GameItem): Boolean {
        return try {
            val gameDir = getGameDirectory(game) ?: return false

            val dirPath = gameDir.absolutePath
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                return false
            }

            // Thay Paths.get() bang File
            FileUtils.deleteDirectoryRecursively(gameDir)
        } catch (e: Exception) {
            AppLogger.error("GameDeletionManager", "Error deleting game files: ${e.message}")
            false
        }
    }

    fun deleteGame(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            // Thay Paths.get() bang File
            FileUtils.deleteDirectoryRecursively(File(path))
        } catch (e: Exception) {
            AppLogger.error("GameDeletionManager", "Error deleting game: ${e.message}")
            false
        }
    }

    private fun getGameDirectory(game: GameItem): File? {
        if (game.storageRootPathRelative.isBlank()) return null

        val gamesDir = File(context.getExternalFilesDir(null), AppConstants.Dirs.GAMES)
        return File(gamesDir, game.storageRootPathRelative)
    }
}
