package com.app.ralaunch.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.app.ralaunch.locales.LocaleManager.strings
import com.app.ralaunch.ui.components.SettingsComponents
import com.app.ralaunch.utils.ConfigManager

@Composable
fun ControlScreen() {
    val virtualJoystickOpacity = remember {
        mutableFloatStateOf(ConfigManager.getInstance().getConfig().controlSettings.virtualJoystickOpacity)
    }
    val vibrationEnabled = remember {
        mutableStateOf(ConfigManager.getInstance().getConfig().controlSettings.vibration)
    }

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsComponents.SliderSetting(
                    title = strings.settingsVirtualJoystickOpacity,
                    value = virtualJoystickOpacity.value,
                    onValueChange = { newValue ->
                        val clampedValue = newValue.coerceIn(0f, 1f)
                        virtualJoystickOpacity.value = clampedValue
                        // 使用传入的 newValue，而不是依赖状态变量
                        ConfigManager.getInstance().updateConfig { config ->
                            config.controlSettings.virtualJoystickOpacity = clampedValue
                        }
                    },
                    valueFormatter = { "${(it * 100).toInt()}%" },
                    icon = Icons.Outlined.Visibility
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsVibrationEnabled,
                    isChecked = vibrationEnabled.value,
                    onCheckedChange = { newValue ->
                        vibrationEnabled.value = newValue
                        ConfigManager.getInstance().updateConfig { config ->
                            config.controlSettings.vibration = newValue
                        }
                    },
                    icon = Icons.Outlined.Vibration
                )
            }
        }
    }
}