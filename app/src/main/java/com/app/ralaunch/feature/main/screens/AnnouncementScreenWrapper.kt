package com.app.ralaunch.feature.main.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.feature.announcement.AnnouncementItem
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreenWrapper() {
    val scope = rememberCoroutineScope()
    val service: AnnouncementRepositoryService = remember {
        KoinJavaComponent.get(AnnouncementRepositoryService::class.java)
    }

    var uiState by remember {
        mutableStateOf<AnnouncementUiState>(AnnouncementUiState.Loading)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var markdownReloadToken by remember { mutableIntStateOf(0) }
    val markdownById = remember { mutableStateMapOf<String, String>() }
    val markdownErrors = remember { mutableStateMapOf<String, String>() }
    val loadingMarkdownIds = remember { mutableStateMapOf<String, Boolean>() }
    val pullToRefreshState = rememberPullToRefreshState()

    fun clearMarkdownState() {
        markdownById.clear()
        markdownErrors.clear()
        loadingMarkdownIds.clear()
    }

    fun syncMarkdownState(announcements: List<AnnouncementItem>) {
        val validIds = announcements.map { it.id }.toSet()
        markdownById.keys.filter { it !in validIds }.forEach { markdownById.remove(it) }
        markdownErrors.keys.filter { it !in validIds }.forEach { markdownErrors.remove(it) }
        loadingMarkdownIds.keys.filter { it !in validIds }.forEach { loadingMarkdownIds.remove(it) }

        announcements.forEach { announcement ->
            if (!announcement.markdown.isNullOrBlank()) {
                markdownById[announcement.id] = announcement.markdown
            }
        }
    }

    fun loadMarkdown(announcementId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && (markdownById.containsKey(announcementId) || loadingMarkdownIds[announcementId] == true)) {
            return
        }

        scope.launch {
            loadingMarkdownIds[announcementId] = true
            if (forceRefresh) {
                markdownErrors.remove(announcementId)
            }

            val result = service.fetchAnnouncementMarkdown(
                announcementId = announcementId,
                forceRefresh = forceRefresh
            )

            result.fold(
                onSuccess = { markdown ->
                    markdownById[announcementId] = markdown
                    markdownErrors.remove(announcementId)
                },
                onFailure = { error ->
                    markdownErrors[announcementId] = error.message ?: "加载内容失败"
                }
            )

            loadingMarkdownIds.remove(announcementId)
        }
    }

    fun loadAnnouncements(forceRefresh: Boolean) {
        scope.launch {
            if (forceRefresh) {
                isRefreshing = true
                markdownReloadToken += 1
                clearMarkdownState()
            } else {
                uiState = AnnouncementUiState.Loading
            }

            val result = service.fetchAnnouncements(forceRefresh = forceRefresh)
            uiState = result.fold(
                onSuccess = { announcements ->
                    syncMarkdownState(announcements)
                    AnnouncementUiState.Success(announcements)
                },
                onFailure = { error ->
                    AnnouncementUiState.Error(error.message ?: "加载公告失败")
                }
            )
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadAnnouncements(forceRefresh = false)
    }

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { loadAnnouncements(forceRefresh = true) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = uiState,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(240)) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(240)
                        ))
                        .togetherWith(
                            fadeOut(animationSpec = tween(180)) +
                                scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(180)
                                )
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "announcementStateTransition"
            ) { currentState ->
                when (currentState) {
                    AnnouncementUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    is AnnouncementUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = currentState.message,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { loadAnnouncements(forceRefresh = true) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }

                    is AnnouncementUiState.Success -> {
                        if (currentState.announcements.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无公告",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(
                                    items = currentState.announcements,
                                    key = { it.id }
                                ) { announcement ->
                                    LaunchedEffect(announcement.id, markdownReloadToken) {
                                        loadMarkdown(announcementId = announcement.id)
                                    }

                                    AnnouncementCard(
                                        announcement = announcement,
                                        markdown = markdownById[announcement.id] ?: announcement.markdown,
                                        isMarkdownLoading = loadingMarkdownIds[announcement.id] == true,
                                        markdownError = markdownErrors[announcement.id],
                                        onRetryLoadMarkdown = {
                                            loadMarkdown(
                                                announcementId = announcement.id,
                                                forceRefresh = true
                                            )
                                        }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (uiState is AnnouncementUiState.Loading || isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: AnnouncementItem,
    markdown: String?,
    isMarkdownLoading: Boolean,
    markdownError: String?,
    onRetryLoadMarkdown: () -> Unit
) {
    var animateSizeAfterLoading by remember(announcement.id) { mutableStateOf(false) }
    LaunchedEffect(announcement.id, isMarkdownLoading) {
        if (isMarkdownLoading) {
            animateSizeAfterLoading = true
        }
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .let { base ->
            if (animateSizeAfterLoading) {
                base.animateContentSize(
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = FastOutSlowInEasing
                    ),
                    finishedListener = { _, _ ->
                        animateSizeAfterLoading = false
                    }
                )
            } else {
                base
            }
        }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = announcement.publishedAt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (announcement.tags.isNotEmpty()) {
                    Text(
                        text = "  ·  " + announcement.tags.joinToString(" / "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            when {
                !markdown.isNullOrBlank() -> {
                    MarkdownText(markdown = markdown)
                }

                isMarkdownLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                !markdownError.isNullOrBlank() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = markdownError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRetryLoadMarkdown) {
                            Text("重试")
                        }
                    }
                }

                else -> {
                    Text(
                        text = "暂无内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private sealed interface AnnouncementUiState {
    data object Loading : AnnouncementUiState
    data class Success(val announcements: List<AnnouncementItem>) : AnnouncementUiState
    data class Error(val message: String) : AnnouncementUiState
}
