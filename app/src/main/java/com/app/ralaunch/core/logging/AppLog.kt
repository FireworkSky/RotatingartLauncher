package com.app.ralaunch.core.logging

import android.util.Log
import com.app.ralaunch.core.logging.contract.Logger

object AppLog {
    @Volatile
    private var logger: Logger? = null

    fun install(logger: Logger) {
        this.logger = logger
    }

    fun reset() {
        logger = null
    }

    @JvmStatic
    fun v(tag: String, message: String): Int = activeLogger.v(tag, message)

    @JvmStatic
    fun v(tag: String, message: String, throwable: Throwable?): Int = activeLogger.v(tag, message, throwable)

    @JvmStatic
    fun d(tag: String, message: String): Int = activeLogger.d(tag, message)

    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable?): Int = activeLogger.d(tag, message, throwable)

    @JvmStatic
    fun i(tag: String, message: String): Int = activeLogger.i(tag, message)

    @JvmStatic
    fun i(tag: String, message: String, throwable: Throwable?): Int = activeLogger.i(tag, message, throwable)

    @JvmStatic
    fun w(tag: String, message: String): Int = activeLogger.w(tag, message)

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable?): Int = activeLogger.w(tag, message, throwable)

    @JvmStatic
    fun e(tag: String, message: String): Int = activeLogger.e(tag, message)

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable?): Int = activeLogger.e(tag, message, throwable)

    @JvmStatic
    fun getStackTraceString(throwable: Throwable): String = Log.getStackTraceString(throwable)

    private val activeLogger: Logger
        get() = logger ?: FallbackLogger

    private object FallbackLogger : Logger {
        override fun v(tag: String, message: String): Int = Log.v(tag, message)
        override fun v(tag: String, message: String, throwable: Throwable?): Int =
            if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)

        override fun d(tag: String, message: String): Int = Log.d(tag, message)
        override fun d(tag: String, message: String, throwable: Throwable?): Int =
            if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)

        override fun i(tag: String, message: String): Int = Log.i(tag, message)
        override fun i(tag: String, message: String, throwable: Throwable?): Int =
            if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)

        override fun w(tag: String, message: String): Int = Log.w(tag, message)
        override fun w(tag: String, message: String, throwable: Throwable?): Int =
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)

        override fun e(tag: String, message: String): Int = Log.e(tag, message)
        override fun e(tag: String, message: String, throwable: Throwable?): Int =
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
