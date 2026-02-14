package com.app.ralaunch.feature.main.contracts

sealed interface ImportUiEvent {
    data object StartImport : ImportUiEvent
    data object CancelImport : ImportUiEvent
}

sealed interface ImportUiEffect

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val errorMessage: String? = null,
    val lastCompletedGameId: String? = null
)
