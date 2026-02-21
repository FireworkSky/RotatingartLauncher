package com.app.ralaunch.shared.core.component.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.component.GlassSurface
import com.app.ralaunch.shared.core.model.ui.GameItemUi

/**
 * 游戏列表内容组件 - Material Design 3 毛玻璃面板
 * 
 * 特性：
 * - 双栏布局：左侧游戏网格，右侧毛玻璃详情面板
 * - 右侧面板自动应用 Haze 毛玻璃模糊
 * - 空状态带有发光图标
 */
@Composable
fun GameListContent(
    games: List<GameItemUi>,
    selectedGame: GameItemUi?,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit = {},
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> }
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== 左侧面板 - 游戏网格 =====
        Box(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
        ) {
            GameGridSection(
                games = games,
                selectedGame = selectedGame,
                onGameClick = onGameClick,
                onGameLongClick = onGameLongClick,
                onAddClick = onAddClick,
                isLoading = isLoading,
                iconLoader = iconLoader,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ===== 右侧面板 - 毛玻璃详情 =====
        GlassSurface(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            blurEnabled = true,
            showBorder = true
        ) {
            DetailSection(
                selectedGame = selectedGame,
                onLaunchClick = onLaunchClick,
                onDeleteClick = onDeleteClick,
                onEditClick = onEditClick,
                iconLoader = iconLoader,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GameGridSection(
    games: List<GameItemUi>,
    selectedGame: GameItemUi?,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit,
    onAddClick: () -> Unit,
    isLoading: Boolean,
    iconLoader: @Composable (String?, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (games.isEmpty() && !isLoading) {
            EmptyGameListContent(onAddClick = onAddClick)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(games, key = { it.id }) { game ->
                    GameCard(
                        game = game,
                        isSelected = game.id == selectedGame?.id,
                        onClick = { onGameClick(game) },
                        onLongClick = { onGameLongClick(game) },
                        iconLoader = iconLoader,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(300),
                            fadeOutSpec = tween(200),
                            placementSpec = tween(
                                durationMillis = 280,
                                easing = EaseInOutCubic
                            )
                        ).zIndex(if (game.id == selectedGame?.id) 1f else 0f)
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun DetailSection(
    selectedGame: GameItemUi?,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    iconLoader: @Composable (String?, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = selectedGame,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "game_detail"
        ) { game ->
            if (game != null) {
                GameDetailPanel(
                    game = game,
                    onLaunchClick = onLaunchClick,
                    onDeleteClick = onDeleteClick,
                    onEditClick = onEditClick,
                    iconLoader = iconLoader
                )
            } else {
                EmptySelectionContent()
            }
        }
    }
}

/**
 * 空选择状态组件 - 带发光图标
 */
@Composable
fun EmptySelectionContent(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                // 柔和发光
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.15f),
                                primaryColor.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.maxDimension * 0.8f
                        ),
                        radius = size.maxDimension * 0.8f
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = primaryColor.copy(alpha = 0.55f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "选择一个游戏",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "从左侧列表中选择要启动的游戏",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * 空游戏列表组件 - 带发光图标
 */
@Composable
fun EmptyGameListContent(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    // 柔和发光
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.15f),
                                    primaryColor.copy(alpha = 0.03f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.maxDimension * 0.8f
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = primaryColor.copy(alpha = 0.55f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "还没有游戏",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击下方按钮添加你的第一个游戏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("导入游戏")
            }
        }
    }
}
