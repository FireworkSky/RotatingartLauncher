package com.app.ralaunch.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.app.ralaunch.MainActivity
import com.app.ralaunch.R
import com.app.ralaunch.utils.AssetsManager
import kotlin.time.Duration.Companion.milliseconds

/*******************************************************************************
 * RotatingArtLauncher - InitializationScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/7/4)
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

@SuppressLint("InlinedApi", "UseKtx")
@Composable
fun InitializationScreen(
    appContext: android.content.Context,
    prefs: android.content.SharedPreferences,
    onNavigateToMain: () -> Unit = {}
) {
    val context = LocalContext.current

    // ====== 从 AssetsManager 获取状态 ======
    val installState by AssetsManager.state.collectAsState()
    val hasPermissions by remember { derivedStateOf { MainActivity.hasStoragePermission } }

    // ====== 页面状态 ======
    var currentPage by remember { mutableStateOf(InitPage.LEGAL) }

    // ====== 初始化 ======
    LaunchedEffect(Unit) {
        AssetsManager.init()
    }

    // ====== 监听安装完成 ======
    LaunchedEffect(installState.isComplete) {
        if (installState.isComplete) {
            kotlinx.coroutines.delay(1000.milliseconds)
            onNavigateToMain()
        }
    }

    // ====== 权限请求 ======
    @SuppressLint("UseKtx")
    fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // 忽略
                }
            }
        }
    }

    // ====== UI ======
    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Column {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page_transition"
            ) { page ->
                when (page) {
                    InitPage.LEGAL -> LegalPage(
                        onAccept = {
                            currentPage = InitPage.SETUP
                        },
                        onDecline = {
                            // 退出
                        },
                        onOpenOfficialDownload = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        "https://github.com/FireworkSky/RotatingartLauncher".toUri())
                                )
                            } catch (_: Exception) {
                                // 忽略
                            }
                        }
                    )
                    InitPage.SETUP -> SetupPage(
                        installState = installState,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { requestManageStoragePermission() },
                        onStartInstallation = {
                            AssetsManager.startInstallation(
                                context = appContext,
                                prefs = prefs,
                                onComplete = onNavigateToMain
                            )
                        },
                        onCancelInstallation = { AssetsManager.cancelInstallation() },
                        onReset = { AssetsManager.reset(prefs) }
                    )
                }
            }
        }
    }
}

// ============================================================
// 枚举和数据类
// ============================================================

enum class InitPage { LEGAL, SETUP }

// ============================================================
// UI 组件 - LegalPage
// ============================================================

@Composable
private fun LegalPage(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenOfficialDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_init_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(76.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.main_splash_brand),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_version, "1.0.0"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "用户协议",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = """
                        欢迎使用 RotatingArtLauncher！

                        使用本软件即表示您同意以下条款：
                        
                        1. 本软件基于 GPL v3 协议开源
                        2. 您可以在遵守开源协议的前提下自由使用
                        3. 本项目不承担任何直接或间接损失的责任
                        4. 所有第三方组件均遵循其各自的许可证
                        
                        点击 "接受并继续" 表示您已阅读并同意以上条款。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenOfficialDownload) {
                    Text("访问官方仓库")
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDecline) {
                        Text("退出")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onAccept) {
                        Text("接受并继续")
                    }
                }
            }
        }
    }
}

// ============================================================
// UI 组件 - SetupPage
// ============================================================

@Composable
private fun SetupPage(
    installState: AssetsManager.InstallState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStartInstallation: () -> Unit,
    onCancelInstallation: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左侧控制区
        Card(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "运行时组件安装",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                StatusHeader(
                    isComplete = installState.isComplete,
                    isExtracting = installState.isExtracting,
                    hasPermissions = hasPermissions
                )

                LinearProgressIndicator(
                    progress = { installState.overallProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Text(
                    text = "${installState.overallProgress}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = installState.statusMessage.ifBlank { "准备就绪" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 错误信息
                installState.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                    ErrorCard(error = error)
                }

                // 权限提示
                if (!hasPermissions && !installState.isComplete) {
                    PermissionHint()
                }

                Spacer(modifier = Modifier.weight(1f))

                // 按钮组
                ActionButtons(
                    hasPermissions = hasPermissions,
                    isExtracting = installState.isExtracting,
                    isComplete = installState.isComplete,
                    onRequestPermissions = onRequestPermissions,
                    onStartInstallation = onStartInstallation,
                    onCancelInstallation = onCancelInstallation,
                    onReset = onReset
                )
            }
        }

        // 右侧组件列表 - 关键修复：传入 weight modifier
        ComponentListCard(
            components = installState.components,
            isComplete = installState.isComplete,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
        )
    }
}

// ============================================================
// 子组件
// ============================================================

@Composable
private fun StatusHeader(
    isComplete: Boolean,
    isExtracting: Boolean,
    hasPermissions: Boolean
) {
    Text(
        text = when {
            isComplete -> "安装完成 ✅"
            isExtracting -> "正在安装... ⏳"
            !hasPermissions -> "⚠️ 需要授予管理所有文件权限"
            else -> "点击下方按钮开始安装"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            isComplete -> MaterialTheme.colorScheme.primary
            !hasPermissions -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun ErrorCard(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PermissionHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚠️ 需要管理所有文件权限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "此权限用于在设备存储中安装运行时组件。\n点击下方按钮授予权限。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ActionButtons(
    hasPermissions: Boolean,
    isExtracting: Boolean,
    isComplete: Boolean,
    onRequestPermissions: () -> Unit,
    onStartInstallation: () -> Unit,
    onCancelInstallation: () -> Unit,
    onReset: () -> Unit
) {
    when {
        !hasPermissions && !isComplete -> {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_manage),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("授予管理所有文件权限")
            }
        }
        isExtracting -> {
            OutlinedButton(
                onClick = onCancelInstallation,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("取消安装")
            }
        }
        isComplete -> {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新安装")
            }
        }
        else -> {
            Button(
                onClick = onStartInstallation,
                enabled = hasPermissions && !isExtracting && !isComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_save),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始安装")
            }
        }
    }
}

@Composable
private fun ComponentListCard(
    components: List<AssetsManager.ComponentState>,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(),  // 添加 fillMaxWidth 确保宽度填满
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "组件列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${components.count { it.isInstalled }}/${components.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isComplete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (components.isEmpty()) {
                    Text(
                        text = "暂无组件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    components.forEach { component ->
                        ComponentItem(component = component)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentItem(
    component: AssetsManager.ComponentState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                when {
                    component.isInstalled -> Icon(
                        painter = painterResource(android.R.drawable.stat_notify_more),
                        contentDescription = "已安装",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    component.progress > 0 -> Text(
                        "${component.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = component.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (component.status.isNotBlank()) {
                Text(
                    text = component.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = {
                    if (component.isInstalled) 1f else component.progress / 100f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (component.isInstalled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            )
        }
    }
}