package com.app.ralaunch.shared.data.local

import androidx.datastore.preferences.core.*

/**
 * DataStore 偏好键定义
 */
object PreferencesKeys {

    // ==================== 外观设置 ====================
    val THEME_MODE = intPreferencesKey("theme_mode")
    val THEME_COLOR = intPreferencesKey("theme_color")
    val BACKGROUND_TYPE = stringPreferencesKey("background_type")
    val BACKGROUND_COLOR = intPreferencesKey("background_color")
    val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
    val BACKGROUND_VIDEO_PATH = stringPreferencesKey("background_video_path")
    val BACKGROUND_OPACITY = intPreferencesKey("background_opacity")
    val VIDEO_PLAYBACK_SPEED = floatPreferencesKey("video_playback_speed")
    val LANGUAGE = stringPreferencesKey("language")

    // ==================== 控制设置 ====================
    val CONTROLS_OPACITY = floatPreferencesKey("controls_opacity")
    val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    val VIRTUAL_CONTROLLER_VIBRATION_ENABLED = booleanPreferencesKey("virtual_controller_vibration_enabled")
    val VIRTUAL_CONTROLLER_VIBRATION_INTENSITY = floatPreferencesKey("virtual_controller_vibration_intensity")
    val VIRTUAL_CONTROLLER_AS_FIRST = booleanPreferencesKey("virtual_controller_as_first")
    val BACK_BUTTON_OPEN_MENU = booleanPreferencesKey("back_button_open_menu")
    val TOUCH_MULTITOUCH_ENABLED = booleanPreferencesKey("touch_multitouch_enabled")
    val FPS_DISPLAY_ENABLED = booleanPreferencesKey("fps_display_enabled")
    val FPS_DISPLAY_X = floatPreferencesKey("fps_display_x")
    val FPS_DISPLAY_Y = floatPreferencesKey("fps_display_y")
    val KEYBOARD_TYPE = stringPreferencesKey("keyboard_type")
    val TOUCH_EVENT_ENABLED = booleanPreferencesKey("touch_event_enabled")

    // ==================== 触屏设置 ====================
    val MOUSE_RIGHT_STICK_ENABLED = booleanPreferencesKey("mouse_right_stick_enabled")
    val MOUSE_RIGHT_STICK_ATTACK_MODE = intPreferencesKey("mouse_right_stick_attack_mode")
    val MOUSE_RIGHT_STICK_SPEED = intPreferencesKey("mouse_right_stick_speed")
    val MOUSE_RIGHT_STICK_RANGE_LEFT = floatPreferencesKey("mouse_right_stick_range_left")
    val MOUSE_RIGHT_STICK_RANGE_TOP = floatPreferencesKey("mouse_right_stick_range_top")
    val MOUSE_RIGHT_STICK_RANGE_RIGHT = floatPreferencesKey("mouse_right_stick_range_right")
    val MOUSE_RIGHT_STICK_RANGE_BOTTOM = floatPreferencesKey("mouse_right_stick_range_bottom")

    // ==================== 开发者设置 ====================
    val LOG_SYSTEM_ENABLED = booleanPreferencesKey("log_system_enabled")
    val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
    val SET_THREAD_AFFINITY_TO_BIG_CORE = booleanPreferencesKey("set_thread_affinity_to_big_core")

    // ==================== FNA 设置 ====================
    val FNA_RENDERER = stringPreferencesKey("fna_renderer")
    val FNA_MAP_BUFFER_RANGE_OPTIMIZATION = booleanPreferencesKey("fna_map_buffer_range_optimization")

    // ==================== 画质设置 ====================
    val FNA_QUALITY_LEVEL = intPreferencesKey("fna_quality_level") // 0=高, 1=中, 2=低
    val FNA_SHADER_LOW_PRECISION = booleanPreferencesKey("fna_shader_low_precision")

    // ==================== CoreCLR 设置 ====================
    val SERVER_GC = booleanPreferencesKey("server_gc")
    val CONCURRENT_GC = booleanPreferencesKey("concurrent_gc")
    val GC_HEAP_COUNT = stringPreferencesKey("gc_heap_count")
    val TIERED_COMPILATION = booleanPreferencesKey("tiered_compilation")
    val QUICK_JIT = booleanPreferencesKey("quick_jit")
    val JIT_OPTIMIZE_TYPE = intPreferencesKey("jit_optimize_type")
    val RETAIN_VM = booleanPreferencesKey("retain_vm")

    // ==================== 内存优化 ====================
    val KILL_LAUNCHER_UI_AFTER_LAUNCH = booleanPreferencesKey("kill_launcher_ui_after_launch")

    // ==================== 音频设置 ====================
    val SDL_AAUDIO_LOW_LATENCY = booleanPreferencesKey("sdl_aaudio_low_latency")

    // ==================== Box64 设置 ====================
    val BOX64_ENABLED = booleanPreferencesKey("box64_enabled")
    val BOX64_GAME_PATH = stringPreferencesKey("box64_game_path")

    // ==================== 初始化状态 ====================
    val LEGAL_AGREED = booleanPreferencesKey("legal_agreed")
    val PERMISSIONS_GRANTED = booleanPreferencesKey("permissions_granted")
    val COMPONENTS_EXTRACTED = booleanPreferencesKey("components_extracted")

    // ==================== 控制布局 ====================
    val CURRENT_LAYOUT_ID = stringPreferencesKey("current_layout_id")
}
