package com.app.ralaunch.utils

import android.util.Log
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.shared.util.Logger
import com.app.ralaunch.shared.util.LogLevel
import java.io.File

/**
 * 统一日志系统
 * - 所有日志输出到 logcat
 * - LogcatReader 捕获 logcat 并保存到文件
 * 
 * 实现 shared 模块的 Logger 接口
 */
object AppLogger : Logger {
    private const val TAG = "RALaunch"
    private const val ENABLE_DEBUG = false

    private var logcatReader: LogcatReader? = null
    private var logDir: File? = null
    private var initialized = false

    /**
     * 日志级别 - 使用 shared 模块的 LogLevel
     */
    @Deprecated("使用 com.app.ralaunch.shared.util.LogLevel", ReplaceWith("LogLevel"))
    enum class Level(val priority: Int, val tag: String) {
        ERROR(0, "E"),
        WARN(1, "W"),
        INFO(2, "I"),
        DEBUG(3, "D")
    }

    /**
     * 初始化日志器
     */
    @JvmStatic
    fun init(logDirectory: File) {
        if (initialized) {
            Log.w(TAG, "AppLogger already initialized")
            return
        }

        logDir = logDirectory
        Log.i(TAG, "==================== AppLogger.init() START ====================")
        Log.i(TAG, "logDir: ${logDir?.absolutePath ?: "NULL"}")

        try {
            logDir?.takeIf { !it.exists() }?.mkdirs()

            logcatReader = LogcatReader.getInstance()
            val settingsManager = SettingsAccess
            if (settingsManager.isLogSystemEnabled) {
                logcatReader?.start(logDir)
                Log.i(TAG, "LogcatReader started")
            } else {
                Log.w(TAG, "LogcatReader not started - logging disabled in settings")
            }

            initialized = true
            Log.i(TAG, "AppLogger.init() completed")
            info("Logger", "Log system initialized: ${logDir?.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logging", e)
        }
    }

    /**
     * 关闭日志器
     */
    @JvmStatic
    fun close() {
        logcatReader?.stop()
        logcatReader = null
        initialized = false
        Log.i(TAG, "AppLogger closed")
    }

    override fun error(tag: String, message: String) {
        log(Level.ERROR, tag, message, null)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        log(Level.ERROR, tag, message, throwable)
    }

    override fun warn(tag: String, message: String) {
        log(Level.WARN, tag, message, null)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        log(Level.WARN, tag, message, throwable)
    }

    override fun info(tag: String, message: String) {
        log(Level.INFO, tag, message, null)
    }

    override fun debug(tag: String, message: String) {
        if (ENABLE_DEBUG) {
            log(Level.DEBUG, tag, message, null)
        }
    }
    
    // JvmStatic 版本供 Java 调用
    @JvmStatic fun e(tag: String, message: String) = error(tag, message)
    @JvmStatic fun e(tag: String, message: String, t: Throwable?) = error(tag, message, t)
    @JvmStatic fun w(tag: String, message: String) = warn(tag, message)
    @JvmStatic fun w(tag: String, message: String, t: Throwable?) = warn(tag, message, t)
    @JvmStatic fun i(tag: String, message: String) = info(tag, message)
    @JvmStatic fun d(tag: String, message: String) = debug(tag, message)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            Level.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.DEBUG -> if (ENABLE_DEBUG) Log.d(tag, message)
        }
    }

    /**
     * 获取当前日志文件
     */
    @JvmStatic
    fun getLogFile(): File? = logcatReader?.logFile

    /**
     * 获取 LogcatReader 实例
     */
    @JvmStatic
    fun getLogcatReader(): LogcatReader? = logcatReader
}
