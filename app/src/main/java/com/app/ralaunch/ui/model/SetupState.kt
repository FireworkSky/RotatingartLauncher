package com.app.ralaunch.ui.model

data class SetupState(
    val currentScreen: SetupScreen = SetupScreen.Legal,
    val components: List<ComponentItem> = emptyList(),
    val overallProgress: Int = 0,
    val overallStatus: String = "准备安装...",
    val isExtracting: Boolean = false,
    val extractionError: String? = null
)

enum class SetupScreen {
    Legal, Extraction
}