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
        betaCode: String? = null,
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
        } else if (game.appId == SteamCatalog.TMODLOADER_APP_ID) {
            // tModLoader 要摆在 Terraria 目录同级（1.4 的两种布局之一）
            siblingInstallDir(repository, definition)
        } else {
            // 其它 Steam 游戏：目录名就用 gameId（Terraria 必须叫 Terraria，见 deterministicInstallDir）
            deterministicInstallDir(repository, definition)
        }
        AppLog.i(TAG, "Steam 安装 ${game.name}（$branch）-> ${root.absolutePath}")

        val code = SteamCli.run(
            context,
            buildList {
                add("--game-root"); add(root.absolutePath)
                add("--app"); add(game.appId.toString())
                add("--branch"); add(branch)
                add("--platform"); add(game.platform)
                add("--credentials"); add(SteamCli.credentialsFile(context).absolutePath)
                if (!betaCode.isNullOrBlank()) {
                    add("--beta-code"); add(betaCode)
                }
                if (game.appId == SteamCatalog.TMODLOADER_APP_ID) {
                    // 共享内容 depot（原版素材）从已有 Terraria 安装合并，不下载
                    add("--skip-depot"); add(SteamCatalog.TMODLOADER_CONTENT_DEPOT_ID.toString())
                }
            },
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
        val named = item.copy(displayedName = "${game.name}（${branchLabel(branch)}）")
        repository.upsert(named)
        named
    }

    /**
     * Steam 下载的落地目录：**直接用 gameId 当目录名**，不留随机后缀。
     *
     * 这一条是硬要求，不是美观问题：tModLoader 1.3 的 `Main.PostSocialInitialize` 找素材用的是
     * `../Terraria/Content`（退路是 `../Content`，再不行就弹「Content folder not found」并 Exit(1)）。
     * 所以 Terraria 的安装目录必须正好叫 `Terraria`，`../Terraria/Content` 才会指回它自己 ——
     * GOG 那份能跑就是因为目录名天然是 `Terraria`。同名的目录已被占用时退回随机目录。
     */
    private fun deterministicInstallDir(
        repository: IGameRepositoryServiceV3,
        definition: GameDefinition,
    ): File {
        val gamesDir = File(repository.getGameGlobalStorageDirFull())
        val dir = File(gamesDir, definition.gameId)
        if (!dir.exists()) {
            dir.mkdirs()
            AppLog.i(TAG, "安装目录（固定名）: ${dir.absolutePath}")
            return dir
        }
        AppLog.w(TAG, "目录 ${dir.name} 已被占用，改用随机目录")
        val (rootPath, _) = repository.createGameStorageRoot(definition.gameId)
        return File(rootPath)
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

    /**
     * tML 1.4 只从已有 Terraria 安装里补**素材**（`Content/` 与图标）。
     *
     * 绝对不要拷程序集：tML 1.4 自带整套 .NET 10 依赖（`Libraries/` 里的 FNA、ReLogic…），
     * 而 Terraria 目录里那份是 **mono/net40 的 FNA.dll + System.* BCL**，拷过去会把它的
     * net10 FNA 顶掉，本地 BCL 还会抢在 .NET 10 共享框架之前被解析 —— tML 1.4 直接起不来。
     */
    private fun copyVanillaAssets(repository: IGameRepositoryServiceV3, root: File, onStatus: (String) -> Unit) {
        val vanilla = repository.games.value.firstOrNull { it.gameId == "Terraria" } ?: return
        val vanillaDir = File(repository.getGameGlobalStorageDirFull(), vanilla.storageRootPathRelative)
        if (!vanillaDir.exists()) return
        onStatus("合并 vanilla Terraria 素材（只拷 Content/，程序集用 tML 自带的）...")
        val contentSrc = File(vanillaDir, "Content")
        if (contentSrc.exists()) {
            val dst = File(root, "Content")
            contentSrc.walkTopDown().forEach { item ->
                if (item.isDirectory) return@forEach
                val target = File(dst, item.toRelativeString(contentSrc))
                if (target.exists()) return@forEach
                target.parentFile?.mkdirs()
                item.copyTo(target, overwrite = false)
            }
        }
        // 图标：游戏列表里显示用（不是游戏运行依赖）
        listOf("Terraria.png").forEach { name ->
            val src = File(vanillaDir, name)
            val dst = File(root, name)
            if (src.exists() && !dst.exists()) src.copyTo(dst, overwrite = false)
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

    private fun branchLabel(branch: String): String = SteamCatalog.branchLabel(branch)

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

        onStatus("下载 tModLoader 1.3（${branchLabel(game.branch)}）...")
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
            "status" -> {
                val text = event.optString("message")
                AppLog.i(TAG, "[cli] $text")
                onStatus(text)
            }
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
