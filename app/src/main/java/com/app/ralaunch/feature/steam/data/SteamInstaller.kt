package com.app.ralaunch.feature.steam.data

import android.content.Context
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.installer.GameDefinition
import com.app.ralaunch.feature.installer.plugins.SteamInstallPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Steam 下载 + 安装编排：
 *   1. 用 CLI 把 app 的内容 depot 拉到游戏的 storage root
 *   2. 写 game_info.json 并 upsert 到游戏列表
 *   3. tModLoader 1.3 特殊：覆盖到已有 Terraria 目录，并换成 TML13Launcher 包装器
 */
object SteamInstaller {
    private const val TAG = "SteamInstaller"

    private val plugin = SteamInstallPlugin()

    suspend fun install(
        context: Context,
        repository: IGameRepositoryServiceV3,
        game: SteamGame,
        branch: String = game.branch,
        onStatus: (String) -> Unit,
        onProgress: (Float, String?) -> Unit,
    ): GameItem = withContext(Dispatchers.IO) {
        val definition = plugin.definitionFor(game)
            ?: throw IllegalStateException("未知的 Steam 游戏：${game.name}")

        if (game.requiresTerraria && branch.startsWith("1.3")) {
            return@withContext overlayTModLoader(context, repository, game.copy(branch = branch), onStatus, onProgress)
        }

        // 已有同 gameId 的安装（比如换分支）→ 复用目录并覆盖，不新建一份
        val existing = repository.games.value.firstOrNull { it.gameId == definition.gameId }
        val root = if (existing != null) {
            val dir = File(repository.getGameGlobalStorageDirFull(), existing.storageRootPathRelative)
            onStatus("覆盖已有安装：${dir.name}（分支 $branch）")
            clearInstallDir(dir)
            dir
        } else {
            siblingInstallDir(repository, definition)
        }
        AppLog.i(TAG, "Steam 安装 ${game.name}（$branch）-> ${root.absolutePath}")

        val code = SteamCli.run(
            context,
            listOf(
                "--game-root", root.absolutePath,
                "--app", game.appId.toString(),
                "--branch", branch,
                "--platform", game.platform,
                "--credentials", SteamCli.credentialsFile(context).absolutePath,
            ),
        ) { event -> handleEvent(event, onStatus, onProgress, game) }

        if (code != 0) {
            throw IllegalStateException("Steam 下载失败（退出码 $code）")
        }

        // Steam 版 Terraria 启动会直接 P/Invoke steam_api，装机时就补上 Steamworks.NET shim
        if (game.appId == SteamCatalog.TERRARIA_APP_ID) {
            SteamCli.ensureSteamworksShim(context, root)
        }

        // 包里的 FNA.dll.config 是 Linux dllmap，Android 上会让 FNA 直接初始化失败
        plugin.cleanupFnaConfig(root)

        val isTmlStandalone = game.appId == SteamCatalog.TMODLOADER_APP_ID
        if (isTmlStandalone) {
            copyVanillaAssets(repository, root, onStatus)
            onStatus("替换 Android 版 MonoMod...")
            val ok = plugin.applyMonoModPatches(root)
            AppLog.i(TAG, "MonoMod 替换${if (ok) "成功" else "失败"}")
        }
        val launchTarget = if (isTmlStandalone) "tModLoader.dll" else null
        val runtime = if (launchTarget == "tModLoader.dll") "10.0.4" else runtimeFor(game)
        val item = plugin.buildGameItem(root, root, definition, launchTarget, runtime)
        val named = item.copy(displayedName = "${game.name}（${branchLabel(game, branch)}）")
        repository.upsert(named)
        named
    }

    /**
     * tModLoader 1.4 的落地目录：与 Terraria 安装目录**同级**。
     * GOG 的 tModLoader 安装也是 `.../Terraria` + `.../tModLoader` 这种摆放，
     * 这里沿用同一套布局（名字固定为 gameId，换分支时复用同一目录）。
     * 没有 Terraria 安装时退回随机目录（缺原版素材，但仍可安装）。
     */
    private fun siblingInstallDir(
        repository: IGameRepositoryServiceV3,
        definition: GameDefinition,
    ): File {
        val gamesDir = File(repository.getGameGlobalStorageDirFull())
        val terraria = repository.games.value.firstOrNull { it.gameId == "Terraria" }
        if (terraria == null) {
            val (rootPath, _) = repository.createGameStorageRoot(definition.gameId)
            return File(rootPath)
        }
        val terrariaDir = File(gamesDir, terraria.storageRootPathRelative)
        val parent = terrariaDir.parentFile ?: gamesDir
        return File(parent, definition.gameId).also {
            it.mkdirs()
            AppLog.i(TAG, "tModLoader 安装目录（与 Terraria 同级）: ${it.absolutePath}")
        }
    }

    /** tML 1.4 独立安装需要 vanilla Terraria 的 Content 与程序集：从已有 Terraria 安装里拷 */
    private fun copyVanillaAssets(repository: IGameRepositoryServiceV3, root: File, onStatus: (String) -> Unit) {
        val vanilla = repository.games.value.firstOrNull { it.gameId == "Terraria" } ?: return
        val vanillaDir = File(repository.getGameGlobalStorageDirFull(), vanilla.storageRootPathRelative)
        if (!vanillaDir.exists()) return
        val needed = listOf(
            "Content", "Terraria.exe", "Terraria.png", "FNA.dll", "steam_appid.txt",
            "I18N.dll", "I18N.West.dll", "Mono.Posix.dll", "Mono.Security.dll",
            "System.Configuration.dll", "System.Core.dll", "System.Data.dll",
            "System.Drawing.dll", "System.Numerics.dll", "System.Runtime.Serialization.dll",
            "System.Security.dll", "System.Windows.Forms.dll", "System.Windows.Forms.dll.config",
            "System.Xml.dll", "System.Xml.Linq.dll", "System.dll", "WindowsBase.dll",
        )
        onStatus("拷贝 vanilla Terraria 素材（Content/ 等）...")
        needed.forEach { name ->
            val src = File(vanillaDir, name)
            if (!src.exists()) return@forEach
            val dst = File(root, name)
            if (src.isDirectory) {
                // 目录要「合并」而不是跳过：tML 自带的 Content 只有几百张图，需要补齐原版 1.4 万张
                src.walkTopDown().forEach { item ->
                    if (item.isDirectory) return@forEach
                    val target = File(dst, item.toRelativeString(src))
                    if (target.exists()) return@forEach
                    target.parentFile?.mkdirs()
                    item.copyTo(target, overwrite = false)
                }
            } else if (!dst.exists()) {
                src.copyTo(dst, overwrite = false)
            }
        }
    }

    /** 覆盖安装：保留 Content/（tML 1.4 需要 Terraria 素材）与账号相关目录，其余清掉 */
    private fun clearInstallDir(dir: File) {
        val keep = setOf("Content", "steam-shim", "Localizer", "Mods", "Worlds", "Players")
        dir.listFiles()?.forEach { child ->
            if (child.name in keep) return@forEach
            child.deleteRecursively()
        }
        dir.mkdirs()
    }

    private fun branchLabel(game: SteamGame, branch: String): String =
        game.branches.firstOrNull { it.id == branch }?.label ?: branch

    private suspend fun overlayTModLoader(
        context: Context,
        repository: IGameRepositoryServiceV3,
        game: SteamGame,
        onStatus: (String) -> Unit,
        onProgress: (Float, String?) -> Unit,
    ): GameItem = withContext(Dispatchers.IO) {
        val terraria = repository.games.value.firstOrNull { it.gameId == "Terraria" }
            ?: throw IllegalStateException("请先安装 Terraria，再装 tModLoader 1.3")

        val targetDir = File(repository.getGameGlobalStorageDirFull(), terraria.storageRootPathRelative)
        val tempDir = File(targetDir, "temp_tml13_steam").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        onStatus("下载 tModLoader 1.3（${branchLabel(game, game.branch)}）...")
        val code = SteamCli.run(
            context,
            listOf(
                "--game-root", tempDir.absolutePath,
                "--app", game.appId.toString(),
                "--branch", game.branch,
                "--platform", game.platform,
                "--credentials", SteamCli.credentialsFile(context).absolutePath,
                // 共享内容 depot 是原版素材：平铺时保留游戏自己的 Content，不下载它
                "--skip-depot", SteamCatalog.TMODLOADER_CONTENT_DEPOT_ID.toString(),
            ),
        ) { event -> handleEvent(event, onStatus, onProgress, game) }

        try {
            if (code != 0) throw IllegalStateException("tModLoader 下载失败（退出码 $code）")

            // 与 GOG 的 tModLoader 1.3 安装同一套布局：平铺到 Terraria.exe 同级目录
            onStatus("平铺覆盖到 Terraria 目录（与 GOG 安装同一布局）...")
            if (!plugin.overlayLegacyTModLoader(tempDir, targetDir)) {
                throw IllegalStateException("tModLoader 1.3 文件覆盖失败")
            }
            plugin.cleanupFnaConfig(targetDir)
        } finally {
            tempDir.deleteRecursively()
        }

        val updated = terraria.copy(
            gameExePathRelative = SteamInstallPlugin.LEGACY_LAUNCHER,
            modLoaderEnabled = true,
            dotNetRuntimeVersionOverride = SteamInstallPlugin.LEGACY_RUNTIME,
        )
        // upsert → saveGameInfo 会重写 game_info.json（保留图标与描述）
        repository.upsert(updated)
        updated
    }

    private fun runtimeFor(game: SteamGame): String? = when (game.appId) {
        SteamCatalog.TMODLOADER_APP_ID -> "mono:4.5"
        SteamCatalog.TERRARIA_APP_ID -> "mono:4.5"
        else -> null
    }

    private fun handleEvent(
        event: JSONObject,
        onStatus: (String) -> Unit,
        onProgress: (Float, String?) -> Unit,
        game: SteamGame,
    ) {
        when (event.optString("type")) {
            "status" -> onStatus(event.optString("message"))
            "progress" -> onProgress(
                event.optDouble("percent", 0.0).toFloat().coerceIn(0f, 100f),
                event.optString("current").takeIf { it.isNotBlank() },
            )
            "error" -> onStatus("错误：" + event.optString("message").lineSequence().firstOrNull())
            "need-guard" -> onStatus("需要 Steam Guard 验证码")
            "done" -> onStatus("${game.name} 下载完成")
        }
    }
}
