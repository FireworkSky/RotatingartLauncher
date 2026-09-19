package com.app.ralaunch.feature.patch.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.common.util.StreamUtils
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import com.app.ralaunch.utils.GameManager
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

data class PatchManagementUiState(
    val games: List<GameItem> = emptyList(),
    val selectedGame: GameItem? = null,
    val selectedGameIndex: Int = -1,
    val patches: List<Patch> = emptyList()
)

class PatchManagementViewModel(
    private val appContext: Context,
    private val patchManager: PatchManager?
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
            patchManager?.getApplicablePatches(game.gameId) ?: emptyList()
        } ?: emptyList()
        _uiState.update { it.copy(patches = patches) }
    }

    fun isPatchEnabled(patchId: String): Boolean {
        val selectedGame = _uiState.value.selectedGame ?: return false
        val gameAsmPath = selectedGame.gameExePathFull?.let { Paths.get(it) } ?: return false
        return patchManager?.isPatchEnabled(gameAsmPath, patchId) ?: false
    }

    fun setPatchEnabled(patchId: String, enabled: Boolean) {
        val selectedGame = _uiState.value.selectedGame ?: return
        val gameAsmPath = selectedGame.gameExePathFull?.let { Paths.get(it) } ?: return
        patchManager?.setPatchEnabled(gameAsmPath, patchId, enabled)
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
                    val result = patchManager?.installPatch(tempPatchPath) ?: false
                    if (result) {
                        refreshPatches()
                    }
                    result
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
