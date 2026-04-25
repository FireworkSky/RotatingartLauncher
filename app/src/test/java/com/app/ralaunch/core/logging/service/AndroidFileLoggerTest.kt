package com.app.ralaunch.core.logging.service

import com.app.ralaunch.core.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AndroidFileLoggerTest {

    @Test
    fun configuredLoggerWritesFormattedAppLogLines() {
        val dir = Files.createTempDirectory("ralaunch-android-file-logger").toFile()
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "app.log" },
                emitToAndroidLog = false
            )

            logger.configure(dir, enabled = true)
            logger.i("TestTag", "hello")

            val logFile = logger.currentLogFile()
            requireNotNull(logFile)
            val content = logFile.readText()
            assertTrue(content.contains("[I] [TestTag] hello"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rawLineWriterUsesConfiguredFileWithoutReformatting() {
        val dir = Files.createTempDirectory("ralaunch-logcat-file-logger").toFile()
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "logcat.log" },
                emitToAndroidLog = false
            )

            logger.configure(dir, enabled = true)
            logger.writeRawLine("[04-25 12:00:00.000] [I] [TestTag] raw message")

            val logFile = logger.currentLogFile()
            requireNotNull(logFile)
            assertEquals("[04-25 12:00:00.000] [I] [TestTag] raw message\n", logFile.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun disabledConfigurationDoesNotCreateCurrentFile() {
        val dir = Files.createTempDirectory("ralaunch-disabled-file-logger").toFile()
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "disabled.log" },
                emitToAndroidLog = false
            )

            logger.configure(dir, enabled = false)
            logger.i("TestTag", "ignored")

            assertNull(logger.currentLogFile())
            assertTrue(dir.listFiles()?.isEmpty() ?: true)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun closeClearsCurrentFile() {
        val dir = Files.createTempDirectory("ralaunch-close-file-logger").toFile()
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "close.log" },
                emitToAndroidLog = false
            )

            logger.configure(dir, enabled = true)
            requireNotNull(logger.currentLogFile())

            logger.close()

            assertNull(logger.currentLogFile())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun refreshConfigurationCanEnableLoggingAfterDisabledStart() {
        val dir = Files.createTempDirectory("ralaunch-refresh-file-logger").toFile()
        var enabled = false
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "refresh.log" },
                emitToAndroidLog = false,
                isFileLoggingEnabled = { enabled }
            )

            logger.start(dir)
            assertNull(logger.currentLogFile())

            enabled = true
            logger.refreshConfiguration()
            logger.i("TestTag", "enabled later")

            val logFile = logger.currentLogFile()
            requireNotNull(logFile)
            assertTrue(logFile.readText().contains("[I] [TestTag] enabled later"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun logLevelControlsDebugFileWrites() {
        val dir = Files.createTempDirectory("ralaunch-verbose-file-logger").toFile()
        var logLevel = LogLevel.INFO
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "verbose.log" },
                emitToAndroidLog = false,
                logLevel = { logLevel }
            )

            logger.configure(dir, enabled = true)
            logger.d("TestTag", "debug hidden")
            assertEquals("", logger.currentLogFile()?.readText())

            logLevel = LogLevel.DEBUG
            logger.d("TestTag", "debug visible")
            assertTrue(logger.currentLogFile()?.readText()?.contains("[D] [TestTag] debug visible") == true)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun refreshConfigurationAppliesVerboseLogLevelChanges() {
        val dir = Files.createTempDirectory("ralaunch-refresh-log-level").toFile()
        var logLevel = LogLevel.INFO
        try {
            val logger = AndroidFileLogger(
                fileNameProvider = { "refresh-level.log" },
                emitToAndroidLog = false,
                logLevel = { logLevel }
            )

            logger.start(dir)
            logger.v("TestTag", "verbose hidden")
            assertTrue(logger.currentLogFile()?.readText()?.contains("verbose hidden") == false)

            logLevel = LogLevel.VERBOSE
            logger.refreshConfiguration()
            logger.v("TestTag", "verbose visible")

            assertTrue(logger.currentLogFile()?.readText()?.contains("[V] [TestTag] verbose visible") == true)
        } finally {
            dir.deleteRecursively()
        }
    }
}
