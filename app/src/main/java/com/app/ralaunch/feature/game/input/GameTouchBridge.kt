package com.app.ralaunch.feature.game.input

import android.content.res.Resources
import android.view.MotionEvent
import com.app.ralaunch.feature.controls.TouchPointerTracker
import com.app.ralaunch.feature.game.ui.legacy.GameActivity
import com.app.ralaunch.core.logging.AppLog

/**
 * 触摸事件桥接，封装指针过滤与原生 touch 数据同步
 */
class GameTouchBridge {

    companion object {
        private const val TAG = "GameTouchBridge"
        private var touchBridgeAvailable: Boolean? = null
    }

    private val touchX = FloatArray(10)
    private val touchY = FloatArray(10)

    fun handleMotionEvent(event: MotionEvent, res: Resources) {
        if (!isTouchBridgeAvailable()) return

        try {
            val action = event.actionMasked
            val metrics = res.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                GameActivity.nativeClearTouchDataBridge()
                return
            }

            val pointerCount = event.pointerCount
            val actionIndex = event.actionIndex
            val isPointerUp = action == MotionEvent.ACTION_POINTER_UP

            var validCount = 0
            for (i in 0 until pointerCount) {
                if (validCount >= 10) break
                if (isPointerUp && i == actionIndex) continue

                val pointerId = event.getPointerId(i)
                if (TouchPointerTracker.isPointerConsumed(pointerId)) continue

                touchX[validCount] = event.getX(i) / screenWidth
                touchY[validCount] = event.getY(i) / screenHeight
                validCount++
            }

            GameActivity.nativeSetTouchDataBridge(validCount, touchX, touchY, screenWidth, screenHeight)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error in handleMotionEvent: ${e.message}", e)
        }
    }

    private fun isTouchBridgeAvailable(): Boolean {
        if (touchBridgeAvailable == null) {
            touchBridgeAvailable = try {
                GameActivity.nativeClearTouchDataBridge()
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
        return touchBridgeAvailable!!
    }

    @Suppress("unused")
    private fun actionToString(action: Int): String = when (action) {
        MotionEvent.ACTION_DOWN -> "DOWN"
        MotionEvent.ACTION_UP -> "UP"
        MotionEvent.ACTION_MOVE -> "MOVE"
        MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
        MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
        MotionEvent.ACTION_CANCEL -> "CANCEL"
        else -> "UNKNOWN($action)"
    }
}
