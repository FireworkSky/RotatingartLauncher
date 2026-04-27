package com.app.ralaunch.core.logging.service

import android.os.Build
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import java.io.File
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
            .listFiles { file -> LogFilePolicy.isAppLogFile(file) }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    fun buildExportContent(): String {
        val files = getLogFiles()

        return buildString {
            appendDiagnosticSection(buildDeviceInfo())
            appendDiagnosticSection(buildGameRepositoryInfo())
            appendDiagnosticSection(buildPatchManagementInfo())

            files.forEachIndexed { index, file ->
                val banner = "=============== ${file.name} ==============="

                appendLine("=".repeat(banner.length))
                appendLine(banner)
                appendLine("=".repeat(banner.length))
                append(file.readText())
                if (!endsWith("\n")) {
                    appendLine()
                }
                if (index != files.lastIndex) {
                    appendLine()
                }
            }
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
                appendLine("Occurred At: ${sdf.format(Date())}\n")

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

    private fun StringBuilder.appendDiagnosticSection(section: String) {
        if (section.isBlank()) return
        if (isNotEmpty() && !endsWith("\n")) {
            appendLine()
        }
        append(section)
        if (!endsWith("\n")) {
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
    }
}
