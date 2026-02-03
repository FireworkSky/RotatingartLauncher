package com.app.ralaunch.shared.domain.repository

import com.app.ralaunch.shared.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库接口
 *
 * 定义应用设置的读写操作
 */
interface SettingsRepository {

    /**
     * 获取完整设置 (Flow)
     */
    fun getSettings(): Flow<AppSettings>

    /**
     * 获取完整设置 (同步)
     */
    suspend fun getSettingsSnapshot(): AppSettings

    /**
     * 更新完整设置
     */
    suspend fun updateSettings(settings: AppSettings)

    // ==================== 外观设置 ====================

    fun getThemeModeFlow(): Flow<ThemeMode>
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)

    fun getThemeColorFlow(): Flow<Int>
    suspend fun getThemeColor(): Int
    suspend fun setThemeColor(color: Int)

    fun getBackgroundTypeFlow(): Flow<BackgroundType>
    suspend fun getBackgroundType(): Int
    suspend fun setBackgroundType(type: Int)

    suspend fun getLanguage(): String
    suspend fun setLanguage(language: String)

    fun getBackgroundImagePath(): Flow<String>
    suspend fun setBackgroundImagePath(path: String)

    fun getBackgroundVideoPath(): Flow<String>
    suspend fun setBackgroundVideoPath(path: String)

    fun getBackgroundOpacityFlow(): Flow<Int>
    suspend fun getBackgroundOpacity(): Int
    suspend fun setBackgroundOpacity(opacity: Int)

    fun getVideoPlaybackSpeedFlow(): Flow<Float>
    suspend fun getVideoPlaybackSpeed(): Float
    suspend fun setVideoPlaybackSpeed(speed: Float)

    // ==================== 控制设置 ====================

    fun getControlsOpacity(): Flow<Float>
    suspend fun setControlsOpacity(opacity: Float)

    suspend fun isTouchMultitouchEnabled(): Boolean
    suspend fun setTouchMultitouchEnabled(enabled: Boolean)

    suspend fun isMouseRightStickEnabled(): Boolean
    suspend fun setMouseRightStickEnabled(enabled: Boolean)

    fun isVibrationEnabledFlow(): Flow<Boolean>
    suspend fun isVibrationEnabled(): Boolean
    suspend fun setVibrationEnabled(enabled: Boolean)

    suspend fun getVibrationStrength(): Float
    suspend fun setVibrationStrength(strength: Float)

    fun isFpsDisplayEnabled(): Flow<Boolean>
    suspend fun setFpsDisplayEnabled(enabled: Boolean)

    fun getKeyboardType(): Flow<KeyboardType>
    suspend fun setKeyboardType(type: KeyboardType)

    // ==================== 游戏设置 ====================

    suspend fun isBigCoreAffinityEnabled(): Boolean
    suspend fun setBigCoreAffinityEnabled(enabled: Boolean)

    suspend fun isLowLatencyAudioEnabled(): Boolean
    suspend fun setLowLatencyAudioEnabled(enabled: Boolean)

    suspend fun getRendererType(): String
    suspend fun setRendererType(renderer: String)

    // ==================== 开发者设置 ====================

    fun isLogSystemEnabledFlow(): Flow<Boolean>
    suspend fun isLoggingEnabled(): Boolean
    suspend fun setLoggingEnabled(enabled: Boolean)

    fun isVerboseLoggingFlow(): Flow<Boolean>
    suspend fun isVerboseLogging(): Boolean
    suspend fun setVerboseLogging(enabled: Boolean)

    suspend fun isKillLauncherUIEnabled(): Boolean
    suspend fun setKillLauncherUIEnabled(enabled: Boolean)

    // ==================== .NET 运行时设置 ====================

    suspend fun isServerGCEnabled(): Boolean
    suspend fun setServerGCEnabled(enabled: Boolean)

    suspend fun isConcurrentGCEnabled(): Boolean
    suspend fun setConcurrentGCEnabled(enabled: Boolean)

    suspend fun isTieredCompilationEnabled(): Boolean
    suspend fun setTieredCompilationEnabled(enabled: Boolean)

    // ==================== FNA 设置 ====================

    fun getFnaRenderer(): Flow<FnaRenderer>
    suspend fun setFnaRenderer(renderer: FnaRenderer)

    suspend fun isFnaMapBufferRangeOptEnabled(): Boolean
    suspend fun setFnaMapBufferRangeOptEnabled(enabled: Boolean)

    // ==================== 画质设置 ====================

    suspend fun getQualityLevel(): Int
    suspend fun setQualityLevel(level: Int)

    suspend fun isShaderLowPrecision(): Boolean
    suspend fun setShaderLowPrecision(enabled: Boolean)

    suspend fun getTargetFps(): Int
    suspend fun setTargetFps(fps: Int)

    // ==================== 内存优化 ====================

    fun isKillLauncherUIAfterLaunch(): Flow<Boolean>
    suspend fun setKillLauncherUIAfterLaunch(enabled: Boolean)

    // ==================== 迁移支持 ====================

    /**
     * 从旧版设置迁移
     */
    suspend fun migrateFromLegacy(legacySettings: Map<String, Any>)

    /**
     * 重置为默认设置
     */
    suspend fun resetToDefaults()
}
