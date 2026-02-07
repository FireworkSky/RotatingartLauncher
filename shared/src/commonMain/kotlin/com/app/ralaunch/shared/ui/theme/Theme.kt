package com.app.ralaunch.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

// ==================== Haze 全局状态 ====================

/**
 * 全局 HazeState CompositionLocal
 * 用于在组件树中共享模糊源状态，实现跨组件毛玻璃效果
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

// ==================== 动态颜色生成 ====================

/**
 * 从种子颜色生成动态深色主题
 */
private fun generateDarkColorScheme(seedColor: Color): ColorScheme {
    val hsl = colorToHsl(seedColor)
    
    return darkColorScheme(
        primary = hslToColor(hsl[0], 0.7f, 0.75f),
        onPrimary = hslToColor(hsl[0], 0.8f, 0.15f),
        primaryContainer = hslToColor(hsl[0], 0.6f, 0.25f),
        onPrimaryContainer = hslToColor(hsl[0], 0.7f, 0.90f),
        secondary = hslToColor((hsl[0] + 30) % 360, 0.5f, 0.70f),
        onSecondary = hslToColor((hsl[0] + 30) % 360, 0.6f, 0.15f),
        secondaryContainer = hslToColor((hsl[0] + 30) % 360, 0.4f, 0.25f),
        onSecondaryContainer = hslToColor((hsl[0] + 30) % 360, 0.5f, 0.85f),
        tertiary = hslToColor((hsl[0] + 60) % 360, 0.5f, 0.70f),
        onTertiary = hslToColor((hsl[0] + 60) % 360, 0.6f, 0.15f),
        tertiaryContainer = hslToColor((hsl[0] + 60) % 360, 0.4f, 0.25f),
        onTertiaryContainer = hslToColor((hsl[0] + 60) % 360, 0.5f, 0.85f),
        error = AppColors.Error80,
        onError = AppColors.Error20,
        errorContainer = AppColors.Error30,
        onErrorContainer = AppColors.Error90,
        // 背景带有轻微主题色调
        background = hslToColor(hsl[0], 0.08f, 0.08f),
        onBackground = hslToColor(hsl[0], 0.10f, 0.90f),
        surface = hslToColor(hsl[0], 0.10f, 0.12f),
        onSurface = hslToColor(hsl[0], 0.10f, 0.90f),
        surfaceVariant = hslToColor(hsl[0], 0.15f, 0.20f),
        onSurfaceVariant = hslToColor(hsl[0], 0.10f, 0.80f),
        outline = hslToColor(hsl[0], 0.20f, 0.45f),
        outlineVariant = hslToColor(hsl[0], 0.15f, 0.30f),
        // Surface container 层次（MD3 新增）
        surfaceDim = hslToColor(hsl[0], 0.08f, 0.06f),
        surfaceBright = hslToColor(hsl[0], 0.12f, 0.22f),
        surfaceContainerLowest = hslToColor(hsl[0], 0.06f, 0.04f),
        surfaceContainerLow = hslToColor(hsl[0], 0.08f, 0.09f),
        surfaceContainer = hslToColor(hsl[0], 0.10f, 0.14f),
        surfaceContainerHigh = hslToColor(hsl[0], 0.10f, 0.17f),
        surfaceContainerHighest = hslToColor(hsl[0], 0.10f, 0.22f),
        inverseSurface = hslToColor(hsl[0], 0.08f, 0.90f),
        inverseOnSurface = hslToColor(hsl[0], 0.08f, 0.15f),
        inversePrimary = hslToColor(hsl[0], 0.7f, 0.40f),
        scrim = Color.Black
    )
}

/**
 * 从种子颜色生成动态浅色主题
 */
private fun generateLightColorScheme(seedColor: Color): ColorScheme {
    val hsl = colorToHsl(seedColor)
    
    return lightColorScheme(
        primary = hslToColor(hsl[0], 0.7f, 0.40f),
        onPrimary = Color.White,
        primaryContainer = hslToColor(hsl[0], 0.8f, 0.90f),
        onPrimaryContainer = hslToColor(hsl[0], 0.8f, 0.10f),
        secondary = hslToColor((hsl[0] + 30) % 360, 0.5f, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = hslToColor((hsl[0] + 30) % 360, 0.6f, 0.90f),
        onSecondaryContainer = hslToColor((hsl[0] + 30) % 360, 0.6f, 0.10f),
        tertiary = hslToColor((hsl[0] + 60) % 360, 0.5f, 0.40f),
        onTertiary = Color.White,
        tertiaryContainer = hslToColor((hsl[0] + 60) % 360, 0.6f, 0.90f),
        onTertiaryContainer = hslToColor((hsl[0] + 60) % 360, 0.6f, 0.10f),
        error = AppColors.Error40,
        onError = Color.White,
        errorContainer = AppColors.Error90,
        onErrorContainer = AppColors.Error10,
        // 背景使用低饱和度，接近纯白但带有轻微主题色调
        background = hslToColor(hsl[0], 0.08f, 0.98f),
        onBackground = hslToColor(hsl[0], 0.20f, 0.15f),
        surface = hslToColor(hsl[0], 0.05f, 0.99f),
        onSurface = hslToColor(hsl[0], 0.15f, 0.12f),
        surfaceVariant = hslToColor(hsl[0], 0.10f, 0.95f),
        onSurfaceVariant = hslToColor(hsl[0], 0.15f, 0.30f),
        outline = hslToColor(hsl[0], 0.15f, 0.60f),
        outlineVariant = hslToColor(hsl[0], 0.10f, 0.80f),
        // Surface container 层次（MD3 新增）
        surfaceDim = hslToColor(hsl[0], 0.08f, 0.88f),
        surfaceBright = hslToColor(hsl[0], 0.05f, 0.99f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hslToColor(hsl[0], 0.06f, 0.97f),
        surfaceContainer = hslToColor(hsl[0], 0.08f, 0.95f),
        surfaceContainerHigh = hslToColor(hsl[0], 0.08f, 0.93f),
        surfaceContainerHighest = hslToColor(hsl[0], 0.08f, 0.90f),
        inverseSurface = hslToColor(hsl[0], 0.10f, 0.18f),
        inverseOnSurface = hslToColor(hsl[0], 0.06f, 0.95f),
        inversePrimary = hslToColor(hsl[0], 0.7f, 0.75f),
        scrim = Color.Black
    )
}

/**
 * RGB 转 HSL
 */
private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    
    val s: Float
    val h: Float
    
    if (max == min) {
        s = 0f
        h = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }
    }
    
    return floatArrayOf(h, s, l)
}

/**
 * HSL 转 Color
 */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    
    return Color(r + m, g + m, b + m)
}

/**
 * 扩展颜色 - 用于游戏特定 UI + 毛玻璃 + 发光动效
 */
data class ExtendedColors(
    val gameCardBackground: Color,
    val gameCardBorder: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    // 毛玻璃
    val glassOverlay: Color,
    val glassBorder: Color,
    val glassSurface: Color,
    // 发光
    val glowPrimary: Color,
    val glowPrimaryIntense: Color,
    val glowSecondary: Color,
    val glowSuccess: Color
)

private val DarkExtendedColors = ExtendedColors(
    gameCardBackground = AppColors.GameCardBackground,
    gameCardBorder = AppColors.GameCardBorder,
    success = AppColors.Success80,
    onSuccess = AppColors.Success20,
    warning = AppColors.Warning80,
    onWarning = AppColors.Warning20,
    // 深色毛玻璃
    glassOverlay = AppColors.GlassDark,
    glassBorder = AppColors.GlassBorderDark,
    glassSurface = AppColors.GlassDarkMedium,
    // 深色发光（亮色系在暗背景上更明显）
    glowPrimary = AppColors.GlowPrimary,
    glowPrimaryIntense = AppColors.GlowPrimaryIntense,
    glowSecondary = AppColors.GlowSecondary,
    glowSuccess = AppColors.GlowSuccess
)

private val LightExtendedColors = ExtendedColors(
    gameCardBackground = AppColors.GameCardBackgroundLight,
    gameCardBorder = AppColors.GameCardBorderLight,
    success = AppColors.Success40,
    onSuccess = Color.White,
    warning = AppColors.Warning40,
    onWarning = Color.White,
    // 亮色毛玻璃
    glassOverlay = AppColors.GlassLight,
    glassBorder = AppColors.GlassBorderLight,
    glassSurface = AppColors.GlassLightMedium,
    // 亮色发光（稍暗以在亮背景上可见）
    glowPrimary = AppColors.GlowPrimary.copy(alpha = 0.35f),
    glowPrimaryIntense = AppColors.GlowPrimaryIntense.copy(alpha = 0.5f),
    glowSecondary = AppColors.GlowSecondary.copy(alpha = 0.3f),
    glowSuccess = AppColors.GlowSuccess.copy(alpha = 0.3f)
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * 应用主题 - 跨平台共享
 * 
 * @param themeMode 主题模式: 0=跟随系统, 1=深色, 2=浅色
 * @param themeColor 主题颜色 (ARGB 整数)，用于动态生成颜色方案
 * @param darkTheme 是否深色模式（当 themeMode=0 时使用）
 */
@Composable
fun RaLaunchTheme(
    themeMode: Int = 0,
    themeColor: Int = 0xFF6750A4.toInt(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 计算是否使用深色主题
    val useDarkTheme = when (themeMode) {
        1 -> true   // 强制深色
        2 -> false  // 强制浅色
        else -> darkTheme // 跟随系统
    }
    
    // 根据种子颜色动态生成颜色方案（不使用 remember，确保每次 themeColor 变化都重新计算）
    val seedColor = Color(themeColor)
    val colorScheme = if (useDarkTheme) {
        generateDarkColorScheme(seedColor)
    } else {
        generateLightColorScheme(seedColor)
    }
    
    val extendedColors = if (useDarkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * 获取扩展颜色
 */
object RaLaunchTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
