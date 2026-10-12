package com.app.ralaunch.feature.controls

import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.SdlVersion
import org.libsdl.app.SDLActivity as SDL3Activity
import org.libsdl.app2.SDLActivity as SDL2Activity

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
     * 通知当前 SDL 层的实现。
     *
     * 两条路径的实现方式不同：
     *  - SDL3：org.libsdl.app.SDLActivity 的 native 方法（由 SDL fork 的 SDL_touch.c 实现，
     *    被占用的触点不会合成鼠标事件）；
     *  - SDL2：org.libsdl.app2.SDLActivity 的 Java 实现（app2 胶水层在转发触摸前过滤掉这些触点）。
     * 同时吞掉 Throwable：native 方法在库尚未加载时会抛 UnsatisfiedLinkError（Error 而非 Exception），
     * 早期用 catch(Exception) 兜不住，会在玩家第一次触摸虚拟按键时直接崩掉进程。
     */
    private inline fun notifySdl(what: String, sdl3Call: () -> Unit, sdl2Call: () -> Unit) {
        runCatching {
            if (SdlVersion.isSdl2(SettingsAccess.getInstance().selectedSdlVersion)) {
                sdl2Call()
            } else {
                sdl3Call()
            }
        }.onFailure { e ->
            AppLog.w(TAG, "Failed to notify SDL about $what: ${e.message}")
        }
    }

    /**
     * 标记触摸点被虚拟控件占用
     * 同时通知 SDL 层，让此触摸点不会转换为鼠标事件
     */
    @Synchronized
    fun consumePointer(pointerId: Int) {
        sConsumedPointers.add(pointerId)
        notifySdl(
            "consumed pointer",
            { SDL3Activity.nativeConsumeFingerTouch(pointerId) },
            { SDL2Activity.nativeConsumeFingerTouch(pointerId) }
        )
    }

    /**
     * 释放触摸点
     * 同时通知 SDL 层
     */
    @Synchronized
    fun releasePointer(pointerId: Int) {
        sConsumedPointers.remove(pointerId)
        notifySdl(
            "released pointer",
            { SDL3Activity.nativeReleaseFingerTouch(pointerId) },
            { SDL2Activity.nativeReleaseFingerTouch(pointerId) }
        )
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
        notifySdl(
            "cleared pointers",
            { SDL3Activity.nativeClearConsumedFingers() },
            { SDL2Activity.nativeClearConsumedFingers() }
        )
    }
}



