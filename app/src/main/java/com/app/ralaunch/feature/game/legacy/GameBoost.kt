package com.app.ralaunch.feature.game.legacy

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.stream.Collectors

object GameBoost {

    private const val TAG = "GameBoost"

    fun ignite(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        optimizeFileSystemNio(context)

        if (totalRamGB < 4.0) {
            applyLowMemoryGc()
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            applyAndroid7LegacyTweaks()
        }

        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            Os.setenv("DOTNET_TieredCompilation", "1", true)
            Os.setenv("DOTNET_ReadyToRun", "1", true)
            Os.setenv("mesa_glthread", "true", true)
            Os.setenv("vblank_mode", "0", true)
            Os.setenv("SDL_JOYSTICK_DISABLE", "1", true)
            Os.setenv("SDL_HAPTIC_DISABLE", "1", true)
        } catch (t: Throwable) {}
    }

    private fun optimizeFileSystemNio(context: Context) {
        try {
            val gameDir = context.getExternalFilesDir(null)?.toPath() ?: return
            if (Files.exists(gameDir)) {
                Files.walk(gameDir, 2).use { stream ->
                    val mods = stream.filter { it.toString().endsWith(".tmod") }.collect(Collectors.toList())
                    mods.forEach { path ->
                        try {
                            FileChannel.open(path, StandardOpenOption.READ).use { ch ->
                                val buf = ByteBuffer.allocateDirect(4096)
                                ch.read(buf)
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "NIO error", t)
        }
    }

    private fun applyAndroid7LegacyTweaks() {
        try {
            Os.setenv("SDL_AUDIODRIVER", "android", true)
            Os.setenv("SDL_AUDIO_SAMPLES", "512", true)
            Os.setenv("FAUDIO_FMT_WBUFFER", "1", true)
            Os.setenv("FNA_AUDIO_SAMPLE_RATE", "44100", true)
            Os.setenv("ALSOFT_REQCHANNELS", "2", true)
            Os.setenv("ALSOFT_REQSAMPLERATE", "44100", true)
            Os.setenv("SDL_AUDIO_FORMAT", "s16", true)
            Os.setenv("FNA_AUDIO_DISABLE_FLOAT", "1", true)
            Os.setenv("SDL_VIDEO_ALLOW_SCREENSAVER", "0", true)
            Os.setenv("SDL_HINT_RENDER_LOGICAL_SIZE_MODE", "letterbox", true)
            Os.setenv("FNA_GRAPHICS_ENABLE_HIGHDPI", "1", true)
            Os.setenv("SDL_ANDROID_TRAP_BACK_BUTTON", "1", true)
            Os.setenv("SDL_ANDROID_BLOCK_ON_PAUSE", "0", true)
            Os.setenv("SDL_VIDEO_MINIMIZE_ON_FOCUS_LOSS", "0", true)
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy tweak error", t)
        }
    }

    private fun applyLowMemoryGc() {
        try {
            Os.setenv("DOTNET_gcServer", "0", true)
            Os.setenv("DOTNET_GCConcurrent", "1", true)
            Os.setenv("MONO_GC_PARAMS", "nursery-size=16m,soft-heap-limit=1024m,evacuation-threshold=60", true)
            Os.setenv("MONO_THREADS_PER_CPU", "30", true)
        } catch (t: Throwable) {}
    }
}
