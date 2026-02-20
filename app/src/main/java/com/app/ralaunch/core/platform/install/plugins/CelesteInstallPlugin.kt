package com.app.ralaunch.core.platform.install.plugins

import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.core.platform.install.*
import com.app.ralaunch.feature.patch.data.PatchManager
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Celeste/Everest 安装插件
 */
class CelesteInstallPlugin : BaseInstallPlugin() {

    override val pluginId = "celeste"
    override val displayName = "Celeste / Everest"
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

    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRoot: File,
        callback: InstallCallback
    ) {
        isCancelled = false

        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress("开始安装...", 0)
                }

                if (!gameStorageRoot.exists()) gameStorageRoot.mkdirs()

                // 解压游戏本体
                val extractResult = GameExtractorUtils.extractZip(
                    zipFile = gameFile,
                    outputDir = gameStorageRoot,
                    progressCallback = { msg, progress ->
                        if (!isCancelled) {
                            val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onProgress(msg, progressInt)
                            }
                        }
                    }
                )
                
                when (extractResult) {
                    is GameExtractorUtils.ExtractResult.Error -> {
                        withContext(Dispatchers.Main) { callback.onError(extractResult.message) }
                        return@launch
                    }
                    is GameExtractorUtils.ExtractResult.Success -> { /* 继续 */ }
                }

                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }

                var definition = GameDefinition.CELESTE

                // 安装 Everest
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 Everest...", 55)
                    }
                    installEverest(modLoaderFile, gameStorageRoot, callback)
                    definition = GameDefinition.EVEREST
                }

                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                val iconPath = extractIcon(gameStorageRoot, definition)

                // 创建游戏信息文件 - outputDir 既是存储根目录也是实际游戏目录
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                createGameInfo(gameStorageRoot, definition, iconPath)

                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    gameDir = gameStorageRoot,
                    iconPath = iconPath
                )

                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(gameItem)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "安装失败")
                }
            }
        }
    }

    private suspend fun installEverest(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val extractResult = GameExtractorUtils.extractZip(
            zipFile = modLoaderFile,
            outputDir = outputDir,
            sourcePrefix = "main",
            progressCallback = { msg, progress ->
                if (!isCancelled) {
                    val progressInt = 55 + (progress * 25).toInt().coerceIn(0, 25)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress("安装 Everest: $msg", progressInt)
                    }
                }
            }
        )

        when (extractResult) {
            is GameExtractorUtils.ExtractResult.Error -> throw Exception(extractResult.message)
            is GameExtractorUtils.ExtractResult.Success -> { /* 继续 */ }
        }

        // 安装 MonoMod 库
        withContext(Dispatchers.Main) {
            callback.onProgress("安装 MonoMod 库...", 85)
        }
        installMonoMod(outputDir)

        // 执行 Everest MiniInstaller
        withContext(Dispatchers.Main) {
            callback.onProgress("执行 Everest MiniInstaller...", 90)
        }

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (e: Exception) { null }
        val patches = patchManager?.getPatchesByIds(
            listOf("com.app.ralaunch.everest.miniinstaller.fix")
        ) ?: emptyList()

        if (patches.size != 1) {
            throw Exception("未找到 Everest MiniInstaller 修补程序，或者存在多个同 ID 修补程序")
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
            throw Exception("Everest MiniInstaller 执行失败，错误码：$patchResult")
        }
    }
}
