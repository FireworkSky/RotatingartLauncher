package com.app.ralaunch.utils

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object LauncherUpdateChecker {

    private const val GITHUB_API_BASE_URL = "https://api.github.com"
    private const val VERSION_CONFIG_GITHUB_URL =
        "https://raw.githubusercontent.com/RotatingArtDev/RAL-Version/main/version.json"
    private const val VERSION_CONFIG_GITEE_URL =
        "https://gitee.com/daohei/RAL-Version/raw/main/version.json"
    private const val DEFAULT_REPOSITORY_OWNER = "FireworkSky"
    private const val DEFAULT_REPOSITORY_NAME = "RotatingartLauncher"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val USER_AGENT = "RotatingartLauncher-Android"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 使用默认仓库检查更新
     * @param context 上下文（可以是 Activity 或 Application）
     * @param currentVersionName 当前版本号
     */
    suspend fun checkForUpdate(
        context: Context,
        currentVersionName: String
    ): Result<LauncherUpdateInfo?> {
        return checkForUpdate(
            context = context,
            repositoryOwner = DEFAULT_REPOSITORY_OWNER,
            repositoryName = DEFAULT_REPOSITORY_NAME,
            currentVersionName = currentVersionName
        )
    }

    /**
     * 使用自定义仓库检查更新
     */
    suspend fun checkForUpdate(
        context: Context,
        repositoryOwner: String,
        repositoryName: String,
        currentVersionName: String
    ): Result<LauncherUpdateInfo?> {
        val currentVersion = parseVersion(currentVersionName)
            ?: return Result.failure(
                IllegalArgumentException("Invalid current version: $currentVersionName")
            )

        val configResult = fetchVersionConfigRelease(context)
        val configRelease = configResult.getOrNull()

        if (configRelease != null) {
            val latestVersion = parseVersion(configRelease.version)
            if (latestVersion != null && isRemoteVersionNewer(currentVersion, latestVersion)) {
                val githubUrl = configRelease.downloads.github.trim()
                val cloudUrl = configRelease.downloads.cloud.trim()
                val releaseUrl = configRelease.releasePage.trim().ifBlank {
                    "https://github.com/$repositoryOwner/$repositoryName/releases/latest"
                }
                val releaseNotes = buildString {
                    if (configRelease.publishedAt.isNotBlank()) {
                        append("发布日期：")
                        append(configRelease.publishedAt.trim())
                        append('\n')
                        append('\n')
                    }
                    if (configRelease.description.isNotBlank()) {
                        append(configRelease.description.trim())
                    }
                    if (configRelease.changelog.isNotEmpty()) {
                        if (isNotEmpty()) {
                            append('\n')
                            append('\n')
                        }
                        append("更新内容：")
                        append('\n')
                        configRelease.changelog.forEach { item ->
                            append("• ")
                            append(item.trim())
                            append('\n')
                        }
                    }
                }.trim()

                return Result.success(
                    LauncherUpdateInfo(
                        currentVersion = currentVersionName,
                        latestVersion = configRelease.version.trim(),
                        releaseName = configRelease.releaseName.ifBlank {
                            configRelease.version.trim()
                        },
                        releaseNotes = releaseNotes,
                        downloadUrl = githubUrl,
                        releaseUrl = releaseUrl,
                        githubDownloadUrl = githubUrl,
                        cloudDownloadUrl = cloudUrl,
                        publishedAt = configRelease.publishedAt.trim()
                    )
                )
            }
            return Result.success(null)
        }

        return fetchLatestStableRelease(repositoryOwner, repositoryName).mapCatching { release ->
            if (release.tagName.isBlank()) return@mapCatching null
            val latestVersion = parseVersion(release.tagName) ?: return@mapCatching null
            if (!isRemoteVersionNewer(currentVersion, latestVersion)) return@mapCatching null

            val githubUrl = release.resolveDownloadUrl()
            LauncherUpdateInfo(
                currentVersion = currentVersionName,
                latestVersion = release.tagName.trim(),
                releaseName = release.name.ifBlank { release.tagName.trim() },
                releaseNotes = release.body.trim(),
                downloadUrl = githubUrl,
                releaseUrl = release.htmlUrl.ifBlank {
                    "https://github.com/$repositoryOwner/$repositoryName/releases/latest"
                },
                githubDownloadUrl = githubUrl,
                cloudDownloadUrl = "",
                publishedAt = release.publishedAt.trim()
            )
        }
    }

    private suspend fun fetchVersionConfigRelease(context: Context): Result<VersionReleaseDto?> {
        val headers = mapOf(
            "Accept" to "application/json",
            "User-Agent" to USER_AGENT
        )

        fun resolveRelease(config: VersionConfigDto): VersionReleaseDto? {
            val direct = VersionReleaseDto(
                version = config.version,
                releaseName = config.releaseName,
                publishedAt = config.publishedAt,
                description = config.description,
                changelog = config.changelog,
                downloads = config.downloads,
                releasePage = config.releasePage
            )
            if (direct.version.isNotBlank()) return direct
            return config.latest
        }

        val primaryUrl = if (isChinese(context)) VERSION_CONFIG_GITEE_URL else VERSION_CONFIG_GITHUB_URL
        val fallbackUrl = if (primaryUrl == VERSION_CONFIG_GITEE_URL) {
            VERSION_CONFIG_GITHUB_URL
        } else {
            VERSION_CONFIG_GITEE_URL
        }

        var primaryError: Throwable?
        var fallbackError: Throwable?

        val primary = JsonHttpRepositoryClient.getJson<VersionConfigDto>(
            urlString = primaryUrl,
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = headers
        ).mapCatching { cfg -> resolveRelease(cfg) }
        primary.getOrNull()?.let { return Result.success(it) }
        primaryError = primary.exceptionOrNull()

        val fallback = JsonHttpRepositoryClient.getJson<VersionConfigDto>(
            urlString = fallbackUrl,
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = headers
        ).mapCatching { cfg -> resolveRelease(cfg) }
        fallback.getOrNull()?.let { return Result.success(it) }
        fallbackError = fallback.exceptionOrNull()

        return Result.failure(
            primaryError
                ?: fallbackError
                ?: IllegalStateException("Unable to load version config")
        )
    }

    private fun isChinese(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "zh"
    }

    private suspend fun fetchLatestStableRelease(
        repositoryOwner: String,
        repositoryName: String
    ): Result<GitHubReleaseResponse> {
        val headers = mapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to USER_AGENT
        )

        val latestUrl = "$GITHUB_API_BASE_URL/repos/$repositoryOwner/$repositoryName/releases/latest"
        val latestResult = JsonHttpRepositoryClient.getJson<GitHubReleaseResponse>(
            urlString = latestUrl,
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = headers
        )
        latestResult.getOrNull()
            ?.takeIf { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
            ?.let { return Result.success(it) }

        val releasesUrl = "$GITHUB_API_BASE_URL/repos/$repositoryOwner/$repositoryName/releases?per_page=10"
        val releasesResult = JsonHttpRepositoryClient.getJson<List<GitHubReleaseResponse>>(
            urlString = releasesUrl,
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = headers
        )
        releasesResult.getOrNull()
            ?.firstOrNull { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
            ?.let { return Result.success(it) }

        val redirectTagResult = resolveLatestTagByRedirect(repositoryOwner, repositoryName)
        val redirectTag = redirectTagResult.getOrNull()
        if (!redirectTag.isNullOrBlank()) {
            val releaseUrl = "https://github.com/$repositoryOwner/$repositoryName/releases/tag/$redirectTag"
            return Result.success(
                GitHubReleaseResponse(
                    tagName = redirectTag,
                    name = redirectTag,
                    body = "",
                    htmlUrl = releaseUrl,
                    assets = emptyList(),
                    draft = false,
                    prerelease = false
                )
            )
        }

        val latestError = latestResult.exceptionOrNull()
        val listError = releasesResult.exceptionOrNull()
        val redirectError = redirectTagResult.exceptionOrNull()
        return Result.failure(
            latestError
                ?: listError
                ?: redirectError
                ?: IllegalStateException("Unable to resolve latest release")
        )
    }

    private suspend fun resolveLatestTagByRedirect(
        repositoryOwner: String,
        repositoryName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val latestReleasePage = "https://github.com/$repositoryOwner/$repositoryName/releases/latest"
            val connection = URL(latestReleasePage).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connect()

                val finalUrl = connection.url.toString()
                finalUrl.substringAfter("/releases/tag/", "")
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim()
                    .ifBlank { throw IllegalStateException("Cannot parse tag from $finalUrl") }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseVersion(rawValue: String): ParsedVersion? {
        val cleanedValue = rawValue
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')

        if (cleanedValue.isBlank()) return null

        val parts = cleanedValue.split('.')
            .mapNotNull { segment -> segment.toIntOrNull() }

        if (parts.isEmpty()) return null
        return ParsedVersion(parts)
    }

    private fun isRemoteVersionNewer(
        currentVersion: ParsedVersion,
        remoteVersion: ParsedVersion
    ): Boolean {
        val maxLength = maxOf(currentVersion.parts.size, remoteVersion.parts.size)
        for (index in 0 until maxLength) {
            val currentPart = currentVersion.parts.getOrElse(index) { 0 }
            val remotePart = remoteVersion.parts.getOrElse(index) { 0 }
            if (remotePart > currentPart) return true
            if (remotePart < currentPart) return false
        }
        return false
    }

    private fun GitHubReleaseResponse.resolveDownloadUrl(): String {
        val apkAssets = assets.filter { asset ->
            val lowerName = asset.name.lowercase()
            val lowerType = asset.contentType.lowercase()
            lowerName.endsWith(".apk") ||
                    lowerType.contains("application/vnd.android.package-archive")
        }
        if (apkAssets.isEmpty()) return ""

        val preferredKeywords = listOf("arm64-v8a", "arm64", "aarch64")
        val preferred = apkAssets.firstOrNull { asset ->
            val lowerName = asset.name.lowercase()
            preferredKeywords.any { keyword -> keyword in lowerName }
        }
        return (preferred ?: apkAssets.first()).browserDownloadUrl.trim()
    }

    private data class ParsedVersion(
        val parts: List<Int>
    )

    @Serializable
    private data class GitHubReleaseResponse(
        @SerialName("tag_name")
        val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerialName("published_at")
        val publishedAt: String = "",
        @SerialName("html_url")
        val htmlUrl: String = "",
        val assets: List<GitHubReleaseAssetResponse> = emptyList(),
        val draft: Boolean = false,
        val prerelease: Boolean = false
    )

    @Serializable
    private data class GitHubReleaseAssetResponse(
        val name: String = "",
        @SerialName("browser_download_url")
        val browserDownloadUrl: String = "",
        @SerialName("content_type")
        val contentType: String = ""
    )

    @Serializable
    private data class VersionConfigDto(
        val schemaVersion: Int = 1,
        val channel: String = "stable",
        val latest: VersionReleaseDto? = null,
        val version: String = "",
        val releaseName: String = "",
        val publishedAt: String = "",
        val description: String = "",
        val changelog: List<String> = emptyList(),
        val downloads: VersionDownloadsDto = VersionDownloadsDto(),
        val releasePage: String = ""
    )

    @Serializable
    private data class VersionReleaseDto(
        val version: String = "",
        val releaseName: String = "",
        val publishedAt: String = "",
        val description: String = "",
        val changelog: List<String> = emptyList(),
        val downloads: VersionDownloadsDto = VersionDownloadsDto(),
        val releasePage: String = ""
    )

    @Serializable
    private data class VersionDownloadsDto(
        val github: String = "",
        val cloud: String = ""
    )
}

data class LauncherUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val githubDownloadUrl: String = "",
    val cloudDownloadUrl: String = "",
    val publishedAt: String = ""
)