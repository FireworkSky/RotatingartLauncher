package com.app.ralaunch.core.common

import android.content.Context
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.game.ui.legacy.GameActivity
import com.app.ralaunch.feature.game.ui.sdl2.GameActivitySdl2
import com.app.ralaunch.core.platform.runtime.SdlVersion
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.steam.data.SteamCli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        val gameDir = gameFile.parentFile
        val steamInstall = gameDir != null &&
            (File(gameDir, "steam_appid.txt").exists() || File(gameDir, "steam-shim").exists())
        if (steamInstall && gameDir != null) {
            // Steam 版游戏：先补上 Steamworks.NET shim，再把登录账号的 SteamID/昵称写进
            // steam-shim/identity.json，最后拉起游戏（shim 靠它把成就/云存档挂到账号上）
            scope.launch {
                runCatching {
                    // Terraria 的 Steam 版 exe 会直接 P/Invoke steam_api：没有 shim 就会
                    // “Please launch the game from your Steam client” 然后 Environment.Exit
                    if (File(gameDir, "Terraria.exe").exists()) {
                        SteamCli.ensureSteamworksShim(context, gameDir)
                    }
                    SteamCli.writeIdentityFile(context, gameDir)
                }.onFailure { AppLog.w(TAG, "Steam 启动前置失败: ${it.message}") }
                scope.launch(Dispatchers.Main) { startGameActivity(game) }
            }
            return true
        }

        startGameActivity(game)
        return true
    }

    private fun startGameActivity(game: GameItem) {
        // RAL: 依据「SDL 版本」设置选择入口 Activity。
        //  - SDL3：GameActivity（继承 SDLActivity，Android 视频后端）
        //  - SDL2：GameActivitySdl2（不继承 SDLActivity，Android 原生后端 + 应用内 Surface）
        if (SettingsAccess.getInstance().selectedSdlVersion == SdlVersion.SDL2) {
            AppLog.i(TAG, "SDL version: SDL2 (native Android backend)")
            GameActivitySdl2.launch(context = context, gameStorageId = game.id)
        } else {
            AppLog.i(TAG, "SDL version: SDL3 (Android)")
            GameActivity.launch(context = context, gameStorageId = game.id)
        }
    }
}
