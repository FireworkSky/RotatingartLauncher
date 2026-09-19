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

    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()

        // 检测 Terraria GOG .sh 文件
        if (fileName.endsWith(".sh") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }

        // 检测 Terraria ZIP
        if (fileName.endsWith(".zip") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }

        return null
    }

    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()

        // 检测 tModLoader
        if (fileName.contains("tmodloader") && fileName.endsWith(".zip")) {
            return ModLoaderDetectResult(GameDefinition.TMODLOADER)
        }

        return null
    }

    override suspend fun performInstall(
        gameFile: File,
        modLoaderFile: File?,
        callback: ((Event) -> Unit)?
    ): Result {
        callback?.invoke(
            Event.Progress(
                Strings.installer.starting,
                0
            )
        )

        // 创建存储根目录
        val gameStorageRoot = createStorageRoot(gameFile, modLoaderFile)

        // 解压游戏本体（GOG .sh 或 ZIP）
        val gameFileName = gameFile.name.lowercase()
        val terrariaExeParent: File? = when {
            gameFileName.endsWith(".sh") ->
                when (
                    val result = GogShFileExtractor.builder()
                        .from(gameFile.toPath())
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

            gameFileName.endsWith(".zip") ->
                when (
                    val result = ArchiveExtractor.builder()
                        .from(gameFile.toPath())
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

    private suspend fun installTModLoader(modLoaderFile: File, outputDir: File, callback: ((Event) -> Unit)?) {
        val tempDir = File(outputDir.parentFile, "temp_tmodloader_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            when (
                val result = ArchiveExtractor.builder()
                    .from(modLoaderFile.toPath())
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
