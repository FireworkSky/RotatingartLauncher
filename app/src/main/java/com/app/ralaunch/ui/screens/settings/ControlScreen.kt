package com.app.ralaunch.ui.screens.settings

import androidx.collection.mutableFloatSetOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.app.ralaunch.ui.components.SettingsComponents
import com.app.ralaunch.utils.ConfigManager

@Composable
fun ControlScreen() {
    var virtualJoystickOpacity by remember {
        mutableFloatStateOf(ConfigManager.getInstance().getConfig().controlSettings.virtualJoystickOpacity)
    }
    var vibrationEnabled by remember {
        mutableStateOf(ConfigManager.getInstance().getConfig().controlSettings.vibrationEnabled)
    }

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsComponents.SliderSetting(
                    title = "虚拟手柄透明度",
                    value = virtualJoystickOpacity,
                    onValueChange = { newValue ->
                        val clampedValue = newValue.coerceIn(0f, 1f)
                        virtualJoystickOpacity = clampedValue
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
                    title = "振动反馈",
                    isChecked = vibrationEnabled,
                    onCheckedChange = { newValue ->
                        vibrationEnabled = newValue
                        ConfigManager.getInstance().updateConfig { config ->
                            config.controlSettings.vibrationEnabled = newValue
                        }
                    },
                    icon = Icons.Outlined.Vibration
                )
            }
        }
    }
}