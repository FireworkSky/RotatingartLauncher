package com.app.ralaunch.core.logging.service

import android.util.Log
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.LogLevel
import com.app.ralaunch.core.logging.contract.Logger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AndroidFileLogger(
    private val fileNameProvider: () -> String = { LogFilePolicy.appLogFileName() },
    private val emitToAndroidLog: Boolean = true,
    private val isFileLoggingEnabled: () -> Boolean = { true },
    private val logLevel: () -> LogLevel = { LogLevel.VERBOSE },
    private val logcatFileLogger: AndroidFileLogger? = null,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val writerStartGate: CountDownLatch? = null
) : Logger {
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var writer: AsyncLogWriter? = null
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
            val configuredLogFile = File(logDirectory, fileNameProvider())
            configuredLogFile.createNewFile()
            logFile = configuredLogFile
            writer = AsyncLogWriter(
                file = configuredLogFile,
                queueCapacity = queueCapacity,
                droppedLineFormatter = ::formatDroppedLine,
                startGate = writerStartGate
            )
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
        synchronized(lock) { writer }?.enqueue(
            line = line,
            priority = LogLevel.FileWritePriority.LOW
        )
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

        val formattedLine = buildString {
            append("[")
            append(formatTimestamp())
            append("] [")
            append(level.label)
            append("] [")
            append(tag)
            append("] ")
            append(message)
            if (throwable != null) {
                appendLine()
                append(throwable.stackTraceToString().trimEnd())
            }
        }

        synchronized(lock) { writer }?.enqueue(
            line = formattedLine,
            priority = level.fileWritePriority
        )
    }

    private fun shouldWriteFileLevel(level: LogLevel): Boolean = logLevel().allows(level)

    private fun formatTimestamp(): String = synchronized(timestampFormat) {
        timestampFormat.format(Date())
    }

    private fun formatDroppedLine(droppedCount: Int): String =
        "[${formatTimestamp()}] [W] [$TAG] Dropped $droppedCount log lines because the async log queue was full"

    private fun clearExpiredLogFiles(directory: File) {
        LogFilePolicy.filesOlderThanRetention(directory).forEach { file ->
            runCatching { FileUtils.deleteFileWithinRoot(file, directory) }
                .onFailure { w(TAG, "Failed to delete expired log file: ${file.absolutePath}", it) }
        }
    }

    private fun closeLocked() {
        writer?.close()
        writer = null
        logFile = null
    }

    companion object {
        private const val TAG = "AndroidFileLogger"
        private const val DEFAULT_QUEUE_CAPACITY = 2048
        private const val FLUSH_INTERVAL_MS = 500L
    }

    internal fun drainForTest(timeoutMillis: Long = 5_000L): Boolean =
        synchronized(lock) { writer }?.flush(timeoutMillis) ?: true

    private sealed interface QueueItem {
        data class Line(
            val value: String,
            val priority: LogLevel.FileWritePriority
        ) : QueueItem

        data class Flush(val complete: CountDownLatch? = null) : QueueItem

        data class Stop(val complete: CountDownLatch) : QueueItem
    }

    private class AsyncLogWriter(
        private val file: File,
        queueCapacity: Int,
        private val droppedLineFormatter: (Int) -> String,
        private val startGate: CountDownLatch?
    ) {
        private val queue = LinkedBlockingDeque<QueueItem>(queueCapacity.coerceAtLeast(1))
        private val droppedLineCount = AtomicInteger(0)
        private val closed = AtomicBoolean(false)
        private val writerThread = Thread(::run, "AndroidFileLogger-${file.name}").apply {
            isDaemon = true
            start()
        }

        fun enqueue(
            line: String,
            priority: LogLevel.FileWritePriority
        ) {
            if (closed.get()) return

            val item = QueueItem.Line(
                value = line,
                priority = priority
            )
            if (queue.offer(item)) return

            if (priority > LogLevel.FileWritePriority.LOW && dropQueuedLowPriorityLine()) {
                if (queue.offer(item)) return
            }

            droppedLineCount.incrementAndGet()
        }

        fun flush(timeoutMillis: Long): Boolean {
            if (closed.get()) return true

            val complete = CountDownLatch(1)
            if (!queue.offer(QueueItem.Flush(complete))) {
                return false
            }
            return try {
                complete.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            if (!writerThread.isAlive) return

            val complete = CountDownLatch(1)
            try {
                queue.put(QueueItem.Stop(complete))
                complete.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        private fun run() {
            try {
                startGate?.await()
                PrintWriter(BufferedWriter(FileWriter(file, true))).use { writer ->
                    var lastFlushAt = System.currentTimeMillis()
                    var running = true

                    while (running) {
                        when (val item = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                            is QueueItem.Line -> {
                                writeDroppedMarkerIfNeeded(writer)
                                writer.println(item.value)
                                if (item.priority.flushesImmediately()) {
                                    writer.flush()
                                    lastFlushAt = System.currentTimeMillis()
                                }
                            }

                            is QueueItem.Flush -> {
                                writeDroppedMarkerIfNeeded(writer)
                                writer.flush()
                                item.complete?.countDown()
                                lastFlushAt = System.currentTimeMillis()
                            }

                            is QueueItem.Stop -> {
                                drainRemainingLines(writer)
                                writeDroppedMarkerIfNeeded(writer)
                                writer.flush()
                                item.complete.countDown()
                                running = false
                                lastFlushAt = System.currentTimeMillis()
                            }

                            null -> {
                                writer.flush()
                                lastFlushAt = System.currentTimeMillis()
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastFlushAt >= FLUSH_INTERVAL_MS) {
                            writer.flush()
                            lastFlushAt = now
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                releasePendingControls()
            }
        }

        private fun drainRemainingLines(writer: PrintWriter) {
            while (true) {
                when (val item = queue.poll()) {
                    is QueueItem.Line -> {
                        writeDroppedMarkerIfNeeded(writer)
                        writer.println(item.value)
                    }

                    is QueueItem.Flush -> item.complete?.countDown()
                    is QueueItem.Stop -> item.complete.countDown()
                    null -> return
                }
            }
        }

        private fun releasePendingControls() {
            while (true) {
                when (val item = queue.poll()) {
                    is QueueItem.Flush -> item.complete?.countDown()
                    is QueueItem.Stop -> item.complete.countDown()
                    is QueueItem.Line -> Unit
                    null -> return
                }
            }
        }

        private fun writeDroppedMarkerIfNeeded(writer: PrintWriter) {
            val dropped = droppedLineCount.getAndSet(0)
            if (dropped > 0) {
                writer.println(droppedLineFormatter(dropped))
            }
        }

        private fun dropQueuedLowPriorityLine(): Boolean {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item is QueueItem.Line && item.priority <= LogLevel.FileWritePriority.LOW) {
                    iterator.remove()
                    droppedLineCount.incrementAndGet()
                    return true
                }
            }
            return false
        }
    }
}
