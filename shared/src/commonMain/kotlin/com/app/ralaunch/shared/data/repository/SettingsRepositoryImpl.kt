package com.app.ralaunch.shared.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.app.ralaunch.shared.data.local.PreferencesKeys
import com.app.ralaunch.shared.domain.model.*
import com.app.ralaunch.shared.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 设置仓库实现
 *
 * 使用 DataStore 存储设置
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromValue(prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.LIGHT.value),
            themeColor = prefs[PreferencesKeys.THEME_COLOR] ?: AppSettings.Default.themeColor,
            backgroundType = BackgroundType.fromValue(prefs[PreferencesKeys.BACKGROUND_TYPE] ?: BackgroundType.DEFAULT.value),
            backgroundColor = prefs[PreferencesKeys.BACKGROUND_COLOR] ?: AppSettings.Default.backgroundColor,
            backgroundImagePath = prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] ?: "",
            backgroundVideoPath = prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] ?: "",
            backgroundOpacity = prefs[PreferencesKeys.BACKGROUND_OPACITY] ?: 0,
            videoPlaybackSpeed = prefs[PreferencesKeys.VIDEO_PLAYBACK_SPEED] ?: 1.0f,
            controlsOpacity = prefs[PreferencesKeys.CONTROLS_OPACITY] ?: 0.7f,
            vibrationEnabled = prefs[PreferencesKeys.VIBRATION_ENABLED] ?: true,
            virtualControllerVibrationEnabled = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_ENABLED] ?: false,
            virtualControllerVibrationIntensity = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY] ?: 1.0f,
            virtualControllerAsFirst = prefs[PreferencesKeys.VIRTUAL_CONTROLLER_AS_FIRST] ?: false,
            backButtonOpenMenu = prefs[PreferencesKeys.BACK_BUTTON_OPEN_MENU] ?: false,
            touchMultitouchEnabled = prefs[PreferencesKeys.TOUCH_MULTITOUCH_ENABLED] ?: true,
            fpsDisplayEnabled = prefs[PreferencesKeys.FPS_DISPLAY_ENABLED] ?: false,
            fpsDisplayX = prefs[PreferencesKeys.FPS_DISPLAY_X] ?: -1f,
            fpsDisplayY = prefs[PreferencesKeys.FPS_DISPLAY_Y] ?: -1f,
            keyboardType = KeyboardType.fromValue(prefs[PreferencesKeys.KEYBOARD_TYPE] ?: KeyboardType.VIRTUAL.value),
            touchEventEnabled = prefs[PreferencesKeys.TOUCH_EVENT_ENABLED] ?: true,
            mouseRightStickEnabled = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ENABLED] ?: true,
            mouseRightStickAttackMode = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_ATTACK_MODE] ?: 0,
            mouseRightStickSpeed = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_SPEED] ?: 200,
            mouseRightStickRangeLeft = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_LEFT] ?: 1.0f,
            mouseRightStickRangeTop = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_TOP] ?: 1.0f,
            mouseRightStickRangeRight = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_RIGHT] ?: 1.0f,
            mouseRightStickRangeBottom = prefs[PreferencesKeys.MOUSE_RIGHT_STICK_RANGE_BOTTOM] ?: 1.0f,
            logSystemEnabled = prefs[PreferencesKeys.LOG_SYSTEM_ENABLED] ?: true,
            verboseLogging = prefs[PreferencesKeys.VERBOSE_LOGGING] ?: false,
            setThreadAffinityToBigCore = prefs[PreferencesKeys.SET_THREAD_AFFINITY_TO_BIG_CORE] ?: true,
            fnaRenderer = FnaRenderer.fromValue(prefs[PreferencesKeys.FNA_RENDERER] ?: FnaRenderer.AUTO.value),
            fnaMapBufferRangeOptimization = prefs[PreferencesKeys.FNA_MAP_BUFFER_RANGE_OPTIMIZATION] ?: true,
            serverGC = prefs[PreferencesKeys.SERVER_GC] ?: false,
            concurrentGC = prefs[PreferencesKeys.CONCURRENT_GC] ?: true,
            gcHeapCount = prefs[PreferencesKeys.GC_HEAP_COUNT] ?: "auto",
            tieredCompilation = prefs[PreferencesKeys.TIERED_COMPILATION] ?: true,
            quickJIT = prefs[PreferencesKeys.QUICK_JIT] ?: true,
            jitOptimizeType = prefs[PreferencesKeys.JIT_OPTIMIZE_TYPE] ?: 0,
            retainVM = prefs[PreferencesKeys.RETAIN_VM] ?: false,
            killLauncherUIAfterLaunch = prefs[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] ?: false,
            sdlAaudioLowLatency = prefs[PreferencesKeys.SDL_AAUDIO_LOW_LATENCY] ?: false,
            box64Enabled = prefs[PreferencesKeys.BOX64_ENABLED] ?: false,
            box64GamePath = prefs[PreferencesKeys.BOX64_GAME_PATH] ?: ""
        )
    }

    override suspend fun getSettingsSnapshot(): AppSettings = getSettings().first()

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = settings.themeMode.value
            prefs[PreferencesKeys.THEME_COLOR] = settings.themeColor
            prefs[PreferencesKeys.BACKGROUND_TYPE] = settings.backgroundType.value
            prefs[PreferencesKeys.BACKGROUND_COLOR] = settings.backgroundColor
            prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] = settings.backgroundImagePath
            prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] = settings.backgroundVideoPath
            prefs[PreferencesKeys.BACKGROUND_OPACITY] = settings.backgroundOpacity
            prefs[PreferencesKeys.VIDEO_PLAYBACK_SPEED] = settings.videoPlaybackSpeed
            prefs[PreferencesKeys.CONTROLS_OPACITY] = settings.controlsOpacity
            prefs[PreferencesKeys.VIBRATION_ENABLED] = settings.vibrationEnabled
            prefs[PreferencesKeys.FPS_DISPLAY_ENABLED] = settings.fpsDisplayEnabled
            prefs[PreferencesKeys.KEYBOARD_TYPE] = settings.keyboardType.value
            prefs[PreferencesKeys.LOG_SYSTEM_ENABLED] = settings.logSystemEnabled
            prefs[PreferencesKeys.VERBOSE_LOGGING] = settings.verboseLogging
            prefs[PreferencesKeys.FNA_RENDERER] = settings.fnaRenderer.value
            prefs[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] = settings.killLauncherUIAfterLaunch
        }
    }

    // ==================== 外观设置 ====================

    override fun getThemeModeFlow(): Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromValue(prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.LIGHT.value)
    }

    override suspend fun getThemeMode(): Int {
        return dataStore.data.first()[PreferencesKeys.THEME_MODE] ?: 0
    }

    override suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    override fun getThemeColorFlow(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THEME_COLOR] ?: AppSettings.Default.themeColor
    }

    override suspend fun getThemeColor(): Int {
        return dataStore.data.first()[PreferencesKeys.THEME_COLOR] ?: AppSettings.Default.themeColor
    }

    override suspend fun setThemeColor(color: Int) {
        dataStore.edit { it[PreferencesKeys.THEME_COLOR] = color }
    }

    override fun getBackgroundTypeFlow(): Flow<BackgroundType> = dataStore.data.map { prefs ->
        BackgroundType.fromValue(prefs[PreferencesKeys.BACKGROUND_TYPE] ?: BackgroundType.DEFAULT.value)
    }

    override suspend fun getBackgroundType(): Int {
        val type = dataStore.data.first()[PreferencesKeys.BACKGROUND_TYPE] ?: BackgroundType.DEFAULT.value
        return when (type) {
            "default" -> 0
            "image" -> 1
            "video" -> 2
            else -> 0
        }
    }

    override suspend fun setBackgroundType(type: Int) {
        val typeString = when (type) {
            0 -> "default"
            1 -> "image"
            2 -> "video"
            else -> "default"
        }
        dataStore.edit { it[PreferencesKeys.BACKGROUND_TYPE] = typeString }
    }

    override suspend fun getLanguage(): String {
        return dataStore.data.first()[PreferencesKeys.LANGUAGE] ?: "en"
    }

    override suspend fun setLanguage(language: String) {
        dataStore.edit { it[PreferencesKeys.LANGUAGE] = language }
    }

    override fun getBackgroundImagePath(): Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] ?: ""
    }

    override suspend fun setBackgroundImagePath(path: String) {
        dataStore.edit { it[PreferencesKeys.BACKGROUND_IMAGE_PATH] = path }
    }

    override fun getBackgroundVideoPath(): Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] ?: ""
    }

    override suspend fun setBackgroundVideoPath(path: String) {
        dataStore.edit { it[PreferencesKeys.BACKGROUND_VIDEO_PATH] = path }
    }

    override fun getBackgroundOpacityFlow(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BACKGROUND_OPACITY] ?: 0
    }

    override suspend fun getBackgroundOpacity(): Int {
        return dataStore.data.first()[PreferencesKeys.BACKGROUND_OPACITY] ?: 0
    }

    override suspend fun setBackgroundOpacity(opacity: Int) {
        dataStore.edit { it[PreferencesKeys.BACKGROUND_OPACITY] = opacity }
    }

    override fun getVideoPlaybackSpeedFlow(): Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VIDEO_PLAYBACK_SPEED] ?: 1.0f
    }

    override suspend fun getVideoPlaybackSpeed(): Float {
        return dataStore.data.first()[PreferencesKeys.VIDEO_PLAYBACK_SPEED] ?: 1.0f
    }

    override suspend fun setVideoPlaybackSpeed(speed: Float) {
        dataStore.edit { it[PreferencesKeys.VIDEO_PLAYBACK_SPEED] = speed }
    }

    // ==================== 控制设置 ====================

    override fun getControlsOpacity(): Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CONTROLS_OPACITY] ?: 0.7f
    }

    override suspend fun setControlsOpacity(opacity: Float) {
        dataStore.edit { it[PreferencesKeys.CONTROLS_OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    override suspend fun isTouchMultitouchEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.TOUCH_MULTITOUCH_ENABLED] ?: true
    }

    override suspend fun setTouchMultitouchEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TOUCH_MULTITOUCH_ENABLED] = enabled }
    }

    override suspend fun isMouseRightStickEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.MOUSE_RIGHT_STICK_ENABLED] ?: false
    }

    override suspend fun setMouseRightStickEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MOUSE_RIGHT_STICK_ENABLED] = enabled }
    }

    override fun isVibrationEnabledFlow(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VIBRATION_ENABLED] ?: true
    }

    override suspend fun isVibrationEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.VIBRATION_ENABLED] ?: true
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.VIBRATION_ENABLED] = enabled }
    }

    override suspend fun getVibrationStrength(): Float {
        return dataStore.data.first()[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY] ?: 0.5f
    }

    override suspend fun setVibrationStrength(strength: Float) {
        dataStore.edit { it[PreferencesKeys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY] = strength.coerceIn(0f, 1f) }
    }

    override fun isFpsDisplayEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.FPS_DISPLAY_ENABLED] ?: false
    }

    override suspend fun setFpsDisplayEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FPS_DISPLAY_ENABLED] = enabled }
    }

    override fun getKeyboardType(): Flow<KeyboardType> = dataStore.data.map { prefs ->
        KeyboardType.fromValue(prefs[PreferencesKeys.KEYBOARD_TYPE] ?: KeyboardType.VIRTUAL.value)
    }

    override suspend fun setKeyboardType(type: KeyboardType) {
        dataStore.edit { it[PreferencesKeys.KEYBOARD_TYPE] = type.value }
    }

    // ==================== 游戏设置 ====================

    override suspend fun isBigCoreAffinityEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.SET_THREAD_AFFINITY_TO_BIG_CORE] ?: false
    }

    override suspend fun setBigCoreAffinityEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SET_THREAD_AFFINITY_TO_BIG_CORE] = enabled }
    }

    override suspend fun isLowLatencyAudioEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.SDL_AAUDIO_LOW_LATENCY] ?: false
    }

    override suspend fun setLowLatencyAudioEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SDL_AAUDIO_LOW_LATENCY] = enabled }
    }

    override suspend fun getRendererType(): String {
        val renderer = dataStore.data.first()[PreferencesKeys.FNA_RENDERER] ?: "auto"
        return when (renderer.lowercase()) {
            "auto" -> "自动"
            "opengl", "opengl es" -> "OpenGL ES"
            "vulkan" -> "Vulkan"
            "software" -> "软件渲染"
            else -> "自动"
        }
    }

    override suspend fun setRendererType(renderer: String) {
        val value = when (renderer) {
            "自动" -> "auto"
            "OpenGL ES" -> "opengl"
            "Vulkan" -> "vulkan"
            "软件渲染" -> "software"
            else -> "auto"
        }
        dataStore.edit { it[PreferencesKeys.FNA_RENDERER] = value }
    }

    // ==================== 开发者设置 ====================

    override fun isLogSystemEnabledFlow(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LOG_SYSTEM_ENABLED] ?: true
    }

    override suspend fun isLoggingEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.LOG_SYSTEM_ENABLED] ?: false
    }

    override suspend fun setLoggingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LOG_SYSTEM_ENABLED] = enabled }
    }

    override fun isVerboseLoggingFlow(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VERBOSE_LOGGING] ?: false
    }

    override suspend fun isVerboseLogging(): Boolean {
        return dataStore.data.first()[PreferencesKeys.VERBOSE_LOGGING] ?: false
    }

    override suspend fun setVerboseLogging(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.VERBOSE_LOGGING] = enabled }
    }

    override suspend fun isKillLauncherUIEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] ?: false
    }

    override suspend fun setKillLauncherUIEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] = enabled }
    }

    // ==================== .NET 运行时设置 ====================

    override suspend fun isServerGCEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.SERVER_GC] ?: true
    }

    override suspend fun setServerGCEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SERVER_GC] = enabled }
    }

    override suspend fun isConcurrentGCEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.CONCURRENT_GC] ?: true
    }

    override suspend fun setConcurrentGCEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CONCURRENT_GC] = enabled }
    }

    override suspend fun isTieredCompilationEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.TIERED_COMPILATION] ?: true
    }

    override suspend fun setTieredCompilationEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TIERED_COMPILATION] = enabled }
    }

    // ==================== FNA 设置 ====================

    override fun getFnaRenderer(): Flow<FnaRenderer> = dataStore.data.map { prefs ->
        FnaRenderer.fromValue(prefs[PreferencesKeys.FNA_RENDERER] ?: FnaRenderer.AUTO.value)
    }

    override suspend fun setFnaRenderer(renderer: FnaRenderer) {
        dataStore.edit { it[PreferencesKeys.FNA_RENDERER] = renderer.value }
    }

    override suspend fun isFnaMapBufferRangeOptEnabled(): Boolean {
        return dataStore.data.first()[PreferencesKeys.FNA_MAP_BUFFER_RANGE_OPTIMIZATION] ?: false
    }

    override suspend fun setFnaMapBufferRangeOptEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FNA_MAP_BUFFER_RANGE_OPTIMIZATION] = enabled }
    }

    // ==================== 画质设置 ====================

    override suspend fun getQualityLevel(): Int {
        return dataStore.data.first()[PreferencesKeys.FNA_QUALITY_LEVEL] ?: 0
    }

    override suspend fun setQualityLevel(level: Int) {
        dataStore.edit { it[PreferencesKeys.FNA_QUALITY_LEVEL] = level }
    }

    override suspend fun isShaderLowPrecision(): Boolean {
        return dataStore.data.first()[PreferencesKeys.FNA_SHADER_LOW_PRECISION] ?: false
    }

    override suspend fun setShaderLowPrecision(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FNA_SHADER_LOW_PRECISION] = enabled }
    }

    override suspend fun getTargetFps(): Int {
        return dataStore.data.first()[PreferencesKeys.FNA_TARGET_FPS] ?: 0
    }

    override suspend fun setTargetFps(fps: Int) {
        dataStore.edit { it[PreferencesKeys.FNA_TARGET_FPS] = fps }
    }

    // ==================== 内存优化 ====================

    override fun isKillLauncherUIAfterLaunch(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] ?: false
    }

    override suspend fun setKillLauncherUIAfterLaunch(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KILL_LAUNCHER_UI_AFTER_LAUNCH] = enabled }
    }

    // ==================== 迁移支持 ====================

    override suspend fun migrateFromLegacy(legacySettings: Map<String, Any>) {
        dataStore.edit { prefs ->
            legacySettings.forEach { (key, value) ->
                when (key) {
                    "theme_mode" -> prefs[PreferencesKeys.THEME_MODE] = (value as? Number)?.toInt() ?: 2
                    "theme_color" -> prefs[PreferencesKeys.THEME_COLOR] = (value as? Number)?.toInt() ?: AppSettings.Default.themeColor
                    "background_type" -> prefs[PreferencesKeys.BACKGROUND_TYPE] = value.toString()
                    "background_image_path" -> prefs[PreferencesKeys.BACKGROUND_IMAGE_PATH] = value.toString()
                    "background_video_path" -> prefs[PreferencesKeys.BACKGROUND_VIDEO_PATH] = value.toString()
                    "fna_renderer" -> prefs[PreferencesKeys.FNA_RENDERER] = value.toString()
                    "verbose_logging" -> prefs[PreferencesKeys.VERBOSE_LOGGING] = value as? Boolean ?: false
                }
            }
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }
}
