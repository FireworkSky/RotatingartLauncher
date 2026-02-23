package com.app.ralaunch.feature.main

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.app.ralaunch.R
import com.app.ralaunch.shared.core.component.AppNavigationRail
import com.app.ralaunch.shared.core.component.dialogs.RendererOption
import com.app.ralaunch.shared.core.navigation.*
import com.app.ralaunch.shared.core.theme.LocalHazeState
import com.app.ralaunch.shared.core.theme.RaLaunchTheme
import com.app.ralaunch.shared.core.model.ui.GameItemUi
import com.app.ralaunch.shared.core.component.game.GameInfoEditSubScreen
import com.app.ralaunch.shared.core.component.game.GameListContent
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
    // 应用 Logo
    appLogo: (@Composable () -> Unit)? = null,
    // 游戏列表相关
    games: List<GameItemUi> = emptyList(),
    selectedGame: GameItemUi? = null,
    isLoading: Boolean = false,
    onGameClick: (GameItemUi) -> Unit = {},
    onGameLongClick: (GameItemUi) -> Unit = {},
    onLaunchClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onEditClick: (updatedGame: GameItemUi) -> Unit = {},
    showAnnouncementBadge: Boolean = false,
    gameRendererOptions: List<RendererOption> = emptyList(),
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> },
    // 页面内容 Slots
    controlsContent: (@Composable () -> Unit)? = null,
    downloadContent: (@Composable () -> Unit)? = null,
    announcementsContent: (@Composable () -> Unit)? = null,
    settingsContent: (@Composable () -> Unit)? = null,
    importContent: (@Composable () -> Unit)? = null,
    controlStoreContent: (@Composable () -> Unit)? = null,
    fileBrowserContent: (@Composable (initialPath: String, allowedExtensions: List<String>, fileType: String) -> Unit)? = null,
    // 背景层 (视频/图片) - 作为毛玻璃源
    backgroundLayer: @Composable BoxScope.() -> Unit = {},
    // 外部提供的 HazeState（可选，不提供则内部创建）
    externalHazeState: HazeState? = null
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
                val resolvedControlsContent = controlsContent ?: { PlaceholderScreen(stringResource(R.string.main_control_layout)) }
                val resolvedDownloadContent = downloadContent ?: { PlaceholderScreen(stringResource(R.string.main_download)) }
                val resolvedAnnouncementsContent = announcementsContent ?: { PlaceholderScreen(stringResource(R.string.main_announcements)) }
                val resolvedSettingsContent = settingsContent ?: { PlaceholderScreen(stringResource(R.string.main_settings)) }
                val resolvedImportContent = importContent ?: { PlaceholderScreen(stringResource(R.string.main_import_game)) }
                val resolvedControlStoreContent = controlStoreContent ?: { PlaceholderScreen(stringResource(R.string.main_control_store)) }
                val resolvedFileBrowserContent = fileBrowserContent ?: { _, _, _ ->
                    PlaceholderScreen(stringResource(R.string.main_file_browser))
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
                    logo = appLogo
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
                            PageContent(
                                targetScreen = targetScreen,
                                navState = navState,
                                games = games,
                                selectedGame = selectedGame,
                                isLoading = isLoading,
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onLaunchClick = onLaunchClick,
                                onDeleteClick = onDeleteClick,
                                onEditClick = onEditClick,
                                gameRendererOptions = gameRendererOptions,
                                iconLoader = iconLoader,
                                controlsContent = resolvedControlsContent,
                                downloadContent = resolvedDownloadContent,
                                announcementsContent = resolvedAnnouncementsContent,
                                settingsContent = resolvedSettingsContent,
                                importContent = resolvedImportContent,
                                controlStoreContent = resolvedControlStoreContent,
                                fileBrowserContent = resolvedFileBrowserContent
                            )
                        } else {
                            DeferredPage {
                                PageContent(
                                    targetScreen = targetScreen,
                                    navState = navState,
                                    games = games,
                                    selectedGame = selectedGame,
                                    isLoading = isLoading,
                                    onGameClick = onGameClick,
                                    onGameLongClick = onGameLongClick,
                                    onLaunchClick = onLaunchClick,
                                    onDeleteClick = onDeleteClick,
                                    onEditClick = onEditClick,
                                    gameRendererOptions = gameRendererOptions,
                                    iconLoader = iconLoader,
                                    controlsContent = resolvedControlsContent,
                                    downloadContent = resolvedDownloadContent,
                                    announcementsContent = resolvedAnnouncementsContent,
                                    settingsContent = resolvedSettingsContent,
                                    importContent = resolvedImportContent,
                                    controlStoreContent = resolvedControlStoreContent,
                                    fileBrowserContent = resolvedFileBrowserContent
                                )
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
 * 页面内容路由 - 根据当前 Screen 渲染对应页面
 */
@Composable
private fun PageContent(
    targetScreen: Screen,
    navState: NavState,
    games: List<GameItemUi>,
    selectedGame: GameItemUi?,
    isLoading: Boolean,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: (updatedGame: GameItemUi) -> Unit,
    gameRendererOptions: List<RendererOption>,
    iconLoader: @Composable (String?, Modifier) -> Unit,
    controlsContent: @Composable () -> Unit,
    downloadContent: @Composable () -> Unit,
    announcementsContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    importContent: @Composable () -> Unit,
    controlStoreContent: @Composable () -> Unit,
    fileBrowserContent: @Composable (String, List<String>, String) -> Unit
) {
    val noGameSelectedTitle = stringResource(R.string.main_no_game_selected)
    val noGameSelectedMessage = stringResource(R.string.main_select_game)
    val emptyGamesTitle = stringResource(R.string.patch_no_games)
    val emptyGamesMessage = stringResource(R.string.main_empty_game_list_hint)
    val addGameButtonText = stringResource(R.string.main_add_game)
    val launchGameButtonText = stringResource(R.string.main_launch_game)
    val editContentDescription = stringResource(R.string.control_layout_edit)
    val deleteContentDescription = stringResource(R.string.delete)
    val moreActionsContentDescription = stringResource(R.string.control_layout_more_actions)
    val collapseActionsContentDescription = stringResource(R.string.control_layout_hide_quick_actions)
    val followGlobalSettingsText = stringResource(R.string.renderer_follow_global_settings)
    val editGameInfoTitle = stringResource(R.string.main_edit_game_info)
    val backContentDescription = stringResource(R.string.back)
    val editDisplayInfoText = stringResource(R.string.main_edit_display_info)
    val gameNameLabelText = stringResource(R.string.game_name)
    val gameDescriptionLabelText = stringResource(R.string.game_description)
    val rendererOptionalText = stringResource(R.string.main_renderer_optional)
    val rendererOverrideLabelText = stringResource(R.string.main_renderer_override)
    val followGlobalButtonText = stringResource(R.string.renderer_follow_global)
    val selectRendererButtonText = stringResource(R.string.renderer_select)
    val cancelButtonText = stringResource(R.string.cancel)
    val saveButtonText = stringResource(R.string.control_edit_save)

    when (targetScreen) {
        is Screen.Games -> {
            GamesPageContent(
                games = games,
                selectedGame = selectedGame,
                isLoading = isLoading,
                onGameClick = onGameClick,
                onGameLongClick = onGameLongClick,
                onLaunchClick = onLaunchClick,
                onDeleteClick = onDeleteClick,
                onEditClick = {
                    selectedGame?.id?.let { navState.navigateToGameDetail(it) }
                },
                onAddClick = { navState.navigateTo(Screen.Import) },
                noSelectionTitle = noGameSelectedTitle,
                noSelectionMessage = noGameSelectedMessage,
                emptyGamesTitle = emptyGamesTitle,
                emptyGamesMessage = emptyGamesMessage,
                addGameButtonText = addGameButtonText,
                launchGameButtonText = launchGameButtonText,
                editActionContentDescription = editContentDescription,
                deleteActionContentDescription = deleteContentDescription,
                moreActionsContentDescription = moreActionsContentDescription,
                collapseActionsContentDescription = collapseActionsContentDescription,
                iconLoader = iconLoader
            )
        }
        is Screen.Controls -> controlsContent()
        is Screen.Download -> downloadContent()
        is Screen.Announcements -> announcementsContent()
        is Screen.Settings -> settingsContent()
        is Screen.Import -> importContent()
        is Screen.ControlStore -> controlStoreContent()
        is Screen.FileBrowser -> fileBrowserContent(targetScreen.initialPath, targetScreen.allowedExtensions, targetScreen.fileType)
        is Screen.GameDetail -> {
            val game = games.find { it.id == targetScreen.storageId }
            if (game != null) {
                GameInfoEditSubScreen(
                    game = game,
                    rendererOptions = gameRendererOptions,
                    onBack = { navState.goBack() },
                    onSave = onEditClick,
                    followGlobalSettingsText = followGlobalSettingsText,
                    editGameInfoTitle = editGameInfoTitle,
                    backContentDescription = backContentDescription,
                    editDisplayInfoText = editDisplayInfoText,
                    gameNameLabelText = gameNameLabelText,
                    gameDescriptionLabelText = gameDescriptionLabelText,
                    rendererOptionalText = rendererOptionalText,
                    rendererOverrideLabelText = rendererOverrideLabelText,
                    followGlobalButtonText = followGlobalButtonText,
                    selectRendererButtonText = selectRendererButtonText,
                    cancelButtonText = cancelButtonText,
                    saveButtonText = saveButtonText
                )
            } else {
                PlaceholderScreen(stringResource(R.string.main_game_not_found, targetScreen.storageId))
            }
        }
        is Screen.ControlEditor -> PlaceholderScreen(stringResource(R.string.main_control_editor_placeholder))
        is Screen.Initialization -> { /* 已移至独立 Activity */ }
    }
}

/**
 * 游戏列表页面内容
 */
@Composable
private fun GamesPageContent(
    games: List<GameItemUi>,
    selectedGame: GameItemUi?,
    isLoading: Boolean,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onAddClick: () -> Unit,
    noSelectionTitle: String,
    noSelectionMessage: String,
    emptyGamesTitle: String,
    emptyGamesMessage: String,
    addGameButtonText: String,
    launchGameButtonText: String,
    editActionContentDescription: String,
    deleteActionContentDescription: String,
    moreActionsContentDescription: String,
    collapseActionsContentDescription: String,
    iconLoader: @Composable (String?, Modifier) -> Unit
) {
    GameListContent(
        games = games,
        selectedGame = selectedGame,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
        onLaunchClick = onLaunchClick,
        onDeleteClick = onDeleteClick,
        onEditClick = onEditClick,
        onAddClick = onAddClick,
        noSelectionTitle = noSelectionTitle,
        noSelectionMessage = noSelectionMessage,
        emptyGamesTitle = emptyGamesTitle,
        emptyGamesMessage = emptyGamesMessage,
        addGameButtonText = addGameButtonText,
        launchGameButtonText = launchGameButtonText,
        editActionContentDescription = editActionContentDescription,
        deleteActionContentDescription = deleteActionContentDescription,
        moreActionsContentDescription = moreActionsContentDescription,
        collapseActionsContentDescription = collapseActionsContentDescription,
        isLoading = isLoading,
        iconLoader = iconLoader
    )
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
            games = listOf(
                GameItemUi(
                    id = "1",
                    displayedName = "Sample Game",
                    iconPathFull = null
                )
            )
        )
    }
}
