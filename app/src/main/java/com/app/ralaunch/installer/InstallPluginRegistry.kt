package com.app.ralaunch.installer

import java.io.File

/**
 * 游戏安装插件注册表
 * 管理所有可用的安装插件
 */
object InstallPluginRegistry {
    
    private val plugins = mutableListOf<GameInstallPlugin>()
    
    init {
        // 注册内置插件
        registerPlugin(TerrariaInstallPlugin())
        registerPlugin(SmapiInstallPlugin())
    }
    
    /**
     * 注册插件
     */
    fun registerPlugin(plugin: GameInstallPlugin) {
        plugins.add(plugin)
    }
    
    /**
     * 获取所有已注册的插件
     */
    fun getAllPlugins(): List<GameInstallPlugin> = plugins.toList()
    
    /**
     * 根据游戏文件自动选择合适的插件
     */
    fun selectPluginForGame(gameFile: File): GameInstallPlugin? {
        for (plugin in plugins) {
            val result = plugin.detectGame(gameFile)
            if (result != null) {
                return plugin
            }
        }
        return null
    }
    
    /**
     * 根据模组加载器文件自动选择合适的插件
     */
    fun selectPluginForModLoader(modLoaderFile: File): GameInstallPlugin? {
        for (plugin in plugins) {
            val result = plugin.detectModLoader(modLoaderFile)
            if (result != null) {
                return plugin
            }
        }
        return null
    }
    
    /**
     * 根据插件 ID 获取插件
     */
    fun getPluginById(pluginId: String): GameInstallPlugin? {
        return plugins.find { it.pluginId == pluginId }
    }
    
    /**
     * 检测游戏文件
     */
    fun detectGame(gameFile: File): Pair<GameInstallPlugin, GameDetectResult>? {
        for (plugin in plugins) {
            val result = plugin.detectGame(gameFile)
            if (result != null) {
                return Pair(plugin, result)
            }
        }
        return null
    }
    
    /**
     * 检测模组加载器文件
     */
    fun detectModLoader(modLoaderFile: File): Pair<GameInstallPlugin, ModLoaderDetectResult>? {
        for (plugin in plugins) {
            val result = plugin.detectModLoader(modLoaderFile)
            if (result != null) {
                return Pair(plugin, result)
            }
        }
        return null
    }
}

