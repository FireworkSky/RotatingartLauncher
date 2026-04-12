package com.app.ralaunch.feature.main.contracts

import com.app.ralaunch.core.navigation.NavigationEvent
import com.app.ralaunch.feature.main.ui.background.BackgroundType
import com.app.ralaunch.core.model.GameItemUi

data class MainUiState(
    val games: List<GameItemUi> = emptyList(),
    val selectedGame: GameItemUi? = null,
    val isLoading: Boolean = true,
    val backgroundType: BackgroundType = BackgroundType.None,
    val isVideoPlaying: Boolean = true,
    val showAnnouncementBadge: Boolean = false,
    val forceAnnouncement: ForceAnnouncementUiModel? = null,
    val gamePendingDeletion: GameItemUi? = null,
    val deletePosition: Int = -1,
    val isDeletingGame: Boolean = false,
    val availableUpdate: AppUpdateUiModel? = null
)

data class ForceAnnouncementUiModel(
    val announcementId: String,
    val title: String,
    val publishedAt: String,
    val tags: List<String> = emptyList(),
    val markdown: String? = null
)

data class AppUpdateUiModel(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val githubDownloadUrl: String = "",
    val cloudDownloadUrl: String = "",
    val publishedAt: String = ""
)

sealed interface MainUiEvent {
    data object CheckAppUpdate : MainUiEvent
    data object CheckAppUpdateManually : MainUiEvent
    data class GameSelected(val game: GameItemUi) : MainUiEvent
    data class GameEdited(val game: GameItemUi) : MainUiEvent
    data object LaunchRequested : MainUiEvent
    data object DeleteRequested : MainUiEvent
    data object DeleteDialogDismissed : MainUiEvent
    data object DeleteConfirmed : MainUiEvent
    data object UpdateDialogDismissed : MainUiEvent
    data object UpdateIgnoreClicked : MainUiEvent
    data object UpdateActionClicked : MainUiEvent
    data object UpdateCloudActionClicked : MainUiEvent
    data object AppResumed : MainUiEvent
    data object AppPaused : MainUiEvent
    data object AnnouncementTabOpened : MainUiEvent
    data object AnnouncementPopupLearnMoreClicked : MainUiEvent
    data object AnnouncementPopupViewClicked : MainUiEvent
}

sealed interface MainUiEffect {
    data class ShowToast(val message: String) : MainUiEffect
    data class ShowSuccess(val message: String) : MainUiEffect
    data class DownloadLauncherUpdate(
        val downloadUrl: String,
        val latestVersion: String,
        val releaseUrl: String
    ) : MainUiEffect
    data class OpenUrl(val url: String) : MainUiEffect
    data class Navigate(val event: NavigationEvent) : MainUiEffect
    data object ExitLauncher : MainUiEffect
}
