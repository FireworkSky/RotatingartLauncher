package com.app.ralaunch.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.utils.AssetIntegrityChecker

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
                if (isChecking) "正在检查..." 
                else if (result?.isValid == true) "检查通过" 
                else "发现问题"
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
                    Text("正在检查资产完整性...")
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
                                "点击「自动修复」可尝试修复上述问题。",
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
                    Text("自动修复")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isChecking
            ) {
                Text("关闭")
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
                text = "联机功能声明",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "联机功能使用 EasyTier (LGPL-3.0) 第三方开源组件，在使用过程中所遇到的问题请通过相关渠道进行反馈。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "联机功能使用 P2P 技术，联机成功后房间内用户之间将直接连接。不会使用第三方服务器对您的流量进行转发。最终联机体验和参与联机者的网络情况有较大关系。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "在多人联机全过程中，您必须严格遵守您所在国家与地区的全部法律法规。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
