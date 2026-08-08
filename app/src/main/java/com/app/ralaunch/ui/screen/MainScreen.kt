package com.app.ralaunch.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.ralaunch.BuildConfig
import com.app.ralaunch.R
import com.app.ralaunch.strings.StringsResource.Strings

/*******************************************************************************
 * RotatingArtLauncher - MainScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/7/5)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

/**
 * 导航目的地数据类 - 支持选中/未选中双图标
 */
data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * 主屏幕
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    startDestination: String = "games"
) {
    val navController = rememberNavController()
    val colorScheme = MaterialTheme.colorScheme
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 固定导航栏宽度
    val railWidth = 120.dp
    val iconSize = 28.dp
    val itemSpacing = 8.dp
    val headerSize = 64.dp

    // 导航目的地列表 - 每个都有选中和未选中两种图标
    val destinations = listOf(
        NavDestination(
            route = "games",
            label = Strings.nav.games,
            selectedIcon = Icons.Filled.Gamepad,
            unselectedIcon = Icons.Outlined.Gamepad
        ),
        NavDestination(
            route = "controls",
            label = Strings.nav.controls,
            selectedIcon = Icons.Filled.Tune,
            unselectedIcon = Icons.Outlined.Tune
        ),
        NavDestination(
            route = "plugins",
            label = Strings.nav.plugins,
            selectedIcon = Icons.Filled.Extension,
            unselectedIcon = Icons.Outlined.Extension
        ),
        NavDestination(
            route = "announcements",
            label = Strings.nav.announcements,
            selectedIcon = Icons.Filled.Campaign,
            unselectedIcon = Icons.Outlined.Campaign
        ),
        NavDestination(
            route = "settings",
            label = Strings.nav.settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        )
    )

    Row(modifier = modifier.fillMaxSize()) {
        // 导航栏 - 与页面背景色一致
        NavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .width(railWidth),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            header = {
                NavigationRailHeader(
                    modifier = Modifier.padding(top = 16.dp),
                    iconSize = headerSize
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                destinations.forEach { destination ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true

                    NavigationRailItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = null,
                                modifier = Modifier.size(iconSize)
                            )
                        },
                        label = {
                            Text(
                                destination.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = colorScheme.primary,
                            unselectedIconColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            selectedTextColor = colorScheme.primary,
                            unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            indicatorColor = colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ),
                        alwaysShowLabel = true,
                        modifier = Modifier
                            .width(railWidth - 16.dp)
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // 内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("games") { GameListScreen() }
                composable("controls") { ControlScreen() }
                composable("plugins") { PluginsScreen() }
                composable("announcements") { AnnouncementScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

/**
 * 导航栏头部
 */
@Composable
private fun NavigationRailHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // 应用图标
        Box(
            modifier = Modifier
                .size(iconSize)
                .background(
                    colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_init_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(iconSize * 0.6f),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "RaLauncher",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 占位屏幕
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconSize = 120.dp
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(
                        colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(1),
                    style = MaterialTheme.typography.displayLarge,
                    color = colorScheme.primary.copy(alpha = 0.2f),
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ==================== 各页面屏幕函数 ====================

@Composable
private fun GameListScreen() = PlaceholderScreen(
    title = "游戏列表",
    subtitle = "浏览和启动你的游戏"
)

@Composable
private fun ControlScreen() = PlaceholderScreen(
    title = "控制台",
    subtitle = "管理游戏和系统设置"
)

@Composable
private fun PluginsScreen() = PlaceholderScreen(
    title = "插件管理",
    subtitle = "管理和配置已安装的插件"
)