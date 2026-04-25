package com.app.ralaunch.feature.controls

import com.app.ralaunch.core.logging.AppLog
import org.libsdl.app.SDLActivity

/**
 * 触摸点跟踪器
 * 用于跟踪哪些触摸点被虚拟控件使用
 * 同时通知 SDL 层，让被占用的触摸点不会转换为鼠标事件
 */
object TouchPointerTracker {
    private const val TAG = "TouchPointerTracker"

    // 被虚拟控件占用的触摸点 ID
    private val sConsumedPointers: MutableSet<Int?> = HashSet<Int?>()

    /**
     * 标记触摸点被虚拟控件占用
     * 同时通知 SDL 层，让此触摸点不会转换为鼠标事件
     */
    @Synchronized
    fun consumePointer(pointerId: Int) {
        sConsumedPointers.add(pointerId)
        // 通知 SDL 层
        try {
            SDLActivity.nativeConsumeFingerTouch(pointerId)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to notify SDL about consumed pointer: " + e.message)
        }
    }

    /**
     * 释放触摸点
     * 同时通知 SDL 层
     */
    @Synchronized
    fun releasePointer(pointerId: Int) {
        sConsumedPointers.remove(pointerId)
        // 通知 SDL 层
        try {
            SDLActivity.nativeReleaseFingerTouch(pointerId)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to notify SDL about released pointer: " + e.message)
        }
    }

    /**
     * 检查触摸点是否被占用
     */
    @JvmStatic
    @Synchronized
    fun isPointerConsumed(pointerId: Int): Boolean {
        return sConsumedPointers.contains(pointerId)
    }

    @get:Synchronized
    val consumedCount: Int
        /**
         * 获取被占用的触摸点数量
         */
        get() = sConsumedPointers.size

    /**
     * 清除所有占用
     * 同时通知 SDL 层
     */
    @Synchronized
    fun clearAll() {
        sConsumedPointers.clear()
        // 通知 SDL 层
        try {
            SDLActivity.nativeClearConsumedFingers()
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to notify SDL about cleared pointers: " + e.message)
        }
    }
}



