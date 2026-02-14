package com.app.ralaunch.shared.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 应用导航路由定义
 * 使用 sealed class 实现类型安全的导航
 */
sealed class Screen(
    val route: String,
    val title: String = ""
) {
    // ===== 主导航页面 =====
    
    /** 游戏列表页 */
    data object Games : Screen("games", "游戏")
    
    /** 控制布局页 */
    data object Controls : Screen("controls", "控制")
    
    /** 下载页 (GOG Client) */
    data object Download : Screen("download", "下载")
    
    /** 导入游戏页 */
    data object Import : Screen("import", "导入")
    
    /** 设置页 */
    data object Settings : Screen("settings", "设置")
    
    // ===== 子页面/全屏页面 =====
    
    /** 控制包商店 */
    data object ControlStore : Screen("control_store", "控制包商店")
    
    /** 初始化页面 */
    data object Initialization : Screen("initialization", "初始化")
    
    /** 文件浏览器 */
    data class FileBrowser(
        val initialPath: String = "",
        val allowedExtensions: List<String> = emptyList(),
        val fileType: String = ""
    ) : Screen(
        route = "file_browser",
        title = "文件浏览"
    )
    
    /** 游戏详情/编辑（全屏模式，参数为游戏存储 ID） */
    data class GameDetail(val storageId: String) : Screen(
        route = "game_detail/$storageId",
        title = "游戏详情"
    )
    
    /** 控制布局编辑器 */
    data class ControlEditor(val layoutId: String? = null) : Screen(
        route = if (layoutId == null) "control_editor" else "control_editor/$layoutId",
        title = "编辑布局"
    )

    companion object {
        /** 从路由字符串解析 Screen */
        fun fromRoute(route: String): Screen? {
            val parts = route.split("/")
            return when (parts.firstOrNull()) {
                "games" -> Games
                "controls" -> Controls
                "download" -> Download
                "import" -> Import
                "settings" -> Settings
                "control_store" -> ControlStore
                "initialization" -> Initialization
                "file_browser" -> FileBrowser(parts.getOrNull(1) ?: "")
                "game_detail" -> parts.getOrNull(1)?.let { GameDetail(it) }
                "control_editor" -> ControlEditor(parts.getOrNull(1))
                else -> null
            }
        }
    }
}

/**
 * 主导航栏目的地
 * 只包含显示在导航栏中的主要页面
 */
enum class NavDestination(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GAMES(
        screen = Screen.Games,
        label = "游戏",
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    ),
    CONTROLS(
        screen = Screen.Controls,
        label = "控制",
        selectedIcon = Icons.Filled.TouchApp,
        unselectedIcon = Icons.Outlined.TouchApp
    ),
    DOWNLOAD(
        screen = Screen.Download,
        label = "下载",
        selectedIcon = Icons.Filled.RocketLaunch,
        unselectedIcon = Icons.Outlined.RocketLaunch
    ),
    IMPORT(
        screen = Screen.Import,
        label = "导入",
        selectedIcon = Icons.Filled.Add,
        unselectedIcon = Icons.Outlined.Add
    ),
    SETTINGS(
        screen = Screen.Settings,
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    );

    val route: String get() = screen.route

    companion object {
        /** 从路由获取导航目的地 */
        fun fromRoute(route: String): NavDestination? {
            return entries.find { it.route == route }
        }

        /** 从 Screen 获取导航目的地 */
        fun fromScreen(screen: Screen): NavDestination? {
            return entries.find { it.screen == screen }
        }
    }
}
