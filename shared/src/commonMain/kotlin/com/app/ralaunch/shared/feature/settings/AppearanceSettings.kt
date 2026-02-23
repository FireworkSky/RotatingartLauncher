package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 外观设置状态
 */
data class AppearanceState(
    val themeMode: Int = 0,              // 0=跟随系统, 1=深色, 2=浅色
    val themeColor: Int = 0,             // 主题颜色ID
    val backgroundType: Int = 0,         // 0=默认, 1=图片, 2=视频
    val backgroundOpacity: Int = 0,      // 背景透明度 0-100
    val videoPlaybackSpeed: Float = 1.0f,// 视频播放速度 0.5-2.0
    val language: String = "auto"
)

/**
 * 外观设置内容 - 跨平台
 */
@Composable
fun AppearanceSettingsContent(
    state: AppearanceState,
    onThemeModeChange: (Int) -> Unit,
    onThemeColorClick: () -> Unit,
    onBackgroundTypeChange: (Int) -> Unit,
    onSelectImageClick: () -> Unit,
    onSelectVideoClick: () -> Unit,
    onBackgroundOpacityChange: (Int) -> Unit,
    onVideoSpeedChange: (Float) -> Unit,
    onRestoreDefaultBackground: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 主题设置
        ThemeSection(
            themeMode = state.themeMode,
            onThemeModeChange = onThemeModeChange,
            themeColor = state.themeColor,
            onThemeColorClick = onThemeColorClick
        )

        // 背景设置
        BackgroundSection(
            backgroundType = state.backgroundType,
            backgroundOpacity = state.backgroundOpacity,
            videoSpeed = state.videoPlaybackSpeed,
            onSelectImageClick = onSelectImageClick,
            onSelectVideoClick = onSelectVideoClick,
            onOpacityChange = onBackgroundOpacityChange,
            onVideoSpeedChange = onVideoSpeedChange,
            onRestoreDefault = onRestoreDefaultBackground
        )

        // 语言设置
        LanguageSection(
            language = state.language,
            onLanguageClick = onLanguageClick
        )
    }
}

@Composable
private fun ThemeSection(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    themeColor: Int,
    onThemeColorClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_appearance_theme_section)) {
        // 主题模式
        ClickableSettingItem(
            title = stringResource(Res.string.settings_appearance_theme_mode_title),
            subtitle = stringResource(Res.string.settings_appearance_theme_mode_subtitle),
            value = when (themeMode) {
                0 -> stringResource(Res.string.settings_appearance_theme_mode_system)
                1 -> stringResource(Res.string.settings_appearance_theme_mode_dark)
                2 -> stringResource(Res.string.settings_appearance_theme_mode_light)
                else -> stringResource(Res.string.settings_appearance_theme_mode_system)
            },
            icon = Icons.Default.DarkMode,
            onClick = {
                // 循环切换主题模式
                val nextMode = (themeMode + 1) % 3
                onThemeModeChange(nextMode)
            }
        )

        SettingsDivider()

        // 主题颜色
        ClickableSettingItem(
            title = stringResource(Res.string.settings_appearance_theme_color_title),
            subtitle = stringResource(Res.string.settings_appearance_theme_color_subtitle),
            icon = Icons.Default.Palette,
            onClick = onThemeColorClick
        )
    }
}

@Composable
private fun BackgroundSection(
    backgroundType: Int,
    backgroundOpacity: Int,
    videoSpeed: Float,
    onSelectImageClick: () -> Unit,
    onSelectVideoClick: () -> Unit,
    onOpacityChange: (Int) -> Unit,
    onVideoSpeedChange: (Float) -> Unit,
    onRestoreDefault: () -> Unit
) {
    val hasBackground = backgroundType != 0

    SettingsSection(title = stringResource(Res.string.settings_appearance_background_section)) {
        // 选择背景图片
        ClickableSettingItem(
            title = stringResource(Res.string.settings_appearance_background_image_title),
            subtitle = if (backgroundType == 1) {
                stringResource(Res.string.settings_appearance_background_set)
            } else {
                stringResource(Res.string.settings_appearance_background_select_image)
            },
            icon = Icons.Default.Image,
            onClick = onSelectImageClick
        )

        SettingsDivider()

        // 选择背景视频
        ClickableSettingItem(
            title = stringResource(Res.string.settings_appearance_background_video_title),
            subtitle = if (backgroundType == 2) {
                stringResource(Res.string.settings_appearance_background_set)
            } else {
                stringResource(Res.string.settings_appearance_background_select_video)
            },
            icon = Icons.Default.VideoLibrary,
            onClick = onSelectVideoClick
        )

        // 背景透明度（仅在有背景时显示）
        if (hasBackground) {
            SettingsDivider()

            SliderSettingItem(
                title = stringResource(Res.string.settings_appearance_background_opacity_title),
                subtitle = stringResource(Res.string.settings_appearance_background_opacity_subtitle),
                icon = Icons.Default.Opacity,
                value = backgroundOpacity.toFloat(),
                valueRange = 0f..100f,
                steps = 9,
                valueLabel = "${backgroundOpacity}%",
                onValueChange = { onOpacityChange(it.toInt()) }
            )
        }

        // 视频播放速度（仅在视频背景时显示）
        if (backgroundType == 2) {
            SettingsDivider()

            SliderSettingItem(
                title = stringResource(Res.string.settings_appearance_video_speed_title),
                subtitle = stringResource(Res.string.settings_appearance_video_speed_subtitle),
                icon = Icons.Default.Speed,
                value = videoSpeed,
                valueRange = 0.5f..2.0f,
                steps = 5,
                valueLabel = String.format("%.1fx", videoSpeed),
                onValueChange = onVideoSpeedChange
            )
        }

        // 恢复默认背景（仅在有背景时显示）
        if (hasBackground) {
            SettingsDivider()

            ClickableSettingItem(
                title = stringResource(Res.string.settings_appearance_restore_background_title),
                subtitle = stringResource(Res.string.settings_appearance_restore_background_subtitle),
                icon = Icons.Default.Restore,
                onClick = onRestoreDefault
            )
        }
    }
}

@Composable
private fun LanguageSection(
    language: String,
    onLanguageClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_appearance_language_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.settings_appearance_language_title),
            subtitle = stringResource(Res.string.settings_appearance_language_subtitle),
            value = language,
            icon = Icons.Default.Language,
            onClick = onLanguageClick
        )
    }
}
