package com.app.ralaunch.core.common.util

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import com.app.ralaunch.core.logging.AppLog
import kotlin.math.max
import kotlin.math.min

/**
 * 屏幕适配工具类
 */
object DensityAdapter {
    private const val TAG = "DensityAdapter"

    private const val BASE_WIDTH = 2560f
    private const val BASE_HEIGHT = 1080f

    private var appDensity = 0f
    private var appScaledDensity = 0f
    private var appDisplayMetrics: DisplayMetrics? = null

    private var screenWidth = 0
    private var screenHeight = 0

    private var scaleX = 1f
    private var scaleY = 1f

    /**
     * 在 Application 中初始化
     */
    @JvmStatic
    fun init(application: Application) {
        appDisplayMetrics = application.resources.displayMetrics

        if (appDensity == 0f) {
            appDisplayMetrics?.let { metrics ->
                appDensity = metrics.density
                appScaledDensity = metrics.scaledDensity

                screenWidth = max(metrics.widthPixels, metrics.heightPixels)
                screenHeight = min(metrics.widthPixels, metrics.heightPixels)

                scaleX = screenWidth / BASE_WIDTH
                scaleY = screenHeight / BASE_HEIGHT
            }

            application.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    if (newConfig.fontScale > 0) {
                        appScaledDensity = application.resources.displayMetrics.scaledDensity
                        AppLog.d(TAG, "系统字体大小变化，更新 scaledDensity: $appScaledDensity")
                    }
                }

                override fun onLowMemory() {}
            })
        }
    }

    @JvmStatic
    fun adapt(activity: Activity) {
        val displayMetrics = activity.resources.displayMetrics

        val currentWidth = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val currentHeight = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

        if (currentWidth != screenWidth || currentHeight != screenHeight) {
            screenWidth = currentWidth
            screenHeight = currentHeight
            scaleX = screenWidth / BASE_WIDTH
            scaleY = screenHeight / BASE_HEIGHT
        }
    }

    @JvmStatic
    fun adapt(activity: Activity, isBaseOnWidth: Boolean) {
        adapt(activity)
    }

    @JvmStatic
    fun cancelAdapt(activity: Activity) {
        AppLog.d(TAG, "取消适配: ${activity.javaClass.simpleName}（本方案无需取消）")
    }

    @JvmStatic
    fun getDesignWidthDp(): Float = BASE_WIDTH

    @JvmStatic
    fun getDesignHeightDp(): Float = BASE_HEIGHT

    @JvmStatic
    fun getScreenWidth(): Int = screenWidth

    @JvmStatic
    fun getScreenHeight(): Int = screenHeight

    @JvmStatic
    fun getScaleX(): Float = scaleX

    @JvmStatic
    fun getScaleY(): Float = scaleY

    @JvmStatic
    fun px2dp(context: Context, px: Float): Float = px / context.resources.displayMetrics.density

    @JvmStatic
    fun dp2px(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density

    @JvmStatic
    fun getAdaptScale(context: Context?): Float = scaleX

    @JvmStatic
    fun scaleX(baseValue: Float): Float = baseValue * scaleX

    @JvmStatic
    fun scaleY(baseValue: Float): Float = baseValue * scaleY
}
