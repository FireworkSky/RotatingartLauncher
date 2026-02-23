package com.app.ralaunch.feature.gog.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderRule
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderVersion
import com.app.ralaunch.feature.gog.data.GogDownloader
import com.app.ralaunch.feature.gog.data.model.GogGameFile

/**
 * 下载状态
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    object SelectingVersion : DownloadStatus()
    data class Downloading(
        val fileName: String,
        val progress: Float,
        val downloaded: Long,
        val total: Long,
        val speed: Long
    ) : DownloadStatus()
    data class Completed(val gamePath: String?, val modLoaderPath: String?) : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

/**
 * GOG 下载对话框 - 横屏双栏布局
 */
@Composable
fun GogDownloadDialog(
    gameName: String,
    gameFiles: List<GogGameFile>,
    modLoaderRule: ModLoaderRule?,
    downloadStatus: DownloadStatus,
    onSelectGameVersion: (GogGameFile) -> Unit,
    onSelectModLoaderVersion: (ModLoaderVersion) -> Unit,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGameFile by remember { mutableStateOf<GogGameFile?>(null) }
    var selectedModLoaderVersion by remember { mutableStateOf<ModLoaderVersion?>(null) }
    var showModLoaderVersions by remember { mutableStateOf(false) }

    LaunchedEffect(modLoaderRule) {
        if (modLoaderRule != null && selectedModLoaderVersion == null) {
            selectedModLoaderVersion = modLoaderRule.versions.firstOrNull { it.stable }
                ?: modLoaderRule.versions.firstOrNull()
        }
    }

    Dialog(
        onDismissRequest = {
            if (downloadStatus !is DownloadStatus.Downloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadStatus !is DownloadStatus.Downloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                DownloadDialogHeader(
                    gameName = gameName,
                    modLoaderName = modLoaderRule?.name,
                    onClose = {
                        if (downloadStatus !is DownloadStatus.Downloading) onDismiss()
                    }
                )

                // 内容区域
                when (downloadStatus) {
                    is DownloadStatus.Idle,
                    is DownloadStatus.SelectingVersion -> {
                        // 横屏双栏布局
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 左侧：游戏版本选择
                            GameVersionPanel(
                                gameFiles = gameFiles,
                                selectedGameFile = selectedGameFile,
                                onSelectGameFile = {
                                    selectedGameFile = it
                                    onSelectGameVersion(it)
                                },
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight()
                            )

                            // 右侧：ModLoader + 下载按钮
                            RightPanel(
                                modLoaderRule = modLoaderRule,
                                selectedModLoaderVersion = selectedModLoaderVersion,
                                showModLoaderVersions = showModLoaderVersions,
                                onToggleModLoaderVersions = { showModLoaderVersions = !showModLoaderVersions },
                                onSelectModLoaderVersion = {
                                    selectedModLoaderVersion = it
                                    onSelectModLoaderVersion(it)
                                    showModLoaderVersions = false
                                },
                                downloadEnabled = selectedGameFile != null,
                                onStartDownload = onStartDownload,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    is DownloadStatus.Downloading -> {
                        DownloadProgressContent(
                            status = downloadStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is DownloadStatus.Completed -> {
                        DownloadCompletedContent(
                            gamePath = downloadStatus.gamePath,
                            modLoaderPath = downloadStatus.modLoaderPath,
                            onInstall = onInstall,
                            onClose = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is DownloadStatus.Failed -> {
                        DownloadFailedContent(
                            error = downloadStatus.error,
                            onRetry = onStartDownload,
                            onClose = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadDialogHeader(
    gameName: String,
    modLoaderName: String?,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gog_download),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = gameName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (modLoaderName != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = modLoaderName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun GameVersionPanel(
    gameFiles: List<GogGameFile>,
    selectedGameFile: GogGameFile?,
    onSelectGameFile: (GogGameFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.gog_select_game_version),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(gameFiles) { file ->
                    GameFileItem(
                        gameFile = file,
                        isSelected = selectedGameFile == file,
                        onClick = { onSelectGameFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RightPanel(
    modLoaderRule: ModLoaderRule?,
    selectedModLoaderVersion: ModLoaderVersion?,
    showModLoaderVersions: Boolean,
    onToggleModLoaderVersions: () -> Unit,
    onSelectModLoaderVersion: (ModLoaderVersion) -> Unit,
    downloadEnabled: Boolean,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ModLoader 版本选择
        if (modLoaderRule != null) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModLoaderVersionSelector(
                        modLoaderRule = modLoaderRule,
                        selectedVersion = selectedModLoaderVersion,
                        expanded = showModLoaderVersions,
                        onToggle = onToggleModLoaderVersions,
                        onSelectVersion = onSelectModLoaderVersion
                    )
                }
            }
        } else {
            // 无 ModLoader 时的占位
            Spacer(modifier = Modifier.weight(1f))
        }

        // 下载按钮
        Button(
            onClick = onStartDownload,
            enabled = downloadEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gog_download),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.gog_start_download),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GameFileItem(
    gameFile: GogGameFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color.White.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gameFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gameFile.version, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    Text("•", color = Color.White.copy(alpha = 0.4f))
                    Text(gameFile.getSizeFormatted(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ModLoaderVersionSelector(
    modLoaderRule: ModLoaderRule,
    selectedVersion: ModLoaderVersion?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectVersion: (ModLoaderVersion) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.gog_select_modloader_version, modLoaderRule.name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggle() },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVersion?.version ?: stringResource(R.string.gog_select_version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (selectedVersion?.stable == true) {
                        Text(stringResource(R.string.runtime_version_stable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(modLoaderRule.versions) { version ->
                    ModLoaderVersionItem(
                        version = version,
                        isSelected = selectedVersion == version,
                        onClick = { onSelectVersion(version) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModLoaderVersionItem(
    version: ModLoaderVersion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(version.version, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (version.stable) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)) {
                    Text(stringResource(R.string.runtime_version_stable), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressContent(
    status: DownloadStatus.Downloading,
    modifier: Modifier = Modifier
) {
    // 横屏进度显示
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：进度环
        Box(
            modifier = Modifier.weight(0.4f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.size(140.dp),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "${(status.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 右侧：详情
        Column(
            modifier = Modifier.weight(0.6f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.gog_download_status_downloading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = status.fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${GogDownloader.formatSize(status.downloaded)} / ${GogDownloader.formatSize(status.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (status.speed > 0) {
                    Text(
                        text = GogDownloader.formatSpeed(status.speed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCompletedContent(
    gamePath: String?,
    modLoaderPath: String?,
    onInstall: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：图标
        Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        // 右侧：信息和按钮
        Column(
            modifier = Modifier.weight(0.6f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.gog_download_complete),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (gamePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gog_game_downloaded), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
                }
            }
            if (modLoaderPath != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gog_modloader_downloaded), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.InstallMobile, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.gog_install_now), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                ) {
                    Text(stringResource(R.string.gog_install_later))
                }
            }
        }
    }
}

@Composable
private fun DownloadFailedContent(
    error: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Error, null, Modifier.size(120.dp), tint = MaterialTheme.colorScheme.error)
        }

        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.gog_download_failed_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
