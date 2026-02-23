package com.app.ralaunch.feature.main

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
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
import com.app.ralaunch.feature.main.contracts.AppUpdateUiModel
import com.app.ralaunch.feature.main.contracts.ImportUiState
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
import com.app.ralaunch.feature.main.update.LauncherUpdateChecker
import com.app.ralaunch.feature.main.update.LauncherUpdateInfo
import com.app.ralaunch.feature.main.update.LauncherUpdatePreferences
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
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
    private val deleteGameFilesUseCase: DeleteGameFilesUseCase,
    private val settingsRepository: SettingsRepositoryV2,
    private val announcementRepositoryService: AnnouncementRepositoryService,
    private val launcherUpdateChecker: LauncherUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MainUiEffect>()
    val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()

    private val _importUiState = MutableStateFlow(ImportUiState())
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    private val gameItemsMap = mutableMapOf<String, GameItem>()
    private var activeInstaller: GameInstaller? = null
    private var isUpdateCheckInProgress = false
    private var lastUpdateCheckAt: Long = 0L
    private var latestAnnouncementId: String? = null

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 60_000L
    }

    init {
        onEvent(MainUiEvent.RefreshRequested)
        onEvent(MainUiEvent.CheckAppUpdate)
        loadAnnouncementBadgeFromSettings()
        checkAnnouncementUnreadOnStartup()
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RefreshRequested -> refreshGames()
            is MainUiEvent.CheckAppUpdate -> checkAppUpdate()
            is MainUiEvent.CheckAppUpdateManually -> checkAppUpdate(
                force = true,
                bypassIgnoredVersion = true,
                fromUserAction = true
            )
            is MainUiEvent.GameSelected -> selectGame(event.game.id)
            is MainUiEvent.GameEdited -> updateGame(event.game)
            is MainUiEvent.LaunchRequested -> launchSelectedGame()
            is MainUiEvent.DeleteRequested -> requestDeleteSelectedGame()
            is MainUiEvent.DeleteDialogDismissed -> dismissDeleteDialog()
            is MainUiEvent.DeleteConfirmed -> confirmDelete()
            is MainUiEvent.UpdateDialogDismissed -> dismissUpdateDialog()
            is MainUiEvent.UpdateIgnoreClicked -> ignoreCurrentUpdate()
            is MainUiEvent.UpdateActionClicked -> openUpdateUrl()
            is MainUiEvent.ImportCompleted -> onGameImportComplete(event.game)
            is MainUiEvent.AppResumed -> {
                _uiState.update { it.copy(isVideoPlaying = true) }
                checkAppUpdate(force = false)
            }
            is MainUiEvent.AppPaused -> _uiState.update { it.copy(isVideoPlaying = false) }
            is MainUiEvent.AnnouncementTabOpened -> markAnnouncementsAsRead()
        }
    }

    private fun checkAnnouncementUnreadOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = announcementRepositoryService.fetchAnnouncements(forceRefresh = false)
            result.onSuccess { announcements ->
                val latestId = announcements.firstOrNull()
                    ?.id
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                latestAnnouncementId = latestId

                val settings = settingsRepository.getSettingsSnapshot()
                val lastAnnouncementId = settings.lastAnnouncementId.trim()
                val shouldShowBadge = latestId != null && latestId != lastAnnouncementId
                if (settings.isAnnouncementBadgeShown != shouldShowBadge) {
                    runCatching {
                        settingsRepository.update {
                            isAnnouncementBadgeShown = shouldShowBadge
                        }
                    }.onFailure { error ->
                        AppLogger.warn(
                            "MainViewModel",
                            "Failed to persist isAnnouncementBadgeShown: ${error.message}",
                            error
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(showAnnouncementBadge = shouldShowBadge) }
                }
            }.onFailure { error ->
                AppLogger.warn(
                    "MainViewModel",
                    "Failed to fetch announcements on startup: ${error.message}",
                    error
                )
            }
        }
    }

    private fun loadAnnouncementBadgeFromSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val shouldShowBadge = runCatching {
                settingsRepository.getSettingsSnapshot().isAnnouncementBadgeShown
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(showAnnouncementBadge = shouldShowBadge) }
            }
        }
    }

    private fun markAnnouncementsAsRead() {
        if (!_uiState.value.showAnnouncementBadge) return
        val latestId = latestAnnouncementId
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                settingsRepository.update {
                    isAnnouncementBadgeShown = false
                    if (latestId != null) {
                        lastAnnouncementId = latestId
                    }
                }
            }.onFailure { error ->
                AppLogger.warn(
                    "MainViewModel",
                    "Failed to persist announcement read state: ${error.message}",
                    error
                )
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(showAnnouncementBadge = false) }
            }
        }
    }

    fun startImport(gameFilePath: String?, modLoaderFilePath: String?) {
        if (_importUiState.value.isImporting) return

        if (gameFilePath.isNullOrEmpty() && modLoaderFilePath.isNullOrEmpty()) {
            _importUiState.update {
                it.copy(errorMessage = appContext.getString(R.string.import_select_game_first))
            }
            return
        }

        val storage: GameListStorage = try {
            KoinJavaComponent.get(GameListStorage::class.java)
        } catch (_: Exception) {
            _importUiState.update {
                it.copy(
                    isImporting = false,
                    errorMessage = appContext.getString(R.string.import_storage_init_failed)
                )
            }
            return
        }

        _importUiState.update {
            it.copy(
                isImporting = true,
                progress = 0,
                status = appContext.getString(R.string.import_preparing_import),
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
                                    status = appContext.getString(R.string.import_complete_exclamation),
                                    errorMessage = null,
                                    lastCompletedGameId = gameItem.id
                                )
                            }
                            emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.game_added_success)))
                        } catch (e: Exception) {
                            _importUiState.update {
                                it.copy(
                                    isImporting = false,
                                    errorMessage = e.message ?: appContext.getString(R.string.import_error_game_import_failed)
                                )
                            }
                            emitEffect(
                                MainUiEffect.ShowToast(
                                    appContext.getString(
                                        R.string.import_failed_colon,
                                        e.message ?: appContext.getString(R.string.common_unknown_error)
                                    )
                                )
                            )
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
                    emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.import_failed_colon, error)))
                }

                override fun onCancelled() {
                    activeInstaller = null
                    _importUiState.update {
                        it.copy(
                            isImporting = false,
                            lastCompletedGameId = null,
                            errorMessage = appContext.getString(R.string.import_cancelled)
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

    private fun checkAppUpdate(
        force: Boolean = true,
        bypassIgnoredVersion: Boolean = false,
        fromUserAction: Boolean = false
    ) {
        if (isUpdateCheckInProgress) return
        if (_uiState.value.availableUpdate != null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return

        if (fromUserAction) {
            emitEffect(MainUiEffect.ShowToast("正在检查更新..."))
        }
        lastUpdateCheckAt = now
        isUpdateCheckInProgress = true
        viewModelScope.launch {
            try {
                val currentVersion = resolveCurrentVersionName()
                val result = launcherUpdateChecker.checkForUpdate(currentVersion)

                result.onSuccess { info ->
                    if (info == null) {
                        AppLogger.info("MainViewModel", "No update. currentVersion=$currentVersion")
                        if (fromUserAction) {
                            emitEffect(MainUiEffect.ShowToast("当前已是最新版本"))
                        }
                        return@onSuccess
                    }
                    if (!bypassIgnoredVersion) {
                        val ignoredVersion = LauncherUpdatePreferences.getIgnoredUpdateVersion(appContext)
                        if (ignoredVersion == info.latestVersion.trim()) {
                            AppLogger.info("MainViewModel", "Ignored update version=${info.latestVersion}")
                            return@onSuccess
                        }
                    }
                    _uiState.update { state ->
                        state.copy(availableUpdate = info.toAppUpdateUiModel())
                    }
                    AppLogger.info(
                        "MainViewModel",
                        "Update available current=${info.currentVersion}, latest=${info.latestVersion}"
                    )
                }.onFailure { error ->
                    AppLogger.warn("MainViewModel", "Check update failed: ${error.message}", error)
                }
            } finally {
                isUpdateCheckInProgress = false
            }
        }
    }

    private fun dismissUpdateDialog() {
        _uiState.update { it.copy(availableUpdate = null) }
    }

    private fun openUpdateUrl() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        LauncherUpdatePreferences.clearIgnoredUpdateVersion(appContext)
        _uiState.update { it.copy(availableUpdate = null) }
        if (updateInfo.downloadUrl.isNotBlank()) {
            emitEffect(
                MainUiEffect.DownloadLauncherUpdate(
                    downloadUrl = updateInfo.downloadUrl,
                    latestVersion = updateInfo.latestVersion,
                    releaseUrl = updateInfo.releaseUrl
                )
            )
        } else {
            emitEffect(MainUiEffect.OpenUrl(updateInfo.releaseUrl))
        }
    }

    private fun ignoreCurrentUpdate() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        LauncherUpdatePreferences.setIgnoredUpdateVersion(appContext, updateInfo.latestVersion)
        _uiState.update { it.copy(availableUpdate = null) }
    }

    private fun LauncherUpdateInfo.toAppUpdateUiModel(): AppUpdateUiModel {
        return AppUpdateUiModel(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            releaseUrl = releaseUrl
        )
    }

    private fun resolveCurrentVersionName(): String {
        return runCatching {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }

            packageInfo.versionName
                ?.trim()
                ?.ifBlank { "0.0.0" }
                ?: "0.0.0"
        }.getOrDefault("0.0.0")
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
        val settingsRepository: SettingsRepositoryV2 = KoinJavaComponent.get(SettingsRepositoryV2::class.java)
        val announcementRepositoryService: AnnouncementRepositoryService = KoinJavaComponent.get(AnnouncementRepositoryService::class.java)
        val launcherUpdateChecker: LauncherUpdateChecker = KoinJavaComponent.get(LauncherUpdateChecker::class.java)

        return MainViewModel(
            appContext = appContext.applicationContext,
            loadGamesUseCase = loadGamesUseCase,
            addGameUseCase = addGameUseCase,
            updateGameUseCase = updateGameUseCase,
            deleteGameUseCase = deleteGameUseCase,
            launchGameUseCase = launchGameUseCase,
            deleteGameFilesUseCase = deleteGameFilesUseCase,
            settingsRepository = settingsRepository,
            announcementRepositoryService = announcementRepositoryService,
            launcherUpdateChecker = launcherUpdateChecker
        ) as T
    }
}
