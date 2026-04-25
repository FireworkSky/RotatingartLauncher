package com.app.ralaunch.core.logging

enum class LogLevel(
    val label: String,
    val logcatPriority: String,
    private val severity: Int
) {
    VERBOSE("V", "V", 0),
    DEBUG("D", "D", 1),
    INFO("I", "I", 2),
    WARN("W", "W", 3),
    ERROR("E", "E", 4);

    fun allows(level: LogLevel): Boolean = level.severity >= severity
}
