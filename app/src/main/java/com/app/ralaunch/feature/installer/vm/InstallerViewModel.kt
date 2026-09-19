package com.app.ralaunch.feature.installer.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.feature.installer.GameInstaller
import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
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
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<InstallerUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<InstallerUiEffect> = _effects.asSharedFlow()

    private var activeInstaller: GameInstaller? = null

    fun onEvent(event: InstallerUiEvent) {
        when (event) {
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

        val installer = GameInstaller()
        activeInstaller = installer

        // 进度驱动 UI；终态（Complete/Error/Cancelled）由 install 返回的 Result 统一处理
        val onEvent: (Event) -> Unit = { event ->
            when (event) {
                is Event.Progress -> _uiState.update {
                    it.copy(
                        status = event.message,
                        progress = event.progress.coerceIn(0, 100)
                    )
                }

                is Event.Complete, is Event.Error, is Event.Cancelled -> Unit
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            when (
                val result = installer.install(
                    gameFilePath = state.gameFilePath.orEmpty(),
                    modLoaderFilePath = state.modLoaderFilePath,
                    callback = onEvent
                )
            ) {
                is Result.Success -> {
                    activeInstaller = null
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

                is Result.Failure -> {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = result.message
                        )
                    }
                    _effects.tryEmit(
                        InstallerUiEffect.ShowToast(
                            appContext.getString(R.string.import_failed_colon, result.message)
                        )
                    )
                }

                is Result.Cancelled -> {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
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
