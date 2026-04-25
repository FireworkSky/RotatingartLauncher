package com.app.ralaunch.core.logging.service

import com.app.ralaunch.core.logging.LogLevel
import com.app.ralaunch.core.logging.contract.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class LogcatReader(
    private val logger: Logger,
    private val fileLogger: AndroidFileLogger
) {
    private var readerThread: Thread? = null
    private var logcatProcess: Process? = null
    private val running = AtomicBoolean(false)

    @JvmOverloads
    fun start(filterTags: Array<String>? = null, minLevel: LogLevel = LogLevel.VERBOSE) {
        if (running.get()) {
            logger.w(TAG, "LogcatReader already running")
            return
        }

        val pid = android.os.Process.myPid()

        running.set(true)
        readerThread = Thread({
            try {
                val command = buildCommand(pid, filterTags, minLevel)

                logcatProcess = Runtime.getRuntime().exec(command)
                BufferedReader(InputStreamReader(logcatProcess!!.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        processLogLine(line)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    logger.e(TAG, "LogcatReader error", e)
                }
            }
        }, "LogcatReader").apply {
            isDaemon = true
            start()
        }
        logger.i(TAG, "LogcatReader started, logging to: ${fileLogger.currentLogFile()?.absolutePath}")
    }

    fun stop() {
        running.set(false)
        logcatProcess?.destroy()
        logcatProcess = null
        readerThread?.interrupt()
        readerThread = null
        logger.i(TAG, "LogcatReader stopped")
    }

    val isRunning: Boolean get() = running.get()

    internal fun buildCommand(
        pid: Int,
        filterTags: Array<String>? = null,
        minLevel: LogLevel = LogLevel.VERBOSE
    ): String = buildString {
        append("logcat --pid=$pid -v threadtime")
        if (!filterTags.isNullOrEmpty()) {
            append(" *:S")
            filterTags.forEach { append(" $it:${minLevel.logcatPriority}") }
        } else {
            append(" *:${minLevel.logcatPriority}")
        }
    }

    internal fun processLogLine(line: String) {
        if (line.isEmpty() || line.startsWith("---------")) return

        val parsed = parseLine(line) ?: return
        if (shouldFilterTag(parsed.tag)) return

        fileLogger.writeRawLine(parsed.format())
    }

    internal fun parseLine(line: String): ParsedLogLine? {
        THREADTIME_LINE.matchEntire(line)?.let { match ->
            return ParsedLogLine(
                timestamp = match.groupValues[1],
                pid = match.groupValues[2].toIntOrNull(),
                tid = match.groupValues[3].toIntOrNull(),
                level = match.groupValues[4],
                tag = match.groupValues[5].trim(),
                message = match.groupValues[6]
            )
        }

        TIME_LINE.matchEntire(line)?.let { match ->
            return ParsedLogLine(
                timestamp = match.groupValues[1],
                pid = match.groupValues[4].toIntOrNull(),
                tid = null,
                level = match.groupValues[2],
                tag = match.groupValues[3].trim(),
                message = match.groupValues[5]
            )
        }

        return null
    }

    internal fun shouldFilterTag(tag: String?): Boolean {
        if (tag == null) return true
        return SYSTEM_TAG_BLACKLIST.any { tag == it }
    }

    companion object {
        private const val TAG = "LogcatReader"

        private val THREADTIME_LINE = Regex(
            "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})" +
                "(?:\\s+(?!\\d+\\s)[0-9A-Za-z_.]+)?\\s+(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+" +
                "(.+?)\\s*:\\s?(.*)$"
        )
        private val TIME_LINE = Regex(
            "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+" +
                "([A-Z])/(.+?)\\(\\s*(\\d+)\\):\\s?(.*)$"
        )

        private val SYSTEM_TAG_BLACKLIST = arrayOf(
            "ScrollerOptimizationManager", "HWUI", "NativeTurboSchedManager",
            "TurboSchedMonitor", "MiuiMultiWindowUtils", "MiuiProcessManagerImpl",
            "FramePredict", "FirstFrameSpeedUp", "InsetsController", "ViewRootImpl",
            "Choreographer", "HandWritingStubImpl", "ViewRootImplStubImpl",
            "MiInputConsumer", "CompatChangeReporter", "ContentCatcher", "SecurityManager",
            "ComputilityLevel", "Activity", "libc", "SplineOverScroller",
            "BufferQueueProducer", "BLASTBufferQueue",
            "oplus.android.OplusFrameworkFactoryImpl", "DynamicFramerate",
            "OplusViewDragTouchViewHelper"
        )
    }

    internal data class ParsedLogLine(
        val timestamp: String,
        val pid: Int?,
        val tid: Int?,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val processInfo = when {
                pid != null && tid != null -> " [$pid:$tid]"
                pid != null -> " [$pid]"
                else -> ""
            }
            return "[$timestamp] [$level] [$tag]$processInfo $message"
        }
    }
}
