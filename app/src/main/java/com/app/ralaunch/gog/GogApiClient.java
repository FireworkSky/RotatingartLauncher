package com.app.ralaunch.gog;

import android.content.Context;
import android.content.SharedPreferences;
import com.app.ralaunch.utils.AppLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GOG API 客户端
 * 提供 GOG 平台的认证、游戏列表获取、下载等功能
 */
public class GogApiClient {
    private static final String TAG = "GogApiClient";

    // GOG API 端点
    private static final String AUTH_URL = "https://auth.gog.com/token";
    private static final String GAMES_URL = "https://embed.gog.com/account/getFilteredProducts";

    // OAuth 客户端信息
    private static final String CLIENT_ID = "46899977096215655";
    private static final String CLIENT_SECRET = "9d85c43b1482497dbbce61f6e4aa173a433796eeae2ca8c5f6129f2dc4de46d9";
    private static final String REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client";

    // 重试配置 (参考 C++ lgogdownloader)
    private static final int MAX_RETRIES = 3; // 最大重试次数
    private static final int RETRY_DELAY_MS = 1000; // 重试延迟 (毫秒)

    private static final String PREF_NAME = "gog_auth";
    private static final String PREF_ACCESS_TOKEN = "access_token";
    private static final String PREF_REFRESH_TOKEN = "refresh_token";
    private static final String PREF_TOKEN_EXPIRY = "token_expiry";

    private final Context context;
    private final SharedPreferences prefs;
    private final java.net.CookieManager cookieManager;
    private final java.util.Map<String, String> cookies = new java.util.HashMap<>(); // 手动管理所有cookies

    public GogApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 启用 Cookie 管理（参考 C++ CURL 的 cookie jar）
        this.cookieManager = new java.net.CookieManager();
        this.cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
        java.net.CookieHandler.setDefault(cookieManager);
    }

    // 两步验证回调接口
    public interface TwoFactorCallback {
        String requestSecurityCode(String type); // "email" 或 "totp"
    }

    // 可重试操作接口
    private interface RetryableOperation<T> {
        T execute() throws IOException;
    }

    // 下载进度回调
    public interface DownloadProgress {
        void onProgress(long downloaded, long total);
    }

    private TwoFactorCallback twoFactorCallback;

    /**
     * 设置两步验证回调
     */
    public void setTwoFactorCallback(TwoFactorCallback callback) {
        this.twoFactorCallback = callback;
    }

    /**
     * 保存cookie（从Set-Cookie header）
     */
    private void saveCookie(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return;
        }

        // 提取cookie名称和值（忽略过期时间等属性）
        int semicolon = setCookieHeader.indexOf(';');
        String cookieNameValue = semicolon != -1 ? setCookieHeader.substring(0, semicolon) : setCookieHeader;

        int equals = cookieNameValue.indexOf('=');
        if (equals != -1) {
            String name = cookieNameValue.substring(0, equals);
            String value = cookieNameValue.substring(equals + 1);
            cookies.put(name, value);
        }
    }

    /**
     * 保存所有cookies（从HttpURLConnection响应）
     * 修复：getHeaderField只返回第一个Set-Cookie，需要获取所有的
     */
    private void saveAllCookies(HttpURLConnection conn) {
        java.util.List<String> setCookieHeaders = conn.getHeaderFields().get("Set-Cookie");
        if (setCookieHeaders != null) {
            for (String setCookie : setCookieHeaders) {
                saveCookie(setCookie);
            }
        }
    }

    /**
     * 获取所有cookies的Cookie header字符串
     */
    private String getCookieHeader() {
        if (cookies.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 执行带重试的操作 (参考 C++ CurlHandleGetResponse)
     * 对 DNS 解析失败、超时等错误进行重试
     */
    private <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) throws IOException {
        int retries = 0;
        IOException lastException = null;

        while (retries <= MAX_RETRIES) {
            try {
                return operation.execute();
            } catch (java.net.UnknownHostException e) {
                lastException = e;
            } catch (java.net.SocketTimeoutException e) {
                lastException = e;
            } catch (java.net.ConnectException e) {
                lastException = e;
            } catch (IOException e) {
                // 其他 IO 异常，不重试
                throw e;
            }

            retries++;
            if (retries <= MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
        }

        // 所有重试都失败
        AppLogger.error(TAG, operationName + " - All retries failed");
        if (lastException instanceof java.net.UnknownHostException) {
            throw new IOException("Cannot connect to GOG server - DNS resolution failed. Please check your network connection or try using VPN", lastException);
        } else if (lastException instanceof java.net.SocketTimeoutException) {
            throw new IOException("Connection to GOG server timed out. Please check your network connection", lastException);
        } else {
            throw new IOException("Network request failed", lastException);
        }
    }

    /**
     * 使用当前认证信息下载文件
     */
    public void downloadWithAuth(String urlString, File targetFile, DownloadProgress progress) throws IOException {
        if (targetFile == null) throw new IOException("Invalid target file");
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Cannot create download directory: " + parent.getAbsolutePath());
            }
        }

        executeWithRetry(() -> {
            String accessToken = getAccessToken();
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            try {
                conn.setInstanceFollowRedirects(true);
                if (accessToken != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                }
                String cookieHeader = getCookieHeader();
                if (cookieHeader != null) {
                    conn.setRequestProperty("Cookie", cookieHeader);
                }
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

                int code = conn.getResponseCode();
                if (code >= 400) {
                    throw new IOException("Download failed, HTTP " + code);
                }

                long total = conn.getContentLengthLong();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    long downloaded = 0;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                        downloaded += len;
                        if (progress != null) {
                            progress.onProgress(downloaded, total);
                        }
                    }
                }
            } finally {
                conn.disconnect();
            }
            return null;
        }, "download " + targetFile.getName());
    }

    /**
     * 使用用户名和密码登录
     */
    public boolean loginWithCredentials(String username, String password) throws IOException {
        // 第一步：获取登录表单的 _token
        String authFormToken = getAuthFormToken();
        if (authFormToken == null) {
            AppLogger.error(TAG, "无法获取登录表单令牌");
            return false;
        }

        // 第二步：提交登录，获取 auth code
        String authCode = loginAndGetAuthCode(username, password, authFormToken);
        if (authCode == null) {
            return false;
        }

        // 第三步：使用 auth code 换取访问令牌
        return exchangeLoginToken(authCode);
    }

    /**
     * 获取登录表单的 _token
     */
    private String getAuthFormToken() throws IOException {
        return executeWithRetry(() -> {
            String authUrl = "https://auth.gog.com/auth?client_id=" + CLIENT_ID +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                    "&response_type=code&layout=default&brand=gog";

            HttpURLConnection conn = (HttpURLConnection) new URL(authUrl).openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

                saveAllCookies(conn);

                String html = readResponse(conn.getInputStream());

                String tokenPattern = "name=\"login[_token]\" value=\"";
                int tokenStart = html.indexOf(tokenPattern);
                if (tokenStart != -1) {
                    tokenStart += tokenPattern.length();
                    int tokenEnd = html.indexOf("\"", tokenStart);
                    if (tokenEnd != -1) {
                        String token = html.substring(tokenStart, tokenEnd);
                        return token;
                    }
                }

                String altPattern1 = "name='login[_token]' value='";
                int altStart1 = html.indexOf(altPattern1);
                if (altStart1 != -1) {
                    altStart1 += altPattern1.length();
                    int altEnd1 = html.indexOf("'", altStart1);
                    if (altEnd1 != -1) {
                        String token = html.substring(altStart1, altEnd1);
                        return token;
                    }
                }
            } catch (Exception e) {
                AppLogger.error(TAG, "无法从登录表单提取令牌", e);
            } finally {
                conn.disconnect();
            }
            
            AppLogger.error(TAG, "无法从登录表单提取令牌");
            return null;
        }, "获取登录表单令牌");
    }

    /**
     * 提交登录并获取 auth code (支持两步验证)
     */
    private String loginAndGetAuthCode(String username, String password, String token) throws IOException {
        String postData = "login%5Busername%5D=" + URLEncoder.encode(username, "UTF-8") +
                "&login%5Bpassword%5D=" + URLEncoder.encode(password, "UTF-8") +
                "&login%5Blogin%5D=" +
                "&login%5B_token%5D=" + URLEncoder.encode(token, "UTF-8");

        AppLogger.info(TAG, "提交登录请求，用户名: " + username);

        HttpURLConnection conn = (HttpURLConnection) new URL("https://login.gog.com/login_check").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Referer", "https://auth.gog.com/auth");
            conn.setRequestProperty("Origin", "https://login.gog.com");

            // 发送所有cookies
            String cookieHeader = getCookieHeader();
            if (cookieHeader != null) {
                conn.setRequestProperty("Cookie", cookieHeader);
                AppLogger.info(TAG, "发送 Cookies: " + cookieHeader);
            } else {
                AppLogger.warn(TAG, "未找到任何 Cookies - 这可能导致登录失败");
            }

            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false); // 不自动跟随重定向
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            AppLogger.info(TAG, "登录响应码: " + responseCode);

            // 保存登录响应中的所有新cookies（修复：使用saveAllCookies）
            saveAllCookies(conn);

            if (responseCode == 302 || responseCode == 303) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    AppLogger.info(TAG, "重定向到: " + location);

                    // 将相对URL转换为绝对URL
                    if (location.startsWith("/")) {
                        location = "https://login.gog.com" + location;
                        AppLogger.info(TAG, "转换为绝对URL: " + location);
                    }

                    // 检查是否需要两步验证
                    if (location.contains("two_step")) {
                        return handleTwoStepAuth(location, "email");
                    } else if (location.contains("totp")) {
                        return handleTwoStepAuth(location, "totp");
                    } else if (location.contains("code=")) {
                        // 直接获取到 code，无需两步验证
                        return extractCodeFromUrl(location);
                    } else if (location.contains("/login")) {
                        // 重定向回登录页面，说明登录失败
                        AppLogger.error(TAG, "Login failed - incorrect username or password");
                        throw new IOException("Login failed - Please check username and password");
                    } else {
                        // 其他重定向，可能需要跟随重定向链
                        AppLogger.info(TAG, "跟随登录重定向链");
                        return followRedirectsUntilCode(location);
                    }
                }
            } else if (responseCode == 200) {
                // 可能返回错误页面
                String response = readResponse(conn.getInputStream());
                AppLogger.warn(TAG, "登录返回200但未重定向，响应长度: " + response.length());
                if (response.contains("error") || response.contains("invalid")) {
                    AppLogger.error(TAG, "Login failed - server returned error");
                    throw new IOException("Login failed - Incorrect username or password");
                }
            }

            AppLogger.error(TAG, "Login failed - unexpected response code: " + responseCode);
            throw new IOException("Login failed - Response code: " + responseCode);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 处理两步验证
     * @param redirectUrl 两步验证页面 URL
     * @param type "email" 或 "totp"
     */
    private String handleTwoStepAuth(String redirectUrl, String type) throws IOException {
        if (twoFactorCallback == null) {
            AppLogger.error(TAG, "需要两步验证，但未设置回调");
            return null;
        }

        // 获取两步验证页面的 token
        HttpURLConnection conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
        String html;
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Referer", "https://login.gog.com/login_check");

            // 发送所有cookies
            String cookieHeader = getCookieHeader();
            if (cookieHeader != null) {
                conn.setRequestProperty("Cookie", cookieHeader);
                AppLogger.info(TAG, "发送 Cookies 到两步验证页面: " + cookieHeader);
            } else {
                AppLogger.warn(TAG, "警告：没有任何 Cookies!");
            }

            conn.setInstanceFollowRedirects(false); // 不自动跟随重定向，手动处理
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            AppLogger.info(TAG, "两步验证页面响应码: " + responseCode);

            // 保存响应中的所有新cookies（修复：使用saveAllCookies）
            saveAllCookies(conn);

            // 处理重定向
            if (responseCode == 302 || responseCode == 303) {
                String location = conn.getHeaderField("Location");
                AppLogger.warn(TAG, "Two-factor authentication page redirected to: " + location);
                throw new IOException("Two-factor authentication page redirected, session may have expired");
            }

            if (responseCode != 200) {
                AppLogger.error(TAG, "Two-factor authentication page returned unexpected response code: " + responseCode);
                throw new IOException("Cannot access two-factor authentication page");
            }

            html = readResponse(conn.getInputStream());
        } finally {
            conn.disconnect();
        }

        // 调试：输出HTML长度
        AppLogger.info(TAG, "两步验证页面HTML长度: " + html.length() + " 字符");

        // 提取验证表单的 _token
        String tokenFieldName;
        if ("email".equals(type)) {
            tokenFieldName = "second_step_authentication[_token]";
        } else {
            tokenFieldName = "two_factor_authentication[_token]";
        }

        // 使用字面字符串搜索（不需要正则转义）
        String tokenPattern = "name=\"" + tokenFieldName + "\" value=\"";
        AppLogger.info(TAG, "搜索token模式: " + tokenPattern);

        int tokenStart = html.indexOf(tokenPattern);
        if (tokenStart == -1) {
            // 查找是否存在该字段名
            if (html.contains(tokenFieldName)) {
                AppLogger.warn(TAG, "找到字段名但无法匹配完整模式");
                int pos = html.indexOf(tokenFieldName);
                int start = Math.max(0, pos - 50);
                int end = Math.min(html.length(), pos + 150);
                AppLogger.info(TAG, "上下文: " + html.substring(start, end));
            } else {
                AppLogger.warn(TAG, "HTML中不包含: " + tokenFieldName);
                // 输出HTML的前500个字符来调试
                if (html.length() > 0) {
                    int previewLen = Math.min(500, html.length());
                    AppLogger.info(TAG, "HTML预览: " + html.substring(0, previewLen));
                }
            }
            AppLogger.error(TAG, "无法找到两步验证令牌");
            return null;
        }

        AppLogger.info(TAG, "找到token模式，位置: " + tokenStart);
        tokenStart += tokenPattern.length();
        int tokenEnd = html.indexOf("\"", tokenStart);

        if (tokenEnd == -1) {
            AppLogger.error(TAG, "无法找到token结束引号，tokenStart=" + tokenStart);
            return null;
        }

        String verificationToken = html.substring(tokenStart, tokenEnd);
        AppLogger.info(TAG, "成功提取验证token: " + verificationToken.substring(0, Math.min(20, verificationToken.length())) + "...");

        // 请求用户输入安全码
        AppLogger.info(TAG, "准备显示两步验证对话框，类型: " + type);
        String securityCode = twoFactorCallback.requestSecurityCode(type);
        AppLogger.info(TAG, "用户输入验证码完成，长度: " + (securityCode != null ? securityCode.length() : 0));
        if (securityCode == null || securityCode.isEmpty()) {
            AppLogger.error(TAG, "未提供安全码");
            return null;
        }

        // 提交验证码
        return submitTwoFactorCode(type, securityCode, verificationToken);
    }

    /**
     * 提交两步验证码
     */
    private String submitTwoFactorCode(String type, String code, String token) throws IOException {
        String url;
        String postData;

        if ("email".equals(type)) {
            // 邮箱验证 (4位)
            url = "https://login.gog.com/login/two_step";
            if (code.length() != 4) {
                AppLogger.error(TAG, "邮箱验证码应为4位");
                return null;
            }
            postData = "second_step_authentication%5Btoken%5D%5Bletter_1%5D=" + code.charAt(0) +
                    "&second_step_authentication%5Btoken%5D%5Bletter_2%5D=" + code.charAt(1) +
                    "&second_step_authentication%5Btoken%5D%5Bletter_3%5D=" + code.charAt(2) +
                    "&second_step_authentication%5Btoken%5D%5Bletter_4%5D=" + code.charAt(3) +
                    "&second_step_authentication%5Bsend%5D=" +
                    "&second_step_authentication%5B_token%5D=" + URLEncoder.encode(token, "UTF-8");
        } else {
            // TOTP 验证器 (6位)
            url = "https://login.gog.com/login/two_factor/totp";
            if (code.length() != 6) {
                AppLogger.error(TAG, "TOTP验证码应为6位");
                return null;
            }
            postData = "two_factor_authentication%5Btoken%5D%5Bletter_1%5D=" + code.charAt(0) +
                    "&two_factor_authentication%5Btoken%5D%5Bletter_2%5D=" + code.charAt(1) +
                    "&two_factor_authentication%5Btoken%5D%5Bletter_3%5D=" + code.charAt(2) +
                    "&two_factor_authentication%5Btoken%5D%5Bletter_4%5D=" + code.charAt(3) +
                    "&two_factor_authentication%5Btoken%5D%5Bletter_5%5D=" + code.charAt(4) +
                    "&two_factor_authentication%5Btoken%5D%5Bletter_6%5D=" + code.charAt(5) +
                    "&two_factor_authentication%5Bsend%5D=" +
                    "&two_factor_authentication%5B_token%5D=" + URLEncoder.encode(token, "UTF-8");
        }

        AppLogger.info(TAG, "提交两步验证码，类型: " + type);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://login.gog.com/login/two_step");

            // 发送所有cookies
            String cookieHeader = getCookieHeader();
            if (cookieHeader != null) {
                conn.setRequestProperty("Cookie", cookieHeader);
                AppLogger.info(TAG, "发送 Cookies 到两步验证提交: " + cookieHeader);
            }

            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            AppLogger.info(TAG, "两步验证响应码: " + responseCode);

            if (responseCode == 302 || responseCode == 303) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    AppLogger.info(TAG, "两步验证重定向到: " + location);

                    // 将相对URL转换为绝对URL
                    if (location.startsWith("/")) {
                        location = "https://login.gog.com" + location;
                    }

                    // 跟随重定向链直到找到 code 参数（参考 C++ 实现）
                    return followRedirectsUntilCode(location);
                }
            }

            AppLogger.error(TAG, "两步验证提交失败: " + responseCode);
            return null;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 跟随重定向链直到找到包含 code 的URL
     * 参考 C++ website.cpp 的重定向处理逻辑
     */
    private String followRedirectsUntilCode(String initialUrl) throws IOException {
        String currentUrl = initialUrl;
        int maxRedirects = 10; // 防止无限重定向
        int redirectCount = 0;

        while (redirectCount < maxRedirects) {
            AppLogger.info(TAG, "跟随重定向 [" + (redirectCount + 1) + "/" + maxRedirects + "]: " + currentUrl);

            // 检查当前URL是否包含 code 参数
            if (currentUrl.contains("code=")) {
                AppLogger.info(TAG, "找到包含 code 的URL");
                return extractCodeFromUrl(currentUrl);
            }

            // 发送GET请求获取下一个重定向
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");
                conn.setInstanceFollowRedirects(false); // 手动处理重定向
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                // 发送所有cookies
                String cookieHeader = getCookieHeader();
                if (cookieHeader != null) {
                    conn.setRequestProperty("Cookie", cookieHeader);
                }

                int responseCode = conn.getResponseCode();
                AppLogger.info(TAG, "重定向响应码: " + responseCode);

                // 保存新的cookies
                saveAllCookies(conn);

                if (responseCode == 302 || responseCode == 303 || responseCode == 301 || responseCode == 307 || responseCode == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        // 将相对URL转换为绝对URL
                        if (location.startsWith("/")) {
                            String baseUrl = currentUrl.substring(0, currentUrl.indexOf("/", 8)); // 提取协议+域名
                            location = baseUrl + location;
                        }

                        AppLogger.info(TAG, "下一个重定向: " + location);
                        currentUrl = location;
                        redirectCount++;
                    } else {
                        AppLogger.error(TAG, "重定向响应没有 Location 头");
                        return null;
                    }
                } else if (responseCode == 200) {
                    // 可能最终页面就是包含code的页面，但没有通过重定向
                    AppLogger.warn(TAG, "到达最终页面(200)，但URL中没有code参数");
                    return null;
                } else {
                    AppLogger.error(TAG, "重定向链中遇到非预期响应码: " + responseCode);
                    return null;
                }
            } finally {
                conn.disconnect();
            }
        }

        AppLogger.error(TAG, "重定向次数超过最大限制: " + maxRedirects);
        return null;
    }

    /**
     * 从 URL 中提取 code 参数
     */
    private String extractCodeFromUrl(String url) {
        int codeStart = url.indexOf("code=");
        if (codeStart == -1) {
            return null;
        }

        codeStart += 5; // "code=".length()
        int codeEnd = url.indexOf("&", codeStart);
        if (codeEnd == -1) {
            codeEnd = url.indexOf("#", codeStart);
        }
        if (codeEnd == -1) {
            return url.substring(codeStart);
        }
        return url.substring(codeStart, codeEnd);
    }

    /**
     * 使用登录令牌换取访问令牌
     */
    private boolean exchangeLoginToken(String code) throws IOException {
        String postData = "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8") +
                "&grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(code, "UTF-8") +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(AUTH_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                String response = readResponse(conn.getInputStream());
                JSONObject json = new JSONObject(response);

                saveTokens(json);
                AppLogger.info(TAG, "登录成功");
                return true;
            } else {
                AppLogger.error(TAG, "令牌交换失败: " + conn.getResponseCode());
                return false;
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "令牌交换错误", e);
            return false;
        } finally {
            conn.disconnect();
        }
    }

    private void saveTokens(JSONObject json) throws Exception {
        String accessToken = json.getString("access_token");
        String refreshToken = json.getString("refresh_token");
        long expiresIn = json.getLong("expires_in");
        long expiry = System.currentTimeMillis() + (expiresIn * 1000);

        prefs.edit()
            .putString(PREF_ACCESS_TOKEN, accessToken)
            .putString(PREF_REFRESH_TOKEN, refreshToken)
            .putLong(PREF_TOKEN_EXPIRY, expiry)
            .apply();
    }

    /**
     * 刷新令牌
     */
    private boolean refreshToken() {
        String refreshToken = prefs.getString(PREF_REFRESH_TOKEN, null);
        if (refreshToken == null) return false;

        try {
            String postData = "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                    "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8") +
                    "&grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) new URL(AUTH_URL).openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    String response = readResponse(conn.getInputStream());
                    JSONObject json = new JSONObject(response);
                    saveTokens(json);
                    return true;
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "令牌刷新失败", e);
        }
        return false;
    }

    private String getAccessToken() {
        long expiry = prefs.getLong(PREF_TOKEN_EXPIRY, 0);
        if (System.currentTimeMillis() >= expiry - 60000) {
            if (!refreshToken()) return null;
        }
        return prefs.getString(PREF_ACCESS_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    /**
     * 发送 HTTP GET 请求并返回 JSON 响应
     * 对应 C++: galaxyAPI::getResponseJson()
     */
    private JSONObject getResponseJson(String urlString) throws IOException {
        return executeWithRetry(() -> {
            String accessToken = getAccessToken();

            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            try {
                if (accessToken != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                }
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == 200) {
                    // 检查是否是 gzip 压缩
                    InputStream inputStream = conn.getInputStream();
                    String contentEncoding = conn.getHeaderField("Content-Encoding");
                    if (contentEncoding != null && contentEncoding.contains("gzip")) {
                        inputStream = new java.util.zip.GZIPInputStream(inputStream);
                    }

                    String response = readResponse(inputStream);
                    if (response.isEmpty()) {
                        return new JSONObject();
                    }
                    return new JSONObject(response);
                }

                AppLogger.warn(TAG, "API请求失败，响应码: " + conn.getResponseCode());
                return new JSONObject();
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                AppLogger.error(TAG, "获取JSON响应失败: " + urlString, e);
                return new JSONObject();
            } finally {
                conn.disconnect();
            }
        }, "获取JSON响应: " + urlString);
    }

    /**
     * 获取用户数据 (userData.json)
     * 对应 C++: galaxyAPI::getUserData()
     */
    public JSONObject getUserData() throws IOException {
        String url = "https://embed.gog.com/userData.json";
        return getResponseJson(url);
    }

    /**
     * 获取用户信息（包括头像）
     */
    public UserInfo getUserInfo() throws IOException {
        JSONObject userData = getUserData();
        if (userData == null || userData.length() == 0) {
            return null;
        }

        String username = userData.optString("username", "GOG 用户");
        String email = userData.optString("email", "");
        String avatarUrl = "";

        // 头像可能在 avatar 或 user_avatar 字段
        try {
            if (userData.has("avatar")) {
                avatarUrl = userData.getString("avatar");
                // 补全 URL（如果是相对路径）
                if (!avatarUrl.isEmpty() && !avatarUrl.startsWith("http")) {
                    if (avatarUrl.startsWith("//")) {
                        avatarUrl = "https:" + avatarUrl;
                    } else if (avatarUrl.startsWith("/")) {
                        avatarUrl = "https://images.gog.com" + avatarUrl;
                    }
                }

                // 为 GOG 图片 URL 添加尺寸后缀和扩展名（参考 lgogdownloader）
                if (!avatarUrl.isEmpty() && !avatarUrl.contains(".jpg") && !avatarUrl.contains(".png")) {
                    avatarUrl = avatarUrl + "_100.jpg";  // 添加 100x100 尺寸和 .jpg 扩展名
                }
            }
        } catch (org.json.JSONException e) {
            AppLogger.error(TAG, "解析用户头像失败", e);
        }

        AppLogger.info(TAG, "获取用户信息: " + username + ", 头像: " + avatarUrl);
        return new UserInfo(username, email, avatarUrl);
    }

    /**
     * 获取产品构建信息
     * 对应 C++: galaxyAPI::getProductBuilds()
     */
    public JSONObject getProductBuilds(String productId, String platform, String generation) throws IOException {
        String url = "https://content-system.gog.com/products/" + productId +
                    "/os/" + platform + "/builds?generation=" + generation;
        return getResponseJson(url);
    }

    /**
     * 获取产品构建信息 (默认 Linux 平台)
     */
    public JSONObject getProductBuilds(String productId) throws IOException {
        return getProductBuilds(productId, "linux", "2");
    }

    /**
     * 获取 Manifest V1
     * 对应 C++: galaxyAPI::getManifestV1()
     */
    public JSONObject getManifestV1(String productId, String buildId, String manifestId, String platform) throws IOException {
        String url = "https://cdn.gog.com/content-system/v1/manifests/" +
                    productId + "/" + platform + "/" + buildId + "/" + manifestId + ".json";
        return getResponseJson(url);
    }

    /**
     * 获取 Manifest V1 (使用直接 URL)
     */
    public JSONObject getManifestV1(String manifestUrl) throws IOException {
        return getResponseJson(manifestUrl);
    }

    /**
     * 获取 Manifest V2
     * 对应 C++: galaxyAPI::getManifestV2()
     */
    public JSONObject getManifestV2(String manifestHash, boolean isDependency) throws IOException {
        String hash = manifestHash;
        if (!hash.isEmpty() && !hash.contains("/")) {
            hash = hashToGalaxyPath(hash);
        }

        String url;
        if (isDependency) {
            url = "https://cdn.gog.com/content-system/v2/dependencies/meta/" + hash;
        } else {
            url = "https://cdn.gog.com/content-system/v2/meta/" + hash;
        }

        return getResponseJson(url);
    }

    /**
     * 获取依赖信息
     * 对应 C++: galaxyAPI::getDependenciesJson()
     */
    public JSONObject getDependenciesJson() throws IOException {
        String url = "https://content-system.gog.com/dependencies/repository?generation=2";
        JSONObject repository = getResponseJson(url);

        try {
            if (repository != null && repository.has("repository_manifest")) {
                String manifestUrl = repository.getString("repository_manifest");
                return getResponseJson(manifestUrl);
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "获取依赖信息失败", e);
        }

        return new JSONObject();
    }

    /**
     * 将哈希转换为 Galaxy 路径
     * 对应 C++: galaxyAPI::hashToGalaxyPath()
     */
    private String hashToGalaxyPath(String hash) {
        if (hash.length() < 3) {
            return hash;
        }
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash;
    }

    /**
     * 从 downlink url 中提取 secure_link 所需的 path
     * 参考 lgogdownloader C++: galaxyAPI::getPathFromDownlinkUrl()
     */
    private String getPathFromDownlinkUrl(String downlinkUrl, String gamename) {
        if (downlinkUrl == null || downlinkUrl.isEmpty()) return "";
        try {
            String urlDecoded = java.net.URLDecoder.decode(downlinkUrl, "UTF-8");
            if (urlDecoded.endsWith("/")) {
                urlDecoded = urlDecoded.substring(0, urlDecoded.length() - 1);
            }

            int filenameStart = 0;
            int lastSlash = urlDecoded.lastIndexOf('/');
            if (lastSlash != -1) {
                filenameStart = lastSlash + 1;
            }
            int gamenameIdx = urlDecoded.indexOf("/" + gamename + "/");
            if (gamenameIdx != -1) {
                filenameStart = gamenameIdx;
            }

            int filenameEnd = urlDecoded.length();
            int qIdx = urlDecoded.indexOf('?');
            if (qIdx != -1) {
                filenameEnd = qIdx;
                int tokenPos = urlDecoded.indexOf("&token=");
                int accessTokenPos = urlDecoded.indexOf("&access_token=");
                if (tokenPos != -1 && accessTokenPos != -1) {
                    filenameEnd = Math.min(tokenPos, accessTokenPos);
                } else {
                    int amp = urlDecoded.indexOf('&');
                    if (amp != -1) filenameEnd = Math.min(filenameEnd, amp);
                }
            }

            String path = urlDecoded.substring(filenameStart, filenameEnd);
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.contains("/" + gamename + "/")) {
                path = "/" + gamename + path;
            }
            // 去掉可能的问号尾巴
            int lastQ = path.lastIndexOf('?');
            if (lastQ != -1 && lastQ > path.lastIndexOf('/')) {
                path = path.substring(0, lastQ);
            }
            return path;
        } catch (Exception e) {
            AppLogger.error(TAG, "解析 downlink url 失败", e);
            return "";
        }
    }

    /**
     * 获取安全下载链接
     * 对应 C++: galaxyAPI::getSecureLink()
     */
    public JSONObject getSecureLink(String productId, String path) throws IOException {
        // 遵循 lgogdownloader C++ 实现: generation=2 & _version=2，path 直接拼接
        String url = "https://content-system.gog.com/products/" + productId +
                "/secure_link?generation=2&_version=2&path=" + path;
        return getResponseJson(url);
    }

    /**
     * 获取依赖下载链接
     * 对应 C++: galaxyAPI::getDependencyLink()
     */
    public JSONObject getDependencyLink(String path) throws IOException {
        String url = "https://content-system.gog.com/open_link?path=" +
                    URLEncoder.encode(path, "UTF-8");
        return getResponseJson(url);
    }

    /**
     * 获取产品信息
     * 对应 C++: galaxyAPI::getProductInfo()
     */
    public JSONObject getProductInfo(String productId) throws IOException {
        String url = "https://api.gog.com/products/" + productId + "?expand=downloads,expanded_dlcs,description,screenshots,videos,related_products,changelog";
        return getResponseJson(url);
    }

    /**
     * 获取游戏详细信息（包括下载列表）
     * 参考 C++: galaxyAPI::productInfoJsonToGameDetails()
     */
    public GameDetails getGameDetails(String productId) throws IOException {
        JSONObject json = getProductInfo(productId);
        if (json == null || json.length() == 0) {
            throw new IOException("Cannot get game information");
        }

        String title = json.optString("title", "");
        String gamename = json.optString("slug", "");

        // 获取图标和 logo（参考 lgogdownloader）
        String icon = "";
        String logo = "";
        try {
            if (json.has("images")) {
                JSONObject images = json.getJSONObject("images");
                if (images.has("icon")) {
                    icon = "https:" + images.getString("icon");
                }
                if (images.has("logo")) {
                    logo = "https:" + images.getString("logo");
                    // 修复 logo URL（参考 C++ 实现）
                    logo = logo.replace("_glx_logo.jpg", ".jpg");
                }
            }
        } catch (org.json.JSONException e) {
            AppLogger.error(TAG, "解析游戏图标失败", e);
        }

        // 解析下载文件列表
        List<GameFile> installers = new ArrayList<>();
        List<GameFile> extras = new ArrayList<>();
        List<GameFile> patches = new ArrayList<>();

        try {
            if (json.has("downloads")) {
                JSONObject downloads = json.getJSONObject("downloads");

                // 解析安装程序
                if (downloads.has("installers")) {
                    installers = parseGameFiles(downloads.getJSONArray("installers"), "installer", gamename);
                }

                // 解析额外内容
                if (downloads.has("bonus_content")) {
                    extras = parseGameFiles(downloads.getJSONArray("bonus_content"), "extra", gamename);
                }

                // 解析补丁
                if (downloads.has("patches")) {
                    patches = parseGameFiles(downloads.getJSONArray("patches"), "patch", gamename);
                }
            }
        } catch (org.json.JSONException e) {
            AppLogger.error(TAG, "解析游戏下载列表失败", e);
        }

        AppLogger.info(TAG, "游戏 " + title + " 包含 " +
                installers.size() + " 个安装程序, " +
                extras.size() + " 个额外内容, " +
                patches.size() + " 个补丁");

        return new GameDetails(title, gamename, icon, logo, installers, extras, patches);
    }

    /**
     * 解析游戏文件列表
     * 参考 C++: galaxyAPI::fileJsonNodeToGameFileVector()
     * 仅保留 Linux 版本的文件
     */
    private List<GameFile> parseGameFiles(JSONArray filesArray, String type, String gamename) {
        List<GameFile> files = new ArrayList<>();

        for (int i = 0; i < filesArray.length(); i++) {
            try {
                JSONObject fileNode = filesArray.getJSONObject(i);

                // 跳过 count 和 total_size 都为 0 的文件
                // https://github.com/Sude-/lgogdownloader/issues/200
                int count = fileNode.optInt("count", 0);
                long totalSize = fileNode.optLong("total_size", 0);
                if (count == 0 && totalSize == 0) {
                    continue;
                }

                String name = fileNode.optString("name", "");
                String version = fileNode.optString("version", "");
                String language = fileNode.optString("language", "en");
                String os = fileNode.optString("os", "");

                // 仅保留 Linux 版本的文件
                if (!os.equalsIgnoreCase("linux")) {
                    continue;
                }

                // 获取第一个文件的信息
                if (fileNode.has("files") && fileNode.getJSONArray("files").length() > 0) {
                    JSONObject firstFile = fileNode.getJSONArray("files").getJSONObject(0);
                    long size = firstFile.optLong("size", 0);
                    String manualUrl = firstFile.optString("manual_url", "");
                    String path = firstFile.optString("path", "");

                    String downlinkJsonUrl = firstFile.optString("downlink", "");
                    if (!downlinkJsonUrl.isEmpty()) {
                        AppLogger.debug(TAG, "[downlink] request json: " + downlinkJsonUrl);
                        try {
                            JSONObject downlinkJson = getResponseJson(downlinkJsonUrl);
                            if (downlinkJson != null) {
                                String downlinkUrl = downlinkJson.optString("downlink", "");
                                if (!downlinkUrl.isEmpty()) {
                                    // 优先使用 downlink 返回的真实下载 url
                                    manualUrl = downlinkUrl;
                                    path = getPathFromDownlinkUrl(downlinkUrl, gamename);
                                    AppLogger.debug(TAG, "[downlink] resolved for " + gamename + " file=" + name + " url=" + manualUrl + " path=" + path);
                                } else {
                                    AppLogger.warn(TAG, "[downlink] json without downlink field for " + name);
                                }
                            } else {
                                AppLogger.warn(TAG, "[downlink] empty json for " + name);
                            }
                        } catch (Exception e) {
                            AppLogger.warn(TAG, "[downlink] 解析失败: " + e.getMessage());
                        }
                    } else {
                        AppLogger.debug(TAG, "[downlink] none for file " + name + ", fallback manual=" + manualUrl + " path=" + path);
                    }

                    if (manualUrl == null || manualUrl.isEmpty()) {
                        AppLogger.warn(TAG, "未能获取 manualUrl，文件: " + name + " path: " + path);
                    }

                    files.add(new GameFile(name, version, language, os, type, size, manualUrl, path));
                }
            } catch (Exception e) {
                AppLogger.error(TAG, "解析游戏文件失败", e);
            }
        }

        return files;
    }

    /**
     * 获取拥有的游戏列表
     */
    public List<GogGame> getOwnedGames() throws IOException {
        String accessToken = getAccessToken();
        if (accessToken == null) throw new IOException("Not logged in");

        List<GogGame> games = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = GAMES_URL + "?mediaType=1&page=" + page;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            try {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);

                if (conn.getResponseCode() == 200) {
                    String response = readResponse(conn.getInputStream());
                    JSONObject json = new JSONObject(response);
                    JSONArray products = json.getJSONArray("products");

                    for (int i = 0; i < products.length(); i++) {
                        JSONObject product = products.getJSONObject(i);

                        long gameId = product.getLong("id");
                        String title = product.getString("title");

                        // 获取图标 URL 并补全 https: 前缀（参考 lgogdownloader）
                        String imageUrl = product.optString("image", "");
                        AppLogger.info(TAG, "游戏 [" + title + "] 原始图标 URL: " + imageUrl);

                        if (!imageUrl.isEmpty() && imageUrl.startsWith("//")) {
                            imageUrl = "https:" + imageUrl;
                        }

                        // 为 GOG 图片 URL 添加尺寸后缀和扩展名（参考 lgogdownloader）
                        if (!imageUrl.isEmpty() && !imageUrl.contains(".jpg") && !imageUrl.contains(".png")) {
                            imageUrl = imageUrl + "_200.jpg";  // 添加 200x200 尺寸和 .jpg 扩展名
                        }

                        AppLogger.info(TAG, "游戏 [" + title + "] 最终图标 URL: " + imageUrl);

                        games.add(new GogGame(
                            gameId,
                            title,
                            imageUrl,
                            product.optString("url", "")
                        ));
                    }

                    if (page >= json.getInt("totalPages")) break;
                    page++;
                } else {
                    break;
                }
            } catch (Exception e) {
                AppLogger.error(TAG, "获取游戏列表失败", e);
                break;
            } finally {
                conn.disconnect();
            }
        }

        AppLogger.info(TAG, "获取到 " + games.size() + " 个游戏");
        return games;
    }

    private String readResponse(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * GOG 游戏信息
     */
    public static class GogGame {
        public final long id;
        public final String title;
        public final String image;
        public final String url;

        public GogGame(long id, String title, String image, String url) {
            this.id = id;
            this.title = title;
            this.image = image;
            this.url = url;
        }
    }

    /**
     * 用户信息
     */
    public static class UserInfo {
        public final String username;
        public final String email;
        public final String avatarUrl;

        public UserInfo(String username, String email, String avatarUrl) {
            this.username = username;
            this.email = email;
            this.avatarUrl = avatarUrl;
        }
    }

    /**
     * 游戏下载文件信息
     */
    public static class GameFile {
        public final String name;           // 文件名
        public final String version;        // 版本号
        public final String language;       // 语言
        public final String os;             // 操作系统 (windows/linux/mac)
        public final String type;           // 类型 (installer/patch/langpack/extra)
        public final long size;             // 文件大小（字节）
        public final String manualUrl;      // 手动下载链接
        public final String path;           // secure_link 所需的路径

        public GameFile(String name, String version, String language, String os,
                       String type, long size, String manualUrl, String path) {
            this.name = name;
            this.version = version;
            this.language = language;
            this.os = os;
            this.type = type;
            this.size = size;
            this.manualUrl = manualUrl;
            this.path = path;
        }

        public String getSizeFormatted() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 游戏详细信息（包括下载列表）
     */
    public static class GameDetails {
        public final String title;
        public final String gamename;       // slug
        public final String icon;
        public final String logo;
        public final List<GameFile> installers;  // 安装程序
        public final List<GameFile> extras;      // 额外内容
        public final List<GameFile> patches;     // 补丁

        public GameDetails(String title, String gamename, String icon, String logo,
                          List<GameFile> installers, List<GameFile> extras, List<GameFile> patches) {
            this.title = title;
            this.gamename = gamename;
            this.icon = icon;
            this.logo = logo;
            this.installers = installers != null ? installers : new ArrayList<>();
            this.extras = extras != null ? extras : new ArrayList<>();
            this.patches = patches != null ? patches : new ArrayList<>();
        }

        public int getTotalFiles() {
            return installers.size() + extras.size() + patches.size();
        }
    }
}
