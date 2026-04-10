package com.app.ralaunch.shared.core.theme

import com.app.ralaunch.shared.core.model.domain.BackgroundType
import com.app.ralaunch.shared.core.model.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用主题状态管理 - 跨平台
 * 
 * 使用 StateFlow 实现实时响应式更新，替代传统的 EventBus
 */
object AppThemeState {
    
    /**
     * 主题模式
     */
    private val _themeMode = MutableStateFlow(ThemeMode.FOLLOW_SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    /**
     * 主题颜色 (ARGB 整数)
     */
    private val _themeColor = MutableStateFlow(0xFF6750A4.toInt())
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()
    
    /**
     * 背景类型
     */
    private val _backgroundType = MutableStateFlow(BackgroundType.DEFAULT)
    val backgroundType: StateFlow<BackgroundType> = _backgroundType.asStateFlow()
    
    /**
     * 背景图片路径
     */
    private val _backgroundImagePath = MutableStateFlow("")
    val backgroundImagePath: StateFlow<String> = _backgroundImagePath.asStateFlow()
    
    /**
     * 背景视频路径
     */
    private val _backgroundVideoPath = MutableStateFlow("")
    val backgroundVideoPath: StateFlow<String> = _backgroundVideoPath.asStateFlow()
    
    /**
     * 背景/页面透明度 (0-100)
     */
    private val _backgroundOpacity = MutableStateFlow(0)
    val backgroundOpacity: StateFlow<Int> = _backgroundOpacity.asStateFlow()
    
    /**
     * 视频播放速度 (0.5-2.0)
     */
    private val _videoPlaybackSpeed = MutableStateFlow(1.0f)
    val videoPlaybackSpeed: StateFlow<Float> = _videoPlaybackSpeed.asStateFlow()
    
    // ==================== 更新方法 ====================
    
    fun updateThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }
    
    fun updateThemeColor(color: Int) {
        _themeColor.value = color
    }
    
    fun updateBackgroundType(type: BackgroundType) {
        _backgroundType.value = type
    }
    
    fun updateBackgroundImagePath(path: String) {
        _backgroundImagePath.value = path
    }
    
    fun updateBackgroundVideoPath(path: String) {
        _backgroundVideoPath.value = path
    }
    
    fun updateBackgroundOpacity(opacity: Int) {
        _backgroundOpacity.value = opacity.coerceIn(0, 100)
    }
    
    fun updateVideoPlaybackSpeed(speed: Float) {
        _videoPlaybackSpeed.value = speed.coerceIn(0.5f, 2.0f)
    }
    
    /**
     * 恢复默认背景设置
     */
    fun restoreDefaultBackground() {
        _backgroundType.value = BackgroundType.DEFAULT
        _backgroundImagePath.value = ""
        _backgroundVideoPath.value = ""
        _backgroundOpacity.value = 0
        _videoPlaybackSpeed.value = 1.0f
    }
    
    /**
     * 批量更新所有状态（用于初始化）
     */
    fun initializeState(
        themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
        themeColor: Int = 0xFF6750A4.toInt(),
        backgroundType: BackgroundType = BackgroundType.DEFAULT,
        backgroundImagePath: String = "",
        backgroundVideoPath: String = "",
        backgroundOpacity: Int = 0,
        videoPlaybackSpeed: Float = 1.0f
    ) {
        _themeMode.value = themeMode
        _themeColor.value = themeColor
        _backgroundType.value = backgroundType
        _backgroundImagePath.value = backgroundImagePath
        _backgroundVideoPath.value = backgroundVideoPath
        _backgroundOpacity.value = backgroundOpacity
        _videoPlaybackSpeed.value = videoPlaybackSpeed
    }
}
