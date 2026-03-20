package com.app.ralaunch.core.platform.runtime

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
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

    fun startRecording(context: Context) {
        val crashDir = File(context.getExternalFilesDir(null), "crashreport")
        if (!crashDir.exists()) crashDir.mkdirs()
        
        clearOldReports(context, crashDir)
        
        catchJavaCrashes(context, crashDir)
        catchNativeCrashes(context, crashDir)
    }

    fun stopRecording() {
        isRecording = false
    }

    private fun clearOldReports(context: Context, crashDir: File) {
        runCatching {
            crashDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
        }
        runCatching { File(context.filesDir, "FATAL_CRASH.txt").delete() }
        Log.i(TAG, "Old log files cleared.")
    }

    private fun catchJavaCrashes(context: Context, crashDir: File) {
        if (isJavaArmed) return
        isJavaArmed = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeThrowableReport(context, crashDir, thread, throwable, "APP_CRASH")
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "VIP Java Crash Catcher Armed")
    }

    private fun catchNativeCrashes(context: Context, crashDir: File) {
        if (isRecording) return

        Thread {
            try {
                isRecording = true
                val logFile = File(crashDir, "GAME_NATIVE_CRASH.txt")
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                Log.i(TAG, "Black Box activated! Recording to: ${logFile.absolutePath}")

                runCatching { Runtime.getRuntime().exec("logcat -c").waitFor() }
                val process = Runtime.getRuntime().exec("logcat -v threadtime")

                process.inputStream.bufferedReader().use { reader ->
                    logFile.printWriter().use { writer ->
                        writer.println("=========================================")
                        writer.println("✈️ BLACK BOX FLIGHT RECORDER")
                        writer.println("🕒 TIME            : ${timeFormat.format(Date())}")
                        writer.println("📱 DEVICE          : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                        writer.println("🏗️ ABI             : ${getAbiSummary()}")
                        writer.println("🎮 RENDERER        : ${safeEnv("RALCORE_RENDERER", "native")}")
                        writer.println("📚 FNA3D LIB       : ${safeEnv("FNA3D_OPENGL_LIBRARY", "default")}")
                        writer.println("=========================================")

                        while (isRecording) {
                            val line = reader.readLine() ?: break
                            if (shouldRecordLine(line)) {
                                writer.println(line)
                                writer.flush()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Black Box Recorder malfunction", t)
            } finally {
                isRecording = false
            }
        }.start()
    }

    private fun shouldRecordLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.contains("fatal") ||
            lower.contains("f libc") ||
            lower.contains("abort") ||
            lower.contains("sigsegv") ||
            lower.contains("sigabrt") ||
            lower.contains("crash") ||
            lower.contains("exception") ||
            lower.contains("androidruntime") ||
            lower.contains("unsatisfiedlinkerror") ||
            lower.contains("dlopen") ||
            lower.contains("oom") ||
            lower.contains("out of memory") ||
            lower.contains("lowmemory") ||
            lower.contains("kmemleak") ||
            lower.contains("kill") ||
            lower.contains("activitymanager: process") ||
            lower.contains("died") ||
            lower.contains("egl") ||
            lower.contains("gles") ||
            lower.contains("opengl") ||
            lower.contains("mali") ||
            lower.contains("sdl") ||
            lower.contains("fna") ||
            lower.contains("mono") ||
            lower.contains("dotnet") ||
            lower.contains("tmodloader") ||
            lower.contains("terraria")
    }

    private fun writeThrowableReport(
        context: Context,
        crashDir: File,
        thread: Thread?,
        throwable: Throwable,
        prefix: String
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
        
        val reportFile = File(crashDir, "${prefix}_VIP_REPORT.txt")
        val internalFatal = File(context.filesDir, "FATAL_CRASH.txt")
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val vipReport = buildString {
            appendLine("=========================================")
            appendLine("🚨 RALAUNCHER VIP CRASH REPORT")
            appendLine("=========================================")
            appendLine("🕒 CRASH TIME      : ${timeFormat.format(Date())}")
            appendLine("📱 DEVICE          : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("📦 APP VERSION     : $appVersion")
            appendLine("🧵 THREAD          : ${thread?.name ?: "Unknown"}")
            appendLine("🧬 PROCESS         : $processName")
            appendLine("🏗️ ABI             : ${getAbiSummary()}")
            appendLine("-----------------------------------------")
            appendLine("🎮 RENDERER        : ${safeEnv("RALCORE_RENDERER", "native")}")
            appendLine("🧪 EGL             : ${safeEnv("RALCORE_EGL", "system")}")
            appendLine("🧩 GLES            : ${safeEnv("LIBGL_GLES", "system")}")
            appendLine("📚 FNA3D LIB       : ${safeEnv("FNA3D_OPENGL_LIBRARY", "default")}")
            appendLine("-----------------------------------------")
            appendLine("🧠 MEMORY CLASS    : ${memoryClass}MB")
            appendLine("💾 AVAIL MEM       : ${memoryInfo?.availMem?.div(1024 * 1024) ?: -1}MB")
            appendLine("⚠️ LOW MEMORY      : ${memoryInfo?.lowMemory ?: "Unknown"}")
            appendLine("-----------------------------------------")
            appendLine("📁 ERROR FILE      : $errorFile")
            appendLine("🔢 ERROR LINE      : Line $errorLine")
            appendLine("⚙️ ERROR METHOD    : $errorMethod")
            appendLine("-----------------------------------------")
            appendLine("💀 ERROR TYPE      : ${throwable.javaClass.name}")
            appendLine("💬 MESSAGE         : ${throwable.message}")
            appendLine("=========================================")
            appendLine("📚 FULL STACKTRACE:")

            throwable.stackTrace.forEach { appendLine("  at $it") }

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("🔄 CAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                cause.stackTrace.forEach { appendLine("  at $it") }
                cause = cause.cause
            }
            appendLine("=========================================")
        }

        reportFile.writeText(vipReport)
        runCatching { internalFatal.writeText(vipReport) }
    }

    private fun safeEnv(key: String, fallback: String): String {
        return try { Os.getenv(key) ?: fallback } catch (_: Throwable) { fallback }
    }

    private fun getAbiSummary(): String {
        return try { Build.SUPPORTED_ABIS.joinToString(", ") } catch (_: Throwable) { "Unknown" }
    }
}