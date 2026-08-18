package com.app.ralaunch.utils

import android.content.Context
import android.content.SharedPreferences
import com.app.ralaunch.MainActivity
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.platform.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

/*******************************************************************************
 * RotatingArtLauncher - AssetsManager
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

/**
 * Asset Manager - Responsible for runtime component installation and management
 */
object AssetsManager {

    // ====== State Definitions ======

    /**
     * Component state
     */
    data class ComponentState(
        val name: String,
        val description: String,
        val fileName: String,
        val needsExtraction: Boolean = true,
        val isInstalled: Boolean = false,
        val progress: Int = 0,
        val status: String = ""
    )

    /**
     * Installation state
     */
    data class InstallState(
        val isExtracting: Boolean = false,
        val isComplete: Boolean = false,
        val overallProgress: Int = 0,
        val statusMessage: String = "",
        val errorMessage: String? = null,
        val components: List<ComponentState> = emptyList(),
        val isCancelled: Boolean = false
    )

    // ====== Component Configuration ======

    private val componentList = listOf(
        ComponentConfig("dotnet", "Microsoft .NET Runtime", "dotnet.tar.xz", true)
    )

    private data class ComponentConfig(
        val name: String,
        val description: String,
        val fileName: String,
        val needsExtraction: Boolean
    )

    // ====== State Flow ======

    private val _state = MutableStateFlow(InstallState())
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private var installationJob: kotlinx.coroutines.Job? = null

    // ====== Initialization ======

    private fun getInitialComponents(): List<ComponentState> {
        return componentList.map {
            ComponentState(
                name = it.name,
                description = it.description,
                fileName = it.fileName,
                needsExtraction = it.needsExtraction
            )
        }
    }

    // ====== Public Methods ======

    /**
     * Initialize state
     */
    fun init() {
        _state.update { current ->
            current.copy(
                components = getInitialComponents(),
                isExtracting = false,
                isComplete = false,
                overallProgress = 0,
                statusMessage = "Ready",
                errorMessage = null,
                isCancelled = false
            )
        }
    }

    /**
     * Start installation
     */
    fun startInstallation(
        context: Context,
        prefs: SharedPreferences,
        onComplete: () -> Unit = {}
    ) {
        // Prevent duplicate installation
        if (_state.value.isExtracting || _state.value.isComplete) return

        // Check permissions
        if (!MainActivity.hasStoragePermission) {
            _state.update { it.copy(errorMessage = "Manage external storage permission required") }
            return
        }

        // Reset state
        _state.update { current ->
            current.copy(
                components = getInitialComponents(),
                overallProgress = 0,
                statusMessage = "Starting installation...",
                errorMessage = null,
                isCancelled = false,
                isExtracting = true,
                isComplete = false
            )
        }

        installationJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            performInstallation(context, prefs, onComplete)
        }
    }

    /**
     * Cancel installation
     */
    fun cancelInstallation() {
        if (!_state.value.isExtracting || _state.value.isComplete) return

        _state.update { it.copy(isCancelled = true) }
        installationJob?.cancel()
        installationJob = null

        _state.update {
            it.copy(
                isExtracting = false,
                statusMessage = "Installation cancelled"
            )
        }
    }

    /**
     * Reset all states
     */
    fun reset(prefs: SharedPreferences) {
        if (_state.value.isExtracting) {
            cancelInstallation()
        }

        _state.update { current ->
            current.copy(
                components = getInitialComponents(),
                overallProgress = 0,
                isComplete = false,
                isExtracting = false,
                statusMessage = "Reset, ready to reinstall",
                errorMessage = null,
                isCancelled = false
            )
        }

        prefs.edit { putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false) }
    }

    // ====== Internal Methods ======

    private suspend fun performInstallation(
        context: Context,
        prefs: SharedPreferences,
        onComplete: () -> Unit
    ) {
        try {
            // Check permissions again
            if (!MainActivity.hasStoragePermission) {
                throw SecurityException("Manage external storage permission required")
            }

            val components = _state.value.components

            components.forEachIndexed { index, component ->
                // Check if cancelled
                if (_state.value.isCancelled) {
                    throw CancellationException("Installation cancelled by user")
                }

                if (!component.needsExtraction) {
                    updateComponent(index, 100, true, "No extraction needed")
                    return@forEachIndexed
                }

                updateComponent(index, 10, false, "Preparing file...")

                val tempFile = File(context.cacheDir, "temp_${component.fileName}")
                ArchiveExtractor.copyAssetToFile(context, component.fileName, tempFile.toPath())

                if (_state.value.isCancelled) {
                    deleteFileSafely(tempFile, context.cacheDir)
                    throw CancellationException("Installation cancelled by user")
                }

                updateComponent(index, 30, false, "Extracting...")

                val runtimeType = RuntimeManager.RuntimeType.fromDirName(component.name)
                    ?: error("Unsupported component: ${component.name}")

                val stagingRootDir = Path(context.cacheDir.absolutePath, "runtime-staging")
                val stagingDir = stagingRootDir.resolve(component.name)

                // Clean staging directory
                if (stagingDir.exists()) {
                    val deleted = deleteDirectoryRecursively(stagingDir.toFile(), stagingRootDir.toFile())
                    if (!deleted) {
                        throw IllegalStateException("Failed to clean staging directory: ${component.name}")
                    }
                }
                stagingDir.createDirectories()

                when (val result = ArchiveExtractor.builder()
                    .sourcePath(tempFile.toPath())
                    .destinationPath(stagingDir)
                    .callback { event ->
                        if (event is ArchiveExtractor.Event.Progress && event.progress < 1f) {
                            if (_state.value.isCancelled) {
                                throw CancellationException("Installation cancelled by user")
                            }
                            val files = event.processedEntries
                            if (files % 10 == 0) {
                                val progress = 40 + minOf(files / 10, 50)
                                updateComponent(index, progress, false, "Extracting... ($files files)")
                            }
                        }
                    }
                    .build()
                    .extract()) {
                    is ArchiveExtractor.Result.Success -> Unit
                    is ArchiveExtractor.Result.Failure -> throw result.cause
                }

                if (_state.value.isCancelled) {
                    throw CancellationException("Installation cancelled by user")
                }

                // Detect version
                val runtimeVersion = when (runtimeType) {
                    RuntimeManager.RuntimeType.DOTNET ->
                        RuntimeManager.detectDotNetRuntimeVersion(stagingDir)
                    RuntimeManager.RuntimeType.BOX64 ->
                        throw IllegalStateException("Box64 extraction not configured")
                } ?: throw IllegalStateException("Failed to detect ${component.name} version")

                val installDir = RuntimeManager.getRuntimeInstallPath(runtimeType, runtimeVersion)
                val runtimeTypeDir = RuntimeManager.getRuntimeTypeRootPath(runtimeType)
                runtimeTypeDir.createDirectories()

                // Replace installation directory
                if (installDir.exists()) {
                    val deleted = deleteDirectoryRecursively(installDir.toFile(), runtimeTypeDir.toFile())
                    if (!deleted) {
                        throw IllegalStateException("Failed to replace runtime directory: $installDir")
                    }
                }

                stagingDir.moveTo(installDir)
                RuntimeManager.setSelectedRuntimeVersion(runtimeType, runtimeVersion)

                deleteFileSafely(tempFile, context.cacheDir)

                updateComponent(index, 100, true, "Installation complete")
                prefs.edit { putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, true)}
            }

            // Installation complete
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isExtracting = false,
                        isComplete = true,
                        statusMessage = "All components installed ✅",
                        overallProgress = 100
                    )
                }

                // Delay before navigation
                kotlinx.coroutines.delay(1500.milliseconds)
                onComplete()
            }

        } catch (_: CancellationException) {
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isExtracting = false,
                        isCancelled = false,
                        statusMessage = "Installation cancelled"
                    )
                }
            }
        } catch (_: SecurityException) {
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isExtracting = false,
                        errorMessage = "Manage external storage permission required\nPlease grant permission in settings and retry",
                        statusMessage = "Permission denied"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Installation failed")
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isExtracting = false,
                        errorMessage = e.message ?: "Installation failed, please retry",
                        statusMessage = "Installation failed"
                    )
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                _state.update { it.copy(isExtracting = false) }
                installationJob = null
            }
        }
    }

    private fun updateComponent(index: Int, progress: Int, installed: Boolean, status: String) {
        if (_state.value.isCancelled) return

        _state.update { current ->
            val updatedComponents = current.components.toMutableList()
            if (index in updatedComponents.indices) {
                updatedComponents[index] = updatedComponents[index].copy(
                    progress = progress,
                    isInstalled = installed,
                    status = status
                )
            }

            val total = if (updatedComponents.isNotEmpty()) {
                updatedComponents.sumOf { it.progress.coerceIn(0, 100) } / updatedComponents.size
            } else 0

            current.copy(
                components = updatedComponents,
                overallProgress = total,
                statusMessage = status
            )
        }
    }

    private fun deleteDirectoryRecursively(directory: File, rootPath: File): Boolean {
        // Security check
        if (!directory.absolutePath.startsWith(rootPath.absolutePath)) {
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
                        return false
                    }
                }
            }
        }

        return directory.delete()
    }

    private fun deleteFileSafely(file: File, rootPath: File): Boolean {
        if (!file.absolutePath.startsWith(rootPath.absolutePath)) {
            return false
        }
        return file.delete()
    }
}
