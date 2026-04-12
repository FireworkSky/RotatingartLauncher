package com.app.ralaunch.feature.installer.contract

data class InstallerUiState(
    val gameFilePath: String? = null,
    val detectedGameName: String? = null,
    val modLoaderFilePath: String? = null,
    val detectedModLoaderName: String? = null,
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val errorMessage: String? = null
)

enum class InstallerFileType(
    val routeValue: String,
    val allowedExtensions: List<String>
) {
    GAME(
        routeValue = "game",
        allowedExtensions = listOf(".sh", ".zip")
    ),
    MOD_LOADER(
        routeValue = "modloader",
        allowedExtensions = listOf(".zip")
    );

    companion object {
        fun fromRouteValue(value: String?): InstallerFileType? {
            return entries.firstOrNull { it.routeValue == value }
        }
    }
}

sealed interface InstallerUiEvent {
    data class PrefillFromDownload(
        val gameFilePath: String?,
        val modLoaderFilePath: String?,
        val detectedGameName: String?
    ) : InstallerUiEvent

    data class BrowseRequested(val fileType: InstallerFileType) : InstallerUiEvent

    data class FileSelected(
        val fileType: InstallerFileType,
        val path: String,
        val preferredName: String? = null
    ) : InstallerUiEvent

    data object StartImport : InstallerUiEvent
    data object DismissError : InstallerUiEvent
    data object ResetSelections : InstallerUiEvent
}

sealed interface InstallerUiEffect {
    data class NavigateToFileBrowser(val fileType: InstallerFileType) : InstallerUiEffect
    data object NavigateToGames : InstallerUiEffect
    data class ShowToast(val message: String) : InstallerUiEffect
    data class ShowSuccess(val message: String) : InstallerUiEffect
}
