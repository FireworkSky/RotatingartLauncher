package com.app.ralaunch.installer

import com.app.ralib.icon.IconExtractor
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.core.AssemblyPatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : GameInstallPlugin {
    
    override val pluginId = "terraria"
    override val displayName = "Terraria / tModLoader"
    override val supportedGameTypes = listOf("terraria", "tmodloader")
    
    private var installJob: Job? = null
    private var isCancelled = false
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Terraria GOG .sh 文件
        if (fileName.endsWith(".sh") && fileName.contains("terraria")) {
            return GameDetectResult(
                gameName = "Terraria",
                gameType = "terraria",
                launchTarget = "Terraria.exe"
            )
        }
        
        // 检测 Terraria ZIP
        if (fileName.endsWith(".zip") && fileName.contains("terraria")) {
            return GameDetectResult(
                gameName = "Terraria",
                gameType = "terraria",
                launchTarget = "Terraria.exe"
            )
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()
        
        // 检测 tModLoader
        if (fileName.contains("tmodloader") && fileName.endsWith(".zip")) {
            return ModLoaderDetectResult(
                name = "tModLoader",
                type = "tmodloader",
                launchTarget = "tModLoader.dll"
            )
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
                
                // 创建输出目录
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                // 实际的游戏目录（GOG 解压会创建 GoG Games/Terraria 子目录）
                var terrariaGameDir: File? = null
                
                // 使用工具类解压游戏本体
                if (gameFile.name.lowercase().endsWith(".sh")) {
                    val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
                        if (!isCancelled) {
                            val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onProgress(msg, progressInt)
                            }
                        }
                    }
                    
                    when (result) {
                        is GameExtractorUtils.ExtractResult.Error -> {
                            withContext(Dispatchers.Main) {
                                callback.onError(result.message)
                            }
                            return@launch
                        }
                        is GameExtractorUtils.ExtractResult.Success -> {
                            // GOG 解压返回的目录是 GoG Games/Terraria
                            terrariaGameDir = result.outputDir
                        }
                    }
                } else if (gameFile.name.lowercase().endsWith(".zip")) {
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
                    
                    when (result) {
                        is GameExtractorUtils.ExtractResult.Error -> {
                            withContext(Dispatchers.Main) {
                                callback.onError(result.message)
                            }
                            return@launch
                        }
                        is GameExtractorUtils.ExtractResult.Success -> {
                            terrariaGameDir = result.outputDir
                        }
                    }
                }
                
                if (terrariaGameDir == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("游戏解压失败")
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                var finalGameName = "Terraria"
                var launchTarget = "Terraria.exe"
                var iconPath: String? = null
                var finalGameDir = terrariaGameDir
                
                // 安装 tModLoader
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("准备 tModLoader 目录...", 48)
                    }
                    
                    // tModLoader 目录放在 Terraria 的同级目录（GoG Games/tModLoader）
                    val gogGamesDir = terrariaGameDir.parentFile
                    val tModLoaderDir = File(gogGamesDir, "tModLoader")
                    tModLoaderDir.mkdirs()
                    
                    // 只解压 tModLoader 文件，不复制 Terraria 文件
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 tModLoader...", 55)
                    }
                    installTModLoader(modLoaderFile, tModLoaderDir, callback)
                    
                    finalGameName = "tModLoader"
                    launchTarget = "tModLoader.dll"
                    finalGameDir = tModLoaderDir
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 解压 MonoMod 库到游戏目录
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装 MonoMod 库...", 90)
                }
                extractMonoModLibraries(finalGameDir)
                
                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                iconPath = extractIcon(finalGameDir, launchTarget)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                createGameInfo(finalGameDir, finalGameName, launchTarget, iconPath)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(
                        gamePath = finalGameDir.absolutePath,
                        gameBasePath = outputDir.absolutePath,  // 根安装目录，用于删除
                        gameName = finalGameName,
                        launchTarget = launchTarget,
                        iconPath = iconPath
                    )
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "安装失败")
                }
            }
        }
    }
    
    override fun cancel() {
        isCancelled = true
        installJob?.cancel()
    }
    
    private suspend fun installTModLoader(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        // 先解压到临时目录
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
                is GameExtractorUtils.ExtractResult.Error -> {
                    throw Exception(result.message)
                }
                is GameExtractorUtils.ExtractResult.Success -> {
                    // 检查是否有嵌套目录（zip内有根目录的情况）
                    val sourceDir = findTModLoaderRoot(tempDir)
                    
                    withContext(Dispatchers.Main) {
                        callback.onProgress("复制 tModLoader 文件...", 88)
                    }
                    
                    // 复制文件到目标目录
                    copyDirectory(sourceDir, outputDir)
                }
            }
        } finally {
            // 清理临时目录
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * 查找 tModLoader 的实际根目录
     * 如果 zip 内有嵌套目录（如 tModLoader-v2024.xx/），则返回该子目录
     */
    private fun findTModLoaderRoot(extractedDir: File): File {
        // 检查是否直接包含 tModLoader.dll
        if (File(extractedDir, "tModLoader.dll").exists()) {
            return extractedDir
        }
        
        // 检查子目录
        val subdirs = extractedDir.listFiles { file -> file.isDirectory } ?: return extractedDir
        
        for (subdir in subdirs) {
            if (File(subdir, "tModLoader.dll").exists()) {
                return subdir
            }
        }
        
        // 如果只有一个子目录，假设它是根目录
        if (subdirs.size == 1) {
            return subdirs[0]
        }
        
        return extractedDir
    }
    
    /**
     * 复制目录内容
     */
    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }
    
    /**
     * 从 DLL/EXE 文件提取图标
     */
    private fun extractIcon(outputDir: File, launchTarget: String): String? {
        try {
            // 确定要提取图标的文件
            val targetFile = File(outputDir, launchTarget)
            var iconSourceFile = targetFile
            
            // 如果启动目标是 DLL，尝试找对应的 EXE
            if (launchTarget.lowercase().endsWith(".dll")) {
                val baseName = launchTarget.substringBeforeLast(".")
                val exeFile = File(outputDir, "$baseName.exe")
                if (exeFile.exists()) {
                    iconSourceFile = exeFile
                }
            }
            
            // 如果目标文件不存在，尝试搜索游戏目录
            if (!iconSourceFile.exists()) {
                // 搜索可能的 EXE 文件
                val exeFiles = outputDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() == "exe" }
                    .toList()
                
                if (exeFiles.isNotEmpty()) {
                    // 优先选择名称包含游戏名的
                    iconSourceFile = exeFiles.find { 
                        it.name.lowercase().contains("terraria") || 
                        it.name.lowercase().contains("tmodloader")
                    } ?: exeFiles.first()
                }
            }
            
            if (!iconSourceFile.exists()) {
                return null
            }
            
            // 提取图标
            val iconOutputPath = File(outputDir, "icon.png").absolutePath
            val success = IconExtractor.extractIconToPng(iconSourceFile.absolutePath, iconOutputPath)
            
            if (success && File(iconOutputPath).exists() && File(iconOutputPath).length() > 0) {
                return iconOutputPath
            }
            
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 解压 MonoMod 库并应用到游戏目录
     * 1. 解压 MonoMod 到 monomod 目录
     * 2. 从该目录读取 DLL 并替换游戏目录中的对应文件
     */
    private fun extractMonoModLibraries(gameDir: File) {
        try {
            val context = RaLaunchApplication.getAppContext()
            
            // 1. 解压 MonoMod 到目录
            val extractSuccess = AssemblyPatcher.extractMonoMod(context)
            if (!extractSuccess) {
                android.util.Log.w("TerrariaInstallPlugin", "MonoMod 解压失败")
                return
            }
            
            // 2. 从 MonoMod 目录应用补丁到游戏目录
            val patchedCount = AssemblyPatcher.applyMonoModPatches(
                context, gameDir.absolutePath, true)
            
            if (patchedCount >= 0) {
                android.util.Log.i("TerrariaInstallPlugin", 
                    "MonoMod 已应用，替换了 $patchedCount 个文件")
            } else {
                android.util.Log.w("TerrariaInstallPlugin", "MonoMod 应用失败")
            }
        } catch (e: Exception) {
            android.util.Log.e("TerrariaInstallPlugin", "MonoMod 安装异常", e)
        }
    }
    
    private fun createGameInfo(outputDir: File, gameName: String, launchTarget: String, iconPath: String?) {
        val infoFile = File(outputDir, "game_info.json")
        val iconField = if (iconPath != null) {
            """,
  "icon_path": "${iconPath.replace("\\", "\\\\")}" """
        } else ""
        
        val json = """
{
  "game_name": "$gameName",
  "game_type": "${if (gameName == "tModLoader") "tmodloader" else "terraria"}",
  "launch_target": "$launchTarget",
  "install_time": "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}"$iconField
}
        """.trimIndent()
        infoFile.writeText(json)
    }
}

