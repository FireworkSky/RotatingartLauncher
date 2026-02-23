package com.app.ralaunch.feature.main.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.shared.feature.settings.*

/**
 * 控制设置子内容
 */
@Composable
internal fun ControlsSettingsContent(
    touchMultitouchEnabled: Boolean,
    onTouchMultitouchChange: (Boolean) -> Unit,
    mouseRightStickEnabled: Boolean,
    onMouseRightStickChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = stringResource(R.string.settings_controls_touch_section)) {
            SwitchSettingItem(
                title = stringResource(R.string.settings_controls_multitouch_title),
                subtitle = stringResource(R.string.settings_controls_multitouch_subtitle),
                icon = Icons.Default.TouchApp,
                checked = touchMultitouchEnabled,
                onCheckedChange = onTouchMultitouchChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = stringResource(R.string.settings_controls_mouse_right_stick_title),
                subtitle = stringResource(R.string.settings_controls_mouse_right_stick_subtitle),
                icon = Icons.Default.Mouse,
                checked = mouseRightStickEnabled,
                onCheckedChange = onMouseRightStickChange
            )
        }

        SettingsSection(title = stringResource(R.string.settings_controls_vibration_section)) {
            SwitchSettingItem(
                title = stringResource(R.string.settings_vibration),
                subtitle = stringResource(R.string.settings_vibration_desc),
                icon = Icons.Default.Gamepad,
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )

            if (vibrationEnabled) {
                SettingsDivider()

                SliderSettingItem(
                    title = stringResource(R.string.settings_controls_vibration_strength_title),
                    value = vibrationStrength,
                    valueRange = 0f..1f,
                    valueLabel = "${(vibrationStrength * 100).toInt()}%",
                    icon = Icons.Default.Tune,
                    onValueChange = onVibrationStrengthChange
                )
            }
        }
    }
}

/**
 * 游戏设置子内容
 */
@Composable
internal fun GameSettingsContent(
    bigCoreAffinityEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    lowLatencyAudioEnabled: Boolean,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    rendererType: String,
    onRendererClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = stringResource(R.string.settings_game_performance_section)) {
            SwitchSettingItem(
                title = stringResource(R.string.thread_affinity_big_core),
                subtitle = stringResource(R.string.thread_affinity_big_core_desc),
                checked = bigCoreAffinityEnabled,
                onCheckedChange = onBigCoreAffinityChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = stringResource(R.string.low_latency_audio),
                subtitle = stringResource(R.string.settings_game_low_latency_audio_subtitle),
                checked = lowLatencyAudioEnabled,
                onCheckedChange = onLowLatencyAudioChange
            )
        }

        SettingsSection(title = stringResource(R.string.settings_game_renderer_section)) {
            ClickableSettingItem(
                title = stringResource(R.string.renderer_title),
                subtitle = stringResource(R.string.renderer_desc),
                value = rendererType,
                onClick = onRendererClick
            )
        }
    }
}
