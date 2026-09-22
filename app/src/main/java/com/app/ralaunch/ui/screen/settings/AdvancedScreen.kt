package com.app.ralaunch.ui.screen.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IntegrationInstructions
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.model.AppSettings
import com.app.ralaunch.MainActivity
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.component.SectionTitle

import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.ui.component.Switch
import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream

/*******************************************************************************
 * RotatingArtLauncher - DeveloperScreen
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
fun AdvancedScreen() {
    var killUI by remember { mutableStateOf(false) }
    val logFileEnabled by AppConfig.flowOf(AppSettings::logFileEnabled)
        .collectAsStateWithLifecycle(true)
    val logLevel by AppConfig.flowOf(AppSettings::logLevel)
        .collectAsStateWithLifecycle("INFO")
    val logFileMaxSizeMb by AppConfig.flowOf(AppSettings::logFileMaxSizeMb)
        .collectAsStateWithLifecycle(10)
    val logFileMaxCount by AppConfig.flowOf(AppSettings::logFileMaxCount)
        .collectAsStateWithLifecycle(10)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val zipFile = exportLogsToZip(MainActivity.context!!)
                    if (zipFile != null) {
                        MainActivity.context!!.contentResolver.openOutputStream(uri)
                            ?.use { outputStream ->
                                zipFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        // Clean up temp file
                        snackbarHostState.showSnackbar(
                            message = Strings.settings.advanced.logsExported,
                            duration = SnackbarDuration.Short
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            message = Strings.settings.advanced.noLogFiles,
                            duration = SnackbarDuration.Short
                        )
                    }
                    zipFile?.delete()
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        message = Strings.settings.advanced.exportFailed(
                            e.message ?: Strings.settings.launcher.unknownError
                        ),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            SectionTitle(
                title = Strings.settings.advanced.logs,
                icon = Icons.Rounded.Description
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Description,
                    title = Strings.settings.advanced.enableLogFile,
                    description = Strings.settings.advanced.enableLogFileDesc,
                    trailingContent = {
                        Switch(
                            checked = logFileEnabled,
                            onCheckedChange = {
                                AppConfig.s.logFileEnabled = it
                                AppLogger.updateLoggingConfig(fileEnabled = it)
                            }
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Visibility,
                    title = Strings.settings.advanced.logLevel,
                    description = Strings.settings.advanced.logLevelDesc,
                    trailingContent = {
                        var expanded by remember { mutableStateOf(false) }
                        val levels = AppLogger.LogLevel.entries.map { it.string() }

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Text(
                                    text = AppLogger.LogLevel.fromName(logLevel).string(),
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
                                levels.forEachIndexed { index, theme ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = theme,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            val level = AppLogger.LogLevel.entries.toTypedArray()[index]
                                            AppConfig.s.logLevel = level.name
                                            AppLogger.updateLoggingConfig(logLevel = level)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Storage,
                    title = Strings.settings.advanced.logMaxSize,
                    description = Strings.settings.advanced.logMaxSizeDesc(logFileMaxSizeMb),
                    enabled = logFileEnabled,
                    trailingContent = {
                        Text(
                            text = "${logFileMaxSizeMb}MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    belowContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Slider(
                                    enabled = logFileEnabled,
                                    value = logFileMaxSizeMb.toFloat(),
                                    onValueChange = { newValue ->
                                        // 拖动期间仅更新内存态，松手才落盘
                                        AppConfig.c.logFileMaxSizeMb = newValue.toInt()
                                    },
                                    onValueChangeFinished = {
                                        AppConfig.s.logFileMaxSizeMb = AppConfig.c.logFileMaxSizeMb
                                    },
                                    valueRange = AppLogger.LOG_FILE_MIN_SIZE_MB.toFloat()..AppLogger.LOG_FILE_MAX_SIZE_MB.toFloat(),
                                    steps = AppLogger.LOG_FILE_MAX_SIZE_MB,
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        }
                    }
                )


                SettingItem(
                    icon = Icons.Rounded.Storage,
                    title = Strings.settings.advanced.logMaxCount,
                    enabled = logFileEnabled,
                    description = Strings.settings.advanced.logMaxCountDesc(logFileMaxCount),
                    trailingContent = {
                        Text(
                            text = Strings.settings.advanced.fileCount(logFileMaxCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    belowContent = {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Slider(
                                    enabled = logFileEnabled,
                                    value = logFileMaxCount.toFloat(),
                                    onValueChange = { newValue ->
                                        AppConfig.c.logFileMaxCount = newValue.toInt()
                                    },
                                    onValueChangeFinished = {
                                        AppConfig.s.logFileMaxCount = AppConfig.c.logFileMaxCount
                                    },
                                    valueRange = AppLogger.LOG_FILE_MIN_COUNT.toFloat()..AppLogger.LOG_FILE_MAX_COUNT.toFloat(),
                                    steps = AppLogger.LOG_FILE_MAX_COUNT,
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        }
                    }
                )


                SettingItem(
                    icon = Icons.Rounded.Download,
                    title = Strings.settings.advanced.exportLogs,
                    description = Strings.settings.advanced.exportLogsDesc,
                    trailingContent = {},
                    onClick = {
                        saveLogLauncher.launch("logs_${System.currentTimeMillis()}.zip")
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Download,
                    title = Strings.settings.advanced.clearLogs,
                    description = Strings.settings.advanced.clearLogsDesc, trailingContent = {},
                    onClick = {
                        scope.launch {
                            AppLogger.clearLogs()
                            snackbarHostState.showSnackbar(
                                message = Strings.settings.advanced.logsCleared,
                                duration = SnackbarDuration.Long
                            )
                        }
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = Strings.settings.advanced.debug,
                icon = Icons.Rounded.Build
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.ClearAll,
                    title = Strings.settings.advanced.killUi,
                    description = Strings.settings.advanced.killUiDesc, trailingContent = {
                        Switch(
                            checked = killUI,
                            onCheckedChange = { killUI = it }
                        )
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = Strings.settings.advanced.dotnet,
                icon = Icons.Rounded.IntegrationInstructions
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Storage,
                    title = Strings.settings.advanced.serverGc,
                    description = Strings.settings.advanced.serverGcDesc,
                    trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Sync,
                    title = Strings.settings.advanced.concurrentGc,
                    description = Strings.settings.advanced.concurrentGcDesc,
                    trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Speed,
                    title = Strings.settings.advanced.tieredCompilation,
                    description = Strings.settings.advanced.tieredCompilationDesc, trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    })
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun exportLogsToZip(context: Context): File? {
    val logFiles = AppLogger.getLogFiles()

    if (logFiles.isNullOrEmpty()) {
        Timber.w("No log files found")
        return null
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val zipFile = File(context.cacheDir, "logs_$timestamp.zip")

    ZipArchiveOutputStream(FileOutputStream(zipFile)).use { zos ->
        logFiles.forEach { file ->
            zos.putArchiveEntry(ZipArchiveEntry(file.name))
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeArchiveEntry()
        }
    }

    Timber.i("Exported ${logFiles.size} logs to ${zipFile.absolutePath}")
    return zipFile
}
