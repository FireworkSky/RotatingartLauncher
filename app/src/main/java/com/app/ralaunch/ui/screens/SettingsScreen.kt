package com.app.ralaunch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.ralaunch.ui.model.NavItem
import com.app.ralaunch.ui.screens.settings.AboutScreen
import com.app.ralaunch.ui.screens.settings.ControlScreen
import com.app.ralaunch.ui.screens.settings.GeneralScreen
import com.app.ralaunch.ui.screens.settings.AdvancedScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    refreshKey: Int
) {
    var isToolbarExpanded by remember { mutableStateOf(false) }
    val navController = rememberNavController()

    Scaffold(
        floatingActionButton = {
            SettingsFloatingNavigation(
                isExpanded = isToolbarExpanded,
                onExpandedChange = { expanded ->
                    isToolbarExpanded = expanded
                },
                navController = navController
            )
        }
    ) { innerPadding ->
        NavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            startDestination = "settings_general"
        ) {
            composable("settings_general") {
                GeneralScreen()
            }
            composable("settings_control") {
                ControlScreen()
            }
            composable("settings_advanced") {
                AdvancedScreen()
            }
            composable("settings_about") {
                AboutScreen()
            }
        }
    }
}

@Composable
fun SettingsFloatingNavigation(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    navController: NavController
) {
    val items = listOf(
        NavItem(
            title = "常规",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings,
            route = "settings_general"
        ),
        NavItem(
            title = "控制",
            icon = Icons.Outlined.Gamepad,
            selectedIcon = Icons.Filled.Gamepad,
            route = "settings_control"
        ),
        NavItem(
            title = "高级",
            icon = Icons.Outlined.SettingsSuggest,
            selectedIcon = Icons.Filled.SettingsSuggest,
            route = "settings_advanced"
        ),
        NavItem(
            title = "关于",
            icon = Icons.Outlined.Info,
            selectedIcon = Icons.Filled.Info,
            route = "settings_about"
        )
    )

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: "settings_general"

    // 平滑的颜色动画
    val fabContainerColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "fabContainerColor"
    )

    val fabIconColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "fabIconColor"
    )

    // 面板偏移动画
    val panelOffset by animateDpAsState(
        targetValue = if (isExpanded) (-72).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = DampingRatioMediumBouncy,
            stiffness = StiffnessMediumLow
        ),
        label = "panelOffset"
    )

    // 固定位置的浮动按钮和导航面板容器
    Box(
        modifier = Modifier
            .wrapContentSize()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = panelOffset)
        ) {
            Surface(
                modifier = Modifier
                    .width(200.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = true
                    ),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    items.forEachIndexed { index, item ->
                        // 为每个导航项添加延迟动画
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 200,
                                    delayMillis = 100 + index * 50
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 250,
                                    delayMillis = 50 + index * 30
                                ),
                                initialOffsetY = { it / 2 }
                            ),
                            exit = fadeOut(
                                animationSpec = tween(durationMillis = 100)
                            )
                        ) {
                            FloatingNavigationItem(
                                item = item,
                                isSelected = currentDestination == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        if (currentDestination == item.route) {
                                            onExpandedChange(false)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 主浮动按钮 - 固定位置
        FloatingActionButton(
            onClick = { onExpandedChange(!isExpanded) },
            containerColor = fabContainerColor,
            shape = CircleShape,
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.BottomEnd)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                contentDescription = if (isExpanded) "收起导航" else "展开导航",
                tint = fabIconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 平滑的颜色动画
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isPressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 200),
        label = "navigationItemColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "textColor"
    )

    Surface(
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab
            ) { onClick() },
        color = containerColor,
        tonalElevation = if (isPressed) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}