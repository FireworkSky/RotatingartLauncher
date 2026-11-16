package com.app.ralaunch.core.common.util

/**
 * 原生方法封装
 */
object NativeMethods {

    init {
        try {
            System.loadLibrary("main")
        } catch (_: UnsatisfiedLinkError) {
            // 可能已在其他地方加载，忽略
        }
    }

    @JvmStatic
    fun chdir(path: String): Int = nativeChdir(path)

    // ==================== JNI ====================

    @JvmStatic
    private external fun nativeChdir(path: String): Int
}
