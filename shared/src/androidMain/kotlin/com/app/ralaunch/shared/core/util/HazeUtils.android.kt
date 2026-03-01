package com.app.ralaunch.shared.core.util

import android.os.Build
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

// Actual implementation for Android
actual fun Modifier.safeHaze(state: HazeState): Modifier {
    // Only apply blur if Android 12 (API 31) or higher
    // On Android 7, returning 'this' prevents the native graphics crash
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.haze(state)
    } else {
        this
    }
}
