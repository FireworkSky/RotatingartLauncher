package com.app.ralaunch.core.logging.service

import android.util.Log
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.LogLevel
import com.app.ralaunch.core.logging.contract.Logger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidFileLogger(
    private val fileNameProvider: () -> String = { LogFilePolicy.appLogFileName() },
    private val emitToAndroidLog: Boolean = true,
    private val isFileLoggingEnabled: () -> Boolean = { true },
    private val logLevel: () -> LogLevel = { LogLevel.VERBOSE },
    private val logcatFileLogger: AndroidFileLogger? = null
) : Logger {
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var writer: PrintWriter? = null
    private var logFile: File? = null
    private var reader: LogcatReader? = null
    private var initialized = false
    private var logDirectory: File? = null
    private var activeLogcatLevel: LogLevel? = null

    @JvmOverloads
    fun start(logDirectory: File, clearExistingLogs: Boolean = false) {
        this.logDirectory = logDirectory
        applyConfiguration(clearExpiredLogs = clearExistingLogs)
    }

    fun refreshConfiguration() {
        applyConfiguration(clearExpiredLogs = false)
    }

    fun stop() {
        stopReader()
        logcatFileLogger?.close()
        close()
        initialized = false
        logDirectory = null
        i(TAG, "Log capture stopped")
    }

    fun configure(logDirectory: File, enabled: Boolean) {
        synchronized(lock) {
            closeLocked()
            if (!enabled) return

            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            logFile = File(logDirectory, fileNameProvider())
            writer = PrintWriter(FileWriter(logFile, true), true)
        }
    }

    fun close() {
        synchronized(lock) {
            closeLocked()
        }
    }

    fun currentLogFile(): File? = synchronized(lock) { logFile }

    fun currentLogcatFile(): File? = logcatFileLogger?.currentLogFile()

    fun writeRawLine(line: String) {
        synchronized(lock) {
            val currentWriter = writer ?: return
            currentWriter.println(line)
            currentWriter.flush()
        }
    }

    override fun v(tag: String, message: String): Int {
        val result = writeToAndroidLog(LogLevel.VERBOSE, tag, message, null)
        write(LogLevel.VERBOSE, tag, message, null)
        return result
    }

    override fun v(tag: String, message: String, throwable: Throwable?): Int {
        val result = writeToAndroidLog(LogLevel.VERBOSE, tag, message, throwable)
        write(LogLevel.VERBOSE, tag, message, throwable)
        return result
    }

    override fun d(tag: String, message: String): Int {
        val result = writeToAndroidLog(LogLevel.DEBUG, tag, message, null)
        write(LogLevel.DEBUG, tag, message, null)
        return result
    }

    override fun d(tag: String, message: String, throwable: Throwable?): Int {
        val result = writeToAndroidLog(LogLevel.DEBUG, tag, message, throwable)
        write(LogLevel.DEBUG, tag, message, throwable)
        return result
    }

    override fun i(tag: String, message: String): Int {
        val result = writeToAndroidLog(LogLevel.INFO, tag, message, null)
        write(LogLevel.INFO, tag, message, null)
        return result
    }

    override fun i(tag: String, message: String, throwable: Throwable?): Int {
        val result = writeToAndroidLog(LogLevel.INFO, tag, message, throwable)
        write(LogLevel.INFO, tag, message, throwable)
        return result
    }

    override fun w(tag: String, message: String): Int {
        val result = writeToAndroidLog(LogLevel.WARN, tag, message, null)
        write(LogLevel.WARN, tag, message, null)
        return result
    }

    override fun w(tag: String, message: String, throwable: Throwable?): Int {
        val result = writeToAndroidLog(LogLevel.WARN, tag, message, throwable)
        write(LogLevel.WARN, tag, message, throwable)
        return result
    }

    override fun e(tag: String, message: String): Int {
        val result = writeToAndroidLog(LogLevel.ERROR, tag, message, null)
        write(LogLevel.ERROR, tag, message, null)
        return result
    }

    override fun e(tag: String, message: String, throwable: Throwable?): Int {
        val result = writeToAndroidLog(LogLevel.ERROR, tag, message, throwable)
        write(LogLevel.ERROR, tag, message, throwable)
        return result
    }

    private fun applyConfiguration(clearExpiredLogs: Boolean) {
        val directory = logDirectory ?: return

        try {
            directory.takeIf { !it.exists() }?.mkdirs()
            if (clearExpiredLogs) {
                clearExpiredLogFiles(directory)
            }

            if (!isFileLoggingEnabled()) {
                stopReader()
                logcatFileLogger?.close()
                close()
                initialized = false
                w(TAG, "File log capture not started because logging is disabled in settings")
                return
            }

            val configuredLogLevel = logLevel()

            configure(directory, enabled = true)
            logcatFileLogger?.configure(directory, enabled = true)

            val captureLogger = logcatFileLogger
            if (captureLogger != null && (reader == null || activeLogcatLevel != configuredLogLevel)) {
                stopReader()
                reader = LogcatReader(
                    logger = this,
                    fileLogger = captureLogger
                ).also { it.start(minLevel = configuredLogLevel) }
                activeLogcatLevel = configuredLogLevel
            }

            if (initialized) {
                i(TAG, "Log capture configuration refreshed, logDir=${directory.absolutePath}")
            } else {
                initialized = true
                i(TAG, "Log capture started, logDir=${directory.absolutePath}")
            }
        } catch (exception: Exception) {
            e(TAG, "Failed to initialize log capture", exception)
        }
    }

    private fun stopReader() {
        reader?.stop()
        reader = null
        activeLogcatLevel = null
    }

    private fun writeToAndroidLog(level: LogLevel, tag: String, message: String, throwable: Throwable?): Int {
        if (!emitToAndroidLog) return 0
        return when (level) {
            LogLevel.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
            LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }

    private fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!shouldWriteFileLevel(level)) return

        synchronized(lock) {
            val currentWriter = writer ?: return
            currentWriter.println("[${timestampFormat.format(Date())}] [${level.label}] [$tag] $message")
            if (throwable != null) {
                currentWriter.println(throwable.stackTraceToString().trimEnd())
            }
            currentWriter.flush()
        }
    }

    private fun shouldWriteFileLevel(level: LogLevel): Boolean = logLevel().allows(level)

    private fun clearExpiredLogFiles(directory: File) {
        LogFilePolicy.filesOlderThanRetention(directory).forEach { file ->
            runCatching { FileUtils.deleteFileWithinRoot(file, directory) }
                .onFailure { w(TAG, "Failed to delete expired log file: ${file.absolutePath}", it) }
        }
    }

    private fun closeLocked() {
        writer?.apply {
            flush()
            close()
        }
        writer = null
        logFile = null
    }

    companion object {
        private const val TAG = "AndroidFileLogger"
    }
}
