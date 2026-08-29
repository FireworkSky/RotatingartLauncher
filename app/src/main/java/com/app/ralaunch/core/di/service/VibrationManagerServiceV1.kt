package com.app.ralaunch.core.di.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.di.contract.IVibrationManagerServiceV1
import com.app.ralaunch.core.di.contract.VibrationType

/**
 * 振动管理器 - Android 实现
 * 提供统一的振动反馈接口
 * 
 * 实现核心层的 IVibrationManagerServiceV1 接口
 */
class VibrationManagerServiceV1(context: Context) : IVibrationManagerServiceV1 {

    companion object {
        const val TAG = "VibrationManagerServiceV1"
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val isVibrationEnabled: Boolean
        get() = vibrator?.hasVibrator() == true && AppConfig.c.vibrationEnabled

    // ==================== IVibrationManagerServiceV1 接口实现 ====================

    override fun isSupported(): Boolean = vibrator?.hasVibrator() == true

    override fun isEnabled(): Boolean = isVibrationEnabled

    override fun setEnabled(enabled: Boolean) {
        AppConfig.s.vibrationEnabled = enabled
    }

    override fun getIntensity(): Float = AppConfig.c.virtualControllerVibrationIntensity

    override fun setIntensity(intensity: Float) {
        AppConfig.s.virtualControllerVibrationIntensity = intensity.coerceIn(0f, 1f)
    }

    override fun vibrate(type: VibrationType) {
        if (!isVibrationEnabled) return

        when (type) {
            VibrationType.CLICK -> vibrateClick()
            VibrationType.LONG_PRESS -> vibrateHeavyClick()
            VibrationType.HEAVY -> vibrateHeavyClick()
            VibrationType.LIGHT -> vibrateTick()
            VibrationType.TICK -> vibrateTick()
            VibrationType.CONFIRM -> vibrateClick()
            VibrationType.REJECT -> vibrateHeavyClick()
            VibrationType.CUSTOM -> vibrateClick()
        }
    }

    override fun vibrate(durationMs: Long) {
        if (!isVibrationEnabled) return
        vibrateOneShot(durationMs)
    }

    override fun vibratePattern(pattern: LongArray, repeat: Int) {
        if (!isVibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, repeat)
        }
    }

    override fun cancel() {
        vibrator?.cancel()
    }

    // ==================== 原有方法 ====================

    /**
     * 点击振动
     */
    fun vibrateClick() {
        if (!isVibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    /**
     * 重点击振动
     */
    fun vibrateHeavyClick() {
        if (!isVibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    }

    /**
     * 轻触振动
     */
    fun vibrateTick() {
        if (!isVibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    }

    /**
     * 自定义时长和强度振动
     */
    fun vibrateOneShot(milliseconds: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (!isVibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(milliseconds)
        }
    }
}
