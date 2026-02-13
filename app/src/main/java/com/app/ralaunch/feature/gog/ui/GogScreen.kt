package com.app.ralaunch.feature.gog.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.model.ui.GogGameUi
import com.app.ralaunch.shared.core.model.ui.GogUiState
import com.app.ralaunch.feature.gog.ui.components.*

/**
 * GOG 客户端主屏幕
 * 未登录时直接内嵌 GOG 官方登录页面
 */
@Composable
fun GogScreen(
    uiState: GogUiState,
    onWebLogin: (authCode: String) -> Unit,
    onLogout: () -> Unit,
    onVisitGog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameClick: (GogGameUi) -> Unit,
    onLoginError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.isLoggedIn,
            transitionSpec = {
                fadeIn() + slideInHorizontally { it / 2 } togetherWith
                fadeOut() + slideOutHorizontally { -it / 2 }
            },
            label = "gog_content"
        ) { isLoggedIn ->
            if (isLoggedIn) {
                GogLoggedInContent(
                    uiState = uiState,
                    onLogout = onLogout,
                    onSearchQueryChange = onSearchQueryChange,
                    onGameClick = onGameClick
                )
            } else {
                // 直接内嵌 WebView 登录
                GogEmbeddedWebLogin(
                    onLoginSuccess = onWebLogin,
                    onLoginFailed = onLoginError
                )
            }
        }

        // 加载覆盖层
        AnimatedVisibility(
            visible = uiState.isLoading && uiState.isLoggedIn,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GogLoadingOverlay(message = uiState.loadingMessage)
        }
    }
}

/**
 * 已登录内容 - 双栏横屏布局
 */
@Composable
private fun GogLoggedInContent(
    uiState: GogUiState,
    onLogout: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameClick: (GogGameUi) -> Unit
) {
    // 当前选中的游戏
    var selectedGame by remember { mutableStateOf<GogGameUi?>(null) }

    // 初始选中第一个游戏
    LaunchedEffect(uiState.filteredGames) {
        if (selectedGame == null || uiState.filteredGames.none { it.id == selectedGame?.id }) {
            selectedGame = uiState.filteredGames.firstOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部用户信息栏
        GogUserHeader(
            username = uiState.username,
            email = uiState.email,
            avatarUrl = uiState.avatarUrl,
            gameCount = uiState.games.size,
            onLogout = onLogout,
            modifier = Modifier.padding(16.dp)
        )

        // 双栏布局
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 左侧：搜索栏 + 游戏网格
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                GogSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                GogGameGrid(
                    games = uiState.filteredGames,
                    selectedGame = selectedGame,
                    onGameClick = { game ->
                        selectedGame = game
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：游戏详情面板
            GogGameDetailPanel(
                game = selectedGame,
                onDownloadClick = { selectedGame?.let { onGameClick(it) } },
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .padding(bottom = 16.dp)
            )
        }
    }
}
