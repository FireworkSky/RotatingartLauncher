package com.app.ralaunch.feature.main.contracts

import com.app.ralaunch.feature.main.background.BackgroundType
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.model.ui.GameItemUi

data class MainUiState(
    val games: List<GameItemUi> = emptyList(),
    val selectedGame: GameItemUi? = null,
    val isLoading: Boolean = true,
    val backgroundType: BackgroundType = BackgroundType.None,
    val isVideoPlaying: Boolean = true,
    val gamePendingDeletion: GameItemUi? = null,
    val deletePosition: Int = -1,
    val isDeletingGame: Boolean = false
)

sealed interface MainUiEvent {
    data object RefreshRequested : MainUiEvent
    data class GameSelected(val game: GameItemUi) : MainUiEvent
    data class GameEdited(val game: GameItemUi) : MainUiEvent
    data object LaunchRequested : MainUiEvent
    data object DeleteRequested : MainUiEvent
    data object DeleteDialogDismissed : MainUiEvent
    data object DeleteConfirmed : MainUiEvent
    data class ImportCompleted(val gameType: String, val game: GameItem) : MainUiEvent
    data object AppResumed : MainUiEvent
    data object AppPaused : MainUiEvent
}

sealed interface MainUiEffect {
    data class ShowToast(val message: String) : MainUiEffect
    data class ShowSuccess(val message: String) : MainUiEffect
    data object ExitLauncher : MainUiEffect
}
