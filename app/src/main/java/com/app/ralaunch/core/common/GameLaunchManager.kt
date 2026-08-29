package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.game.ui.legacy.GameActivity
import timber.log.Timber
import java.io.File

/**
 * 游戏启动管理器
 *
 * 使用新的存储结构: games/{GameDirName}/game_info.json
 * 所有路径都是相对于游戏目录的
 */
class GameLaunchManager(private val context: Context) {

    companion object {
    }

    fun launchGame(game: GameItem): Boolean {
        Timber.d(">>> launchGame called for: ${game.displayedName}")
        Timber.i("launchGame called for: ${game.displayedName}, path: ${game.gameExePathRelative}")

        if (game.id.isBlank()) {
            Timber.e("Game storage ID is blank, cannot launch")
            return false
        }

        val gamePathFull = game.gameExePathFull
        if (gamePathFull == null) {
            Timber.e("Game storage path is null for game: ${game.displayedName}")
            return false
        }

        val gameFile = File(gamePathFull)

        if (!gameFile.exists() || !gameFile.isFile) {
            Timber.e("Assembly file not found: ${gameFile.absolutePath}")
            return false
        }

        Timber.i("Game runtime: dotnet")

        GameActivity.launch(
            context = context,
            gameStorageId = game.id
        )

        return true
    }
}
