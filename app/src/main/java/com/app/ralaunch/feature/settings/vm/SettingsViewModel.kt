package com.app.ralaunch.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.logging.service.AndroidFileLogger
import com.app.ralaunch.core.platform.runtime.RendererRegistry
import com.app.ralaunch.core.model.BackgroundType
import com.app.ralaunch.core.model.FpsLimit
import com.app.ralaunch.core.model.QualityLevel
import com.app.ralaunch.core.model.ThemeMode
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.common.util.getAppString
import com.app.ralaunch.R
import com.app.ralaunch.feature.settings.ui.SettingsCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页面 UI 状态 - 跨平台
 */
data class SettingsUiState(
    val currentCategory: SettingsCategory? = SettingsCategory.APPEARANCE,

    // 外观设置
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val backgroundType: BackgroundType = BackgroundType.DEFAULT,
    val backgroundOpacity: Int = 0,
    val videoPlaybackSpeed: Float = 1.0f,
    val language: String = "auto",

    // 控制设置
    val touchMultitouchEnabled: Boolean = true,
    val mouseRightStickEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,
    val virtualControllerAsFirst: Boolean = false,

    // 游戏设置
    val bigCoreAffinityEnabled: Boolean = false,
    val lowLatencyAudioEnabled: Boolean = false,
    val ralAudioBufferSize: Int? = null,
    val rendererType: String = DEFAULT_RENDERER_ID,
    val selectedDotNetRuntimeVersion: String? = null,
    val installedDotNetRuntimeVersions: List<String> = emptyList(),

    // 启动器设置
    val multiplayerEnabled: Boolean = false,
    val multiplayerDisclaimerAccepted: Boolean = false,

    // 画质设置
    val qualityLevel: QualityLevel = QualityLevel.HIGH,
    val shaderLowPrecision: Boolean = false,
    val targetFps: FpsLimit = FpsLimit.UNLIMITED,

    // 开发者设置
    val loggingEnabled: Boolean = false,
    val verboseLogging: Boolean = false,
    val killLauncherUIEnabled: Boolean = false,
    val serverGCEnabled: Boolean = true,
    val concurrentGCEnabled: Boolean = true,
    val tieredCompilationEnabled: Boolean = true,
    val coreClrXiaomiCompatEnabled: Boolean = false,
    val fnaMapBufferRangeOptEnabled: Boolean = false,
    val fnaGlPerfDiagnosticsEnabled: Boolean = false,

    // 关于
    val appVersion: String = "",
    val buildInfo: String = ""
)

/**
 * 设置事件 - 跨平台
 */
sealed class SettingsEvent {
    data class SelectCategory(val category: SettingsCategory) : SettingsEvent()

    // 外观
    data class SetThemeMode(val mode: ThemeMode) : SettingsEvent()
    data class SetThemeColor(val color: Int) : SettingsEvent()
    data class SetBackgroundType(val type: BackgroundType) : SettingsEvent()
    data class SetBackgroundOpacity(val opacity: Int) : SettingsEvent()
    data class SetVideoPlaybackSpeed(val speed: Float) : SettingsEvent()
    data class SetLanguage(val language: String) : SettingsEvent()
    data object RestoreDefaultBackground : SettingsEvent()

    // 控制
    data class SetTouchMultitouch(val enabled: Boolean) : SettingsEvent()
    data class SetVibrationEnabled(val enabled: Boolean) : SettingsEvent()
    data class SetVibrationStrength(val strength: Float) : SettingsEvent()
    data class SetVirtualControllerAsFirst(val enabled: Boolean) : SettingsEvent()

    // 游戏
    data class SetBigCoreAffinity(val enabled: Boolean) : SettingsEvent()
    data class SetLowLatencyAudio(val enabled: Boolean) : SettingsEvent()
    data class SetRalAudioBufferSize(val size: Int?) : SettingsEvent()
    data class SetRenderer(val renderer: String) : SettingsEvent()
    data class SetDotNetRuntimeVersion(val version: String) : SettingsEvent()

    // 启动器
    data class SetMultiplayerEnabled(val enabled: Boolean) : SettingsEvent()
    data object AcceptMultiplayerDisclaimer : SettingsEvent()

    // 画质
    data class SetQualityLevel(val level: QualityLevel) : SettingsEvent()
    data class SetShaderLowPrecision(val enabled: Boolean) : SettingsEvent()
    data class SetTargetFps(val fps: FpsLimit) : SettingsEvent()

    // 开发者
    data class SetLoggingEnabled(val enabled: Boolean) : SettingsEvent()
    data class SetVerboseLogging(val enabled: Boolean) : SettingsEvent()
    data class SetKillLauncherUI(val enabled: Boolean) : SettingsEvent()
    data class SetServerGC(val enabled: Boolean) : SettingsEvent()
    data class SetConcurrentGC(val enabled: Boolean) : SettingsEvent()
    data class SetTieredCompilation(val enabled: Boolean) : SettingsEvent()
    data class SetCoreClrXiaomiCompat(val enabled: Boolean) : SettingsEvent()
    data class SetFnaMapBufferRangeOpt(val enabled: Boolean) : SettingsEvent()
    data class SetFnaGlPerfDiagnostics(val enabled: Boolean) : SettingsEvent()
}

/**
 * 设置副作用 - 跨平台
 */
sealed class SettingsEffect {
    data class ShowToast(val message: String) : SettingsEffect()
}

/**
 * 设置 ViewModel - 跨平台
 */
class SettingsViewModel(
    private val settingsRepository: ISettingsRepositoryServiceV2,
    private val runtimeManager: IRuntimeManagerServiceV2,
    private val fileLogger: AndroidFileLogger? = null,
    private val appInfo: AppInfo = AppInfo()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 16)
    val effect: SharedFlow<SettingsEffect> = _effect.asSharedFlow()

    init {
        loadSettings()
    }

    /**
     * 处理事件
     */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectCategory -> selectCategory(event.category)

            // 外观
            is SettingsEvent.SetThemeMode -> setThemeMode(event.mode)
            is SettingsEvent.SetThemeColor -> setThemeColor(event.color)
            is SettingsEvent.SetBackgroundType -> setBackgroundType(event.type)
            is SettingsEvent.SetBackgroundOpacity -> setBackgroundOpacity(event.opacity)
            is SettingsEvent.SetVideoPlaybackSpeed -> setVideoPlaybackSpeed(event.speed)
            is SettingsEvent.SetLanguage -> setLanguage(event.language)
            is SettingsEvent.RestoreDefaultBackground -> restoreDefaultBackground()

            // 控制
            is SettingsEvent.SetTouchMultitouch -> setTouchMultitouch(event.enabled)
            is SettingsEvent.SetVibrationEnabled -> setVibrationEnabled(event.enabled)
            is SettingsEvent.SetVibrationStrength -> setVibrationStrength(event.strength)
            is SettingsEvent.SetVirtualControllerAsFirst -> setVirtualControllerAsFirst(event.enabled)

            // 游戏
            is SettingsEvent.SetBigCoreAffinity -> setBigCoreAffinity(event.enabled)
            is SettingsEvent.SetLowLatencyAudio -> setLowLatencyAudio(event.enabled)
            is SettingsEvent.SetRalAudioBufferSize -> setRalAudioBufferSize(event.size)
            is SettingsEvent.SetRenderer -> setRenderer(event.renderer)
            is SettingsEvent.SetDotNetRuntimeVersion -> setDotNetRuntimeVersion(event.version)

            // 启动器
            is SettingsEvent.SetMultiplayerEnabled -> setMultiplayerEnabled(event.enabled)
            is SettingsEvent.AcceptMultiplayerDisclaimer -> acceptMultiplayerDisclaimer()

            // 画质
            is SettingsEvent.SetQualityLevel -> setQualityLevel(event.level)
            is SettingsEvent.SetShaderLowPrecision -> setShaderLowPrecision(event.enabled)
            is SettingsEvent.SetTargetFps -> setTargetFps(event.fps)

            // 开发者
            is SettingsEvent.SetLoggingEnabled -> setLoggingEnabled(event.enabled)
            is SettingsEvent.SetVerboseLogging -> setVerboseLogging(event.enabled)
            is SettingsEvent.SetKillLauncherUI -> setKillLauncherUI(event.enabled)
            is SettingsEvent.SetServerGC -> setServerGC(event.enabled)
            is SettingsEvent.SetConcurrentGC -> setConcurrentGC(event.enabled)
            is SettingsEvent.SetTieredCompilation -> setTieredCompilation(event.enabled)
            is SettingsEvent.SetCoreClrXiaomiCompat -> setCoreClrXiaomiCompat(event.enabled)
            is SettingsEvent.SetFnaMapBufferRangeOpt -> setFnaMapBufferRangeOpt(event.enabled)
            is SettingsEvent.SetFnaGlPerfDiagnostics -> setFnaGlPerfDiagnostics(event.enabled)
        }
    }

    private fun sendEffect(effect: SettingsEffect) {
        _effect.tryEmit(effect)
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettingsSnapshot()
            val installedDotNetRuntimeVersions = runtimeManager.getInstalledVersions(
                IRuntimeManagerServiceV2.RuntimeType.DOTNET
            )
            _uiState.update { state ->
                state.copy(
                    // 外观
                    themeMode = settings.themeMode,
                    themeColor = settings.themeColor,
                    backgroundType = settings.backgroundType,
                    backgroundOpacity = settings.backgroundOpacity,
                    videoPlaybackSpeed = settings.videoPlaybackSpeed,
                    language = settings.language,
                    // 控制
                    touchMultitouchEnabled = settings.touchMultitouchEnabled,
                    vibrationEnabled = settings.vibrationEnabled,
                    vibrationStrength = settings.virtualControllerVibrationIntensity,
                    virtualControllerAsFirst = settings.virtualControllerAsFirst,
                    // 游戏
                    bigCoreAffinityEnabled = settings.setThreadAffinityToBigCore,
                    lowLatencyAudioEnabled = settings.sdlAaudioLowLatency,
                    ralAudioBufferSize = normalizeRalAudioBufferSize(settings.ralAudioBufferSize),
                    rendererType = RendererRegistry.normalizeRendererId(settings.fnaRenderer),
                    selectedDotNetRuntimeVersion = settings.selectedDotnetRuntimeVersion
                        .trim()
                        .ifBlank { null },
                    installedDotNetRuntimeVersions = installedDotNetRuntimeVersions,
                    // 启动器
                    multiplayerEnabled = settings.multiplayerEnabled,
                    multiplayerDisclaimerAccepted = settings.multiplayerDisclaimerAccepted,
                    // 画质
                    qualityLevel = QualityLevel.fromValue(settings.qualityLevel),
                    shaderLowPrecision = settings.shaderLowPrecision,
                    targetFps = FpsLimit.fromValue(settings.targetFps),
                    // 开发者
                    loggingEnabled = settings.logSystemEnabled,
                    verboseLogging = settings.verboseLogging,
                    killLauncherUIEnabled = settings.killLauncherUIAfterLaunch,
                    serverGCEnabled = settings.serverGC,
                    concurrentGCEnabled = settings.concurrentGC,
                    tieredCompilationEnabled = settings.tieredCompilation,
                    coreClrXiaomiCompatEnabled = settings.coreClrXiaomiCompatEnabled,
                    fnaMapBufferRangeOptEnabled = settings.fnaMapBufferRangeOptimization,
                    fnaGlPerfDiagnosticsEnabled = settings.fnaGlPerfDiagnosticsEnabled,
                    // 关于
                    appVersion = appInfo.versionName,
                    buildInfo = appInfo.versionCode.toString()
                )
            }
        }
    }

    // ==================== 分类选择 ====================

    private fun selectCategory(category: SettingsCategory) {
        _uiState.update { it.copy(currentCategory = category) }
    }

    // ==================== 外观设置 ====================

    private fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.update { themeMode = mode }
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    private fun setThemeColor(color: Int) {
        viewModelScope.launch {
            settingsRepository.update { themeColor = color }
            _uiState.update { it.copy(themeColor = color) }
        }
    }

    private fun setBackgroundType(type: BackgroundType) {
        viewModelScope.launch {
            settingsRepository.update { backgroundType = type }
            _uiState.update { it.copy(backgroundType = type) }
        }
    }

    private fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.update { this.language = language }
            _uiState.update { it.copy(language = language) }
        }
    }

    private fun setBackgroundOpacity(opacity: Int) {
        viewModelScope.launch {
            settingsRepository.update { backgroundOpacity = opacity }
            _uiState.update { it.copy(backgroundOpacity = opacity) }
        }
    }

    private fun setVideoPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.update { videoPlaybackSpeed = speed }
            _uiState.update { it.copy(videoPlaybackSpeed = speed) }
        }
    }

    private fun restoreDefaultBackground() {
        viewModelScope.launch {
            settingsRepository.update {
                backgroundType = BackgroundType.DEFAULT
                backgroundImagePath = ""
                backgroundVideoPath = ""
                backgroundOpacity = 0
                videoPlaybackSpeed = 1.0f
            }
            _uiState.update { it.copy(
                backgroundType = BackgroundType.DEFAULT,
                backgroundOpacity = 0,
                videoPlaybackSpeed = 1.0f
            ) }
        }
    }

    // ==================== 控制设置 ====================

    private fun setTouchMultitouch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { touchMultitouchEnabled = enabled }
            _uiState.update { it.copy(touchMultitouchEnabled = enabled) }
        }
    }

    private fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { vibrationEnabled = enabled }
            _uiState.update { it.copy(vibrationEnabled = enabled) }
        }
    }

    private fun setVibrationStrength(strength: Float) {
        viewModelScope.launch {
            settingsRepository.update { virtualControllerVibrationIntensity = strength }
            _uiState.update { it.copy(vibrationStrength = strength) }
        }
    }

    private fun setVirtualControllerAsFirst(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { virtualControllerAsFirst = enabled }
            _uiState.update { it.copy(virtualControllerAsFirst = enabled) }
        }
    }

    // ==================== 游戏设置 ====================

    private fun setBigCoreAffinity(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { setThreadAffinityToBigCore = enabled }
            _uiState.update { it.copy(bigCoreAffinityEnabled = enabled) }
        }
    }

    private fun setLowLatencyAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { sdlAaudioLowLatency = enabled }
            _uiState.update { it.copy(lowLatencyAudioEnabled = enabled) }
        }
    }

    private fun setRalAudioBufferSize(size: Int?) {
        val normalized = normalizeRalAudioBufferSize(size)
        viewModelScope.launch {
            settingsRepository.update { ralAudioBufferSize = normalized }
            _uiState.update { it.copy(ralAudioBufferSize = normalized) }
        }
    }

    private fun normalizeRalAudioBufferSize(value: Int?): Int? {
        if (value == null) return null
        if (value < 16 || value > 1024) return null
        return if ((value and (value - 1)) == 0) value else null
    }

    private fun setRenderer(renderer: String) {
        val normalized = RendererRegistry.normalizeRendererId(renderer)
        viewModelScope.launch {
            settingsRepository.update { fnaRenderer = normalized }
            _uiState.update { it.copy(rendererType = normalized) }
        }
    }

    private fun setDotNetRuntimeVersion(version: String) {
        val normalized = version.trim()
        if (normalized.isEmpty()) return

        viewModelScope.launch {
            settingsRepository.update { selectedDotnetRuntimeVersion = normalized }
            _uiState.update { it.copy(selectedDotNetRuntimeVersion = normalized) }
            sendEffect(
                SettingsEffect.ShowToast(
                    getAppString(R.string.main_runtime_switched, normalized)
                )
            )
        }
    }

    // ==================== 启动器设置 ====================

    private fun setMultiplayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { multiplayerEnabled = enabled }
            _uiState.update { it.copy(multiplayerEnabled = enabled) }
        }
    }

    private fun acceptMultiplayerDisclaimer() {
        viewModelScope.launch {
            settingsRepository.update {
                multiplayerDisclaimerAccepted = true
                multiplayerEnabled = true
            }
            _uiState.update {
                it.copy(
                    multiplayerDisclaimerAccepted = true,
                    multiplayerEnabled = true
                )
            }
        }
    }

    // ==================== 画质设置 ====================

    private fun setQualityLevel(level: QualityLevel) {
        viewModelScope.launch {
            settingsRepository.update { qualityLevel = level.value }
            _uiState.update { it.copy(qualityLevel = level) }
            val qualityName = when (level) {
                QualityLevel.HIGH -> getAppString(R.string.settings_quality_high)
                QualityLevel.MEDIUM -> getAppString(R.string.settings_quality_medium)
                QualityLevel.LOW -> getAppString(R.string.settings_quality_low)
            }
            sendEffect(
                SettingsEffect.ShowToast(
                    getAppString(R.string.settings_quality_applied_toast, qualityName)
                )
            )
        }
    }

    private fun setShaderLowPrecision(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { shaderLowPrecision = enabled }
            _uiState.update { it.copy(shaderLowPrecision = enabled) }
            sendEffect(SettingsEffect.ShowToast(getAppString(R.string.settings_restart_required_toast)))
        }
    }

    private fun setTargetFps(fps: FpsLimit) {
        viewModelScope.launch {
            settingsRepository.update { targetFps = fps.value }
            _uiState.update { it.copy(targetFps = fps) }
            val fpsName = when (fps) {
                FpsLimit.UNLIMITED -> getAppString(R.string.settings_fps_unlimited)
                FpsLimit.FPS_30 -> getAppString(R.string.settings_fps_30)
                FpsLimit.FPS_45 -> getAppString(R.string.settings_fps_45)
                FpsLimit.FPS_60 -> getAppString(R.string.settings_fps_60)
            }
            sendEffect(
                SettingsEffect.ShowToast(
                    getAppString(R.string.settings_fps_limit_applied_toast, fpsName)
                )
            )
        }
    }

    // ==================== 开发者设置 ====================

    private fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { logSystemEnabled = enabled }
            fileLogger?.refreshConfiguration()
            _uiState.update { it.copy(loggingEnabled = enabled) }
        }
    }

    private fun setVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { verboseLogging = enabled }
            fileLogger?.refreshConfiguration()
            _uiState.update { it.copy(verboseLogging = enabled) }
        }
    }

    private fun setKillLauncherUI(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { killLauncherUIAfterLaunch = enabled }
            _uiState.update { it.copy(killLauncherUIEnabled = enabled) }
        }
    }

    private fun setServerGC(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { serverGC = enabled }
            _uiState.update { it.copy(serverGCEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast(getAppString(R.string.settings_restart_required_toast)))
        }
    }

    private fun setConcurrentGC(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { concurrentGC = enabled }
            _uiState.update { it.copy(concurrentGCEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast(getAppString(R.string.settings_restart_required_toast)))
        }
    }

    private fun setTieredCompilation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { tieredCompilation = enabled }
            _uiState.update { it.copy(tieredCompilationEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast(getAppString(R.string.settings_restart_required_toast)))
        }
    }

    private fun setCoreClrXiaomiCompat(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { coreClrXiaomiCompatEnabled = enabled }
            _uiState.update { it.copy(coreClrXiaomiCompatEnabled = enabled) }
        }
    }

    private fun setFnaMapBufferRangeOpt(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { fnaMapBufferRangeOptimization = enabled }
            _uiState.update { it.copy(fnaMapBufferRangeOptEnabled = enabled) }
        }
    }

    private fun setFnaGlPerfDiagnostics(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { fnaGlPerfDiagnosticsEnabled = enabled }
            _uiState.update { it.copy(fnaGlPerfDiagnosticsEnabled = enabled) }
        }
    }

}

private const val DEFAULT_RENDERER_ID = "native"

/**
 * 应用信息 - 由平台提供
 */
data class AppInfo(
    val versionName: String = "Unknown",
    val versionCode: Long = 0
)
