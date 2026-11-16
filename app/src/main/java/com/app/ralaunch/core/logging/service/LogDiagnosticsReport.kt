package com.app.ralaunch.core.logging.service

import com.app.ralaunch.core.logging.LogEntry
import java.io.File
import java.security.MessageDigest

/** A bounded index into the evidence, never a replacement for the original files. */
internal class LogDiagnosticsReport {
    private data class Group(val first: LogEntry, var last: LogEntry, val priority: Int, var count: Int = 1)

    fun build(files: List<File>, records: (File) -> Sequence<LogEntry>): String {
        val sources = mutableListOf<String>()
        val repeats = LinkedHashMap<List<String>, Group>()
        val issues = LinkedHashMap<List<String>, Group>()
        val launches = LinkedHashMap<String, LogEntry>()
        var total = 0L
        var warnings = 0L
        var errors = 0L
        var fatals = 0L
        var limited = false
        var losses = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        files.forEach { file ->
            var count = 0L
            var first: String? = null
            var last: String? = null
            var sourceSession = "before-first-start"
            try {
                records(file).forEach { entry ->
                    total++
                    count++
                    if (first == null || entry.timestamp < first!!) first = entry.timestamp
                    if (last == null || entry.timestamp > last!!) last = entry.timestamp
                    when (entry.eventLevel) {
                        "W" -> warnings++
                        "E" -> errors++
                        "F", "A" -> fatals++
                    }
                    if (entry.message.contains("async log queue was full")) losses++
                    if (entry.tag in setOf("GameActivity", "GameActivitySdl2") &&
                        entry.message.contains("Process Started")) {
                        sourceSession = entry.timestamp
                        launches[entry.timestamp] = entry
                        if (launches.size > 32) launches.remove(launches.keys.minOrNull())
                    }
                    val fingerprint = digest.digest(entry.message.toByteArray(Charsets.UTF_8))
                        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    val sample = if (entry.text.length > 4096) entry.copy(text = entry.text.take(4096)) else entry
                    val key = listOf(entry.tag, entry.process, entry.eventLevel, fingerprint)
                    // Streaming LRU tables bound memory even when every message is unique.
                    val group = repeats.remove(key)
                    repeats[key] = group?.apply { this.count++; this.last = sample } ?: Group(sample, sample, priority(entry))
                    if (repeats.size > MAX_GROUPS) {
                        repeats.remove(repeats.keys.first())
                        limited = true
                    }
                    if (entry.isIssue) {
                        // Keep separate launches separate, even when their messages are identical.
                        val issueKey = listOf(sourceSession, entry.tag, entry.process.substringBefore(':'), entry.eventLevel, fingerprint)
                        val issue = issues.remove(issueKey)
                        issues[issueKey] = issue?.apply {
                            this.count++
                            if (entry.timestamp > this.last.timestamp) this.last = sample
                        } ?: Group(sample, sample, priority(entry))
                        if (issues.size > MAX_ISSUES) {
                            val leastUseful = issues.minWithOrNull(
                                compareBy<Map.Entry<List<String>, Group>> { it.value.priority }
                                    .thenBy { it.value.last.timestamp }
                            )
                            issues.remove(leastUseful?.key)
                            limited = true
                        }
                    }
                }
                sources += "${file.name}: ${file.length()} bytes, $count records, ${first ?: "no timestamp"} .. ${last ?: "no timestamp"}"
            } catch (exception: Exception) {
                sources += "${file.name}: READ FAILED after $count records: ${exception.javaClass.simpleName}: ${exception.message}"
            }
        }

        val latest = launches.values.maxByOrNull { it.timestamp }
        val recentIssues = issues.values.filter { latest == null || it.last.timestamp >= latest.timestamp }
        return buildString {
            appendLine("=============== Diagnostic Overview ===============")
            appendLine("Overview format: 1; timestamps use the device's local time.")
            appendLine("This is an index of captured evidence, not a crash diagnosis. Original log sections follow.")
            appendLine("Records: $total; WARN=$warnings ERROR=$errors FATAL=$fatals (transport mirrors may duplicate counts).")
            appendLine("Severity uses embedded game headers where present; raw transport severity is preserved below.")
            appendLine("Latest observed game start: ${latest?.timestamp ?: "not captured"}")
            latest?.let { appendLine("  Source: ${it.source}; process/thread: ${it.process.ifBlank { "not recorded" }}; ${it.tag}") }
            appendLine("Observed game starts: ${launches.size}${if (launches.size == 32) " (latest 32 retained)" else ""}")
            appendLine("Capture loss markers: $losses; absent markers do not guarantee crash output was captured.")
            appendLine("Sources:")
            sources.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Issues since latest observed game start:")
            if (recentIssues.isEmpty()) appendLine("  No issue records captured in this range; this does not prove a clean exit.")
            recentIssues.sortedWith(compareByDescending<Group> { it.priority }.thenByDescending { it.last.timestamp })
                .take(20).forEach { group ->
                    val entry = group.last
                    appendLine("  ${entry.timestamp} [${entry.eventLevel}] ${entry.tag} x${group.count} (${entry.source}, ${entry.process.ifBlank { "PID unavailable" }})")
                    appendLine(entry.message.take(4096).prependIndent("    "))
                }
            appendLine()
            appendLine("Most repeated exact messages (per process; counts include the first occurrence):")
            repeats.values.filter { it.count > 1 }.sortedByDescending { it.count }.take(10).forEach { group ->
                appendLine("  x${group.count} [${group.last.eventLevel}] ${group.last.tag} ${group.last.process} ${group.first.timestamp} .. ${group.last.timestamp}")
                appendLine("    ${group.first.message.substringBefore('\n').take(400)}")
            }
            appendLine("Overview bounded: $limited (up to $MAX_GROUPS message groups, $MAX_ISSUES issue groups; full records remain below).")
            appendLine("Live files may grow during export. Missing/truncated sources must not be interpreted as a successful launch.")
        }
    }

    private companion object {
        const val MAX_GROUPS = 2048
        const val MAX_ISSUES = 128

        fun priority(entry: LogEntry): Int = when {
            entry.isFatal -> 4
            entry.eventLevel == "E" -> 3
            entry.tag.startsWith("DOTNET") && entry.isIssue -> 2
            entry.message.contains("avc:  granted") -> 0
            else -> 1
        }
    }
}
