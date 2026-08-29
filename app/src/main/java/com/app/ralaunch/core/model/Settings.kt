package com.app.ralaunch.core.model

import kotlinx.serialization.Serializable
import com.app.ralaunch.jsonconfig.FlowJsonConfig

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
 * 画质预设
 */
@Serializable
enum class QualityLevel(val value: Int) {
    HIGH(0),
    MEDIUM(1),
    LOW(2);

    companion object {
        fun fromValue(value: Int): QualityLevel = entries.find { it.value == value } ?: HIGH
    }
}

/**
 * 帧率限制
 */
@Serializable
enum class FpsLimit(val value: Int) {
    UNLIMITED(0),
    FPS_30(30),
    FPS_45(45),
    FPS_60(60);

    companion object {
        fun fromValue(value: Int): FpsLimit = entries.find { it.value == value } ?: UNLIMITED
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
@FlowJsonConfig
@Serializable
data class AppSettings(
    // 外观设置
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val dynamicColor: Boolean = true,
    val backgroundType: BackgroundType = BackgroundType.DEFAULT,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundImagePath: String = "",
    val backgroundVideoPath: String = "",
    val backgroundOpacity: Int = 0,
    val videoPlaybackSpeed: Float = 1.0f,
    val language: String = "auto",

    // 控制设置
    val controlsOpacity: Float = 0.7f,
    val vibrationEnabled: Boolean = true,
    val virtualControllerVibrationEnabled: Boolean = false,
    val virtualControllerVibrationIntensity: Float = 1.0f,
    val virtualControllerAsFirst: Boolean = false,
    val backButtonOpenMenu: Boolean = false,
    val touchMultitouchEnabled: Boolean = true,
    val fpsDisplayEnabled: Boolean = false,
    val fpsDisplayX: Float = -1f,
    val fpsDisplayY: Float = -1f,
    val keyboardType: KeyboardType = KeyboardType.VIRTUAL,
    val touchEventEnabled: Boolean = true,

    // 触屏设置
    val mouseRightStickSpeed: Int = 200,
    val mouseRightStickRangeLeft: Float = 1.0f,
    val mouseRightStickRangeTop: Float = 1.0f,
    val mouseRightStickRangeRight: Float = 1.0f,
    val mouseRightStickRangeBottom: Float = 1.0f,

    // 开发者设置
    val logSystemEnabled: Boolean = true,
    val verboseLogging: Boolean = false,
    val setThreadAffinityToBigCore: Boolean = false,
    val logFileEnabled: Boolean = true,
    val logFileMaxSizeMb: Int = 10,
    val logFileMaxCount: Int = 10,
    val logLevel: String = "INFO",

    // FNA 设置
    val fnaRenderer: String = "native",
    val fnaMapBufferRangeOptimization: Boolean = true,
    val fnaGlPerfDiagnosticsEnabled: Boolean = false,

    // 画质设置
    val qualityLevel: Int = 0,
    val fnaTextureLodBias: Float = 0f,
    val fnaMaxAnisotropy: Int = 4,
    val fnaRenderScale: Float = 1.0f,
    val shaderLowPrecision: Boolean = false,
    val targetFps: Int = 0,

    // CoreCLR 设置
    val serverGC: Boolean = false,
    val concurrentGC: Boolean = true,
    val gcHeapCount: String = "auto",
    val tieredCompilation: Boolean = true,
    val quickJIT: Boolean = true,
    val jitOptimizeType: Int = 0,
    val coreClrXiaomiCompatEnabled: Boolean = false,
    val retainVM: Boolean = false,

    // 内存优化
    val killLauncherUIAfterLaunch: Boolean = false,

    // 音频设置
    val sdlAaudioLowLatency: Boolean = false,
    val ralAudioBufferSize: Int? = null,

    // 联机设置
    val multiplayerEnabled: Boolean = false,
    val multiplayerDisclaimerAccepted: Boolean = false,

    // 公告
    val lastAnnouncementId: String = "",
    val isAnnouncementBadgeShown: Boolean = false,

    // Runtime 设置
    val selectedDotnetRuntimeVersion: String = "",
    val selectedBox64RuntimeVersion: String = ""
)
