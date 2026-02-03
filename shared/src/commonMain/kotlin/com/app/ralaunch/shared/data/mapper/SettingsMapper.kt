package com.app.ralaunch.shared.data.mapper

import com.app.ralaunch.shared.domain.model.*

/**
 * 设置数据映射器
 *
 * 用于旧版 JSON 设置迁移到 DataStore
 */
object SettingsMapper {

    /**
     * 从旧版 JSON 数据转换为新设置模型
     */
    fun fromLegacyJson(json: Map<String, Any?>): AppSettings {
        return AppSettings(
            // 外观设置
            themeMode = ThemeMode.fromValue((json["theme_mode"] as? Number)?.toInt() ?: 2),
            themeColor = (json["theme_color"] as? Number)?.toInt() ?: AppSettings.Default.themeColor,
            backgroundType = BackgroundType.fromValue((json["background_type"] as? String) ?: "default"),
            backgroundColor = (json["background_color"] as? Number)?.toInt() ?: AppSettings.Default.backgroundColor,
            backgroundImagePath = (json["background_image_path"] as? String) ?: "",
            backgroundVideoPath = (json["background_video_path"] as? String) ?: "",
            backgroundOpacity = (json["background_opacity"] as? Number)?.toInt() ?: 0,
            videoPlaybackSpeed = (json["video_playback_speed"] as? Number)?.toFloat() ?: 1.0f,

            // 控制设置
            controlsOpacity = (json["controls_opacity"] as? Number)?.toFloat() ?: 0.7f,
            vibrationEnabled = (json["controls_vibration_enabled"] as? Boolean) ?: true,
            virtualControllerVibrationEnabled = (json["virtual_controller_vibration_enabled"] as? Boolean) ?: false,
            virtualControllerVibrationIntensity = (json["virtual_controller_vibration_intensity"] as? Number)?.toFloat() ?: 1.0f,
            virtualControllerAsFirst = (json["virtual_controller_as_first"] as? Boolean) ?: false,
            backButtonOpenMenu = (json["back_button_open_menu"] as? Boolean) ?: false,
            touchMultitouchEnabled = (json["touch_multitouch_enabled"] as? Boolean) ?: true,
            fpsDisplayEnabled = (json["fps_display_enabled"] as? Boolean) ?: false,
            fpsDisplayX = (json["fps_display_x"] as? Number)?.toFloat() ?: -1f,
            fpsDisplayY = (json["fps_display_y"] as? Number)?.toFloat() ?: -1f,
            keyboardType = KeyboardType.fromValue((json["keyboard_type"] as? String) ?: "virtual"),
            touchEventEnabled = (json["touch_event_enabled"] as? Boolean) ?: true,

            // 触屏设置
            mouseRightStickEnabled = (json["mouse_right_stick_enabled"] as? Boolean) ?: true,
            mouseRightStickAttackMode = (json["mouse_right_stick_attack_mode"] as? Number)?.toInt() ?: 0,
            mouseRightStickSpeed = (json["mouse_right_stick_speed"] as? Number)?.toInt() ?: 200,
            mouseRightStickRangeLeft = (json["mouse_right_stick_range_left"] as? Number)?.toFloat() ?: 1.0f,
            mouseRightStickRangeTop = (json["mouse_right_stick_range_top"] as? Number)?.toFloat() ?: 1.0f,
            mouseRightStickRangeRight = (json["mouse_right_stick_range_right"] as? Number)?.toFloat() ?: 1.0f,
            mouseRightStickRangeBottom = (json["mouse_right_stick_range_bottom"] as? Number)?.toFloat() ?: 1.0f,

            // 开发者设置
            logSystemEnabled = (json["enable_log_system"] as? Boolean) ?: true,
            verboseLogging = (json["verbose_logging"] as? Boolean) ?: false,
            setThreadAffinityToBigCore = (json["set_thread_affinity_to_big_core_enabled"] as? Boolean) ?: true,

            // FNA 设置
            fnaRenderer = FnaRenderer.fromValue((json["fna_renderer"] as? String) ?: "auto"),
            fnaMapBufferRangeOptimization = (json["fna_enable_map_buffer_range_optimization_if_available"] as? Boolean) ?: true,

            // CoreCLR 设置
            serverGC = (json["coreclr_server_gc"] as? Boolean) ?: false,
            concurrentGC = (json["coreclr_concurrent_gc"] as? Boolean) ?: true,
            gcHeapCount = (json["coreclr_gc_heap_count"] as? String) ?: "auto",
            tieredCompilation = (json["coreclr_tiered_compilation"] as? Boolean) ?: true,
            quickJIT = (json["coreclr_quick_jit"] as? Boolean) ?: true,
            jitOptimizeType = (json["coreclr_jit_optimize_type"] as? Number)?.toInt() ?: 0,
            retainVM = (json["coreclr_retain_vm"] as? Boolean) ?: false,

            // 内存优化
            killLauncherUIAfterLaunch = (json["kill_launcher_ui_after_launch"] as? Boolean) ?: false,

            // 音频设置
            sdlAaudioLowLatency = (json["sdl_aaudio_low_latency"] as? Boolean) ?: false,

            // Box64 设置
            box64Enabled = (json["box64_enabled"] as? Boolean) ?: false,
            box64GamePath = (json["box64_game_path"] as? String) ?: ""
        )
    }

    /**
     * 转换为旧版 JSON 格式（用于兼容）
     */
    fun toLegacyJson(settings: AppSettings): Map<String, Any?> {
        return mapOf(
            "theme_mode" to settings.themeMode.value,
            "theme_color" to settings.themeColor,
            "background_type" to settings.backgroundType.value,
            "background_color" to settings.backgroundColor,
            "background_image_path" to settings.backgroundImagePath,
            "background_video_path" to settings.backgroundVideoPath,
            "background_opacity" to settings.backgroundOpacity,
            "video_playback_speed" to settings.videoPlaybackSpeed,
            "controls_opacity" to settings.controlsOpacity,
            "controls_vibration_enabled" to settings.vibrationEnabled,
            "virtual_controller_vibration_enabled" to settings.virtualControllerVibrationEnabled,
            "virtual_controller_vibration_intensity" to settings.virtualControllerVibrationIntensity,
            "virtual_controller_as_first" to settings.virtualControllerAsFirst,
            "back_button_open_menu" to settings.backButtonOpenMenu,
            "touch_multitouch_enabled" to settings.touchMultitouchEnabled,
            "fps_display_enabled" to settings.fpsDisplayEnabled,
            "fps_display_x" to settings.fpsDisplayX,
            "fps_display_y" to settings.fpsDisplayY,
            "keyboard_type" to settings.keyboardType.value,
            "touch_event_enabled" to settings.touchEventEnabled,
            "mouse_right_stick_enabled" to settings.mouseRightStickEnabled,
            "mouse_right_stick_attack_mode" to settings.mouseRightStickAttackMode,
            "mouse_right_stick_speed" to settings.mouseRightStickSpeed,
            "enable_log_system" to settings.logSystemEnabled,
            "verbose_logging" to settings.verboseLogging,
            "fna_renderer" to settings.fnaRenderer.value,
            "kill_launcher_ui_after_launch" to settings.killLauncherUIAfterLaunch
        )
    }
}
