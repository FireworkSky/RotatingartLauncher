package com.app.ralaunch.shared.domain.model

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
 * 渲染器类型
 */
@Serializable
enum class FnaRenderer(val value: String, val displayName: String) {
    AUTO("auto", "Auto"),
    OPENGL("opengl", "OpenGL"),
    VULKAN("vulkan", "Vulkan");

    companion object {
        fun fromValue(value: String): FnaRenderer =
            entries.find { it.value == value } ?: AUTO
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
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val backgroundType: BackgroundType = BackgroundType.DEFAULT,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundImagePath: String = "",
    val backgroundVideoPath: String = "",
    val backgroundOpacity: Int = 0,
    val videoPlaybackSpeed: Float = 1.0f,

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
    val mouseRightStickEnabled: Boolean = true,
    val mouseRightStickAttackMode: Int = 0,
    val mouseRightStickSpeed: Int = 200,
    val mouseRightStickRangeLeft: Float = 1.0f,
    val mouseRightStickRangeTop: Float = 1.0f,
    val mouseRightStickRangeRight: Float = 1.0f,
    val mouseRightStickRangeBottom: Float = 1.0f,

    // 开发者设置
    val logSystemEnabled: Boolean = true,
    val verboseLogging: Boolean = false,
    val setThreadAffinityToBigCore: Boolean = true,

    // FNA 设置
    val fnaRenderer: FnaRenderer = FnaRenderer.AUTO,
    val fnaMapBufferRangeOptimization: Boolean = true,

    // CoreCLR 设置
    val serverGC: Boolean = false,
    val concurrentGC: Boolean = true,
    val gcHeapCount: String = "auto",
    val tieredCompilation: Boolean = true,
    val quickJIT: Boolean = true,
    val jitOptimizeType: Int = 0,
    val retainVM: Boolean = false,

    // 内存优化
    val killLauncherUIAfterLaunch: Boolean = false,

    // 音频设置
    val sdlAaudioLowLatency: Boolean = false,

    // Box64 设置
    val box64Enabled: Boolean = false,
    val box64GamePath: String = ""
) {
    companion object {
        val Default = AppSettings()
    }
}
