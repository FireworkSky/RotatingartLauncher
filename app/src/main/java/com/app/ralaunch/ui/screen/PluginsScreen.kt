package com.app.ralaunch.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.ui.component.Switch
import com.app.ralaunch.core.common.util.StreamUtils
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import com.app.ralaunch.models.ModuleItem
import com.app.ralaunch.utils.ModuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber
import java.nio.file.Files

/*******************************************************************************
 * RotatingArtLauncher - PluginsScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: LaoSparrow (2026/10/27)
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

/**
 * 插件页面 - 纯 Material Design 3，无 ViewModel
 *
 * 直接收集 [ModuleManager] 模块状态：启用开关（[ModuleManager.setEnabled]）、
 * 删除（[ModuleManager.remove]）、zip 安装（[ModuleManager.install]，版本不高时跳过）
 * 直连管理器，反馈用 Toast，风格与 [GameListScreen] 一致。
 */
@Composable
fun PluginsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modules by remember { ModuleManager.modules }
        .collectAsStateWithLifecycle(ModuleManager.currentModules)

    var pendingDeletion by remember { mutableStateOf<ModuleItem?>(null) }

    // 进入页面时与磁盘对账：管理目录被外部改动（如手动放入模块目录）也能被发现
    LaunchedEffect(Unit) {
        runCatching { ModuleManager.refresh() }
    }

    // zip 安装：SAF 选中后落临时文件再交给管理器（同补丁导入流程）
    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    TemporaryFileAcquirer().use { acquirer ->
                        val tempZip = acquirer.acquireTempFilePath("imported_module.zip")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            Files.newOutputStream(tempZip).use { output ->
                                StreamUtils.transferTo(input, output)
                            }
                        } ?: error("Cannot open selected module: $uri")
                        ModuleManager.install(tempZip)
                    }
                }
            }.onSuccess { module ->
                Toast.makeText(
                    context,
                    Strings.plugins.importSuccess(module.name.ifBlank { module.id }),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Timber.e(it, "Module import failed: %s", uri)
                Toast.makeText(context, Strings.plugins.importFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PluginsContent(
        modules = modules,
        onInstallClick = { zipPicker.launch("application/zip") },
        onRefreshClick = {
            scope.launch {
                runCatching { ModuleManager.refresh() }
                    .onFailure {
                        Timber.e(it, "Module refresh failed")
                        Toast.makeText(context, Strings.plugins.operationFailed, Toast.LENGTH_SHORT).show()
                    }
            }
        },
        onEnabledChange = { module, enabled ->
            scope.launch {
                runCatching { ModuleManager.setEnabled(module.id, enabled) }
                    .onFailure {
                        Timber.e(it, "Set module enabled failed: %s", module.id)
                        Toast.makeText(context, Strings.plugins.operationFailed, Toast.LENGTH_SHORT).show()
                    }
            }
        },
        onDeleteClick = { pendingDeletion = it },
        modifier = modifier
    )

    pendingDeletion?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(Strings.plugins.deleteTitle) },
            text = {
                Text(
                    Strings.plugins.deleteMessage(module.name.ifBlank { module.id })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletion = null
                    scope.launch {
                        runCatching { ModuleManager.remove(module.id) }
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    Strings.plugins.deleteSuccess,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure {
                                Timber.e(it, "Module remove failed: %s", module.id)
                                Toast.makeText(
                                    context,
                                    Strings.plugins.operationFailed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }) { Text(Strings.plugins.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(Strings.cancel)
                }
            }
        )
    }
}

/**
 * 插件内容：标题栏（刷新 + 安装）+ 模块卡片列表
 */
@Composable
fun PluginsContent(
    modules: List<ModuleItem>,
    onInstallClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onEnabledChange: (ModuleItem, Boolean) -> Unit,
    onDeleteClick: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // 页面背景与项目统一：不透明 Surface(tonalElevation = 2.dp)，与 NavigationRail 及其他页面一致
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Strings.plugins.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = Strings.plugins.refresh
                    )
                }
                Button(onClick = onInstallClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Strings.plugins.add, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (modules.isEmpty()) {
                EmptyPluginsContent(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(modules, key = { it.id }) { module ->
                        ModuleCard(
                            module = module,
                            onEnabledChange = { onEnabledChange(module, it) },
                            onDeleteClick = { onDeleteClick(module) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模块卡片：名称 + id + 元信息（类型/版本/作者）+ 描述 + 删除 + 启用开关
 */
@Composable
private fun ModuleCard(
    module: ModuleItem,
    onEnabledChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        // 高于页面背景一级，对齐 GameCard/Announcement 列表卡片层级
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name.ifBlank { module.id },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Strings.plugins.idLabel(module.id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                module.metaLine().takeIf { it.isNotBlank() }?.let { metaLine ->
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                module.description.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = Strings.plugins.delete,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Switch(
                checked = module.enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

/** 元信息行：类型 / 版本 / 作者，空白字段跳过 */
private fun ModuleItem.metaLine(): String =
    listOfNotNull(
        type.takeIf { it.isNotBlank() },
        version.takeIf { it.isNotBlank() }?.let { "v$it" },
        author.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

/**
 * 空模块列表提示
 */
@Composable
private fun EmptyPluginsContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Strings.plugins.empty,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// roughly the width and height of pixel 10XL
@Preview(
    device = "spec:width=891dp,height=411dp,dpi=420,orientation=landscape"
)
@Composable
fun PreviewPluginsContent() {
    val sampleModules = listOf(
        ModuleItem(
            id = "com.example.patchpack",
            type = "patch",
            name = "Example Patch Pack",
            description = "Bundle of community patches",
            version = "1.2.0",
            author = "Someone",
            meta = buildJsonObject { },
            enabled = true
        ),
        ModuleItem(
            id = "com.example.staticpatch",
            type = "static_patch",
            version = "0.3.1",
            meta = buildJsonObject { },
            enabled = false
        )
    )
    PluginsContent(
        modules = sampleModules,
        onInstallClick = {},
        onRefreshClick = {},
        onEnabledChange = { _, _ -> },
        onDeleteClick = {}
    )
}
