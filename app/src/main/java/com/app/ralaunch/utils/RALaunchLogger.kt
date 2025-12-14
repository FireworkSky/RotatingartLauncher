package com.app.ralaunch.utils

import android.util.Log
import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 简洁的文件日志工具
 * - 直接写入文件，不依赖 LogcatReader
 * - 异步写入，性能更好
 * - 自动管理文件大小和日期
 *
 * 使用方式:
 * 1. 在 Application 的 onCreate 中调用 RALaunchLogger.init(...)
 * 2. 使用 Timber.d/e/i/w/v(...) 记录日志
 *    - 日志会根据 init 时的 isDebugBuild 参数决定是否打印到 Logcat (带调用信息)
 *    - 所有日志都会异步写入文件 (带调用信息)
 */
object RALaunchLogger {
    // <<<--- CHANGE THIS TO YOUR ACTUAL APP PACKAGE PREFIX ---
    private const val APP_PACKAGE_PREFIX = "com.app.ralaunch"
    private const val TAG = "RALaunch" // Fallback tag
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024L // 2MB
    private const val MAX_FILES = 5

    private var logDir: File? = null
    private var writer: PrintWriter? = null
    private var currentLogFile: File? = null

    private val logQueue = ConcurrentLinkedQueue<String>()
    private var writeThread: Thread? = null
    private val running = AtomicBoolean(false)

    private var initialized = false

    @VisibleForTesting
    internal var debugMode = false

    /**
     * 初始化日志系统
     * @param logDirectory 日志文件存储目录
     * @param isDebugBuild 是否为 Debug 构建 (影响 Logcat 输出)
     */
    fun init(logDirectory: File, isDebugBuild: Boolean = false) {
        if (initialized) return

        logDir = logDirectory
        debugMode = isDebugBuild

        try {
            logDirectory.mkdirs()
            cleanupOldLogs()
            rotateOrCreateNewFile()

            running.set(true)
            startWriteThread()

            // Plant the custom Timber trees
            Timber.plant(LogcatTree(isDebugBuild))
            Timber.plant(FileLoggingTree())

            initialized = true
            d("RALaunchLogger initialized at: ${logDirectory.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "RALaunchLogger init failed", e)
        }
    }

    // Convenience logging methods using Timber
    fun v(message: String, throwable: Throwable? = null) =
        Timber.v(throwable, "%s", makeMessageWithLocation(message))

    fun d(message: String, throwable: Throwable? = null) =
        Timber.d(throwable, "%s", makeMessageWithLocation(message))

    fun i(message: String, throwable: Throwable? = null) =
        Timber.i(throwable, "%s", makeMessageWithLocation(message))

    fun w(message: String, throwable: Throwable? = null) =
        Timber.w(throwable, "%s", makeMessageWithLocation(message))

    fun e(message: String, throwable: Throwable? = null) =
        Timber.e(throwable, "%s", makeMessageWithLocation(message))


    // ===== Internal Utilities =====

    /**
     * Prepends location info (filename:line) to the original message.
     */
    private fun makeMessageWithLocation(originalMessage: String): String {
        var locationInfo = ""
        try {
            val stackTrace = Throwable().stackTrace
            // Find the first relevant caller outside of Timber, this Logger, and common Kotlin/Android internals
            val callerElement = stackTrace.find { element ->
                element.className.startsWith(APP_PACKAGE_PREFIX) &&
                        !element.className.contains(Timber::class.java.simpleName) &&
                        !element.className.contains(RALaunchLogger::class.java.simpleName) &&
                        element.className != "dalvik.system.VMStack" &&
                        element.className != "java.lang.Thread"
            }
            callerElement?.let {
                locationInfo = "[${it.fileName}:${it.lineNumber}] "
            }
        } catch (_: Exception) {
            // Ignore errors in getting location info
        }
        return "$locationInfo$originalMessage"
    }

    private fun rotateOrCreateNewFile() {
        currentLogFile = createLogFile()
        writer = PrintWriter(FileWriter(currentLogFile!!, true), true)
    }

    private fun createLogFile(): File {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = File(logDir, "ralaunch_${dateStr}.log")
        if (!file.exists()) {
            file.createNewFile()
            FileWriter(file, true).use { fw ->
                fw.write("Log file created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            }
        }
        return file
    }

    private fun startWriteThread() {
        writeThread = thread(name = "RALaunchLogWriter", isDaemon = true) {
            while (running.get()) {
                try {
                    val batch = mutableListOf<String>()
                    repeat(100) {
                        logQueue.poll()?.let { batch.add(it) }
                    }
                    if (batch.isNotEmpty()) {
                        writeToFile(batch)
                    }
                    checkAndRotateFile()
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Log writer error", e)
                }
            }
            flushRemainingLogs()
        }
    }

    private fun writeToFile(lines: List<String>) {
        try {
            lines.forEach { writer?.println(it) }
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write to file failed", e)
        }
    }

    private fun flushRemainingLogs() {
        val remaining = mutableListOf<String>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { remaining.add(it) }
        }
        if (remaining.isNotEmpty()) {
            writeToFile(remaining)
        }
    }

    private fun checkAndRotateFile() {
        currentLogFile?.takeIf { it.length() > MAX_FILE_SIZE }?.let {
            writer?.flush()
            writer?.close()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val newName = "ralaunch_${timestamp}.log"
            val newFile = File(it.parent, newName)
            if (it.renameTo(newFile)) {
                d("Rotated log file to: $newName") // Log rotation event
            } else {
                e("Failed to rename log file for rotation.")
            }
            rotateOrCreateNewFile()
        }
    }

    private fun cleanupOldLogs() {
        try {
            val logFiles = logDir?.listFiles { _, name ->
                name.startsWith("ralaunch_") && name.endsWith(".log")
            } ?: emptyArray()

            if (logFiles.size > MAX_FILES) {
                val numberToDelete = logFiles.size - MAX_FILES
                logFiles.sortedBy { it.lastModified() }
                    .take(numberToDelete)
                    .forEach { file ->
                        try {
                            if (file.delete()) {
                                d("Deleted old log file: ${file.name}")
                            } else {
                                w("Failed to delete old log file: ${file.name}")
                            }
                        } catch (e: Exception) {
                            e("Exception deleting old log file: ${file.name}", e)
                        }
                    }
            }
        } catch (e: Exception) {
            e("Failed to cleanup old logs", e)
        }
    }


    // ===== Timber Trees =====

    /**
     * Timber Tree for Logcat output.
     * Filters based on debug mode.
     * Location info is added via makeMessageWithLocation called by convenience functions.
     * Uses Timber.DebugTree for standard tag inference if needed elsewhere.
     */
    private class LogcatTree(private val debug: Boolean) : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return debug || priority >= Log.INFO
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Since we use convenience functions, the message already contains location info.
            // We can pass it through directly to Android's Log.
            if (isLoggable(tag, priority)) {
                // Use the provided tag or default
                val finalTag = tag ?: TAG
                if (t != null) {
                    Log.println(priority, finalTag, "$message\n${Log.getStackTraceString(t)}")
                } else {
                    Log.println(priority, finalTag, message)
                }
            }
        }
    }

    /**
     * Timber Tree for writing logs to a file.
     * Includes timestamp, log level, tag, and message (with location prepended).
     * Location info is added via makeMessageWithLocation called by convenience functions.
     */
    private class FileLoggingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!initialized) return

            val level = when (priority) {
                Log.ERROR -> "E"
                Log.WARN -> "W"
                Log.INFO -> "I"
                Log.DEBUG -> "D"
                Log.VERBOSE -> "V"
                else -> "?"
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val effectiveTag = tag ?: TAG

            // Build the final log message string for the file
            val sb = StringBuilder()
            // Format: [Timestamp] [Level] [Tag] Message
            sb.append("[$timestamp] [$level] [$effectiveTag] $message") // Message already has location

            t?.let { throwable ->
                sb.append("\n").append(throwable.stackTraceToString())
            }

            logQueue.offer(sb.toString())
        }

        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return true // All logs go to file
        }
    }
}