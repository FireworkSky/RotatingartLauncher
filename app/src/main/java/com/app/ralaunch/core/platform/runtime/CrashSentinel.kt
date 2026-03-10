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
import kotlin.system.exitProcess

/**
 * The Ultimate Crash Sentinel 🗿🧬
 * 
 * Catches all crashes and generates highly detailed forensic reports.
 */
object CrashSentinel {

    private const val TAG = "CrashSentinel"
    private var isArmed = false

    // ===================================================================
    // ... ARM THE DEFENSES ...
    // ===================================================================
    fun armDefenses(context: Context) {
        if (isArmed) return
        isArmed = true

        // ... Rename directory to "crashreport" (all lowercase) as requested ...
        val crashDir = File(context.getExternalFilesDir(null), "crashreport")
        if (!crashDir.exists()) crashDir.mkdirs()

        Log.i(TAG, "🛡️ Arming the Ultimate Crash Sentinel...")

        // ... 1. Setup Java/Kotlin Exception Catcher ...
        setupJavaCrashCatcher(context, crashDir)

        // ... 2. Setup Native/Logcat Reader ...
        setupNativeCrashCatcher(crashDir)
    }

    // ===================================================================
    // ... LAYER 1: APP CRASHES (Highly Detailed Forensic Report) ...
    // ===================================================================
    private fun setupJavaCrashCatcher(context: Context, crashDir: File) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "Unknown" }

                // ... Extract the exact file and line number where the crash originated ...
                val rootCauseElement = exception.stackTrace.firstOrNull()
                val errorFile = rootCauseElement?.fileName ?: "Unknown File"
                val errorLine = rootCauseElement?.lineNumber?.toString() ?: "Unknown Line"
                val errorMethod = "${rootCauseElement?.className}.${rootCauseElement?.methodName}"

                // ... Format the date nicely ...
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val crashTime = timeFormat.format(Date())

                val reportFile = File(crashDir, "APP_CRASH_${System.currentTimeMillis()}.txt")
                
                // ... Build the professional forensic crash report ...
                val crashReport = buildString {
                    appendLine("=========================================")
                    appendLine("🚨 RALAUNCHER FATAL CRASH REPORT 🚨")
                    appendLine("=========================================")
                    appendLine("🕒 CRASH TIME   : $crashTime")
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

                reportFile.writeText(crashReport)
                Log.e(TAG, "FATAL APP CRASH SAVED TO: ${reportFile.absolutePath}")

            } catch (e: Exception) {
                // Ignore failure during crash handling
            } finally {
                // ... Pass the crash to Android OS to close the app properly ...
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
            }
        }
    }

    // ===================================================================
    // ... LAYER 2: GAME CRASHES (Native C++ / SDL / OOM) ...
    // ===================================================================
    private fun setupNativeCrashCatcher(crashDir: File) {
        Thread {
            try {
                // ... Clear old logcat ...
                Runtime.getRuntime().exec("logcat -c").waitFor()

                // ... Start reading logcat continuously with timestamps ...
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                val nativeReportFile = File(crashDir, "GAME_NATIVE_CRASH_LOG.txt")
                
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                nativeReportFile.writeText("=== GAME BACKGROUND LOGGING STARTED AT ${timeFormat.format(Date())} ===\n\n")

                while (true) {
                    val line = reader.readLine() ?: break
                    
                    // ... Filter only fatal signals, memory issues, or our app's specific logs ...
                    if (line.contains("F libc") ||      // Native C++ Segfaults
                        line.contains("DEBUG") ||       // Android Tombstone/Crash dumper
                        line.contains("Fatal") ||       // General fatal errors
                        line.contains("OOM") ||         // Out of memory
                        line.contains("LowMemory") ||   // OS killing processes
                        line.contains("SDL") ||         // SDL engine errors
                        line.contains("mono") ||        // .NET runtime errors
                        line.contains("ralaunch")) {    // Our app's logs
                        
                        nativeReportFile.appendText("$line\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native Crash Catcher failed: ${e.message}")
            }
        }.start()
    }
}
