package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.feature.game.legacy.GameActivity
import com.app.ralaunch.core.common.util.AppLogger
import java.io.File

/**
 * 游戏启动管理器
 *
 * 使用新的存储结构: games/{GameDirName}/game_info.json
 * 所有路径都是相对于游戏目录的
 */
class GameLaunchManager(private val context: Context) {

    companion object {
        private const val TAG = "GameLaunchManager"
    }

    fun launchGame(game: GameItem): Boolean {
        android.util.Log.d(TAG, ">>> launchGame called for: ${game.displayedName}")
        AppLogger.info(TAG, "launchGame called for: ${game.displayedName}, path: ${game.gameExePathRelative}")

        if (game.id.isBlank()) {
            AppLogger.error(TAG, "Game storage ID is blank, cannot launch")
            return false
        }

        val gamePathFull = game.gameExePathFull
        if (gamePathFull == null) {
            AppLogger.error(TAG, "Game storage path is null for game: ${game.displayedName}")
            return false
        }

        val gameFile = File(gamePathFull)

        if (!gameFile.exists() || !gameFile.isFile) {
            AppLogger.error(TAG, "Assembly file not found: ${gameFile.absolutePath}")
            return false
        }

        AppLogger.info(TAG, "Game runtime: dotnet")

        GameActivity.launch(
            context = context,
            gameStorageId = game.id
        )

        return true
    }
}
