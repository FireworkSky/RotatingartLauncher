package com.app.ralaunch.core.platform.runtime

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BlackBoxLogger {

    private const val TAG = "BlackBoxLogger"
    private var isRecording = false
    private var isJavaArmed = false
    private var logcatProcess: java.lang.Process? = null

    fun startRecording(context: Context) {
        val crashDir = File(context.getExternalFilesDir(null), "crashreport")
        if (!crashDir.exists()) crashDir.mkdirs()

        val logFile = File(crashDir, "GAME_CRASH_REPORT.txt")
        if (logFile.exists()) logFile.delete()

        runCatching { File(context.filesDir, "FATAL_CRASH.txt").delete() }

        catchJavaCrashes(context, logFile)
        catchNativeCrashes(logFile)
    }

    fun stopRecording() {
        isRecording = false
        try {
            logcatProcess?.destroy()
        } catch (t: Throwable) {}
    }

    private fun catchJavaCrashes(context: Context, logFile: File) {
        if (isJavaArmed) return
        isJavaArmed = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeThrowableReport(context, logFile, thread, throwable)
                Thread.sleep(500)
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun catchNativeCrashes(logFile: File) {
        if (isRecording) return

        Thread {
            try {
                isRecording = true
                val myPid = Process.myPid().toString()

                runCatching { Runtime.getRuntime().exec("logcat -c").waitFor() }
                
                logcatProcess = Runtime.getRuntime().exec("logcat -b crash,main -v time --pid=$myPid *:E")

                logcatProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var isFileInitialized = false
                    var writer: java.io.PrintWriter? = null

                    try {
                        while (isRecording) {
                            val line = reader.readLine() ?: break
                            
                            if (!isFileInitialized) {
                                writer = java.io.PrintWriter(java.io.FileOutputStream(logFile, true))
                                writer.println("=========================================")
                                writer.println("💀 NATIVE / SYSTEM CRASH LOG 💀")
                                writer.println("=========================================")
                                isFileInitialized = true
                            }

                            writer?.println(line)
                            writer?.flush()
                        }
                    } finally {
                        writer?.close()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Logger malfunction", t)
            } finally {
                isRecording = false
            }
        }.start()
    }

    private fun writeThrowableReport(
        context: Context,
        logFile: File,
        thread: Thread?,
        throwable: Throwable
    ) {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) { "Unknown" }

        val activityManager = try {
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        } catch (_: Throwable) { null }

        val memoryInfo = try {
            ActivityManager.MemoryInfo().also { info -> activityManager?.getMemoryInfo(info) }
        } catch (_: Throwable) { null }

        val memoryClass = try { activityManager?.memoryClass?.toString() ?: "Unknown" } catch (_: Throwable) { "Unknown" }
        val processName = try { if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else context.packageName } catch (_: Throwable) { context.packageName }

        val rootCauseElement = throwable.stackTrace.firstOrNull()
        val errorFile = rootCauseElement?.fileName ?: "Unknown File"
        val errorLine = rootCauseElement?.lineNumber?.toString() ?: "Unknown Line"
        val errorMethod = "${rootCauseElement?.className}.${rootCauseElement?.methodName}"
        
        val internalFatal = File(context.filesDir, "FATAL_CRASH.txt")
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val vipReport = buildString {
            appendLine("=========================================")
            appendLine("🚨 JAVA / MANAGED CRASH REPORT 🚨")
            appendLine("=========================================")
            appendLine("🕒 TIME      : ${timeFormat.format(Date())}")
            appendLine("📱 DEVICE    : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("📦 VERSION   : $appVersion")
            appendLine("🧵 THREAD    : ${thread?.name ?: "Unknown"}")
            appendLine("🧬 PROCESS   : $processName")
            appendLine("🏗️ ABI       : ${getAbiSummary()}")
            appendLine("-----------------------------------------")
            appendLine("🎮 RENDERER  : ${safeEnv("RALCORE_RENDERER", "native")}")
            appendLine("🧪 EGL       : ${safeEnv("RALCORE_EGL", "system")}")
            appendLine("🧩 GLES      : ${safeEnv("LIBGL_GLES", "system")}")
            appendLine("📚 FNA3D     : ${safeEnv("FNA3D_OPENGL_LIBRARY", "default")}")
            appendLine("-----------------------------------------")
            appendLine("🧠 MEM CLASS : ${memoryClass}MB")
            appendLine("💾 AVAIL MEM : ${memoryInfo?.availMem?.div(1024 * 1024) ?: -1}MB")
            appendLine("⚠️ LOW MEM   : ${memoryInfo?.lowMemory ?: "Unknown"}")
            appendLine("-----------------------------------------")
            appendLine("📁 FILE      : $errorFile")
            appendLine("🔢 LINE      : Line $errorLine")
            appendLine("⚙️ METHOD    : $errorMethod")
            appendLine("-----------------------------------------")
            appendLine("💀 TYPE      : ${throwable.javaClass.name}")
            appendLine("💬 MESSAGE   : ${throwable.message}")
            appendLine("=========================================")
            appendLine("📚 STACKTRACE:")

            throwable.stackTrace.forEach { appendLine("  at $it") }

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("🔄 CAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                cause.stackTrace.forEach { appendLine("  at $it") }
                cause = cause.cause
            }
        }

        java.io.FileOutputStream(logFile, true).use { fos ->
            fos.write(vipReport.toByteArray())
            fos.write("\n\n".toByteArray())
        }
        
        runCatching { internalFatal.writeText(vipReport) }
    }

    private fun safeEnv(key: String, fallback: String): String {
        return try { Os.getenv(key) ?: fallback } catch (_: Throwable) { fallback }
    }

    private fun getAbiSummary(): String {
        return try { Build.SUPPORTED_ABIS.joinToString(", ") } catch (_: Throwable) { "Unknown" }
    }
}
