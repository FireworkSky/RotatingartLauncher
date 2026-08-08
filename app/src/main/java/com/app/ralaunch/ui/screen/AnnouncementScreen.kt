package com.app.ralaunch.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.models.AnnouncementItem
import com.app.ralaunch.models.AnnouncementUiState
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.utils.AnnouncementUtils
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import timber.log.Timber

/*******************************************************************************
 * RotatingArtLauncher - AnnouncementScreen
 *
 * This file is part of the RotatingArtLauncher project.
 *
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 *
 * Created by: eternalfuture-e38299 (2026/7/5)
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

@Composable
fun AnnouncementScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf(AnnouncementUiState(isLoading = true)) }

    // 添加调试日志
    LaunchedEffect(uiState.announcements) {
        Timber.d("UI State updated: ${uiState.announcements.size} announcements, selected: ${uiState.selectedId}, isLoading: ${uiState.isLoading}")
        if (uiState.announcements.isNotEmpty()) {
            uiState.announcements.forEach { item ->
                Timber.d("Item: ${item.id} - ${item.title}")
            }
        }
    }

    fun loadContent(announcementId: String) {
        if (announcementId.isBlank()) return

        scope.launch {
            Timber.d("Loading content for: $announcementId")
            val result = AnnouncementUtils.fetchAnnouncementContent(announcementId)
            result.fold(
                onSuccess = { content ->
                    Timber.d("Content loaded for: $announcementId, length: ${content.length}")
                    val updatedAnnouncements = uiState.announcements.map { item ->
                        if (item.id == announcementId) item.copy(content = content) else item
                    }
                    uiState = uiState.copy(
                        announcements = updatedAnnouncements,
                        selectedId = announcementId,
                        isLoading = false,
                        error = null
                    )
                },
                onFailure = { error ->
                    Timber.e("Failed to load content: ${error.message}")
                    uiState = uiState.copy(
                        isLoading = false,
                        error = error.message ?: Strings.announcement.loadFailed
                    )
                }
            )
        }
    }

    fun loadAnnouncements() {
        scope.launch {
            Timber.d("Loading announcements...")
            uiState = uiState.copy(isLoading = true, error = null)
            val result = AnnouncementUtils.fetchAnnouncements(context)
            result.fold(
                onSuccess = { announcements ->
                    Timber.d("Loaded ${announcements.size} announcements")
                    val selectedId = announcements.firstOrNull()?.id
                    uiState = uiState.copy(
                        announcements = announcements,
                        selectedId = selectedId,
                        isLoading = false,
                        error = null
                    )
                    selectedId?.let {
                        loadContent(it)
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to load announcements: ${error.message}")
                    uiState = uiState.copy(
                        isLoading = false,
                        error = error.message ?: Strings.announcement.loadFailed
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        loadAnnouncements()
    }

    AnnouncementScreenContent(
        uiState = uiState,
        onRetry = { loadAnnouncements() },
        onSelect = { id ->
            Timber.d("Selected announcement: $id")
            uiState = uiState.copy(selectedId = id)
            loadContent(id)
        }
    )
}

@Composable
fun AnnouncementScreenContent(
    uiState: AnnouncementUiState,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧公告列表 - 占据 40% 宽度
            AnnouncementList(
                uiState = uiState,
                onRetry = onRetry,
                onSelect = onSelect,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxSize()
            )

            // 右侧详情区域 - 占据 60% 宽度
            AnnouncementDetail(
                uiState = uiState,
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun AnnouncementList(
    uiState: AnnouncementUiState,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题头
            ListHeader(uiState)

            // 内容区域
            when {
                uiState.isLoading && uiState.announcements.isEmpty() -> {
                    LoadingState(modifier = Modifier.weight(1f))
                }

                uiState.error != null && uiState.announcements.isEmpty() -> {
                    ErrorState(
                        message = uiState.error,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f)
                    )
                }

                uiState.announcements.isEmpty() -> {
                    EmptyState(modifier = Modifier.weight(1f))
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.announcements,
                            key = { it.id }
                        ) { announcement ->
                            AnnouncementListItem(
                                announcement = announcement,
                                isSelected = uiState.selectedId == announcement.id,
                                onClick = { onSelect(announcement.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(uiState: AnnouncementUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.nav.announcements,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (uiState.announcements.isNotEmpty()) {
                Text(
                    text = uiState.announcements.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun AnnouncementListItem(
    announcement: AnnouncementItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = announcement.publishedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (announcement.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = announcement.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AnnouncementDetail(
    uiState: AnnouncementUiState,
    modifier: Modifier = Modifier
) {
    // 获取选中的公告，如果没有选中则取第一个
    val selected = if (uiState.selectedId != null) {
        uiState.announcements.find { it.id == uiState.selectedId }
    } else {
        uiState.announcements.firstOrNull()
    } ?: uiState.announcements.firstOrNull()

    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        when {
            // 正在加载且没有公告
            uiState.isLoading && uiState.announcements.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 有选中的公告
            selected != null -> {
                AnnouncementDetailContent(
                    announcement = selected,
                    isLoading = uiState.isLoading && selected.content.isEmpty()
                )
            }
            // 没有公告
            else -> {
                EmptyDetailState(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@SuppressLint("UseKtx")
@Composable
private fun AnnouncementDetailContent(
    announcement: AnnouncementItem,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn {
        item {
            // 使用 key 强制在公告变化时完全重组，解决滑动后 Markdown 渲染异常问题
            key(announcement.id) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (announcement.content.isEmpty()) {
                    Text(
                        text = Strings.announcement.noContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    MarkdownText(
                        markdown = announcement.content,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onSurface
                        ),
                        linkColor = colorScheme.primary,
                        syntaxHighlightTextColor = colorScheme.onSurface,
                        syntaxHighlightColor = colorScheme.surfaceVariant,
                        headingBreakColor = colorScheme.outlineVariant,
                        enableUnderlineForLink = true,
                        onLinkClicked = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // 处理无效链接
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message ?: Strings.announcement.loadFailed,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text(Strings.retry)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = Strings.announcement.empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyDetailState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = Strings.announcement.noContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}