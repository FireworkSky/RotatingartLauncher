package com.app.ralaunch.core.logging

import com.app.ralaunch.core.logging.contract.Logger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppLogTest {

    @After
    fun tearDown() {
        AppLog.reset()
    }

    @Test
    fun installedLoggerReceivesInfoCalls() {
        val logger = RecordingLogger()

        AppLog.install(logger)
        val result = AppLog.i("TestTag", "hello")

        assertEquals(7, result)
        assertEquals(LogCall("i", "TestTag", "hello", null), logger.calls.single())
    }

    @Test
    fun installedLoggerReceivesThrowableCalls() {
        val logger = RecordingLogger()
        val throwable = IllegalStateException("boom")

        AppLog.install(logger)
        val result = AppLog.e("TestTag", "failed", throwable)

        assertEquals(7, result)
        val call = logger.calls.single()
        assertEquals("e", call.level)
        assertEquals("TestTag", call.tag)
        assertEquals("failed", call.message)
        assertSame(throwable, call.throwable)
    }

    private data class LogCall(
        val level: String,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    private class RecordingLogger : Logger {
        val calls = mutableListOf<LogCall>()

        override fun v(tag: String, message: String): Int = record("v", tag, message, null)
        override fun v(tag: String, message: String, throwable: Throwable?): Int = record("v", tag, message, throwable)
        override fun d(tag: String, message: String): Int = record("d", tag, message, null)
        override fun d(tag: String, message: String, throwable: Throwable?): Int = record("d", tag, message, throwable)
        override fun i(tag: String, message: String): Int = record("i", tag, message, null)
        override fun i(tag: String, message: String, throwable: Throwable?): Int = record("i", tag, message, throwable)
        override fun w(tag: String, message: String): Int = record("w", tag, message, null)
        override fun w(tag: String, message: String, throwable: Throwable?): Int = record("w", tag, message, throwable)
        override fun e(tag: String, message: String): Int = record("e", tag, message, null)
        override fun e(tag: String, message: String, throwable: Throwable?): Int = record("e", tag, message, throwable)

        private fun record(level: String, tag: String, message: String, throwable: Throwable?): Int {
            calls += LogCall(level, tag, message, throwable)
            return 7
        }
    }
}
