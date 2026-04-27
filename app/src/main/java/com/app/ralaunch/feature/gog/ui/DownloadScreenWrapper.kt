package com.app.ralaunch.feature.gog.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.feature.gog.ui.components.GogDownloadDialog
import com.app.ralaunch.feature.gog.vm.GogUiEffect
import com.app.ralaunch.feature.gog.vm.GogViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadScreenWrapper(
    onBack: () -> Unit,
    onNavigateToImport: (gamePath: String?, modLoaderPath: String?, gameName: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: GogViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadIfLoggedIn()
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GogUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is GogUiEffect.OpenUrl -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                }
                is GogUiEffect.NavigateToImport -> {
                    onNavigateToImport(effect.gamePath, effect.modLoaderPath, effect.gameName)
                }
            }
        }
    }

    GogScreen(
        uiState = uiState.gogUiState,
        onWebLogin = viewModel::onWebLogin,
        onLogout = viewModel::onLogout,
        onVisitGog = viewModel::onVisitGog,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onGameClick = viewModel::onGameClick,
        onLoginError = viewModel::showError,
        onDismissError = viewModel::dismissError,
        modifier = modifier
    )

    val currentGame = uiState.currentGame
    if (uiState.showDownloadDialog && currentGame != null) {
        GogDownloadDialog(
            gameName = currentGame.title,
            gameFiles = uiState.gameFiles,
            modLoaderRule = uiState.currentModLoaderRule,
            selectedGameFile = uiState.selectedGameFile,
            selectedModLoaderVersion = uiState.selectedModLoaderVersion,
            downloadStatus = uiState.downloadStatus,
            onSelectGameVersion = viewModel::onSelectGameVersion,
            onSelectModLoaderVersion = viewModel::onSelectModLoaderVersion,
            onStartDownload = viewModel::onStartDownload,
            onInstall = viewModel::onInstall,
            onDismiss = viewModel::dismissDownloadDialog
        )
    }
}
