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
import com.app.ralaunch.feature.settings.ui.openSponsorsPage
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.strings.generated.ZhHans
import com.app.ralaunch.ui.component.SectionTitle

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
                        snackbarHostState.showSnackbar(
                            message = Strings.settings.about.alreadyLatest,
                            duration = SnackbarDuration.Short
                        )
                    }
                }.onFailure { error ->
                    updateErrorMessage = error.message ?: Strings.settings.about.checkFailed

                    snackbarHostState.showSnackbar(
                        message = updateErrorMessage!!,
                        duration = SnackbarDuration.Short
                    )
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: Strings.settings.about.checkFailed
                snackbarHostState.showSnackbar(
                    message = updateErrorMessage!!,
                    duration = SnackbarDuration.Short
                )
            } finally {
                isChecking = false
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
                title = Strings.settings.about.appInfo,
                icon = Icons.Rounded.Info
            )

            SettingsGroup {
                Item {
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
                }

                SettingItem(
                    icon = Icons.Rounded.Update,
                    title = Strings.settings.about.checkUpdate,
                    description = Strings.settings.about.checkUpdateDesc, trailingContent = {},
                    onClick = { checkForUpdate() })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = Strings.settings.about.community,
                icon = Icons.Rounded.Group
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Group,
                    title = Strings.settings.about.discord,
                    description = Strings.settings.about.discordDesc,
                    trailingContent = {},
                    onClick = {
                        openUrl("https://discord.gg/cVkrRdffGp")
                    })

                SettingItem(
                    icon = Icons.Rounded.Group,
                    title = Strings.settings.about.qqGroup,
                    description = Strings.settings.about.qqGroupDesc,
                    trailingContent = {},
                    onClick = {
                        openUrl("https://qm.qq.com/q/BWiPSj6wWQ")
                    })

                SettingItem(
                    icon = Icons.Rounded.Code,
                    title = Strings.settings.about.github,
                    description = Strings.settings.about.githubDesc, trailingContent = {},
                    onClick = {
                        openUrl("https://github.com/FireworkSky/RotatingartLauncher")
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = Strings.settings.about.support,
                icon = Icons.Rounded.Verified
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = Strings.settings.about.sponsorWall,
                    description = Strings.settings.about.sponsorWallDesc,
                    trailingContent = {},
                    onClick = { openSponsorsPage(MainActivity.context!!) })

                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = Strings.settings.about.afdian,
                    description = Strings.settings.about.supportDesc,
                    trailingContent = {},
                    onClick = {
                        openUrl("https://afdian.com/a/RotatingartLauncher")
                    })

                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = Strings.settings.about.patreon,
                    description = Strings.settings.about.supportDesc, trailingContent = {},
                    onClick = {
                        openUrl("https://www.patreon.com/c/RotatingArtLauncher")
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = Strings.settings.about.openSource,
                icon = Icons.Rounded.Copyright
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Copyright,
                    title = Strings.settings.about.openSourceLicenses,
                    description = Strings.settings.about.openSourceLicensesDesc, trailingContent = {},
                    onClick = { showLicenseDialog = true })
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
                text = Strings.settings.about.newVersionFound,
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
                text = Strings.settings.about.latestBadge,
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
                text = Strings.settings.about.currentVersion,
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
                text = Strings.settings.about.latestVersion,
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
                    text = Strings.settings.about.publishDate,
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
            text = Strings.settings.about.changelog,
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
                text = Strings.settings.about.noReleaseNotes,
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
            Text(Strings.settings.about.updateLater)
        }

        Button(
            onClick = onUpdate,
            modifier = Modifier.weight(1f)
        ) {
            Text(Strings.settings.about.updateNow)
        }
    }
}

@Composable
private fun LicenseDialog(
    onDismiss: () -> Unit
) {
    val titleText = Strings.settings.about.openSourceLicenses
    val closeText = Strings.close

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
    LicenseInfo("Kotlin", "Apache 2.0", Strings.settings.about.licenseKotlin),
    LicenseInfo("Jetpack Compose", "Apache 2.0", Strings.settings.about.licenseCompose),
    LicenseInfo("Coil", "Apache 2.0", Strings.settings.about.licenseCoil),
    LicenseInfo("OkHttp", "Apache 2.0", Strings.settings.about.licenseOkhttp),
    LicenseInfo("Kotlinx Coroutines", "Apache 2.0", Strings.settings.about.licenseCoroutines),
    LicenseInfo("Kotlinx Serialization", "Apache 2.0", Strings.settings.about.licenseSerialization),
    LicenseInfo("Material Icons", "Apache 2.0", Strings.settings.about.licenseMaterialIcons),
    LicenseInfo("AndroidX", "Apache 2.0", Strings.settings.about.licenseAndroidx),
    LicenseInfo("FNA", "Ms-PL", Strings.settings.about.licenseFna),
    LicenseInfo("MonoMod", "MIT", Strings.settings.about.licenseMonomod)
)


private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)

        // 日期格式跟随应用语言（而非系统区域设置）
        val isZh = StringsResource.Strings === ZhHans
        val outputFormat = SimpleDateFormat(
            if (isZh) "yyyy年MM月dd日" else "MMM d, yyyy",
            if (isZh) Locale.CHINA else Locale.US
        )
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