package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Black Box Logger & VIP Crash Catcher ✈️🚨
 * 
 * Handles EVERYTHING related to crashes:
 * 1. Java/Kotlin app crashes (generates beautiful forensic reports).
 * 2. Native C++ / OOM game crashes (records logcat continuously).
 */
object BlackBoxLogger {

    private const val TAG = "BlackBoxLogger"
    private var isRecording = false
    private var isJavaArmed = false

    // ===================================================================
    // ... MAIN TRIGGER: Start all defenses ...
    // ===================================================================
    fun startRecording(context: Context) {
        val crashDir = File(context.getExternalFilesDir(null), "crashreport")
        if (!crashDir.exists()) crashDir.mkdirs()

        // ... 1. Arm the VIP Java Crash Catcher ...
        catchJavaCrashes(context, crashDir)

        // ... 2. Start the Native Black Box Recorder ...
        catchNativeCrashes(crashDir)
    }

    // ===================================================================
    // ... PART 1: THE VIP FORENSIC CRASH CATCHER (Java/Kotlin) 🚨 ...
    // ===================================================================
    private fun catchJavaCrashes(context: Context, crashDir: File) {
        if (isJavaArmed) return
        isJavaArmed = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "Unknown" }

                val rootCauseElement = exception.stackTrace.firstOrNull()
                val errorFile = rootCauseElement?.fileName ?: "Unknown File"
                val errorLine = rootCauseElement?.lineNumber?.toString() ?: "Unknown Line"
                val errorMethod = "${rootCauseElement?.className}.${rootCauseElement?.methodName}"

                val reportFile = File(crashDir, "APP_CRASH_${System.currentTimeMillis()}.txt")
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val vipReport = buildString {
                    appendLine("=========================================")
                    appendLine("🚨 RALAUNCHER VIP CRASH REPORT 🚨")
                    appendLine("=========================================")
                    appendLine("🕒 CRASH TIME   : ${timeFormat.format(Date())}")
                    appendLine("📱 DEVICE       : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendLine("📦 APP VERSION  : $appVersion")
                    appendLine("-----------------------------------------")
                    appendLine("📁 ERROR FILE   : $errorFile")
                    appendLine("🔢 ERROR LINE   : Line $errorLine")
                    appendLine("⚙️ ERROR METHOD : $errorMethod")
                    appendLine("-----------------------------------------")
                    appendLine("💀 ERROR TYPE   : ${exception.javaClass.name}")
                    appendLine("💬 MESSAGE      : ${exception.message}")
                    appendLine("=========================================")
                    appendLine("📚 FULL STACKTRACE:")
                    
                    exception.stackTrace.forEach { appendLine("  at $it") }
                    
                    var cause = exception.cause
                    while (cause != null) {
                        appendLine("\n🔄 CAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.forEach { appendLine("  at $it") }
                        cause = cause.cause
                    }
                    appendLine("=========================================")
                }

                reportFile.writeText(vipReport)
                // Also write to internal storage for legacy compatibility
                File(context.filesDir, "FATAL_CRASH.txt").writeText(vipReport)

            } catch (e: Exception) {
                // Ignore failure during crash handling
            } finally {
                // Let the OS or Fishnet handle the actual shutdown
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
        Log.i(TAG, "🚨 VIP Java Crash Catcher Armed!")
    }

    // ===================================================================
    // ... PART 2: THE NATIVE LOGCAT RECORDER (C++ / SDL / OOM) ✈️ ...
    // ===================================================================
    private fun catchNativeCrashes(crashDir: File) {
        if (isRecording) return

        Thread {
            try {
                isRecording = true
                val logFile = File(crashDir, "Crash.txt")
                
                Log.i(TAG, "✈️ Black Box activated! Recording flight data to: ${logFile.absolutePath}")

                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat -v threadtime")
                
                process.inputStream.bufferedReader().use { reader ->
                    logFile.printWriter().use { writer ->
                        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        writer.println("=========================================")
                        writer.println("✈️ BLACK BOX FLIGHT RECORDER STARTED")
                        writer.println("🕒 TIME: ${timeFormat.format(Date())}")
                        writer.println("=========================================")
                        
                        while (isRecording) {
                            val line = reader.readLine() ?: break
                            
                            if (line.contains("F libc") ||
                                line.contains("DEBUG") ||
                                line.contains("Fatal") ||
                                line.contains("OOM") ||
                                line.contains("LowMemory") ||
                                line.contains("SDL") ||
                                line.contains("mono") ||
                                line.contains("ralaunch") ||
                                line.contains("Exception")) {
                                
                                writer.println(line)
                                writer.flush() 
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Black Box Recorder malfunction: ${e.message}")
            } finally {
                isRecording = false
            }
        }.start()
    }
}
