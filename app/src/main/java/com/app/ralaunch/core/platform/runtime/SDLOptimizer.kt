package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.media.AudioManager
import android.system.Os
import android.util.Log

/**
 * SDL & Audio Optimizer
 * 
 * Specifically designed to resolve Audio (SDL2 / OpenAL / FNA) related 
 * crashes on older Android devices (especially Android 7.1.1).
 */
object SDLOptimizer {

    private const val TAG = "SDLOptimizer"

    // ===================================================================
    // ... MAIN ENTRY POINT: Call this before launching the game ...
    // ===================================================================
    fun applyAudioFixes(context: Context) {
        Log.i(TAG, "🛠️ Initiating SDL Audio cleanup and optimization...")

        // ... 1. Claim the speaker aggressively ...
        forceClaimAudioHardware(context)

        // ... 2. Inject survival environment variables for old Androids ...
        injectEnvironmentVariables()

        Log.i(TAG, "✅ SDLOptimizer configuration completed successfully!")
    }

    // ===================================================================
    // ... THE BULLDOZER: Force clear audio focus from other apps ...
    // ===================================================================
    @Suppress("DEPRECATION")
    private fun forceClaimAudioHardware(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // ... Request audio focus using the legacy Android 7 method ...
            val result = audioManager.requestAudioFocus(
                { focusChange -> 
                    Log.d(TAG, "Audio Focus changed: $focusChange") 
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "✅ Audio Focus GRANTED! Hardware speaker claimed.")
            } else {
                Log.w(TAG, "⚠ Audio Focus DENIED! Another process might be holding the speaker.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to claim Audio Focus: ${e.message}")
        }
    }

        // ===================================================================
    // ... THE VACCINE: Inject environment variables for Audio & Graphics ...
    // ===================================================================
    private fun injectEnvironmentVariables() {
        try {
            // --- 1. AUDIO FIXES (Android 7 compatibility) ---
            Os.setenv("SDL_AUDIODRIVER", "android", true)
            Os.setenv("SDL_AUDIO_SAMPLES", "512", true) 
            Os.setenv("FAUDIO_FMT_WBUFFER", "1", true)
            Os.setenv("FNA_AUDIO_SAMPLE_RATE", "44100", true)
            Os.setenv("ALSOFT_REQCHANNELS", "2", true) 
            Os.setenv("ALSOFT_REQSAMPLERATE", "44100", true)

            // --- 2. GRAPHICS FIXES (Prevent Stretched/Squeezed rendering) ---
            // ... Force SDL to maintain the original aspect ratio (Letterboxing) ...
            Os.setenv("SDL_VIDEO_ALLOW_SCREENSAVER", "0", true)
            Os.setenv("SDL_HINT_RENDER_LOGICAL_SIZE_MODE", "letterbox", true)
            
            // ... Prevent FNA engine from stretching the window manually ...
            Os.setenv("FNA_GRAPHICS_ENABLE_HIGHDPI", "1", true)
            
            // ... Optional: Force 1280x720 (16:9 HD) resolution for better performance ...
            // ... Uncomment these if the game still looks stretched ...
            // Os.setenv("FNA_GRAPHICS_DISPLAY_WIDTH", "1280", true)
            // Os.setenv("FNA_GRAPHICS_DISPLAY_HEIGHT", "720", true)

            Log.i(TAG, "✅ Audio and Graphics environment variables injected successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to inject environment variables: ${e.message}")
        }
    }
