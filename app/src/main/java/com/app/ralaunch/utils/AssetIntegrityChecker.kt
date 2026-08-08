package com.app.ralaunch.utils

import android.content.Context
import androidx.core.content.edit
import com.app.ralaunch.core.platform.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/*******************************************************************************
 * RotatingArtLauncher - AssetIntegrityChecker
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/7/31)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object AssetIntegrityChecker {

    /**
     * Check result
     */
    data class CheckResult(
        val isValid: Boolean,
        val issues: List<Issue>,
        val summary: String
    ) {
        data class Issue(
            val type: IssueType,
            val description: String,
            val filePath: String? = null,
            val canAutoFix: Boolean = false
        )

        enum class IssueType {
            MISSING_FILE,
            EMPTY_FILE,
            VERSION_MISMATCH,
            CORRUPTED_FILE,
            PERMISSION_ERROR,
            DIRECTORY_MISSING
        }
    }

    /**
     * Critical component definition
     */
    private data class CriticalComponent(
        val name: String,
        val dirName: String,
        val criticalFiles: List<String> = emptyList(),
        val minSizeBytes: Long = 1024
    )

    private val CRITICAL_COMPONENTS = listOf(
        CriticalComponent(
            name = "dotnet",
            dirName = "${AppConstants.Dirs.RUNTIMES}/dotnet"
        )
    )

    // ====== Public Methods ======

    /**
     * Perform integrity check
     */
    suspend fun checkIntegrity(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<CheckResult.Issue>()
        val filesDir = context.filesDir

        Timber.i("Starting asset integrity check...")

        for (component in CRITICAL_COMPONENTS) {
            val componentName = component.name
            val componentDir = File(filesDir, component.dirName)

            if (!componentDir.exists()) {
                issues.add(CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = "Directory missing: $componentName",
                    filePath = componentDir.absolutePath,
                    canAutoFix = true
                ))
                continue
            }

            if (component.dirName.endsWith("/dotnet")) {
                issues.addAll(checkDotNetComponent(componentDir, componentName))
            } else {
                for (fileName in component.criticalFiles) {
                    val file = File(componentDir, fileName)
                    checkFile(file, componentName, component.minSizeBytes)?.let {
                        issues.add(it)
                    }
                }
            }
        }

        val summary = if (issues.isEmpty()) {
            "All asset integrity checks passed"
        } else {
            val criticalCount = issues.count {
                it.type == CheckResult.IssueType.MISSING_FILE ||
                        it.type == CheckResult.IssueType.DIRECTORY_MISSING
            }
            val warningCount = issues.size - criticalCount
            buildString {
                append("Found ${issues.size} issue(s)")
                if (criticalCount > 0) {
                    append(" ($criticalCount critical")
                }
                if (warningCount > 0) {
                    append(if (criticalCount > 0) ", $warningCount warning" else "$warningCount warning")
                }
                if (criticalCount > 0 || warningCount > 0) {
                    append(")")
                }
            }
        }

        Timber.i("Asset integrity check completed: %s", summary)
        issues.forEach { issue ->
            Timber.w("  - [%s] %s: %s", issue.type, issue.description, issue.filePath)
        }

        CheckResult(
            isValid = issues.isEmpty(),
            issues = issues,
            summary = summary
        )
    }
    suspend fun forceReinstall(
        context: Context,
        onComplete: (() -> Unit)? = null
    ): FixResult = withContext(Dispatchers.IO) {
        Timber.i("Starting force reinstall of all assets...")

        val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, 0)

        for (component in CRITICAL_COMPONENTS) {
            val componentDir = File(context.filesDir, component.dirName)
            if (componentDir.exists()) {
                if (!deleteDirectoryRecursively(componentDir, context.filesDir)) {
                    Timber.w("Failed to clean component directory: %s", componentDir.absolutePath)
                }
            }
        }

        prefs.edit {
                putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)
        }

        AssetsManager.reset(prefs)

        try {
            AssetsManager.startInstallation(
                context = context,
                prefs = prefs,
                onComplete = {
                    Timber.i("Force reinstall completed")
                    onComplete?.invoke()
                }
            )

            var waitCount = 0
            while (AssetsManager.state.value.isExtracting && waitCount < 600) {
                kotlinx.coroutines.delay(100.milliseconds)
                waitCount++
            }

            val finalState = AssetsManager.state.value
            if (finalState.isComplete) {
                FixResult(
                    success = true,
                    message = "All components reinstalled successfully ✅",
                    needsRestart = false
                )
            } else {
                FixResult(
                    success = false,
                    message = finalState.errorMessage ?: "Installation timeout or failed"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Force reinstall failed")
            FixResult(
                success = false,
                message = "Reinstall failed: ${e.message}"
            )
        }
    }

    fun getStatusSummary(): String {
        val sb = StringBuilder()

        val dotnetRuntime = RuntimeManager.getSelectedRuntime(RuntimeManager.RuntimeType.DOTNET)
        val dotnetStatus = if (dotnetRuntime != null && hasValidDotNetLayout(dotnetRuntime.rootPath.toFile())) {
            val versions = RuntimeManager.getInstalledVersions(RuntimeManager.RuntimeType.DOTNET)
            if (versions.isNotEmpty()) {
                ".NET Runtime installed (versions: ${versions.joinToString()})"
            } else {
                ".NET Runtime installed"
            }
        } else {
            ".NET Runtime not installed"
        }
        sb.appendLine(dotnetStatus)

        return sb.toString()
    }

    /**
     * Fix result
     */
    data class FixResult(
        val success: Boolean,
        val message: String,
        val errors: List<String> = emptyList(),
        val needsRestart: Boolean = false
    )

    // ====== Private Methods ======

    private fun checkFile(
        file: File,
        componentName: String,
        minSize: Long
    ): CheckResult.Issue? {
        return when {
            !file.exists() -> CheckResult.Issue(
                type = CheckResult.IssueType.MISSING_FILE,
                description = "Missing file: $componentName/${file.name}",
                filePath = file.absolutePath,
                canAutoFix = true
            )
            file.length() == 0L -> CheckResult.Issue(
                type = CheckResult.IssueType.EMPTY_FILE,
                description = "Empty file: $componentName/${file.name}",
                filePath = file.absolutePath,
                canAutoFix = true
            )
            file.length() < minSize -> CheckResult.Issue(
                type = CheckResult.IssueType.CORRUPTED_FILE,
                description = "Corrupted file: $componentName/${file.name} (size: ${file.length()}, min: $minSize)",
                filePath = file.absolutePath,
                canAutoFix = true
            )
            !file.canRead() -> CheckResult.Issue(
                type = CheckResult.IssueType.PERMISSION_ERROR,
                description = "Permission error: $componentName/${file.name}",
                filePath = file.absolutePath,
                canAutoFix = false
            )
            else -> null
        }
    }

    private fun resolveAffectedComponents(
        context: Context,
        issues: List<CheckResult.Issue>
    ): List<CriticalComponent> {
        val filesDir = context.filesDir.absoluteFile.normalize()
        return CRITICAL_COMPONENTS.filter { component ->
            val componentDir = File(filesDir, component.dirName).absoluteFile.normalize()
            val componentPath = componentDir.path
            issues.any { issue ->
                val filePath = issue.filePath ?: return@any false
                val normalizedIssuePath = File(filePath).absoluteFile.normalize().path
                normalizedIssuePath == componentPath ||
                        normalizedIssuePath.startsWith(componentPath + File.separator)
            }
        }
    }

    private fun checkDotNetComponent(
        dotnetDir: File,
        componentName: String
    ): List<CheckResult.Issue> {
        val issues = mutableListOf<CheckResult.Issue>()
        val selectedRuntime = RuntimeManager.getSelectedRuntime(RuntimeManager.RuntimeType.DOTNET)
        if (selectedRuntime == null) {
            issues.add(
                CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = "Directory missing: $componentName runtime",
                    filePath = dotnetDir.absolutePath,
                    canAutoFix = true
                )
            )
            return issues
        }

        val runtimeRootPath = selectedRuntime.rootPath
        val dotnetRuntimeRoot = runtimeRootPath.toFile()

        val hostFxrVersionDir = getHostFxrVersionDir(dotnetRuntimeRoot)
        val hostFxrLib = hostFxrVersionDir?.let { File(it, "libhostfxr.so") }
        issues.addIfNotNull(
            checkFile(
                file = hostFxrLib ?: File(dotnetRuntimeRoot, "host/fxr/libhostfxr.so"),
                componentName = componentName,
                minSize = 100_000
            )
        )

        val runtimeRoot = File(dotnetRuntimeRoot, "shared/Microsoft.NETCore.App")
        val runtimeVersionDir = getDotNetRuntimeVersionDir(dotnetRuntimeRoot)
        if (runtimeVersionDir == null) {
            issues.add(
                CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = "Directory missing: $componentName runtime",
                    filePath = runtimeRoot.absolutePath,
                    canAutoFix = true
                )
            )
            return issues
        }

        listOf(
            "libcoreclr.so" to 1_000_000L,
            "libclrjit.so" to 1_000_000L,
            "libhostpolicy.so" to 100_000L
        ).forEach { (fileName, minSize) ->
            issues.addIfNotNull(
                checkFile(
                    file = File(runtimeVersionDir, fileName),
                    componentName = componentName,
                    minSize = minSize
                )
            )
        }

        return issues
    }

    private fun hasValidDotNetLayout(dotnetDir: File): Boolean {
        val hostFxrLib = getHostFxrVersionDir(dotnetDir)?.let { File(it, "libhostfxr.so") }
        if (hostFxrLib == null || !hostFxrLib.exists() || hostFxrLib.length() <= 100_000) {
            return false
        }

        val runtimeVersionDir = getDotNetRuntimeVersionDir(dotnetDir) ?: return false
        return listOf("libcoreclr.so", "libclrjit.so", "libhostpolicy.so").all { fileName ->
            val file = File(runtimeVersionDir, fileName)
            file.exists() && file.length() > 0
        }
    }

    private fun getDotNetRuntimeVersionDir(dotnetDir: File): File? {
        return File(dotnetDir, "shared/Microsoft.NETCore.App")
            .listFiles()
            ?.firstOrNull { it.isDirectory }
    }

    private fun getHostFxrVersionDir(dotnetDir: File): File? {
        return File(dotnetDir, "host/fxr")
            .listFiles()
            ?.firstOrNull { it.isDirectory }
    }

    private fun MutableList<CheckResult.Issue>.addIfNotNull(issue: CheckResult.Issue?) {
        if (issue != null) add(issue)
    }

    /**
     * Recursively delete directory with security check
     */
    private fun deleteDirectoryRecursively(directory: File, rootPath: File): Boolean {
        // Security check: ensure path is within rootPath
        if (!directory.absolutePath.startsWith(rootPath.absolutePath)) {
            Timber.w("Security: Attempted to delete path outside root: %s", directory.absolutePath)
            return false
        }

        if (!directory.exists()) return true

        if (directory.isDirectory) {
            directory.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    if (!deleteDirectoryRecursively(child, rootPath)) {
                        return false
                    }
                } else {
                    if (!child.delete()) {
                        Timber.w("Failed to delete file: %s", child.absolutePath)
                        return false
                    }
                }
            }
        }

        return directory.delete()
    }
}