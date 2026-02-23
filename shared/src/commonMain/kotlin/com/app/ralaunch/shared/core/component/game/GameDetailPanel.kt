package com.app.ralaunch.shared.core.component.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.component.menu.AnchoredActionItem
import com.app.ralaunch.shared.core.component.menu.AnchoredActionMenu
import com.app.ralaunch.shared.core.component.menu.AnchoredActionMenuStyle
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
    onEditClick: () -> Unit,
    launchButtonText: String,
    editContentDescription: String,
    deleteContentDescription: String,
    moreOptionsContentDescription: String,
    collapseOptionsContentDescription: String,
    modifier: Modifier = Modifier,
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> }
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Menu state
    var showMenu by remember { mutableStateOf(false) }

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
            // 启动按钮
            LaunchButton(
                onClick = onLaunchClick,
                text = launchButtonText,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.size(48.dp))
        }
        } // End of Column

        AnchoredActionMenu(
            expanded = showMenu,
            onExpandedChange = { showMenu = it },
            items = listOf(
                AnchoredActionItem(
                    key = "edit",
                    icon = Icons.Default.Edit,
                    contentDescription = editContentDescription,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onEditClick
                ),
                AnchoredActionItem(
                    key = "delete",
                    icon = Icons.Default.Delete,
                    contentDescription = deleteContentDescription,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = onDeleteClick
                )
            ),
            modifier = Modifier.matchParentSize(),
            anchorAlignment = Alignment.BottomEnd,
            anchorPadding = PaddingValues(end = 16.dp, bottom = 16.dp),
            style = AnchoredActionMenuStyle.TonalIcon,
            itemSpacing = 8.dp,
            mainButtonSize = 48.dp,
            dismissOnOutsideClick = false,
            mainIconCollapsed = Icons.Default.MoreVert,
            mainIconExpanded = Icons.Default.Close,
            mainContentDescriptionCollapsed = moreOptionsContentDescription,
            mainContentDescriptionExpanded = collapseOptionsContentDescription,
            mainContainerColorCollapsed = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            mainContainerColorExpanded = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            mainContentColorCollapsed = MaterialTheme.colorScheme.onSecondaryContainer,
            mainContentColorExpanded = MaterialTheme.colorScheme.primary,
            rotateMainIcon = true,
            animateItemAlpha = true,
            collapsedOffsetBase = 56.dp,
            collapsedOffsetStep = 56.dp
        )
    } // End of outer Box
}


/** 启动按钮 */
@Composable
private fun LaunchButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 按下缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
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
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
