package com.app.ralaunch.ui.screen.settings

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.app.ralaunch.BuildConfig
import com.app.ralaunch.MainActivity
import com.app.ralaunch.R
import com.app.ralaunch.core.ui.dialog.LicenseInfo
import com.app.ralaunch.core.ui.dialog.defaultLicenses
import com.app.ralaunch.feature.settings.ui.openSponsorsPage
import com.app.ralaunch.ui.component.SectionTitle
import com.app.ralaunch.ui.component.SettingItem
import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.utils.LauncherUpdateChecker
import com.app.ralaunch.utils.LauncherUpdateInfo
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/*******************************************************************************
 * RotatingArtLauncher - AboutScreen
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
fun AboutScreen() {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<LauncherUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLatest by remember { mutableStateOf(false) }

    var showLicenseDialog by remember { mutableStateOf(false) }

    fun checkForUpdate() {
        scope.launch {
            isChecking = true
            updateErrorMessage = null
            isLatest = false

            try {
                val result = LauncherUpdateChecker.checkForUpdate(
                    context = MainActivity.context!!,
                    currentVersionName = BuildConfig.VERSION_NAME
                )

                result.onSuccess { info ->
                    if (info != null) {
                        updateInfo = info
                        showUpdateDialog = true
                    } else {
                        isLatest = true
                        snackbarHostState.showSnackbar(message = "已是最新版本", duration = SnackbarDuration.Short)
                    }
                }.onFailure { error ->
                    updateErrorMessage = error.message ?: "检查更新失败"

                    snackbarHostState.showSnackbar(message = updateErrorMessage!!, duration = SnackbarDuration.Short)
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "检查更新失败"
                snackbarHostState.showSnackbar(message = updateErrorMessage!!, duration = SnackbarDuration.Short)
            } finally {
                isChecking = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionTitle(
                title = "应用信息",
                icon = Icons.Rounded.Info
            )

            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_init_logo),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "RotatingArt Launcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingItem(
                    icon = Icons.Rounded.Update,
                    title = "检查更新",
                    description = "检查应用是否有新版本",
                    showDivider = false,
                    trailingContent = {},
                    onClick = { checkForUpdate() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "社区",
                icon = Icons.Rounded.Group
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Group,
                    title = "Discord 社区",
                    description = "加入Discord讨论群组",
                    trailingContent = {},
                    onClick = {
                        openUrl("https://discord.gg/cVkrRdffGp")
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Group,
                    title = "QQ 群",
                    description = "加入QQ讨论群组",
                    trailingContent = {},
                    onClick = {
                        openUrl("https://qm.qq.com/q/BWiPSj6wWQ")
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Code,
                    title = "GitHub",
                    description = "查看项目源代码",
                    showDivider = false,
                    trailingContent = {},
                    onClick = {
                        openUrl("https://github.com/FireworkSky/RotatingartLauncher")
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "支持",
                icon = Icons.Rounded.Verified
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = "赞助墙",
                    description = "查看所有赞助者",
                    trailingContent = {},
                    onClick = { openSponsorsPage(MainActivity.context!!) }
                )

                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = "爱发电",
                    description = "支持项目开发",
                    trailingContent = {},
                    onClick = {
                        openUrl("https://afdian.com/a/RotatingartLauncher")
                    }
                )

                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = "Patreon",
                    description = "支持项目开发",
                    showDivider = false,
                    trailingContent = {},
                    onClick = {
                        openUrl("https://www.patreon.com/c/RotatingArtLauncher")
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "开源",
                icon = Icons.Rounded.Copyright
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Copyright,
                    title = "开源许可证",
                    description = "查看使用的开源许可证",
                    showDivider = false,
                    trailingContent = {},
                    onClick = { showLicenseDialog = true }
                )
            }
        }

        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                updateInfo = updateInfo!!,
                onDismiss = {
                    showUpdateDialog = false
                },
                onUpdate = {
                    updateInfo?.let { info ->
                        openUrl(info.downloadUrl)
                    }
                }
            )
        }
        if (showLicenseDialog) LicenseDialog(onDismiss = { showLicenseDialog = false })
    }
}

@Composable
private fun UpdateDialog(
    updateInfo: LauncherUpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 500.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题
                UpdateDialogHeader(updateInfo)

                Spacer(modifier = Modifier.height(16.dp))

                // 更新内容
                UpdateDialogContent(updateInfo)

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮
                UpdateDialogActions(
                    onDismiss = onDismiss,
                    onUpdate = onUpdate
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogHeader(updateInfo: LauncherUpdateInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "发现新版本",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "v${updateInfo.latestVersion}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "最新",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun UpdateDialogContent(updateInfo: LauncherUpdateInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        UpdateVersionInfo(updateInfo)

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            Modifier,
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        UpdateChangelog(updateInfo)
    }
}

@Composable
private fun UpdateVersionInfo(updateInfo: LauncherUpdateInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "当前版本",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "v${updateInfo.currentVersion}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最新版本",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "v${updateInfo.latestVersion}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (updateInfo.publishedAt.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "发布日期",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDate(updateInfo.publishedAt),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdateChangelog(updateInfo: LauncherUpdateInfo) {
    Column {
        Text(
            text = "更新内容",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (updateInfo.releaseNotes.isNotBlank()) {
            val changelogItems = updateInfo.releaseNotes
                .split("\n")
                .filter { it.isNotBlank() }

            if (changelogItems.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(changelogItems) { item ->
                        UpdateChangelogItem(item)
                    }
                }
            } else {
                SelectionContainer {
                    Text(
                        text = updateInfo.releaseNotes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            Text(
                text = "暂无更新说明",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun UpdateChangelogItem(item: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        val displayText = when {
            item.startsWith("•") || item.startsWith("-") || item.startsWith("*") -> item.trim()
            item.startsWith("新增") || item.startsWith("修复") ||
                    item.startsWith("优化") || item.startsWith("更新") -> "• $item"
            else -> item
        }

        Text(
            text = displayText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun UpdateDialogActions(
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        ) {
            Text("稍后更新")
        }

        Button(
            onClick = onUpdate,
            modifier = Modifier.weight(1f)
        ) {
            Text("立即更新")
        }
    }
}

@Composable
private fun LicenseDialog(
    onDismiss: () -> Unit
) {
    val titleText = stringResource(R.string.settings_open_source_licenses)
    val closeText = stringResource(R.string.close)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(16.dp),
        title = {
            Text(titleText, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(defaultLicenses()) { license ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = license.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = license.license,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (license.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = license.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(closeText)
            }
        }
    )
}

private fun defaultLicenses() = listOf(
    LicenseInfo("Kotlin", "Apache 2.0", "Kotlin 编程语言"),
    LicenseInfo("Jetpack Compose", "Apache 2.0", "Android UI 框架"),
    LicenseInfo("Coil", "Apache 2.0", "图片加载库"),
    LicenseInfo("OkHttp", "Apache 2.0", "HTTP 客户端"),
    LicenseInfo("Kotlinx Coroutines", "Apache 2.0", "协程库"),
    LicenseInfo("Kotlinx Serialization", "Apache 2.0", "序列化库"),
    LicenseInfo("Material Icons", "Apache 2.0", "Material Design 图标"),
    LicenseInfo("AndroidX", "Apache 2.0", "Android 扩展库"),
    LicenseInfo("FNA", "Ms-PL", "XNA 移植框架"),
    LicenseInfo("MonoMod", "MIT", "Mono 修改框架")
)


private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)

        val outputFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

private fun openUrl(url: String): Boolean {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        MainActivity.context?.startActivity(browserIntent, Bundle())
        return true
    } catch (e: Exception) {
        Timber.e(e, "Unable to open link: $url, error: ")
    }
    return false
}