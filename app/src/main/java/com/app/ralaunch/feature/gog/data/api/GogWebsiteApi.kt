package com.app.ralaunch.feature.gog.data.api

import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.data.GogConstants
import com.app.ralaunch.feature.gog.data.model.*
import com.app.ralaunch.core.common.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * GOG 网站 API 客户端
 * 处理用户数据、游戏列表、产品信息等
 * 借鉴 lgogdownloader 的 website.h 设计
 */
class GogWebsiteApi(private val authClient: GogAuthClient) {

    private val galaxyApi = GogGalaxyApi(authClient)

    // ==================== 用户数据 ====================

    /**
     * 获取用户数据 JSON
     */
    @Throws(IOException::class)
    fun getUserData(): JSONObject = getResponseJson(GogConstants.USER_DATA_URL)

    /**
     * 获取用户信息
     */
    @Throws(IOException::class)
    fun getUserInfo(): GogUserInfo? {
        val userData = getUserData()
        if (userData.length() == 0) return null

        val username = userData.optString("username", authClient.localize(R.string.gog_user))
        val email = userData.optString("email", "")
        val userId = userData.optString("userId", "")
        var avatarUrl = ""

        try {
            if (userData.has("avatar")) {
                avatarUrl = userData.getString("avatar")
                if (avatarUrl.isNotEmpty() && !avatarUrl.startsWith("http")) {
                    avatarUrl = when {
                        avatarUrl.startsWith("//") -> "https:$avatarUrl"
                        avatarUrl.startsWith("/") -> "https://images.gog.com$avatarUrl"
                        else -> avatarUrl
                    }
                }
                if (avatarUrl.isNotEmpty() && !avatarUrl.contains(".jpg") && !avatarUrl.contains(".png")) {
                    avatarUrl = "${avatarUrl}_100.jpg"
                }
            }
        } catch (e: org.json.JSONException) {
            AppLogger.error(TAG, "解析用户头像失败", e)
        }

        return GogUserInfo(username, email, avatarUrl, userId)
    }

    // ==================== 游戏列表 ====================

    /**
     * 获取拥有的游戏列表
     */
    @Throws(IOException::class)
    fun getOwnedGames(): List<GogGame> {
        val accessToken = authClient.getAccessToken() ?: throw IOException(authClient.localize(R.string.gog_error_not_logged_in))
        val games = mutableListOf<GogGame>()
        var page = 1

        while (true) {
            val url = "${GogConstants.GAMES_URL}?mediaType=1&page=$page"
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
                conn.readTimeout = GogConstants.READ_TIMEOUT_MS

                if (conn.responseCode == 200) {
                    val response = readResponse(conn.inputStream)
                    val json = JSONObject(response)
                    val products = json.getJSONArray("products")

                    for (i in 0 until products.length()) {
                        val product = products.getJSONObject(i)
                        games.add(GogGame.fromJson(
                            id = product.getLong("id"),
                            title = product.getString("title"),
                            image = product.optString("image", ""),
                            url = product.optString("url", "")
                        ))
                    }

                    if (page >= json.getInt("totalPages")) break
                    page++
                } else break
            } catch (e: Exception) {
                AppLogger.error(TAG, "获取游戏列表失败", e)
                break
            } finally {
                conn.disconnect()
            }
        }

        AppLogger.info(TAG, "获取到 ${games.size} 个游戏")
        return games
    }

    /**
     * 获取拥有的游戏 ID 列表
     */
    @Throws(IOException::class)
    fun getOwnedGamesIds(): List<String> {
        return getOwnedGames().map { it.id.toString() }
    }

    // ==================== 产品信息 ====================

    /**
     * 获取产品信息 JSON
     */
    @Throws(IOException::class)
    fun getProductInfo(productId: String): JSONObject {
        val url = "${GogConstants.PRODUCT_API_URL}/$productId?expand=downloads,expanded_dlcs,description,screenshots,videos,related_products,changelog"
        return getResponseJson(url)
    }

    /**
     * 获取游戏详情
     */
    @Throws(IOException::class)
    fun getGameDetails(productId: String): GogGameDetails {
        val json = getProductInfo(productId)
        if (json.length() == 0) throw IOException(authClient.localize(R.string.gog_error_cannot_get_game_info))

        val title = json.optString("title", "")
        val gamename = json.optString("slug", "")

        var icon = ""
        var logo = ""
        try {
            json.optJSONObject("images")?.let { images ->
                images.optString("icon").takeIf { it.isNotEmpty() }?.let { icon = "https:$it" }
                images.optString("logo").takeIf { it.isNotEmpty() }?.let {
                    logo = "https:$it".replace("_glx_logo.jpg", ".jpg")
                }
            }
        } catch (e: org.json.JSONException) {
            AppLogger.error(TAG, "解析游戏图标失败", e)
        }

        val changelog = json.optString("changelog", "")

        val installers = mutableListOf<GogGameFile>()
        val extras = mutableListOf<GogGameFile>()
        val patches = mutableListOf<GogGameFile>()

        try {
            json.optJSONObject("downloads")?.let { downloads ->
                downloads.optJSONArray("installers")?.let {
                    installers.addAll(parseGameFiles(it, "installer", gamename))
                }
                downloads.optJSONArray("bonus_content")?.let {
                    extras.addAll(parseGameFiles(it, "extra", gamename))
                }
                downloads.optJSONArray("patches")?.let {
                    patches.addAll(parseGameFiles(it, "patch", gamename))
                }
            }
        } catch (e: org.json.JSONException) {
            AppLogger.error(TAG, "解析游戏下载列表失败", e)
        }

        // 解析 DLC
        val dlcs = mutableListOf<GogGameDetails>()
        json.optJSONArray("expanded_dlcs")?.let { dlcsArray ->
            for (i in 0 until dlcsArray.length()) {
                try {
                    val dlcJson = dlcsArray.getJSONObject(i)
                    val dlcId = dlcJson.optString("id", "")
                    // 这里可以添加所有权检查
                    val dlcDetails = parseGameDetailsFromJson(dlcJson, gamename, title)
                    if (dlcDetails.getTotalFiles() > 0) {
                        dlcs.add(dlcDetails)
                    }
                } catch (e: Exception) {
                    AppLogger.error(TAG, "解析 DLC 失败", e)
                }
            }
        }

        return GogGameDetails(
            productId = json.optString("id", productId),
            gamename = gamename,
            title = title,
            icon = icon,
            logo = logo,
            changelog = changelog,
            installers = installers,
            extras = extras,
            patches = patches,
            dlcs = dlcs
        )
    }

    private fun parseGameDetailsFromJson(
        json: JSONObject,
        gamenameBasegame: String,
        titleBasegame: String
    ): GogGameDetails {
        val title = json.optString("title", "")
        val gamename = json.optString("slug", "")

        val installers = mutableListOf<GogGameFile>()
        val extras = mutableListOf<GogGameFile>()
        val patches = mutableListOf<GogGameFile>()

        json.optJSONObject("downloads")?.let { downloads ->
            downloads.optJSONArray("installers")?.let {
                installers.addAll(parseGameFiles(it, "installer", gamename))
            }
            downloads.optJSONArray("bonus_content")?.let {
                extras.addAll(parseGameFiles(it, "extra", gamename))
            }
            downloads.optJSONArray("patches")?.let {
                patches.addAll(parseGameFiles(it, "patch", gamename))
            }
        }

        return GogGameDetails(
            productId = json.optString("id", ""),
            gamename = gamename,
            title = title,
            installers = installers,
            extras = extras,
            patches = patches,
            gamenameBasegame = gamenameBasegame,
            titleBasegame = titleBasegame
        )
    }

    private fun parseGameFiles(filesArray: JSONArray, type: String, gamename: String): List<GogGameFile> {
        val files = mutableListOf<GogGameFile>()

        for (i in 0 until filesArray.length()) {
            try {
                val fileNode = filesArray.getJSONObject(i)

                val count = fileNode.optInt("count", 0)
                val totalSize = fileNode.optLong("total_size", 0)
                if (count == 0 && totalSize == 0L) continue

                val name = fileNode.optString("name", "")
                val version = fileNode.optString("version", "")
                val language = fileNode.optString("language", "en")
                val os = fileNode.optString("os", "")

                // 只处理 Linux 平台
                if (!os.equals("linux", ignoreCase = true)) continue

                val filesArr = fileNode.optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    val firstFile = filesArr.getJSONObject(0)
                    val size = firstFile.optLong("size", 0)
                    var manualUrl = firstFile.optString("manual_url", "")
                    var path = firstFile.optString("path", "")

                    val downlinkJsonUrl = firstFile.optString("downlink", "")
                    if (downlinkJsonUrl.isNotEmpty()) {
                        try {
                            val downlinkJson = getResponseJson(downlinkJsonUrl)
                            val downlinkUrl = downlinkJson.optString("downlink", "")
                            if (downlinkUrl.isNotEmpty()) {
                                manualUrl = downlinkUrl
                                path = galaxyApi.getPathFromDownlinkUrl(downlinkUrl, gamename)
                            }
                        } catch (e: Exception) {
                            AppLogger.warn(TAG, "解析 downlink 失败: ${e.message}")
                        }
                    }

                    files.add(GogGameFile(
                        id = firstFile.optString("id", ""),
                        name = name,
                        version = version,
                        language = language,
                        os = os,
                        type = type,
                        size = size,
                        manualUrl = manualUrl,
                        path = path,
                        gamename = gamename,
                        platform = GogConstants.Platform.fromCode(os),
                        languageId = GogConstants.Language.fromCode(language),
                        galaxyDownlinkJsonUrl = downlinkJsonUrl
                    ))
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "解析游戏文件失败", e)
            }
        }
        return files
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
                AppLogger.warn(TAG, "API请求失败，响应码: ${conn.responseCode}")
                return JSONObject()
            }
        } catch (e: Exception) {
            if (e is IOException) throw e
            AppLogger.error(TAG, "获取JSON响应失败: $urlString", e)
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
        private const val TAG = "GogWebsiteApi"
    }
}
