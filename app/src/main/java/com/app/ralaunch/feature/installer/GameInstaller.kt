package com.app.ralaunch.feature.installer

import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import com.app.ralaunch.strings.StringsResource.Strings
import kotlinx.coroutines.CancellationException

/**
 * 统一的游戏安装器
 * 使用插件系统处理不同游戏的安装逻辑
 * 游戏文件支持本地路径与 SAF content URI（见 [GameFile]）
 *
 * 安装目录结构：
 * - GameInstaller 按文件内部特征选择安装插件（不依赖文件名）
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
     * @param gameFile 游戏本体文件（本地路径或 SAF URI）
     * @param modLoaderFile 模组加载器文件（本地路径或 SAF URI，可选）
     * @param callback 安装事件回调
     * @return 安装结果；成功时 GameItem 已由插件保存
     */
    suspend fun install(
        gameFile: GameFile?,
        modLoaderFile: GameFile? = null,
        callback: ((Event) -> Unit)? = null
    ): Result = try {
        // 选择合适的插件：优先按模组加载器，其次按游戏本体（均基于文件内容）
        val plugin = modLoaderFile?.let { InstallPluginRegistry.selectPluginForModLoader(it) }
            ?: gameFile?.let { InstallPluginRegistry.selectPluginForGame(it) }
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
    fun detectGame(gameFile: GameFile): GameDetectResult? {
        return InstallPluginRegistry.detectGame(gameFile)?.second
    }

    /**
     * 检测模组加载器
     */
    fun detectModLoader(modLoaderFile: GameFile): ModLoaderDetectResult? {
        return InstallPluginRegistry.detectModLoader(modLoaderFile)?.second
    }

    /**
     * 取消安装
     */
    fun cancel() {
        currentPlugin?.cancel()
    }
}
