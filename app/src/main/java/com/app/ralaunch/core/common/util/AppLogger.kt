package com.app.ralaunch.core.common.util

import android.util.Log
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.shared.core.util.Logger
import java.io.File

object AppLogger : Logger {
    private const val TAG = "RALaunch"
    private val ENABLE_DEBUG = true

    private var logcatReader: LogcatReader? = null
    private var logDir: File? = null
    private var initialized = false

    @JvmStatic
    @JvmOverloads
    fun init(logDirectory: File, clearExistingLogs: Boolean = false) {
        if (initialized) {
            Log.w(TAG, "AppLogger already initialized")
            return
        }

        logDir = logDirectory
        Log.i(TAG, "==================== AppLogger.init() START ====================")
        Log.i(TAG, "logDir: ${logDir?.absolutePath ?: "NULL"}")

        try {
            logDir?.takeIf { !it.exists() }?.mkdirs()
            if (clearExistingLogs) {
                clearLogFiles(logDir)
            }

            logcatReader = LogcatReader.getInstance()
            if (SettingsAccess.isLogSystemEnabled) {
                logcatReader?.start(logDir)
                Log.i(TAG, "LogcatReader started")
            } else {
                Log.w(TAG, "LogcatReader not started - logging disabled in settings")
            }

            initialized = true
            Log.i(TAG, "AppLogger.init() completed")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize logging", t)
        }
    }

    @JvmStatic
    fun close() {
        logcatReader?.stop()
        logcatReader = null
        initialized = false
        Log.i(TAG, "AppLogger closed")
    }

    override fun error(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun debug(tag: String, message: String) {
        if (ENABLE_DEBUG) {
            Log.d(tag, message)
        }
    }

    @JvmStatic fun e(tag: String, message: String) = error(tag, message)
    @JvmStatic fun e(tag: String, message: String, t: Throwable?) = error(tag, message, t)
    @JvmStatic fun w(tag: String, message: String) = warn(tag, message)
    @JvmStatic fun w(tag: String, message: String, t: Throwable?) = warn(tag, message, t)
    @JvmStatic fun i(tag: String, message: String) = info(tag, message)
    @JvmStatic fun d(tag: String, message: String) = debug(tag, message)

    @JvmStatic
    fun getLogFile(): File? = logcatReader?.logFile

    @JvmStatic
    fun getLogcatReader(): LogcatReader? = logcatReader

    private fun clearLogFiles(directory: File?) {
        try {
            directory?.listFiles { file -> 
                file.isFile && file.extension.equals("log", ignoreCase = true) 
            }?.forEach { file ->
                file.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear old logs", t)
        }
    }
}
