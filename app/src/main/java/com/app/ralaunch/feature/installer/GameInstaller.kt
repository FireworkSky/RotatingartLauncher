package com.app.ralaunch.feature.installer

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.GameItem
import java.io.File

/**
 * 统一的游戏安装器
 * 使用插件系统处理不同游戏的安装逻辑
 *
 * 安装目录结构：
 * - GameInstaller 检测游戏类型，获取 gameId（如 "SMAPI", "Celeste", "Terraria"）
 * - IGameRepositoryServiceV3 使用 gameId 生成存储 ID（如 "SMAPI_abc12345"）并创建目录
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
class GameInstaller(private val gameRepository: IGameRepositoryServiceV3) {
    
    private var currentPlugin: GameInstallPlugin? = null
    
    /**
     * 安装回调包装：导入完成后做统一后处理，再交给调用方
     *
     * 后处理内容：删除游戏自带的 Mono dllmap 配置 [LEGACY_DLL_MAP_CONFIG]。
     * 这类配置（Terraria/FNA 系列自带）把库名映射成「带版本号」的形式，例如
     *   <dllmap dll="FNA3D" os="linux" target="libFNA3D.so.0"/>
     * 而 APK 内所有原生库都是「去版本化」命名（libFNA3D.so / libSDL3.so / libSDL2.so），
     * 于是 Mono 按配置里的名字 dlopen 必然失败：
     *   dlopen failed: library "libFNA3D.so.0" not found
     * 删掉后 Mono 走默认探测（"FNA3D" → "libFNA3D.so"），
     * 由 GameLauncher 预载的 SDL2/SDL3 版变体通过 SONAME 命中。
     * .NET(CoreCLR) 不读取 dllmap，因此该步骤只影响 Mono 路径，删掉对 CoreCLR 无害。
     */
    private class CleanupCallback(
        private val gameStorageRoot: File,
        private val delegate: InstallCallback
    ) : InstallCallback {

        override fun onProgress(message: String, progress: Int) {
            delegate.onProgress(message, progress)
        }

        override fun onComplete(gameItem: GameItem) {
            removeLegacyDllMapConfigs(gameStorageRoot)
            delegate.onComplete(gameItem)
        }

        override fun onError(error: String) {
            delegate.onError(error)
        }

        override fun onCancelled() {
            delegate.onCancelled()
        }
    }
    
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
        val (gameStorageRoot, _) = gameRepository.createGameStorageRoot(gameId)
        val gameStorageRootFile = File(gameStorageRoot)
        
        // 执行安装
        // RAL: 包一层回调，导入完成后统一做后处理（见 CleanupCallback 注释）
        plugin.install(
            gameFile,
            modLoaderFile,
            gameStorageRootFile,
            CleanupCallback(gameStorageRootFile, callback)
        )
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

    companion object {
        private const val TAG = "GameInstaller"

        /** 游戏自带的 Mono dllmap 配置文件名（见 CleanupCallback 注释） */
        private const val LEGACY_DLL_MAP_CONFIG = "FNA.dll.config"

        /**
         * 递归移除游戏目录里自带的 Mono dllmap 配置
         *
         * @return 实际删除的文件数
         */
        private fun removeLegacyDllMapConfigs(gameStorageRoot: File): Int {
            if (!gameStorageRoot.isDirectory) return 0
            var removed = 0
            gameStorageRoot.walkTopDown()
                .filter { it.isFile && it.name.equals(LEGACY_DLL_MAP_CONFIG, ignoreCase = true) }
                .forEach { file ->
                    if (file.delete()) {
                        removed++
                        AppLog.i(TAG, "已移除自带的 dllmap 配置 / Removed legacy dllmap config: ${file.absolutePath}")
                    } else {
                        AppLog.w(TAG, "移除 dllmap 配置失败 / Failed to remove: ${file.absolutePath}")
                    }
                }
            return removed
        }
    }
}
