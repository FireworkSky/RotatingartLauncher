package com.app.ralaunch.feature.main.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.R
import com.app.ralaunch.feature.announcement.AnnouncementItem
import com.app.ralaunch.feature.announcement.AnnouncementUiEvent
import com.app.ralaunch.feature.announcement.AnnouncementUiState
import com.app.ralaunch.feature.announcement.AnnouncementViewModel
import com.app.ralaunch.feature.announcement.AnnouncementViewModelFactory
import com.app.ralaunch.shared.core.component.GlassSurface
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreenWrapper() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return

    val viewModel: AnnouncementViewModel = remember(activity) {
        ViewModelProvider(
            activity,
            AnnouncementViewModelFactory(activity.applicationContext)
        )[AnnouncementViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.selectedAnnouncementId) {
        val selectedId = uiState.selectedAnnouncementId ?: return@LaunchedEffect
        viewModel.onEvent(AnnouncementUiEvent.EnsureMarkdown(selectedId))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            AnnouncementListPane(
                uiState = uiState,
                onRefresh = { viewModel.onEvent(AnnouncementUiEvent.Refresh) },
                onRetry = { viewModel.onEvent(AnnouncementUiEvent.Retry) },
                onSelect = { announcementId ->
                    viewModel.onEvent(AnnouncementUiEvent.SelectAnnouncement(announcementId))
                },
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
            )

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AnnouncementDetailPane(
                uiState = uiState,
                onRetryMarkdown = { announcementId ->
                    viewModel.onEvent(
                        AnnouncementUiEvent.EnsureMarkdown(
                            announcementId = announcementId,
                            forceRefresh = true
                        )
                    )
                },
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            )
        }

        if (uiState.isInitialLoading || uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnouncementListPane(
    uiState: AnnouncementUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        showBorder = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.main_announcements),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.announcements.isNotEmpty()) {
                    Text(
                        text = uiState.announcements.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            PullToRefreshBox(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh
            ) {
                when {
                    uiState.isInitialLoading && uiState.announcements.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    !uiState.loadErrorMessage.isNullOrBlank() && uiState.announcements.isEmpty() -> {
                        AnnouncementMessageState(
                            message = uiState.loadErrorMessage,
                            actionText = stringResource(R.string.retry),
                            onAction = onRetry
                        )
                    }

                    uiState.announcements.isEmpty() -> {
                        AnnouncementMessageState(
                            message = stringResource(R.string.announcement_empty)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = uiState.announcements,
                                key = { it.id }
                            ) { announcement ->
                                AnnouncementListItem(
                                    announcement = announcement,
                                    isSelected = uiState.selectedAnnouncement?.id == announcement.id,
                                    onClick = { onSelect(announcement.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementListItem(
    announcement: AnnouncementItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(14.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = announcement.publishedAt,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (announcement.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = announcement.tags.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AnnouncementDetailPane(
    uiState: AnnouncementUiState,
    onRetryMarkdown: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        showBorder = false
    ) {
        val selectedAnnouncement = uiState.selectedAnnouncement
        if (selectedAnnouncement == null) {
            AnnouncementMessageState(
                modifier = Modifier.fillMaxSize(),
                message = if (uiState.announcements.isEmpty()) {
                    stringResource(R.string.announcement_empty)
                } else {
                    stringResource(R.string.announcement_no_content)
                }
            )
        } else {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = selectedAnnouncement.id,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220))
                        .togetherWith(fadeOut(animationSpec = tween(160)))
                },
                label = "announcementDetailTransition"
            ) { selectedId ->
                val announcement = uiState.announcements.firstOrNull { it.id == selectedId }
                    ?: selectedAnnouncement
                val markdown = uiState.markdownById[announcement.id] ?: announcement.markdown
                val markdownError = uiState.markdownErrors[announcement.id]
                val isMarkdownLoading = announcement.id in uiState.loadingMarkdownIds
                val detailScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailScrollState)
                        .padding(20.dp)
                ) {
                        Text(
                            text = announcement.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = announcement.publishedAt,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (announcement.tags.isNotEmpty()) {
                                Text(
                                    text = announcement.tags.joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        when {
                            !markdown.isNullOrBlank() -> {
                                MarkdownText(
                                    markdown = markdown,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            isMarkdownLoading -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            !markdownError.isNullOrBlank() -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = markdownError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { onRetryMarkdown(announcement.id) }) {
                                        Text(text = stringResource(R.string.retry))
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.announcement_no_content),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementMessageState(
    message: String?,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!actionText.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}
