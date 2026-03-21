package com.app.ralaunch.core.platform.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.system.Os
import android.util.Log

object GameBoost {

    private const val TAG = "GameBoost"

    fun ignite(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val isLowRamDevice = totalRamGB <= 4.5

        Log.i(TAG, "Hardware Check: Total RAM = ${String.format("%.1f", totalRamGB)} GB")

        if (isLowRamDevice) {
            tweakDotNetGarbageCollector(isLowRam = true)
            tweakSdlSurvival()
            boostThreadPriority()
        } else {
            tweakDotNetGarbageCollector(isLowRam = false)
            boostThreadPriority()
        }
    }

    private fun tweakDotNetGarbageCollector(isLowRam: Boolean) {
        try {
            if (isLowRam) {
                Os.setenv("DOTNET_gcServer", "0", true)
                Os.setenv("DOTNET_GCConcurrent", "1", true)
                Os.setenv("DOTNET_GCRetainVM", "0", true)
                Os.setenv("MONO_GC_PARAMS", "nursery-size=16m,soft-heap-limit=512m", true)
                Os.setenv("MONO_DISABLE_SHARED_AREA", "1", true)
            } else {
                Os.setenv("DOTNET_gcServer", "1", true)
                Os.setenv("MONO_GC_PARAMS", "nursery-size=32m,soft-heap-limit=1024m", true)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to tweak .NET GC", t)
        }
    }

    private fun tweakSdlSurvival() {
        try {
            Os.setenv("SDL_JOYSTICK_DISABLE", "1", true)
            Os.setenv("SDL_HAPTIC_DISABLE", "1", true)
            Os.setenv("SDL_AUDIO_SAMPLES", "256", true)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to tweak SDL", t)
        }
    }

    private fun boostThreadPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not boost thread priority")
        }
    }
}
