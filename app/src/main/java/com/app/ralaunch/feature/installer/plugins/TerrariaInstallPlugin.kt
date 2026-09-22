package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.extractor.GogShFileExtractor
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.feature.installer.*
import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import java.io.File

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : BaseInstallPlugin() {

    override val pluginId = "terraria"
    override val displayName: String
        get() = Strings.installer.terraria.name
    override val supportedGames = listOf(GameDefinition.TERRARIA, GameDefinition.TMODLOADER)

    override fun detectGame(gameFile: GameFile): GameDetectResult? {
        // GOG 安装器：game_data.zip 内 gameinfo 首行为游戏名
        val gogInstaller = gameFile.container as? GameFileInspector.Container.GogInstaller
        if (gogInstaller != null) {
            return if (gogInstaller.gameInfo.id.equals("Terraria", ignoreCase = true)) {
                GameDetectResult(GameDefinition.TERRARIA, gogInstaller.gameInfo.version.orEmpty())
            } else {
                null
            }
        }

        // ZIP：包含 Terraria 主程序
        val zip = gameFile.container as? GameFileInspector.Container.Zip ?: return null
        return if (zip.hasEntryNamed("Terraria.exe")) {
            GameDetectResult(GameDefinition.TERRARIA)
        } else {
            null
        }
    }

    override fun detectModLoader(modLoaderFile: GameFile): ModLoaderDetectResult? {
        // ZIP：tModLoader 发行包特征（主程序 + deps 清单）
        val zip = modLoaderFile.container as? GameFileInspector.Container.Zip ?: return null
        return if (zip.hasEntryNamed("tModLoader.dll") && zip.hasEntryNamed("tModLoader.deps.json")) {
            ModLoaderDetectResult(GameDefinition.TMODLOADER)
        } else {
            null
        }
    }

    override suspend fun performInstall(
        gameFile: GameFile?,
        modLoaderFile: GameFile?,
        callback: ((Event) -> Unit)?
    ): Result {
        callback?.invoke(
            Event.Progress(
                Strings.installer.starting,
                0
            )
        )

        val gameFile = gameFile ?: throw Exception(Strings.installer.extractGameFailed)

        // 创建存储根目录
        val gameStorageRoot = createStorageRoot(gameFile, modLoaderFile)

        // 解压游戏本体（GOG 安装器或 ZIP，按容器特征分派）
        val terrariaExeParent: File? = when {
            gameFile.container is GameFileInspector.Container.GogInstaller ->
                when (
                    val result = GogShFileExtractor.builder()
                        .from(gameFile.source)
                        .to(gameStorageRoot.toPath())
                        .callback { event ->
                            if (event is GogShFileExtractor.Event.Progress && !isCancelled) {
                                val progressInt = (event.progress * 45).toInt().coerceIn(0, 45)
                                callback?.invoke(Event.Progress(event.message, progressInt))
                            }
                        }
                        .build()
                        .extract()
                ) {
                    is GogShFileExtractor.Result.Success -> result.gamePath.toFile()
                    is GogShFileExtractor.Result.Failure -> null
                }

            gameFile.container is GameFileInspector.Container.Zip ->
                when (
                    val result = ArchiveExtractor.builder()
                        .from(gameFile.source)
                        .to(gameStorageRoot.toPath())
                        .callback { event ->
                            if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                                val progressInt = (event.progress * 45).toInt().coerceIn(0, 45)
                                callback?.invoke(Event.Progress(event.message, progressInt))
                            }
                        }
                        .build()
                        .extract()
                ) {
                    is ArchiveExtractor.Result.Success -> result.destinationPath.toFile()
                    is ArchiveExtractor.Result.Failure -> null
                }

            else -> null
        }

        if (terrariaExeParent == null) {
            throw Exception(
                Strings.installer.extractGameFailed
            )
        }

        if (isCancelled) return cancelledInstall(callback)

        // 确定最终的游戏定义
        var definition = GameDefinition.TERRARIA
        var finalExeParent = terrariaExeParent

        // 安装 tModLoader
        if (modLoaderFile != null) {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.terraria.tModLoader.prepareDir,
                    48
                )
            )

            val gogGamesDir = terrariaExeParent.parentFile
            val tModLoaderExeParent = File(gogGamesDir, "tModLoader")
            tModLoaderExeParent.mkdirs()

            callback?.invoke(
                Event.Progress(
                    Strings.installer.terraria.tModLoader.installing,
                    55
                )
            )
            installTModLoader(modLoaderFile, tModLoaderExeParent, callback)

            definition = GameDefinition.TMODLOADER
            finalExeParent = tModLoaderExeParent
        }

        if (isCancelled) return cancelledInstall(callback)

        // 安装 MonoMod 库
        callback?.invoke(
            Event.Progress(
                Strings.installer.monoMod,
                90
            )
        )
        installMonoMod(finalExeParent)

        // 提取图标
        callback?.invoke(
            Event.Progress(
                Strings.installer.extractIcon,
                92
            )
        )
        val iconPath = extractIcon(finalExeParent, definition)

        // 创建 GameItem，由基类保存并发出完成事件
        val gameItem = createGameItem(
            definition = definition,
            storageRootDir = gameStorageRoot,
            actualGameDir = finalExeParent,
            iconPath = iconPath
        )

        return finishInstall(gameItem, callback)
    }

    private suspend fun installTModLoader(modLoaderFile: GameFile, outputDir: File, callback: ((Event) -> Unit)?) {
        val tempDir = File(outputDir.parentFile, "temp_tmodloader_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            when (
                val result = ArchiveExtractor.builder()
                    .from(modLoaderFile.source)
                    .to(tempDir.toPath())
                    .callback { event ->
                        if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                            val progressInt = 55 + (event.progress * 30).toInt().coerceIn(0, 30)
                            callback?.invoke(
                                Event.Progress(
                                    Strings.installer.terraria.tModLoader.withDetail(event.message),
                                    progressInt
                                )
                            )
                        }
                    }
                    .build()
                    .extract()
            ) {
                is ArchiveExtractor.Result.Failure -> throw Exception(result.message)
                is ArchiveExtractor.Result.Success -> {
                    val sourceDir = findTModLoaderRoot(tempDir)
                    callback?.invoke(
                        Event.Progress(
                            Strings.installer.terraria.tModLoader.copyFiles,
                            88
                        )
                    )
                    copyDirectory(sourceDir, outputDir)
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun findTModLoaderRoot(extractedDir: File): File {
        if (File(extractedDir, "tModLoader.dll").exists()) return extractedDir

        val subdirs = extractedDir.listFiles { file -> file.isDirectory } ?: return extractedDir

        for (subdir in subdirs) {
            if (File(subdir, "tModLoader.dll").exists()) return subdir
        }

        return if (subdirs.size == 1) subdirs[0] else extractedDir
    }
}
