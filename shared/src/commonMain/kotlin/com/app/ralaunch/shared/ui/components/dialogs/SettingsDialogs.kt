package com.app.ralaunch.shared.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 语言选择数据
 */
data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String
)

/**
 * 语言选择对话框
 */
@Composable
fun LanguageSelectDialog(
    currentLanguage: String,
    languages: List<LanguageOption> = defaultLanguages(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择语言", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(languages) { lang ->
                    val isSelected = lang.code == currentLanguage
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(lang.code)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = lang.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun defaultLanguages() = listOf(
    LanguageOption("zh-CN", "Chinese (Simplified)", "简体中文"),
    LanguageOption("zh-TW", "Chinese (Traditional)", "繁體中文"),
    LanguageOption("en", "English", "English"),
    LanguageOption("ja", "Japanese", "日本語"),
    LanguageOption("ko", "Korean", "한국어"),
    LanguageOption("ru", "Russian", "Русский"),
    LanguageOption("es", "Spanish", "Español"),
    LanguageOption("fr", "French", "Français"),
    LanguageOption("de", "German", "Deutsch"),
    LanguageOption("pt", "Portuguese", "Português")
)

// ==================== 颜色选择器 ====================
// 颜色选择器已移至单独文件: ColorPickerDialog.kt
// 为保持兼容性，提供别名
@Composable
fun ThemeColorSelectDialog(
    currentColor: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) = ColorPickerDialog(currentColor, onSelect, onDismiss)

/**
 * 渲染器选项
 */
data class RendererOption(
    val id: String,
    val name: String,
    val description: String
)

/**
 * 渲染器选择对话框（横屏双列布局）
 */
@Composable
fun RendererSelectDialog(
    currentRenderer: String,
    renderers: List<RendererOption> = defaultRenderers(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Text("选择渲染器", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(renderers.size) { index ->
                    val renderer = renderers[index]
                    val isSelected = renderer.id == currentRenderer
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(renderer.id)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelect(renderer.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = renderer.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = renderer.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun defaultRenderers() = listOf(
    RendererOption("auto", "自动选择", "根据设备自动选择最佳渲染器"),
    RendererOption("native", "Native OpenGL ES 3", "最快，有GPU加速，但可能有渲染错误"),
    RendererOption("gl4es", "GL4ES", "最完美，游戏兼容性最强，但帧率稍慢"),
    RendererOption("gl4es+angle", "GL4ES + ANGLE", "翻译成Vulkan，速度和兼容性最佳，推荐高通骁龙使用"),
    RendererOption("mobileglues", "MobileGlues 1.3.3", "OpenGL 4.6 翻译至 OpenGL ES 3.2（现代化翻译层）"),
    RendererOption("angle", "ANGLE (Vulkan Backend)", "OpenGL ES over Vulkan (Google官方，需要 Android 7.0+)")
)

/**
 * 日志查看对话框
 */
@Composable
fun LogViewerDialog(
    logs: List<String>,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("日志查看", fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = onExport) {
                        Text("导出")
                    }
                    TextButton(onClick = onClear) {
                        Text("清除")
                    }
                }
            }
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无日志",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 开源许可对话框
 */
@Composable
fun LicenseDialog(
    licenses: List<LicenseInfo> = defaultLicenses(),
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("开源许可", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(licenses) { license ->
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
                Text("关闭")
            }
        }
    )
}

data class LicenseInfo(
    val name: String,
    val license: String,
    val description: String = ""
)

fun defaultLicenses() = listOf(
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

/**
 * 补丁信息
 */
data class PatchInfo(
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean = true
)

/**
 * 补丁管理对话框 - 简化版
 * 
 * 显示已安装的补丁列表，支持导入和删除补丁
 */
@Composable
fun PatchManagementDialog(
    patches: List<PatchInfo> = emptyList(),
    onImportPatch: () -> Unit = {},
    onDeletePatch: (PatchInfo) -> Unit = {},
    onTogglePatch: (PatchInfo, Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("补丁管理", fontWeight = FontWeight.Bold)
                TextButton(onClick = onImportPatch) {
                    Text("导入")
                }
            }
        },
        text = {
            if (patches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "暂无已安装的补丁",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击「导入」添加补丁文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(patches) { patch ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = patch.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "v${patch.version}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (patch.description.isNotEmpty()) {
                                        Text(
                                            text = patch.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = patch.isEnabled,
                                    onCheckedChange = { onTogglePatch(patch, it) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}