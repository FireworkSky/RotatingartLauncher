package com.app.ralaunch.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.ui.model.NavItem

@Composable
@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
fun MainScreen() {
    val navController = rememberNavController()
    val refreshKey = remember { mutableIntStateOf(0) } // 刷新计数器

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                navController = navController,
                onRefresh = { refreshKey.intValue++ } // 点击刷新时增加计数器
            )
        },
        content = { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavigationRail(
                    modifier = Modifier.width(80.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    NavRail(navController = navController)
                }

                // 使用 key 来强制重组屏幕
                NavHost(
                    navController = navController,
                    startDestination = "game",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("game") {
                        GameScreen(refreshKey = refreshKey.intValue)
                    }
                    composable("file") {
                        FileManagerScreen(refreshKey = refreshKey.intValue)
                    }
                    composable("download") {
                        DownloadScreen(refreshKey = refreshKey.intValue)
                    }
                    composable("settings") {
                        SettingsScreen(refreshKey = refreshKey.intValue)
                    }
                }
            }
        }
    )
}

@Composable
fun NavRail(navController: NavController) {
    val items = listOf(
        NavItem(LocaleManager.strings.navrailGame, Icons.Filled.VideogameAsset, "game"),
        NavItem(LocaleManager.strings.navrailFile, Icons.Filled.Folder, "file"),
        NavItem(LocaleManager.strings.navrailDownload, Icons.Filled.Download, "download"),
        NavItem(LocaleManager.strings.navrailSettings, Icons.Filled.Settings, "settings")
    )

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route ?: "game"

    items.forEach { item ->
        NavigationRailItem(
            selected = currentDestination == item.route,
            onClick = {
                navController.navigate(item.route) {
                    launchSingleTop = true
                }
            },
            icon = {
                Icon(
                    item.icon,
                    null,
                    tint = if (currentDestination == item.route) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            label = {
                Text(
                    item.title,
                    color = if (currentDestination == item.route) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    navController: NavController,
    onRefresh: () -> Unit, // 添加刷新回调
    modifier: Modifier = Modifier
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: "game"

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                when (currentRoute) {
                    "game" -> LocaleManager.strings.navrailGame
                    "file" -> LocaleManager.strings.navrailFile
                    "download" -> LocaleManager.strings.navrailDownload
                    "settings" -> LocaleManager.strings.navrailSettings
                    else -> LocaleManager.strings.appName
                },
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        actions = {
            // 刷新按钮
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }

            // 根据当前页面显示不同的操作按钮
            when (currentRoute) {
                "game" -> {
                    IconButton(onClick = { /* 添加游戏 */ }) {
                        Icon(Icons.Default.Add, contentDescription = "添加游戏")
                    }
                }
                "file" -> {
                    IconButton(onClick = { /* 创建文件 */ }) {
                        Icon(Icons.Default.Add, contentDescription = "创建")
                    }
                }
            }
        }
    )
}