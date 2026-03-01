package com.app.ralaunch.shared.core.util

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState

// Expect function for safe Haze application
expect fun Modifier.safeHaze(state: HazeState): Modifier
