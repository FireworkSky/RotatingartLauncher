package com.app.ralaunch.feature.announcement

/**
 * 公告条目
 */
data class AnnouncementItem(
    val id: String,
    val title: String,
    val markdown: String?,
    val publishedAt: String,
    val tags: List<String> = emptyList()
)
