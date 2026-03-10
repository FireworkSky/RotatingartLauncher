package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Black Box Logger (Native & Background Crash Catcher)
 * 
 * Runs a background thread that continuously records the deep Linux system log (Logcat).
 * If the app is killed instantly by the OS (OOM, SIGSEGV, or Native C++ crashes) bypassing 
 * the normal Java crash handler, this file will contain the exact final moments of the engine.
 */
object BlackBoxLogger {

    private const val TAG = "BlackBoxLogger"
    private var isRecording = false

    // ===================================================================
    // ... MAIN TRIGGER: Start recording system logs ...
    // ===================================================================
    fun startRecording(context: Context) {
        if (isRecording) return

        Thread {
            try {
                isRecording = true
                
                // ... 1. Setup the Black Box directory and file ...
                val crashDir = File(context.getExternalFilesDir(null), "crashreport")
                if (!crashDir.exists()) crashDir.mkdirs()
                
                val logFile = File(crashDir, "Crash.txt")
                
                Log.i(TAG, "✈️ Black Box activated! Recording flight data to: ${logFile.absolutePath}")

                // ... 2. Clear old logs to ensure we only capture current session data ...
                Runtime.getRuntime().exec("logcat -c").waitFor()

                // ... 3. Start continuous log capture (using threadtime for high precision) ...
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
                            
                            // ... 4. FORENSIC FILTERING ...
                            // We don't want to save everything (it would be gigabytes of junk).
                            // We ONLY intercept lines containing critical keywords:
                            if (line.contains("F libc") ||      // Native C++ Segfaults (The true silent killer)
                                line.contains("DEBUG") ||       // Android Tombstone/Crash dumper
                                line.contains("Fatal") ||       // General fatal errors
                                line.contains("OOM") ||         // Out of memory panics
                                line.contains("LowMemory") ||   // OS killing processes to save RAM
                                line.contains("SDL") ||         // SDL engine internal errors
                                line.contains("mono") ||        // .NET/tModLoader runtime errors
                                line.contains("ralaunch") ||    // Our app's logs
                                line.contains("Exception")) {   // Unhandled exceptions
                                
                                writer.println(line)
                                writer.flush() // Force save to disk immediately before OS kills us!
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
