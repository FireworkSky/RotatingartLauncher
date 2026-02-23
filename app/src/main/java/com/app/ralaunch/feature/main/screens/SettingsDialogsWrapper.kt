package com.app.ralaunch.feature.main.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.AssetIntegrityChecker

/**
 * 资产完整性检查结果对话框
 */
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
                imageVector = if (result?.isValid == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result?.isValid == true) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                if (isChecking) stringResource(R.string.asset_check_in_progress)
                else if (result?.isValid == true) stringResource(R.string.asset_check_passed)
                else stringResource(R.string.asset_check_issues_found)
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
                    Text(stringResource(R.string.asset_check_checking_message))
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
                                        AssetIntegrityChecker.CheckResult.IssueType.MISSING_FILE -> "⚠"
                                        AssetIntegrityChecker.CheckResult.IssueType.EMPTY_FILE -> "⚠"
                                        AssetIntegrityChecker.CheckResult.IssueType.DIRECTORY_MISSING -> "❌"
                                        AssetIntegrityChecker.CheckResult.IssueType.VERSION_MISMATCH -> "ℹ"
                                        AssetIntegrityChecker.CheckResult.IssueType.CORRUPTED_FILE -> "⚠"
                                        AssetIntegrityChecker.CheckResult.IssueType.PERMISSION_ERROR -> "🔒"
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = issue.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        val canFix = result.issues.any { it.canAutoFix }
                        if (canFix) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.asset_check_auto_fix_tip),
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
                    Text(stringResource(R.string.asset_check_auto_fix))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isChecking
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * 联机功能声明对话框
 */
@Composable
internal fun MultiplayerDisclaimerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.multiplayer_disclaimer_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.multiplayer_disclaimer_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.multiplayer_disclaimer_p2p),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.multiplayer_disclaimer_legal),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
