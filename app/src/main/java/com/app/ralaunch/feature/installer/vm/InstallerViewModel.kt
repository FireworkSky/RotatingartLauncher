package com.app.ralaunch.feature.installer.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.di.contract.GameListStorage
import com.app.ralaunch.core.di.contract.GameRepositoryV2
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.installer.GameInstaller
import com.app.ralaunch.feature.installer.InstallCallback
import com.app.ralaunch.feature.installer.InstallPluginRegistry
import com.app.ralaunch.feature.installer.contract.InstallerFileType
import com.app.ralaunch.feature.installer.contract.InstallerUiEffect
import com.app.ralaunch.feature.installer.contract.InstallerUiEvent
import com.app.ralaunch.feature.installer.contract.InstallerUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstallerViewModel(
    private val appContext: Context,
    private val gameRepository: GameRepositoryV2,
    private val gameListStorage: GameListStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<InstallerUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<InstallerUiEffect> = _effects.asSharedFlow()

    private var activeInstaller: GameInstaller? = null

    fun onEvent(event: InstallerUiEvent) {
        when (event) {
            is InstallerUiEvent.PrefillFromDownload -> prefillFromDownload(
                gameFilePath = event.gameFilePath,
                modLoaderFilePath = event.modLoaderFilePath,
                detectedGameName = event.detectedGameName
            )

            is InstallerUiEvent.BrowseRequested -> browseFor(event.fileType)
            is InstallerUiEvent.FileSelected -> selectFile(
                fileType = event.fileType,
                path = event.path,
                preferredName = event.preferredName
            )

            InstallerUiEvent.StartImport -> startImport()
            InstallerUiEvent.DismissError -> clearError()
            InstallerUiEvent.ResetSelections -> resetSelections()
        }
    }

    private fun prefillFromDownload(
        gameFilePath: String?,
        modLoaderFilePath: String?,
        detectedGameName: String?
    ) {
        if (_uiState.value.isImporting) return

        _uiState.update {
            it.copy(
                gameFilePath = gameFilePath,
                detectedGameName = detectedGameName
                    ?: gameFilePath?.let(::fallbackDisplayName),
                modLoaderFilePath = modLoaderFilePath,
                detectedModLoaderName = modLoaderFilePath?.let(::fallbackDisplayName),
                progress = 0,
                status = "",
                errorMessage = null
            )
        }

        gameFilePath?.let { detectSelection(InstallerFileType.GAME, it, detectedGameName) }
        modLoaderFilePath?.let { detectSelection(InstallerFileType.MOD_LOADER, it, null) }
    }

    private fun browseFor(fileType: InstallerFileType) {
        if (_uiState.value.isImporting) return
        _effects.tryEmit(InstallerUiEffect.NavigateToFileBrowser(fileType))
    }

    private fun selectFile(
        fileType: InstallerFileType,
        path: String,
        preferredName: String?
    ) {
        if (_uiState.value.isImporting) return

        val fallbackName = preferredName ?: fallbackDisplayName(path)
        _uiState.update { state ->
            when (fileType) {
                InstallerFileType.GAME -> state.copy(
                    gameFilePath = path,
                    detectedGameName = fallbackName,
                    progress = 0,
                    status = "",
                    errorMessage = null
                )

                InstallerFileType.MOD_LOADER -> state.copy(
                    modLoaderFilePath = path,
                    detectedModLoaderName = fallbackName,
                    progress = 0,
                    status = "",
                    errorMessage = null
                )
            }
        }

        detectSelection(fileType, path, preferredName)
    }

    private fun detectSelection(
        fileType: InstallerFileType,
        path: String,
        preferredName: String?
    ) {
        val file = File(path)
        viewModelScope.launch(Dispatchers.IO) {
            val detectedName = when (fileType) {
                InstallerFileType.GAME -> InstallPluginRegistry.detectGame(file)
                    ?.second
                    ?.definition
                    ?.displayName

                InstallerFileType.MOD_LOADER -> InstallPluginRegistry.detectModLoader(file)
                    ?.second
                    ?.definition
                    ?.displayName
            } ?: preferredName ?: fallbackDisplayName(path)

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    when (fileType) {
                        InstallerFileType.GAME ->
                            if (state.gameFilePath == path) {
                                state.copy(detectedGameName = detectedName)
                            } else {
                                state
                            }

                        InstallerFileType.MOD_LOADER ->
                            if (state.modLoaderFilePath == path) {
                                state.copy(detectedModLoaderName = detectedName)
                            } else {
                                state
                            }
                    }
                }
            }
        }
    }

    private fun startImport() {
        val state = _uiState.value
        if (state.isImporting) return

        if (state.gameFilePath.isNullOrEmpty() && state.modLoaderFilePath.isNullOrEmpty()) {
            _uiState.update {
                it.copy(errorMessage = appContext.getString(R.string.import_select_game_first))
            }
            return
        }

        _uiState.update {
            it.copy(
                isImporting = true,
                progress = 0,
                status = appContext.getString(R.string.import_preparing_import),
                errorMessage = null
            )
        }

        val installer = GameInstaller(gameListStorage)
        activeInstaller = installer

        installer.install(
            gameFilePath = state.gameFilePath.orEmpty(),
            modLoaderFilePath = state.modLoaderFilePath,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    _uiState.update {
                        it.copy(
                            status = message,
                            progress = progress.coerceIn(0, 100)
                        )
                    }
                }

                override fun onComplete(gameItem: GameItem) {
                    activeInstaller = null
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            gameRepository.upsert(gameItem, 0)
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isImporting = false,
                                        progress = 100,
                                        status = appContext.getString(R.string.import_complete_exclamation),
                                        errorMessage = null
                                    )
                                }
                                _effects.tryEmit(
                                    InstallerUiEffect.ShowSuccess(
                                        appContext.getString(R.string.game_added_success)
                                    )
                                )
                                _effects.tryEmit(InstallerUiEffect.NavigateToGames)
                                resetSelections()
                            }
                        } catch (e: Exception) {
                            val message = e.message
                                ?: appContext.getString(R.string.import_error_game_import_failed)
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isImporting = false,
                                        errorMessage = message
                                    )
                                }
                                _effects.tryEmit(
                                    InstallerUiEffect.ShowToast(
                                        appContext.getString(
                                            R.string.import_failed_colon,
                                            e.message ?: appContext.getString(R.string.common_unknown_error)
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                override fun onError(error: String) {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = error
                        )
                    }
                    _effects.tryEmit(
                        InstallerUiEffect.ShowToast(
                            appContext.getString(R.string.import_failed_colon, error)
                        )
                    )
                }

                override fun onCancelled() {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = appContext.getString(R.string.import_cancelled)
                        )
                    }
                }
            }
        )
    }

    private fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun resetSelections() {
        _uiState.value = InstallerUiState()
    }

    private fun fallbackDisplayName(path: String): String {
        return File(path).nameWithoutExtension
    }

    override fun onCleared() {
        activeInstaller?.cancel()
        activeInstaller = null
        super.onCleared()
    }
}
