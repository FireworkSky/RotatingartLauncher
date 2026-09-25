package com.app.ralaunch.feature.init.vm

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.feature.init.model.ComponentState
import com.app.ralaunch.feature.init.model.InitStep
import com.app.ralaunch.feature.init.model.InitUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo

sealed class InitializationEffect {
    data class OpenUrl(val url: String) : InitializationEffect()
}

class InitializationViewModel(
    private val appContext: Context,
    private val prefs: SharedPreferences,
    private val runtimeManager: IRuntimeManagerServiceV2
) : ViewModel() {
    private val _uiState = MutableStateFlow(InitUiState())
    val uiState: StateFlow<InitUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<InitializationEffect>()
    val effect: SharedFlow<InitializationEffect> = _effect.asSharedFlow()

    val appVersionName: String = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
    }.getOrDefault("").ifBlank { "Unknown" }

    init {
        _uiState.value = InitUiState(
            step = if (prefs.getBoolean(AppConstants.InitKeys.LEGAL_AGREED, false)) {
                InitStep.PERMISSION
            } else {
                InitStep.LEGAL
            },
            components = listOf(
                ComponentState(
                    name = "dotnet",
                    description = appContext.getString(R.string.init_component_dotnet_desc),
                    fileName = "dotnet.tar.xz",
                    needsExtraction = true
                )
            )
        )
    }

    fun markPermissions(hasPermissions: Boolean) {
        _uiState.update {
            it.copy(
                hasPermissions = hasPermissions,
                step = if (it.step == InitStep.LEGAL) it.step else if (hasPermissions) InitStep.EXTRACTION else InitStep.PERMISSION
            )
        }
        if (hasPermissions) {
            prefs.edit().putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true).apply()
        }
    }

    fun acceptLegal() {
        prefs.edit().putBoolean(AppConstants.InitKeys.LEGAL_AGREED, true).apply()
        _uiState.update { it.copy(step = if (it.hasPermissions) InitStep.EXTRACTION else InitStep.PERMISSION) }
    }

    fun startExtraction() {
        val state = _uiState.value
        if (state.isExtracting || state.isComplete || !state.hasPermissions) return

        _uiState.update {
            it.copy(
                isExtracting = true,
                statusMessage = appContext.getString(R.string.init_preparing_file),
                error = null,
                step = InitStep.EXTRACTION
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                extractAll(_uiState.value.components)
                prefs.edit().putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, true).apply()
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        isComplete = true,
                        overallProgress = 100,
                        statusMessage = appContext.getString(R.string.init_complete)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        error = e.message ?: appContext.getString(R.string.error_message_default)
                    )
                }
            }
        }
    }

    fun openOfficialDownloadPage() {
        viewModelScope.launch {
            _effect.emit(
                InitializationEffect.OpenUrl("https://github.com/FireworkSky/RotatingartLauncher")
            )
        }
    }

    private suspend fun extractAll(components: List<ComponentState>) {
        components.forEachIndexed { index, component ->
            if (!component.needsExtraction) {
                updateComponent(index, 100, true, appContext.getString(R.string.init_no_extraction_needed))
                return@forEachIndexed
            }

            updateComponent(index, 10, false, appContext.getString(R.string.init_preparing_file))

            val tempFile = File(appContext.cacheDir, "temp_${component.fileName}")
            ArchiveExtractor.copyAssetToFile(appContext, component.fileName, tempFile)

            updateComponent(index, 30, false, appContext.getString(R.string.init_extracting))

            val runtimeType = IRuntimeManagerServiceV2.RuntimeType.fromDirName(component.name)
                ?: error("Unsupported runtime component: ${component.name}")
            val stagingRootDir = Path(appContext.cacheDir.absolutePath, "runtime-staging")
            val stagingDir = stagingRootDir.resolve(component.name)
            if (stagingDir.exists() &&
                !FileUtils.deleteDirectoryRecursivelyWithinRoot(stagingDir, stagingRootDir)
            ) {
                throw IllegalStateException("Failed to clear staging directory for ${component.name}")
            }
            stagingDir.createDirectories()

            val callback = ArchiveExtractor.ProgressCallback { files, _ ->
                val progress = 40 + minOf(files / 10, 50)
                updateComponent(
                    index,
                    progress,
                    false,
                    appContext.getString(R.string.init_extracting_files, files)
                )
            }

            when {
                component.fileName.endsWith(".tar.xz") ->
                    ArchiveExtractor.extractTarXz(tempFile, stagingDir.toFile(), null, callback)
                component.fileName.endsWith(".tar.gz") ->
                    ArchiveExtractor.extractTarGz(tempFile, stagingDir.toFile(), null, callback)
                else ->
                    ArchiveExtractor.extractTar(tempFile, stagingDir.toFile(), null, callback)
            }

            val runtimeVersion = when (runtimeType) {
                IRuntimeManagerServiceV2.RuntimeType.DOTNET -> runtimeManager.detectDotNetRuntimeVersion(stagingDir)
                IRuntimeManagerServiceV2.RuntimeType.BOX64 -> throw IllegalStateException("Box64 archive extraction is not configured")
            } ?: throw IllegalStateException("Failed to detect runtime version for ${component.name}")

            val installDir = runtimeManager.getRuntimeInstallPath(runtimeType, runtimeVersion)
            val runtimeTypeDir = runtimeManager.getRuntimeTypeRootPath(runtimeType)
            runtimeTypeDir.createDirectories()
            if (installDir.exists() &&
                !FileUtils.deleteDirectoryRecursivelyWithinRoot(installDir, runtimeTypeDir)
            ) {
                throw IllegalStateException("Failed to replace runtime directory: $installDir")
            }
            stagingDir.moveTo(installDir)
            runtimeManager.setSelectedRuntimeVersion(runtimeType, runtimeVersion)

            FileUtils.deleteFileWithinRoot(tempFile, appContext.cacheDir)
            updateComponent(index, 100, true, appContext.getString(R.string.init_complete))
        }
    }

    private fun updateComponent(index: Int, progress: Int, installed: Boolean, status: String) {
        _uiState.update { state ->
            val nextComponents = state.components.toMutableList()
            if (index in nextComponents.indices) {
                nextComponents[index] = nextComponents[index].copy(
                    progress = progress,
                    isInstalled = installed,
                    status = status
                )
            }
            val overall = if (nextComponents.isNotEmpty()) {
                nextComponents.sumOf { it.progress.coerceIn(0, 100) } / nextComponents.size
            } else {
                0
            }
            state.copy(
                components = nextComponents,
                overallProgress = overall,
                statusMessage = status
            )
        }
    }
}
