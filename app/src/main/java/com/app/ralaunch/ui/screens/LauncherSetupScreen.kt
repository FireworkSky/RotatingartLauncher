package com.app.ralaunch.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.locales.LocaleManager.strings
import com.app.ralaunch.ui.model.ComponentItem
import com.app.ralaunch.ui.model.SetupScreen
import com.app.ralaunch.ui.model.SetupState
import com.app.ralaunch.ui.viewmodel.SetupViewModel
import kotlinx.coroutines.delay

@Composable
fun LauncherSetupScreen(
    onInitializationComplete: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    val context = LocalContext.current

    // 关键修复：使用 collectAsStateWithLifecycle 来观察状态变化
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 检查初始化状态
    LaunchedEffect(Unit) {
        viewModel.checkInitializationStatus(context)
    }

    // 监听初始化完成
    LaunchedEffect(state.overallProgress) {
        if (state.overallProgress >= 100 && !state.isExtracting) {
            delay(100)
            onInitializationComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // 左侧信息面板
            InfoPanel(modifier = Modifier.weight(5f))

            Spacer(modifier = Modifier.width(24.dp))

            // 右侧内容面板
            Box(modifier = Modifier.weight(7f)) {
                when (state.currentScreen) {
                    SetupScreen.Legal -> LegalScreen(
                        onDecline = { exitApp(context) },
                        onAccept = {
                            viewModel.acceptLegalAgreement(context)
                        }
                    )
                    SetupScreen.Extraction -> ExtractionScreen(
                        state = state,
                        onStartInstallation = { viewModel.startExtraction(context) },
                        onRetryInstallation = { viewModel.retryExtraction(context) }
                    )
                }

                // 显示错误信息
                state.extractionError?.let { error ->
                    ErrorDialog(
                        error = error,
                        onRetry = { viewModel.retryExtraction(context) },
                        onDismiss = { /* 可以添加取消操作 */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = LocaleManager.strings.appName,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = LocaleManager.strings.appDescription,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )

        HorizontalDivider(
            modifier = Modifier
                .width(120.dp)
                .padding(vertical = 20.dp),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = strings.setupOneStepTitle,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = strings.setupOneStepDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 18.dp)
        )

        Row(
            modifier = Modifier.padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = strings.setupOptimizedForLandscape,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegalScreen(
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // 标题区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "⚖️",
                    fontSize = 36.sp,
                    modifier = Modifier
                        .width(56.dp)
                        .padding(end = 18.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = strings.setupLegalTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = strings.setupLegalContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = strings.setupExitApp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = strings.setupAcceptAndContinue,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractionScreen(
    state: SetupState,
    onStartInstallation: () -> Unit,
    onRetryInstallation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        // 使用 LazyColumn 确保所有内容都能滚动显示
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题区域
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "📦",
                        fontSize = 36.sp,
                        modifier = Modifier
                            .width(56.dp)
                            .padding(end = 18.dp),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = strings.setupExtractionTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 描述文本
            item {
                Text(
                    text = strings.setupExtractionDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            // 总体进度区域
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.overallStatus,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${state.overallProgress}%",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { state.overallProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // 组件列表标题
            item {
                Text(
                    text = strings.setupComponentsListTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 组件列表
            items(state.components) { component ->
                ComponentItemCard(component = component)
            }

            // 操作按钮 - 放在最后，确保始终可见
            item {
                Button(
                    onClick = {
                        if (state.overallProgress >= 100) {
                            onRetryInstallation()
                        } else {
                            onStartInstallation()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    enabled = !state.isExtracting || state.overallProgress >= 100,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = when {
                            state.overallProgress >= 100 -> strings.setupInstallButtonReinstall
                            state.isExtracting -> strings.setupInstallButtonInstalling
                            else -> strings.setupInstallButtonStart
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentItemCard(component: ComponentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = component.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = component.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = component.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    text = "${component.progress}%",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { component.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "安装错误",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(text = error)
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun exitApp(context: Context) {
    // 结束Activity
    (context as? Activity)?.finish()
}