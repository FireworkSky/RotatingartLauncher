package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 控制设置内容 - 跨平台
 */
@Composable
fun ControlsSettingsContent(
    touchMultitouchEnabled: Boolean,
    onTouchMultitouchChange: (Boolean) -> Unit,
    mouseRightStickEnabled: Boolean,
    onMouseRightStickChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit,
    virtualControllerAsFirst: Boolean,
    onVirtualControllerAsFirstChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = stringResource(Res.string.settings_controls_touch_section)) {
            SwitchSettingItem(
                title = stringResource(Res.string.settings_controls_multitouch_title),
                subtitle = stringResource(Res.string.settings_controls_multitouch_subtitle),
                icon = Icons.Default.TouchApp,
                checked = touchMultitouchEnabled,
                onCheckedChange = onTouchMultitouchChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = stringResource(Res.string.settings_controls_mouse_right_stick_title),
                subtitle = stringResource(Res.string.settings_controls_mouse_right_stick_subtitle),
                icon = Icons.Default.Mouse,
                checked = mouseRightStickEnabled,
                onCheckedChange = onMouseRightStickChange
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_controls_vibration_section)) {
            SwitchSettingItem(
                title = stringResource(Res.string.settings_vibration),
                subtitle = stringResource(Res.string.settings_vibration_desc),
                icon = Icons.Default.Gamepad,
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )

            if (vibrationEnabled) {
                SettingsDivider()

                SliderSettingItem(
                    title = stringResource(Res.string.settings_controls_vibration_strength_title),
                    value = vibrationStrength,
                    valueRange = 0f..1f,
                    valueLabel = "${(vibrationStrength * 100).toInt()}%",
                    icon = Icons.Default.Tune,
                    onValueChange = onVibrationStrengthChange
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.settings_controls_controller_section)) {
            SwitchSettingItem(
                title = stringResource(Res.string.virtual_controller_as_first),
                subtitle = stringResource(Res.string.virtual_controller_as_first_desc),
                icon = Icons.Default.Gamepad,
                checked = virtualControllerAsFirst,
                onCheckedChange = onVirtualControllerAsFirstChange
            )
        }
    }
}
