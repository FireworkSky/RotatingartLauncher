package com.app.ralaunch.installer

import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.core.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Don't Starve (饥荒) 安装插件
 * 支持导入 GOG Linux 版 .sh 安装文件
 */
class DontStarveInstallPlugin : GameInstallPlugin {
    
    override val pluginId = "dontstarve"
    override val displayName = "Don't Starve"
    override val supportedGameTypes = listOf("dontstarve", "dont_starve")
    
    private var installJob: Job? = null
    private var isCancelled = false
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Don't Starve GOG .sh 文件
        // 文件名格式: don_t_starve_554439_66995.sh 或类似
        if (fileName.endsWith(".sh") && 
            (fileName.contains("don_t_starve") || fileName.contains("dontstarve") || fileName.contains("dont_starve"))) {
            return GameDetectResult(
                gameName = "Don't Starve",
                gameType = "dontstarve",
                launchTarget = "dontstarve" // Linux可执行文件
            )
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        // Don't Starve 暂不支持模组加载器（游戏内置模组系统）
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
                    callback.onProgress("开始安装 Don't Starve...", 0)
                }
                
                // 创建输出目录
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                var dontStarveGameDir: File? = null
                
                // 解压 GOG .sh 文件
                if (gameFile.name.lowercase().endsWith(".sh")) {
                    val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
                        if (!isCancelled) {
                            val progressInt = (progress * 80).toInt().coerceIn(0, 80)
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
                            dontStarveGameDir = result.outputDir
                        }
                    }
                }
                
                if (dontStarveGameDir == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("游戏解压失败")
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 查找可执行文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("查找游戏文件...", 85)
                }
                
                val launchTarget = findDontStarveExecutable(dontStarveGameDir)
                
                if (launchTarget == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("未找到 Don't Starve 可执行文件")
                    }
                    return@launch
                }
                
                // 设置可执行权限
                val exeFile = File(dontStarveGameDir, launchTarget)
                if (exeFile.exists()) {
                    setExecutablePermissions(exeFile.parentFile)
                }
                
                // 初始化Box64环境
                withContext(Dispatchers.Main) {
                    callback.onProgress("初始化 Box64 环境...", 90)
                }
                
                val context = RaLaunchApplication.getAppContext()
                GameLauncher.initializeBox64(context)
                
                // 提取图标
                val iconPath = extractIcon(dontStarveGameDir)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                createGameInfo(dontStarveGameDir, "Don't Starve", launchTarget, iconPath)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(
                        gamePath = dontStarveGameDir.absolutePath,
                        gameBasePath = outputDir.absolutePath,
                        gameName = "Don't Starve",
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
    
    /**
     * 查找 Don't Starve 可执行文件
     * GOG Linux 版通常结构:
     * - game/bin/dontstarve (32位)
     * - game/bin64/dontstarve (64位) - 优先使用
     */
    private fun findDontStarveExecutable(gameDir: File): String? {
        // 可能的可执行文件名
        val possibleExeNames = listOf(
            "dontstarve",
            "dontstarve.bin.x86_64",
            "dontstarve.bin.x86",
            "dontstarve_steam",
            "dontstarve.x86_64",
            "dontstarve.x86"
        )
        
        // 可能的目录 (优先64位)
        val possibleDirs = listOf(
            "bin64",
            "bin",
            "game/bin64",
            "game/bin",
            "data/bin64",
            "data/bin",
            "" // 根目录
        )
        
        // 优先查找64位版本
        for (dir in possibleDirs) {
            for (exeName in possibleExeNames) {
                val targetDir = if (dir.isEmpty()) gameDir else File(gameDir, dir)
                val exeFile = File(targetDir, exeName)
                if (exeFile.exists() && exeFile.isFile) {
                    return exeFile.relativeTo(gameDir).path.replace("\\", "/")
                }
            }
        }
        
        // 递归搜索
        return gameDir.walkTopDown()
            .filter { file ->
                file.isFile && possibleExeNames.any { 
                    file.name.equals(it, ignoreCase = true) 
                }
            }
            .firstOrNull()
            ?.relativeTo(gameDir)
            ?.path
            ?.replace("\\", "/")
    }
    
    /**
     * 提取图标 - GOG 游戏图标通常在 support/icon.png
     */
    private fun extractIcon(gameDir: File): String? {
        try {
            // GOG 图标路径
            val gogIcon = File(gameDir, "support/icon.png")
            if (gogIcon.exists() && gogIcon.length() > 0) {
                return gogIcon.absolutePath
            }
            
            // 其他可能的图标位置
            val iconPaths = listOf(
                "data/icon.png",
                "icon.png",
                "game/icon.png"
            )
            
            for (path in iconPaths) {
                val icon = File(gameDir, path)
                if (icon.exists() && icon.length() > 0) {
                    return icon.absolutePath
                }
            }
            
            // 搜索 icon.png
            val foundIcon = gameDir.walkTopDown()
                .filter { it.isFile && it.name.equals("icon.png", ignoreCase = true) }
                .firstOrNull()
            
            return foundIcon?.takeIf { it.length() > 0 }?.absolutePath
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 设置目录下所有文件的执行权限
     */
    private fun setExecutablePermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                // 为所有文件设置可执行权限（.so 库和可执行文件都需要）
                file.setExecutable(true, false)
                file.setReadable(true, false)
            }
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
  "game_type": "dontstarve",
  "launch_target": "$launchTarget",
  "runtime": "box64",
  "install_time": "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}"$iconField
}
        """.trimIndent()
        infoFile.writeText(json)
    }
}
