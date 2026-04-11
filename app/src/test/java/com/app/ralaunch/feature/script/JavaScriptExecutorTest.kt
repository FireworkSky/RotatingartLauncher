package com.app.ralaunch.feature.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class JavaScriptExecutorTest {

    private val executor = JavaScriptExecutor()
    private val hostBridge = HostBridge()

    @Test
    fun `evaluates simple expression`() {
        assertEquals(7.0, executor.eval("1 + 2 * 3"))
    }

    @Test
    fun `injects bindings into scope`() {
        val result = executor.eval(
            script = "greeting + ', ' + target",
            bindings = mapOf(
                "greeting" to "Hello",
                "target" to "Rhino"
            )
        )

        assertEquals("Hello, Rhino", result)
    }

    @Test
    fun `returns null for undefined result`() {
        assertNull(executor.eval("var answer = 42;"))
    }

    @Test
    fun `coerces result to requested java type`() {
        val result = executor.evalAs(
            script = "21 * 2",
            desiredType = Int::class.javaObjectType
        )

        assertEquals(42, result)
    }

    @Test
    fun `returns null from typed execute when result undefined`() {
        val result = executor.evalAs(
            script = "var answer = 42;",
            desiredType = String::class.java
        )

        assertNull(result)
    }

    @Test
    fun `infers type from reified overload`() {
        val result: Int? = executor.evalAs("21 * 2")

        assertEquals(42, result)
    }

    @Test
    fun `does not leak globals between evaluations`() {
        assertEquals(42.0, executor.eval("temporaryValue = 42; temporaryValue"))
        assertEquals("undefined", executor.eval("typeof temporaryValue"))
    }

    @Test
    fun `does not leak bindings between evaluations`() {
        assertEquals(
            "Hello",
            executor.eval(
                script = "greeting",
                bindings = mapOf("greeting" to "Hello")
            )
        )
        assertEquals("undefined", executor.eval("typeof greeting"))
    }

    @Test
    fun `invokes methods on bound java objects`() {
        val result = executor.eval(
            script = "bridge.describe(bridge.increment(41), bridge.label())",
            bindings = mapOf("bridge" to hostBridge)
        )

        assertEquals("bridge-42", result)
    }

    @Test
    fun `executes script file directly`() {
        val scriptFile = File.createTempFile("rhino-executor", ".js").apply {
            writeText("if (typeof total !== 'number') throw new Error('missing binding');")
            deleteOnExit()
        }

        executor.execute(
            scriptPath = scriptFile.absolutePath,
            bindings = mapOf("total" to 42)
        )
    }

    class HostBridge {
        fun increment(value: Int): Int = value + 1

        fun label(): String = "bridge"

        fun describe(value: Int, label: String): String = "$label-$value"
    }
}
