package com.app.ralaunch.feature.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.feature.main.AddGameUseCase
import com.app.ralaunch.feature.main.DeleteGameFilesUseCase
import com.app.ralaunch.feature.main.DeleteGameUseCase
import com.app.ralaunch.feature.main.LaunchGameUseCase
import com.app.ralaunch.feature.main.LoadGamesUseCase
import com.app.ralaunch.feature.main.UpdateGameUseCase
import com.app.ralaunch.core.common.GameDeletionManager
import com.app.ralaunch.core.common.GameLaunchManager
import com.app.ralaunch.core.platform.install.GameInstaller
import com.app.ralaunch.core.platform.install.InstallCallback
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.contract.repository.GameRepositoryV2
import com.app.ralaunch.shared.core.data.repository.GameListStorage
import com.app.ralaunch.shared.core.model.ui.applyFromUiModel
import com.app.ralaunch.shared.core.model.ui.toUiModels
import com.app.ralaunch.feature.main.contracts.ImportUiState
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
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
import org.koin.java.KoinJavaComponent

class MainViewModel(
    private val appContext: Context,
    private val loadGamesUseCase: LoadGamesUseCase,
    private val addGameUseCase: AddGameUseCase,
    private val updateGameUseCase: UpdateGameUseCase,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val launchGameUseCase: LaunchGameUseCase,
    private val deleteGameFilesUseCase: DeleteGameFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MainUiEffect>()
    val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()

    private val _importUiState = MutableStateFlow(ImportUiState())
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    private val gameItemsMap = mutableMapOf<String, GameItem>()
    private var activeInstaller: GameInstaller? = null

    init {
        onEvent(MainUiEvent.RefreshRequested)
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RefreshRequested -> refreshGames()
            is MainUiEvent.GameSelected -> selectGame(event.game.id)
            is MainUiEvent.GameEdited -> updateGame(event.game)
            is MainUiEvent.LaunchRequested -> launchSelectedGame()
            is MainUiEvent.DeleteRequested -> requestDeleteSelectedGame()
            is MainUiEvent.DeleteDialogDismissed -> dismissDeleteDialog()
            is MainUiEvent.DeleteConfirmed -> confirmDelete()
            is MainUiEvent.ImportCompleted -> onGameImportComplete(event.game)
            is MainUiEvent.AppResumed -> _uiState.update { it.copy(isVideoPlaying = true) }
            is MainUiEvent.AppPaused -> _uiState.update { it.copy(isVideoPlaying = false) }
        }
    }

    fun startImport(gameFilePath: String?, modLoaderFilePath: String?) {
        if (_importUiState.value.isImporting) return

        if (gameFilePath.isNullOrEmpty() && modLoaderFilePath.isNullOrEmpty()) {
            _importUiState.update {
                it.copy(errorMessage = "请先选择游戏文件")
            }
            return
        }

        val storage: GameListStorage = try {
            KoinJavaComponent.get(GameListStorage::class.java)
        } catch (_: Exception) {
            _importUiState.update {
                it.copy(
                    isImporting = false,
                    errorMessage = "无法初始化游戏存储"
                )
            }
            return
        }

        _importUiState.update {
            it.copy(
                isImporting = true,
                progress = 0,
                status = "准备中...",
                errorMessage = null,
                lastCompletedGameId = null
            )
        }

        val installer = GameInstaller(storage)
        activeInstaller = installer

        installer.install(
            gameFilePath = gameFilePath ?: "",
            modLoaderFilePath = modLoaderFilePath,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    _importUiState.update {
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
                            addGameUseCase(gameItem, 0)
                            refreshGames(selectedId = null)
                            _importUiState.update {
                                it.copy(
                                    isImporting = false,
                                    progress = 100,
                                    status = "导入完成！",
                                    errorMessage = null,
                                    lastCompletedGameId = gameItem.id
                                )
                            }
                            emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.game_added_success)))
                        } catch (e: Exception) {
                            _importUiState.update {
                                it.copy(
                                    isImporting = false,
                                    errorMessage = e.message ?: "导入失败"
                                )
                            }
                            emitEffect(MainUiEffect.ShowToast("导入失败: ${e.message ?: "未知错误"}"))
                        }
                    }
                }

                override fun onError(error: String) {
                    activeInstaller = null
                    _importUiState.update {
                        it.copy(
                            isImporting = false,
                            lastCompletedGameId = null,
                            errorMessage = error
                        )
                    }
                    emitEffect(MainUiEffect.ShowToast("导入失败: $error"))
                }

                override fun onCancelled() {
                    activeInstaller = null
                    _importUiState.update {
                        it.copy(
                            isImporting = false,
                            lastCompletedGameId = null,
                            errorMessage = "导入已取消"
                        )
                    }
                }
            }
        )
    }

    fun clearImportError() {
        _importUiState.update {
            it.copy(errorMessage = null)
        }
    }

    fun resetImportCompletedFlag() {
        _importUiState.update {
            it.copy(lastCompletedGameId = null)
        }
    }

    private fun refreshGames(selectedId: String? = _uiState.value.selectedGame?.id) {
        viewModelScope.launch(Dispatchers.IO) {
            val games = loadGamesUseCase().distinctBy { it.id }
            gameItemsMap.clear()
            games.forEach { game ->
                gameItemsMap[game.id] = game
            }
            val uiGames = games.toUiModels()
            val selectedGame = selectedId?.let { id -> uiGames.find { it.id == id } }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        games = uiGames,
                        selectedGame = selectedGame,
                        gamePendingDeletion = null,
                        deletePosition = -1,
                        isDeletingGame = false,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun selectGame(gameId: String) {
        val selected = _uiState.value.games.find { it.id == gameId } ?: return
        _uiState.update { it.copy(selectedGame = selected) }
    }

    private fun updateGame(updatedGameUi: com.app.ralaunch.shared.core.model.ui.GameItemUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = gameItemsMap[updatedGameUi.id] ?: return@launch
            game.applyFromUiModel(updatedGameUi)
            updateGameUseCase(game)
            refreshGames(selectedId = updatedGameUi.id)
        }
    }

    private fun requestDeleteSelectedGame() {
        if (_uiState.value.isDeletingGame) return
        val selectedGame = _uiState.value.selectedGame
        if (selectedGame == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }
        val deletePosition = _uiState.value.games.indexOfFirst { it.id == selectedGame.id }
        _uiState.update {
            it.copy(
                gamePendingDeletion = selectedGame,
                deletePosition = deletePosition
            )
        }
    }

    private fun dismissDeleteDialog() {
        if (_uiState.value.isDeletingGame) return
        _uiState.update {
            it.copy(
                gamePendingDeletion = null,
                deletePosition = -1,
                isDeletingGame = false
            )
        }
    }

    private fun confirmDelete() {
        if (_uiState.value.isDeletingGame) return
        val pendingGame = _uiState.value.gamePendingDeletion ?: return
        _uiState.update { it.copy(isDeletingGame = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val game = gameItemsMap[pendingGame.id]
                if (game == null) {
                    emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.error_operation_failed)))
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                gamePendingDeletion = null,
                                deletePosition = -1
                            )
                        }
                    }
                    return@launch
                }

                val filesDeleted = deleteGameFilesUseCase(game)
                deleteGameUseCase(game.id)
                refreshGames(selectedId = null)

                if (filesDeleted) {
                    emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.main_game_deleted)))
                } else {
                    emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_game_deleted_partial)))
                }
            } catch (_: Exception) {
                emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.error_operation_failed)))
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDeletingGame = false) }
                }
            }
        }
    }

    private fun launchSelectedGame() {
        val selectedGame = _uiState.value.selectedGame
        if (selectedGame == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }

        val game = gameItemsMap[selectedGame.id]
        if (game == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val success = launchGameUseCase(game)
            if (!success) {
                emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.game_launch_failed)))
                return@launch
            }
            if (SettingsAccess.isKillLauncherUIAfterLaunch) {
                emitEffect(MainUiEffect.ExitLauncher)
            }
        }
    }

    private fun onGameImportComplete(game: GameItem) {
        viewModelScope.launch(Dispatchers.IO) {
            addGameUseCase(game, 0)
            refreshGames(selectedId = null)
            emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.game_added_success)))
        }
    }

    private fun emitEffect(effect: MainUiEffect) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    override fun onCleared() {
        activeInstaller?.cancel()
        activeInstaller = null
        super.onCleared()
    }
}

class MainViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val gameRepository: GameRepositoryV2 = KoinJavaComponent.get(GameRepositoryV2::class.java)
        val loadGamesUseCase = LoadGamesUseCase(gameRepository)
        val addGameUseCase = AddGameUseCase(gameRepository)
        val updateGameUseCase = UpdateGameUseCase(gameRepository)
        val deleteGameUseCase = DeleteGameUseCase(gameRepository)
        val launchGameUseCase = LaunchGameUseCase(GameLaunchManager(appContext))
        val deleteGameFilesUseCase = DeleteGameFilesUseCase(GameDeletionManager(appContext))

        return MainViewModel(
            appContext = appContext.applicationContext,
            loadGamesUseCase = loadGamesUseCase,
            addGameUseCase = addGameUseCase,
            updateGameUseCase = updateGameUseCase,
            deleteGameUseCase = deleteGameUseCase,
            launchGameUseCase = launchGameUseCase,
            deleteGameFilesUseCase = deleteGameFilesUseCase
        ) as T
    }
}
