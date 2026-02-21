package com.app.ralaunch.feature.announcement

import android.content.Context
import com.app.ralaunch.core.common.JsonHttpRepositoryClient
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.shared.core.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 公告远程仓库服务
 */
class AnnouncementRepositoryService(private val context: Context) {
    companion object {
        private const val TAG = "AnnouncementRepoService"

        const val REPO_URL_GITHUB = "https://raw.githubusercontent.com/RotatingArtDev/RAL-Announcements/main"
        const val REPO_URL_GITEE = "https://gitee.com/daohei/RAL-Announcements/raw/main"
        const val REPO_INDEX_FILE = "announcements.json"

        private const val FALLBACK_LOCALE = "en-US"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private const val CACHE_VALID_DURATION_MS = 10 * 60 * 1000L

        private val localDateTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    var repoUrl: String = getDefaultRepoUrlFromLauncherSetting()

    private var cachedAnnouncements: List<AnnouncementItem>? = null
    private var resolvedAnnouncements: Map<String, ResolvedAnnouncement> = emptyMap()
    private val markdownCache = mutableMapOf<String, String>()
    private var cacheTimestamp: Long = 0L
    private var cachedLocaleTag: String? = null

    suspend fun fetchAnnouncements(forceRefresh: Boolean = false): Result<List<AnnouncementItem>> {
        invalidateCachesIfLocaleChanged()

        if (!forceRefresh && isCacheValid()) {
            return Result.success(cachedAnnouncements.orEmpty())
        }

        return withContext(Dispatchers.IO) {
            val primaryUrl = repoUrl
            val fallbackUrl = getFallbackRepoUrl(primaryUrl)

            var result = tryFetchIndexFrom(primaryUrl)
            if (result.isFailure) {
                AppLogger.info(TAG, "Primary source failed, trying fallback: $fallbackUrl")
                result = tryFetchIndexFrom(fallbackUrl)
            }

            result.getOrNull()?.let { payload ->
                cachedAnnouncements = payload.announcements
                resolvedAnnouncements = payload.resolvedById
                cacheTimestamp = System.currentTimeMillis()
                pruneMarkdownCache(payload.announcements)
                AppLogger.info(TAG, "Fetched announcements: ${payload.announcements.size}")
            }

            result.exceptionOrNull()?.let { error ->
                AppLogger.error(TAG, "Failed to fetch announcements", error)
            }

            result.map { it.announcements }
        }
    }

    suspend fun fetchAnnouncementMarkdown(
        announcementId: String,
        forceRefresh: Boolean = false
    ): Result<String> {
        invalidateCachesIfLocaleChanged()

        if (!forceRefresh) {
            markdownCache[announcementId]?.let { cached ->
                return Result.success(cached)
            }
        }

        val resolved = resolveAnnouncement(announcementId)
            ?: return Result.failure(IllegalArgumentException("Announcement not found: $announcementId"))

        return withContext(Dispatchers.IO) {
            val primaryUrl = repoUrl
            val fallbackUrl = getFallbackRepoUrl(primaryUrl)
            var lastError: Throwable? = null

            for (locale in resolved.readmeLocaleCandidates) {
                val relativePath = "announcements/$announcementId/README.$locale.md"
                val primaryResult = JsonHttpRepositoryClient.getText(
                    urlString = "$primaryUrl/$relativePath",
                    connectTimeoutMs = CONNECT_TIMEOUT,
                    readTimeoutMs = READ_TIMEOUT
                )

                if (primaryResult.isSuccess) {
                    val content = primaryResult.getOrThrow()
                    markdownCache[announcementId] = content
                    return@withContext Result.success(content)
                }

                lastError = primaryResult.exceptionOrNull() ?: lastError

                val fallbackResult = JsonHttpRepositoryClient.getText(
                    urlString = "$fallbackUrl/$relativePath",
                    connectTimeoutMs = CONNECT_TIMEOUT,
                    readTimeoutMs = READ_TIMEOUT
                )

                if (fallbackResult.isSuccess) {
                    val content = fallbackResult.getOrThrow()
                    markdownCache[announcementId] = content
                    return@withContext Result.success(content)
                }

                lastError = fallbackResult.exceptionOrNull() ?: lastError
            }

            val error = lastError ?: IllegalStateException("Failed to fetch README for $announcementId")
            AppLogger.error(TAG, "Failed to fetch markdown: $announcementId", error)
            Result.failure(error)
        }
    }

    fun clearCache() {
        cachedAnnouncements = null
        resolvedAnnouncements = emptyMap()
        markdownCache.clear()
        cacheTimestamp = 0L
        cachedLocaleTag = null
    }

    private fun isCacheValid(): Boolean {
        if (cachedAnnouncements == null) return false
        return System.currentTimeMillis() - cacheTimestamp < CACHE_VALID_DURATION_MS
    }

    private fun getFallbackRepoUrl(primaryUrl: String): String {
        return if (primaryUrl == REPO_URL_GITEE) REPO_URL_GITHUB else REPO_URL_GITEE
    }

    private fun getDefaultRepoUrlFromLauncherSetting(): String {
        return if (resolveLauncherLocaleTag().startsWith("zh", ignoreCase = true)) {
            REPO_URL_GITEE
        } else {
            REPO_URL_GITHUB
        }
    }

    private suspend fun tryFetchIndexFrom(baseUrl: String): Result<IndexPayload> {
        return JsonHttpRepositoryClient.getJson<AnnouncementRepositoryDto>(
            urlString = "$baseUrl/$REPO_INDEX_FILE",
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT,
            readTimeoutMs = READ_TIMEOUT
        ).mapCatching { repository ->
            val mapped = repository.announcements.mapNotNull { entry ->
                mapAnnouncementEntry(entry)
            }.sortedByDescending { it.sortKey }

            IndexPayload(
                announcements = mapped.map { it.item },
                resolvedById = mapped.associate { it.item.id to it.resolvedAnnouncement }
            )
        }
    }

    private fun mapAnnouncementEntry(entry: AnnouncementEntryDto): MappedAnnouncement? {
        if (entry.id.isBlank() || entry.meta.isEmpty()) return null

        val availableLocales = entry.meta.keys.filter { it.isNotBlank() }
        if (availableLocales.isEmpty()) return null

        val localeCandidates = buildLocaleCandidates(availableLocales)
        val localeMeta = entry.meta[localeCandidates.firstOrNull()] ?: return null

        return MappedAnnouncement(
            sortKey = entry.publishedAt,
            item = AnnouncementItem(
                id = entry.id,
                title = localeMeta.title,
                markdown = null,
                publishedAt = normalizePublishedDate(entry.publishedAt),
                tags = localeMeta.tags
            ),
            resolvedAnnouncement = ResolvedAnnouncement(
                readmeLocaleCandidates = localeCandidates
            )
        )
    }

    private fun buildLocaleCandidates(availableLocales: List<String>): List<String> {
        val candidates = mutableListOf<String>()

        fun addLocale(target: String) {
            val resolved = availableLocales.firstOrNull { it.equals(target, ignoreCase = true) }
            if (resolved != null && resolved !in candidates) {
                candidates.add(resolved)
            }
        }

        val launcherLocaleTag = resolveLauncherLocaleTag()
        addLocale(launcherLocaleTag)
        addLocale(launcherLocaleTag.substringBefore('-'))
        addLocale(FALLBACK_LOCALE)

        availableLocales.firstOrNull()?.let { firstLocale ->
            if (firstLocale !in candidates) {
                candidates.add(firstLocale)
            }
        }

        return candidates
    }

    private fun resolveLauncherLocaleTag(): String {
        val configuredLanguage = LocaleManager.getCurrentLanguage()
        if (configuredLanguage == LocaleHelper.LANGUAGE_AUTO) {
            return context.resources.configuration.locales[0].toLanguageTag()
        }
        return languageCodeToLocaleTag(configuredLanguage)
    }

    private fun languageCodeToLocaleTag(languageCode: String): String = when (languageCode.lowercase()) {
        LocaleHelper.LANGUAGE_ZH -> "zh-CN"
        LocaleHelper.LANGUAGE_EN -> "en-US"
        LocaleHelper.LANGUAGE_RU -> "ru-RU"
        LocaleHelper.LANGUAGE_ES -> "es-ES"
        LocaleHelper.LANGUAGE_JA -> "ja-JP"
        else -> Locale.forLanguageTag(languageCode).toLanguageTag()
    }

    private suspend fun resolveAnnouncement(announcementId: String): ResolvedAnnouncement? {
        resolvedAnnouncements[announcementId]?.let { return it }
        val refreshResult = fetchAnnouncements(forceRefresh = true)
        if (refreshResult.isFailure) {
            AppLogger.warn(TAG, "Failed to refresh announcements before markdown fetch")
        }
        return resolvedAnnouncements[announcementId]
    }

    private fun pruneMarkdownCache(announcements: List<AnnouncementItem>) {
        val validIds = announcements.map { it.id }.toSet()
        markdownCache.keys.retainAll(validIds)
    }

    private fun invalidateCachesIfLocaleChanged() {
        val currentLocaleTag = resolveLauncherLocaleTag()
        if (cachedLocaleTag == currentLocaleTag) return

        cachedAnnouncements = null
        resolvedAnnouncements = emptyMap()
        markdownCache.clear()
        cacheTimestamp = 0L
        cachedLocaleTag = currentLocaleTag
    }

    private fun normalizePublishedDate(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return value

        return try {
            Instant.parse(value)
                .atZone(ZoneId.systemDefault())
                .format(localDateTimeFormatter)
        } catch (_: DateTimeParseException) {
            if (value.length >= 10 && value[4] == '-' && value[7] == '-') {
                value.substring(0, 10)
            } else {
                value
            }
        }
    }

    @Serializable
    private data class AnnouncementRepositoryDto(
        val version: Int = 1,
        val announcements: List<AnnouncementEntryDto> = emptyList()
    )

    @Serializable
    private data class AnnouncementEntryDto(
        val id: String,
        val publishedAt: String,
        val meta: Map<String, AnnouncementLocaleMetaDto> = emptyMap()
    )

    @Serializable
    private data class AnnouncementLocaleMetaDto(
        val title: String,
        val tags: List<String> = emptyList()
    )

    private data class ResolvedAnnouncement(
        val readmeLocaleCandidates: List<String>
    )

    private data class MappedAnnouncement(
        val sortKey: String,
        val item: AnnouncementItem,
        val resolvedAnnouncement: ResolvedAnnouncement
    )

    private data class IndexPayload(
        val announcements: List<AnnouncementItem>,
        val resolvedById: Map<String, ResolvedAnnouncement>
    )
}
