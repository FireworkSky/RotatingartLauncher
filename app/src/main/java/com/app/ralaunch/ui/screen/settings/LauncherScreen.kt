package com.app.ralaunch.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.MainActivity
import com.app.ralaunch.ui.component.SectionTitle

import com.app.ralaunch.ui.component.SettingsGroup
import com.app.ralaunch.ui.component.Switch
import com.app.ralaunch.utils.AssetIntegrityChecker
import com.app.ralaunch.utils.AssetsManager
import com.app.ralaunch.utils.PatchManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*******************************************************************************
 * RotatingArtLauncher - LauncherScreen
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
fun LauncherScreen() {
    val context = LocalContext.current
    var multiplayer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Asset check dialog states
    var showAssetCheckDialog by remember { mutableStateOf(false) }
    var isCheckingAssets by remember { mutableStateOf(false) }
    var assetCheckResult by remember { mutableStateOf<AssetIntegrityChecker.CheckResult?>(null) }
    var isReinstalling by remember { mutableStateOf(false) }

    // Asset reinstall progress dialog
    var showReinstallProgress by remember { mutableStateOf(false) }
    var reinstallProgress by remember { mutableIntStateOf(0) }
    var reinstallMessage by remember { mutableStateOf("") }

    // Check integrity on demand
    suspend fun performAssetCheck() {
        try {
            isCheckingAssets = true
            assetCheckResult = null
            val result = AssetIntegrityChecker.checkIntegrity(context)
            assetCheckResult = result

            if (result.isValid) {
                snackbarHostState.showSnackbar(
                    message = "✅ Integrity check passed",
                    duration = SnackbarDuration.Short
                )
                showAssetCheckDialog = false
            } else {
                showAssetCheckDialog = true
            }
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(
                message = "❌ Check failed: ${e.message ?: "Unknown error"}",
                duration = SnackbarDuration.Short
            )
        } finally {
            isCheckingAssets = false
        }
    }

    // Force reinstall all assets
    suspend fun performForceReinstall() {
        try {
            isReinstalling = true
            showReinstallProgress = true
            reinstallProgress = 0
            reinstallMessage = "Starting force reinstall..."

            // Collect progress from AssetsManager state
            val progressJob = scope.launch {
                AssetsManager.state.collectLatest { state ->
                    if (state.isExtracting || state.isComplete) {
                        reinstallProgress = state.overallProgress.coerceIn(0, 100)
                        reinstallMessage = state.statusMessage.ifBlank { "Installing..." }
                    }
                }
            }

            val fixResult = AssetIntegrityChecker.forceReinstall(
                context = context,
                onComplete = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "✅ Assets reinstalled successfully",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            )

            // Cancel progress collection
            progressJob.cancel()

            isReinstalling = false
            showReinstallProgress = false

            if (fixResult.success) {
                assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                snackbarHostState.showSnackbar(
                    message = "✅ ${fixResult.message}",
                    duration = SnackbarDuration.Long
                )
            } else {
                snackbarHostState.showSnackbar(
                    message = "❌ Reinstall failed: ${fixResult.message}",
                    duration = SnackbarDuration.Long
                )
            }
        } catch (e: Exception) {
            isReinstalling = false
            showReinstallProgress = false
            snackbarHostState.showSnackbar(
                message = "❌ Reinstall failed: ${e.message ?: "Unknown error"}",
                duration = SnackbarDuration.Long
            )
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
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            SectionTitle(
                title = "Assets",
                icon = Icons.Rounded.Folder
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Info,
                    title = "Status Summary",
                    description = "Runtime Status",
                    trailingContent = {
                        Text(
                            text = AssetIntegrityChecker.getStatusSummary(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    })

                SettingItem(
                    icon = Icons.Rounded.Verified,
                    title = "Check Integrity",
                    description = "Check launcher asset file integrity",
                    trailingContent = {},
                    onClick = {
                        scope.launch {
                            performAssetCheck()
                        }
                    })

                SettingItem(
                    icon = Icons.Rounded.Download,
                    title = "Force Reinstall Assets",
                    description = "Re-extract all runtime assets", trailingContent = {},
                    onClick = {
                        scope.launch {
                            performForceReinstall()
                        }
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "Multiplayer",
                icon = Icons.Rounded.Wifi
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Wifi,
                    title = "Enable Multiplayer",
                    description = "Enable multiplayer support", trailingContent = {
                        Switch(
                            checked = multiplayer,
                            onCheckedChange = {
                                multiplayer = it
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = if (it) "✅ Multiplayer enabled" else "Multiplayer disabled",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    })
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(
                title = "Patches",
                icon = Icons.Rounded.Sync
            )

            SettingsGroup {
                SettingItem(
                    icon = Icons.Rounded.Update,
                    title = "Force Reinstall Patches",
                    description = "Reinstall all built-in patches", trailingContent = {},
                    onClick = {
                        scope.launch {
                            try {
                                PatchManager.installBuiltInPatches(MainActivity.context!!)
                                snackbarHostState.showSnackbar(
                                    message = "✅ Patches installed successfully",
                                    duration = SnackbarDuration.Short
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    message = "❌ Patch installation failed: ${e.message}",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    })
            }
        }
    }

    // Asset Check Result Dialog
    if (showAssetCheckDialog && !isCheckingAssets) {
        AssetCheckResultDialog(
            isChecking = false,
            result = assetCheckResult,
            onAutoFix = {
                scope.launch {
                    performForceReinstall()
                }
            },
            onDismiss = {
                showAssetCheckDialog = false
            }
        )
    }

    // Reinstall Progress Dialog
    if (showReinstallProgress) {
        ReinstallProgressDialog(
            progress = reinstallProgress,
            message = reinstallMessage,
            onDismiss = {
                // Prevent dismissal during installation
            }
        )
    }
}

// ============================================================
// Asset Check Result Dialog
// ============================================================

@Composable
internal fun AssetCheckResultDialog(
    isChecking: Boolean,
    result: AssetIntegrityChecker.CheckResult?,
    onAutoFix: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        icon = {
            Icon(
                imageVector = if (result?.isValid == true) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (result?.isValid == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                if (isChecking) "Checking..."
                else if (result?.isValid == true) "Integrity Check Passed"
                else "Integrity Issues Found"
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (isChecking) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking asset file integrity...")
                } else if (result != null) {
                    Text(
                        result.summary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (result.issues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        result.issues.forEach { issue ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (issue.type) {
                                        AssetIntegrityChecker.CheckResult.IssueType.MISSING_FILE -> "⚠️"
                                        AssetIntegrityChecker.CheckResult.IssueType.EMPTY_FILE -> "⚠️"
                                        AssetIntegrityChecker.CheckResult.IssueType.DIRECTORY_MISSING -> "❌"
                                        AssetIntegrityChecker.CheckResult.IssueType.VERSION_MISMATCH -> "ℹ️"
                                        AssetIntegrityChecker.CheckResult.IssueType.CORRUPTED_FILE -> "⚠️"
                                        AssetIntegrityChecker.CheckResult.IssueType.PERMISSION_ERROR -> "🔒"
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = issue.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val canFix = result.issues.any { it.canAutoFix }
                        if (canFix) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Click 'Auto Fix' below to reinstall affected components.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isChecking && result?.issues?.any { it.canAutoFix } == true) {
                TextButton(onClick = onAutoFix) {
                    Text("Auto Fix")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isChecking
            ) {
                Text("Close")
            }
        }
    )
}


@Composable
internal fun ReinstallProgressDialog(
    progress: Int,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal */ },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Reinstalling Assets...")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = false
            ) {
                Text("Please wait...")
            }
        },
        dismissButton = null
    )
}