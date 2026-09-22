package com.app.ralaunch.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import com.app.ralaunch.feature.installer.GameInstaller
import com.app.ralaunch.feature.installer.GameFile
import com.app.ralaunch.feature.installer.InstallPluginRegistry
import com.app.ralaunch.strings.StringsResource.Strings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/*******************************************************************************
 * RotatingArtLauncher - GameInstallScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/9/19)
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
 * 游戏导入页面 - 纯 Material Design 3，无 ViewModel / Koin
 *
 * 布局沿用旧导入页：左侧引导教程（导入中切换为进度面板）+ 右侧文件选择与导入按钮。
 * 文件选择走 SAF（OpenDocument），检测与安装直接基于 content URI 的文件内容
 * （[GameFile] + [InstallPluginRegistry]），不复制缓存、不依赖文件名。
 * 状态与导入任务挂在 [GameInstallController] 单例上，页面切换/旋转不中断导入。
 */
@Composable
fun GameInstallScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val gamePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { GameInstallController.pick(context, it, ImportSlot.GAME) }
    }
    val modLoaderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { GameInstallController.pick(context, it, ImportSlot.MOD_LOADER) }
    }

    val hasFiles = GameInstallController.gameFile != null ||
        GameInstallController.modLoaderFile != null

    // 页面背景与 GameListScreen 等一致：不透明 Surface，无毛玻璃/模糊
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ===== 左侧：引导 / 导入进度 =====
            if (GameInstallController.isImporting) {
                ImportProgressPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            } else {
                ImportGuidePanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // ===== 右侧：文件选择 + 导入按钮 =====
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                FilePickCard(
                    title = Strings.importScreen.gameFile,
                    subtitle = GameInstallController.gameFile?.displayName
                        ?: Strings.importScreen.gameFileHint,
                    icon = Icons.Outlined.SportsEsports,
                    selected = GameInstallController.gameFile != null,
                    loading = GameInstallController.loadingSlot == ImportSlot.GAME,
                    enabled = !GameInstallController.isImporting,
                    onClick = { gamePicker.launch(ImportSlot.GAME.mimeTypes) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                FilePickCard(
                    title = Strings.importScreen.modLoaderFile,
                    subtitle = GameInstallController.modLoaderFile?.displayName
                        ?: Strings.importScreen.modLoaderHint,
                    icon = Icons.Outlined.Build,
                    selected = GameInstallController.modLoaderFile != null,
                    loading = GameInstallController.loadingSlot == ImportSlot.MOD_LOADER,
                    enabled = !GameInstallController.isImporting,
                    badge = Strings.importScreen.optional,
                    onClick = { modLoaderPicker.launch(ImportSlot.MOD_LOADER.mimeTypes) }
                )

                GameInstallController.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { GameInstallController.dismissError() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = Strings.importScreen.close,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { GameInstallController.startImport(context.applicationContext) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !GameInstallController.isImporting && hasFiles
                ) {
                    if (GameInstallController.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Strings.importScreen.inProgress(GameInstallController.progress),
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.importScreen.start,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧引导面板：标题 + 检测结果 + 四步教程（可滚动）
 */
@Composable
private fun ImportGuidePanel(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = Strings.importScreen.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        val detectedName = GameInstallController.detectedModLoader
            ?: GameInstallController.detectedGame
        val detectedLabel = if (GameInstallController.detectedModLoader != null) {
            Strings.importScreen.detectedModLoader
        } else {
            Strings.importScreen.detectedGame
        }
        if (detectedName != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = detectedLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = detectedName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GuideSection(
                title = Strings.importScreen.guide.step1.title,
                icon = Icons.Outlined.ShoppingCart,
                entries = listOf(
                    GuideEntry.Step(Strings.importScreen.guide.step1.item1),
                    GuideEntry.Step(Strings.importScreen.guide.step1.item2),
                    GuideEntry.Step(Strings.importScreen.guide.step1.item3)
                )
            )
            GuideSection(
                title = Strings.importScreen.guide.step2.title,
                icon = Icons.Outlined.CloudDownload,
                entries = listOf(
                    GuideEntry.Step(Strings.importScreen.guide.step2.item1),
                    GuideEntry.Step(Strings.importScreen.guide.step2.item2),
                    GuideEntry.Step(Strings.importScreen.guide.step2.item3),
                    GuideEntry.Step(Strings.importScreen.guide.step2.item4)
                ),
                imageResId = R.drawable.guide_gog_download
            )
            GuideSection(
                title = Strings.importScreen.guide.step3.title,
                icon = Icons.Outlined.Build,
                entries = listOf(
                    GuideEntry.Heading(Strings.importScreen.guide.step3.tmodloader),
                    GuideEntry.Sub(Strings.importScreen.guide.step3.tmodloaderReleases),
                    GuideEntry.Sub(Strings.importScreen.guide.step3.tmodloaderStable),
                    GuideEntry.Heading(Strings.importScreen.guide.step3.smapi),
                    GuideEntry.Sub(Strings.importScreen.guide.step3.smapiDownload),
                    GuideEntry.Sub(Strings.importScreen.guide.step3.smapiLinux)
                ),
                imageResId = R.drawable.guide_tmodloader_download
            )
            GuideSection(
                title = Strings.importScreen.guide.step4.title,
                icon = Icons.Outlined.InstallMobile,
                entries = listOf(
                    GuideEntry.Step(Strings.importScreen.guide.step4.item1),
                    GuideEntry.Step(Strings.importScreen.guide.step4.item2),
                    GuideEntry.Step(Strings.importScreen.guide.step4.item3),
                    GuideEntry.Step(Strings.importScreen.guide.step4.item4),
                    GuideEntry.Step(Strings.importScreen.guide.step4.item5)
                )
            )
        }
    }
}

/**
 * 引导条目：编号步骤 / 分组标题 / 缩进子项
 */
private sealed interface GuideEntry {
    data class Step(val text: String) : GuideEntry
    data class Heading(val text: String) : GuideEntry
    data class Sub(val text: String) : GuideEntry
}

/**
 * 引导步骤区块（支持可选参考截图）
 */
@Composable
private fun GuideSection(
    title: String,
    icon: ImageVector,
    entries: List<GuideEntry>,
    modifier: Modifier = Modifier,
    imageResId: Int? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            var stepNumber = 1
            entries.forEach { entry ->
                when (entry) {
                    is GuideEntry.Heading -> Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    is GuideEntry.Sub -> Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 2.dp)
                    )

                    is GuideEntry.Step -> {
                        Row(modifier = Modifier.padding(bottom = 2.dp)) {
                            Text(
                                text = "$stepNumber.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        stepNumber++
                    }
                }
            }

            if (imageResId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = Strings.importScreen.referenceScreenshot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

/**
 * 文件选择卡片：MD3 Card，选中态用 secondaryContainer 区分（与 GameCard 一致）
 */
@Composable
private fun FilePickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Card(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        badge?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 导入进度面板：圆形进度 + 状态日志
 */
@Composable
private fun ImportProgressPanel(modifier: Modifier = Modifier) {
    val progress = GameInstallController.progress.coerceIn(0, 100)
    val statusText = GameInstallController.status.ifBlank { Strings.importScreen.preparing }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = Strings.importScreen.progressTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 8.dp
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 导入槽位：SAF 过滤的 MIME 类型（文件有效性由内容检测判断）
 */
private enum class ImportSlot(val mimeTypes: Array<String>) {
    GAME(
        mimeTypes = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-sh",
            "text/x-sh",
            "application/octet-stream"
        )
    ),
    MOD_LOADER(
        mimeTypes = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
        )
    )
}

/**
 * 已选择的待导入文件：SAF Uri 的显示名与对应的 [GameFile]
 */
private data class PickedFile(val displayName: String, val gameFile: GameFile)

/**
 * 游戏导入控制器 - 无 ViewModel 的状态与任务持有者
 *
 * 状态用 Compose mutableStateOf 暴露给页面；检测/安装协程挂在自持
 * SupervisorJob Scope 上，页面导航或旋转不会中断正在进行的导入
 * （与 GameManager 的进程级单例状态模式一致）。
 */
private object GameInstallController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var installer: GameInstaller? = null

    var gameFile by mutableStateOf<PickedFile?>(null)
    var modLoaderFile by mutableStateOf<PickedFile?>(null)
    var detectedGame by mutableStateOf<String?>(null)
    var detectedModLoader by mutableStateOf<String?>(null)
    var loadingSlot by mutableStateOf<ImportSlot?>(null)
    var isImporting by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var status by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)

    /** SAF 选中文件后：读取显示名 -> 基于文件内容的插件检测（不复制、不依赖文件名） */
    fun pick(context: Context, uri: Uri, slot: ImportSlot) {
        if (isImporting || loadingSlot != null) return
        val appContext = context.applicationContext
        scope.launch {
            loadingSlot = slot
            errorMessage = null
            try {
                val displayName = withContext(Dispatchers.IO) {
                    queryDisplayName(appContext, uri)
                        ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: "selected_file"
                }
                val picked = PickedFile(File(displayName).name, GameFile.of(appContext, uri))

                val detected = withContext(Dispatchers.IO) {
                    when (slot) {
                        ImportSlot.GAME -> InstallPluginRegistry.detectGame(picked.gameFile)
                            ?.second?.definition?.displayName
                        ImportSlot.MOD_LOADER -> InstallPluginRegistry.detectModLoader(picked.gameFile)
                            ?.second?.definition?.displayName
                    }
                }

                // 内容无法识别为任何支持的游戏/模组加载器
                if (detected == null) {
                    errorMessage = if (slot == ImportSlot.GAME) {
                        Strings.importScreen.unsupportedGameFile
                    } else {
                        Strings.importScreen.unsupportedModLoaderFile
                    }
                    return@launch
                }

                if (slot == ImportSlot.GAME) {
                    gameFile = picked
                    detectedGame = detected
                } else {
                    modLoaderFile = picked
                    detectedModLoader = detected
                }
            } catch (e: Exception) {
                Timber.e(e, "读取 SAF 文件失败: $uri")
                errorMessage = Strings.importScreen.readFailed
            } finally {
                loadingSlot = null
                status = ""
            }
        }
    }

    /** 开始导入；进度驱动 UI，终态由返回的 Result 统一处理 */
    fun startImport(appContext: Context) {
        if (isImporting) return

        val game = gameFile
        val modLoader = modLoaderFile
        if (game == null && modLoader == null) {
            errorMessage = Strings.importScreen.selectGameFirst
            return
        }

        isImporting = true
        progress = 0
        status = Strings.importScreen.preparing
        errorMessage = null

        val newInstaller = GameInstaller()
        installer = newInstaller

        scope.launch {
            val result = newInstaller.install(
                gameFile = game?.gameFile,
                modLoaderFile = modLoader?.gameFile,
                callback = { event ->
                    if (event is Event.Progress) {
                        status = event.message
                        progress = event.progress.coerceIn(0, 100)
                    }
                }
            )
            when (result) {
                is Result.Success -> {
                    Toast.makeText(
                        appContext,
                        Strings.importScreen.addedSuccess,
                        Toast.LENGTH_SHORT
                    ).show()
                    reset()
                }

                is Result.Failure -> {
                    isImporting = false
                    errorMessage = result.message
                    Toast.makeText(
                        appContext,
                        Strings.importScreen.failed(result.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is Result.Cancelled -> {
                    isImporting = false
                    errorMessage = result.message
                }
            }
        }
    }

    fun dismissError() {
        errorMessage = null
    }

    /** 清空选择 */
    private fun reset() {
        gameFile = null
        modLoaderFile = null
        detectedGame = null
        detectedModLoader = null
        isImporting = false
        progress = 0
        status = ""
        errorMessage = null
        installer = null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
