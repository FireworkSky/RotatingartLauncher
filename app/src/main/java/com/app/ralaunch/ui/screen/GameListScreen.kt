package com.app.ralaunch.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.ralaunch.R
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.utils.GameManager
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/*******************************************************************************
 * RotatingArtLauncher - GameListScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/9/19)
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
 * 游戏列表页面 - 纯 Material Design 3，无 ViewModel
 *
 * 直接从 [GameManager] 收藏游戏状态，双栏布局：左侧游戏网格 + 右侧详情面板。
 * 启动/删除直接调用 [GameManager]，反馈用 Toast。
 */
@Composable
fun GameListScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gamesFlow = remember { GameManager.games }
    val games by gamesFlow.collectAsStateWithLifecycle(GameManager.currentGames)

    var selectedGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<GameItem?>(null) }

    val selectedGame = games.firstOrNull { it.id == selectedGameId } ?: games.firstOrNull()

    GameListContent(
        games = games,
        selectedGame = selectedGame,
        onGameClick = { selectedGameId = it.id },
        onLaunchClick = {
            val game = selectedGame
            if (game == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.main_select_game_first),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                scope.launch {
                    runCatching { GameManager.launch(game.id).start() }
                        .onFailure {
                            Timber.e(it, "Launch failed: ${game.displayedName}")
                            Toast.makeText(
                                context,
                                context.getString(R.string.game_launch_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        },
        onDeleteClick = { selectedGame?.let { pendingDeletion = it } },
        modifier = modifier
    )

    pendingDeletion?.let { game ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.main_delete_game_confirm_message, game.displayedName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletion = null
                    scope.launch {
                        runCatching { GameManager.remove(game.id) }
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.main_game_deleted),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure {
                                Timber.e(it, "Delete failed: ${game.id}")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_operation_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 游戏列表内容：左侧游戏网格 + 右侧详情面板
 */
@Composable
fun GameListContent(
    games: List<GameItem>,
    selectedGame: GameItem?,
    onGameClick: (GameItem) -> Unit,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 页面背景与项目统一：不透明 Surface(tonalElevation = 2.dp)，遮住窗口背景渐变，
    // 且与 NavigationRail 的 surfaceColorAtElevation(2.dp) 及其他页面一致
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                if (games.isEmpty()) {
                    EmptyGameListContent(modifier = Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(games, key = { it.id }) { game ->
                            GameCard(
                                game = game,
                                isSelected = game.id == selectedGame?.id,
                                onClick = { onGameClick(game) }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                // 高于页面背景(surfaceContainerLow)一级，对齐 SettingsGroup 容器层级
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                GameDetailPanel(
                    game = selectedGame,
                    onLaunchClick = onLaunchClick,
                    onDeleteClick = onDeleteClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 游戏卡片 - 标准 MD3 Card，选中态用容器色区分
 */
@Composable
private fun GameCard(
    game: GameItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                // 高于页面背景一级，对齐 AnnouncementScreen 列表卡片层级
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                GameIcon(
                    iconPath = game.iconPathFull,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = game.displayedName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

/**
 * 详情面板：图标 + 名称 + 描述 + 启动/删除按钮
 */
@Composable
private fun GameDetailPanel(
    game: GameItem?,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (game == null) {
        EmptySelectionContent(modifier = modifier)
        return
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GameIcon(
                iconPath = game.iconPathFull,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = game.displayedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            game.displayedDescription.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onLaunchClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.main_launch_game),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalIconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

/**
 * 游戏图标：优先加载本地图标文件，缺失时回退默认图标
 */
@Composable
private fun GameIcon(
    iconPath: String?,
    modifier: Modifier = Modifier
) {
    if (iconPath != null) {
        AsyncImage(
            model = File(iconPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.medium)
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    }
}

/**
 * 空游戏列表提示
 */
@Composable
private fun EmptyGameListContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SportsEsports,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.patch_no_games),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 未选择游戏提示
 */
@Composable
private fun EmptySelectionContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.main_no_game_selected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.main_select_game),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// roughly the width and height of pixel 10XL
@Preview(
    device = "spec:width=891dp,height=411dp,dpi=420,orientation=landscape"
)
@Composable
fun PreviewGameListContent() {
    val sampleGames = listOf(
        GameItem(
            id = "celeste_demo",
            displayedName = "Celeste",
            gameId = "celeste",
            gameExePathRelative = "Celeste.exe"
        ),
        GameItem(
            id = "terraria_demo",
            displayedName = "Terraria",
            displayedDescription = "Dig, fight, explore, build!",
            gameId = "terraria",
            gameExePathRelative = "Terraria.exe"
        ),
        GameItem(
            id = "stardew_demo",
            displayedName = "Stardew Valley",
            gameId = "stardew",
            gameExePathRelative = "Stardew Valley.exe"
        )
    )
    GameListContent(
        games = sampleGames,
        selectedGame = sampleGames.first(),
        onGameClick = {},
        onLaunchClick = {},
        onDeleteClick = {}
    )
}
