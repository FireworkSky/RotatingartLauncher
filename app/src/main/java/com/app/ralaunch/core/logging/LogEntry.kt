package com.app.ralaunch.core.logging

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One log record with its stack trace. Original text and transport severity are preserved. */
data class LogEntry(
    val id: String,
    val source: String,
    val timestamp: String,
    val level: String,
    val tag: String,
    val process: String,
    val text: String,
    val occurrences: Int = 1,
    val lastTimestamp: String = timestamp
) {
    val message: String = RECORD.matchEntire(text.substringBefore('\n'))?.groups?.get(6)?.range?.first
        ?.let { text.substring(it) } ?: text

    /** The transport and the game may report different severity levels. Keep both. */
    val eventLevel: String = if (level in setOf("F", "A")) level else {
        when (MANAGED_HEADER.find(message)?.groupValues?.get(1)) {
            "TRACE" -> "V"
            "DEBUG" -> "D"
            "INFO" -> "I"
            "WARN", "WARNING" -> "W"
            "ERROR" -> "E"
            "FATAL" -> "F"
            else -> level
        }
    }

    val isFatal: Boolean = eventLevel in setOf("F", "A") || FATAL_TEXT.containsMatchIn(message)
    val isIssue: Boolean = eventLevel in setOf("W", "E", "F", "A") || ISSUE_TEXT.containsMatchIn(message)

    fun matches(query: String, issuesOnly: Boolean, sourceFilter: String? = null): Boolean =
        (!issuesOnly || isIssue) && (sourceFilter == null || source == sourceFilter) &&
            (query.isBlank() || "$source\n$text".contains(query.trim(), ignoreCase = true))

    companion object {
        private val RECORD = Regex(
            "^\\[(?:(\\d{4})-)?(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)]" +
                " \\[([A-Z])] \\[([^]]+)](?: \\[(\\d+(?::\\d+)?)])? ?(.*)$"
        )
        private val MANAGED_HEADER = Regex(
            "^\\[\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?] \\[[^]\\r\\n]+/(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)]"
        )
        private val ISO_RECORD = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\S+Z) \\[([^]]+)] (.*)$")
        private val LOCAL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private val FATAL_TEXT = Regex("\\b(?:Fatal signal \\d+|FATAL EXCEPTION|Unhandled exception|SIGABRT|SIGSEGV)\\b", RegexOption.IGNORE_CASE)
        private val ISSUE_TEXT = Regex(
            "\\b(?:[\\w.]*Exception|failed|failure|fatal|unhandled|error)\\b",
            RegexOption.IGNORE_CASE
        )

        internal fun parse(source: String, id: String, fallbackTimestamp: String, line: String): LogEntry {
            ISO_RECORD.matchEntire(line)?.let { match ->
                val localTime = runCatching {
                    Instant.parse(match.groupValues[1]).atZone(ZoneId.systemDefault()).format(LOCAL_TIMESTAMP)
                }.getOrDefault(fallbackTimestamp)
                return LogEntry(id, source, localTime, "I", match.groupValues[2], "", line)
            }
            val match = RECORD.matchEntire(line)
            val timestamp = match?.let {
                val year = it.groupValues[1].ifEmpty { fallbackTimestamp.take(4) }
                val time = it.groupValues[2].let { value -> if ('.' in value) value else "$value.000" }
                "$year-$time"
            } ?: fallbackTimestamp
            return LogEntry(
                id = id,
                source = source,
                timestamp = timestamp,
                level = match?.groupValues?.get(3) ?: "I",
                tag = match?.groupValues?.get(4).orEmpty(),
                process = match?.groupValues?.get(5).orEmpty(),
                text = line
            )
        }
    }
}
