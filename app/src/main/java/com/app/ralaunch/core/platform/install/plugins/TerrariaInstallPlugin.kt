package com.app.ralaunch.core.platform.install.plugins

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.install.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : BaseInstallPlugin() {
    
    override val pluginId = "terraria"
    override val displayName: String
        get() = RaLaunchApp.getInstance().getString(R.string.install_plugin_display_name_terraria_tmodloader)
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
    
    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRootFull: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_starting),
                        0
                    )
                }
                
                if (!gameStorageRootFull.exists()) gameStorageRootFull.mkdirs()
                
                // 解压游戏本体
                var terrariaExeParent: File? = extractGameFile(gameFile, gameStorageRootFull, callback)
                
                if (terrariaExeParent == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError(
                            RaLaunchApp.getInstance().getString(R.string.install_extract_game_failed)
                        )
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 确定最终的游戏定义
                var definition = GameDefinition.TERRARIA
                var finalExeParent = terrariaExeParent
                
                // 安装 tModLoader
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_prepare_dir),
                            48
                        )
                    }
                    
                    val gogGamesDir = terrariaExeParent.parentFile
                    val tModLoaderExeParent = File(gogGamesDir, "tModLoader")
                    tModLoaderExeParent.mkdirs()
                    
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader),
                            55
                        )
                    }
                    installTModLoader(modLoaderFile, tModLoaderExeParent, callback)
                    
                    definition = GameDefinition.TMODLOADER
                    finalExeParent = tModLoaderExeParent
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 安装 MonoMod 库
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_monomod),
                        90
                    )
                }
                installMonoMod(finalExeParent)

                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_extract_icon),
                        92
                    )
                }
                val iconPath = extractIcon(finalExeParent, definition)

                // 创建游戏信息文件 - 使用 outputDir 作为存储根目录
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_finishing),
                        98
                    )
                }
                createGameInfo(gameStorageRootFull, finalExeParent, definition, iconPath)

                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    storageRootDir = gameStorageRootFull,
                    actualGameDir = finalExeParent,
                    iconPath = iconPath
                )
                
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_complete),
                        100
                    )
                    callback.onComplete(gameItem)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(
                        e.message ?: RaLaunchApp.getInstance().getString(R.string.install_failed)
                    )
                }
            }
        }
    }
    
    private suspend fun extractGameFile(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val fileName = gameFile.name.lowercase()
        
        return if (fileName.endsWith(".sh")) {
            extractGogSh(gameFile, outputDir, callback)
        } else if (fileName.endsWith(".zip")) {
            extractZip(gameFile, outputDir, callback)
        } else null
    }
    
    private suspend fun extractGogSh(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
            if (!isCancelled) {
                val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onProgress(msg, progressInt)
                }
            }
        }
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun extractZip(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractZip(
            zipFile = gameFile,
            outputDir = outputDir,
            progressCallback = { msg, progress ->
                if (!isCancelled) {
                    val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress(msg, progressInt)
                    }
                }
            }
        )
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun installTModLoader(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val tempDir = File(outputDir.parentFile, "temp_tmodloader_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 55 + (progress * 30).toInt().coerceIn(0, 30)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(
                                    R.string.install_tmodloader_with_detail,
                                    msg
                                ),
                                progressInt
                            )
                        }
                    }
                }
            )
            
            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = findTModLoaderRoot(tempDir)
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_copy_files),
                            88
                        )
                    }
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
