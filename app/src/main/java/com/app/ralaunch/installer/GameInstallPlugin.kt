package com.app.ralaunch.installer

import java.io.File

/**
 * 游戏安装插件接口
 * 每个游戏/模组加载器都可以实现自己的安装逻辑
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
     * 支持的游戏类型列表
     */
    val supportedGameTypes: List<String>
    
    /**
     * 检测游戏文件
     * @param gameFile 游戏文件路径
     * @return 游戏信息，如果不支持返回 null
     */
    fun detectGame(gameFile: File): GameDetectResult?
    
    /**
     * 检测模组加载器文件
     * @param modLoaderFile 模组加载器文件路径
     * @return 模组加载器信息，如果不支持返回 null
     */
    fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult?
    
    /**
     * 安装游戏
     * @param gameFile 游戏本体文件
     * @param modLoaderFile 模组加载器文件（可选）
     * @param outputDir 输出目录
     * @param callback 安装回调
     */
    fun install(
        gameFile: File,
        modLoaderFile: File?,
        outputDir: File,
        callback: InstallCallback
    )
    
    /**
     * 取消安装
     */
    fun cancel()
}

/**
 * 游戏检测结果
 */
data class GameDetectResult(
    val gameName: String,
    val gameType: String,
    val version: String = "",
    val launchTarget: String? = null,
    val iconPath: String? = null
)

/**
 * 模组加载器检测结果
 */
data class ModLoaderDetectResult(
    val name: String,
    val type: String,
    val version: String = "",
    val launchTarget: String,
    val iconPath: String? = null
)

