package com.app.ralaunch.feature.installer

import com.app.ralaunch.core.model.GameItem

/**
 * 游戏安装插件接口
 * 每个游戏/模组加载器都可以实现自己的安装逻辑
 *
 * install 为挂起函数：过程事件经 [Event] 回调发出（形式与 ArchiveExtractor 一致），
 * 最终结果以 [Result] 返回；成功时 GameItem 已由插件负责保存。
 */
interface GameInstallPlugin {

    /**
     * 插件唯一标识
     */
    val pluginId: String

    /**
     * 插件显示名称
     */
    val displayName: String

    /**
     * 支持的游戏定义列表
     */
    val supportedGames: List<GameDefinition>

    /**
     * 检测游戏文件（基于文件内部特征，不依赖文件名）
     * @param gameFile 游戏文件（本地路径或 SAF URI）
     * @return 游戏检测结果，如果不支持返回 null
     */
    fun detectGame(gameFile: GameFile): GameDetectResult?

    /**
     * 检测模组加载器文件（基于文件内部特征，不依赖文件名）
     * @param modLoaderFile 模组加载器文件（本地路径或 SAF URI）
     * @return 模组加载器检测结果，如果不支持返回 null
     */
    fun detectModLoader(modLoaderFile: GameFile): ModLoaderDetectResult?

    /**
     * 安装游戏；成功时 GameItem 已持久化
     * 存储根目录由插件经 GameManager.createDirectory 自行创建
     *
     * @param gameFile 游戏本体文件（本地路径或 SAF URI；仅模组加载器导入时可为 null）
     * @param modLoaderFile 模组加载器文件（可选）
     * @param callback 安装事件回调
     * @return 安装结果；除协程取消（CancellationException）外不抛异常
     */
    suspend fun install(
        gameFile: GameFile?,
        modLoaderFile: GameFile?,
        callback: ((Event) -> Unit)? = null
    ): Result

    /**
     * 取消安装；进行中的 install 会以 [Result.Cancelled] 收尾
     */
    fun cancel()

    /**
     * 安装事件，形式与 ArchiveExtractor.Event 对齐
     */
    sealed interface Event {
        val message: String

        /**
         * 安装进度
         * @param progress 进度值 (0-100)
         */
        data class Progress(
            override val message: String,
            val progress: Int
        ) : Event

        /**
         * 安装完成；[gameItem] 已持久化
         */
        data class Complete(
            override val message: String,
            val gameItem: GameItem
        ) : Event

        /**
         * 安装失败
         */
        data class Error(
            override val message: String,
            val cause: Throwable? = null
        ) : Event

        /**
         * 安装被取消
         */
        data class Cancelled(
            override val message: String
        ) : Event
    }

    /**
     * 安装结果，形式与 ArchiveExtractor.Result 对齐
     */
    sealed class Result {
        data class Success(val gameItem: GameItem) : Result()
        data class Failure(val message: String, val cause: Throwable? = null) : Result()
        data class Cancelled(val message: String) : Result()
    }
}

/**
 * 游戏检测结果
 */
data class GameDetectResult(
    /** 游戏定义 */
    val definition: GameDefinition,
    /** 检测到的版本（可选） */
    val version: String = ""
)

/**
 * 模组加载器检测结果
 */
data class ModLoaderDetectResult(
    /** 模组加载器定义 */
    val definition: GameDefinition,
    /** 检测到的版本（可选） */
    val version: String = ""
)
