package com.app.ralaunch.feature.main.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.core.navigation.NavDestination
import com.app.ralaunch.core.navigation.NavState
import com.app.ralaunch.core.navigation.Screen
import com.app.ralaunch.core.navigation.rememberNavState
import com.app.ralaunch.core.theme.LocalHazeState
import com.app.ralaunch.core.theme.RaLaunchTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * 主应用 Composable - Material Design 3 + 毛玻璃
 * 
 * 特性：
 * - 自动创建 HazeState 并提供给子组件
 * - 背景层作为模糊源
 * - 导航栏和面板自动应用毛玻璃效果
 * - 页面切换带有方向性滑动+淡入淡出动画
 */
@Composable
fun MainApp(
    navState: NavState,
    modifier: Modifier = Modifier,
    showAnnouncementBadge: Boolean = false,
    // 背景层 (视频/图片) - 作为毛玻璃源
    backgroundLayer: @Composable BoxScope.() -> Unit = {},
    // 外部提供的 HazeState（可选，不提供则内部创建）
    externalHazeState: HazeState? = null,
    pageContent: @Composable (Screen) -> Unit
) {
    // 使用外部或内部 HazeState
    val hazeState = externalHazeState ?: remember { HazeState() }

    // 通过 CompositionLocal 提供 HazeState 给所有子组件
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = modifier.fillMaxSize()) {
            // 背景层 - 标记为毛玻璃源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
            ) {
                backgroundLayer()
            }

            // 主内容 - NavigationRail + Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                // 当前导航目的地（使用 derivedStateOf 确保状态变更被正确追踪）
                val currentDest by remember {
                    derivedStateOf { navState.currentDestination }
                }
                val navGamesLabel = stringResource(R.string.game_list_title)
                val navControlsLabel = stringResource(R.string.main_control_layout)
                val navDownloadLabel = stringResource(R.string.main_download)
                val navImportLabel = stringResource(R.string.main_import_game)
                val navAnnouncementsLabel = stringResource(R.string.main_announcements)
                val navSettingsLabel = stringResource(R.string.main_settings)

                // 左侧导航栏（会自动从 LocalHazeState 获取模糊状态）
                AppNavigationRail(
                    currentDestination = currentDest,
                    onNavigate = { navState.navigateTo(it) },
                    showAnnouncementBadge = showAnnouncementBadge,
                    labelProvider = { destination ->
                        when (destination) {
                            NavDestination.GAMES -> navGamesLabel
                            NavDestination.CONTROLS -> navControlsLabel
                            NavDestination.DOWNLOAD -> navDownloadLabel
                            NavDestination.IMPORT -> navImportLabel
                            NavDestination.ANNOUNCEMENTS -> navAnnouncementsLabel
                            NavDestination.SETTINGS -> navSettingsLabel
                        }
                    },
                    logo = {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .graphicsLayer {
                                    clip = true
                                    scaleX = 1.42f
                                    scaleY = 1.42f
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                )

                // 主内容区域 - 带平滑页面切换动画
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 跟踪是否为首次加载（首次不播放动画，避免抖动）
                    var isFirstComposition by remember { mutableStateOf(true) }

                    AnimatedContent(
                        targetState = navState.currentScreen,
                        transitionSpec = {
                            if (isFirstComposition) {
                                // 首次加载：无动画，直接显示
                                (fadeIn(animationSpec = snap()))
                                    .togetherWith(fadeOut(animationSpec = snap()))
                                    .using(SizeTransform(clip = false))
                            } else {
                                // 后续切换：入场延迟启动，让退场先播放，减少同时渲染压力
                                (scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = tween(
                                        durationMillis = 400,
                                        delayMillis = 80,
                                        easing = EaseInOutCubic
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        delayMillis = 80,
                                        easing = EaseInOutCubic
                                    )
                                ))
                                    .togetherWith(
                                        // 退场：立即开始，稍快结束
                                        scaleOut(
                                            targetScale = 0.92f,
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = EaseInOutCubic
                                            )
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = 250,
                                                easing = EaseInOutCubic
                                            )
                                        )
                                    )
                                    .using(SizeTransform(clip = false))
                            }
                        },
                        contentKey = { it.route },
                        label = "pageTransition"
                    ) { targetScreen ->
                        // 首次加载完成后标记
                        LaunchedEffect(Unit) {
                            if (isFirstComposition) {
                                isFirstComposition = false
                            }
                        }

                        // 首次加载直接渲染，后续切换延迟渲染以避免阻塞动画
                        if (isFirstComposition) {
                            pageContent(targetScreen)
                        } else {
                            DeferredPage {
                                pageContent(targetScreen)
                            }
                        }
                    }

                    // 加载指示器已由 SplashOverlay 取代
                }
            }
        }
    }
}

/**
 * 延迟渲染容器 - 异步加载优化
 *
 * 页面切换时，先让动画播放 2 帧，再渲染重内容，
 * 避免新页面的首次组合阻塞动画线程导致卡顿。
 */
@Composable
private fun DeferredPage(
    content: @Composable () -> Unit
) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 等待 2 帧：让缩放/淡出动画先启动
        withFrameMillis { }
        withFrameMillis { }
        ready = true
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (ready) {
            content()
        }
    }
}

/**
 * 占位屏幕 - 用于尚未实现的页面
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * MainApp 预览
 */
@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun MainAppPreview() {
    RaLaunchTheme {
        MainApp(
            navState = rememberNavState(),
            pageContent = {
                PlaceholderScreen(title = it.route)
            }
        )
    }
}
