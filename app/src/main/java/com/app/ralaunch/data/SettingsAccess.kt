package com.app.ralaunch.data

import com.app.ralaunch.shared.domain.model.BackgroundType
import com.app.ralaunch.shared.domain.model.AppSettings
import com.app.ralaunch.shared.domain.model.KeyboardType
import com.app.ralaunch.shared.domain.model.ThemeMode
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent

/**
 * 设置访问入口
 *
 * 统一转发到 SettingsRepositoryV2，避免各调用方直接操作 Koin/协程。
 */
object SettingsAccess {

    private val settingsRepository: SettingsRepositoryV2 by lazy {
        KoinJavaComponent.get(SettingsRepositoryV2::class.java)
    }

    private val settings
        get() = settingsRepository.Settings

    private fun update(block: AppSettings.() -> Unit) {
        runBlocking {
            settingsRepository.update(block)
        }
    }

    // ==================== 便捷方法 ====================

    // 主题设置
    var themeMode: Int
        get() = settings.themeMode.value
        set(value) = update { themeMode = ThemeMode.fromValue(value) }

    var themeColor: Int
        get() = settings.themeColor
        set(value) = update { themeColor = value }

    var backgroundType: String
        get() = settings.backgroundType.value
        set(value) = update { backgroundType = BackgroundType.fromValue(value) }

    val backgroundColor: Int
        get() = settings.backgroundColor

    var backgroundImagePath: String
        get() = settings.backgroundImagePath
        set(value) = update { backgroundImagePath = value }

    var backgroundVideoPath: String
        get() = settings.backgroundVideoPath
        set(value) = update { backgroundVideoPath = value }

    var backgroundOpacity: Int
        get() = settings.backgroundOpacity
        set(value) = update { backgroundOpacity = value }

    var videoPlaybackSpeed: Float
        get() = settings.videoPlaybackSpeed
        set(value) = update { videoPlaybackSpeed = value }

    // 控制设置
    var controlsOpacity: Float
        get() = settings.controlsOpacity
        set(value) = update { controlsOpacity = value.coerceIn(0f, 1f) }

    var vibrationEnabled: Boolean
        get() = settings.vibrationEnabled
        set(value) = update { vibrationEnabled = value }

    var isVirtualControllerVibrationEnabled: Boolean
        get() = settings.virtualControllerVibrationEnabled
        set(value) = update { virtualControllerVibrationEnabled = value }

    var virtualControllerVibrationIntensity: Float
        get() = settings.virtualControllerVibrationIntensity
        set(value) = update { virtualControllerVibrationIntensity = value.coerceIn(0f, 1f) }

    var isVirtualControllerAsFirst: Boolean
        get() = settings.virtualControllerAsFirst
        set(value) = update { virtualControllerAsFirst = value }

    var isBackButtonOpenMenuEnabled: Boolean
        get() = settings.backButtonOpenMenu
        set(value) = update { backButtonOpenMenu = value }

    val isTouchMultitouchEnabled: Boolean
        get() = settings.touchMultitouchEnabled

    // FNA 触屏设置
    val isMouseRightStickEnabled: Boolean
        get() = settings.mouseRightStickEnabled

    var mouseRightStickAttackMode: Int
        get() = settings.mouseRightStickAttackMode
        set(value) = update { mouseRightStickAttackMode = value }

    var mouseRightStickSpeed: Int
        get() = settings.mouseRightStickSpeed
        set(value) = update { mouseRightStickSpeed = value }

    var mouseRightStickRangeLeft: Float
        get() = settings.mouseRightStickRangeLeft
        set(value) = update { mouseRightStickRangeLeft = value }

    var mouseRightStickRangeTop: Float
        get() = settings.mouseRightStickRangeTop
        set(value) = update { mouseRightStickRangeTop = value }

    var mouseRightStickRangeRight: Float
        get() = settings.mouseRightStickRangeRight
        set(value) = update { mouseRightStickRangeRight = value }

    var mouseRightStickRangeBottom: Float
        get() = settings.mouseRightStickRangeBottom
        set(value) = update { mouseRightStickRangeBottom = value }

    // 开发者设置
    var isLogSystemEnabled: Boolean
        get() = settings.logSystemEnabled
        set(value) = update { logSystemEnabled = value }

    var isVerboseLogging: Boolean
        get() = settings.verboseLogging
        set(value) = update { verboseLogging = value }

    var setThreadAffinityToBigCoreEnabled: Boolean
        get() = settings.setThreadAffinityToBigCore
        set(value) = update { setThreadAffinityToBigCore = value }

    var fnaRenderer: String
        get() = settings.fnaRenderer
        set(value) = update { fnaRenderer = value }

    var isFnaEnableMapBufferRangeOptimization: Boolean
        get() = settings.fnaMapBufferRangeOptimization
        set(value) = update { fnaMapBufferRangeOptimization = value }

    // 画质优化设置
    var fnaQualityLevel: Int
        get() = settings.qualityLevel
        set(value) = update { qualityLevel = value }

    var fnaTextureLodBias: Float
        get() = settings.fnaTextureLodBias
        set(value) = update { fnaTextureLodBias = value }

    var fnaMaxAnisotropy: Int
        get() = settings.fnaMaxAnisotropy
        set(value) = update { fnaMaxAnisotropy = value }

    var fnaRenderScale: Float
        get() = settings.fnaRenderScale
        set(value) = update { fnaRenderScale = value }

    var isFnaShaderLowPrecision: Boolean
        get() = settings.shaderLowPrecision
        set(value) = update { shaderLowPrecision = value }

    var fnaTargetFps: Int
        get() = settings.targetFps
        set(value) = update { targetFps = value }

    // CoreCLR GC 设置
    var isServerGC: Boolean
        get() = settings.serverGC
        set(value) = update { serverGC = value }

    var isConcurrentGC: Boolean
        get() = settings.concurrentGC
        set(value) = update { concurrentGC = value }

    val gcHeapCount: String
        get() = settings.gcHeapCount

    val isRetainVM: Boolean
        get() = settings.retainVM

    var isTieredCompilation: Boolean
        get() = settings.tieredCompilation
        set(value) = update { tieredCompilation = value }

    val isQuickJIT: Boolean
        get() = settings.quickJIT

    val jitOptimizeType: Int
        get() = settings.jitOptimizeType

    var isFPSDisplayEnabled: Boolean
        get() = settings.fpsDisplayEnabled
        set(value) = update { fpsDisplayEnabled = value }

    var fpsDisplayX: Float
        get() = settings.fpsDisplayX
        set(value) = update { fpsDisplayX = value }

    var fpsDisplayY: Float
        get() = settings.fpsDisplayY
        set(value) = update { fpsDisplayY = value }

    var keyboardType: String
        get() = settings.keyboardType.value
        set(value) = update { keyboardType = KeyboardType.fromValue(value) }

    var isTouchEventEnabled: Boolean
        get() = settings.touchEventEnabled
        set(value) = update { touchEventEnabled = value }

    var isKillLauncherUIAfterLaunch: Boolean
        get() = settings.killLauncherUIAfterLaunch
        set(value) = update { killLauncherUIAfterLaunch = value }

    var isSdlAaudioLowLatency: Boolean
        get() = settings.sdlAaudioLowLatency
        set(value) = update { sdlAaudioLowLatency = value }

    // 联机设置
    var isMultiplayerEnabled: Boolean
        get() = settings.multiplayerEnabled
        set(value) = update { multiplayerEnabled = value }

    var hasMultiplayerDisclaimerAccepted: Boolean
        get() = settings.multiplayerDisclaimerAccepted
        set(value) = update { multiplayerDisclaimerAccepted = value }

    fun reload() {
        runBlocking {
            settingsRepository.getSettingsSnapshot()
        }
    }

    const val ATTACK_MODE_HOLD = 0
    const val ATTACK_MODE_CLICK = 1
    const val ATTACK_MODE_CONTINUOUS = 2

    @JvmStatic
    fun getInstance(): SettingsAccess = this
}
