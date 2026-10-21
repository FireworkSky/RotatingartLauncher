package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.core.logging.AppLog
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
     * 用 RAL 自带的 Android 版 MonoMod 替换游戏目录里的 MonoMod：
     * tModLoader 1.4 自带的 MonoMod.Core 在 Android 上会
     * `PlatformTriple.CreateCurrentSystem()` 抛 NotImplementedException，必须换成魔改版。
     */
    fun applyMonoModPatches(gameDir: File): Boolean =
        runCatching { installMonoMod(gameDir) }.getOrDefault(false)

    /** 删掉 Steam 包里带进来的 FNA.dll.config（Linux dllmap，Android 用不到，会挡 FNA 初始化） */
    fun cleanupFnaConfig(gameDir: File) = removeStaleFnaConfig(gameDir)

    /**
     * 把 Steam 下载下来的 tModLoader 1.3 depot 平铺覆盖到 Terraria 目录。
     *
     * 与 GOG 的 tModLoader 1.3 安装走同一套规则（见 BaseInstallPlugin）：
     * tModLoader.exe 与 Terraria.exe 同级、跳过 mono BCL / glibc 平台库 / 旧 FNA、
     * 最后换成包装 exe + 配套 FNA。
     *
     * depot 里的 `Content/` **必须覆盖**：tModLoader 1.3 是按 Terraria 1.3.5.3 编的，
     * 它要的 `Images/ChestStack_0.xnb` 这类文件在 1.4.5.8 的 Content 里已经没了，
     * 主素材目录（`../Terraria/Content`）+ 备用素材目录（`./Content`）都会找不到 →
     * `Main.LoadTextures` 直接 ContentLoadException 崩掉。
     */
    fun overlayLegacyTModLoader(downloadDir: File, terrariaDir: File): Boolean = runCatching {
        val sourceDir = findLegacyRoot(downloadDir)
        copyLegacyOverlay(sourceDir, terrariaDir, keepExistingGameFiles = false)
        installLegacyBootstrap(terrariaDir)
        AppLog.i(TAG, "tModLoader 1.3 已平铺到 ${terrariaDir.absolutePath}")
        true
    }.getOrElse {
        AppLog.e(TAG, "tModLoader 1.3 覆盖失败: ${it.message}")
        false
    }

    companion object {
        private const val TAG = "SteamInstallPlugin"

        /** tModLoader 1.3 的启动入口（包装 exe），与 GOG 安装一致 */
        const val LEGACY_LAUNCHER = LEGACY_TML_LAUNCHER

        /** tModLoader 1.3 的运行时（mono），与 GOG 安装一致 */
        const val LEGACY_RUNTIME = LEGACY_TML_RUNTIME
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
