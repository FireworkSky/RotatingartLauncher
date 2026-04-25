package com.app.ralaunch.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LogFilePolicyTest {

    @Test
    fun appLogFileNameUsesDateWithoutPid() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-04-25")!!

        assertEquals("ralaunch_2026-04-25.log", LogFilePolicy.appLogFileName(date))
    }

    @Test
    fun logcatLogFileNameUsesDateWithoutPid() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-04-25")!!

        assertEquals("ralaunch_2026-04-25_logcat.log", LogFilePolicy.logcatLogFileName(date))
    }

    @Test
    fun logFilePredicatesSeparateAppAndLogcatFiles() {
        val dir = Files.createTempDirectory("ralaunch-log-policy-predicates").toFile()
        try {
            val appLog = File(dir, "ralaunch_2026-04-25.log").apply { writeText("app") }
            val logcatLog = File(dir, "ralaunch_2026-04-25_logcat.log").apply { writeText("logcat") }
            val legacyLog = File(dir, "ralaunch_2026-04-25_1234.log").apply { writeText("legacy") }

            assertTrue(LogFilePolicy.isAppLogFile(appLog))
            assertFalse(LogFilePolicy.isLogcatLogFile(appLog))
            assertTrue(LogFilePolicy.isManagedLogFile(appLog))

            assertFalse(LogFilePolicy.isAppLogFile(logcatLog))
            assertTrue(LogFilePolicy.isLogcatLogFile(logcatLog))
            assertTrue(LogFilePolicy.isManagedLogFile(logcatLog))

            assertFalse(LogFilePolicy.isAppLogFile(legacyLog))
            assertFalse(LogFilePolicy.isLogcatLogFile(legacyLog))
            assertFalse(LogFilePolicy.isManagedLogFile(legacyLog))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun filenameGenerationIsSafeAcrossConcurrentCallers() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-04-25")!!
        val executor = Executors.newFixedThreadPool(8)
        val names = Collections.synchronizedSet(mutableSetOf<String>())

        try {
            repeat(200) {
                executor.execute {
                    names += LogFilePolicy.appLogFileName(date)
                    names += LogFilePolicy.logcatLogFileName(date)
                }
            }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        assertEquals(setOf("ralaunch_2026-04-25.log", "ralaunch_2026-04-25_logcat.log"), names)
    }

    @Test
    fun retentionOnlySelectsCurrentManagedLogsOlderThanSevenDays() {
        val dir = Files.createTempDirectory("ralaunch-log-policy").toFile()
        try {
            val now = 1_000_000_000_000L
            val oldManaged = File(dir, "ralaunch_2026-04-01.log").apply {
                writeText("old")
                setLastModified(now - 8L * 24L * 60L * 60L * 1000L)
            }
            val oldLogcatManaged = File(dir, "ralaunch_2026-04-01_logcat.log").apply {
                writeText("old logcat")
                setLastModified(now - 8L * 24L * 60L * 60L * 1000L)
            }
            val oldLegacyLog = File(dir, "ralaunch_2026-04-01_1234.log").apply {
                writeText("old legacy")
                setLastModified(now - 8L * 24L * 60L * 60L * 1000L)
            }
            val recentManaged = File(dir, "ralaunch_2026-04-24.log").apply {
                writeText("recent")
                setLastModified(now - 2L * 24L * 60L * 60L * 1000L)
            }
            val unrelated = File(dir, "other.log").apply {
                writeText("other")
                setLastModified(now - 30L * 24L * 60L * 60L * 1000L)
            }

            val expired = LogFilePolicy.filesOlderThanRetention(dir, now)

            assertTrue(oldManaged in expired)
            assertTrue(oldLogcatManaged in expired)
            assertFalse(oldLegacyLog in expired)
            assertFalse(recentManaged in expired)
            assertFalse(unrelated in expired)
        } finally {
            dir.deleteRecursively()
        }
    }
}
