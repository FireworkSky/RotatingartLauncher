package com.app.ralaunch.core.logging

enum class LogLevel(
    val label: String,
    val logcatPriority: String,
    private val severity: Int,
    val fileWritePriority: FileWritePriority
) {
    VERBOSE("V", "V", 0, FileWritePriority.LOW),
    DEBUG("D", "D", 1, FileWritePriority.LOW),
    INFO("I", "I", 2, FileWritePriority.LOW),
    WARN("W", "W", 3, FileWritePriority.HIGH),
    ERROR("E", "E", 4, FileWritePriority.HIGH);

    fun allows(level: LogLevel): Boolean = level.severity >= severity

    enum class FileWritePriority {
        LOW,
        HIGH;

        fun flushesImmediately(): Boolean = this == HIGH
    }
}
