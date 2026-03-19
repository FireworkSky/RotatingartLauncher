package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
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
        clearOldReports(crashDir)
        catchJavaCrashes(context, crashDir)
        catchNativeCrashes(crashDir)
    }

    private fun clearOldReports(crashDir: File) {
        runCatching {
            crashDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        }
        runCatching { File(crashDir.parentFile, "FATAL_CRASH.txt").delete() }
    }

    private fun catchJavaCrashes(context: Context, crashDir: File) {
        if (isJavaArmed) return
        isJavaArmed = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                clearOldReports(crashDir)
                writeThrowableReport(context, crashDir, thread, throwable, "APP_CRASH")
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "🚨 VIP Java Crash Catcher Armed!")
    }

    private fun catchNativeCrashes(crashDir: File) {
        if (isRecording) return

        Thread {
            try {
                isRecording = true
                clearOldReports(crashDir)
                val logFile = File(crashDir, "💥Crash.txt")

                Log.i(TAG, "✈️ Black Box activated! Recording flight data to: ${logFile.absolutePath}")

                runCatching { Runtime.getRuntime().exec("logcat -c").waitFor() }
                val process = Runtime.getRuntime().exec("logcat -v threadtime")

                process.inputStream.bufferedReader().use { reader ->
                    logFile.printWriter().use { writer ->
                        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        writer.println("=========================================")
                        writer.println("✈️ BLACK BOX FLIGHT RECORDER STARTED ✈️")
                        writer.println("🕒 TIME   : ${timeFormat.format(Date())}")
                        writer.println("📱 DEVICE : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
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
                Log.e(TAG, "❌ Black Box Recorder malfunction", t)
            } finally {
                isRecording = false
            }
        }.start()
    }

    fun stopRecording() {
        isRecording = false
    }

    private fun shouldRecordLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.contains("fatal") ||
            lower.contains("f libc") ||
            lower.contains("abort") ||
            lower.contains("sigsegv") ||
            lower.contains("sigabrt") ||
            lower.contains("signal") ||
            lower.contains("crash") ||
            lower.contains("exception") ||
            lower.contains("androidruntime") ||
            lower.contains("unsatisfiedlinkerror") ||
            lower.contains("dlopen") ||
            lower.contains("oom") ||
            lower.contains("outofmemory") ||
            lower.contains("lowmemory") ||
            lower.contains("anr") ||
            lower.contains("watchdog") ||
            lower.contains("debug") ||
            lower.contains("egl") ||
            lower.contains("gles") ||
            lower.contains("opengl") ||
            lower.contains("vulkan") ||
            lower.contains("angle") ||
            lower.contains("zink") ||
            lower.contains("gl4es") ||
            lower.contains("sdl") ||
            lower.contains("fna") ||
            lower.contains("mono") ||
            lower.contains("dotnet") ||
            lower.contains("hostpolicy") ||
            lower.contains("libc++") ||
            lower.contains("libgles") ||
            lower.contains("libegl") ||
            lower.contains("mali") ||
            lower.contains("renderer") ||
            lower.contains("tmodloader") ||
            lower.contains("terraria") ||
            lower.contains("ralaunch")
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
        } catch (_: Throwable) {
            "Unknown"
        }

        val rootCauseElement = throwable.stackTrace.firstOrNull()
        val errorFile = rootCauseElement?.fileName ?: "Unknown File"
        val errorLine = rootCauseElement?.lineNumber?.toString() ?: "Unknown Line"
        val errorMethod = "${rootCauseElement?.className}.${rootCauseElement?.methodName}"
        val reportFile = File(crashDir, "🚨${prefix}_${System.currentTimeMillis()}.txt")
        val internalFatal = File(context.filesDir, "FATAL_CRASH.txt")
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        runCatching { internalFatal.delete() }

        val vipReport = buildString {
            appendLine("=========================================")
            appendLine("🚨 RALAUNCHER VIP CRASH REPORT 🚨")
            appendLine("=========================================")
            appendLine("🕒 CRASH TIME   : ${timeFormat.format(Date())}")
            appendLine("📱 DEVICE       : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("📦 APP VERSION  : $appVersion")
            appendLine("🧵 THREAD       : ${thread?.name ?: "Unknown"}")
            appendLine("-----------------------------------------")
            appendLine("📁 ERROR FILE   : $errorFile")
            appendLine("🔢 ERROR LINE   : Line $errorLine")
            appendLine("⚙️ ERROR METHOD : $errorMethod")
            appendLine("-----------------------------------------")
            appendLine("💀 ERROR TYPE   : ${throwable.javaClass.name}")
            appendLine("💬 MESSAGE      : ${throwable.message}")
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
        internalFatal.writeText(vipReport)
    }
}
