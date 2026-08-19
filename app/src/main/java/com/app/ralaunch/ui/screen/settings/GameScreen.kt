package com.app.ralaunch.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IntegrationInstructions
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.ConfigurationState
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.component.SectionTitle
import com.app.ralaunch.ui.component.SettingItem
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        checked = ConfigurationState.bigCore,
                        onCheckedChange = { ConfigurationState.bigCore = it }
                    )
                }
            )

            SettingItem(
                icon = Icons.Rounded.Audiotrack,
                title = Strings.settings.game.lowLatency,
                description = Strings.settings.game.lowLatencyDesc,
                showDivider = false,
                trailingContent = {
                    Switch(
                        checked = ConfigurationState.lowLatency,
                        onCheckedChange = { ConfigurationState.lowLatency = it }
                    )
                }
            )
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
                    val presets = ConfigurationState.AppConfig.QualityPreset.entries.toTypedArray().map { it.getDisplayName() }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = ConfigurationState.qualityPreset.getDisplayName(),
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
                                        ConfigurationState.qualityPreset = ConfigurationState.AppConfig.QualityPreset.entries.toTypedArray()[index]
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            SettingItem(
                icon = Icons.Rounded.IntegrationInstructions,
                title = Strings.settings.game.shaderPrecision,
                description = Strings.settings.game.shaderPrecisionDesc,
                trailingContent = {
                    Switch(
                        checked = ConfigurationState.shaderPrecision,
                        onCheckedChange = { ConfigurationState.shaderPrecision = it }
                    )
                }
            )

            SettingItem(
                icon = Icons.Rounded.MoreVert,
                title = Strings.settings.game.fpsLimit,
                description = Strings.settings.game.fpsLimitDesc,
                showDivider = false,
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val limits = ConfigurationState.AppConfig.FpsLimit.entries.toTypedArray()

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = if (ConfigurationState.fpsLimit.value == 0)
                                    Strings.settings.game.fpsUnlimited
                                else "${ConfigurationState.fpsLimit.value} fps",
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
                                        ConfigurationState.fpsLimit = ConfigurationState.AppConfig.FpsLimit.entries.toTypedArray()[index]
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
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
                                text = ConfigurationState.dotnetVersion.ifEmpty {
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
                                        ConfigurationState.dotnetVersion = version
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            SettingItem(
                icon = Icons.Rounded.GraphicEq,
                title = Strings.settings.game.renderer,
                description = Strings.settings.game.rendererDesc,
                showDivider = false,
                onClick = {
                    showRendererDialog = true
                },
                trailingContent = {
                    Text(
                        text = ConfigurationState.renderer.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }

        if (showRendererDialog) {
            RendererSelectionDialog(
                currentRenderer = ConfigurationState.renderer,
                onRendererSelected = { selected ->
                    ConfigurationState.renderer = selected
                },
                onDismiss = { showRendererDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RendererSelectionDialog(
    currentRenderer: ConfigurationState.AppConfig.Renderer,
    onRendererSelected: (ConfigurationState.AppConfig.Renderer) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRenderer by remember { mutableStateOf(currentRenderer) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Strings.settings.game.renderer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Strings.settings.game.rendererDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 渲染器列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(ConfigurationState.AppConfig.Renderer.entries) { renderer ->
                        RendererItem(
                            renderer = renderer,
                            isSelected = selectedRenderer == renderer,
                            onClick = { selectedRenderer = renderer }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(Strings.cancel, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            onRendererSelected(selectedRenderer)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .padding(start = 4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(Strings.apply, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RendererItem(
    renderer: ConfigurationState.AppConfig.Renderer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = renderer.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                when (renderer) {
                    ConfigurationState.AppConfig.Renderer.GL4ES_ANGLE ->
                        Tag(Strings.settings.renderer.recommended, isSelected)
                    ConfigurationState.AppConfig.Renderer.VULKAN ->
                        Tag(Strings.settings.renderer.experimental, isSelected)
                    else -> Unit
                }
            }

            Text(
                text = renderer.getDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Tag(text: String, isSelected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.15f else 0.08f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}