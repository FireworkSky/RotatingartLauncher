package com.app.ralaunch.feature.script

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import kotlin.io.path.Path
import kotlin.io.path.readText

class JavaScriptExecutor {

    private val sharedScope: ScriptableObject by lazy {
        withContext { context ->
            configureContext(context)

            context.initSafeStandardObjects(null, true).apply {
                parentScope = null
            }
        }
    }

    /**
     * Evaluates JavaScript in a fresh execution scope backed by a shared safe Rhino scope.
     *
     * Unsafe: Rhino still executes arbitrary code, and any bound Java object may expose access back
     * into JVM APIs. Do not use this with untrusted scripts or untrusted bindings.
     *
     * Returns `null` when the script result is `undefined` or `null`.
     *
     * @param script JavaScript source to evaluate.
     * @param bindings values exposed as globals in the script scope.
     * @param sourceName logical source name used in Rhino error reporting.
     */
    fun eval(
        script: String,
        bindings: Map<String, Any?> = emptyMap(),
        sourceName: String = DEFAULT_SOURCE_NAME
    ): Any? {
        return withContext { context ->
            configureContext(context)

            val scope = createExecutionScope(context)
            bindings.forEach { (key, value) ->
                ScriptableObject.putProperty(scope, key, Context.javaToJS(value, scope))
            }

            normalizeResult(context.evaluateString(scope, script, sourceName, 1, null))
        }
    }

    /**
     * Evaluates JavaScript and coerces the result to the requested Java type via Rhino.
     *
     * Unsafe: Rhino still executes arbitrary code, and any bound Java object may expose access back
     * into JVM APIs. Do not use this with untrusted scripts or untrusted bindings.
     *
     * Returns `null` when the script result is `undefined` or `null`.
     *
     * @param script JavaScript source to evaluate.
     * @param desiredType target Java type for Rhino conversion.
     * @param bindings values exposed as globals in the script scope.
     * @param sourceName logical source name used in Rhino error reporting.
     */
    fun <T> evalAs(
        script: String,
        desiredType: Class<T>,
        bindings: Map<String, Any?> = emptyMap(),
        sourceName: String = DEFAULT_SOURCE_NAME
    ): T? {
        val result = eval(
            script = script,
            bindings = bindings,
            sourceName = sourceName
        ) ?: return null

        @Suppress("UNCHECKED_CAST")
        return Context.jsToJava(result, desiredType) as T
    }

    /**
     * Kotlin-friendly overload that infers the target Java type from [T].
     *
     * Unsafe: Rhino still executes arbitrary code, and any bound Java object may expose access back
     * into JVM APIs. Do not use this with untrusted scripts or untrusted bindings.
     *
     * Returns `null` when the script result is `undefined` or `null`.
     *
     * @param script JavaScript source to evaluate.
     * @param bindings values exposed as globals in the script scope.
     * @param sourceName logical source name used in Rhino error reporting.
     */
    inline fun <reified T> evalAs(
        script: String,
        bindings: Map<String, Any?> = emptyMap(),
        sourceName: String = DEFAULT_SOURCE_NAME
    ): T? {
        return evalAs(
            script = script,
            desiredType = T::class.java,
            bindings = bindings,
            sourceName = sourceName
        )
    }

    /**
     * Executes a JavaScript file in a fresh execution scope backed by a shared safe Rhino scope.
     *
     * Unsafe: Rhino still executes arbitrary code, and any bound Java object may expose access back
     * into JVM APIs. Do not use this with untrusted scripts or untrusted bindings.
     *
     * The script file path is used as the Rhino source name for error reporting.
     *
     * @param scriptPath JavaScript file to read and execute.
     * @param bindings values exposed as globals in the script scope.
     */
    fun execute(
        scriptPath: String,
        bindings: Map<String, Any?> = emptyMap()
    ) {
        eval(
            script = Path(scriptPath).readText(),
            bindings = bindings,
            sourceName = Path(scriptPath).toAbsolutePath().toString()
        )
    }

    private fun createExecutionScope(context: Context): Scriptable {
        return context.newObject(sharedScope).apply {
            prototype = sharedScope
            parentScope = null
        }
    }

    private fun configureContext(context: Context) {
        context.isInterpretedMode = true
    }

    private inline fun <T> withContext(block: (Context) -> T): T {
        val context = Context.enter()

        return try {
            block(context)
        } catch (exception: JavaScriptExecutionException) {
            throw exception
        } catch (exception: Exception) {
            throw JavaScriptExecutionException(
                message = "Failed to execute JavaScript.",
                cause = exception
            )
        } finally {
            Context.exit()
        }
    }

    private fun normalizeResult(result: Any?): Any? {
        return when (result) {
            null,
            Undefined.instance,
            Context.getUndefinedValue() -> null

            is Wrapper -> result.unwrap()
            else -> result
        }
    }

    companion object {
        const val DEFAULT_SOURCE_NAME = "inline-script"
    }
}

class JavaScriptExecutionException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
