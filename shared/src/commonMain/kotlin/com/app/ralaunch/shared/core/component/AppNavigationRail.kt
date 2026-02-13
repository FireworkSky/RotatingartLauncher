package com.app.ralaunch.shared.core.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.shared.core.navigation.NavDestination
import com.app.ralaunch.shared.core.theme.AppThemeState
import com.app.ralaunch.shared.core.theme.LocalHazeState
import com.app.ralaunch.shared.core.theme.RaLaunchTheme
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 导航目的地 (兼容别名)
 */
@Deprecated(
    message = "请使用 NavDestination",
    replaceWith = ReplaceWith(
        "NavDestination",
        "com.app.ralaunch.shared.core.navigation.NavDestination"
    )
)
typealias NavigationDestination = NavDestination

/**
 * 应用导航栏 - Material Design 3 丝滑滑动风格
 *
 * 特性：
 * - 毛玻璃背景（当 HazeState 可用时）
 * - 丝滑滑动指示器（单个药丸在项目间 spring 弹性滑动）
 * - 左侧发光条随指示器移动
 * - Crossfade 图标过渡
 * - 弹性按压缩放
 * - 指示器切换脉冲
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppNavigationRail(
    currentDestination: NavDestination?,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    logo: (@Composable () -> Unit)? = null
) {
    // 主题响应
    val themeMode by AppThemeState.themeMode.collectAsState()
    val themeColor by AppThemeState.themeColor.collectAsState()
    val hazeState = LocalHazeState.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // 导航项
    val mainDestinations = remember { NavDestination.entries.filter { it != NavDestination.SETTINGS } }

    // ====== 滑动指示器状态 ======

    // 跟踪每个项目的中心 Y 坐标（根坐标系）
    var containerTopY by remember { mutableFloatStateOf(0f) }
    val itemCenterYs = remember { mutableStateMapOf<NavDestination, Float>() }

    // 记住上次的主导航目的地（子页面时保持指示器位置）
    var lastMainDest by remember { mutableStateOf(NavDestination.GAMES) }
    // 使用 SideEffect 同步更新（比 LaunchedEffect 更即时，避免异步延迟导致指示器不跟随）
    SideEffect {
        if (currentDestination != null) {
            lastMainDest = currentDestination
        }
    }
    val selectedDest = currentDestination ?: lastMainDest

    // 指示器 Y 坐标动画（Animatable 实现首次 snap + 后续 spring）
    val indicatorY = remember { Animatable(0f) }
    var indicatorReady by remember { mutableStateOf(false) }

    // 目标位置（响应选中项 + 位置测量变化）
    val targetCenterY by remember(selectedDest) {
        derivedStateOf {
            itemCenterYs[selectedDest] ?: 0f
        }
    }

    // 驱动指示器平滑滑动
    LaunchedEffect(targetCenterY) {
        if (targetCenterY > 0f) {
            if (!indicatorReady) {
                // 首次：直接跳到位置，无动画
                indicatorY.snapTo(targetCenterY)
                indicatorReady = true
            } else {
                // 后续：快速弹性滑动（加速响应）
                indicatorY.animateTo(
                    targetCenterY,
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 400f
                    )
                )
            }
        }
    }

    // 指示器缩放脉冲（切换时轻微放大再回弹）
    val indicatorScale = remember { Animatable(1f) }
    LaunchedEffect(selectedDest) {
        if (indicatorReady) {
            indicatorScale.animateTo(1.1f, tween(80, easing = FastOutSlowInEasing))
            indicatorScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }

    // ====== UI 渲染 ======

    // 毛玻璃容器
    val baseModifier = modifier
        .fillMaxHeight()
        .width(88.dp)

    val hazeModifier = if (hazeState != null) {
        baseModifier.hazeChild(
            state = hazeState,
            style = HazeMaterials.thin(surfaceColor)
        )
    } else {
        baseModifier.background(surfaceColor.copy(alpha = 0.85f))
    }

    Surface(
        modifier = hazeModifier,
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    containerTopY = coords.positionInRoot().y
                }
        ) {
            // ===== 滑动指示器 =====
            if (indicatorReady) {
                val pillHeight = 52.dp
                val pillWidth = 60.dp
                val glowBarHeight = 28.dp
                val localCenterY = indicatorY.value - containerTopY
                val offsetY = with(density) { localCenterY.toDp() } - pillHeight / 2

                // 药丸背景 - M3 风格活跃指示器
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = offsetY)
                        .size(pillWidth, pillHeight)
                        .scale(indicatorScale.value)
                        .clip(RoundedCornerShape(16.dp))
                        .background(primaryColor.copy(alpha = 0.12f))
                        // 柔和辉光
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.maxDimension * 0.8f
                                ),
                                cornerRadius = CornerRadius(16f)
                            )
                        }
                )

                // 左侧发光条
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = offsetY + (pillHeight - glowBarHeight) / 2)
                        .width(3.5.dp)
                        .height(glowBarHeight)
                        .scale(indicatorScale.value)
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.3f),
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.3f)
                                )
                            )
                        )
                        .drawBehind {
                            // 发光扩散
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = size.height * 0.5f
                                ),
                                topLeft = Offset(-8f, -4f),
                                size = Size(size.width + 16f, size.height + 8f),
                                cornerRadius = CornerRadius(8f)
                            )
                        }
                )
            }

            // ===== 导航项列表 =====
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 主导航项（每项 weight(1f) 填满分配空间，消除点击死区）
                mainDestinations.forEach { destination ->
                    SilkyNavItem(
                        destination = destination,
                        isSelected = selectedDest == destination,
                        onClick = { onNavigate(destination) },
                        onPositioned = { rootCenterY ->
                            itemCenterYs[destination] = rootCenterY
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 设置项固定底部（不参与 weight 分配）
                SilkyNavItem(
                    destination = NavDestination.SETTINGS,
                    isSelected = selectedDest == NavDestination.SETTINGS,
                    onClick = { onNavigate(NavDestination.SETTINGS) },
                    onPositioned = { rootCenterY ->
                        itemCenterYs[NavDestination.SETTINGS] = rootCenterY
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 丝滑导航项 - 弹性按压 + Crossfade 图标 + 平滑颜色过渡
 * 
 * 使用 Box 填满 weight 分配的空间，消除项目间的点击死区
 */
@Composable
private fun SilkyNavItem(
    destination: NavDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 弹性按压缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    // 快速颜色过渡（即时反馈选中状态）
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "navColor"
    )

    // 图标大小脉冲（加速响应）
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 26.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconPulse"
    )

    // Box 填满分配空间（weight），整个区域可点击
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coords ->
                val centerY = coords.positionInRoot().y + coords.size.height / 2f
                onPositioned(centerY)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Crossfade：填充 ↔ 轮廓图标快速过渡
            Crossfade(
                targetState = isSelected,
                animationSpec = tween(140),
                label = "iconCrossfade"
            ) { selected ->
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = destination.label,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (isSelected) 10.sp else 9.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}
