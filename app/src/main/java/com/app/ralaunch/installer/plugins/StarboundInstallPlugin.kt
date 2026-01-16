package com.app.ralaunch.installer.plugins

import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.installer.GameDetectResult
import com.app.ralaunch.installer.GameExtractorUtils
import com.app.ralaunch.installer.GameInstallPlugin
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.installer.ModLoaderDetectResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Starbound 安装插件
 * 支持导入 starbound_1_4_4_34261.sh 文件
 */
class StarboundInstallPlugin : GameInstallPlugin {
    
    override val pluginId = "starbound"
    override val displayName = "Starbound"
    override val supportedGameTypes = listOf("starbound")
    
    private var installJob: Job? = null
    private var isCancelled = false
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Starbound GOG .sh 文件
        if (fileName.endsWith(".sh") && fileName.contains("starbound")) {
            return GameDetectResult(
                gameName = "Starbound",
                gameType = "starbound",
                launchTarget = "starbound" // Linux可执行文件，无后缀
            )
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        // Starbound 暂不支持模组加载器
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
                    callback.onProgress("开始安装 Starbound...", 0)
                }
                
                // 创建输出目录
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                var starboundGameDir: File? = null
                
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
                            starboundGameDir = result.outputDir
                        }
                    }
                }
                
                if (starboundGameDir == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("游戏解压失败")
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 查找 starbound 可执行文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("查找游戏文件...", 85)
                }
                
                val linuxDir = File(starboundGameDir, "linux")
                val starboundExe = File(linuxDir, "starbound")
                
                // 设置 linux 目录下所有文件的执行权限（内部存储才能设置）
                if (linuxDir.exists()) {
                    setExecutablePermissions(linuxDir)
                }
                
                val launchTarget = if (starboundExe.exists()) {
                    "linux/starbound"
                } else {
                    // 搜索可执行文件
                    findStarboundExecutable(starboundGameDir)?.let {
                        it.relativeTo(starboundGameDir).path
                    } ?: "starbound"
                }
                
                // 初始化Box64环境
                withContext(Dispatchers.Main) {
                    callback.onProgress("初始化 Box64 环境...", 90)
                }
                
                val context = RaLaunchApplication.getAppContext()
                GameLauncher.initializeBox64(context)
                
                // 提取图标 (优先从可执行文件读取)
                val iconPath = extractIcon(starboundGameDir, launchTarget)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                createGameInfo(starboundGameDir, "Starbound", launchTarget, iconPath)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(
                        gamePath = starboundGameDir.absolutePath,
                        gameBasePath = outputDir.absolutePath,
                        gameName = "Starbound",
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
     * 查找 Starbound 可执行文件
     */
    private fun findStarboundExecutable(gameDir: File): File? {
        // 优先查找 linux 目录
        val linuxDir = File(gameDir, "linux")
        if (linuxDir.exists()) {
            val exe = File(linuxDir, "starbound")
            if (exe.exists()) return exe
        }
        
        // 搜索整个目录
        return gameDir.walkTopDown()
            .filter { it.isFile && it.name == "starbound" && !it.name.contains(".") }
            .firstOrNull()
    }
    
    /**
     * 提取图标 - GOG 游戏图标在 support/icon.png
     */
    private fun extractIcon(gameDir: File, launchTarget: String): String? {
        try {
            // GOG 图标路径 (由 GogShFileExtractor 提取到 support/icon.png)
            val gogIcon = File(gameDir, "support/icon.png")
            if (gogIcon.exists() && gogIcon.length() > 0) {
                return gogIcon.absolutePath
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
  "game_type": "starbound",
  "launch_target": "$launchTarget",
  "runtime": "box64",
  "install_time": "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}"$iconField
}
        """.trimIndent()
        infoFile.writeText(json)
    }
}

