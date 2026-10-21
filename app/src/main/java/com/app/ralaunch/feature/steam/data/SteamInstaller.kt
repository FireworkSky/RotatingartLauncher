package com.app.ralaunch.feature.steam.data

import android.content.Context
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.GameItem
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

    private val skipOverlayNames = setOf(
        "mscorlib.dll", "lib", "lib64", "steam_api.dll", "steam_api64.dll",
        "steam_appid.txt", "installscript.vdf", "Terraria.bin.x86_64", "TerrariaServer.bin.x86_64",
    )

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
            return@withContext overlayTModLoader(context, repository, game.copy(branch = branch), definition, onStatus, onProgress)
        }

        // 已有同 gameId 的安装（比如换分支）→ 复用目录并覆盖，不新建一份
        val existing = repository.games.value.firstOrNull { it.gameId == definition.gameId }
        val root = if (existing != null) {
            val dir = File(repository.getGameGlobalStorageDirFull(), existing.storageRootPathRelative)
            onStatus("覆盖已有安装：${dir.name}（分支 $branch）")
            clearInstallDir(dir)
            dir
        } else {
            val (rootPath, _) = repository.createGameStorageRoot(definition.gameId)
            File(rootPath)
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
        definition: com.app.ralaunch.feature.installer.GameDefinition,
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

        onStatus("下载 tModLoader 1.3（${game.branch}）...")
        val code = SteamCli.run(
            context,
            listOf(
                "--game-root", tempDir.absolutePath,
                "--app", game.appId.toString(),
                "--branch", game.branch,
                "--platform", game.platform,
                "--credentials", SteamCli.credentialsFile(context).absolutePath,
            ),
        ) { event -> handleEvent(event, onStatus, onProgress, game) }

        try {
            if (code != 0) throw IllegalStateException("tModLoader 下载失败（退出码 $code）")

            onStatus("覆盖到 Terraria 目录...")
            copyOverlay(tempDir, targetDir)
            installBootstrap(context, targetDir)
        } finally {
            tempDir.deleteRecursively()
        }

        val updated = terraria.copy(
            gameExePathRelative = game.launchTarget,
            modLoaderEnabled = true,
            dotNetRuntimeVersionOverride = "mono:4.5",
        )
        // 重写 game_info.json（保持图标与描述）
        plugin.buildGameItem(
            targetDir, targetDir, definition, game.launchTarget, "mono:4.5",
        )
        repository.upsert(updated)
        updated
    }

    private fun copyOverlay(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            if (file.isDirectory) return@forEach
            val relative = file.toRelativeString(source).replace('\\', '/')
            val name = relative.substringAfterLast('/')
            if (name.lowercase() in skipOverlayNames) return@forEach
            if (name.lowercase().startsWith("system.") || name.lowercase() == "system.dll") return@forEach
            val destination = File(target, relative)
            destination.parentFile?.mkdirs()
            file.copyTo(destination, overwrite = true)
        }
    }

    private fun installBootstrap(context: Context, targetDir: File) {
        for (asset in listOf("TML13Launcher.exe", "FNA.dll")) {
            val destination = File(targetDir, asset)
            context.assets.open("prebuilt/tml13/$asset").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
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
