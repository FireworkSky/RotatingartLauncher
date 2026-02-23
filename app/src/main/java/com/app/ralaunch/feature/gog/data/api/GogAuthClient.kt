package com.app.ralaunch.feature.gog.data.api

import android.content.Context
import androidx.annotation.StringRes
import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.data.GogConstants
import com.app.ralaunch.core.common.util.AppLogger
import org.json.JSONObject
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets

/**
 * GOG 认证客户端
 * 处理登录、令牌管理、两步验证
 * 借鉴 lgogdownloader 的 website.h 设计
 */
class GogAuthClient(context: Context) {
    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        GogConstants.PREF_NAME, Context.MODE_PRIVATE
    )
    
    private val cookies = mutableMapOf<String, String>()
    
    var twoFactorCallback: TwoFactorCallback? = null

    fun interface TwoFactorCallback {
        fun requestSecurityCode(type: String): String?
    }

    internal fun localize(@StringRes resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    // ==================== 公共 API ====================
    
    /**
     * 使用凭据登录
     */
    @Throws(IOException::class)
    fun login(username: String, password: String): Boolean {
        val authFormToken = getAuthFormToken() ?: run {
            AppLogger.error(TAG, "无法获取登录表单令牌")
            return false
        }

        val authCode = loginAndGetAuthCode(username, password, authFormToken) ?: return false
        return exchangeToken(authCode)
    }

    /**
     * 是否已登录
     */
    fun isLoggedIn(): Boolean = getAccessToken() != null

    /**
     * 登出
     */
    fun logout() {
        prefs.edit().clear().apply()
        cookies.clear()
    }

    /**
     * 获取访问令牌
     */
    fun getAccessToken(): String? {
        val expiry = prefs.getLong(GogConstants.PREF_TOKEN_EXPIRY, 0)
        if (System.currentTimeMillis() >= expiry - 60000) {
            if (!refreshToken()) return null
        }
        return prefs.getString(GogConstants.PREF_ACCESS_TOKEN, null)
    }

    /**
     * 使用授权码换取令牌 (用于 WebView 登录)
     * @param authCode 从 OAuth 回调获取的授权码
     * @return 是否成功
     */
    fun exchangeCodeForToken(authCode: String): Boolean {
        return exchangeToken(authCode)
    }

    /**
     * 刷新令牌
     */
    fun refreshToken(): Boolean {
        val refreshToken = prefs.getString(GogConstants.PREF_REFRESH_TOKEN, null) ?: return false

        return try {
            val postData = buildString {
                append("client_id=").append(URLEncoder.encode(GogConstants.CLIENT_ID, "UTF-8"))
                append("&client_secret=").append(URLEncoder.encode(GogConstants.CLIENT_SECRET, "UTF-8"))
                append("&grant_type=refresh_token")
                append("&refresh_token=").append(URLEncoder.encode(refreshToken, "UTF-8"))
            }

            val conn = URL(GogConstants.AUTH_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
                conn.readTimeout = GogConstants.READ_TIMEOUT_MS

                conn.outputStream.use { it.write(postData.toByteArray(StandardCharsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val response = readResponse(conn.inputStream)
                    val json = JSONObject(response)
                    saveTokens(json)
                    true
                } else false
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "令牌刷新失败", e)
            false
        }
    }

    // ==================== 内部方法 ====================

    @Throws(IOException::class)
    private fun getAuthFormToken(): String? {
        val authUrl = "${GogConstants.AUTH_FORM_URL}?client_id=${GogConstants.CLIENT_ID}" +
                "&redirect_uri=${URLEncoder.encode(GogConstants.REDIRECT_URI, "UTF-8")}" +
                "&response_type=code&layout=default&brand=gog"

        val conn = URL(authUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = GogConstants.READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

            saveAllCookies(conn)
            val html = readResponse(conn.inputStream)

            return extractFormToken(html, "name=\"login[_token]\" value=\"", "\"")
                ?: extractFormToken(html, "name='login[_token]' value='", "'")
        } catch (e: Exception) {
            AppLogger.error(TAG, "无法从登录表单提取令牌", e)
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun extractFormToken(html: String, pattern: String, endChar: String): String? {
        val start = html.indexOf(pattern)
        if (start == -1) return null
        val tokenStart = start + pattern.length
        val tokenEnd = html.indexOf(endChar, tokenStart)
        return if (tokenEnd != -1) html.substring(tokenStart, tokenEnd) else null
    }

    @Throws(IOException::class)
    private fun loginAndGetAuthCode(username: String, password: String, token: String): String? {
        val postData = buildString {
            append("login%5Busername%5D=").append(URLEncoder.encode(username, "UTF-8"))
            append("&login%5Bpassword%5D=").append(URLEncoder.encode(password, "UTF-8"))
            append("&login%5Blogin%5D=")
            append("&login%5B_token%5D=").append(URLEncoder.encode(token, "UTF-8"))
        }

        val conn = URL(GogConstants.LOGIN_CHECK_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Referer", "https://auth.gog.com/auth")
            conn.setRequestProperty("Origin", "https://login.gog.com")
            getCookieHeader()?.let { conn.setRequestProperty("Cookie", it) }
            conn.doOutput = true
            conn.instanceFollowRedirects = false
            conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = GogConstants.READ_TIMEOUT_MS

            conn.outputStream.use { it.write(postData.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = conn.responseCode
            saveAllCookies(conn)

            return when (responseCode) {
                302, 303 -> {
                    var location = conn.getHeaderField("Location") ?: return null
                    if (location.startsWith("/")) location = "https://login.gog.com$location"

                    when {
                        location.contains("two_step") -> handleTwoStepAuth(location, "email")
                        location.contains("totp") -> handleTwoStepAuth(location, "totp")
                        location.contains("code=") -> extractCodeFromUrl(location)
                        location.contains("/login") -> {
                            throw IOException(localize(R.string.gog_error_login_failed_check_credentials))
                        }
                        else -> followRedirectsUntilCode(location)
                    }
                }
                200 -> {
                    val response = readResponse(conn.inputStream)
                    if (response.contains("error") || response.contains("invalid")) {
                        throw IOException(localize(R.string.gog_error_login_failed_wrong_credentials))
                    }
                    null
                }
                else -> throw IOException(localize(R.string.gog_error_login_failed_response_code, responseCode))
            }
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun handleTwoStepAuth(redirectUrl: String, type: String): String? {
        val callback = twoFactorCallback ?: run {
            AppLogger.error(TAG, "需要两步验证，但未设置回调")
            return null
        }

        val conn = URL(redirectUrl).openConnection() as HttpURLConnection
        val html: String
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", GogConstants.LOGIN_CHECK_URL)
            getCookieHeader()?.let { conn.setRequestProperty("Cookie", it) }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = GogConstants.READ_TIMEOUT_MS

            val responseCode = conn.responseCode
            saveAllCookies(conn)

            when (responseCode) {
                302, 303 -> throw IOException(localize(R.string.gog_error_two_factor_redirect))
                200 -> html = readResponse(conn.inputStream)
                else -> throw IOException(localize(R.string.gog_error_cannot_access_two_factor))
            }
        } finally {
            conn.disconnect()
        }

        val tokenFieldName = if (type == "email") {
            "second_step_authentication[_token]"
        } else {
            "two_factor_authentication[_token]"
        }

        val tokenPattern = "name=\"$tokenFieldName\" value=\""
        val tokenStart = html.indexOf(tokenPattern)
        if (tokenStart == -1) return null

        val valueStart = tokenStart + tokenPattern.length
        val tokenEnd = html.indexOf("\"", valueStart)
        if (tokenEnd == -1) return null

        val verificationToken = html.substring(valueStart, tokenEnd)
        val securityCode = callback.requestSecurityCode(type) ?: return null

        return submitTwoFactorCode(type, securityCode, verificationToken)
    }

    @Throws(IOException::class)
    private fun submitTwoFactorCode(type: String, code: String, token: String): String? {
        val url: String
        val postData: String

        if (type == "email") {
            url = GogConstants.TWO_STEP_URL
            if (code.length != 4) return null
            postData = buildString {
                append("second_step_authentication%5Btoken%5D%5Bletter_1%5D=").append(code[0])
                append("&second_step_authentication%5Btoken%5D%5Bletter_2%5D=").append(code[1])
                append("&second_step_authentication%5Btoken%5D%5Bletter_3%5D=").append(code[2])
                append("&second_step_authentication%5Btoken%5D%5Bletter_4%5D=").append(code[3])
                append("&second_step_authentication%5Bsend%5D=")
                append("&second_step_authentication%5B_token%5D=").append(URLEncoder.encode(token, "UTF-8"))
            }
        } else {
            url = GogConstants.TOTP_URL
            if (code.length != 6) return null
            postData = buildString {
                for (i in 0..5) {
                    append("two_factor_authentication%5Btoken%5D%5Bletter_${i + 1}%5D=").append(code[i])
                    if (i < 5) append("&")
                }
                append("&two_factor_authentication%5Bsend%5D=")
                append("&two_factor_authentication%5B_token%5D=").append(URLEncoder.encode(token, "UTF-8"))
            }
        }

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", GogConstants.TWO_STEP_URL)
            getCookieHeader()?.let { conn.setRequestProperty("Cookie", it) }
            conn.doOutput = true
            conn.instanceFollowRedirects = false

            conn.outputStream.use { it.write(postData.toByteArray(StandardCharsets.UTF_8)) }

            return if (conn.responseCode == 302 || conn.responseCode == 303) {
                var location = conn.getHeaderField("Location") ?: return null
                if (location.startsWith("/")) location = "https://login.gog.com$location"
                followRedirectsUntilCode(location)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun followRedirectsUntilCode(initialUrl: String): String? {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount < GogConstants.MAX_REDIRECTS) {
            if (currentUrl.contains("code=")) {
                return extractCodeFromUrl(currentUrl)
            }

            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.instanceFollowRedirects = false
                conn.connectTimeout = GogConstants.CONNECT_TIMEOUT_MS
                conn.readTimeout = GogConstants.READ_TIMEOUT_MS
                getCookieHeader()?.let { conn.setRequestProperty("Cookie", it) }

                val responseCode = conn.responseCode
                saveAllCookies(conn)

                when (responseCode) {
                    301, 302, 303, 307, 308 -> {
                        var location = conn.getHeaderField("Location") ?: return null
                        if (location.startsWith("/")) {
                            val baseUrl = currentUrl.substring(0, currentUrl.indexOf("/", 8))
                            location = baseUrl + location
                        }
                        currentUrl = location
                        redirectCount++
                    }
                    200 -> return null
                    else -> return null
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun extractCodeFromUrl(url: String): String? {
        val codeStart = url.indexOf("code=")
        if (codeStart == -1) return null
        val valueStart = codeStart + 5
        var codeEnd = url.indexOf("&", valueStart)
        if (codeEnd == -1) codeEnd = url.indexOf("#", valueStart)
        return if (codeEnd == -1) url.substring(valueStart) else url.substring(valueStart, codeEnd)
    }

    private fun exchangeToken(code: String): Boolean {
        try {
            val postData = buildString {
                append("client_id=").append(URLEncoder.encode(GogConstants.CLIENT_ID, "UTF-8"))
                append("&client_secret=").append(URLEncoder.encode(GogConstants.CLIENT_SECRET, "UTF-8"))
                append("&grant_type=authorization_code")
                append("&code=").append(URLEncoder.encode(code, "UTF-8"))
                append("&redirect_uri=").append(URLEncoder.encode(GogConstants.REDIRECT_URI, "UTF-8"))
            }

            val conn = URL(GogConstants.AUTH_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true

                conn.outputStream.use { it.write(postData.toByteArray(StandardCharsets.UTF_8)) }

                return if (conn.responseCode == 200) {
                    val response = readResponse(conn.inputStream)
                    val json = JSONObject(response)
                    saveTokens(json)
                    true
                } else false
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "令牌交换错误", e)
            return false
        }
    }

    private fun saveTokens(json: JSONObject) {
        val accessToken = json.getString("access_token")
        val refreshToken = json.getString("refresh_token")
        val expiresIn = json.getLong("expires_in")
        val expiry = System.currentTimeMillis() + (expiresIn * 1000)

        prefs.edit()
            .putString(GogConstants.PREF_ACCESS_TOKEN, accessToken)
            .putString(GogConstants.PREF_REFRESH_TOKEN, refreshToken)
            .putLong(GogConstants.PREF_TOKEN_EXPIRY, expiry)
            .apply()
    }

    // ==================== Cookie 管理 ====================

    private fun saveCookie(setCookieHeader: String?) {
        if (setCookieHeader.isNullOrEmpty()) return
        val semicolon = setCookieHeader.indexOf(';')
        val cookieNameValue = if (semicolon != -1) setCookieHeader.substring(0, semicolon) else setCookieHeader
        val equals = cookieNameValue.indexOf('=')
        if (equals != -1) {
            cookies[cookieNameValue.substring(0, equals)] = cookieNameValue.substring(equals + 1)
        }
    }

    private fun saveAllCookies(conn: HttpURLConnection) {
        conn.headerFields["Set-Cookie"]?.forEach { saveCookie(it) }
    }

    private fun getCookieHeader(): String? {
        if (cookies.isEmpty()) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    // ==================== 工具方法 ====================

    @Throws(IOException::class)
    private fun readResponse(inputStream: InputStream?): String {
        inputStream ?: return ""
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            buildString { var line: String?; while (reader.readLine().also { line = it } != null) append(line) }
        }
    }

    companion object {
        private const val TAG = "GogAuthClient"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
