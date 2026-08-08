package com.app.ralaunch.utils

import android.content.Context
import com.app.ralaunch.models.*
import com.app.ralaunch.strings.StringsResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLException

/*******************************************************************************
 * RotatingArtLauncher - AnnouncementUtils
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

object AnnouncementUtils {

    private const val REPO_URL = "https://raw.githubusercontent.com/RotatingArtDev/RAL-Announcements/main"
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun fetchAnnouncements(context: Context): Result<List<AnnouncementItem>> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Fetching announcements from: $REPO_URL/announcements.json")

                val url = "$REPO_URL/announcements.json"
                val response = fetchTextFromUrl(url)

                Timber.d("Announcements response received, length: ${response.length}")
                Timber.v("Response preview: ${response.take(500)}")

                val items = parseAnnouncementResponse(response, context)
                Timber.i("Successfully parsed ${items.size} announcements")

                items.forEach { item ->
                    Timber.v("Announcement: id=${item.id}, title=${item.title}, date=${item.publishedAt}")
                }

                Result.success(items)
            } catch (e: UnknownHostException) {
                Timber.e(e, "Network error: Cannot reach GitHub - check internet connection")
                Result.failure(Exception(StringsResource.Strings.announcement.networkError))
            } catch (e: SSLException) {
                Timber.e(e, "SSL error: HTTPS handshake failed")
                Result.failure(Exception(StringsResource.Strings.announcement.sslError))
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch announcements: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAnnouncementContent(announcementId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Fetching content for announcement: $announcementId")

                // 获取当前语言
                val currentLanguage = StringsResource.getCurrentLanguage()

                // 根据语言选择对应的文件后缀
                val languageSuffix = getLanguageSuffix(currentLanguage)
                val url = "$REPO_URL/announcements/$announcementId/README.${languageSuffix}.md"
                Timber.v("Trying: $url")

                val content = fetchTextFromUrl(url)
                Timber.i("Successfully loaded content for: $announcementId (${languageSuffix})")
                Result.success(content)

            } catch (e: Exception) {
                // 如果请求失败，尝试降级到英文
                try {
                    Timber.w(e, "Failed to load preferred language, trying English fallback")
                    val fallbackUrl = "$REPO_URL/announcements/$announcementId/README.en-US.md"
                    val content = fetchTextFromUrl(fallbackUrl)
                    Timber.i("Successfully loaded English fallback for: $announcementId")
                    Result.success(content)
                } catch (fallbackError: Exception) {
                    Timber.e(fallbackError, "Failed to load English fallback for: $announcementId")
                    Result.failure(fallbackError)
                }
            }
        }
    }

    /**
     * 获取语言文件后缀
     */
    private fun getLanguageSuffix(language: StringsResource.Language): String {
        return when (language) {
            StringsResource.Language.ZhHans -> "zh-CN"
            StringsResource.Language.En -> "en-US"/*
            StringsResource.Language.Ru -> "ru-RU"
            StringsResource.Language.Es -> "es-ES"*/
            StringsResource.Language.System -> {
                // 如果系统语言，根据系统语言决定
                val systemLang = StringsResource.fromSystemLocale()
                getLanguageSuffix(systemLang)
            }
            // 默认英文
        }
    }

    private suspend fun fetchTextFromUrl(urlString: String): String {
        return withContext(Dispatchers.IO) {
            Timber.v("Making HTTP request to: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "RotatingArtLauncher/1.0")

                val responseCode = connection.responseCode
                Timber.v("HTTP response code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorMsg = "HTTP error: $responseCode for URL: $urlString"
                    Timber.e(errorMsg)
                    throw Exception(errorMsg)
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                    response.append('\n')
                }
                reader.close()

                val result = response.toString()
                Timber.v("Downloaded ${result.length} bytes from: $urlString")
                result
            } finally {
                connection.disconnect()
                Timber.v("HTTP connection closed for: $urlString")
            }
        }
    }

    private fun parseAnnouncementResponse(response: String, context: Context): List<AnnouncementItem> {
        Timber.d("Parsing announcement response")

        try {
            val data = json.decodeFromString<AnnouncementResponse>(response)
            Timber.d("Parsed data: version=${data.version}, entries=${data.announcements.size}")

            // 获取当前语言和可用语言列表
            val currentLanguage = StringsResource.getCurrentLanguage()
            val localeTags = getAvailableLocales(context)

            Timber.d("Current language: $currentLanguage")
            Timber.d("Available locales: $localeTags")

            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

            val items = data.announcements.mapNotNull { entry ->
                // 根据当前语言选择元数据
                val meta = selectMetaByLanguage(entry.meta, currentLanguage, localeTags)
                if (meta == null) {
                    Timber.w("No matching meta for entry: ${entry.id}")
                    return@mapNotNull null
                }
                AnnouncementItem(
                    id = entry.id,
                    title = meta.title,  // 从元数据获取标题
                    content = "",
                    publishedAt = formatDate(entry.publishedAt, dateFormatter),
                    tags = meta.tags
                )
            }.sortedByDescending { it.publishedAt }

            Timber.i("Parsed ${items.size} announcement items")
            return items

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse announcement JSON response")
            throw Exception("Failed to parse announcement data: ${e.message}")
        }
    }

    /**
     * 根据当前语言选择对应的元数据
     */
    private fun selectMetaByLanguage(
        metaMap: Map<String, AnnouncementMeta>,
        currentLanguage: StringsResource.Language,
        availableLocales: List<String>
    ): AnnouncementMeta? {
        Timber.v("Selecting meta for language: $currentLanguage, available: ${metaMap.keys}")

        // 获取当前语言对应的 locale tag
        val targetLocale = when (currentLanguage) {
            StringsResource.Language.ZhHans -> "zh-CN"
            StringsResource.Language.En -> "en-US"/*
            StringsResource.Language.Ru -> "ru-RU"
            StringsResource.Language.Es -> "es-ES"*/
            StringsResource.Language.System -> {
                // 从系统语言获取
                val systemLocale = StringsResource.fromSystemLocale()
                when (systemLocale) {
                    StringsResource.Language.ZhHans -> "zh-CN"
                    StringsResource.Language.En -> "en-US"/*
                    StringsResource.Language.Ru -> "ru-RU"
                    StringsResource.Language.Es -> "es-ES"*/
                    else -> "en-US"
                }
            }
        }

        // 1. 精确匹配
        metaMap[targetLocale]?.let {
            Timber.v("Exact match found: $targetLocale")
            return it
        }

        // 2. 语言前缀匹配 (zh-CN -> zh)
        val prefix = targetLocale.substringBefore('-')
        metaMap.entries.firstOrNull { it.key.startsWith(prefix) }?.let {
            Timber.v("Prefix match found: ${it.key} for prefix: $prefix")
            return it.value
        }

        // 3. 尝试匹配用户语言的任何变体
        availableLocales.forEach { locale ->
            metaMap[locale]?.let {
                Timber.v("Found match for available locale: $locale")
                return it
            }
        }

        // 4. 尝试匹配语言前缀（从可用语言列表）
        availableLocales.forEach { locale ->
            val localePrefix = locale.substringBefore('-')
            metaMap.entries.firstOrNull { it.key.startsWith(localePrefix) }?.let {
                Timber.v("Found prefix match from available locales: ${it.key}")
                return it.value
            }
        }

        // 5. 降级到第一个可用
        val fallback = metaMap.values.firstOrNull()
        Timber.w("No matching meta found, using fallback: ${fallback?.title ?: "null"}")
        return fallback
    }

    /**
     * 获取设备支持的语言列表
     */
    private fun getAvailableLocales(context: Context): List<String> {
        return try {
            val locales = context.resources.configuration.locales
            (0 until locales.size()).map { index ->
                locales[index].toLanguageTag()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get available locales")
            listOf("en-US")
        }
    }

    private fun formatDate(raw: String, formatter: DateTimeFormatter): String {
        return try {
            val instant = Instant.parse(raw)
            val formatted = instant.atZone(ZoneId.systemDefault()).format(formatter)
            Timber.v("Formatted date: $raw -> $formatted")
            formatted
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse date: $raw, using raw string")
            raw.substringBefore('T')
        }
    }
}