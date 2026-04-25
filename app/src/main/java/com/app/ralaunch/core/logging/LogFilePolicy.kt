package com.app.ralaunch.core.logging

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object LogFilePolicy {
    const val RETENTION_DAYS = 2

    private const val PREFIX = "ralaunch_"
    private const val EXTENSION = ".log"
    private const val LOGCAT_SUFFIX = "_logcat"
    private val dateFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val appLogFileRegex = Regex("^${Regex.escape(PREFIX)}\\d{4}-\\d{2}-\\d{2}${Regex.escape(EXTENSION)}$")
    private val logcatLogFileRegex = Regex(
        "^${Regex.escape(PREFIX)}\\d{4}-\\d{2}-\\d{2}${Regex.escape(LOGCAT_SUFFIX)}${Regex.escape(EXTENSION)}$"
    )

    fun appLogFileName(date: Date = Date()): String = "$PREFIX${formatDate(date)}$EXTENSION"

    fun logcatLogFileName(date: Date = Date()): String = "$PREFIX${formatDate(date)}$LOGCAT_SUFFIX$EXTENSION"

    fun isAppLogFile(file: File): Boolean = file.isFile && appLogFileRegex.matches(file.name)

    fun isLogcatLogFile(file: File): Boolean = file.isFile && logcatLogFileRegex.matches(file.name)

    fun isManagedLogFile(file: File): Boolean = isAppLogFile(file) || isLogcatLogFile(file)

    fun filesOlderThanRetention(directory: File, nowMillis: Long = System.currentTimeMillis()): List<File> {
        val cutoffMillis = nowMillis - RETENTION_DAYS * 24L * 60L * 60L * 1000L
        return directory
            .listFiles { file -> isManagedLogFile(file) && file.lastModified() < cutoffMillis }
            ?.toList()
            ?: emptyList()
    }

    private fun formatDate(date: Date): String = dateFormatter.format(Instant.ofEpochMilli(date.time))
}
