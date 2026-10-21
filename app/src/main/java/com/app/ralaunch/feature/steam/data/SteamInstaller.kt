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
        onStatus: (String) -> Unit,
        onProgress: (Float, String?) -> Unit,
    ): GameItem = withContext(Dispatchers.IO) {
        val definition = plugin.definitionFor(game)
            ?: throw IllegalStateException("未知的 Steam 游戏：${game.name}")

        if (game.requiresTerraria) {
            return@withContext overlayTModLoader(context, repository, game, definition, onStatus, onProgress)
        }

        val (rootPath, _) = repository.createGameStorageRoot(definition.gameId)
        val root = File(rootPath)
        AppLog.i(TAG, "Steam 安装 ${game.name} -> ${root.absolutePath}")

        val code = SteamCli.run(
            context,
            listOf(
                "--game-root", root.absolutePath,
                "--app", game.appId.toString(),
                "--branch", game.branch,
                "--platform", game.platform,
                "--credentials", SteamCli.credentialsFile(context).absolutePath,
            ),
        ) { event -> handleEvent(event, onStatus, onProgress, game) }

        if (code != 0) {
            throw IllegalStateException("Steam 下载失败（退出码 $code）")
        }

        val item = plugin.buildGameItem(root, root, definition, null, runtimeFor(game))
        repository.upsert(item)
        item
    }

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
