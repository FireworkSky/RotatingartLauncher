package com.app.ralaunch.utils

import android.util.Log
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
 */
object Logger {
    private const val TAG = "RALaunch"
    private const val ENABLE_DEBUG = false
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
    private const val MAX_FILES = 5 // 最多保留5个日志文件

    private var logDir: File? = null
    private var writer: PrintWriter? = null
    private var currentLogFile: File? = null
    private var initialized = false

    // 异步写入队列
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var writeThread: Thread? = null
    private val running = AtomicBoolean(false)

    fun init(logDirectory: File) {
        if (initialized) {
            Log.w(TAG, "AppLogger already initialized")
            return
        }

        logDir = logDirectory
        Log.i(TAG, "==================== AppLogger.init() START ====================")

        try {
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }

            // 清理旧日志文件
            cleanupOldLogs()

            // 创建当前日志文件
            currentLogFile = createLogFile()
            writer = PrintWriter(FileWriter(currentLogFile!!, true), true)

            // 启动写入线程
            running.set(true)
            startWriteThread()

            initialized = true
            info("Logger", "Log system initialized: ${logDirectory.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logging", e)
        }
    }

    fun close() {
        running.set(false)
        writeThread?.interrupt()
        writeThread = null

        writer?.flush()
        writer?.close()
        writer = null

        initialized = false
        Log.i(TAG, "AppLogger closed")
    }

    // 日志方法
    fun error(message: String, throwable: Throwable? = null, tag: String = TAG) {
        log(Level.ERROR, tag, message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null, tag: String = TAG) {
        log(Level.WARN, tag, message, throwable)
    }

    fun info(message: String, tag: String = TAG, ) {
        log(Level.INFO, tag, message, null)
    }

    fun debug(message: String, tag: String = TAG, ) {
        if (ENABLE_DEBUG) log(Level.DEBUG, tag, message, null)
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        // 1. 输出到 Logcat
        when (level) {
            Level.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.DEBUG -> if (ENABLE_DEBUG) Log.d(tag, message)
        }

        // 2. 异步写入文件
        if (initialized) {
            val logEntry = buildLogEntry(level, tag, message, throwable)
            logQueue.offer(logEntry)
        }
    }

    private fun buildLogEntry(level: Level, tag: String, message: String, throwable: Throwable?): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val levelChar = when (level) {
            Level.ERROR -> "E"
            Level.WARN -> "W"
            Level.INFO -> "I"
            Level.DEBUG -> "D"
        }

        val sb = StringBuilder()
        sb.append("[$timestamp] [$levelChar] [$tag] $message")

        throwable?.let {
            sb.append("\n").append(throwable.stackTraceToString())
        }

        return sb.toString()
    }

    private fun startWriteThread() {
        writeThread = thread(name = "LogWriter", isDaemon = true) {
            while (running.get()) {
                try {
                    // 批量写入，提高性能
                    val logs = mutableListOf<String>()
                    while (logQueue.isNotEmpty() && logs.size < 100) {
                        logQueue.poll()?.let { logs.add(it) }
                    }

                    if (logs.isNotEmpty()) {
                        writeToFile(logs)
                    }

                    // 检查文件大小，需要时轮转
                    checkFileSize()

                    Thread.sleep(100) // 降低 CPU 使用
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Log write error", e)
                }
            }

            // 退出前写入剩余日志
            flushRemainingLogs()
        }
    }

    private fun writeToFile(logs: List<String>) {
        try {
            logs.forEach { log ->
                writer?.println(log)
            }
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    private fun flushRemainingLogs() {
        val remainingLogs = mutableListOf<String>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { remainingLogs.add(it) }
        }
        if (remainingLogs.isNotEmpty()) {
            writeToFile(remainingLogs)
        }
    }

    private fun checkFileSize() {
        val file = currentLogFile ?: return
        if (file.length() > MAX_FILE_SIZE) {
            rotateLogFile()
        }
    }

    private fun rotateLogFile() {
        try {
            writer?.flush()
            writer?.close()

            // 重命名当前文件
            val oldFile = currentLogFile!!
            val newName = "ralaunch_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.log"
            val newFile = File(oldFile.parent, newName)
            oldFile.renameTo(newFile)

            // 创建新文件
            currentLogFile = createLogFile()
            writer = PrintWriter(FileWriter(currentLogFile!!, true), true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log file", e)
        }
    }

    private fun createLogFile(): File {
        val fileName = "ralaunch_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.log"
        return File(logDir, fileName).also { file ->
            if (!file.exists()) {
                file.createNewFile()
                // 写入文件头
                FileWriter(file, true).use { fw ->
                    fw.write("=== RALaunch Log Start ===\n")
                    fw.write("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                    fw.write("=======================\n\n")
                }
            }
        }
    }

    private fun cleanupOldLogs() {
        try {
            val logFiles = logDir?.listFiles { file ->
                file.name.startsWith("ralaunch_") && file.name.endsWith(".log")
            } ?: emptyArray()

            if (logFiles.size > MAX_FILES) {
                logFiles.sortedBy { it.lastModified() }
                    .take(logFiles.size - MAX_FILES)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old logs", e)
        }
    }

    fun getLogFile(): File? = currentLogFile

    enum class Level { ERROR, WARN, INFO, DEBUG }
}