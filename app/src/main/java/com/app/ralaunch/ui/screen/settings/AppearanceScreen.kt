package com.app.ralaunch.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.model.AppSettings
import com.app.ralaunch.core.model.ThemeMode
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.component.SectionTitle

import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.ui.component.Switch
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.materialkolor.ktx.toHex

/*******************************************************************************
 * RotatingArtLauncher - AppearanceScreen
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
fun AppearanceScreen() {
    var showColorPicker by remember { mutableStateOf(false) }
    val themeColor by AppConfig.flowOf(AppSettings::themeColor)
        .collectAsStateWithLifecycle(0xFF6750A4.toInt())
    val dynamicColor by AppConfig.flowOf(AppSettings::dynamicColor)
        .collectAsStateWithLifecycle(true)
    val themeMode by AppConfig.flowOf(AppSettings::themeMode)
        .collectAsStateWithLifecycle(ThemeMode.LIGHT)
    val language by AppConfig.flowOf(AppSettings::language)
        .collectAsStateWithLifecycle("auto")

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(themeColor),
            onColorSelected = { color ->
                AppConfig.s.themeColor = color.toArgb()
            },
            onDismiss = { showColorPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        SectionTitle(
            title = Strings.settings.appearance.theme,
            icon = Icons.Rounded.BrightnessMedium
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.DarkMode,
                title = Strings.settings.appearance.themeMode,
                description = Strings.settings.appearance.themeModeDesc,
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val themes = ThemeMode.entries.map { it.string() }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = themeMode.string(),
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
                            themes.forEachIndexed { index, theme ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = theme,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        AppConfig.s.themeMode = ThemeMode.entries[index]
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                })

            SettingItem(
                icon = Icons.Rounded.ColorLens,
                title = Strings.settings.appearance.dynamicColor,
                description = Strings.settings.appearance.dynamicColorDesc,
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { AppConfig.s.dynamicColor = it },
                    )
                },
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )

            SettingItem(
                icon = Icons.Rounded.ColorLens,
                title = Strings.settings.appearance.themeColor,
                description = Strings.settings.appearance.themeColorDesc,
                enabled = !dynamicColor, trailingContent = {
                    Surface(
                        shape = CircleShape,
                        color = if (!dynamicColor) {
                            Color(themeColor)
                        } else {
                            Color(themeColor).copy(alpha = 0.3f)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = !dynamicColor) {
                                if (!dynamicColor) showColorPicker = true
                            }
                    ) {}
                })
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(
            title = Strings.settings.appearance.language,
            icon = Icons.Rounded.Translate
        )

        SettingsGroup {
            SettingItem(
                icon = Icons.Rounded.Language,
                title = Strings.settings.appearance.language,
                description = Strings.settings.appearance.languageDesc, trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    val languages =
                        StringsResource.Language.entries.toTypedArray().map { it.string() }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Text(
                                text = StringsResource.Language.fromCode(language).string(),
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
                            languages.forEachIndexed { index, lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = lang,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        StringsResource.setLanguage(StringsResource.Language.entries[index])
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                })
        }
    }
}

/** 主题模式的旧字符串系统显示名（旧设置界面专用） */
private fun ThemeMode.string(): String = when (this) {
    ThemeMode.LIGHT -> Strings.settings.theme.light
    ThemeMode.DARK -> Strings.settings.theme.dark
    ThemeMode.FOLLOW_SYSTEM -> Strings.settings.system
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var currentColor by remember { mutableStateOf(initialColor) }
    val controller = remember { ColorPickerController() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = Strings.settings.appearance.colorPickerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    initialColor = currentColor,
                    onColorChanged = { colorEnvelope ->
                        currentColor = colorEnvelope.color
                    },
                    controller = controller
                )

                AlphaSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    wheelAlpha = currentColor.alpha,
                    initialColor = currentColor.copy(alpha = 1f),
                    controller = controller
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = currentColor,
                            modifier = Modifier.size(40.dp)
                        ) {}

                        Column {
                            Text(
                                text = "RGB: ${(currentColor.red * 255).toInt()}, " +
                                        "${(currentColor.green * 255).toInt()}, " +
                                        "${(currentColor.blue * 255).toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "HEX: #${currentColor.toHex()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                Strings.cancel,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = {
                                onColorSelected(currentColor)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                Strings.apply,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}