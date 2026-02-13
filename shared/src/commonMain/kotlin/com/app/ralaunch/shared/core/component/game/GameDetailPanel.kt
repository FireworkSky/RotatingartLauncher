package com.app.ralaunch.shared.core.component.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.shared.core.model.ui.GameItemUi

/**
 * 游戏详情面板 - Material Design 3
 * 
 * 特性：
 * - 毛玻璃风格图标展示
 * - 发光启动按钮
 * - 平滑过渡动画
 */
@Composable
fun GameDetailPanel(
    game: GameItemUi,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: (updatedGame: GameItemUi) -> Unit,
    modifier: Modifier = Modifier,
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> }
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Menu state
    var showMenu by remember { mutableStateOf(false) }

    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember(game.id) { mutableStateOf(game.displayedName) }
    var editedDescription by remember(game.id) { mutableStateOf(game.displayedDescription ?: "") }

    // Edit dialog
    if (showEditDialog) {
        Dialog(
            onDismissRequest = { showEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "编辑游戏信息",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("游戏名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("游戏描述") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showEditDialog = false
                            // Reset to original values
                            editedName = game.displayedName
                            editedDescription = game.displayedDescription ?: ""
                        }) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = {
                                // Create updated game with modified fields
                                val updatedGame = game.copy(
                                    displayedName = editedName.trim(),
                                    displayedDescription = editedDescription.trim().ifEmpty { null }
                                )
                                onEditClick(updatedGame)
                                showEditDialog = false
                            },
                            enabled = editedName.isNotBlank()
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 上半部分：Hero 区域
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标 - 带发光背景
            Box(
                modifier = Modifier
                    .size(88.dp)
                    // 发光光晕
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.2f),
                                    primaryColor.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.maxDimension * 0.8f
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (game.iconPathFull != null) {
                        iconLoader(
                            game.iconPathFull,
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 游戏名称
            Text(
                text = game.displayedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // 描述
            game.displayedDescription?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // 下半部分：操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 启动按钮 - 发光效果
            GlowLaunchButton(
                onClick = onLaunchClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            // FAB 菜单按钮
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // 主菜单按钮
                FilledTonalIconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showMenu)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        contentColor = if (showMenu)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多选项",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        } // End of Column

        // 浮动菜单项（放在 Column 外部作为 overlay，在外层 Box 内）
        androidx.compose.animation.AnimatedVisibility(
            visible = showMenu,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                initialScale = 0.8f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            ),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(
                targetScale = 0.8f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 76.dp)  // 16dp matches Column padding, 76dp = 16dp padding + 48dp button + 12dp gap
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑按钮
                FilledTonalIconButton(
                    onClick = {
                        showMenu = false
                        showEditDialog = true
                    },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 删除按钮
                FilledTonalIconButton(
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    } // End of outer Box
}


/**
 * 发光启动按钮 - 带脉冲发光效果
 */
@Composable
private fun GlowLaunchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 脉冲发光动画
    val infiniteTransition = rememberInfiniteTransition(label = "launch_btn_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // 按下缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            // 底部发光
            .drawBehind {
                val glowRadius = 12.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = glowAlpha),
                            primaryColor.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(center.x, size.height),
                        radius = size.width * 0.5f
                    ),
                    topLeft = Offset(-glowRadius, 0f),
                    size = Size(size.width + glowRadius * 2, size.height + glowRadius),
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 4.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "启动游戏",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
