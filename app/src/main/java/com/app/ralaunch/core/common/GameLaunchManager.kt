package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.game.ui.legacy.GameActivity
import com.app.ralaunch.feature.game.ui.sdl2.GameActivitySdl2
import com.app.ralaunch.core.platform.runtime.SdlVersion
import com.app.ralaunch.core.logging.AppLog
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
        AppLog.d(TAG, ">>> launchGame called for: ${game.displayedName}")
        AppLog.i(TAG, "launchGame called for: ${game.displayedName}, path: ${game.gameExePathRelative}")

        if (game.id.isBlank()) {
            AppLog.e(TAG, "Game storage ID is blank, cannot launch")
            return false
        }

        val gamePathFull = game.gameExePathFull
        if (gamePathFull == null) {
            AppLog.e(TAG, "Game storage path is null for game: ${game.displayedName}")
            return false
        }

        val gameFile = File(gamePathFull)

        if (!gameFile.exists() || !gameFile.isFile) {
            AppLog.e(TAG, "Assembly file not found: ${gameFile.absolutePath}")
            return false
        }

        AppLog.i(TAG, "Game runtime: dotnet")

        // RAL: 依据「SDL 版本」设置选择入口 Activity。
        //  - SDL3：GameActivity（继承 SDLActivity，Android 视频后端）
        //  - SDL2：GameActivitySdl2（不继承 SDLActivity，Wayland 视频后端 + 应用内合成器）
        if (SettingsAccess.getInstance().selectedSdlVersion == SdlVersion.SDL2) {
            AppLog.i(TAG, "SDL version: SDL2 (Wayland)")
            GameActivitySdl2.launch(context = context, gameStorageId = game.id)
        } else {
            AppLog.i(TAG, "SDL version: SDL3 (Android)")
            GameActivity.launch(context = context, gameStorageId = game.id)
        }

        return true
    }
}
