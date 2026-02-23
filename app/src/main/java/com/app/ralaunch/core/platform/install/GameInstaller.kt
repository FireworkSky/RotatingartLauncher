package com.app.ralaunch.core.platform.install

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.shared.core.data.repository.GameListStorage
import java.io.File

/**
 * 统一的游戏安装器
 * 使用插件系统处理不同游戏的安装逻辑
 *
 * 安装目录结构：
 * - GameInstaller 检测游戏类型，获取 gameId（如 "SMAPI", "Celeste", "Terraria"）
 * - GameListStorage 使用 gameId 生成存储 ID（如 "SMAPI_abc12345"）并创建目录
 * - 插件将游戏文件提取到此目录或其子目录
 * - game_info.json 必须位于存储根目录，其中 id 字段必须与目录名匹配
 * - game_info.json 中的路径（gameExePathRelative, iconPathRelative）相对于存储根目录
 *
 * 示例：
 * games/SMAPI_abc12345/                        <- 存储根目录 (storageId = "SMAPI_abc12345")
 *   ├── game_info.json                         <- id: "SMAPI_abc12345"
 *   └── data/noarch/game/                      <- 实际游戏目录 (actual game dir)
 *       ├── Stardew Valley.exe
 *       └── Content/
 *
 * game_info.json 中：
 *   "id": "SMAPI_abc12345"                     (matches directory name)
 *   "gameId": "SMAPI"                          (game type identifier)
 *   "gameExePathRelative": "data/noarch/game/Stardew Valley.exe"
 */
class GameInstaller(private val storage: GameListStorage) {
    
    private var currentPlugin: GameInstallPlugin? = null
    
    /**
     * 安装游戏
     * @param gameFilePath 游戏本体文件路径（.sh 或 .zip）
     * @param modLoaderFilePath 模组加载器文件路径（.zip）
     * @param callback 安装回调
     */
    fun install(
        gameFilePath: String,
        modLoaderFilePath: String? = null,
        callback: InstallCallback
    ) {
        val gameFile = File(gameFilePath)
        val modLoaderFile = modLoaderFilePath?.let { File(it) }
        
        // 选择合适的插件
        val plugin = if (modLoaderFile != null) {
            InstallPluginRegistry.selectPluginForModLoader(modLoaderFile)
                ?: InstallPluginRegistry.selectPluginForGame(gameFile)
        } else {
            InstallPluginRegistry.selectPluginForGame(gameFile)
        }
        
        if (plugin == null) {
            callback.onError(
                RaLaunchApp.getInstance().getString(R.string.install_plugin_not_found)
            )
            return
        }
        
        currentPlugin = plugin
        
        // 检测游戏类型
        val detectResult = plugin.detectGame(gameFile)
        val modLoaderResult = modLoaderFile?.let { plugin.detectModLoader(it) }

        // 确定游戏 ID（从 GameDefinition）
        val gameId = modLoaderResult?.definition?.gameId
            ?: detectResult?.definition?.gameId
            ?: "unknown"

        // 通过 storage 创建游戏目录并获取存储 ID
        val (gameStorageRoot, storageId) = storage.createGameStorageRoot(gameId)
        val gameStorageRootFile = File(gameStorageRoot)
        
        // 执行安装
        plugin.install(gameFile, modLoaderFile, gameStorageRootFile, callback)
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
        
        val suffixes = listOf("_linux", "_win", "_osx", "_android", "_setup", "_installer",
                              "-linux", "-win", "-osx", "-android", "-setup", "-installer")
        for (suffix in suffixes) {
            if (name.lowercase().endsWith(suffix)) {
                name = name.dropLast(suffix.length)
            }
        }
        
        name = name.replace('_', ' ').replace('-', ' ')
        
        return name.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
