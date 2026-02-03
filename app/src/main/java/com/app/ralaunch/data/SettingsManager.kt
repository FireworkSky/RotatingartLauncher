package com.app.ralaunch.data

import android.content.Context
import com.app.ralaunch.utils.AppLogger
import org.json.JSONException
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 设置管理器 - 使用 JSON 文件保存所有应用设置
 */
class SettingsManager private constructor() {
    private var settings: JSONObject
    private val settingsFile: File

    init {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        settingsFile = File(context.filesDir, SETTINGS_FILE)
        settings = loadSettings()
    }

    private fun loadSettings(): JSONObject {
        return if (settingsFile.exists()) {
            try {
                FileInputStream(settingsFile).use { fis ->
                    val data = ByteArray(settingsFile.length().toInt())
                    fis.read(data)
                    JSONObject(String(data, Charsets.UTF_8))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load settings: ${e.message}")
                JSONObject()
            }
        } else {
            JSONObject()
        }
    }

    @Synchronized
    private fun saveSettings() {
        try {
            FileOutputStream(settingsFile).use { fos ->
                val jsonString = settings.toString(2)
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save settings: ${e.message}")
        }
    }

    // ==================== 通用存取方法 ====================

    fun getString(key: String, defaultValue: String): String {
        return try {
            settings.optString(key, defaultValue)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting string for key: $key: ${e.message}")
            defaultValue
        }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return try {
            settings.optInt(key, defaultValue)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting int for key: $key: ${e.message}")
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            settings.optBoolean(key, defaultValue)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting boolean for key: $key: ${e.message}")
            defaultValue
        }
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return try {
            settings.optDouble(key, defaultValue)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting double for key: $key: ${e.message}")
            defaultValue
        }
    }

    fun getFloat(key: String, defaultValue: Float): Float {
        return try {
            if (settings.has(key)) settings.getDouble(key).toFloat() else defaultValue
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error getting float for key: $key: ${e.message}")
            defaultValue
        }
    }

    fun putString(key: String, value: String) {
        try {
            settings.put(key, value)
            saveSettings()
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error setting string for key: $key: ${e.message}")
        }
    }

    fun putInt(key: String, value: Int) {
        try {
            settings.put(key, value)
            saveSettings()
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error setting int for key: $key: ${e.message}")
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        try {
            settings.put(key, value)
            saveSettings()
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error setting boolean for key: $key: ${e.message}")
        }
    }

    fun putDouble(key: String, value: Double) {
        try {
            settings.put(key, value)
            saveSettings()
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error setting double for key: $key: ${e.message}")
        }
    }

    fun putFloat(key: String, value: Float) {
        try {
            settings.put(key, value.toDouble())
            saveSettings()
        } catch (e: JSONException) {
            AppLogger.e(TAG, "Error setting float for key: $key: ${e.message}")
        }
    }

    // ==================== 便捷方法 ====================

    // 主题设置
    var themeMode: Int
        get() = getInt(Keys.THEME_MODE, Defaults.THEME_MODE)
        set(value) = putInt(Keys.THEME_MODE, value)

    var themeColor: Int
        get() = getInt(Keys.THEME_COLOR, Defaults.THEME_COLOR)
        set(value) = putInt(Keys.THEME_COLOR, value)

    var backgroundType: String
        get() = getString(Keys.BACKGROUND_TYPE, Defaults.BACKGROUND_TYPE)
        set(value) = putString(Keys.BACKGROUND_TYPE, value)

    val backgroundColor: Int
        get() = getInt(Keys.BACKGROUND_COLOR, Defaults.BACKGROUND_COLOR)

    var backgroundImagePath: String
        get() = getString(Keys.BACKGROUND_IMAGE_PATH, Defaults.BACKGROUND_IMAGE_PATH)
        set(value) = putString(Keys.BACKGROUND_IMAGE_PATH, value)

    var backgroundVideoPath: String
        get() = getString(Keys.BACKGROUND_VIDEO_PATH, Defaults.BACKGROUND_VIDEO_PATH)
        set(value) = putString(Keys.BACKGROUND_VIDEO_PATH, value)

    var backgroundOpacity: Int
        get() = getInt(Keys.BACKGROUND_OPACITY, Defaults.BACKGROUND_OPACITY)
        set(value) = putInt(Keys.BACKGROUND_OPACITY, value)

    var videoPlaybackSpeed: Float
        get() = getDouble(Keys.VIDEO_PLAYBACK_SPEED, Defaults.VIDEO_PLAYBACK_SPEED.toDouble()).toFloat()
        set(value) = putDouble(Keys.VIDEO_PLAYBACK_SPEED, value.toDouble())

    // 控制设置
    var controlsOpacity: Float
        get() = getFloat(Keys.CONTROLS_OPACITY, Defaults.CONTROLS_OPACITY)
        set(value) = putFloat(Keys.CONTROLS_OPACITY, value.coerceIn(0f, 1f))

    var vibrationEnabled: Boolean
        get() = getBoolean(Keys.CONTROLS_VIBRATION_ENABLED, Defaults.CONTROLS_VIBRATION_ENABLED)
        set(value) = putBoolean(Keys.CONTROLS_VIBRATION_ENABLED, value)

    var isVirtualControllerVibrationEnabled: Boolean
        get() = getBoolean(Keys.VIRTUAL_CONTROLLER_VIBRATION_ENABLED, Defaults.VIRTUAL_CONTROLLER_VIBRATION_ENABLED)
        set(value) = putBoolean(Keys.VIRTUAL_CONTROLLER_VIBRATION_ENABLED, value)

    var virtualControllerVibrationIntensity: Float
        get() = getFloat(Keys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY, Defaults.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY)
        set(value) = putFloat(Keys.VIRTUAL_CONTROLLER_VIBRATION_INTENSITY, value.coerceIn(0f, 1f))

    var isVirtualControllerAsFirst: Boolean
        get() = getBoolean(Keys.VIRTUAL_CONTROLLER_AS_FIRST, Defaults.VIRTUAL_CONTROLLER_AS_FIRST)
        set(value) = putBoolean(Keys.VIRTUAL_CONTROLLER_AS_FIRST, value)

    var isBackButtonOpenMenuEnabled: Boolean
        get() = getBoolean(Keys.BACK_BUTTON_OPEN_MENU, Defaults.BACK_BUTTON_OPEN_MENU)
        set(value) = putBoolean(Keys.BACK_BUTTON_OPEN_MENU, value)

    val isTouchMultitouchEnabled: Boolean
        get() = getBoolean(Keys.TOUCH_MULTITOUCH_ENABLED, Defaults.TOUCH_MULTITOUCH_ENABLED)

    // FNA 触屏设置
    val isMouseRightStickEnabled: Boolean
        get() = getBoolean(Keys.MOUSE_RIGHT_STICK_ENABLED, Defaults.MOUSE_RIGHT_STICK_ENABLED)

    var mouseRightStickAttackMode: Int
        get() = getInt(Keys.MOUSE_RIGHT_STICK_ATTACK_MODE, Defaults.MOUSE_RIGHT_STICK_ATTACK_MODE)
        set(value) = putInt(Keys.MOUSE_RIGHT_STICK_ATTACK_MODE, value)

    var mouseRightStickSpeed: Int
        get() = getInt(Keys.MOUSE_RIGHT_STICK_SPEED, Defaults.MOUSE_RIGHT_STICK_SPEED)
        set(value) = putInt(Keys.MOUSE_RIGHT_STICK_SPEED, value)

    var mouseRightStickRangeLeft: Float
        get() = getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_LEFT, Defaults.MOUSE_RIGHT_STICK_RANGE_LEFT)
        set(value) = putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_LEFT, value)

    var mouseRightStickRangeTop: Float
        get() = getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_TOP, Defaults.MOUSE_RIGHT_STICK_RANGE_TOP)
        set(value) = putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_TOP, value)

    var mouseRightStickRangeRight: Float
        get() = getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_RIGHT, Defaults.MOUSE_RIGHT_STICK_RANGE_RIGHT)
        set(value) = putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_RIGHT, value)

    var mouseRightStickRangeBottom: Float
        get() = getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_BOTTOM, Defaults.MOUSE_RIGHT_STICK_RANGE_BOTTOM)
        set(value) = putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_BOTTOM, value)

    // 开发者设置
    var isLogSystemEnabled: Boolean
        get() = getBoolean(Keys.ENABLE_LOG_SYSTEM, Defaults.ENABLE_LOG_SYSTEM)
        set(value) = putBoolean(Keys.ENABLE_LOG_SYSTEM, value)

    var isVerboseLogging: Boolean
        get() = getBoolean(Keys.VERBOSE_LOGGING, Defaults.VERBOSE_LOGGING)
        set(value) = putBoolean(Keys.VERBOSE_LOGGING, value)

    var setThreadAffinityToBigCoreEnabled: Boolean
        get() = getBoolean(Keys.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED, Defaults.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED)
        set(value) = putBoolean(Keys.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED, value)

    var fnaRenderer: String
        get() = getString(Keys.FNA_RENDERER, Defaults.FNA_RENDERER)
        set(value) = putString(Keys.FNA_RENDERER, value)

    var isFnaEnableMapBufferRangeOptimization: Boolean
        get() = getBoolean(Keys.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE, Defaults.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE)
        set(value) = putBoolean(Keys.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE, value)

    // 画质优化设置
    var fnaQualityLevel: Int
        get() = getInt(Keys.FNA_QUALITY_LEVEL, Defaults.FNA_QUALITY_LEVEL)
        set(value) = putInt(Keys.FNA_QUALITY_LEVEL, value)

    var fnaTextureLodBias: Float
        get() = getFloat(Keys.FNA_TEXTURE_LOD_BIAS, Defaults.FNA_TEXTURE_LOD_BIAS)
        set(value) = putFloat(Keys.FNA_TEXTURE_LOD_BIAS, value)

    var fnaMaxAnisotropy: Int
        get() = getInt(Keys.FNA_MAX_ANISOTROPY, Defaults.FNA_MAX_ANISOTROPY)
        set(value) = putInt(Keys.FNA_MAX_ANISOTROPY, value)

    var fnaRenderScale: Float
        get() = getFloat(Keys.FNA_RENDER_SCALE, Defaults.FNA_RENDER_SCALE)
        set(value) = putFloat(Keys.FNA_RENDER_SCALE, value)

    var isFnaShaderLowPrecision: Boolean
        get() = getBoolean(Keys.FNA_SHADER_LOW_PRECISION, Defaults.FNA_SHADER_LOW_PRECISION)
        set(value) = putBoolean(Keys.FNA_SHADER_LOW_PRECISION, value)


    // CoreCLR GC 设置
    var isServerGC: Boolean
        get() = getBoolean(Keys.CORECLR_SERVER_GC, Defaults.CORECLR_SERVER_GC)
        set(value) = putBoolean(Keys.CORECLR_SERVER_GC, value)

    var isConcurrentGC: Boolean
        get() = getBoolean(Keys.CORECLR_CONCURRENT_GC, Defaults.CORECLR_CONCURRENT_GC)
        set(value) = putBoolean(Keys.CORECLR_CONCURRENT_GC, value)

    val gcHeapCount: String
        get() = getString(Keys.CORECLR_GC_HEAP_COUNT, Defaults.CORECLR_GC_HEAP_COUNT)

    val isRetainVM: Boolean
        get() = getBoolean(Keys.CORECLR_RETAIN_VM, Defaults.CORECLR_RETAIN_VM)

    var isTieredCompilation: Boolean
        get() = getBoolean(Keys.CORECLR_TIERED_COMPILATION, Defaults.CORECLR_TIERED_COMPILATION)
        set(value) = putBoolean(Keys.CORECLR_TIERED_COMPILATION, value)

    val isQuickJIT: Boolean
        get() = getBoolean(Keys.CORECLR_QUICK_JIT, Defaults.CORECLR_QUICK_JIT)

    val jitOptimizeType: Int
        get() = getInt(Keys.CORECLR_JIT_OPTIMIZE_TYPE, Defaults.CORECLR_JIT_OPTIMIZE_TYPE)

    var isFPSDisplayEnabled: Boolean
        get() = getBoolean(Keys.FPS_DISPLAY_ENABLED, Defaults.FPS_DISPLAY_ENABLED)
        set(value) = putBoolean(Keys.FPS_DISPLAY_ENABLED, value)

    var fpsDisplayX: Float
        get() = getFloat(Keys.FPS_DISPLAY_X, Defaults.FPS_DISPLAY_X)
        set(value) = putFloat(Keys.FPS_DISPLAY_X, value)

    var fpsDisplayY: Float
        get() = getFloat(Keys.FPS_DISPLAY_Y, Defaults.FPS_DISPLAY_Y)
        set(value) = putFloat(Keys.FPS_DISPLAY_Y, value)

    var keyboardType: String
        get() = getString(Keys.KEYBOARD_TYPE, Defaults.KEYBOARD_TYPE)
        set(value) = putString(Keys.KEYBOARD_TYPE, value)

    var isTouchEventEnabled: Boolean
        get() = getBoolean(Keys.TOUCH_EVENT_ENABLED, Defaults.TOUCH_EVENT_ENABLED)
        set(value) = putBoolean(Keys.TOUCH_EVENT_ENABLED, value)

    var isKillLauncherUIAfterLaunch: Boolean
        get() = getBoolean(Keys.KILL_LAUNCHER_UI_AFTER_LAUNCH, Defaults.KILL_LAUNCHER_UI_AFTER_LAUNCH)
        set(value) = putBoolean(Keys.KILL_LAUNCHER_UI_AFTER_LAUNCH, value)

    var isSdlAaudioLowLatency: Boolean
        get() = getBoolean(Keys.SDL_AAUDIO_LOW_LATENCY, Defaults.SDL_AAUDIO_LOW_LATENCY)
        set(value) = putBoolean(Keys.SDL_AAUDIO_LOW_LATENCY, value)

    // 联机设置
    var isMultiplayerEnabled: Boolean
        get() = getBoolean(Keys.MULTIPLAYER_ENABLED, Defaults.MULTIPLAYER_ENABLED)
        set(value) = putBoolean(Keys.MULTIPLAYER_ENABLED, value)

    var hasMultiplayerDisclaimerAccepted: Boolean
        get() = getBoolean(Keys.MULTIPLAYER_DISCLAIMER_ACCEPTED, Defaults.MULTIPLAYER_DISCLAIMER_ACCEPTED)
        set(value) = putBoolean(Keys.MULTIPLAYER_DISCLAIMER_ACCEPTED, value)

    fun reload() {
        settings = loadSettings()
    }

    // 设置键常量
    object Keys {
        const val THEME_MODE = "theme_mode"
        const val APP_LANGUAGE = "app_language"
        const val THEME_COLOR = "theme_color"
        const val BACKGROUND_TYPE = "background_type"
        const val BACKGROUND_COLOR = "background_color"
        const val BACKGROUND_IMAGE_PATH = "background_image_path"
        const val BACKGROUND_VIDEO_PATH = "background_video_path"
        const val BACKGROUND_OPACITY = "background_opacity"
        const val VIDEO_PLAYBACK_SPEED = "video_playback_speed"

        const val CONTROLS_OPACITY = "controls_opacity"
        const val CONTROLS_VIBRATION_ENABLED = "controls_vibration_enabled"
        const val VIRTUAL_CONTROLLER_VIBRATION_ENABLED = "virtual_controller_vibration_enabled"
        const val VIRTUAL_CONTROLLER_VIBRATION_INTENSITY = "virtual_controller_vibration_intensity"
        const val VIRTUAL_CONTROLLER_AS_FIRST = "virtual_controller_as_first"
        const val BACK_BUTTON_OPEN_MENU = "back_button_open_menu"
        const val TOUCH_MULTITOUCH_ENABLED = "touch_multitouch_enabled"
        const val FPS_DISPLAY_ENABLED = "fps_display_enabled"
        const val FPS_DISPLAY_X = "fps_display_x"
        const val FPS_DISPLAY_Y = "fps_display_y"
        const val KEYBOARD_TYPE = "keyboard_type"
        const val TOUCH_EVENT_ENABLED = "touch_event_enabled"

        const val MOUSE_RIGHT_STICK_ENABLED = "mouse_right_stick_enabled"
        const val MOUSE_RIGHT_STICK_ATTACK_MODE = "mouse_right_stick_attack_mode"
        const val MOUSE_RIGHT_STICK_SPEED = "mouse_right_stick_speed"
        const val MOUSE_RIGHT_STICK_RANGE_LEFT = "mouse_right_stick_range_left"
        const val MOUSE_RIGHT_STICK_RANGE_TOP = "mouse_right_stick_range_top"
        const val MOUSE_RIGHT_STICK_RANGE_RIGHT = "mouse_right_stick_range_right"
        const val MOUSE_RIGHT_STICK_RANGE_BOTTOM = "mouse_right_stick_range_bottom"

        const val ENABLE_LOG_SYSTEM = "enable_log_system"
        const val VERBOSE_LOGGING = "verbose_logging"
        const val SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED = "set_thread_affinity_to_big_core_enabled"

        const val FNA_RENDERER = "fna_renderer"
        const val FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE = "fna_enable_map_buffer_range_optimization_if_available"
        
        // 画质优化设置
        const val FNA_QUALITY_LEVEL = "fna_quality_level" // 0=高, 1=中, 2=低
        const val FNA_TEXTURE_LOD_BIAS = "fna_texture_lod_bias" // 纹理 LOD 偏移 (0-4)
        const val FNA_MAX_ANISOTROPY = "fna_max_anisotropy" // 各向异性过滤 (1, 2, 4, 8, 16)
        const val FNA_RENDER_SCALE = "fna_render_scale" // 渲染分辨率缩放 (0.5, 0.75, 1.0)
        const val FNA_SHADER_LOW_PRECISION = "fna_shader_low_precision" // 低精度 shader

        const val CORECLR_SERVER_GC = "coreclr_server_gc"
        const val CORECLR_CONCURRENT_GC = "coreclr_concurrent_gc"
        const val CORECLR_GC_HEAP_COUNT = "coreclr_gc_heap_count"
        const val CORECLR_TIERED_COMPILATION = "coreclr_tiered_compilation"
        const val CORECLR_QUICK_JIT = "coreclr_quick_jit"
        const val CORECLR_JIT_OPTIMIZE_TYPE = "coreclr_jit_optimize_type"
        const val CORECLR_RETAIN_VM = "coreclr_retain_vm"

        const val KILL_LAUNCHER_UI_AFTER_LAUNCH = "kill_launcher_ui_after_launch"

        const val SDL_AAUDIO_LOW_LATENCY = "sdl_aaudio_low_latency"

        const val MULTIPLAYER_ENABLED = "multiplayer_enabled"
        const val MULTIPLAYER_DISCLAIMER_ACCEPTED = "multiplayer_disclaimer_accepted"
    }

    // 默认值
    object Defaults {
        const val THEME_MODE = 2
        const val THEME_COLOR = 0xFF6750A4.toInt()
        const val BACKGROUND_TYPE = "default"
        const val BACKGROUND_COLOR = 0xFFFFFFFF.toInt()
        const val BACKGROUND_IMAGE_PATH = ""
        const val BACKGROUND_VIDEO_PATH = ""
        const val BACKGROUND_OPACITY = 0
        const val VIDEO_PLAYBACK_SPEED = 1.0f

        const val ENABLE_LOG_SYSTEM = true
        const val VERBOSE_LOGGING = false
        // 默认禁用，因为某些设备（如小米）可能有安全限制
        const val SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED = false

        const val FNA_RENDERER = "auto"
        const val FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE = true
        
        // 画质优化默认值
        const val FNA_QUALITY_LEVEL = 0 // 高画质
        const val FNA_TEXTURE_LOD_BIAS = 0f // 无偏移
        const val FNA_MAX_ANISOTROPY = 4 // 中等各向异性
        const val FNA_RENDER_SCALE = 1.0f // 原生分辨率
        const val FNA_SHADER_LOW_PRECISION = false // 默认高精度 shader

        const val CONTROLS_OPACITY = 0.7f
        const val CONTROLS_VIBRATION_ENABLED = true
        const val VIRTUAL_CONTROLLER_VIBRATION_ENABLED = false
        const val VIRTUAL_CONTROLLER_VIBRATION_INTENSITY = 1.0f
        const val VIRTUAL_CONTROLLER_AS_FIRST = false
        const val BACK_BUTTON_OPEN_MENU = false
        const val TOUCH_MULTITOUCH_ENABLED = true
        const val FPS_DISPLAY_ENABLED = false
        const val FPS_DISPLAY_X = -1f
        const val FPS_DISPLAY_Y = -1f
        const val KEYBOARD_TYPE = "virtual"
        const val TOUCH_EVENT_ENABLED = true

        const val MOUSE_RIGHT_STICK_ENABLED = true
        const val MOUSE_RIGHT_STICK_ATTACK_MODE = 0
        const val MOUSE_RIGHT_STICK_SPEED = 200
        const val MOUSE_RIGHT_STICK_RANGE_LEFT = 1.0f
        const val MOUSE_RIGHT_STICK_RANGE_TOP = 1.0f
        const val MOUSE_RIGHT_STICK_RANGE_RIGHT = 1.0f
        const val MOUSE_RIGHT_STICK_RANGE_BOTTOM = 1.0f

        const val CORECLR_SERVER_GC = false
        const val CORECLR_CONCURRENT_GC = true
        const val CORECLR_GC_HEAP_COUNT = "auto"
        const val CORECLR_TIERED_COMPILATION = true
        const val CORECLR_QUICK_JIT = true
        const val CORECLR_JIT_OPTIMIZE_TYPE = 0
        const val CORECLR_RETAIN_VM = false

        const val KILL_LAUNCHER_UI_AFTER_LAUNCH = false

        const val SDL_AAUDIO_LOW_LATENCY = false

        const val MULTIPLAYER_ENABLED = false
        const val MULTIPLAYER_DISCLAIMER_ACCEPTED = false
    }

    companion object {
        private const val TAG = "SettingsManager"
        private const val SETTINGS_FILE = "settings.json"

        const val ATTACK_MODE_HOLD = 0
        const val ATTACK_MODE_CLICK = 1
        const val ATTACK_MODE_CONTINUOUS = 2

        @Volatile
        private var instance: SettingsManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): SettingsManager {
            return instance ?: SettingsManager().also { instance = it }
        }
    }
}
