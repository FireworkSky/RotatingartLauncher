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
    // ... THE VACCINE: Inject environment variables to prevent SDL crashes ...
    // ===================================================================
    private fun injectEnvironmentVariables() {
        try {
            // ... Level 1: Force Android AudioTrack (Most stable for old OS) ...
            Os.setenv("SDL_AUDIODRIVER", "android", true)
            
            // ... Level 2: Fix Buffer Overflow (The real cause of "Already Open" error) ...
            // Reduce sample size so the old Android buffer doesn't choke
            Os.setenv("SDL_AUDIO_SAMPLES", "512", true) 
            Os.setenv("FAUDIO_FMT_WBUFFER", "1", true)
            
            // ... Level 3: Force FNA/OpenAL to use a very standard sample rate ...
            Os.setenv("FNA_AUDIO_SAMPLE_RATE", "44100", true)
            Os.setenv("ALSOFT_REQCHANNELS", "2", true) 
            Os.setenv("ALSOFT_REQSAMPLERATE", "44100", true)

            // ... Optional: If it still crashes, uncomment the line below to mute the game entirely ...
            // Os.setenv("FNA_AUDIO_DISABLE_SOUND", "1", true)

            Log.i(TAG, "✅ Anti-crash Audio Environment Variables injected!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to inject environment variables: ${e.message}")
        }
    }
}
