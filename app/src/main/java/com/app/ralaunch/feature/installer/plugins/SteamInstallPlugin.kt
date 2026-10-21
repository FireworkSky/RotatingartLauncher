package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.installer.BaseInstallPlugin
import com.app.ralaunch.feature.installer.GameDefinition
import com.app.ralaunch.feature.installer.GameDetectResult
import com.app.ralaunch.feature.installer.InstallCallback
import com.app.ralaunch.feature.installer.ModLoaderDetectResult
import com.app.ralaunch.feature.steam.data.SteamCatalog
import com.app.ralaunch.feature.steam.data.SteamGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Steam 在线下载的安装插件：本身不做解压，只负责在 SteamContentCli 把内容落盘之后
 * 写 game_info.json / 生成 GameItem（复用基类的图标与元数据逻辑）。
 */
class SteamInstallPlugin : BaseInstallPlugin() {
    override val pluginId = "steam"
    override val displayName = "Steam"
    override val supportedGames: List<GameDefinition> = listOf(
        GameDefinition.TERRARIA,
        GameDefinition.FEZ,
        GameDefinition.CELESTE,
        GameDefinition.STARDEW_VALLEY,
        GameDefinition.TMODLOADER,
    )

    override fun detectGame(gameFile: File): GameDetectResult? = null

    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? = null

    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRoot: File,
        callback: InstallCallback
    ) {
        callback.onError("Steam 游戏请从下载页安装")
    }

    fun definitionFor(game: SteamGame): GameDefinition? = when (game.appId) {
        SteamCatalog.TERRARIA_APP_ID -> GameDefinition.TERRARIA
        SteamCatalog.TMODLOADER_APP_ID -> GameDefinition.TMODLOADER
        224760 -> GameDefinition.FEZ
        413150 -> GameDefinition.STARDEW_VALLEY
        504230 -> GameDefinition.CELESTE
        else -> null
    }

    /**
     * 内容下载完成后生成游戏条目。
     * @param storageRoot 由 IGameRepositoryServiceV3.createGameStorageRoot 创建的目录
     * @param actualGameDir 真正放游戏文件的目录（通常等于 storageRoot，或它的子目录）
     */
    suspend fun buildGameItem(
        storageRoot: File,
        actualGameDir: File,
        definition: GameDefinition,
        launchTarget: String?,
        runtimeOverride: String?,
    ): GameItem = withContext(Dispatchers.IO) {
        val icon = extractIcon(actualGameDir, definition)
        createGameInfo(storageRoot, actualGameDir, definition, icon, launchTarget, runtimeOverride)
        createGameItem(definition, storageRoot, actualGameDir, icon, launchTarget, runtimeOverride)
    }
}
