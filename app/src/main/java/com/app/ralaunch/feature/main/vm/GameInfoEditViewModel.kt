package com.app.ralaunch.feature.main.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
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

class GameInfoEditViewModel(
    private val runtimeManager: IRuntimeManagerServiceV2
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameInfoEditUiState())
    val uiState: StateFlow<GameInfoEditUiState> = _uiState.asStateFlow()

    init {
        loadRuntimeOptions()
    }

    private fun loadRuntimeOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val installedVersions = runtimeManager.getInstalledVersions(
                IRuntimeManagerServiceV2.RuntimeType.DOTNET
            )
            val selectedVersion = runtimeManager.getSelectedRuntimeVersion(
                IRuntimeManagerServiceV2.RuntimeType.DOTNET
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
