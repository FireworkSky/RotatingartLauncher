package com.app.ralaunch.feature.main.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.main.contracts.ImportUiState
import java.io.File

/**
 * 游戏导入 Screen - 现代化双栏布局
 * 
 * 状态由外部管理，避免导航时丢失
 */
@Composable
fun ImportScreenWrapper(
    gameFilePath: String? = null,
    detectedGameId: String? = null,
    modLoaderFilePath: String? = null,
    detectedModLoaderId: String? = null,
    importUiState: ImportUiState = ImportUiState(),
    onBack: () -> Unit = {},
    onStartImport: () -> Unit = {},
    onSelectGameFile: () -> Unit = {},
    onSelectModLoader: () -> Unit = {},
    onDismissError: () -> Unit = {}
) {
    // UI
    ModernImportScreen(
        gameFilePath = gameFilePath,
        detectedGameId = detectedGameId,
        modLoaderFilePath = modLoaderFilePath,
        detectedModLoaderId = detectedModLoaderId,
        isImporting = importUiState.isImporting,
        importProgress = importUiState.progress,
        importStatus = importUiState.status,
        errorMessage = importUiState.errorMessage,
        onSelectGameFile = onSelectGameFile,
        onSelectModLoader = onSelectModLoader,
        onStartImport = onStartImport,
        onDismissError = onDismissError
    )
}

/**
 * 现代化导入界面
 */
@Composable
private fun ModernImportScreen(
    gameFilePath: String?,
    detectedGameId: String?,
    modLoaderFilePath: String?,
    detectedModLoaderId: String?,
    isImporting: Boolean,
    importProgress: Int,
    importStatus: String,
    errorMessage: String?,
    onSelectGameFile: () -> Unit,
    onSelectModLoader: () -> Unit,
    onStartImport: () -> Unit,
    onDismissError: () -> Unit
) {
    val hasFiles = !gameFilePath.isNullOrEmpty() || !modLoaderFilePath.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 主内容 - 双栏布局
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ===== 左侧面板 - 引导信息 =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp)
            ) {
                AnimatedContent(
                    targetState = isImporting,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.04f))
                    },
                    label = "import_left_panel_switch"
                ) { importing ->
                    if (importing) {
                        ImportProgressPanel(
                            importProgress = importProgress,
                            importStatus = importStatus,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 上部：标题
                            Text(
                                text = stringResource(R.string.import_new_game_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 检测结果（选择文件后显示）
                            val displayName = detectedModLoaderId ?: detectedGameId
                            val displayLabel = if (detectedModLoaderId != null) {
                                stringResource(R.string.import_detected_modloader_label)
                            } else {
                                stringResource(R.string.import_detected_game_label)
                            }

                            AnimatedVisibility(
                                visible = displayName != null,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = displayLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = displayName ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            // 中部：引导教程（可滚动）
                            val guideScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(guideScrollState),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Step 1: 购买游戏
                                ImportGuideSection(
                                    title = stringResource(R.string.import_guide_step1_title),
                                    icon = Icons.Outlined.ShoppingCart,
                                    steps = listOf(
                                        stringResource(R.string.import_guide_step1_item1),
                                        stringResource(R.string.import_guide_step1_item2),
                                        stringResource(R.string.import_guide_step1_item3)
                                    )
                                )

                                // Step 2: 下载游戏
                                ImportGuideSection(
                                    title = stringResource(R.string.import_guide_step2_title),
                                    icon = Icons.Outlined.CloudDownload,
                                    steps = listOf(
                                        stringResource(R.string.import_guide_step2_item1),
                                        stringResource(R.string.import_guide_step2_item2),
                                        stringResource(R.string.import_guide_step2_item3),
                                        stringResource(R.string.import_guide_step2_item4)
                                    ),
                                    imageResId = R.drawable.guide_gog_download
                                )

                                // Step 3: 模组加载器（可选）
                                ImportGuideSection(
                                    title = stringResource(R.string.import_guide_step3_title),
                                    icon = Icons.Outlined.Build,
                                    steps = listOf(
                                        stringResource(R.string.import_guide_step3_item1),
                                        stringResource(R.string.import_guide_step3_item2),
                                        stringResource(R.string.import_guide_step3_item3),
                                        "",
                                        stringResource(R.string.import_guide_step3_item4),
                                        stringResource(R.string.import_guide_step3_item5),
                                        stringResource(R.string.import_guide_step3_item6)
                                    ),
                                    imageResId = R.drawable.guide_tmodloader_download
                                )

                                // Step 4: 导入到启动器
                                ImportGuideSection(
                                    title = stringResource(R.string.import_guide_step4_title),
                                    icon = Icons.Outlined.InstallMobile,
                                    steps = listOf(
                                        stringResource(R.string.import_guide_step4_item1),
                                        stringResource(R.string.import_guide_step4_item2),
                                        stringResource(R.string.import_guide_step4_item3),
                                        stringResource(R.string.import_guide_step4_item4),
                                        stringResource(R.string.import_guide_step4_item5)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ===== 右侧面板 - 操作区域 =====
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // 游戏文件卡片
                ModernFileCard(
                    title = stringResource(R.string.import_game_file),
                    subtitle = if (gameFilePath != null) File(gameFilePath).name else stringResource(R.string.import_game_file_subtitle_hint),
                    icon = Icons.Outlined.SportsEsports,
                    isSelected = gameFilePath != null,
                    isPrimary = true,
                    onClick = onSelectGameFile,
                    enabled = !isImporting
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 模组加载器卡片
                ModernFileCard(
                    title = stringResource(R.string.import_modloader_file),
                    subtitle = if (modLoaderFilePath != null) File(modLoaderFilePath).name else stringResource(R.string.import_modloader_subtitle_hint),
                    icon = Icons.Outlined.Build,
                    isSelected = modLoaderFilePath != null,
                    isPrimary = false,
                    badge = stringResource(R.string.import_optional_badge),
                    onClick = onSelectModLoader,
                    enabled = !isImporting
                )

                // 错误显示
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissError) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 导入按钮
                Button(
                    onClick = onStartImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isImporting && hasFiles,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.import_in_progress_percent, importProgress),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.import_start),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportProgressPanel(
    importProgress: Int,
    importStatus: String,
    modifier: Modifier = Modifier
) {
    val progress = importProgress.coerceIn(0, 100)
    val statusText = if (importStatus.isBlank()) stringResource(R.string.import_preparing_import) else importStatus
    val statusScrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.import_progress_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
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
                            modifier = Modifier.size(136.dp),
                            strokeWidth = 10.dp
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(statusScrollState)
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
 * 功能特性项
 */
@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 现代化文件选择卡片
 */
@Composable
private fun ModernFileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    badge: String? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = primaryColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            primaryColor.copy(alpha = 0.15f)
                        } else if (isPrimary) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) {
                        primaryColor
                    } else if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 文本
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
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 箭头图标
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 导入引导步骤区块（支持可选参考图片）
 */
@Composable
private fun ImportGuideSection(
    title: String,
    icon: ImageVector,
    steps: List<String>,
    @DrawableRes imageResId: Int? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

            // 步骤列表（跳过空字符串，用于分组间隔）
            var stepNumber = 1
            steps.forEach { step ->
                if (step.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (step.startsWith("  ")) {
                    // 缩进子项（无编号）
                    Text(
                        text = step.trimStart(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 22.dp, bottom = 2.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${stepNumber}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    stepNumber++
                }
            }

            // 可选参考截图
            if (imageResId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = stringResource(R.string.import_reference_screenshot),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
