package com.app.ralaunch.core.logging.service

import android.os.Build
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.android.provider.RaLaunchFileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.LogEntry
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class LogExportHelper(
    private val logsDirPathProvider: () -> String,
    private val gameRepositoryProvider: () -> IGameRepositoryServiceV3? = { null },
    private val patchManagerProvider: () -> PatchManager? = { null }
) {
    constructor(
        storagePathsProvider: StoragePathsProviderServiceV1,
        gameRepository: IGameRepositoryServiceV3? = null,
        patchManager: PatchManager? = null
    ) : this(
        logsDirPathProvider = storagePathsProvider::logsDirPathFull,
        gameRepositoryProvider = { gameRepository },
        patchManagerProvider = { patchManager }
    )

    fun getLogFiles(): List<File> {
        val logsDir = resolveLogsDir() ?: return emptyList()
        return logsDir
            .listFiles { file -> LogFilePolicy.isManagedLogFile(file) }
            ?.sortedWith(
                compareBy<File> { if (LogFilePolicy.isLogcatLogFile(it)) 1 else 0 }
                    .thenBy { it.lastModified() }
            )
            ?: emptyList()
    }

    fun getAppLogFiles(): List<File> {
        val logsDir = resolveLogsDir() ?: return emptyList()
        return logsDir
            .listFiles { file -> LogFilePolicy.isAppLogFile(file) || LogFilePolicy.isRuntimeLogFile(file) }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    fun buildExportContent(): String {
        val buffer = StringWriter()
        writeExportTo(buffer)
        return buffer.toString()
    }

    fun getPreviewLogFiles(): List<File> {
        val diagnostics = mutableListOf<File>()
        resolveLogsDir()?.let { directory ->
            listOf(TouchInputDiagnostics.FILE_NAME, "${TouchInputDiagnostics.FILE_NAME}.previous")
                .mapTo(diagnostics) { File(directory, it) }
        }
        runCatching {
            patchManagerProvider()?.installedPatches?.firstOrNull {
                it.manifest.id == "com.app.ralaunch.tmodloader.touch"
            }?.let { patch ->
                listOf("touch-input.log", "touch-input.log.previous").mapTo(diagnostics) {
                    patch.patchPath.resolve(it).toFile()
                }
            }
        }
        return (getLogFiles() + diagnostics.filter { it.isFile }).distinctBy { it.absolutePath }
    }

    /** Bounded, chronological snapshot of every captured source, including game logcat. */
    fun readRecentEntries(
        limit: Int = 1000,
        query: String = "",
        issuesOnly: Boolean = false,
        sourceFilter: String? = null
    ): List<LogEntry> {
        require(limit > 0)
        return getPreviewLogFiles().filter { sourceFilter == null || it.name == sourceFilter }.flatMap { file ->
            // Compact exact records per process, including interleaved system chatter. Raw export is unchanged.
            val tail = LinkedHashMap<List<String>, LogEntry>()
            try {
                readRecords(file, MAX_PREVIEW_BYTES).forEach { entry ->
                    if (!LogcatReader.isCompatibilityNoise(entry.tag, entry.message) &&
                        entry.matches(query, issuesOnly, sourceFilter)) {
                        val key = listOf(entry.process, entry.tag, entry.eventLevel, entry.message)
                        val previous = tail.remove(key)
                        tail[key] = if (previous == null) entry else previous.copy(
                            occurrences = previous.occurrences + 1,
                            lastTimestamp = entry.timestamp
                        )
                        if (tail.size > limit) tail.remove(tail.keys.first())
                    }
                }
            } catch (exception: java.io.IOException) {
                val issue = LogEntry(
                    id = "${file.name}:read-failure", source = file.name,
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()),
                    level = "W", tag = TAG, process = "",
                    text = "Cannot read source ${file.name}: ${exception.message}"
                )
                if (issue.matches(query, issuesOnly, sourceFilter)) tail[listOf(issue.id)] = issue
            }
            tail.values.toList()
        }.sortedBy { it.lastTimestamp }.takeLast(limit)
    }

    /** Read complete records and preserve stacks; callers choose a bounded preview or full evidence. */
    private fun readRecords(file: File, maxBytes: Long? = null): Sequence<LogEntry> = sequence {
        val fileDate = Regex("\\d{4}-\\d{2}-\\d{2}").find(file.name)?.value
        val fallback = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(file.lastModified()))
        val fallbackTime = fileDate?.let { it + fallback.drop(10) } ?: fallback
        val offset = maxBytes?.let { (file.length() - it).coerceAtLeast(0) } ?: 0L
        var current: LogEntry? = null
        var lineNumber = 0
        file.inputStream().apply { channel.position(offset) }.bufferedReader(Charsets.UTF_8).use { input ->
            if (offset > 0) input.readLine()
            for (line in input.lineSequence()) {
                lineNumber++
                val parsed = LogEntry.parse(file.name, "${file.name}:$offset:$lineNumber", fallbackTime, line)
                val stackLine = parsed.message.trimStart().let {
                    it.startsWith("at ") || it.startsWith("---") || it.startsWith("Caused by:") ||
                        it.startsWith("... ") || it.startsWith("---> ")
                }
                val previous = current
                val sameOrigin = parsed.tag.isEmpty() ||
                    (previous?.tag == parsed.tag && previous.process == parsed.process)
                if (previous != null && sameOrigin && (stackLine ||
                        (parsed.tag.isEmpty() && !LogFilePolicy.isRuntimeLogFile(file)))) {
                    val combined = previous.text + "\n" + line
                    current = previous.copy(text = if (combined.length <= MAX_RECORD_CHARS) combined else {
                        combined.take(MAX_RECORD_CHARS / 2) + "\n[record shortened in preview/overview; full text below]\n" +
                            combined.takeLast(MAX_RECORD_CHARS / 2)
                    })
                } else {
                    previous?.let { yield(it) }
                    current = parsed.copy(text = line.take(MAX_RECORD_CHARS))
                }
            }
            current?.let { yield(it) }
        }
    }

    suspend fun shareLogs(context: Context) {
        try {
            val uri = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "shared_logs").apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
                directory.listFiles()?.filter {
                    it.isFile && (it.name.startsWith("ralaunch-logs-") || LogFilePolicy.isManagedLogFile(it)) &&
                        it.lastModified() < cutoff
                }?.forEach { it.delete() }
                // Keep each share immutable while QQ/other recipients read the URI asynchronously.
                val file = File.createTempFile("ralaunch-logs-", ".txt", directory)
                file.outputStream().use { writeExportTo(it) }
                FileProvider.getUriForFile(context, RaLaunchFileProvider.AUTHORITY, file)
            }
            withContext(Dispatchers.Main) {
                val title = context.getString(R.string.control_editor_share_logs)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    clipData = ClipData.newUri(context.contentResolver, title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.log_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 将导出内容直接写入流，避免先把所有日志读成一个巨大 String
     * 再转 ByteArray，降低导出快照/日志时的峰值内存。
     */
    fun writeExportTo(output: OutputStream) {
        BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).let { writer ->
            writeExportTo(writer)
            writer.flush()
        }
    }

    fun writeExportTo(writer: Writer) {
        writer.appendDiagnosticSection(buildDeviceInfo())
        val files = getLogFiles()
        writer.appendDiagnosticSection(LogDiagnosticsReport().build(getPreviewLogFiles()) { readRecords(it) })
        writer.appendDiagnosticSection(buildGameRepositoryInfo())
        writer.appendDiagnosticSection(buildPatchManagementInfo())
        writeAndroidTouchDiagnostics(writer)
        writeTouchDiagnostics(writer)

        files.forEachIndexed { index, file ->
            val banner = "=============== ${file.name} ==============="

            writer.appendLine("=".repeat(banner.length))
            writer.appendLine(banner)
            writer.appendLine("=".repeat(banner.length))
            // 按行流式拷贝，避免 file.readText() 把整个日志文件载入内存。
            try {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        writer.append(line)
                        writer.append('\n')
                    }
                }
            } catch (exception: java.io.IOException) {
                writer.appendLine("[SOURCE READ FAILED: ${file.name}: ${exception.message}]")
            }
            if (index != files.lastIndex) {
                writer.appendLine()
            }
        }
        writer.flush()
    }

    private fun writeAndroidTouchDiagnostics(writer: Writer) {
        val directory = resolveLogsDir() ?: return
        listOf("${TouchInputDiagnostics.FILE_NAME}.previous", TouchInputDiagnostics.FILE_NAME).forEach { name ->
            try {
                val file = File(directory, name)
                if (file.isFile) {
                    writer.appendLine("=============== Android Touch Diagnostics: $name ===============")
                    file.bufferedReader().use { it.copyTo(writer) }
                    writer.appendLine()
                }
            } catch (e: Exception) {
                writer.appendLine("Android Touch Diagnostics ($name): ${e.message}")
            }
        }
    }

    private fun writeTouchDiagnostics(writer: Writer) {
        try {
            val patch = patchManagerProvider()?.installedPatches?.firstOrNull {
                it.manifest.id == "com.app.ralaunch.tmodloader.touch"
            } ?: return
            listOf("touch-input.log.previous", "touch-input.log").forEach { name ->
                val file = patch.patchPath.resolve(name).toFile()
                if (file.isFile) {
                    writer.appendLine("=============== Touch Input Diagnostics: $name ===============")
                    file.bufferedReader().use { it.copyTo(writer) }
                    writer.appendLine()
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to export touch input diagnostics", e)
            writer.appendLine("Touch Input Diagnostics: ${e.message}")
        }
    }

    fun buildGameRepositoryInfo(): String {
        return try {
            val gameRepository = gameRepositoryProvider()
                ?: return buildDiagnosticSection("Game Repository Information") {
                    appendLine("Status: Unavailable")
                }
            val games = gameRepository.games.value

            buildDiagnosticSection("Game Repository Information") {
                appendLine("Games Root: ${safeValue { gameRepository.getGameGlobalStorageDirFull() }}")
                appendLine("Installed Games: ${games.size}")

                if (games.isEmpty()) {
                    appendLine("No installed games.")
                }

                games.forEachIndexed { index, game ->
                    appendLine()
                    appendLine("[${index + 1}] ${game.displayedName.ifBlank { "Unknown" }}")
                    appendLine("  Id: ${game.id.ifBlank { "Unknown" }}")
                    appendLine("  Game Id: ${game.gameId.ifBlank { "Unknown" }}")
                    appendLine("  Storage Relative: ${game.storageRootPathRelative.ifBlank { "Unknown" }}")
                    appendLine("  Storage Full: ${game.storageRootPathFull ?: "Unknown"}")
                    appendLine("  Executable Relative: ${game.gameExePathRelative.ifBlank { "Unknown" }}")
                    appendLine("  Executable Full: ${game.gameExePathFull ?: "Unknown"}")
                    game.iconPathRelative?.takeIf { it.isNotBlank() }?.let {
                        appendLine("  Icon Relative: $it")
                        appendLine("  Icon Full: ${game.iconPathFull ?: "Unknown"}")
                    }
                    appendLine("  Mod Loader Enabled: ${game.modLoaderEnabled}")
                    appendLine("  Renderer Override: ${game.rendererOverride ?: "None"}")
                    appendLine("  .NET Runtime Override: ${game.dotNetRuntimeVersionOverride ?: "None"}")
                    appendEnvVarSummary(game.gameEnvVars)
                }
            }
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to build game repository info", e)
            buildFailureSection("Game Repository Information", e)
        }
    }

    fun buildPatchManagementInfo(): String {
        return try {
            val patchManager = patchManagerProvider()
                ?: return buildDiagnosticSection("Patch Management Information") {
                    appendLine("Status: Unavailable")
                }
            val installedPatches = patchManager.installedPatches
            val games = gameRepositoryProvider()?.games?.value.orEmpty()

            buildDiagnosticSection("Patch Management Information") {
                appendLine("Status: Available")
                appendLine("Installed Patches: ${installedPatches.size}")

                if (installedPatches.isEmpty()) {
                    appendLine("No installed patches.")
                }

                installedPatches
                    .sortedWith(compareByDescending<Patch> { it.manifest.priority }.thenBy { it.manifest.id })
                    .forEachIndexed { index, patch ->
                        val manifest = patch.manifest
                        appendLine()
                        appendLine("[${index + 1}] ${manifest.name.ifBlank { manifest.id.ifBlank { "Unknown" } }}")
                        appendLine("  Id: ${manifest.id.ifBlank { "Unknown" }}")
                        appendLine("  Version: ${manifest.version.ifBlank { "Unknown" }}")
                        appendLine("  Author: ${manifest.author.ifBlank { "Unknown" }}")
                        appendLine("  Priority: ${manifest.priority}")
                        val targetGames = manifest.targetGames
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ")
                            ?: "None"
                        appendLine("  Target Games: $targetGames")
                        appendLine("  Entry Assembly: ${safeValue { patch.getEntryAssemblyAbsolutePath().toString() }}")
                        manifest.entryPoint?.let { entryPoint ->
                            appendLine("  Entry Point: ${entryPoint.typeName}.${entryPoint.methodName}")
                        }
                        appendLine("  Dependency Libs: ${manifest.dependencies?.libs?.size ?: 0}")
                    }

                appendLine()
                appendLine("Per-Game Patch State:")
                if (games.isEmpty()) {
                    appendLine("No installed games available for patch state.")
                }

                games.forEach { game ->
                    val gameAsmPath = Paths.get(game.gameExePathFull ?: game.gameExePathRelative)
                    val applicablePatches = patchManager.getApplicablePatches(game.gameId)
                    val enabledPatchIds = applicablePatches
                        .filter { patchManager.isPatchEnabled(gameAsmPath, it.manifest.id) }
                        .map { it.manifest.id }
                    val disabledPatchIds = applicablePatches
                        .filterNot { patchManager.isPatchEnabled(gameAsmPath, it.manifest.id) }
                        .map { it.manifest.id }

                    appendLine()
                    appendLine("${game.displayedName.ifBlank { game.id }} (${game.gameId.ifBlank { "Unknown" }})")
                    appendLine("  Assembly Path: $gameAsmPath")
                    appendLine("  Applicable Patches: ${applicablePatches.size}")
                    appendLine("  Enabled Applicable Patch Ids: ${enabledPatchIds.joinOrNone()}")
                    appendLine("  Disabled Applicable Patch Ids: ${disabledPatchIds.joinOrNone()}")
                }
            }
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to build patch management info", e)
            buildFailureSection("Patch Management Information", e)
        }
    }

    private fun buildDeviceInfo(): String {
        try {
            return buildString {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                appendLine("Exported At: ${sdf.format(Date())} (${java.util.TimeZone.getDefault().id})\n")

                val versionName = runCatching {
                    @Suppress("DEPRECATION")
                    RaLaunchApp.getAppContext().packageManager.getPackageInfo(RaLaunchApp.getAppContext().packageName, 0).versionName
                }.getOrNull()
                val versionCode = runCatching {
                    @Suppress("DEPRECATION")
                    RaLaunchApp.getAppContext().packageManager.getPackageInfo(RaLaunchApp.getAppContext().packageName, 0).longVersionCode
                }.getOrNull()
                appendLine("App Version: ${versionName ?: "Unknown"} (Version Code: ${versionCode ?: "Unknown"})")

                val packageName = runCatching {
                    RaLaunchApp.getAppContext().packageName
                }.getOrNull()
                appendLine("Package Name: ${packageName ?: "Unknown"}")

                appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            }
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to build device info", e)
            return ""
        }
    }

    private fun resolveLogsDir(): File? {
        val logsDirPath = runCatching { logsDirPathProvider() }.getOrNull() ?: return null
        return File(logsDirPath).also { logsDir ->
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
        }
    }

    private fun Writer.appendDiagnosticSection(section: String) {
        if (section.isBlank()) return
        append(section)
        if (!section.endsWith("\n")) {
            appendLine()
        }
        appendLine()
    }

    private fun buildDiagnosticSection(title: String, block: StringBuilder.() -> Unit): String {
        return buildString {
            val banner = "=============== $title ==============="
            appendLine("=".repeat(banner.length))
            appendLine(banner)
            appendLine("=".repeat(banner.length))
            block()
        }
    }

    private fun buildFailureSection(title: String, throwable: Throwable): String {
        return buildDiagnosticSection(title) {
            appendLine("Status: Failed")
            appendLine("Error: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun StringBuilder.appendEnvVarSummary(envVars: Map<String, String?>) {
        appendLine("  Environment Vars: ${if (envVars.isEmpty()) "None" else "${envVars.size} key(s)"}")
        envVars.keys.sorted().forEach { key ->
            val state = if (envVars[key] == null) "unset" else "set"
            appendLine("    ${key.ifBlank { "Unknown" }}: $state")
        }
    }

    private fun safeValue(valueProvider: () -> String): String {
        return runCatching { valueProvider().takeIf { it.isNotBlank() } ?: "Unknown" }
            .getOrDefault("Unknown")
    }

    private fun List<String>.joinOrNone(): String {
        return if (isEmpty()) "None" else joinToString(", ")
    }

    companion object {
        private const val TAG = "LogExportHelper"
        private const val MAX_RECORD_CHARS = 64 * 1024
        private const val MAX_PREVIEW_BYTES = 1024 * 1024L

    }
}
