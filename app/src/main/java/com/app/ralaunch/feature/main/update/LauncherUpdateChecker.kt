package com.app.ralaunch.feature.main.update

import com.app.ralaunch.core.common.JsonHttpRepositoryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class LauncherUpdateChecker(
    private val repositoryOwner: String = DEFAULT_REPOSITORY_OWNER,
    private val repositoryName: String = DEFAULT_REPOSITORY_NAME
) {

    companion object {
        private const val GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_REPOSITORY_OWNER = "FireworkSky"
        private const val DEFAULT_REPOSITORY_NAME = "RotatingartLauncher"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "RotatingartLauncher-Android"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun checkForUpdate(currentVersionName: String): Result<LauncherUpdateInfo?> {
        val currentVersion = parseVersion(currentVersionName)
            ?: return Result.failure(
                IllegalArgumentException("Invalid current version: $currentVersionName")
            )

        return fetchLatestStableRelease().mapCatching { release ->
            if (release.tagName.isBlank()) return@mapCatching null

            val latestVersion = parseVersion(release.tagName) ?: return@mapCatching null
            if (!isRemoteVersionNewer(currentVersion, latestVersion)) {
                return@mapCatching null
            }

            LauncherUpdateInfo(
                currentVersion = currentVersionName,
                latestVersion = release.tagName.trim(),
                releaseName = release.name.ifBlank { release.tagName.trim() },
                releaseNotes = release.body.trim(),
                downloadUrl = release.resolveDownloadUrl(),
                releaseUrl = release.htmlUrl.ifBlank {
                    "https://github.com/$repositoryOwner/$repositoryName/releases/latest"
                }
            )
        }
    }

    private suspend fun fetchLatestStableRelease(): Result<GitHubReleaseResponse> {
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

        val redirectTagResult = resolveLatestTagByRedirect()
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

    private suspend fun resolveLatestTagByRedirect(): Result<String> = withContext(Dispatchers.IO) {
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
}

data class LauncherUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String
)
