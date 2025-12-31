package com.app.ralaunch.controls.packs.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 控件包商店 Compose 主题
 */

// 浅色主题颜色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B8DEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF1E3A5F),
    secondary = Color(0xFF9965F4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0D5FF),
    onSecondaryContainer = Color(0xFF1A0052),
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    error = Color(0xFFB00020),
    onError = Color.White,
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFAFBFC),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFE0E0E0)
)

// 深色主题颜色
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF1E3A5F),
    primaryContainer = Color(0xFF3C5A80),
    onPrimaryContainer = Color(0xFFE8F2FF),
    secondary = Color(0xFFBB86FC),
    onSecondary = Color(0xFF1A0052),
    secondaryContainer = Color(0xFF4A3080),
    onSecondaryContainer = Color(0xFFE0D5FF),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF1B5E20),
    error = Color(0xFFCF6679),
    onError = Color(0xFF410002),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// 状态颜色
object PackStatusColors {
    val installed = Color(0xFF4CAF50)
    val updateAvailable = Color(0xFFFF9800)
    val downloading = Color(0xFF2196F3)
    val installing = Color(0xFF9C27B0)
}

@Composable
fun ControlPackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

