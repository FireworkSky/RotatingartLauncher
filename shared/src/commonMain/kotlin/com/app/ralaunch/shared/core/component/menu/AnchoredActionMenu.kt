package com.app.ralaunch.shared.core.component.menu

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AnchoredActionMenuStyle {
    Fab,
    TonalIcon
}

data class AnchoredActionItem(
    val key: String,
    val icon: ImageVector,
    val contentDescription: String,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun AnchoredActionMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<AnchoredActionItem>,
    modifier: Modifier = Modifier,
    anchorAlignment: Alignment = Alignment.BottomEnd,
    anchorPadding: PaddingValues = PaddingValues(0.dp),
    style: AnchoredActionMenuStyle = AnchoredActionMenuStyle.Fab,
    itemSpacing: Dp = 12.dp,
    mainButtonSize: Dp = if (style == AnchoredActionMenuStyle.Fab) 56.dp else 48.dp,
    dismissOnOutsideClick: Boolean = true,
    mainIconCollapsed: ImageVector,
    mainIconExpanded: ImageVector,
    mainContentDescriptionCollapsed: String,
    mainContentDescriptionExpanded: String,
    mainContainerColorCollapsed: Color,
    mainContainerColorExpanded: Color,
    mainContentColorCollapsed: Color,
    mainContentColorExpanded: Color,
    rotateMainIcon: Boolean = true,
    tonalIconSize: Dp = 20.dp,
    animateItemAlpha: Boolean = false,
    collapsedOffsetBase: Dp = mainButtonSize + itemSpacing,
    collapsedOffsetStep: Dp = mainButtonSize + itemSpacing
) {
    val defaultItemContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val defaultItemContentColor = MaterialTheme.colorScheme.onSecondaryContainer

    val menuProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        },
        label = "anchored_action_menu_progress"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (expanded) {
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
        },
        label = "anchored_action_menu_icon_rotation"
    )

    val shadowProgress = menuProgress.coerceIn(0f, 1f)
    val childFabElevation = 4.dp * (shadowProgress * shadowProgress)
    val childFabPressedElevation = 6.dp * shadowProgress
    val menuVisible = expanded || menuProgress > 0.01f
    val itemAlpha = if (animateItemAlpha) shadowProgress else 1f
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val mainIcon = if (expanded) mainIconExpanded else mainIconCollapsed
    val mainContentDescription = if (expanded) {
        mainContentDescriptionExpanded
    } else {
        mainContentDescriptionCollapsed
    }
    val mainContainerColor = if (expanded) mainContainerColorExpanded else mainContainerColorCollapsed
    val mainContentColor = if (expanded) mainContentColorExpanded else mainContentColorCollapsed
    val mainIconModifier = when (style) {
        AnchoredActionMenuStyle.Fab -> {
            if (rotateMainIcon) Modifier.rotate(iconRotation) else Modifier
        }

        AnchoredActionMenuStyle.TonalIcon -> {
            if (rotateMainIcon) {
                Modifier
                    .size(tonalIconSize)
                    .rotate(iconRotation)
            } else {
                Modifier.size(tonalIconSize)
            }
        }
    }

    Box(modifier = modifier) {
        if (dismissOnOutsideClick && expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null
                    ) { onExpandedChange(false) }
            )
        }

        Column(
            modifier = Modifier
                .align(anchorAlignment)
                .padding(anchorPadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                val itemCount = items.size
                items.forEachIndexed { index, item ->
                    val collapsedOffset = collapsedOffsetBase + collapsedOffsetStep * (itemCount - 1 - index)
                    val offsetY by animateDpAsState(
                        targetValue = if (expanded) 0.dp else collapsedOffset,
                        animationSpec = if (expanded) {
                            spring(dampingRatio = 0.72f + (index * 0.03f), stiffness = Spring.StiffnessLow)
                        } else {
                            spring(dampingRatio = 0.96f, stiffness = Spring.StiffnessMedium)
                        },
                        label = "anchored_action_menu_offset_${item.key}"
                    )
                    val baseContainerColor = item.containerColor ?: defaultItemContainerColor
                    val baseContentColor = item.contentColor ?: defaultItemContentColor
                    val animatedContainerColor = baseContainerColor.copy(alpha = baseContainerColor.alpha * itemAlpha)
                    val animatedContentColor = baseContentColor.copy(alpha = baseContentColor.alpha * itemAlpha)

                    when (style) {
                        AnchoredActionMenuStyle.Fab -> {
                            FloatingActionButton(
                                onClick = {
                                    if (!menuVisible) return@FloatingActionButton
                                    onExpandedChange(false)
                                    item.onClick()
                                },
                                modifier = Modifier
                                    .size(mainButtonSize)
                                    .offset(y = offsetY),
                                containerColor = animatedContainerColor,
                                contentColor = animatedContentColor,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = childFabElevation,
                                    pressedElevation = childFabPressedElevation,
                                    focusedElevation = childFabElevation,
                                    hoveredElevation = childFabElevation
                                )
                            ) {
                                Icon(item.icon, item.contentDescription)
                            }
                        }

                        AnchoredActionMenuStyle.TonalIcon -> {
                            FilledTonalIconButton(
                                onClick = {
                                    if (!menuVisible) return@FilledTonalIconButton
                                    onExpandedChange(false)
                                    item.onClick()
                                },
                                modifier = Modifier
                                    .size(mainButtonSize)
                                    .offset(y = offsetY),
                                shape = RoundedCornerShape(14.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = animatedContainerColor,
                                    contentColor = animatedContentColor
                                )
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.contentDescription,
                                    modifier = Modifier.size(tonalIconSize)
                                )
                            }
                        }
                    }
                }
            }

            when (style) {
                AnchoredActionMenuStyle.Fab -> {
                    FloatingActionButton(
                        onClick = { onExpandedChange(!expanded) },
                        modifier = Modifier.size(mainButtonSize),
                        containerColor = mainContainerColor,
                        contentColor = mainContentColor,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 6.dp,
                            focusedElevation = 4.dp,
                            hoveredElevation = 4.dp
                        )
                    ) {
                        Icon(
                            imageVector = mainIcon,
                            contentDescription = mainContentDescription,
                            modifier = mainIconModifier
                        )
                    }
                }

                AnchoredActionMenuStyle.TonalIcon -> {
                    FilledTonalIconButton(
                        onClick = { onExpandedChange(!expanded) },
                        modifier = Modifier.size(mainButtonSize),
                        shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = mainContainerColor,
                            contentColor = mainContentColor
                        )
                    ) {
                        Icon(
                            imageVector = mainIcon,
                            contentDescription = mainContentDescription,
                            modifier = mainIconModifier
                        )
                    }
                }
            }
        }
    }
}
