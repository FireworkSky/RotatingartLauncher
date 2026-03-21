package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.media.AudioManager
import android.system.Os
import android.util.Log
import com.app.ralaunch.core.platform.runtime.renderer.RendererLoader

object SDLOptimizer {

    private const val TAG = "SDLOptimizer"
    private var nativeInjectorLoaded = false

    init {
        try {
            System.loadLibrary("ral_injector")
            nativeInjectorLoaded = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load ral_injector.so", t)
            nativeInjectorLoaded = false
        }
    }

    fun applyAudioFixes(context: Context) {
        Log.i(TAG, "Initiating SDL Audio cleanup and optimization...")
        forceClaimAudioHardware(context)
        injectEnvironmentVariables()
        Log.i(TAG, "SDLOptimizer configuration completed successfully!")
    }

    fun applyRenderer(context: Context, rendererId: String): Boolean {
        return try {
            val loaded = RendererLoader.loadRenderer(context, rendererId)
            if (!loaded) {
                Log.w(TAG, "RendererLoader failed for renderer=$rendererId")
                return false
            }

            val rendererInfo = com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
                .getRendererInfo(
                    com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
                        .normalizeRendererId(rendererId)
                )

            val eglPath = com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
                .getRendererLibraryPath(rendererInfo?.eglLibrary)

            val glesPath = com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
                .getRendererLibraryPath(rendererInfo?.glesLibrary)

            if (nativeInjectorLoaded && rendererInfo?.needsPreload == true && (!eglPath.isNullOrEmpty() || !glesPath.isNullOrEmpty())) {
                try {
                    nativeForceLoadLibs(eglPath, glesPath)
                    Log.i(TAG, "Native renderer preload completed")
                } catch (t: Throwable) {
                    Log.e(TAG, "Native renderer preload failed", t)
                }
            } else {
                Log.i(TAG, "Native injector unavailable or preload not required")
            }

            true
        } catch (t: Throwable) {
            Log.e(TAG, "applyRenderer failed", t)
            false
        }
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to claim Audio Focus", t)
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject environment variables", t)
        }
    }

    @JvmStatic
    private external fun nativeForceLoadLibs(eglPath: String?, glesPath: String?)
}  
