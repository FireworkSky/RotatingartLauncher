package com.app.ralaunch.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.screen.settings.*
import kotlinx.coroutines.launch

/*******************************************************************************
 * RotatingArtLauncher - SettingsScreen
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

// ==================== 数据类 ====================

data class SettingCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val iconFilled: ImageVector
)

// ==================== 主入口 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val settingCategories = listOf(
        SettingCategory(
            id = "appearance",
            title = Strings.settings.appearance.title,
            icon = Icons.Outlined.Palette,
            iconFilled = Icons.Filled.Palette
        ),
        SettingCategory(
            id = "controls",
            title = Strings.settings.controls.title,
            icon = Icons.Outlined.TouchApp,
            iconFilled = Icons.Filled.TouchApp
        ),
        SettingCategory(
            id = "game",
            title = Strings.settings.game.title,
            icon = Icons.Outlined.SportsEsports,
            iconFilled = Icons.Filled.SportsEsports
        ),
        SettingCategory(
            id = "launcher",
            title = "启动器设置",
            icon = Icons.Outlined.Rocket,
            iconFilled = Icons.Filled.Rocket
        ),
        SettingCategory(
            id = "advanced",
            title = "高级设置",
            icon = Icons.Outlined.Code,
            iconFilled = Icons.Filled.Code
        ),
        SettingCategory(
            id = "about",
            title = "关于",
            icon = Icons.Outlined.Info,
            iconFilled = Icons.Filled.Info
        )
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { settingCategories.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 底部不加外层留白，让分页内容铺满到屏幕底边，间距由各页滚动内容内部提供
                .padding(start = 16.dp, top = 12.dp, end = 16.dp)
        ) {
            M3ETabRow(
                categories = settingCategories,
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    // 底边与屏幕齐平，只保留顶部圆角
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    when (page) {
                        0 -> AppearanceScreen()
                        1 -> ControlsScreen()
                        2 -> GameScreen()
                        3 -> LauncherScreen()
                        4 -> AdvancedScreen()
                        5 -> AboutScreen()
                    }
                }
            }
        }
    }
}

// ==================== M3E Tab Row 组件 ====================

@Composable
private fun M3ETabRow(
    categories: List<SettingCategory>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEachIndexed { index, category ->
                val isSelected = selectedTab == index

                M3ETab(
                    category = category,
                    isSelected = isSelected,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun M3ETab(
    category: SettingCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isSelected) category.iconFilled else category.icon,
                contentDescription = category.title,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )

            Text(
                text = category.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}