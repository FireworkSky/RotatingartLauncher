package com.app.ralaunch.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IntegrationInstructions
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.model.AppSettings
import com.app.ralaunch.core.model.FpsLimit
import com.app.ralaunch.core.model.QualityLevel
import com.app.ralaunch.core.platform.runtime.AndroidRendererRegistry
import com.app.ralaunch.core.platform.runtime.RendererRegistry
import com.app.ralaunch.core.ui.dialog.RendererSelectDialog
import com.app.ralaunch.feature.settings.ui.buildRendererOptions
import com.app.ralaunch.strings.StringsResource.Strings
import kotlinx.coroutines.flow.map
import com.app.ralaunch.ui.component.SectionTitle

import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.ui.component.Switch
import java.io.File

/*******************************************************************************
 * RotatingArtLauncher - GameScreen
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
fun GameScreen() {
    var showRendererDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val availableRenderers = remember { buildRendererOptions() }
    val rendererId by AppConfig.flowOf(AppSettings::fnaRenderer)
        .map { RendererRegistry.normalizeRendererId(it) }
        .collectAsStateWithLifecycle("native")
    val qualityLevel by AppConfig.flowOf(AppSettings::qualityLevel)
        .map { QualityLevel.fromValue(it) }
        .collectAsStateWithLifecycle(QualityLevel.HIGH)
    val targetFps by AppConfig.flowOf(AppSettings::targetFps)
        .map { FpsLimit.fromValue(it) }
        .collectAsStateWithLifecycle(FpsLimit.UNLIMITED)
    val bigCore by AppConfig.flowOf(AppSettings::setThreadAffinityToBigCore)
        .collectAsStateWithLifecycle(false)
    val lowLatency by AppConfig.flowOf(AppSettings::sdlAaudioLowLatency)
        .collectAsStateWithLifecycle(false)
    val shaderLowPrecision by AppConfig.flowOf(AppSettings::shaderLowPrecision)
        .collectAsStateWithLifecycle(false)
    val dotnetVersion by AppConfig.flowOf(AppSettings::selectedDotnetRuntimeVersion)
        .collectAsStateWithLifecycle("")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        SectionTitle(
            title = Strings.settings.game.performance,
            icon = Icons.Rounded.Speed
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Memory,
                title = Strings.settings.game.bigCore,
                description = Strings.settings.game.bigCoreDesc,
                trailingContent = {
                    Switch(
                        checked = bigCore,
                        onCheckedChange = { AppConfig.s.setThreadAffinityToBigCore = it }
                    )
                })

            SettingItem(
                icon = Icons.Rounded.Audiotrack,
                title = Strings.settings.game.lowLatency,
                description = Strings.settings.game.lowLatencyDesc, trailingContent = {
                    Switch(
                        checked = lowLatency,
                        onCheckedChange = { AppConfig.s.sdlAaudioLowLatency = it }
                    )
                })
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(
            title = Strings.settings.game.quality,
            icon = Icons.Rounded.Texture
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Settings,
                title = Strings.settings.game.qualityPreset,
                description = Strings.settings.game.qualityPresetDesc,
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val presets = QualityLevel.entries.map { it.displayName() }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = qualityLevel.displayName(),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            presets.forEachIndexed { index, preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        AppConfig.s.qualityLevel = QualityLevel.entries[index].value
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                })

            SettingItem(
                icon = Icons.Rounded.IntegrationInstructions,
                title = Strings.settings.game.shaderPrecision,
                description = Strings.settings.game.shaderPrecisionDesc,
                trailingContent = {
                    Switch(
                        checked = shaderLowPrecision,
                        onCheckedChange = { AppConfig.s.shaderLowPrecision = it }
                    )
                })

            SettingItem(
                icon = Icons.Rounded.MoreVert,
                title = Strings.settings.game.fpsLimit,
                description = Strings.settings.game.fpsLimitDesc, trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val limits = FpsLimit.entries.toList()

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = if (targetFps.value == 0)
                                    Strings.settings.game.fpsUnlimited
                                else "${targetFps.value} fps",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            limits.forEachIndexed { index, limit ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (limit.value == 0)
                                                Strings.settings.game.fpsUnlimited
                                            else "${limit.value}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        AppConfig.s.targetFps = FpsLimit.entries[index].value
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                })
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(
            title = Strings.settings.game.runtime,
            icon = Icons.Rounded.Build
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Storage,
                title = Strings.settings.game.dotnetRuntime,
                description = Strings.settings.game.dotnetRuntimeDesc,
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val dotnetDir = File(context.filesDir, "runtimes/dotnet")
                    val netsVersion = if (dotnetDir.exists() && dotnetDir.isDirectory) {
                        dotnetDir.listFiles { file ->
                            file.isDirectory
                        }?.map { it.name } ?: emptyList()
                    } else {
                        emptyList()
                    }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = dotnetVersion.ifEmpty {
                                    Strings.settings.game.dotnetNotSelected
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            netsVersion.forEach { version ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = version,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        AppConfig.s.selectedDotnetRuntimeVersion = version
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                })

            SettingItem(
                icon = Icons.Rounded.GraphicEq,
                title = Strings.settings.game.renderer,
                description = Strings.settings.game.rendererDesc, onClick = {
                    showRendererDialog = true
                },
                trailingContent = {
                    Text(
                        text = AndroidRendererRegistry.getRendererDisplayName(rendererId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                })
        }

        if (showRendererDialog) {
            RendererSelectDialog(
                currentRenderer = rendererId,
                renderers = availableRenderers,
                onSelect = { selected ->
                    AppConfig.s.fnaRenderer = selected
                },
                onDismiss = { showRendererDialog = false }
            )
        }
    }
}

/** 画质预设的旧字符串系统显示名（旧设置界面专用） */
private fun QualityLevel.displayName(): String = when (this) {
    QualityLevel.HIGH -> Strings.settings.game.qualityHigh
    QualityLevel.MEDIUM -> Strings.settings.game.qualityMedium
    QualityLevel.LOW -> Strings.settings.game.qualityLow
}
