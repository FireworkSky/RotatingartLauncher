package com.app.ralaunch.controls.packs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.ralaunch.R
import com.app.ralaunch.controls.packs.ControlPackItem
import com.app.ralaunch.controls.packs.ControlPackStatus

/**
 * 控件包预览对话框
 * 显示控件包详情、预览图和操作按钮
 * 支持横屏和竖屏两种布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackPreviewDialog(
    pack: ControlPackItem,
    repoUrl: String,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val info = pack.info
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    // 构建预览图 URL 列表
    val previewUrls = remember(info) {
        if (info.previewImagePaths.isEmpty()) {
            // 默认尝试 preview.png
            listOf("$repoUrl/packs/${info.id}/preview.png")
        } else {
            info.previewImagePaths.map { path ->
                "$repoUrl/packs/${info.id}/$path"
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.9f else 0.92f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.75f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            if (isLandscape) {
                // 横屏布局：左边预览图，右边信息
                LandscapeLayout(
                    pack = pack,
                    previewUrls = previewUrls,
                    onDismiss = onDismiss,
                    onDownloadClick = onDownloadClick,
                    onUpdateClick = onUpdateClick,
                    onApplyClick = onApplyClick,
                    onDeleteClick = onDeleteClick
                )
            } else {
                // 竖屏布局：上下排列
                PortraitLayout(
                    pack = pack,
                    previewUrls = previewUrls,
                    onDismiss = onDismiss,
                    onDownloadClick = onDownloadClick,
                    onUpdateClick = onUpdateClick,
                    onApplyClick = onApplyClick,
                    onDeleteClick = onDeleteClick
                )
            }
        }
    }
}

/**
 * 横屏布局
 */
@Composable
private fun LandscapeLayout(
    pack: ControlPackItem,
    previewUrls: List<String>,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val info = pack.info
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧：预览图区域（占 60%）
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (previewUrls.isNotEmpty()) {
                PreviewImagePager(
                    imageUrls = previewUrls,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NoPreviewPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 右侧：信息区域（占 40%）
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 可滚动的信息区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 作者和版本
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = info.author.ifEmpty { stringResource(R.string.pack_unknown_author) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "v${info.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 标签
                if (info.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(info.tags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // 描述
                if (info.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            ActionButtons(
                pack = pack,
                onDismiss = onDismiss,
                onDownloadClick = onDownloadClick,
                onUpdateClick = onUpdateClick,
                onApplyClick = onApplyClick,
                onDeleteClick = onDeleteClick
            )
        }
    }
}

/**
 * 竖屏布局
 */
@Composable
private fun PortraitLayout(
    pack: ControlPackItem,
    previewUrls: List<String>,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val info = pack.info
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }
        
        // 预览图区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (previewUrls.isNotEmpty()) {
                PreviewImagePager(
                    imageUrls = previewUrls,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NoPreviewPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }
        
        // 信息区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 作者和版本
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = info.author.ifEmpty { stringResource(R.string.pack_unknown_author) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "v${info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 标签
            if (info.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(info.tags) { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // 描述
            if (info.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮
            ActionButtons(
                pack = pack,
                onDismiss = onDismiss,
                onDownloadClick = onDownloadClick,
                onUpdateClick = onUpdateClick,
                onApplyClick = onApplyClick,
                onDeleteClick = onDeleteClick
            )
        }
    }
}

/**
 * 操作按钮组件
 */
@Composable
private fun ActionButtons(
    pack: ControlPackItem,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (pack.status) {
            ControlPackStatus.NOT_INSTALLED -> {
                Button(
                    onClick = {
                        onDownloadClick()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pack_download))
                }
            }
            ControlPackStatus.INSTALLED -> {
                Button(
                    onClick = {
                        onApplyClick()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pack_apply))
                }
                
                OutlinedButton(
                    onClick = {
                        onDeleteClick()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ControlPackStatus.UPDATE_AVAILABLE -> {
                Button(
                    onClick = {
                        onUpdateClick()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pack_update))
                }
                
                OutlinedButton(
                    onClick = {
                        onDeleteClick()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ControlPackStatus.DOWNLOADING,
            ControlPackStatus.INSTALLING -> {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (pack.status == ControlPackStatus.DOWNLOADING)
                            stringResource(R.string.pack_downloading)
                        else
                            stringResource(R.string.pack_installing)
                    )
                }
            }
        }
    }
}

/**
 * 预览图翻页器
 */
@Composable
private fun PreviewImagePager(
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrls[page])
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.pack_preview_image, page + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        // 页面指示器（多于1张图片时显示）
        if (imageUrls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(imageUrls.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * 无预览图占位
 */
@Composable
private fun NoPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.pack_no_preview),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
