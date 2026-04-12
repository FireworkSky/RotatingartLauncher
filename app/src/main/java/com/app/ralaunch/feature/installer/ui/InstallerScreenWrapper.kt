package com.app.ralaunch.feature.installer.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.core.common.MessageHelper
import com.app.ralaunch.core.navigation.NavState
import com.app.ralaunch.core.navigation.Screen
import com.app.ralaunch.core.navigation.navigateToGames
import com.app.ralaunch.feature.installer.contract.InstallerFileType
import com.app.ralaunch.feature.installer.contract.InstallerUiEffect
import com.app.ralaunch.feature.installer.contract.InstallerUiEvent
import com.app.ralaunch.feature.installer.vm.InstallerViewModel
import org.koin.compose.viewmodel.koinViewModel

data class InstallerRouteActions(
    val prefillFromDownload: (String?, String?, String?) -> Unit,
    val onFileSelected: (String?, String) -> Unit
)

@Composable
fun rememberInstallerRouteActions(): InstallerRouteActions {
    val viewModel: InstallerViewModel = koinViewModel()

    return remember(viewModel) {
        InstallerRouteActions(
            prefillFromDownload = { gamePath, modLoaderPath, gameName ->
                viewModel.onEvent(
                    InstallerUiEvent.PrefillFromDownload(
                        gameFilePath = gamePath,
                        modLoaderFilePath = modLoaderPath,
                        detectedGameName = gameName
                    )
                )
            },
            onFileSelected = { fileType, path ->
                val installerFileType = InstallerFileType.fromRouteValue(fileType)
                if (installerFileType != null) {
                    viewModel.onEvent(
                        InstallerUiEvent.FileSelected(
                            fileType = installerFileType,
                            path = path
                        )
                    )
                }
            }
        )
    }
}

@Composable
fun InstallerScreenWrapper(
    navState: NavState
) {
    val viewModel: InstallerViewModel = koinViewModel()
    val activity = LocalContext.current as? Activity
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is InstallerUiEffect.NavigateToFileBrowser -> {
                    navState.navigateTo(
                        Screen.FileBrowser(
                            initialPath = "",
                            allowedExtensions = effect.fileType.allowedExtensions,
                            fileType = effect.fileType.routeValue
                        )
                    )
                }

                InstallerUiEffect.NavigateToGames -> navState.navigateToGames()

                is InstallerUiEffect.ShowToast -> {
                    MessageHelper.showToast(
                        activity = activity,
                        message = effect.message
                    )
                }

                is InstallerUiEffect.ShowSuccess -> {
                    MessageHelper.showSuccess(
                        activity = activity,
                        message = effect.message
                    )
                }
            }
        }
    }

    ImportScreenWrapper(
        gameFilePath = uiState.value.gameFilePath,
        detectedGameId = uiState.value.detectedGameName,
        modLoaderFilePath = uiState.value.modLoaderFilePath,
        detectedModLoaderId = uiState.value.detectedModLoaderName,
        importUiState = uiState.value,
        onBack = {
            viewModel.onEvent(InstallerUiEvent.ResetSelections)
            navState.navigateToGames()
        },
        onStartImport = {
            viewModel.onEvent(InstallerUiEvent.StartImport)
        },
        onSelectGameFile = {
            viewModel.onEvent(InstallerUiEvent.BrowseRequested(InstallerFileType.GAME))
        },
        onSelectModLoader = {
            viewModel.onEvent(InstallerUiEvent.BrowseRequested(InstallerFileType.MOD_LOADER))
        },
        onDismissError = {
            viewModel.onEvent(InstallerUiEvent.DismissError)
        }
    )
}
