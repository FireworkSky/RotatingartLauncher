package com.app.ralaunch.feature.patch.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.common.util.StreamUtils
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import com.app.ralaunch.utils.GameManager
import com.app.ralaunch.utils.ModuleManager
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.utils.modules.Patch
import com.app.ralaunch.utils.modules.PatchModuleHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

data class PatchManagementUiState(
    val games: List<GameItem> = emptyList(),
    val selectedGame: GameItem? = null,
    val selectedGameIndex: Int = -1,
    val patches: List<Patch> = emptyList()
)

class PatchManagementViewModel(
    private val appContext: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(PatchManagementUiState())
    val uiState: StateFlow<PatchManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            GameManager.games.collectLatest { games ->
                val currentSelected = _uiState.value.selectedGame
                val selectedGame = games.find { it.id == currentSelected?.id }
                _uiState.update {
                    it.copy(
                        games = games,
                        selectedGame = selectedGame,
                        selectedGameIndex = games.indexOfFirst { game -> game.id == selectedGame?.id }
                    )
                }
                refreshPatches()
            }
        }
    }

    fun selectGame(game: GameItem, index: Int) {
        _uiState.update {
            it.copy(
                selectedGame = game,
                selectedGameIndex = index
            )
        }
        refreshPatches()
    }

    fun refreshPatches() {
        val selectedGame = _uiState.value.selectedGame
        val patches = selectedGame?.let { game ->
            PatchModuleHandler.getApplicablePatches(game.gameId)
        } ?: emptyList()
        _uiState.update { it.copy(patches = patches) }
    }

    fun isPatchEnabled(patchId: String): Boolean {
        return _uiState.value.selectedGame?.enabledModules?.contains(patchId) == true
    }

    fun setPatchEnabled(patchId: String, enabled: Boolean) {
        val selectedGame = _uiState.value.selectedGame ?: return
        viewModelScope.launch {
            GameManager.configure(selectedGame.id) {
                enabledModules = if (enabled) enabledModules + patchId else enabledModules - patchId
            }
        }
    }

    suspend fun importPatch(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                TemporaryFileAcquirer().use { tfa ->
                    val tempPatchPath = tfa.acquireTempFilePath("imported_patch.zip")
                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        Files.newOutputStream(tempPatchPath).use { outputStream ->
                            StreamUtils.transferTo(inputStream, outputStream)
                        }
                    }
                    // 用户显式导入：总是重装
                    ModuleManager.install(tempPatchPath, force = true)
                    refreshPatches()
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
