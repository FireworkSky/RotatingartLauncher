package com.app.ralaunch.feature.installer

import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import com.app.ralaunch.strings.StringsResource.Strings
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * 统一的游戏安装器
 * 使用插件系统处理不同游戏的安装逻辑
 *
 * 安装目录结构：
 * - GameInstaller 按文件选择安装插件
 * - 插件检测游戏类型获取 gameId（如 "SMAPI", "Celeste", "Terraria"），
 *   调用 GameManager.createDirectory 生成存储 ID（如 "SMAPI_abc12345"）并创建目录
 * - 插件将游戏文件提取到此目录或其子目录，并负责保存 GameItem
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
class GameInstaller {

    private var currentPlugin: GameInstallPlugin? = null

    /**
     * 安装游戏
     * @param gameFilePath 游戏本体文件路径（.sh 或 .zip）
     * @param modLoaderFilePath 模组加载器文件路径（.zip）
     * @param callback 安装事件回调
     * @return 安装结果；成功时 GameItem 已由插件保存
     */
    suspend fun install(
        gameFilePath: String,
        modLoaderFilePath: String? = null,
        callback: ((Event) -> Unit)? = null
    ): Result = try {
        val gameFile = File(gameFilePath)
        val modLoaderFile = modLoaderFilePath?.let { File(it) }

        // 选择合适的插件：优先按模组加载器，其次按游戏本体
        val plugin = modLoaderFile?.let { InstallPluginRegistry.selectPluginForModLoader(it) }
                ?: InstallPluginRegistry.selectPluginForGame(gameFile)
            ?: return Result.Failure(Strings.installer.pluginNotFound)

        currentPlugin = plugin

        // 存储根目录由插件经 GameManager 自行创建；执行安装
        plugin.install(gameFile, modLoaderFile, callback)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: Strings.installer.failed
        callback?.invoke(Event.Error(message, e))
        Result.Failure(message, e)
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
}
