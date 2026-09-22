package com.app.ralaunch.feature.installer

import com.app.ralaunch.feature.installer.plugins.CelesteInstallPlugin
import com.app.ralaunch.feature.installer.plugins.SmapiInstallPlugin
import com.app.ralaunch.feature.installer.plugins.TerrariaInstallPlugin

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
        registerPlugin(CelesteInstallPlugin())
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
     * 根据游戏文件内容自动选择合适的插件
     */
    fun selectPluginForGame(gameFile: GameFile): GameInstallPlugin? {
        for (plugin in plugins) {
            val result = plugin.detectGame(gameFile)
            if (result != null) {
                return plugin
            }
        }
        return null
    }
    
    /**
     * 根据模组加载器文件内容自动选择合适的插件
     */
    fun selectPluginForModLoader(modLoaderFile: GameFile): GameInstallPlugin? {
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
     * 检测游戏文件（基于文件内部特征）
     */
    fun detectGame(gameFile: GameFile): Pair<GameInstallPlugin, GameDetectResult>? {
        for (plugin in plugins) {
            val result = plugin.detectGame(gameFile)
            if (result != null) {
                return Pair(plugin, result)
            }
        }
        return null
    }
    
    /**
     * 检测模组加载器文件（基于文件内部特征）
     */
    fun detectModLoader(modLoaderFile: GameFile): Pair<GameInstallPlugin, ModLoaderDetectResult>? {
        for (plugin in plugins) {
            val result = plugin.detectModLoader(modLoaderFile)
            if (result != null) {
                return Pair(plugin, result)
            }
        }
        return null
    }
}

