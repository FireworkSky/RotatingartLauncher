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
import androidx.compose.material3.Scaffold
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
import com.app.ralaunch.ConfigurationState
import com.app.ralaunch.MainActivity
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
                            message = "Logs exported successfully",
                            duration = SnackbarDuration.Short
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            message = "No log files found",
                            duration = SnackbarDuration.Short
                        )
                    }
                    zipFile?.delete()
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        message = "Export failed: ${e.message}",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            SectionTitle(
                title = "日志",
                icon = Icons.Rounded.Description
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Description,
                    title = "启用日志文件",
                    description = "记录应用运行日志到文件",
                    trailingContent = {
                        Switch(
                            checked = ConfigurationState.logFileEnabled,
                            onCheckedChange = { ConfigurationState.logFileEnabled = it }
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Visibility,
                    title = "日志级别",
                    description = "记录日志的最低级别",
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
                                    text = ConfigurationState.logLevel.string(),
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
                                            ConfigurationState.logLevel =
                                                AppLogger.LogLevel.entries.toTypedArray()[index]
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
                    title = "日志文件最大大小",
                    description = "单个日志文件的大小限制 (${ConfigurationState.logFileMaxSize / 1024 / 1024}MB)",
                    enabled = ConfigurationState.logFileEnabled,
                    trailingContent = {
                        Text(
                            text = "${ConfigurationState.logFileMaxSize / 1024 / 1024}MB",
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
                                    enabled = ConfigurationState.logFileEnabled,
                                    value = (ConfigurationState.logFileMaxSize / 1024 / 1024).toFloat(),
                                    onValueChange = { newValue ->
                                        ConfigurationState.logFileMaxSize =
                                            (newValue.toLong() * 1024 * 1024)
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
                    title = "日志文件最大数量",
                    enabled = ConfigurationState.logFileEnabled,
                    description = "保留的日志文件最大数量 (${ConfigurationState.logFileMaxCount}个)",
                    trailingContent = {
                        Text(
                            text = "${ConfigurationState.logFileMaxCount}个",
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
                                    enabled = ConfigurationState.logFileEnabled,
                                    value = ConfigurationState.logFileMaxCount.toFloat(),
                                    onValueChange = { newValue ->
                                        ConfigurationState.logFileMaxCount = newValue.toInt()
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
                    title = "导出日志",
                    description = "导出日志文件到外部存储",
                    trailingContent = {},
                    onClick = {
                        saveLogLauncher.launch("logs_${System.currentTimeMillis()}.zip")
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Download,
                    title = "清空日志",
                    description = "删除所有日志文件", trailingContent = {},
                    onClick = {
                        scope.launch {
                            AppLogger.clearLogs()
                            snackbarHostState.showSnackbar(
                                message = "已清空日志文件",
                                duration = SnackbarDuration.Long
                            )
                        }
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "调试",
                icon = Icons.Rounded.Build
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.ClearAll,
                    title = "结束后台UI",
                    description = "启动游戏后关闭启动器UI", trailingContent = {
                        Switch(
                            checked = killUI,
                            onCheckedChange = { killUI = it }
                        )
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = ".NET",
                icon = Icons.Rounded.IntegrationInstructions
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Storage,
                    title = "Server GC",
                    description = "使用服务器垃圾回收模式",
                    trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Sync,
                    title = "Concurrent GC",
                    description = "启用并发垃圾回收",
                    trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Speed,
                    title = "分层编译",
                    description = "启用分层编译优化", trailingContent = {
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    })
            }
        }
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
