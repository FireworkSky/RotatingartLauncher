package com.app.ralaunch.shared.core.component.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.model.ui.GameItemUi
import com.app.ralaunch.shared.core.theme.RaLaunchTheme

/**
 * 游戏卡片组件 - Material Design 3 + 发光选中效果
 * 
 * 特性：
 * - 选中态外发光效果
 * - 弹性缩放动画
 * - 渐变发光边框
 * - 平滑颜色过渡
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: GameItemUi,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val extendedColors = RaLaunchTheme.extendedColors
    val cardShape = RoundedCornerShape(14.dp)
    val glowRadius = 10.dp
    val glowPadding = 4.dp

    // 弹性缩放动画
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isSelected -> 1.0f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "backgroundColor"
    )

    // 边框色动画
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            primaryColor.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        },
        animationSpec = tween(300),
        label = "borderColor"
    )

    // 发光强度动画
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.45f else 0f,
        animationSpec = tween(400),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(glowPadding)
            .scale(scale)
            // 外发光效果（在卡片外围绘制柔和光晕）
            .drawBehind {
                if (glowAlpha > 0f) {
                    val glowRadiusPx = glowRadius.toPx()
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = glowAlpha * 0.6f),
                                primaryColor.copy(alpha = glowAlpha * 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.maxDimension / 2f + glowRadiusPx
                        ),
                        topLeft = Offset(-glowRadiusPx, -glowRadiusPx),
                        size = Size(size.width + glowRadiusPx * 2, size.height + glowRadiusPx * 2),
                        cornerRadius = CornerRadius(glowRadiusPx + 14.dp.toPx())
                    )
                }
            }
            .clip(cardShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                brush = if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.7f),
                            primaryColor.copy(alpha = 0.3f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(borderColor, borderColor)
                    )
                },
                shape = cardShape
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            // 图标区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                GameCardIconSection(
                    iconPath = game.iconPathFull,
                    iconLoader = iconLoader
                )
            }

            // 底部文字
            Text(
                text = game.displayedName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GameCardIconSection(
    iconPath: String?,
    iconLoader: @Composable (String?, Modifier) -> Unit
) {
    if (iconPath != null) {
        iconLoader(
            iconPath,
            Modifier
                .fillMaxSize(0.75f)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        // 默认图标 - 带毛玻璃背景
        Box(
            modifier = Modifier
                .fillMaxSize(0.55f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GameCardInfoSection(
    name: String,
    isShortcut: Boolean
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isShortcut) {
            Spacer(modifier = Modifier.height(4.dp))
            AssistChip(
                onClick = {},
                label = { Text("快捷方式", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(24.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
    }
}
