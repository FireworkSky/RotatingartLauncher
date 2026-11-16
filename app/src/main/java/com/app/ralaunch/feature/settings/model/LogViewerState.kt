package com.app.ralaunch.feature.settings.model

import com.app.ralaunch.core.logging.LogEntry

data class LogViewerState(
    val entries: List<LogEntry> = emptyList(),
    val sources: List<String> = emptyList(),
    val query: String = "",
    val issuesOnly: Boolean = false,
    val source: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)
