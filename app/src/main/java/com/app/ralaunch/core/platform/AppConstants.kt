package com.app.ralaunch.core.platform

/**
 * 应用常量 - 跨平台共享
 * 统一管理路径、SharedPreferences 键等常量
 */
object AppConstants {

    // ==================== SharedPreferences ====================
    
    /** 应用主 SharedPreferences 名称 */
    const val PREFS_NAME = "app_prefs"
    
    // 初始化状态键
    object InitKeys {
        const val LEGAL_AGREED = "legal_agreed"
        const val PERMISSIONS_GRANTED = "permissions_granted"
        const val COMPONENTS_EXTRACTED = "components_extracted"
    }
    
    // ==================== 目录名称 ====================
    
    object Dirs {
        /** 游戏数据目录 */
        const val GAMES = "games"
        /** 日志目录 */
        const val LOGS = "logs"
        /** 运行时目录 */
        const val RUNTIMES = "runtimes"
        /** 旧版 .NET 运行时目录 */
        const val LEGACY_DOTNET = "dotnet"
        /** Bootstrap 存放目录 */
        const val BOOTSTRAP = "bootstrap"
        /** 补丁目录 */
        const val PATCHES = "patches"
        /** 模块目录 */
        const val MODULES = "modules"
    }
    
    // ==================== 文件名称 ====================
    
    object Files {
        /** 游戏列表 JSON (根目录，包含游戏名称列表) */
        const val GAME_LIST = "game_list.json"
        /** 单个游戏信息 JSON */
        const val GAME_INFO = "game_info.json"
        /** 设置 JSON */
        const val SETTINGS = "settings.json"
        /** 模块列表 JSON（modules 根目录，包含有序模块 id 与启用状态） */
        const val MODULE_MANAGER = "module_manager.json"
        /** 单个模块元数据 JSON */
        const val MODULE_META = "meta.json"
    }
}
