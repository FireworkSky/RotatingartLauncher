package com.app.ralaunch.feature.gog.data.api

import com.app.ralaunch.feature.gog.data.GogConstants
import com.app.ralaunch.feature.gog.data.model.*
import com.app.ralaunch.core.logging.AppLog
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * GOG Galaxy API 客户端
 * 处理产品构建、清单、依赖等 Galaxy 特定功能
 * 借鉴 lgogdownloader 的 galaxyapi.h 设计
 */
class GogGalaxyApi(private val authClient: GogAuthClient) {

    // ==================== 产品构建 ====================

    /**
     * 获取产品构建信息
     */
    @Throws(IOException::class)
    fun getProductBuilds(
        productId: String,
        platform: String = "linux",
        generation: String = "2"
    ): JSONObject {
        val url = "${GogConstants.CONTENT_SYSTEM_URL}/products/$productId/os/$platform/builds?generation=$generation"
        return getResponseJson(url)
    }

    /**
     * 获取构建列表
     */
    @Throws(IOException::class)
    fun getBuildList(productId: String, platform: String = "linux"): List<GogBuildInfo> {
        val json = getProductBuilds(productId, platform)
        val builds = mutableListOf<GogBuildInfo>()
        
        val items = json.optJSONArray("items") ?: return builds
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            builds.add(GogBuildInfo(
                buildId = item.optString("build_id", ""),
                productId = item.optString("product_id", productId),
                os = item.optString("os", platform),
                branch = item.optString("branch", null),
                versionName = item.optString("version_name", ""),
                generation = item.optInt("generation", 2),
                isAvailable = item.optBoolean("is_available", true),
                datePublished = item.optString("date_published", "")
            ))
        }
        return builds
    }

    // ==================== 清单 ====================

    /**
     * 获取 V1 清单
     */
    @Throws(IOException::class)
    fun getManifestV1(manifestUrl: String): JSONObject = getResponseJson(manifestUrl)

    /**
     * 获取 V1 清单（通过参数构建 URL）
     */
    @Throws(IOException::class)
    fun getManifestV1(
        productId: String,
        buildId: String,
        manifestId: String = "repository",
        platform: String = "linux"
    ): JSONObject {
        val url = "${GogConstants.CDN_URL}/content-system/v1/manifests/$productId/$platform/$buildId/$manifestId.json"
        return getResponseJson(url)
    }

    /**
     * 获取 V2 清单
     */
    @Throws(IOException::class)
    fun getManifestV2(manifestHash: String, isDependency: Boolean = false): JSONObject {
        val hash = if (manifestHash.isNotEmpty() && !manifestHash.contains("/")) {
            hashToGalaxyPath(manifestHash)
        } else manifestHash

        val url = if (isDependency) {
            "${GogConstants.CDN_URL}/content-system/v2/dependencies/meta/$hash"
        } else {
            "${GogConstants.CDN_URL}/content-system/v2/meta/$hash"
        }
        return getResponseJson(url)
    }

    // ==================== Depot 项目 ====================

    /**
     * 获取 Depot 项目列表
     */
    @Throws(IOException::class)
    fun getDepotItems(hash: String, isDependency: Boolean = false): List<GogDepotItem> {
        val json = getManifestV2(hash, isDependency)
        val items = mutableListOf<GogDepotItem>()
        
        val depot = json.optJSONObject("depot") ?: return items

        // 处理小文件容器
        depot.optJSONObject("smallFilesContainer")?.let { sfc ->
            val chunksArray = sfc.optJSONArray("chunks")
            if (chunksArray != null && chunksArray.length() > 0) {
                val chunks = mutableListOf<GogDepotChunk>()
                var totalCompressed = 0L
                var totalUncompressed = 0L

                for (i in 0 until chunksArray.length()) {
                    val chunkJson = chunksArray.getJSONObject(i)
                    val chunk = GogDepotChunk(
                        md5Compressed = chunkJson.optString("compressedMd5", ""),
                        md5Uncompressed = chunkJson.optString("md5", ""),
                        sizeCompressed = chunkJson.optLong("compressedSize", 0),
                        sizeUncompressed = chunkJson.optLong("size", 0),
                        offsetCompressed = totalCompressed,
                        offsetUncompressed = totalUncompressed
                    )
                    totalCompressed += chunk.sizeCompressed
                    totalUncompressed += chunk.sizeUncompressed
                    chunks.add(chunk)
                }

                items.add(GogDepotItem(
                    path = "galaxy_smallfilescontainer",
                    chunks = chunks,
                    totalSizeCompressed = totalCompressed,
                    totalSizeUncompressed = totalUncompressed,
                    md5 = sfc.optString("md5", chunks.firstOrNull()?.md5Uncompressed ?: ""),
                    isDependency = isDependency,
                    isSmallFilesContainer = true
                ))
            }
        }

        // 处理常规项目
        val itemsArray = depot.optJSONArray("items")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemJson = itemsArray.getJSONObject(i)
                val chunksArray = itemJson.optJSONArray("chunks") ?: continue

                val chunks = mutableListOf<GogDepotChunk>()
                var totalCompressed = 0L
                var totalUncompressed = 0L

                for (j in 0 until chunksArray.length()) {
                    val chunkJson = chunksArray.getJSONObject(j)
                    val chunk = GogDepotChunk(
                        md5Compressed = chunkJson.optString("compressedMd5", ""),
                        md5Uncompressed = chunkJson.optString("md5", ""),
                        sizeCompressed = chunkJson.optLong("compressedSize", 0),
                        sizeUncompressed = chunkJson.optLong("size", 0),
                        offsetCompressed = totalCompressed,
                        offsetUncompressed = totalUncompressed
                    )
                    totalCompressed += chunk.sizeCompressed
                    totalUncompressed += chunk.sizeUncompressed
                    chunks.add(chunk)
                }

                var path = itemJson.optString("path", "")
                path = path.replace("\\", "/")

                val sfcRef = itemJson.optJSONObject("sfcRef")

                items.add(GogDepotItem(
                    path = path,
                    chunks = chunks,
                    totalSizeCompressed = totalCompressed,
                    totalSizeUncompressed = totalUncompressed,
                    md5 = itemJson.optString("md5", chunks.firstOrNull()?.md5Uncompressed ?: ""),
                    isDependency = isDependency,
                    isInSFC = sfcRef != null,
                    sfcOffset = sfcRef?.optLong("offset", 0) ?: 0,
                    sfcSize = sfcRef?.optLong("size", 0) ?: 0
                ))
            }
        }

        return items
    }

    // ==================== 链接 ====================

    /**
     * 获取安全下载链接
     */
    @Throws(IOException::class)
    fun getSecureLink(productId: String, path: String): JSONObject {
        val url = "${GogConstants.CONTENT_SYSTEM_URL}/products/$productId/secure_link?generation=2&path=$path&_version=2"
        return getResponseJson(url)
    }

    /**
     * 获取依赖下载链接
     */
    @Throws(IOException::class)
    fun getDependencyLink(path: String): JSONObject {
        val encodedPath = URLEncoder.encode(path, "UTF-8")
        val url = "${GogConstants.CONTENT_SYSTEM_URL}/open_link?path=$encodedPath"
        return getResponseJson(url)
    }

    // ==================== 依赖 ====================

    /**
     * 获取依赖仓库信息
     */
    @Throws(IOException::class)
    fun getDependenciesJson(): JSONObject {
        val repository = getResponseJson("${GogConstants.DEPENDENCIES_URL}?generation=2")
        return try {
            if (repository.has("repository_manifest")) {
                getResponseJson(repository.getString("repository_manifest"))
            } else JSONObject()
        } catch (e: Exception) {
            AppLog.e(TAG, "获取依赖信息失败", e)
            JSONObject()
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 将哈希转换为 Galaxy 路径格式
     */
    fun hashToGalaxyPath(hash: String): String {
        return if (hash.length >= 4 && !hash.contains("/")) {
            "${hash.substring(0, 2)}/${hash.substring(2, 4)}/$hash"
        } else hash
    }

    /**
     * 从 downlink URL 中提取路径
     */
    fun getPathFromDownlinkUrl(downlinkUrl: String?, gamename: String): String {
        if (downlinkUrl.isNullOrEmpty()) return ""
        return try {
            var urlDecoded = java.net.URLDecoder.decode(downlinkUrl, "UTF-8")
            if (urlDecoded.endsWith("/")) urlDecoded = urlDecoded.dropLast(1)

            var filenameStart = urlDecoded.lastIndexOf('/').let { if (it != -1) it + 1 else 0 }
            val gamenameIdx = urlDecoded.indexOf("/$gamename/")
            if (gamenameIdx != -1) filenameStart = gamenameIdx

            var filenameEnd = urlDecoded.length
            val qIdx = urlDecoded.indexOf('?')
            if (qIdx != -1) {
                filenameEnd = qIdx
                val tokenPos = urlDecoded.indexOf("&token=")
                val accessTokenPos = urlDecoded.indexOf("&access_token=")
                if (tokenPos != -1 && accessTokenPos != -1) {
                    filenameEnd = minOf(tokenPos, accessTokenPos)
                } else {
                    urlDecoded.indexOf('&').takeIf { it != -1 }?.let { filenameEnd = minOf(filenameEnd, it) }
                }
            }

            var path = urlDecoded.substring(filenameStart, filenameEnd)
            if (!path.startsWith("/")) path = "/$path"
            if (!path.contains("/$gamename/")) path = "/$gamename$path"

            path.lastIndexOf('?').takeIf { it != -1 && it > path.lastIndexOf('/') }?.let {
                path = path.substring(0, it)
            }
            path
        } catch (e: Exception) {
            AppLog.e(TAG, "解析 downlink url 失败", e)
            ""
        }
    }

    // ==================== HTTP 请求 ====================

    @Throws(IOException::class)
    private fun getResponseJson(urlString: String): JSONObject {
        val accessToken = authClient.getAccessToken()
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            accessToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
            conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = GogConstants.READ_TIMEOUT_MS

            if (conn.responseCode == 200) {
                var inputStream: InputStream = conn.inputStream
                val contentEncoding = conn.getHeaderField("Content-Encoding")
                if (contentEncoding?.contains("gzip") == true) {
                    inputStream = GZIPInputStream(inputStream)
                }
                val response = readResponse(inputStream)
                return if (response.isEmpty()) JSONObject() else JSONObject(response)
            } else {
                AppLog.w(TAG, "API请求失败，响应码: ${conn.responseCode}")
                return JSONObject()
            }
        } catch (e: Exception) {
            if (e is IOException) throw e
            AppLog.e(TAG, "获取JSON响应失败: $urlString", e)
            return JSONObject()
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun readResponse(inputStream: InputStream?): String {
        inputStream ?: return ""
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            buildString { var line: String?; while (reader.readLine().also { line = it } != null) append(line) }
        }
    }

    companion object {
        private const val TAG = "GogGalaxyApi"
    }
}
