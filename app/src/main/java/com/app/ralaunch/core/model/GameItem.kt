package com.app.ralaunch.core.model

import com.app.ralaunch.jsonconfig.FlowJsonConfig
import com.app.ralaunch.utils.GameManager
import kotlinx.serialization.Serializable
import kotlin.io.path.Path

/**
 * 游戏项数据模型 - 统一跨平台版本
 *
 * 序列化到 game_info.json，使用相对路径存储。
 * 绝对路径 getter 经 GameManager.directory(id) 解析，目录缺失或
 * 未初始化时返回 null，不持有根目录字段。
 *
 * @property id 唯一安装标识符，用作存储目录名（游戏名+随机哈希）
 * @property displayedName 显示名称
 * @property displayedDescription 描述信息
 * @property gameId 游戏类型标识符（如 "celeste", "terraria"）
 * @property gameExePathRelative 游戏主程序相对路径（相对于游戏存储根目录）
 * @property iconPathRelative 图标相对路径（相对于游戏存储根目录），可为空
 * @property modLoaderEnabled 是否启用 ModLoader
 * @property rendererOverride 渲染器覆盖（null 表示跟随全局设置）
 * @property dotNetRuntimeVersionOverride .NET 运行时版本覆盖（null 表示跟随全局设置）
 * @property gameEnvVars 游戏环境变量（null 值表示在启动前 unset 对应变量）
 */
// 构造参数默认值由 @FlowJsonConfig 生成代码要求（initial = GameItem()）
@FlowJsonConfig
@Serializable
data class GameItem(
    val id: String = "",
    var displayedName: String = "",
    var displayedDescription: String = "",
    var gameId: String = "",
    var gameExePathRelative: String = "",
    var iconPathRelative: String? = null,
    var modLoaderEnabled: Boolean = true,
    var rendererOverride: String? = null,
    var dotNetRuntimeVersionOverride: String? = null,
    var gameEnvVars: Map<String, String?> = emptyMap()
) {
    /**
     * 游戏存储目录名称（相对于全局 games 目录）
     *
     * 等同于 [id]，返回子目录名称。
     * 例: 全局目录为 `/data/games/`，此属性返回 `"celeste_a1b2c3d4"`，
     * 则存储根目录为 `/data/games/celeste_a1b2c3d4/`
     */
    val storageRootPathRelative: String
        get() = id

    /**
     * 游戏存储根目录的绝对路径
     *
     * 经 [GameManager.directory] 解析；目录缺失、越界或未初始化时返回 null。
     *
     * 例: `/data/games/celeste_a1b2c3d4/`
     */
    val storageRootPathFull: String?
        get() = runCatching { GameManager.directory(id).toString() }.getOrNull()

    /**
     * 游戏主程序的绝对路径
     *
     * 通过 [storageRootPathFull] 与 [gameExePathRelative] 拼接得到。
     * 若 [storageRootPathFull] 无法确定则返回 null。
     *
     * 例: `/data/games/celeste_a1b2c3d4/Celeste.exe`
     */
    val gameExePathFull: String?
        get() = storageRootPathFull?.let {
            Path(it).resolve(gameExePathRelative).toString()
        }

    /**
     * 游戏图标的绝对路径
     *
     * 通过 [storageRootPathFull] 与 [iconPathRelative] 拼接得到。
     * 若 [iconPathRelative] 未设置或 [storageRootPathFull] 无法确定则返回 null。
     *
     * 例: `/data/games/celeste_a1b2c3d4/icon.png`
     */
    val iconPathFull: String?
        get() = iconPathRelative?.let {
            storageRootPathFull?.let { base -> Path(base).resolve(it).toString() }
        }
}
