package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.media.AudioManager
import android.system.Os
import android.util.Log
import com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry

object SDLOptimizer {

    private const val TAG = "SDLOptimizer"

    init {
        try {
            System.loadLibrary("ral_injector")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ral_injector.so", e)
        }
    }

    fun applyAudioFixes(context: Context) {
        Log.i(TAG, "Initiating SDL Audio cleanup and optimization...")
        forceClaimAudioHardware(context)
        injectEnvironmentVariables()
        Log.i(TAG, "SDLOptimizer configuration completed successfully!")
    }

    @Suppress("DEPRECATION")
    private fun forceClaimAudioHardware(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val result = audioManager.requestAudioFocus(
                { focusChange -> Log.d(TAG, "Audio Focus changed: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "Audio Focus GRANTED!")
            } else {
                Log.w(TAG, "Audio Focus DENIED!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to claim Audio Focus: ${e.message}")
        }
    }

    private fun injectEnvironmentVariables() {
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

            Log.i(TAG, "Injected all environment variables successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject environment variables: ${e.message}")
        }
    }

    fun applyRenderer(rendererId: String) {
        Log.i(TAG, "Initiating Native Renderer Injection for: $rendererId")
        
        val normalizedId = RendererRegistry.normalizeRendererId(rendererId)
        val info = RendererRegistry.getRendererInfo(normalizedId)
        
        if (info == null) {
            Log.w(TAG, "Renderer $normalizedId not found in Registry!")
            return
        }

        val envs = RendererRegistry.buildRendererEnv(normalizedId)
        for ((key, value) in envs) {
            if (value != null) {
                try {
                    Os.setenv(key, value, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set Renderer env: $key=$value", e)
                }
            }
        }

        if (info.needsPreload) {
            val eglAbsPath = RendererRegistry.getRendererLibraryPath(info.eglLibrary)
            val glesAbsPath = RendererRegistry.getRendererLibraryPath(info.glesLibrary)
            
            if (eglAbsPath != null || glesAbsPath != null) {
                Log.i(TAG, "Forcing native load for EGL: $eglAbsPath, GLES: $glesAbsPath")
                nativeForceLoadLibs(eglAbsPath, glesAbsPath)
            } else {
                Log.w(TAG, "Needs preload but files not found!")
            }
        }
        
        Log.i(TAG, "Renderer configuration completed!")
    }

    @JvmStatic
    private external fun nativeForceLoadLibs(eglPath: String?, glesPath: String?)
}  
