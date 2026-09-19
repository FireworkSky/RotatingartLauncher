package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.feature.installer.*
import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import com.app.ralaunch.feature.patch.data.PatchManager
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.io.path.Path
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Celeste/Everest 安装插件
 */
class CelesteInstallPlugin : BaseInstallPlugin() {

    override val pluginId = "celeste"
    override val displayName: String
        get() = Strings.installer.celeste.name
    override val supportedGames = listOf(GameDefinition.CELESTE, GameDefinition.EVEREST)

    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()

        if (fileName.endsWith(".zip") && fileName.contains("celeste")) {
            return GameDetectResult(GameDefinition.CELESTE)
        }

        return null
    }

    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        ZipFile(modLoaderFile).use { zip ->
            val everestLibEntry = zip.getEntry("main/everest-lib")
            if (everestLibEntry != null) {
                return ModLoaderDetectResult(GameDefinition.EVEREST)
            }
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

        // 解压游戏本体
        when (
            val extractResult = ArchiveExtractor.builder()
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
            is ArchiveExtractor.Result.Failure -> throw Exception(extractResult.message)
            is ArchiveExtractor.Result.Success -> { /* 继续 */ }
        }

        if (isCancelled) return cancelledInstall(callback)

        var definition = GameDefinition.CELESTE

        // 安装 Everest
        if (modLoaderFile != null) {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.celeste.everest.installing,
                    55
                )
            )
            installEverest(modLoaderFile, gameStorageRoot, callback)
            definition = GameDefinition.EVEREST
        }

        // 提取图标
        callback?.invoke(
            Event.Progress(
                Strings.installer.extractIcon,
                92
            )
        )
        val iconPath = extractIcon(gameStorageRoot, definition)

        // 创建 GameItem，由基类保存并发出完成事件
        val gameItem = createGameItem(
            definition = definition,
            gameDir = gameStorageRoot,
            iconPath = iconPath
        )

        return finishInstall(gameItem, callback)
    }

    private suspend fun installEverest(modLoaderFile: File, outputDir: File, callback: ((Event) -> Unit)?) {
        when (
            val extractResult = ArchiveExtractor.builder()
                .from(modLoaderFile.toPath())
                .prefix(Path("main"))
                .to(outputDir.toPath())
                .callback { event ->
                    if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                        val progressInt = 55 + (event.progress * 25).toInt().coerceIn(0, 25)
                        callback?.invoke(
                            Event.Progress(
                                Strings.installer.celeste.everest.withDetail(event.message),
                                progressInt
                            )
                        )
                    }
                }
                .build()
                .extract()
        ) {
            is ArchiveExtractor.Result.Failure -> throw Exception(extractResult.message)
            is ArchiveExtractor.Result.Success -> { /* 继续 */ }
        }

        // 安装 MonoMod 库
        callback?.invoke(
            Event.Progress(
                Strings.installer.monoMod,
                85
            )
        )
        installMonoMod(outputDir)

        // 执行 Everest MiniInstaller
        callback?.invoke(
            Event.Progress(
                Strings.installer.celeste.everest.miniInstaller,
                90
            )
        )

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (e: Exception) { null }
        val patches = patchManager?.getPatchesByIds(
            listOf("com.app.ralaunch.everest.miniinstaller.fix")
        ) ?: emptyList()

        if (patches.size != 1) {
            throw Exception(
                Strings.installer.celeste.everest.miniInstallerPatchMissing
            )
        }

        val patchResult = GameLauncher.launchDotNetAssembly(
            outputDir.resolve("MiniInstaller.dll").toString(),
            arrayOf(),
            patches
        )

        outputDir.resolve("everest-launch.txt")
            .writeText("# Splash screen disabled by Rotating Art Launcher\n--disable-splash\n")
        outputDir.resolve("EverestXDGFlag")
            .writeText("") // 创建一个空文件作为标记，告诉 Everest 使用 XDG 数据目录（Linux/MacOS）

        if (patchResult != 0) {
            throw Exception(
                Strings.installer.celeste.everest.miniInstallerFailed(patchResult)
            )
        }
    }
}
