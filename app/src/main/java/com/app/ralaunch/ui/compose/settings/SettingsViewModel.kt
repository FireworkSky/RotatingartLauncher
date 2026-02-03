package com.app.ralaunch.ui.compose.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.shared.ui.screens.settings.SettingsCategory
import com.app.ralaunch.shared.ui.screens.settings.SettingsUiState
import com.app.ralaunch.shared.ui.screens.settings.SettingsEvent
import com.app.ralaunch.shared.ui.screens.settings.SettingsEffect
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.utils.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置 ViewModel - App 层实现
 * 
 * 使用 SettingsManager 适配旧版设置存储
 */
class SettingsViewModel(
    private val context: Context
) : ViewModel() {

    private val settingsManager = SettingsManager.getInstance()

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
            
            // 画质
            is SettingsEvent.SetQualityLevel -> setQualityLevel(event.level)
            is SettingsEvent.SetShaderLowPrecision -> setShaderLowPrecision(event.enabled)
            is SettingsEvent.SetTargetFps -> setTargetFps(event.fps)
            
            is SettingsEvent.ViewLogs -> sendEffect(SettingsEffect.ViewLogsPage)
            is SettingsEvent.ClearCache -> sendEffect(SettingsEffect.ClearCacheComplete)
            is SettingsEvent.ExportLogs -> sendEffect(SettingsEffect.ExportLogsToFile)
            is SettingsEvent.ForceReinstallPatches -> sendEffect(SettingsEffect.ForceReinstallPatchesComplete)

            // 关于
            is SettingsEvent.OpenLicense -> sendEffect(SettingsEffect.OpenLicensePage)
            is SettingsEvent.OpenSponsors -> sendEffect(SettingsEffect.OpenSponsorsPage)
            is SettingsEvent.OpenUrl -> sendEffect(SettingsEffect.OpenUrl(event.url))
            is SettingsEvent.CheckUpdate -> sendEffect(SettingsEffect.ShowToast("正在检查更新..."))
        }
    }

    fun clearEffect() {
        _effect.value = null
    }

    private fun sendEffect(effect: SettingsEffect) {
        _effect.value = effect
    }

    /**
     * 加载所有设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val languageCode = LocaleManager.getLanguage(context)
            val languageName = LocaleManager.getLanguageDisplayName(languageCode)

            _uiState.update { state ->
                state.copy(
                    // 外观
                    themeMode = settingsManager.themeMode,
                    themeColor = settingsManager.themeColor,
                    backgroundType = parseBackgroundType(settingsManager.backgroundType),
                    backgroundOpacity = settingsManager.backgroundOpacity,
                    videoPlaybackSpeed = settingsManager.videoPlaybackSpeed,
                    language = languageName,

                    // 控制
                    touchMultitouchEnabled = settingsManager.isTouchMultitouchEnabled,
                    mouseRightStickEnabled = settingsManager.isMouseRightStickEnabled,
                    vibrationEnabled = settingsManager.vibrationEnabled,
                    vibrationStrength = settingsManager.virtualControllerVibrationIntensity,

                    // 游戏
                    bigCoreAffinityEnabled = settingsManager.setThreadAffinityToBigCoreEnabled,
                    lowLatencyAudioEnabled = settingsManager.isSdlAaudioLowLatency,
                    rendererType = getRendererDisplayName(settingsManager.fnaRenderer),

                    // 开发者
                    loggingEnabled = settingsManager.isLogSystemEnabled,
                    verboseLogging = settingsManager.isVerboseLogging,
                    killLauncherUIEnabled = settingsManager.isKillLauncherUIAfterLaunch,
                    serverGCEnabled = settingsManager.isServerGC,
                    concurrentGCEnabled = settingsManager.isConcurrentGC,
                    tieredCompilationEnabled = settingsManager.isTieredCompilation,
                    fnaMapBufferRangeOptEnabled = settingsManager.isFnaEnableMapBufferRangeOptimization,
                    
                    // 画质
                    qualityLevel = settingsManager.fnaQualityLevel,
                    shaderLowPrecision = settingsManager.isFnaShaderLowPrecision,
                    targetFps = settingsManager.fnaTargetFps,

                    // 关于
                    appVersion = packageInfo?.versionName ?: "Unknown",
                    buildInfo = "Build ${packageInfo?.longVersionCode ?: 0}"
                )
            }
        }
    }

    // ==================== 分类选择 ====================

    fun selectCategory(category: SettingsCategory) {
        _uiState.update { it.copy(currentCategory = category) }
    }

    // ==================== 外观设置 ====================

    fun setThemeMode(mode: Int) {
        settingsManager.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
        // 实时更新全局主题状态
        AppThemeState.updateThemeMode(mode)
    }

    private fun setThemeColor(color: Int) {
        settingsManager.themeColor = color
        _uiState.update { it.copy(themeColor = color) }
        // 实时更新全局主题状态
        AppThemeState.updateThemeColor(color)
    }

    fun setBackgroundType(type: Int) {
        val typeString = when (type) {
            0 -> "default"
            1 -> "image"
            2 -> "video"
            else -> "default"
        }
        settingsManager.backgroundType = typeString
        _uiState.update { it.copy(backgroundType = type) }
        // 实时更新全局主题状态
        AppThemeState.updateBackgroundType(type)
    }

    private fun setBackgroundOpacity(opacity: Int) {
        settingsManager.backgroundOpacity = opacity
        _uiState.update { it.copy(backgroundOpacity = opacity) }
        // 实时更新全局主题状态
        AppThemeState.updateBackgroundOpacity(opacity)
        sendEffect(SettingsEffect.BackgroundOpacityChanged(opacity))
    }

    private fun setVideoPlaybackSpeed(speed: Float) {
        settingsManager.videoPlaybackSpeed = speed
        _uiState.update { it.copy(videoPlaybackSpeed = speed) }
        // 实时更新全局主题状态
        AppThemeState.updateVideoPlaybackSpeed(speed)
        sendEffect(SettingsEffect.VideoSpeedChanged(speed))
    }

    private fun setLanguage(languageCode: String) {
        val languageName = LocaleManager.getLanguageDisplayName(languageCode)
        _uiState.update { it.copy(language = languageName) }
    }

    private fun restoreDefaultBackground() {
        settingsManager.backgroundType = "default"
        settingsManager.backgroundImagePath = ""
        settingsManager.backgroundVideoPath = ""
        settingsManager.backgroundOpacity = 0
        settingsManager.videoPlaybackSpeed = 1.0f
        
        _uiState.update { it.copy(
            backgroundType = 0,
            backgroundOpacity = 0,
            videoPlaybackSpeed = 1.0f
        ) }
        
        // 实时更新全局主题状态 - 恢复默认
        AppThemeState.restoreDefaultBackground()
        
        sendEffect(SettingsEffect.RestoreDefaultBackgroundComplete)
    }

    // ==================== 控制设置 ====================

    fun setTouchMultitouch(enabled: Boolean) {
        // TODO: 等待 SettingsManager 添加 setter 方法
        _uiState.update { it.copy(touchMultitouchEnabled = enabled) }
    }

    fun setMouseRightStick(enabled: Boolean) {
        // TODO: 等待 SettingsManager 添加 setter 方法
        _uiState.update { it.copy(mouseRightStickEnabled = enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        settingsManager.vibrationEnabled = enabled
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setVibrationStrength(strength: Float) {
        settingsManager.virtualControllerVibrationIntensity = strength
        _uiState.update { it.copy(vibrationStrength = strength) }
    }

    // ==================== 游戏设置 ====================

    fun setBigCoreAffinity(enabled: Boolean) {
        settingsManager.setThreadAffinityToBigCoreEnabled = enabled
        _uiState.update { it.copy(bigCoreAffinityEnabled = enabled) }
    }

    fun setLowLatencyAudio(enabled: Boolean) {
        settingsManager.isSdlAaudioLowLatency = enabled
        _uiState.update { it.copy(lowLatencyAudioEnabled = enabled) }
    }

    private fun setRenderer(rendererId: String) {
        settingsManager.fnaRenderer = rendererId
        _uiState.update { it.copy(rendererType = getRendererDisplayName(rendererId)) }
    }

    // ==================== 开发者设置 ====================

    fun setLoggingEnabled(enabled: Boolean) {
        settingsManager.isLogSystemEnabled = enabled
        _uiState.update { it.copy(loggingEnabled = enabled) }
    }

    private fun setVerboseLogging(enabled: Boolean) {
        settingsManager.isVerboseLogging = enabled
        _uiState.update { it.copy(verboseLogging = enabled) }
    }

    private fun setKillLauncherUI(enabled: Boolean) {
        settingsManager.isKillLauncherUIAfterLaunch = enabled
        _uiState.update { it.copy(killLauncherUIEnabled = enabled) }
    }

    private fun setServerGC(enabled: Boolean) {
        settingsManager.isServerGC = enabled
        _uiState.update { it.copy(serverGCEnabled = enabled) }
        sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
    }

    private fun setConcurrentGC(enabled: Boolean) {
        settingsManager.isConcurrentGC = enabled
        _uiState.update { it.copy(concurrentGCEnabled = enabled) }
        sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
    }

    private fun setTieredCompilation(enabled: Boolean) {
        settingsManager.isTieredCompilation = enabled
        _uiState.update { it.copy(tieredCompilationEnabled = enabled) }
        sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
    }

    private fun setFnaMapBufferRangeOpt(enabled: Boolean) {
        settingsManager.isFnaEnableMapBufferRangeOptimization = enabled
        _uiState.update { it.copy(fnaMapBufferRangeOptEnabled = enabled) }
    }

    // ==================== 画质设置 ====================

    private fun setQualityLevel(level: Int) {
        settingsManager.fnaQualityLevel = level
        _uiState.update { it.copy(qualityLevel = level) }
        val qualityName = when (level) {
            0 -> "高画质"
            1 -> "中画质"
            2 -> "低画质"
            else -> "高画质"
        }
        sendEffect(SettingsEffect.ShowToast("已设置为${qualityName}，重启游戏后生效"))
    }

    private fun setShaderLowPrecision(enabled: Boolean) {
        settingsManager.isFnaShaderLowPrecision = enabled
        _uiState.update { it.copy(shaderLowPrecision = enabled) }
        sendEffect(SettingsEffect.ShowToast("重启游戏后生效"))
    }

    private fun setTargetFps(fps: Int) {
        settingsManager.fnaTargetFps = fps
        _uiState.update { it.copy(targetFps = fps) }
        val fpsName = when (fps) {
            0 -> "无限制"
            else -> "$fps FPS"
        }
        sendEffect(SettingsEffect.ShowToast("帧率限制已设置为${fpsName}，重启游戏后生效"))
    }

    // ==================== 工具方法 ====================

    private fun parseBackgroundType(type: String?): Int {
        return when (type?.lowercase()) {
            "default" -> 0
            "image" -> 1
            "video" -> 2
            else -> 0
        }
    }

    private fun getRendererDisplayName(renderer: String?): String {
        return when (renderer?.lowercase()) {
            "auto" -> "自动选择"
            "native" -> "Native OpenGL ES 3"
            "gl4es" -> "GL4ES"
            "gl4es+angle" -> "GL4ES + ANGLE"
            "mobileglues" -> "MobileGlues"
            "angle" -> "ANGLE"
            else -> "自动选择"
        }
    }

    // ==================== Factory ====================

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context) as T
        }
    }
}
