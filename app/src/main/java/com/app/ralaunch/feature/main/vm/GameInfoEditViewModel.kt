package com.app.ralaunch.feature.main.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.utils.RuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameInfoEditUiState(
    val installedDotNetRuntimeVersions: List<String> = emptyList(),
    val globalDotNetRuntimeVersion: String? = null
)

class GameInfoEditViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameInfoEditUiState())
    val uiState: StateFlow<GameInfoEditUiState> = _uiState.asStateFlow()

    init {
        loadRuntimeOptions()
    }

    private fun loadRuntimeOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val installedVersions = RuntimeManager.getInstalledVersions(
                RuntimeManager.RuntimeType.DOTNET
            )
            val selectedVersion = RuntimeManager.getSelectedRuntimeVersion(
                RuntimeManager.RuntimeType.DOTNET
            ) ?: installedVersions.firstOrNull()
            _uiState.update {
                it.copy(
                    installedDotNetRuntimeVersions = installedVersions,
                    globalDotNetRuntimeVersion = selectedVersion
                )
            }
        }
    }
}
