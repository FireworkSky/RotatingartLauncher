package com.app.ralaunch.data.migration

import android.content.Context
import android.util.Log
import com.app.ralaunch.shared.domain.model.AppSettings
import com.app.ralaunch.shared.domain.model.BackgroundType
import com.app.ralaunch.shared.domain.model.KeyboardType
import com.app.ralaunch.shared.domain.model.ThemeMode
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 设置数据迁移器
 *
 * 将旧版键名 settings.json 一次性迁移到 AppSettings 规范 JSON。
 */
class SettingsDataMigrator(
    private val context: Context,
    private val settingsRepository: SettingsRepositoryV2
) {
    companion object {
        private const val TAG = "SettingsDataMigrator"
        private const val SETTINGS_FILE = "settings.json"
        private const val MIGRATION_COMPLETED_KEY = "settings_migrated_to_canonical_json"
    }

    private val canonicalJson = Json {
        ignoreUnknownKeys = false
        coerceInputValues = true
    }

    private val legacyJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun needsMigration(): Boolean {
        val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_COMPLETED_KEY, false)) return false

        val settingsFile = File(context.filesDir, SETTINGS_FILE)
        if (!settingsFile.exists()) return false

        val raw = runCatching { settingsFile.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        return isLegacySettingsPayload(raw)
    }

    suspend fun migrate(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val settingsFile = File(context.filesDir, SETTINGS_FILE)
            if (!settingsFile.exists()) {
                markMigrationCompleted()
                return@withContext Result.success(Unit)
            }

            val raw = settingsFile.readText(Charsets.UTF_8)

            if (runCatching { canonicalJson.decodeFromString<AppSettings>(raw) }.isSuccess) {
                markMigrationCompleted()
                return@withContext Result.success(Unit)
            }

            val legacy = legacyJson.decodeFromString<LegacyAppSettings>(raw)
            settingsRepository.updateSettings(legacy.toAppSettings())
            markMigrationCompleted()

            Log.i(TAG, "Settings migration completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Settings migration failed", e)
            Result.failure(e)
        }
    }

    suspend fun readLegacySettings(): AppSettings? = withContext(Dispatchers.IO) {
        try {
            val settingsFile = File(context.filesDir, SETTINGS_FILE)
            if (!settingsFile.exists()) return@withContext null

            val raw = settingsFile.readText(Charsets.UTF_8)
            val legacy = legacyJson.decodeFromString<LegacyAppSettings>(raw)
            legacy.toAppSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read legacy settings", e)
            null
        }
    }

    fun resetMigrationStatus() {
        context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIGRATION_COMPLETED_KEY, false)
            .apply()
    }

    private fun isLegacySettingsPayload(raw: String): Boolean {
        if (runCatching { canonicalJson.decodeFromString<AppSettings>(raw) }.isSuccess) {
            return false
        }
        return runCatching { legacyJson.decodeFromString<LegacyAppSettings>(raw) }.isSuccess
    }

    private fun markMigrationCompleted() {
        context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIGRATION_COMPLETED_KEY, true)
            .apply()
    }
}

@Serializable
private data class LegacyAppSettings(
    @SerialName("theme_mode") val themeMode: Int = 2,
    @SerialName("theme_color") val themeColor: Int = 0xFF6750A4.toInt(),
    @SerialName("background_type") val backgroundType: String = "default",
    @SerialName("background_color") val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    @SerialName("background_image_path") val backgroundImagePath: String = "",
    @SerialName("background_video_path") val backgroundVideoPath: String = "",
    @SerialName("background_opacity") val backgroundOpacity: Int = 0,
    @SerialName("video_playback_speed") val videoPlaybackSpeed: Float = 1.0f,
    @SerialName("language") val language: String = "",
    @SerialName("app_language") val appLanguage: String = "",

    @SerialName("controls_opacity") val controlsOpacity: Float = 0.7f,
    @SerialName("controls_vibration_enabled") val vibrationEnabled: Boolean = true,
    @SerialName("virtual_controller_vibration_enabled") val virtualControllerVibrationEnabled: Boolean = false,
    @SerialName("virtual_controller_vibration_intensity") val virtualControllerVibrationIntensity: Float = 1.0f,
    @SerialName("virtual_controller_as_first") val virtualControllerAsFirst: Boolean = false,
    @SerialName("back_button_open_menu") val backButtonOpenMenu: Boolean = false,
    @SerialName("touch_multitouch_enabled") val touchMultitouchEnabled: Boolean = true,
    @SerialName("fps_display_enabled") val fpsDisplayEnabled: Boolean = false,
    @SerialName("fps_display_x") val fpsDisplayX: Float = -1f,
    @SerialName("fps_display_y") val fpsDisplayY: Float = -1f,
    @SerialName("keyboard_type") val keyboardType: String = "virtual",
    @SerialName("touch_event_enabled") val touchEventEnabled: Boolean = true,

    @SerialName("mouse_right_stick_enabled") val mouseRightStickEnabled: Boolean = true,
    @SerialName("mouse_right_stick_attack_mode") val mouseRightStickAttackMode: Int = 0,
    @SerialName("mouse_right_stick_speed") val mouseRightStickSpeed: Int = 200,
    @SerialName("mouse_right_stick_range_left") val mouseRightStickRangeLeft: Float = 1.0f,
    @SerialName("mouse_right_stick_range_top") val mouseRightStickRangeTop: Float = 1.0f,
    @SerialName("mouse_right_stick_range_right") val mouseRightStickRangeRight: Float = 1.0f,
    @SerialName("mouse_right_stick_range_bottom") val mouseRightStickRangeBottom: Float = 1.0f,

    @SerialName("enable_log_system") val logSystemEnabled: Boolean = true,
    @SerialName("verbose_logging") val verboseLogging: Boolean = false,
    @SerialName("set_thread_affinity_to_big_core_enabled") val setThreadAffinityToBigCore: Boolean = false,

    @SerialName("fna_renderer") val fnaRenderer: String = "auto",
    @SerialName("fna_enable_map_buffer_range_optimization_if_available") val fnaMapBufferRangeOptimization: Boolean = true,

    @SerialName("fna_quality_level") val qualityLevel: Int = 0,
    @SerialName("fna_texture_lod_bias") val fnaTextureLodBias: Float = 0f,
    @SerialName("fna_max_anisotropy") val fnaMaxAnisotropy: Int = 4,
    @SerialName("fna_render_scale") val fnaRenderScale: Float = 1.0f,
    @SerialName("fna_shader_low_precision") val shaderLowPrecision: Boolean = false,
    @SerialName("fna_target_fps") val targetFps: Int = 0,

    @SerialName("coreclr_server_gc") val serverGC: Boolean = false,
    @SerialName("coreclr_concurrent_gc") val concurrentGC: Boolean = true,
    @SerialName("coreclr_gc_heap_count") val gcHeapCount: String = "auto",
    @SerialName("coreclr_tiered_compilation") val tieredCompilation: Boolean = true,
    @SerialName("coreclr_quick_jit") val quickJIT: Boolean = true,
    @SerialName("coreclr_jit_optimize_type") val jitOptimizeType: Int = 0,
    @SerialName("coreclr_retain_vm") val retainVM: Boolean = false,

    @SerialName("kill_launcher_ui_after_launch") val killLauncherUIAfterLaunch: Boolean = false,
    @SerialName("sdl_aaudio_low_latency") val sdlAaudioLowLatency: Boolean = false,

    @SerialName("multiplayer_enabled") val multiplayerEnabled: Boolean = false,
    @SerialName("multiplayer_disclaimer_accepted") val multiplayerDisclaimerAccepted: Boolean = false,

    @SerialName("box64_enabled") val box64Enabled: Boolean = false,
    @SerialName("box64_game_path") val box64GamePath: String = ""
) {
    fun toAppSettings(): AppSettings {
        val defaultLanguage = if (appLanguage.isNotBlank()) appLanguage else "en"
        val normalizedLanguage = language.ifBlank { defaultLanguage }

        return AppSettings(
            themeMode = ThemeMode.fromValue(themeMode),
            themeColor = themeColor,
            backgroundType = BackgroundType.fromValue(backgroundType),
            backgroundColor = backgroundColor,
            backgroundImagePath = backgroundImagePath,
            backgroundVideoPath = backgroundVideoPath,
            backgroundOpacity = backgroundOpacity,
            videoPlaybackSpeed = videoPlaybackSpeed,
            language = normalizedLanguage,
            controlsOpacity = controlsOpacity,
            vibrationEnabled = vibrationEnabled,
            virtualControllerVibrationEnabled = virtualControllerVibrationEnabled,
            virtualControllerVibrationIntensity = virtualControllerVibrationIntensity,
            virtualControllerAsFirst = virtualControllerAsFirst,
            backButtonOpenMenu = backButtonOpenMenu,
            touchMultitouchEnabled = touchMultitouchEnabled,
            fpsDisplayEnabled = fpsDisplayEnabled,
            fpsDisplayX = fpsDisplayX,
            fpsDisplayY = fpsDisplayY,
            keyboardType = KeyboardType.fromValue(keyboardType),
            touchEventEnabled = touchEventEnabled,
            mouseRightStickEnabled = mouseRightStickEnabled,
            mouseRightStickAttackMode = mouseRightStickAttackMode,
            mouseRightStickSpeed = mouseRightStickSpeed,
            mouseRightStickRangeLeft = mouseRightStickRangeLeft,
            mouseRightStickRangeTop = mouseRightStickRangeTop,
            mouseRightStickRangeRight = mouseRightStickRangeRight,
            mouseRightStickRangeBottom = mouseRightStickRangeBottom,
            logSystemEnabled = logSystemEnabled,
            verboseLogging = verboseLogging,
            setThreadAffinityToBigCore = setThreadAffinityToBigCore,
            fnaRenderer = fnaRenderer,
            fnaMapBufferRangeOptimization = fnaMapBufferRangeOptimization,
            qualityLevel = qualityLevel,
            fnaTextureLodBias = fnaTextureLodBias,
            fnaMaxAnisotropy = fnaMaxAnisotropy,
            fnaRenderScale = fnaRenderScale,
            shaderLowPrecision = shaderLowPrecision,
            targetFps = targetFps,
            serverGC = serverGC,
            concurrentGC = concurrentGC,
            gcHeapCount = gcHeapCount,
            tieredCompilation = tieredCompilation,
            quickJIT = quickJIT,
            jitOptimizeType = jitOptimizeType,
            retainVM = retainVM,
            killLauncherUIAfterLaunch = killLauncherUIAfterLaunch,
            sdlAaudioLowLatency = sdlAaudioLowLatency,
            multiplayerEnabled = multiplayerEnabled,
            multiplayerDisclaimerAccepted = multiplayerDisclaimerAccepted,
            box64Enabled = box64Enabled,
            box64GamePath = box64GamePath
        )
    }
}
