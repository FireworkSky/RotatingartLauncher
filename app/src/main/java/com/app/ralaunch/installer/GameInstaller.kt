package com.app.ralaunch.installer

import android.content.Context
import java.io.File

/**
 * 统一的游戏安装器
 * 使用插件系统处理不同游戏的安装逻辑
 */
class GameInstaller(private val context: Context) {
    
    companion object {
        private const val GAMES_DIR = "games"
    }
    
    private var currentPlugin: GameInstallPlugin? = null
    
    /**
     * 安装游戏
     * @param gameFilePath 游戏本体文件路径（.sh 或 .zip）
     * @param modLoaderFilePath 模组加载器文件路径（.zip）
     * @param gameName 游戏名称（可选，自动检测）
     * @param callback 安装回调
     */
    fun install(
        gameFilePath: String,
        modLoaderFilePath: String? = null,
        gameName: String? = null,
        callback: InstallCallback
    ) {
        val gameFile = File(gameFilePath)
        val modLoaderFile = modLoaderFilePath?.let { File(it) }
        
        // 选择合适的插件
        val plugin = if (modLoaderFile != null) {
            // 优先根据模组加载器选择插件
            InstallPluginRegistry.selectPluginForModLoader(modLoaderFile)
                ?: InstallPluginRegistry.selectPluginForGame(gameFile)
        } else {
            InstallPluginRegistry.selectPluginForGame(gameFile)
        }
        
        if (plugin == null) {
            callback.onError("未找到支持此游戏的安装插件")
            return
        }
        
        currentPlugin = plugin
        
        // 创建游戏目录
        val gamesDir = File(context.getExternalFilesDir(null), GAMES_DIR)
        if (!gamesDir.exists()) {
            gamesDir.mkdirs()
        }
        
        // 确定游戏名称
        val finalGameName = gameName 
            ?: modLoaderFile?.let { plugin.detectModLoader(it)?.name }
            ?: plugin.detectGame(gameFile)?.gameName
            ?: extractGameNameFromPath(gameFilePath)
        
        val gameDir = createGameDirectory(gamesDir, finalGameName)
        
        // 执行安装
        plugin.install(gameFile, modLoaderFile, gameDir, callback)
    }
    
    /**
     * 检测游戏
     */
    fun detectGame(gameFilePath: String): GameDetectResult? {
        val gameFile = File(gameFilePath)
        return InstallPluginRegistry.detectGame(gameFile)?.second
    }
    
    /**
     * 检测模组加载器
     */
    fun detectModLoader(modLoaderFilePath: String): ModLoaderDetectResult? {
        val modLoaderFile = File(modLoaderFilePath)
        return InstallPluginRegistry.detectModLoader(modLoaderFile)?.second
    }
    
    /**
     * 取消安装
     */
    fun cancel() {
        currentPlugin?.cancel()
    }
    
    /**
     * 从文件路径提取游戏名称
     */
    private fun extractGameNameFromPath(path: String): String {
        val file = File(path)
        var name = file.nameWithoutExtension
        
        // 清理常见后缀
        val suffixes = listOf("_linux", "_win", "_osx", "_android", "_setup", "_installer",
                              "-linux", "-win", "-osx", "-android", "-setup", "-installer")
        for (suffix in suffixes) {
            if (name.lowercase().endsWith(suffix)) {
                name = name.dropLast(suffix.length)
            }
        }
        
        // 转换下划线和连字符为空格
        name = name.replace('_', ' ').replace('-', ' ')
        
        // 首字母大写
        return name.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
    
    /**
     * 创建游戏目录
     */
    private fun createGameDirectory(gamesDir: File, gameName: String): File {
        val baseName = gameName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        
        // 生成随机哈希后缀
        val hash = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val gameDir = File(gamesDir, "${baseName}_$hash")
        
        gameDir.mkdirs()
        return gameDir
    }
}
