package com.app.ralaunch.controls.packs.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.app.ralaunch.R
import com.app.ralaunch.controls.packs.ControlPackItem
import com.app.ralaunch.controls.packs.ControlPackStatus
import java.io.File

/**
 * 控件包商店主界面
 * 不使用 Scaffold/TopAppBar 避免测量问题
 */
@Composable
fun ControlPackScreen(
    uiState: ControlPackUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPackClick: (ControlPackItem) -> Unit,
    onDownloadClick: (ControlPackItem) -> Unit,
    onUpdateClick: (ControlPackItem) -> Unit,
    onApplyClick: (ControlPackItem) -> Unit,
    onDeleteClick: (ControlPackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    // 使用 "all" 作为默认选中分类
    var selectedCategoryId by remember { mutableStateOf("all") }

    // 构建完整分类列表（添加"全部"分类）
    val allCategories = remember(uiState.categories) {
        listOf(com.app.ralaunch.controls.packs.PackCategory.ALL) + uiState.categories
    }

    // 根据分类筛选控件包
    val filteredPacks = remember(uiState.packs, selectedCategoryId) {
        if (selectedCategoryId == "all") {
            uiState.packs
        } else {
            uiState.packs.filter { pack ->
                pack.info.category == selectedCategoryId
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 左侧分类栏
        CategorySidebar(
            categories = allCategories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { selectedCategoryId = it },
            modifier = Modifier.width(72.dp)
        )

        // 右侧内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // 自定义顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_desc_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = stringResource(R.string.pack_download_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.main_refresh),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 搜索栏
            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(
                        message = uiState.error,
                        onRetry = onRefresh
                    )
                    filteredPacks.isEmpty() -> EmptyState()
                    else -> PackList(
                        packs = filteredPacks,
                        onPackClick = onPackClick,
                        onDownloadClick = onDownloadClick,
                        onUpdateClick = onUpdateClick,
                        onApplyClick = onApplyClick,
                        onDeleteClick = onDeleteClick
                    )
                }
            }
        }
    }
}

/**
 * 左侧分类栏
 */
@Composable
private fun CategorySidebar(
    categories: List<com.app.ralaunch.controls.packs.PackCategory>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        categories.forEach { category ->
            CategoryItem(
                category = category,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

/**
 * 根据图标名称获取对应的 Material Icon
 */
private fun getIconByName(iconName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconName.lowercase()) {
        "apps" -> Icons.Default.Apps
        "keyboard" -> Icons.Default.Keyboard
        "sports_esports", "gamepad" -> Icons.Default.SportsEsports
        "mouse" -> Icons.Default.Mouse
        "touch_app" -> Icons.Default.TouchApp
        "videogame_asset" -> Icons.Default.VideogameAsset
        else -> Icons.Default.Category
    }
}

/**
 * 分类项
 */
@Composable
private fun CategoryItem(
    category: com.app.ralaunch.controls.packs.PackCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    // 对于 "all" 分类使用多语言字符串资源
    val displayName = if (category.id == "all") {
        stringResource(R.string.pack_category_all)
    } else {
        category.name
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = getIconByName(category.icon),
            contentDescription = displayName,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(52.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.pack_search_hint),
                fontSize = 14.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.control_clear)
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.pack_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.pack_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pack_empty_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.pack_retry))
            }
        }
    }
}

@Composable
private fun PackList(
    packs: List<ControlPackItem>,
    onPackClick: (ControlPackItem) -> Unit,
    onDownloadClick: (ControlPackItem) -> Unit,
    onUpdateClick: (ControlPackItem) -> Unit,
    onApplyClick: (ControlPackItem) -> Unit,
    onDeleteClick: (ControlPackItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 使用 index + id 组合作为唯一 key，避免重复 ID 导致崩溃
        itemsIndexed(packs, key = { index, item -> "${index}_${item.info.id}" }) { _, pack ->
            PackItem(
                item = pack,
                onClick = { onPackClick(pack) },
                onDownloadClick = { onDownloadClick(pack) },
                onUpdateClick = { onUpdateClick(pack) },
                onApplyClick = { onApplyClick(pack) },
                onDeleteClick = { onDeleteClick(pack) }
            )
        }
    }
}

@Composable
private fun PackItem(
    item: ControlPackItem,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val info = item.info

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.localIconPath != null && File(item.localIconPath).exists()) {
                        AsyncImage(
                            model = File(item.localIconPath),
                            contentDescription = info.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 信息区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 第一行：名称 + 状态
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StatusChip(status = item.status)
                }

                // 第二行：作者 • 版本 • 标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "${info.author} • v${info.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (info.tags.isNotEmpty()) {
                        Text(
                            text = " • ${info.tags.take(2).joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 进度条（下载/安装中显示）
                if (item.status == ControlPackStatus.DOWNLOADING || 
                    item.status == ControlPackStatus.INSTALLING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (item.status == ControlPackStatus.INSTALLING) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧操作区
            if (item.status != ControlPackStatus.DOWNLOADING && 
                item.status != ControlPackStatus.INSTALLING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 操作按钮
                    CompactActionButton(
                        status = item.status,
                        onDownloadClick = onDownloadClick,
                        onUpdateClick = onUpdateClick,
                        onApplyClick = onApplyClick
                    )
                    
                    // 删除按钮（已安装时显示）
                    if (item.status == ControlPackStatus.INSTALLED || 
                        item.status == ControlPackStatus.UPDATE_AVAILABLE) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.pack_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ControlPackStatus) {
    val (text, color) = when (status) {
        ControlPackStatus.NOT_INSTALLED -> return // 不显示
        ControlPackStatus.INSTALLED -> stringResource(R.string.pack_installed) to PackStatusColors.installed
        ControlPackStatus.UPDATE_AVAILABLE -> stringResource(R.string.pack_update_available) to PackStatusColors.updateAvailable
        ControlPackStatus.DOWNLOADING -> stringResource(R.string.pack_downloading) to PackStatusColors.downloading
        ControlPackStatus.INSTALLING -> stringResource(R.string.pack_installing) to PackStatusColors.installing
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CompactActionButton(
    status: ControlPackStatus,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    val (icon, color, onClick) = when (status) {
        ControlPackStatus.NOT_INSTALLED -> Triple(
            Icons.Default.Download,
            MaterialTheme.colorScheme.primary,
            onDownloadClick
        )
        ControlPackStatus.INSTALLED -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.tertiary,
            onApplyClick
        )
        ControlPackStatus.UPDATE_AVAILABLE -> Triple(
            Icons.Default.Update,
            MaterialTheme.colorScheme.secondary,
            onUpdateClick
        )
        else -> return
    }

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ActionButton(
    status: ControlPackStatus,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    val (text, icon, onClick) = when (status) {
        ControlPackStatus.NOT_INSTALLED -> Triple(
            stringResource(R.string.pack_download),
            Icons.Default.Download,
            onDownloadClick
        )
        ControlPackStatus.INSTALLED -> Triple(
            stringResource(R.string.pack_apply),
            Icons.Default.Check,
            onApplyClick
        )
        ControlPackStatus.UPDATE_AVAILABLE -> Triple(
            stringResource(R.string.pack_update),
            Icons.Default.Update,
            onUpdateClick
        )
        else -> return
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

/**
 * UI State
 */
data class ControlPackUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val packs: List<ControlPackItem> = emptyList(),
    val categories: List<com.app.ralaunch.controls.packs.PackCategory> = emptyList(),
    val searchQuery: String = ""
)
