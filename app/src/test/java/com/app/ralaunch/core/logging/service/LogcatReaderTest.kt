package com.app.ralaunch.core.logging.service

import com.app.ralaunch.core.logging.LogLevel
import com.app.ralaunch.core.logging.contract.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LogcatReaderTest {

    @Test
    fun tagBlacklistUsesExactMatchesSoActivityNamedAppTagsAreKept() {
        val reader = LogcatReader(RecordingLogger(), disabledFileLogger())

        assertFalse(reader.shouldFilterTag("MainActivityCompose"))
        assertFalse(reader.shouldFilterTag("GameActivity"))
        assertTrue(reader.shouldFilterTag("Activity"))
    }

    @Test
    fun processLogLineWritesFirstPartyActivityTags() {
        val dir = Files.createTempDirectory("ralaunch-logcat-reader").toFile()
        try {
            val fileLogger = AndroidFileLogger(
                fileNameProvider = { "logcat.log" },
                emitToAndroidLog = false
            )
            fileLogger.configure(dir, enabled = true)
            val reader = LogcatReader(RecordingLogger(), fileLogger)

            reader.processLogLine("04-25 12:00:00.000 I/MainActivityCompose( 123): app message")

            val content = fileLogger.currentLogFile()?.readText().orEmpty()
            assertTrue(content.contains("[04-25 12:00:00.000] [I] [MainActivityCompose] [123] app message"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun processLogLineParsesThreadtimeLinesWithPaddedTags() {
        val dir = Files.createTempDirectory("ralaunch-logcat-threadtime").toFile()
        try {
            val fileLogger = AndroidFileLogger(
                fileNameProvider = { "logcat.log" },
                emitToAndroidLog = false
            )
            fileLogger.configure(dir, enabled = true)
            val reader = LogcatReader(RecordingLogger(), fileLogger)

            reader.processLogLine(
                "04-25 23:31:03.760  2174  2625 I SDM     : " +
                    "DisplayBuiltIn::IdlePowerCollapse: IPC received, disabling partial update for one frame"
            )

            val content = fileLogger.currentLogFile()?.readText().orEmpty()
            assertTrue(
                content.contains(
                    "[04-25 23:31:03.760] [I] [SDM] [2174:2625] " +
                        "DisplayBuiltIn::IdlePowerCollapse: IPC received, disabling partial update for one frame"
                )
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun parseLineHandlesOptionalUidInThreadtimeOutput() {
        val reader = LogcatReader(RecordingLogger(), disabledFileLogger())

        val parsed = reader.parseLine("04-25 23:31:03.758 u0_a123 2206 2270 E PERFHAL-PERFCTRL: limit reached")

        assertEquals(
            LogcatReader.ParsedLogLine(
                timestamp = "04-25 23:31:03.758",
                pid = 2206,
                tid = 2270,
                level = "E",
                tag = "PERFHAL-PERFCTRL",
                message = "limit reached"
            ),
            parsed
        )
    }

    @Test
    fun processLogLineDropsExactSystemBlacklistTag() {
        val dir = Files.createTempDirectory("ralaunch-logcat-filter").toFile()
        try {
            val fileLogger = AndroidFileLogger(
                fileNameProvider = { "logcat.log" },
                emitToAndroidLog = false
            )
            fileLogger.configure(dir, enabled = true)
            val reader = LogcatReader(RecordingLogger(), fileLogger)

            reader.processLogLine("04-25 12:00:00.000 I/Activity( 123): framework message")

            assertTrue(fileLogger.currentLogFile()?.readText().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildCommandUsesVerbosePriorityWhenRequested() {
        val reader = LogcatReader(RecordingLogger(), disabledFileLogger())

        assertEquals(
            "logcat --pid=123 -v threadtime *:V",
            reader.buildCommand(123, minLevel = LogLevel.VERBOSE)
        )
        assertEquals(
            "logcat --pid=123 -v threadtime *:I",
            reader.buildCommand(123, minLevel = LogLevel.INFO)
        )
    }

    private fun disabledFileLogger(): AndroidFileLogger =
        AndroidFileLogger(emitToAndroidLog = false)

    private class RecordingLogger : Logger {
        override fun v(tag: String, message: String): Int = 0
        override fun v(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun d(tag: String, message: String): Int = 0
        override fun d(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun i(tag: String, message: String): Int = 0
        override fun i(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun w(tag: String, message: String): Int = 0
        override fun w(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun e(tag: String, message: String): Int = 0
        override fun e(tag: String, message: String, throwable: Throwable?): Int = 0
    }
}
