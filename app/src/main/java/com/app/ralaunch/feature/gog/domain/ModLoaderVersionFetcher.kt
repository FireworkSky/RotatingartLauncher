package com.app.ralaunch.feature.gog.domain

import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderVersion
import com.app.ralaunch.core.logging.AppLog
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * ModLoader 版本获取器
 * 从 GitHub Releases API 动态获取最新版本
 */
object ModLoaderVersionFetcher {

    private const val TAG = "ModLoaderVersionFetcher"
    private const val GITHUB_API = "https://api.github.com/repos"
    private const val MAX_STABLE_VERSIONS = 5  // 返回的稳定版本数
    private const val FETCH_COUNT = 30  // 从 API 获取的版本数（用于筛选稳定版）

    /**
     * GitHub Release 信息
     */
    data class GitHubRelease(
        val tagName: String,
        val name: String,
        val prerelease: Boolean,
        val draft: Boolean,
        val assets: List<GitHubAsset>
    )

    data class GitHubAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    /**
     * ModLoader 仓库配置
     */
    enum class ModLoaderRepo(
        val owner: String,
        val repo: String,
        val assetPattern: Regex,
        val fileNameTemplate: (String) -> String
    ) {
        TMODLOADER(
            owner = "tModLoader",
            repo = "tModLoader",
            assetPattern = Regex("tModLoader\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "tModLoader-$version.zip" }
        ),
        SMAPI(
            owner = "Pathoschild",
            repo = "SMAPI",
            assetPattern = Regex("SMAPI-[\\d.]+-installer\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "SMAPI-$version-installer.zip" }
        ),
        EVEREST(
            owner = "EverestAPI",
            repo = "Everest",
            assetPattern = Regex("main\\.zip", RegexOption.IGNORE_CASE),
            fileNameTemplate = { version -> "Everest-$version.zip" }
        );

        companion object {
            fun fromModLoaderName(name: String): ModLoaderRepo? {
                return when (name.lowercase()) {
                    "tmodloader" -> TMODLOADER
                    "smapi" -> SMAPI
                    "everest" -> EVEREST
                    else -> null
                }
            }
        }
    }

    /**
     * 获取 ModLoader 的最新稳定版本列表
     * @param modLoaderName ModLoader 名称 (tModLoader, SMAPI, Everest)
     * @param maxCount 最大返回数量
     * @param includePrerelease 是否包含预发布版本（已弃用，始终只返回稳定版）
     */
    fun fetchVersions(
        modLoaderName: String,
        maxCount: Int = MAX_STABLE_VERSIONS,
        @Suppress("UNUSED_PARAMETER") includePrerelease: Boolean = false
    ): List<ModLoaderVersion> {
        val repo = ModLoaderRepo.fromModLoaderName(modLoaderName) ?: run {
            AppLog.w(TAG, "未知的 ModLoader: $modLoaderName")
            return emptyList()
        }

        return try {
            val releases = fetchGitHubReleases(repo.owner, repo.repo)
            
            // 只获取稳定版本（非 draft、非 prerelease）
            releases
                .filter { !it.draft && !it.prerelease }
                .take(maxCount)
                .mapNotNull { release ->
                    val asset = release.assets.find { repo.assetPattern.matches(it.name) }
                    
                    if (asset != null) {
                        ModLoaderVersion(
                            version = release.tagName,
                            url = asset.downloadUrl,
                            fileName = repo.fileNameTemplate(release.tagName),
                            stable = true
                        )
                    } else {
                        val downloadUrl = buildFallbackDownloadUrl(repo, release.tagName)
                        ModLoaderVersion(
                            version = release.tagName,
                            url = downloadUrl,
                            fileName = repo.fileNameTemplate(release.tagName),
                            stable = true
                        )
                    }
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "获取 $modLoaderName 版本失败", e)
            emptyList()
        }
    }

    /**
     * 从 GitHub API 获取 Releases
     */
    private fun fetchGitHubReleases(owner: String, repo: String): List<GitHubRelease> {
        val url = "$GITHUB_API/$owner/$repo/releases?per_page=$FETCH_COUNT"
        
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "RotatingartLauncher")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode != 200) {
                AppLog.w(TAG, "GitHub API 返回 ${conn.responseCode}")
                return emptyList()
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                .use { it.readText() }

            return parseReleasesJson(response)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解析 GitHub Releases JSON
     */
    private fun parseReleasesJson(json: String): List<GitHubRelease> {
        val releases = mutableListOf<GitHubRelease>()
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            
            val assets = mutableListOf<GitHubAsset>()
            val assetsArray = obj.optJSONArray("assets")
            if (assetsArray != null) {
                for (j in 0 until assetsArray.length()) {
                    val assetObj = assetsArray.getJSONObject(j)
                    assets.add(GitHubAsset(
                        name = assetObj.optString("name", ""),
                        downloadUrl = assetObj.optString("browser_download_url", ""),
                        size = assetObj.optLong("size", 0)
                    ))
                }
            }

            releases.add(GitHubRelease(
                tagName = obj.optString("tag_name", ""),
                name = obj.optString("name", ""),
                prerelease = obj.optBoolean("prerelease", false),
                draft = obj.optBoolean("draft", false),
                assets = assets
            ))
        }

        return releases
    }

    /**
     * 构建备用下载链接（某些项目使用固定格式）
     */
    private fun buildFallbackDownloadUrl(repo: ModLoaderRepo, version: String): String {
        return when (repo) {
            ModLoaderRepo.TMODLOADER -> 
                "https://github.com/tModLoader/tModLoader/releases/download/$version/tModLoader.zip"
            ModLoaderRepo.SMAPI -> 
                "https://github.com/Pathoschild/SMAPI/releases/download/$version/SMAPI-${version.removePrefix("v")}-installer.zip"
            ModLoaderRepo.EVEREST -> 
                "https://github.com/EverestAPI/Everest/releases/download/$version/main.zip"
        }
    }
}
