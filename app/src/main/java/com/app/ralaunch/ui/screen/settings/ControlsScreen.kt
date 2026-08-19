package com.app.ralaunch.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.ConfigurationState
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.component.SectionTitle
import com.app.ralaunch.ui.component.SettingItem
import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.ui.component.Switch

/*******************************************************************************
 * RotatingArtLauncher - ControlsScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/7/5)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

@Composable
fun ControlsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionTitle(
            title = Strings.settings.controls.touch,
            icon = Icons.Rounded.TouchApp
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Devices,
                title = Strings.settings.controls.multitouch,
                description = Strings.settings.controls.multitouchDesc,
                trailingContent = {
                    Switch(
                        checked = ConfigurationState.multitouch,
                        onCheckedChange = { ConfigurationState.multitouch = it }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(
            title = Strings.settings.controls.vibration,
            icon = Icons.Rounded.SurroundSound
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.SurroundSound,
                title = Strings.settings.controls.vibrationFeedback,
                description = Strings.settings.controls.vibrationFeedbackDesc,
                trailingContent = {
                    Switch(
                        checked = ConfigurationState.vibration,
                        onCheckedChange = { ConfigurationState.vibration = it }
                    )
                }
            )

            SettingItem(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = Strings.settings.controls.vibrationIntensity,
                description = Strings.settings.controls.vibrationIntensityDesc,
                showDivider = false,
                enabled = ConfigurationState.vibration,
                trailingContent = {
                    Text(
                        text = "${(ConfigurationState.vibrationLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            )

            // 滑块在下方
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Strings.settings.controls.weak,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Strings.settings.controls.strong,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    enabled = ConfigurationState.vibration,
                    value = ConfigurationState.vibrationLevel,
                    onValueChange = { ConfigurationState.vibrationLevel = it },
                    valueRange = 0f..1f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(
            title = Strings.settings.controls.controller,
            icon = Icons.Rounded.Gamepad
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Router,
                title = Strings.settings.controls.virtualController,
                description = Strings.settings.controls.virtualControllerDesc,
                showDivider = false,
                trailingContent = {
                    Switch(
                        checked = ConfigurationState.virtualController,
                        onCheckedChange = { ConfigurationState.virtualController = it },
                    )
                }
            )
        }
    }
}