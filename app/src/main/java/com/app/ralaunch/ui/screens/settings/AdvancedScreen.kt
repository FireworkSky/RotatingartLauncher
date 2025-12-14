package com.app.ralaunch.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.app.ralaunch.locales.AppLanguage
import com.app.ralaunch.locales.AppLanguage.Companion.fromDisplayName
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.locales.LocaleManager.strings
import com.app.ralaunch.ui.components.SettingsComponents
import com.app.ralaunch.utils.ConfigManager

const val CONFIRM = "Confirm"

@Composable
@Preview
fun AdvancedScreen() {
    val renderer = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.renderer) }
    val serverGcEnabled = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.serverGc) }
    val concurrentGcEnabled = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.concurrentGc) }
    val tieredCompilationEnabled = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.tieredCompilation) }
    val coreClrDebugLogEnabled = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.coreClrDebugLog) }
    val threadAffinityEnabled = remember { mutableStateOf(ConfigManager.getInstance().getConfig().advancedSettings.threadAffinity) }

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsComponents.DropdownSetting(
                    title = strings.settingsRenderer,
                    icon = Icons.Default.Tune,
                    options = listOf(
                        strings.settingsAuto,
                        ConfigManager.Renderer.NATIVE_OPENGL_ES.displayName,
                        ConfigManager.Renderer.GL4ES.displayName,
                        ConfigManager.Renderer.GL4ES_ANGLE.displayName,
                        ConfigManager.Renderer.MOBILE_GL.displayName,
                        ConfigManager.Renderer.ANGLE_VULKAN.displayName,
                        ConfigManager.Renderer.ZINK_MESA.displayName,
                        ConfigManager.Renderer.ZINK_MESA_25.displayName,
                        ConfigManager.Renderer.VIRGL.displayName,
                        ConfigManager.Renderer.FREEDRENO.displayName
                    ),
                    selectedOption = if (renderer.value == ConfigManager.Renderer.AUTO) strings.settingsAuto else renderer.value.displayName,
                    onOptionSelected = { name ->
                        ConfigManager.getInstance().updateConfig {
                            renderer.value = ConfigManager.Renderer.fromDisplayName(name)
                            it.advancedSettings.renderer = ConfigManager.Renderer.fromDisplayName(name)
                        }
                    }
                )
            }


            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsServerGc,
                    icon = Icons.Default.Memory,
                    description = strings.settingsServerGcDescription,
                    isChecked = serverGcEnabled.value,
                    onCheckedChange = {
                        ConfigManager.getInstance().updateConfig { config ->
                            serverGcEnabled.value = it
                            config.advancedSettings.serverGc = it
                        }
                    }
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsConcurrentGc,
                    icon = Icons.Default.Memory,
                    description = strings.settingsConcurrentGcDescription,
                    isChecked = concurrentGcEnabled.value,
                    onCheckedChange = {
                        ConfigManager.getInstance().updateConfig { config ->
                            concurrentGcEnabled.value = it
                            config.advancedSettings.concurrentGc = it
                        }
                    }
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsTieredCompilation,
                    icon = Icons.Default.Memory,
                    description = strings.settingsTieredCompilationDescription,
                    isChecked = tieredCompilationEnabled.value,
                    onCheckedChange = {
                        ConfigManager.getInstance().updateConfig { config ->
                            tieredCompilationEnabled.value = it
                            config.advancedSettings.tieredCompilation = it
                        }
                    }
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsCoreClrDebugLog,
                    icon = Icons.Filled.BugReport,
                    isChecked = coreClrDebugLogEnabled.value,
                    onCheckedChange = {
                        ConfigManager.getInstance().updateConfig { config ->
                            coreClrDebugLogEnabled.value = it
                            config.advancedSettings.coreClrDebugLog = it
                        }
                    }
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsThreadAffinity,
                    icon = Icons.Filled.Theaters,
                    description = strings.settingsThreadAffinityDescription,
                    isChecked = threadAffinityEnabled.value,
                    onCheckedChange = {
                        ConfigManager.getInstance().updateConfig { config ->
                            threadAffinityEnabled.value = it
                            config.advancedSettings.threadAffinity = it
                        }
                    }
                )
            }
        }
    }
}