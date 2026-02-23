package com.app.ralaunch.shared.core.model.domain

import kotlinx.serialization.Serializable

/**
 * 主题模式
 */
@Serializable
enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),
    DARK(1),
    LIGHT(2);

    companion object {
        fun fromValue(value: Int): ThemeMode = entries.find { it.value == value } ?: LIGHT
    }
}

/**
 * 背景类型
 */
@Serializable
enum class BackgroundType(val value: String) {
    DEFAULT("default"),
    COLOR("color"),
    IMAGE("image"),
    VIDEO("video");

    companion object {
        fun fromValue(value: String): BackgroundType =
            entries.find { it.value == value } ?: DEFAULT
    }
}

/**
 * 键盘类型
 */
@Serializable
enum class KeyboardType(val value: String, val displayName: String) {
    SYSTEM("system", "System"),
    VIRTUAL("virtual", "Virtual");

    companion object {
        fun fromValue(value: String): KeyboardType =
            entries.find { it.value == value } ?: VIRTUAL
    }
}

/**
 * 应用设置 (跨平台版本)
 */
@Serializable
data class AppSettings(
    // 外观设置
    var themeMode: ThemeMode = ThemeMode.LIGHT,
    var themeColor: Int = 0xFF6750A4.toInt(),
    var backgroundType: BackgroundType = BackgroundType.DEFAULT,
    var backgroundColor: Int = 0xFFFFFFFF.toInt(),
    var backgroundImagePath: String = "",
    var backgroundVideoPath: String = "",
    var backgroundOpacity: Int = 0,
    var videoPlaybackSpeed: Float = 1.0f,
    var language: String = "auto",

    // 控制设置
    var controlsOpacity: Float = 0.7f,
    var vibrationEnabled: Boolean = true,
    var virtualControllerVibrationEnabled: Boolean = false,
    var virtualControllerVibrationIntensity: Float = 1.0f,
    var virtualControllerAsFirst: Boolean = false,
    var backButtonOpenMenu: Boolean = false,
    var touchMultitouchEnabled: Boolean = true,
    var fpsDisplayEnabled: Boolean = false,
    var fpsDisplayX: Float = -1f,
    var fpsDisplayY: Float = -1f,
    var keyboardType: KeyboardType = KeyboardType.VIRTUAL,
    var touchEventEnabled: Boolean = true,

    // 触屏设置
    var mouseRightStickEnabled: Boolean = true,
    var mouseRightStickAttackMode: Int = 0,
    var mouseRightStickSpeed: Int = 200,
    var mouseRightStickRangeLeft: Float = 1.0f,
    var mouseRightStickRangeTop: Float = 1.0f,
    var mouseRightStickRangeRight: Float = 1.0f,
    var mouseRightStickRangeBottom: Float = 1.0f,

    // 开发者设置
    var logSystemEnabled: Boolean = true,
    var verboseLogging: Boolean = false,
    var setThreadAffinityToBigCore: Boolean = false,

    // FNA 设置
    var fnaRenderer: String = "native",
    var fnaMapBufferRangeOptimization: Boolean = true,

    // 画质设置
    var qualityLevel: Int = 0,
    var fnaTextureLodBias: Float = 0f,
    var fnaMaxAnisotropy: Int = 4,
    var fnaRenderScale: Float = 1.0f,
    var shaderLowPrecision: Boolean = false,
    var targetFps: Int = 0,

    // CoreCLR 设置
    var serverGC: Boolean = false,
    var concurrentGC: Boolean = true,
    var gcHeapCount: String = "auto",
    var tieredCompilation: Boolean = true,
    var quickJIT: Boolean = true,
    var jitOptimizeType: Int = 0,
    var retainVM: Boolean = false,

    // 内存优化
    var killLauncherUIAfterLaunch: Boolean = false,

    // 音频设置
    var sdlAaudioLowLatency: Boolean = false,
    var ralAudioBufferSize: Int? = null,

    // 联机设置
    var multiplayerEnabled: Boolean = false,
    var multiplayerDisclaimerAccepted: Boolean = false,

    // 公告
    var lastAnnouncementId: String = "",
    var isAnnouncementBadgeShown: Boolean = false,

    // Box64 设置
    var box64Enabled: Boolean = false,
    var box64GamePath: String = ""
) {
    companion object {
        val Default: AppSettings
            get() = AppSettings()
    }
}
