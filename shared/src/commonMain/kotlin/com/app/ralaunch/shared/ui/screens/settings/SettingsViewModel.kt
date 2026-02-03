package com.app.ralaunch.shared.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.shared.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
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
    val themeMode: Int = 0,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val backgroundType: Int = 0,
    val backgroundOpacity: Int = 0,
    val videoPlaybackSpeed: Float = 1.0f,
    val language: String = "简体中文",

    // 控制设置
    val touchMultitouchEnabled: Boolean = true,
    val mouseRightStickEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,

    // 游戏设置
    val bigCoreAffinityEnabled: Boolean = false,
    val lowLatencyAudioEnabled: Boolean = false,
    val rendererType: String = "OpenGL ES",

    // 画质设置
    val qualityLevel: Int = 0, // 0=高, 1=中, 2=低
    val shaderLowPrecision: Boolean = false,
    val targetFps: Int = 0, // 0=无限制, 30, 45, 60

    // 开发者设置
    val loggingEnabled: Boolean = false,
    val verboseLogging: Boolean = false,
    val killLauncherUIEnabled: Boolean = false,
    val serverGCEnabled: Boolean = true,
    val concurrentGCEnabled: Boolean = true,
    val tieredCompilationEnabled: Boolean = true,
    val fnaMapBufferRangeOptEnabled: Boolean = false,

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
    data class SetThemeMode(val mode: Int) : SettingsEvent()
    data class SetThemeColor(val color: Int) : SettingsEvent()
    data class SetBackgroundType(val type: Int) : SettingsEvent()
    data class SetBackgroundOpacity(val opacity: Int) : SettingsEvent()
    data class SetVideoPlaybackSpeed(val speed: Float) : SettingsEvent()
    data class SetLanguage(val language: String) : SettingsEvent()
    data object RestoreDefaultBackground : SettingsEvent()

    // 控制
    data class SetTouchMultitouch(val enabled: Boolean) : SettingsEvent()
    data class SetMouseRightStick(val enabled: Boolean) : SettingsEvent()
    data class SetVibrationEnabled(val enabled: Boolean) : SettingsEvent()
    data class SetVibrationStrength(val strength: Float) : SettingsEvent()

    // 游戏
    data class SetBigCoreAffinity(val enabled: Boolean) : SettingsEvent()
    data class SetLowLatencyAudio(val enabled: Boolean) : SettingsEvent()
    data class SetRenderer(val renderer: String) : SettingsEvent()
    
    // 画质
    data class SetQualityLevel(val level: Int) : SettingsEvent()
    data class SetShaderLowPrecision(val enabled: Boolean) : SettingsEvent()
    data class SetTargetFps(val fps: Int) : SettingsEvent()

    // 启动器
    data object OpenPatchManagement : SettingsEvent()

    // 开发者
    data class SetLoggingEnabled(val enabled: Boolean) : SettingsEvent()
    data class SetVerboseLogging(val enabled: Boolean) : SettingsEvent()
    data class SetKillLauncherUI(val enabled: Boolean) : SettingsEvent()
    data class SetServerGC(val enabled: Boolean) : SettingsEvent()
    data class SetConcurrentGC(val enabled: Boolean) : SettingsEvent()
    data class SetTieredCompilation(val enabled: Boolean) : SettingsEvent()
    data class SetFnaMapBufferRangeOpt(val enabled: Boolean) : SettingsEvent()
    data object ForceReinstallPatches : SettingsEvent()

    // 操作
    data object ViewLogs : SettingsEvent()
    data object ClearCache : SettingsEvent()
    data object ExportLogs : SettingsEvent()
    data object OpenLicense : SettingsEvent()
    data object CheckUpdate : SettingsEvent()
    data object SelectBackgroundImage : SettingsEvent()
    data object SelectBackgroundVideo : SettingsEvent()
    data object OpenLanguageSelector : SettingsEvent()
    data object OpenThemeColorSelector : SettingsEvent()
    data object OpenRendererSelector : SettingsEvent()
    data object OpenSponsors : SettingsEvent()
    data class OpenUrl(val url: String) : SettingsEvent()
}

/**
 * 设置副作用 - 跨平台
 */
sealed class SettingsEffect {
    data class ShowToast(val message: String) : SettingsEffect()
    data object OpenImagePicker : SettingsEffect()
    data object OpenVideoPicker : SettingsEffect()
    data object OpenLanguageDialog : SettingsEffect()
    data object OpenThemeColorDialog : SettingsEffect()
    data object OpenRendererDialog : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
    data object OpenLicensePage : SettingsEffect()
    data object OpenSponsorsPage : SettingsEffect()
    data object ExportLogsToFile : SettingsEffect()
    data object ViewLogsPage : SettingsEffect()
    data object ClearCacheComplete : SettingsEffect()
    data object ForceReinstallPatchesComplete : SettingsEffect()
    data class BackgroundOpacityChanged(val opacity: Int) : SettingsEffect()
    data class VideoSpeedChanged(val speed: Float) : SettingsEffect()
    data object RestoreDefaultBackgroundComplete : SettingsEffect()
    data object OpenPatchManagementDialog : SettingsEffect()
}

/**
 * 设置 ViewModel - 跨平台
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appInfo: AppInfo = AppInfo()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effect = MutableStateFlow<SettingsEffect?>(null)
    val effect: StateFlow<SettingsEffect?> = _effect.asStateFlow()

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
            is SettingsEvent.SelectBackgroundImage -> sendEffect(SettingsEffect.OpenImagePicker)
            is SettingsEvent.SelectBackgroundVideo -> sendEffect(SettingsEffect.OpenVideoPicker)
            is SettingsEvent.OpenLanguageSelector -> sendEffect(SettingsEffect.OpenLanguageDialog)
            is SettingsEvent.OpenThemeColorSelector -> sendEffect(SettingsEffect.OpenThemeColorDialog)

            // 控制
            is SettingsEvent.SetTouchMultitouch -> setTouchMultitouch(event.enabled)
            is SettingsEvent.SetMouseRightStick -> setMouseRightStick(event.enabled)
            is SettingsEvent.SetVibrationEnabled -> setVibrationEnabled(event.enabled)
            is SettingsEvent.SetVibrationStrength -> setVibrationStrength(event.strength)

            // 游戏
            is SettingsEvent.SetBigCoreAffinity -> setBigCoreAffinity(event.enabled)
            is SettingsEvent.SetLowLatencyAudio -> setLowLatencyAudio(event.enabled)
            is SettingsEvent.SetRenderer -> setRenderer(event.renderer)
            is SettingsEvent.OpenRendererSelector -> sendEffect(SettingsEffect.OpenRendererDialog)
            
            // 画质
            is SettingsEvent.SetQualityLevel -> setQualityLevel(event.level)
            is SettingsEvent.SetShaderLowPrecision -> setShaderLowPrecision(event.enabled)
            is SettingsEvent.SetTargetFps -> setTargetFps(event.fps)

            // 启动器
            is SettingsEvent.OpenPatchManagement -> sendEffect(SettingsEffect.OpenPatchManagementDialog)

            // 开发者
            is SettingsEvent.SetLoggingEnabled -> setLoggingEnabled(event.enabled)
            is SettingsEvent.SetVerboseLogging -> setVerboseLogging(event.enabled)
            is SettingsEvent.SetKillLauncherUI -> setKillLauncherUI(event.enabled)
            is SettingsEvent.SetServerGC -> setServerGC(event.enabled)
            is SettingsEvent.SetConcurrentGC -> setConcurrentGC(event.enabled)
            is SettingsEvent.SetTieredCompilation -> setTieredCompilation(event.enabled)
            is SettingsEvent.SetFnaMapBufferRangeOpt -> setFnaMapBufferRangeOpt(event.enabled)
            is SettingsEvent.ViewLogs -> sendEffect(SettingsEffect.ViewLogsPage)
            is SettingsEvent.ClearCache -> clearCache()
            is SettingsEvent.ExportLogs -> sendEffect(SettingsEffect.ExportLogsToFile)
            is SettingsEvent.ForceReinstallPatches -> forceReinstallPatches()

            // 关于
            is SettingsEvent.OpenLicense -> sendEffect(SettingsEffect.OpenLicensePage)
            is SettingsEvent.OpenSponsors -> sendEffect(SettingsEffect.OpenSponsorsPage)
            is SettingsEvent.OpenUrl -> sendEffect(SettingsEffect.OpenUrl(event.url))
            is SettingsEvent.CheckUpdate -> checkUpdate()
        }
    }

    fun clearEffect() {
        _effect.value = null
    }

    private fun sendEffect(effect: SettingsEffect) {
        _effect.value = effect
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    // 外观
                    themeMode = settingsRepository.getThemeMode(),
                    themeColor = settingsRepository.getThemeColor(),
                    backgroundType = settingsRepository.getBackgroundType(),
                    backgroundOpacity = settingsRepository.getBackgroundOpacity(),
                    videoPlaybackSpeed = settingsRepository.getVideoPlaybackSpeed(),
                    language = settingsRepository.getLanguage(),
                    // 控制
                    touchMultitouchEnabled = settingsRepository.isTouchMultitouchEnabled(),
                    mouseRightStickEnabled = settingsRepository.isMouseRightStickEnabled(),
                    vibrationEnabled = settingsRepository.isVibrationEnabled(),
                    vibrationStrength = settingsRepository.getVibrationStrength(),
                    // 游戏
                    bigCoreAffinityEnabled = settingsRepository.isBigCoreAffinityEnabled(),
                    lowLatencyAudioEnabled = settingsRepository.isLowLatencyAudioEnabled(),
                    rendererType = settingsRepository.getRendererType(),
                    // 开发者
                    loggingEnabled = settingsRepository.isLoggingEnabled(),
                    verboseLogging = settingsRepository.isVerboseLogging(),
                    killLauncherUIEnabled = settingsRepository.isKillLauncherUIEnabled(),
                    serverGCEnabled = settingsRepository.isServerGCEnabled(),
                    concurrentGCEnabled = settingsRepository.isConcurrentGCEnabled(),
                    tieredCompilationEnabled = settingsRepository.isTieredCompilationEnabled(),
                    fnaMapBufferRangeOptEnabled = settingsRepository.isFnaMapBufferRangeOptEnabled(),
                    // 关于
                    appVersion = appInfo.versionName,
                    buildInfo = "Build ${appInfo.versionCode}"
                )
            }
        }
    }

    // ==================== 分类选择 ====================

    private fun selectCategory(category: SettingsCategory) {
        _uiState.update { it.copy(currentCategory = category) }
    }

    // ==================== 外观设置 ====================

    private fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    private fun setThemeColor(color: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeColor(color)
            _uiState.update { it.copy(themeColor = color) }
        }
    }

    private fun setBackgroundType(type: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundType(type)
            _uiState.update { it.copy(backgroundType = type) }
        }
    }

    private fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            _uiState.update { it.copy(language = language) }
        }
    }

    private fun setBackgroundOpacity(opacity: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundOpacity(opacity)
            _uiState.update { it.copy(backgroundOpacity = opacity) }
            sendEffect(SettingsEffect.BackgroundOpacityChanged(opacity))
        }
    }

    private fun setVideoPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setVideoPlaybackSpeed(speed)
            _uiState.update { it.copy(videoPlaybackSpeed = speed) }
            sendEffect(SettingsEffect.VideoSpeedChanged(speed))
        }
    }

    private fun restoreDefaultBackground() {
        viewModelScope.launch {
            settingsRepository.setBackgroundType(0)
            settingsRepository.setBackgroundOpacity(0)
            settingsRepository.setVideoPlaybackSpeed(1.0f)
            _uiState.update { it.copy(
                backgroundType = 0,
                backgroundOpacity = 0,
                videoPlaybackSpeed = 1.0f
            ) }
            sendEffect(SettingsEffect.RestoreDefaultBackgroundComplete)
            sendEffect(SettingsEffect.ShowToast("背景已恢复默认"))
        }
    }

    // ==================== 控制设置 ====================

    private fun setTouchMultitouch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTouchMultitouchEnabled(enabled)
            _uiState.update { it.copy(touchMultitouchEnabled = enabled) }
        }
    }

    private fun setMouseRightStick(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMouseRightStickEnabled(enabled)
            _uiState.update { it.copy(mouseRightStickEnabled = enabled) }
        }
    }

    private fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrationEnabled(enabled)
            _uiState.update { it.copy(vibrationEnabled = enabled) }
        }
    }

    private fun setVibrationStrength(strength: Float) {
        viewModelScope.launch {
            settingsRepository.setVibrationStrength(strength)
            _uiState.update { it.copy(vibrationStrength = strength) }
        }
    }

    // ==================== 游戏设置 ====================

    private fun setBigCoreAffinity(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBigCoreAffinityEnabled(enabled)
            _uiState.update { it.copy(bigCoreAffinityEnabled = enabled) }
        }
    }

    private fun setLowLatencyAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLowLatencyAudioEnabled(enabled)
            _uiState.update { it.copy(lowLatencyAudioEnabled = enabled) }
        }
    }

    private fun setRenderer(renderer: String) {
        viewModelScope.launch {
            settingsRepository.setRendererType(renderer)
            _uiState.update { it.copy(rendererType = renderer) }
        }
    }

    // ==================== 画质设置 ====================

    private fun setQualityLevel(level: Int) {
        viewModelScope.launch {
            settingsRepository.setQualityLevel(level)
            _uiState.update { it.copy(qualityLevel = level) }
            val qualityName = when (level) {
                0 -> "高画质"
                1 -> "中画质"
                2 -> "低画质"
                else -> "高画质"
            }
            sendEffect(SettingsEffect.ShowToast("已设置为${qualityName}，重启游戏后生效"))
        }
    }

    private fun setShaderLowPrecision(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShaderLowPrecision(enabled)
            _uiState.update { it.copy(shaderLowPrecision = enabled) }
            sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
        }
    }

    private fun setTargetFps(fps: Int) {
        viewModelScope.launch {
            settingsRepository.setTargetFps(fps)
            _uiState.update { it.copy(targetFps = fps) }
            val fpsName = if (fps == 0) "无限制" else "$fps FPS"
            sendEffect(SettingsEffect.ShowToast("帧率限制已设置为${fpsName}，重启游戏后生效"))
        }
    }

    // ==================== 开发者设置 ====================

    private fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLoggingEnabled(enabled)
            _uiState.update { it.copy(loggingEnabled = enabled) }
        }
    }

    private fun setVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVerboseLogging(enabled)
            _uiState.update { it.copy(verboseLogging = enabled) }
        }
    }

    private fun setKillLauncherUI(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKillLauncherUIEnabled(enabled)
            _uiState.update { it.copy(killLauncherUIEnabled = enabled) }
        }
    }

    private fun setServerGC(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setServerGCEnabled(enabled)
            _uiState.update { it.copy(serverGCEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
        }
    }

    private fun setConcurrentGC(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setConcurrentGCEnabled(enabled)
            _uiState.update { it.copy(concurrentGCEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
        }
    }

    private fun setTieredCompilation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTieredCompilationEnabled(enabled)
            _uiState.update { it.copy(tieredCompilationEnabled = enabled) }
            sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
        }
    }

    private fun setFnaMapBufferRangeOpt(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFnaMapBufferRangeOptEnabled(enabled)
            _uiState.update { it.copy(fnaMapBufferRangeOptEnabled = enabled) }
        }
    }

    private fun clearCache() {
        viewModelScope.launch {
            sendEffect(SettingsEffect.ClearCacheComplete)
            sendEffect(SettingsEffect.ShowToast("缓存已清除"))
        }
    }

    private fun forceReinstallPatches() {
        viewModelScope.launch {
            sendEffect(SettingsEffect.ForceReinstallPatchesComplete)
        }
    }

    private fun checkUpdate() {
        sendEffect(SettingsEffect.ShowToast("正在检查更新..."))
    }
}

/**
 * 应用信息 - 由平台提供
 */
data class AppInfo(
    val versionName: String = "Unknown",
    val versionCode: Long = 0
)
