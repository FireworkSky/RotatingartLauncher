package com.app.ralaunch.shared.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.ui.theme.LocalHazeState
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

// ==================== 毛玻璃表面 ====================

/**
 * 毛玻璃表面组件
 *
 * 当 HazeState 可用时，自动应用背景模糊效果；
 * 不可用时，退化为带透明度的半透明表面。
 *
 * @param modifier 外部修饰符
 * @param shape 表面圆角形状
 * @param blurEnabled 是否启用模糊
 * @param showBorder 是否显示玻璃边框
 * @param content 内容 Composable
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    blurEnabled: Boolean = true,
    showBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val extendedColors = RaLaunchTheme.extendedColors
    val surfaceColor = MaterialTheme.colorScheme.surface

    val hazeModifier = if (hazeState != null && blurEnabled) {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeMaterials.thin(surfaceColor)
        )
    } else {
        Modifier.background(extendedColors.glassOverlay)
    }

    val borderModifier = if (showBorder) {
        Modifier.border(
            width = 0.5.dp,
            color = extendedColors.glassBorder,
            shape = shape
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(hazeModifier)
            .then(borderModifier),
        content = content
    )
}

/**
 * 毛玻璃表面 - 厚实风格（用于面板、侧栏等）
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassSurfaceRegular(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    blurEnabled: Boolean = true,
    showBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val extendedColors = RaLaunchTheme.extendedColors
    val surfaceColor = MaterialTheme.colorScheme.surface

    val hazeModifier = if (hazeState != null && blurEnabled) {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeMaterials.regular(surfaceColor)
        )
    } else {
        Modifier.background(extendedColors.glassSurface)
    }

    val borderModifier = if (showBorder) {
        Modifier.border(
            width = 0.5.dp,
            color = extendedColors.glassBorder,
            shape = shape
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(hazeModifier)
            .then(borderModifier),
        content = content
    )
}

// ==================== 发光效果修饰符 ====================

/**
 * 发光效果修饰符 - 为元素添加外发光
 *
 * @param color 发光颜色
 * @param radius 发光半径
 * @param alpha 发光透明度
 */
fun Modifier.glowEffect(
    color: Color,
    radius: Dp = 12.dp,
    alpha: Float = 0.6f
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.5f),
                Color.Transparent
            ),
            center = center,
            radius = size.maxDimension / 2f + radiusPx
        ),
        topLeft = Offset(-radiusPx, -radiusPx),
        size = Size(size.width + radiusPx * 2, size.height + radiusPx * 2),
        cornerRadius = CornerRadius(radiusPx * 1.5f)
    )
}

/**
 * 脉冲发光修饰符 - 带动画的发光效果
 *
 * @param color 发光颜色
 * @param radius 发光半径
 * @param enabled 是否启用
 */
fun Modifier.pulseGlow(
    color: Color,
    radius: Dp = 16.dp,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    this.drawBehind {
        val radiusPx = radius.toPx()
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = glowAlpha),
                    color.copy(alpha = glowAlpha * 0.4f),
                    Color.Transparent
                ),
                center = center,
                radius = size.maxDimension / 2f + radiusPx
            ),
            topLeft = Offset(-radiusPx, -radiusPx),
            size = Size(size.width + radiusPx * 2, size.height + radiusPx * 2),
            cornerRadius = CornerRadius(radiusPx * 1.5f)
        )
    }
}

/**
 * 发光边框修饰符 - 为元素添加发光边框线
 *
 * @param color 发光颜色
 * @param width 边框宽度
 * @param shape 边框形状
 * @param animated 是否添加动画
 */
fun Modifier.glowBorder(
    color: Color,
    width: Dp = 1.5.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    animated: Boolean = false
): Modifier = composed {
    if (animated) {
        val infiniteTransition = rememberInfiniteTransition(label = "glow_border")
        val borderAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "border_alpha"
        )
        this.border(width, color.copy(alpha = borderAlpha), shape)
    } else {
        this.border(width, color, shape)
    }
}

/**
 * 微光（Shimmer）效果修饰符 - 水平光条扫过效果
 *
 * @param enabled 是否启用
 */
fun Modifier.shimmerEffect(
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val shimmerColors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.2f),
        Color.White.copy(alpha = 0.12f),
        Color.Transparent
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    this.drawBehind {
        val shimmerWidth = size.width * 0.4f
        val offset = translateAnim * (size.width + shimmerWidth) - shimmerWidth

        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(offset, 0f),
                end = Offset(offset + shimmerWidth, size.height)
            ),
            size = size
        )
    }
}

/**
 * 底部渐变发光 - 为按钮等元素底部添加柔和光晕
 */
fun Modifier.bottomGlow(
    color: Color,
    height: Dp = 8.dp,
    alpha: Float = 0.4f
): Modifier = this.drawBehind {
    val glowHeight = height.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                color.copy(alpha = alpha)
            ),
            startY = size.height - glowHeight,
            endY = size.height + glowHeight * 0.5f
        ),
        topLeft = Offset(-glowHeight, size.height - glowHeight),
        size = Size(size.width + glowHeight * 2, glowHeight * 1.5f)
    )
}
