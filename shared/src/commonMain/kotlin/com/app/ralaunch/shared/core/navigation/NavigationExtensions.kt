package com.app.ralaunch.shared.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 导航扩展函数和工具
 */

/**
 * NavState 导航扩展：导航到游戏列表
 */
fun NavState.navigateToGames() = navigateTo(NavDestination.GAMES)

/**
 * NavState 导航扩展：导航到控制布局
 */
fun NavState.navigateToControls() = navigateTo(NavDestination.CONTROLS)

/**
 * NavState 导航扩展：导航到下载
 */
fun NavState.navigateToDownload() = navigateTo(NavDestination.DOWNLOAD)

/**
 * NavState 导航扩展：导航到导入
 */
fun NavState.navigateToImport() = navigateTo(NavDestination.IMPORT)

/**
 * NavState 导航扩展：导航到公告
 */
fun NavState.navigateToAnnouncements() = navigateTo(NavDestination.ANNOUNCEMENTS)

/**
 * NavState 导航扩展：导航到设置
 */
fun NavState.navigateToSettings() = navigateTo(NavDestination.SETTINGS)

/**
 * NavState 导航扩展：导航到控制包商店
 */
fun NavState.navigateToControlStore() = navigateTo(Screen.ControlStore, addToBackStack = true)

/**
 * NavState 导航扩展：导航到文件浏览器
 */
fun NavState.navigateToFileBrowser(initialPath: String = "") = 
    navigateTo(Screen.FileBrowser(initialPath), addToBackStack = true)

/**
 * NavState 导航扩展：导航到游戏详情
 */
fun NavState.navigateToGameDetail(storageId: String) =
    navigateTo(Screen.GameDetail(storageId), addToBackStack = true)

/**
 * NavState 导航扩展：导航到控制布局编辑器
 */
fun NavState.navigateToControlEditor(layoutId: String? = null) = 
    navigateTo(Screen.ControlEditor(layoutId), addToBackStack = true)

/**
 * NavState 导航扩展：导航到初始化页面
 */
fun NavState.navigateToInitialization() = 
    navigateTo(Screen.Initialization, addToBackStack = false)

/**
 * 检查当前是否在指定屏幕
 */
fun NavState.isAt(screen: Screen): Boolean = currentScreen == screen

/**
 * 检查当前是否在指定目的地
 */
fun NavState.isAt(destination: NavDestination): Boolean = currentDestination == destination

/**
 * 检查当前是否在主导航页面
 */
fun NavState.isAtMainDestination(): Boolean = currentDestination != null

/**
 * 检查当前是否在子页面
 */
fun NavState.isAtSubScreen(): Boolean = !isAtMainDestination()

/**
 * 处理系统返回键
 * @return true 如果已处理返回，false 如果需要退出
 */
fun NavState.handleBackPress(): Boolean {
    return when {
        canGoBack -> {
            goBack()
            true
        }
        // 如果不在游戏列表，先返回游戏列表
        currentDestination != NavDestination.GAMES -> {
            navigateToGames()
            true
        }
        else -> false
    }
}

/**
 * 返回键处理效果
 * 在 Compose 中注册返回键处理
 */
@Composable
fun BackHandler(
    navState: NavState,
    enabled: Boolean = true,
    onExit: () -> Unit = {}
) {
    // 注意：这里提供一个占位实现
    // 实际的 BackHandler 需要在 Android 平台通过 activity-compose 库实现
    DisposableEffect(enabled) {
        onDispose { }
    }
}

/**
 * 导航事件
 */
sealed interface NavigationEvent {
    /** 导航到指定屏幕 */
    data class NavigateTo(val screen: Screen) : NavigationEvent
    
    /** 导航到主目的地 */
    data class NavigateToDestination(val destination: NavDestination) : NavigationEvent
    
    /** 返回 */
    data object GoBack : NavigationEvent
    
    /** 返回到根页面 */
    data object PopToRoot : NavigationEvent
}

/**
 * 处理导航事件
 */
fun NavState.handleEvent(event: NavigationEvent) {
    when (event) {
        is NavigationEvent.NavigateTo -> navigateTo(event.screen)
        is NavigationEvent.NavigateToDestination -> navigateTo(event.destination)
        is NavigationEvent.GoBack -> goBack()
        is NavigationEvent.PopToRoot -> popToRoot()
    }
}
