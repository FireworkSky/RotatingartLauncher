package com.app.ralaunch.feature.main.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.platform.runtime.RuntimeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameInfoEditUiState(
    val installedDotNetRuntimeVersions: List<String> = emptyList(),
    val globalDotNetRuntimeVersion: String? = null,
    val installedMonoRuntimeVersions: List<String> = emptyList(),
    val globalRuntimeEngine: String = RuntimeSpec.ENGINE_DOTNET,
    val globalMonoRuntimeVersion: String? = null
)

class GameInfoEditViewModel(
    private val runtimeManager: IRuntimeManagerServiceV2,
    private val settingsRepository: ISettingsRepositoryServiceV2
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
            val installedMonoVersions = runtimeManager.getInstalledVersions(
                IRuntimeManagerServiceV2.RuntimeType.MONO
            )
            val settings = settingsRepository.getSettingsSnapshot()
            _uiState.update {
                it.copy(
                    installedDotNetRuntimeVersions = installedVersions,
                    globalDotNetRuntimeVersion = selectedVersion,
                    installedMonoRuntimeVersions = installedMonoVersions,
                    globalRuntimeEngine = settings.selectedRuntimeEngine
                        .trim()
                        .ifBlank { RuntimeSpec.ENGINE_DOTNET },
                    globalMonoRuntimeVersion = settings.selectedMonoRuntimeVersion
                        .trim()
                        .ifBlank { null }
                        ?: installedMonoVersions.firstOrNull()
                )
            }
        }
    }
}
