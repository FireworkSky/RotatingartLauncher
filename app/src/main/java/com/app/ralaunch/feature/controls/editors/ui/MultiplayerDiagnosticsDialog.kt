package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.network.easytier.DiagStepResult
import com.app.ralaunch.core.platform.network.easytier.DiagStepStatus
import com.app.ralaunch.core.platform.network.easytier.EasyTierDiagnostics
import kotlinx.coroutines.launch

/**
 * 联机诊断弹窗
 * 逐步测试 EasyTier 联机链路的每个环节
 */
@Composable
fun MultiplayerDiagnosticsDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialStepTitles = listOf(
        stringResource(R.string.control_editor_diag_jni_load),
        stringResource(R.string.control_editor_diag_config_generate),
        stringResource(R.string.control_editor_diag_config_parse),
        stringResource(R.string.control_editor_diag_instance_start),
        stringResource(R.string.control_editor_diag_network_collect),
        stringResource(R.string.control_editor_diag_port_check),
        stringResource(R.string.control_editor_diag_cleanup)
    )
    var isRunning by remember { mutableStateOf(false) }
    var steps by remember(initialStepTitles) {
        mutableStateOf(
            initialStepTitles.map { DiagStepResult(it) }
        )
    }
    var expandedStep by remember { mutableStateOf<Int?>(null) }

    // 全屏半透明遮罩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isRunning) onDismiss() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .heightIn(max = 500.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // 阻止点击穿透
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.control_editor_multiplayer_diagnostics),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { if (!isRunning) onDismiss() },
                        enabled = !isRunning
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 步骤列表
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        DiagStepItem(
                            index = index,
                            step = step,
                            isExpanded = expandedStep == index,
                            onClick = {
                                expandedStep = if (expandedStep == index) null else index
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isRunning) onDismiss() },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning
                    ) {
                        Text(stringResource(R.string.close))
                    }

                    Button(
                        onClick = {
                            isRunning = true
                            expandedStep = null
                            // 重置所有步骤
                            steps = steps.map { it.copy(status = DiagStepStatus.PENDING, message = "", detail = null) }
                            scope.launch {
                                try {
                                    EasyTierDiagnostics.runFullDiagnostics { index, result ->
                                        steps = steps.toMutableList().also { it[index] = result }
                                        // 自动展开当前正在运行的步骤
                                        if (result.status == DiagStepStatus.RUNNING) {
                                            expandedStep = index
                                        }
                                        // 展开失败的步骤
                                        if (result.status == DiagStepStatus.FAILED) {
                                            expandedStep = index
                                        }
                                    }
                                } finally {
                                    isRunning = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.control_editor_diagnosing))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.control_editor_start_diagnosis))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个诊断步骤项
 */
@Composable
private fun DiagStepItem(
    index: Int,
    step: DiagStepResult,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (step.status) {
        DiagStepStatus.PENDING -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        DiagStepStatus.RUNNING -> MaterialTheme.colorScheme.primary
        DiagStepStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        DiagStepStatus.FAILED -> MaterialTheme.colorScheme.error
        DiagStepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isExpanded)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 状态图标
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (step.status) {
                        DiagStepStatus.PENDING -> {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                        DiagStepStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = statusColor
                            )
                        }
                        DiagStepStatus.SUCCESS -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = statusColor
                            )
                        }
                        DiagStepStatus.FAILED -> {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = statusColor
                            )
                        }
                        DiagStepStatus.SKIPPED -> {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = statusColor
                            )
                        }
                    }
                }

                // 步骤名称和消息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (step.message.isNotEmpty()) {
                        Text(
                            text = step.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                }

                // 展开箭头
                if (step.detail != null) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 展开详情
            if (isExpanded && step.detail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = step.detail,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
