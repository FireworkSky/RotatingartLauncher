package com.app.ralaunch.feature.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import kotlinx.coroutines.delay

/**
 * MD3 风格启动画面覆盖层
 *
 * 与系统 Starting Window (bg_splash) 视觉一致，确保无缝衔接。
 * 数据就绪后播放退场动画：Logo 上移 + 缩小 + 淡出，背景渐变消散。
 *
 * @param isReady 内容是否已就绪，true 时触发退场动画
 * @param onSplashFinished 退场动画完成后的回调
 */
@Composable
fun SplashOverlay(
    isReady: Boolean,
    onSplashFinished: () -> Unit
) {
    // 控制动画阶段：idle → exiting → finished
    var phase by remember { mutableStateOf(SplashPhase.Idle) }

    // 当内容就绪，延迟一小段时间后开始退场（让首帧完整渲染）
    LaunchedEffect(isReady) {
        if (isReady && phase == SplashPhase.Idle) {
            delay(200) // 等待 Compose 完成首次布局
            phase = SplashPhase.Exiting
        }
    }

    // ── 动画参数 ──
    val transition = updateTransition(targetState = phase, label = "splash")

    // 整体背景透明度
    val bgAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 500, easing = FastOutSlowInEasing)
        },
        label = "bgAlpha"
    ) { state ->
        when (state) {
            SplashPhase.Idle -> 1f
            SplashPhase.Exiting -> 0f
        }
    }

    // Logo 缩放
    val logoScale by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 600, easing = FastOutSlowInEasing)
        },
        label = "logoScale"
    ) { state ->
        when (state) {
            SplashPhase.Idle -> 1f
            SplashPhase.Exiting -> 0.7f
        }
    }

    // Logo 透明度
    val logoAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 400, easing = FastOutLinearInEasing)
        },
        label = "logoAlpha"
    ) { state ->
        when (state) {
            SplashPhase.Idle -> 1f
            SplashPhase.Exiting -> 0f
        }
    }

    // Logo 向上位移
    val logoOffsetY by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 600, easing = FastOutSlowInEasing)
        },
        label = "logoOffsetY"
    ) { state ->
        when (state) {
            SplashPhase.Idle -> 0f
            SplashPhase.Exiting -> -60f
        }
    }

    // 品牌文字透明度（比 Logo 先消失）
    val textAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 250, easing = LinearOutSlowInEasing)
        },
        label = "textAlpha"
    ) { state ->
        when (state) {
            SplashPhase.Idle -> 1f
            SplashPhase.Exiting -> 0f
        }
    }

    // 动画完成检测
    val isTransitionComplete = transition.currentState == SplashPhase.Exiting &&
            transition.isRunning.not()

    LaunchedEffect(isTransitionComplete) {
        if (isTransitionComplete) {
            onSplashFinished()
        }
    }

    // ── UI ──
    if (phase != SplashPhase.Exiting || bgAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                    translationY = logoOffsetY
                }
            ) {
                // App Logo - 带圆角和轻微阴影
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                radius = 200f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                clip = true
                                scaleX = 1.42f
                                scaleY = 1.42f
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 品牌名称
                Text(
                    text = stringResource(R.string.main_splash_brand),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        }
    }
}

private enum class SplashPhase {
    Idle,
    Exiting
}
