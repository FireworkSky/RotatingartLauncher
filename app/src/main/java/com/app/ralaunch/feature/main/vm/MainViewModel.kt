package com.app.ralaunch.feature.main.vm

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.common.GameLaunchManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.R
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.navigation.NavDestination
import com.app.ralaunch.core.navigation.NavigationEvent
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.model.applyFromUiModel
import com.app.ralaunch.core.model.toUiModels
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
import com.app.ralaunch.feature.main.contracts.AppUpdateUiModel
import com.app.ralaunch.feature.main.contracts.ForceAnnouncementUiModel
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
import com.app.ralaunch.feature.main.update.LauncherUpdateChecker
import com.app.ralaunch.feature.main.update.LauncherUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val appContext: Context,
    private val gameRepository: IGameRepositoryServiceV3,
    private val gameLaunchManager: GameLaunchManager,
    private val settingsRepository: ISettingsRepositoryServiceV2,
    private val announcementRepositoryService: AnnouncementRepositoryService,
    private val launcherUpdateChecker: LauncherUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MainUiEffect>()
    val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()

    private val gameItemsMap = mutableMapOf<String, GameItem>()
    private var isUpdateCheckInProgress = false
    private var lastUpdateCheckAt: Long = 0L
    private var latestAnnouncementId: String? = null

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 60_000L
    }

    init {
        observeGames()
        onEvent(MainUiEvent.CheckAppUpdate)
        loadAnnouncementBadgeFromSettings()
        checkAnnouncementUnreadOnStartup()
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.CheckAppUpdate -> checkAppUpdate()
            is MainUiEvent.CheckAppUpdateManually -> checkAppUpdate(
                force = true,
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
            is MainUiEvent.UpdateCloudActionClicked -> openCloudUpdateUrl()
            is MainUiEvent.AppResumed -> {
                _uiState.update { it.copy(isVideoPlaying = true) }
                checkAppUpdate(force = false)
            }
            is MainUiEvent.AppPaused -> _uiState.update { it.copy(isVideoPlaying = false) }
            is MainUiEvent.AnnouncementTabOpened -> markAnnouncementsAsRead()
            is MainUiEvent.AnnouncementPopupLearnMoreClicked -> markAnnouncementsAsRead()
            is MainUiEvent.AnnouncementPopupViewClicked -> openAnnouncementScreen()
        }
    }

    private fun observeGames() {
        viewModelScope.launch {
            gameRepository.games.collectLatest { games ->
                val distinctGames = games.distinctBy { it.id }
                gameItemsMap.clear()
                distinctGames.forEach { game ->
                    gameItemsMap[game.id] = game
                }

                val uiGames = distinctGames.toUiModels()
                _uiState.update { state ->
                    val selectedGameId = state.selectedGame?.id
                    val pendingDeletionId = state.gamePendingDeletion?.id
                    state.copy(
                        games = uiGames,
                        selectedGame = selectedGameId?.let { id ->
                            uiGames.find { it.id == id }
                        },
                        gamePendingDeletion = pendingDeletionId?.let { id ->
                            uiGames.find { it.id == id }
                        },
                        deletePosition = pendingDeletionId?.let { id ->
                            uiGames.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: -1
                        } ?: -1,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun checkAnnouncementUnreadOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = announcementRepositoryService.fetchAnnouncements(forceRefresh = false)
            result.onSuccess { announcements ->
                val latestAnnouncement = announcements.firstOrNull()
                val latestId = latestAnnouncement
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
                        AppLog.w(
                            "MainViewModel",
                            "Failed to persist isAnnouncementBadgeShown: ${error.message}",
                            error
                        )
                    }
                }

                val forceAnnouncement = latestAnnouncement
                    ?.takeIf { shouldShowBadge }
                    ?.let { latest ->
                        val announcementId = latest.id.trim()
                        if (announcementId.isBlank()) {
                            null
                        } else {
                            val markdown = announcementRepositoryService.fetchAnnouncementMarkdown(
                                announcementId = announcementId,
                                forceRefresh = false
                            ).getOrNull()

                            ForceAnnouncementUiModel(
                                announcementId = announcementId,
                                title = latest.title,
                                publishedAt = latest.publishedAt,
                                tags = latest.tags,
                                markdown = markdown
                            )
                        }
                    }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            showAnnouncementBadge = shouldShowBadge,
                            forceAnnouncement = forceAnnouncement
                        )
                    }
                }
            }.onFailure { error ->
                AppLog.w(
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
        val state = _uiState.value
        if (!state.showAnnouncementBadge && state.forceAnnouncement == null) return

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
                AppLog.w(
                    "MainViewModel",
                    "Failed to persist announcement read state: ${error.message}",
                    error
                )
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        showAnnouncementBadge = false,
                        forceAnnouncement = null
                    )
                }
            }
        }
    }

    private fun selectGame(gameId: String) {
        val selected = _uiState.value.games.find { it.id == gameId } ?: return
        _uiState.update { it.copy(selectedGame = selected) }
    }

    private fun updateGame(updatedGameUi: com.app.ralaunch.core.model.GameItemUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = gameItemsMap[updatedGameUi.id] ?: return@launch
            game.applyFromUiModel(updatedGameUi)
            val index = gameRepository.games.value.indexOfFirst { it.id == game.id }
            if (index >= 0) {
                gameRepository.upsert(game, index)
            }
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

                val filesDeleted = gameRepository.deleteGameFiles(game)
                gameRepository.removeById(game.id)

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

        viewModelScope.launch {
            val success = withContext(Dispatchers.Main) {
                gameLaunchManager.launchGame(game)
            }
            if (!success) {
                emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.game_launch_failed)))
                return@launch
            }
            if (SettingsAccess.isKillLauncherUIAfterLaunch) {
                emitEffect(MainUiEffect.ExitLauncher)
            }
        }
    }

    private fun openAnnouncementScreen() {
        emitEffect(
            MainUiEffect.Navigate(
                NavigationEvent.NavigateToDestination(NavDestination.ANNOUNCEMENTS)
            )
        )
        markAnnouncementsAsRead()
    }

    private fun checkAppUpdate(
        force: Boolean = true,
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
                        AppLog.i("MainViewModel", "No update. currentVersion=$currentVersion")
                        if (fromUserAction) {
                            emitEffect(MainUiEffect.ShowToast("当前已是最新版本"))
                        }
                        return@onSuccess
                    }
                    _uiState.update { state ->
                        state.copy(availableUpdate = info.toAppUpdateUiModel())
                    }
                    AppLog.i(
                        "MainViewModel",
                        "Update available current=${info.currentVersion}, latest=${info.latestVersion}"
                    )
                }.onFailure { error ->
                    AppLog.w("MainViewModel", "Check update failed: ${error.message}", error)
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

    private fun openCloudUpdateUrl() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        val cloudUrl = updateInfo.cloudDownloadUrl.trim()
        if (cloudUrl.isBlank()) {
            openUpdateUrl()
            return
        }
        _uiState.update { it.copy(availableUpdate = null) }
        emitEffect(MainUiEffect.OpenUrl(cloudUrl))
    }

    private fun ignoreCurrentUpdate() {
        if (_uiState.value.availableUpdate == null) return
        _uiState.update { it.copy(availableUpdate = null) }
    }

    private fun LauncherUpdateInfo.toAppUpdateUiModel(): AppUpdateUiModel {
        return AppUpdateUiModel(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            releaseUrl = releaseUrl,
            githubDownloadUrl = githubDownloadUrl,
            cloudDownloadUrl = cloudDownloadUrl,
            publishedAt = publishedAt
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
}
