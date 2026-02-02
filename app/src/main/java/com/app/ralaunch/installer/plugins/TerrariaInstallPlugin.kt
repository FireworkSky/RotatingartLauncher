package com.app.ralaunch.installer.plugins

import android.content.Context
import android.util.Log
import com.app.ralaunch.installer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : BaseInstallPlugin() {
    
    override val pluginId = "terraria"
    override val displayName = "Terraria / tModLoader"
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
        outputDir: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress("开始安装...", 0)
                }
                
                if (!outputDir.exists()) outputDir.mkdirs()
                
                // 解压游戏本体
                var terrariaGameDir: File? = extractGameFile(gameFile, outputDir, callback)
                
                if (terrariaGameDir == null) {
                    withContext(Dispatchers.Main) { callback.onError("游戏解压失败") }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 确定最终的游戏定义
                var definition = GameDefinition.TERRARIA
                var finalGameDir = terrariaGameDir
                
                // 安装 tModLoader
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("准备 tModLoader 目录...", 48)
                    }
                    
                    val gogGamesDir = terrariaGameDir.parentFile
                    val tModLoaderDir = File(gogGamesDir, "tModLoader")
                    tModLoaderDir.mkdirs()
                    
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 tModLoader...", 55)
                    }
                    installTModLoader(modLoaderFile, tModLoaderDir, callback)
                    
                    definition = GameDefinition.TMODLOADER
                    finalGameDir = tModLoaderDir
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 安装 MonoMod 库
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装 MonoMod 库...", 90)
                }
                installMonoMod(finalGameDir)
                
                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                val iconPath = extractIcon(finalGameDir, definition)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                createGameInfo(finalGameDir, definition, iconPath)
                
                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    gameDir = finalGameDir.absolutePath,
                    gameBasePath = outputDir.absolutePath,
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
                            callback.onProgress("安装 tModLoader: $msg", progressInt)
                        }
                    }
                }
            )
            
            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = findTModLoaderRoot(tempDir)
                    withContext(Dispatchers.Main) {
                        callback.onProgress("复制 tModLoader 文件...", 88)
                    }
                    copyDirectory(sourceDir, outputDir)
                    
                    // 替换 FNA.dll
                    withContext(Dispatchers.Main) {
                        callback.onProgress("替换 FNA.dll...", 89)
                    }
                    installFNA(outputDir)
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * 从 assets 安装自定义 FNA.dll 到游戏目录
     * tModLoader 的 FNA.dll 位于 Libraries/FNA/1.0.0/FNA.dll
     * @param gameDir 游戏目录
     * @return 是否成功
     */
    private fun installFNA(gameDir: File): Boolean {
        return try {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            
            // tModLoader 的 FNA.dll 位于 Libraries/FNA/1.0.0/ 目录
            val fnaDir = File(gameDir, "Libraries/FNA/1.0.0")
            if (!fnaDir.exists()) {
                fnaDir.mkdirs()
            }
            val targetFile = File(fnaDir, "FNA.dll")
            
            context.assets.open("patches/FNA.dll").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "FNA.dll 已替换: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "FNA.dll 替换失败: ${e.message}")
            false
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
    
    companion object {
        private const val TAG = "TerrariaInstallPlugin"
    }
}
