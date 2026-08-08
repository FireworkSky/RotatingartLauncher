package com.app.ralaunch.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import com.app.ralaunch.BuildConfig
import com.app.ralaunch.ConfigurationState
import kotlin.time.Duration.Companion.milliseconds

/*******************************************************************************
 * RotatingArtLauncher - AppLogger
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/4)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object AppLogger {
    const val LOG_FILE_MIN_SIZE_MB = 1
    const val LOG_FILE_MAX_SIZE_MB = 20

    const val LOG_FILE_MIN_COUNT = 1
    const val LOG_FILE_MAX_COUNT = 20

    private const val LOG_DIR = "logs"
    private val MAX_FILE_SIZE_BYTES = ConfigurationState.logFileMaxSize // 10MB
    private val MAX_LOG_FILES = ConfigurationState.logFileMaxCount
    private const val LOG_EXTENSION = ".log"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    private const val DEFAULT_TAG = "RaLaunchApp"

    private var logDir: File? = null
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var isWriting = false
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false

    // 日志级别过滤 - 默认记录所有级别
    private var currentLogLevel = ConfigurationState.logLevel

    // 是否启用文件日志（默认启用）
    private var fileLogEnabled = ConfigurationState.logFileEnabled

    /**
     * 日志级别枚举
     * 用于控制日志输出过滤
     */
    enum class LogLevel(val priority: Int, val tag: String) {
        VERBOSE(Log.VERBOSE, "V"),
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),
        ASSERT(Log.ASSERT, "A"),
        NONE(Int.MAX_VALUE, "NONE");  // 不输出任何日志

        fun string(): String =
            when (this) {
                VERBOSE -> "VERBOSE"
                DEBUG -> "DEBUG"
                INFO -> "INFO"
                WARN -> "WARN"
                ERROR -> "ERROR"
                ASSERT -> "ASSERT"
                NONE -> "NONE"
            }
    }

    /**
     * 初始化日志系统
     * 应在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        if (isInitialized) return

        // 获取应用私有外部存储目录（Android 10+ 无需存储权限）
        logDir = File(context.getExternalFilesDir(null), LOG_DIR)
        if (!logDir!!.exists()) {
            logDir!!.mkdirs()
        }

        // 清理旧的日志文件
        cleanOldLogs()

        // 种植 Logcat Tree
        Timber.plant(LogcatTree())

        // 种植文件日志 Tree
        Timber.plant(FileTree())

        isInitialized = true

        // 输出启动日志
        Timber.i("AppLogger initialized. Log directory: ${logDir?.absolutePath}")
        Timber.d("Current log level: ${currentLogLevel.tag}")
        Timber.d("File log enabled: $fileLogEnabled")
    }

    /**
     * 检查是否应该输出该级别的日志
     */
    private fun shouldLog(priority: Int): Boolean {
        return priority >= currentLogLevel.priority
    }

    /**
     * 清理过期日志文件
     */
    private fun cleanOldLogs() {
        val files = logDir?.listFiles { file ->
            file.isFile && file.extension == "log"
        } ?: return

        // 按修改时间排序
        val sortedFiles = files.sortedByDescending { it.lastModified() }

        // 删除超过数量限制的文件
        if (sortedFiles.size > MAX_LOG_FILES) {
            sortedFiles.drop(MAX_LOG_FILES).forEach { it.delete() }
        }

        // 计算总大小并删除最旧的文件
        var totalSize = sortedFiles.sumOf { it.length() }
        if (totalSize > MAX_FILE_SIZE_BYTES * MAX_LOG_FILES) {
            val toDelete = sortedFiles.drop(MAX_LOG_FILES / 2)
            toDelete.forEach {
                totalSize -= it.length()
                it.delete()
            }
        }
    }

    /**
     * 获取当前日志文件
     */
    private fun getCurrentLogFile(): File? {
        val dir = logDir ?: return null
        val dateStr = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val fileName = "$dateStr$LOG_EXTENSION"
        val file = File(dir, fileName)

        // 如果文件超过大小限制，创建新文件
        if (file.exists() && file.length() >= MAX_FILE_SIZE_BYTES) {
            val timestamp = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date())
            val newFileName = "${dateStr}_$timestamp$LOG_EXTENSION"
            return File(dir, newFileName)
        }

        return file
    }

    /**
     * 异步写入日志到文件
     */
    private fun writeLogToFile(logEntry: String) {
        // 如果文件日志被禁用，直接返回
        if (!fileLogEnabled) {
            return
        }

        logQueue.offer(logEntry)

        if (!isWriting) {
            isWriting = true
            writeScope.launch {
                processLogQueue()
            }
        }
    }

    /**
     * 处理日志队列
     */
    @SuppressLint("LogNotTimber")
    private suspend fun processLogQueue() {
        while (logQueue.isNotEmpty()) {
            val logEntry = logQueue.poll() ?: break

            try {
                val file = getCurrentLogFile() ?: continue
                withContext(Dispatchers.IO) {
                    BufferedWriter(FileWriter(file, true)).use { writer ->
                        writer.write(logEntry)
                        writer.newLine()
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                // 写入失败时输出到 Logcat 作为备用
                Log.e(DEFAULT_TAG, "Failed to write log: ${e.message}")
            }
        }
        isWriting = false
    }

    /**
     * 格式化日志消息
     */
    private fun formatLogMessage(
        priority: Int,
        message: String,
        throwable: Throwable?
    ): String {
        val timestamp = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date())
        val priorityStr = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "U"
        }
        val msg = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        return "$timestamp $priorityStr/$DEFAULT_TAG: $msg"
    }

    /**
     * Logcat Tree：输出到 Logcat
     */
    private class LogcatTree : Timber.Tree() {
        @SuppressLint("LogNotTimber")
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 检查是否达到输出级别
            if (!shouldLog(priority)) return

            // 统一使用 DEFAULT_TAG
            when (priority) {
                Log.VERBOSE -> Log.v(DEFAULT_TAG, message)
                Log.DEBUG -> Log.d(DEFAULT_TAG, message)
                Log.INFO -> Log.i(DEFAULT_TAG, message)
                Log.WARN -> Log.w(DEFAULT_TAG, message)
                Log.ERROR -> Log.e(DEFAULT_TAG, message, t)
                Log.ASSERT -> Log.wtf(DEFAULT_TAG, message, t)
                else -> Log.println(priority, DEFAULT_TAG, message)
            }
        }
    }

    /**
     * 文件日志 Tree：将日志写入文件
     */
    private class FileTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 检查是否达到输出级别
            if (!shouldLog(priority)) return

            val formatted = formatLogMessage(priority, message, t)
            writeLogToFile(formatted)
        }
    }

    /**
     * 手动刷新日志队列（在应用退出前调用）
     */
    fun flush() {
        writeScope.launch {
            while (logQueue.isNotEmpty()) {
                delay(100.milliseconds)
            }
        }
    }

    /**
     * 获取所有日志文件列表
     */
    fun getLogFiles(): List<File>? {
        return logDir?.listFiles { file ->
            file.isFile
        }?.sortedByDescending { it.lastModified() }
    }

    /**
     * 清空所有日志
     */
    fun clearLogs() {
        logDir?.listFiles { file ->
            file.isFile
        }?.forEach { it.delete() }
    }
}