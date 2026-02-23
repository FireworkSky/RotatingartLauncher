package com.app.ralaunch.feature.gog.data

import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.data.api.GogAuthClient
import com.app.ralaunch.feature.gog.data.GogConstants
import com.app.ralaunch.feature.gog.data.model.GogGameFile
import com.app.ralaunch.core.common.util.AppLogger
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * GOG 下载管理器
 * 处理文件下载、断点续传、进度回调
 * 借鉴 lgogdownloader 的 downloader.h 设计
 */
class GogDownloader(private val authClient: GogAuthClient) {

    /**
     * 下载进度回调
     */
    fun interface DownloadProgress {
        fun onProgress(downloaded: Long, total: Long, speed: Long)
    }

    /**
     * 下载状态
     */
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float, val downloaded: Long, val total: Long, val speed: Long) : DownloadState()
        data class Completed(val file: File) : DownloadState()
        data class Failed(val error: String) : DownloadState()
        object Cancelled : DownloadState()
    }

    @Volatile
    private var isCancelled = false

    /**
     * 取消下载
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * 重置取消状态
     */
    fun reset() {
        isCancelled = false
    }

    /**
     * 下载文件（带认证）
     */
    @Throws(IOException::class)
    fun downloadWithAuth(
        urlString: String,
        targetFile: File,
        progress: DownloadProgress? = null
    ) {
        reset()
        
        targetFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IOException(authClient.localize(R.string.gog_error_cannot_create_download_dir, it.absolutePath))
            }
        }

        executeWithRetry({
            val accessToken = authClient.getAccessToken()
            val conn = URL(urlString).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = true
                accessToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.connectTimeout = GogConstants.DOWNLOAD_TIMEOUT_MS
                conn.readTimeout = GogConstants.DOWNLOAD_TIMEOUT_MS

                val code = conn.responseCode
                if (code >= 400) throw IOException(authClient.localize(R.string.gog_error_download_failed, code))

                val total = conn.contentLengthLong
                var lastTime = System.currentTimeMillis()
                var lastDownloaded = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var len: Int
                        
                        while (input.read(buffer).also { len = it } != -1) {
                            if (isCancelled) {
                                throw IOException(authClient.localize(R.string.gog_download_cancelled))
                            }
                            
                            output.write(buffer, 0, len)
                            downloaded += len
                            
                            // 计算速度
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastTime
                            if (timeDiff >= 1000) {
                                val bytesPerSecond = ((downloaded - lastDownloaded) * 1000) / timeDiff
                                progress?.onProgress(downloaded, total, bytesPerSecond)
                                lastTime = currentTime
                                lastDownloaded = downloaded
                            }
                        }
                        
                        // 最终回调
                        progress?.onProgress(downloaded, total, 0)
                    }
                }
            } finally {
                conn.disconnect()
            }
            null
        }, "download ${targetFile.name}")
    }

    /**
     * 下载游戏文件
     */
    @Throws(IOException::class)
    fun downloadGameFile(
        gameFile: GogGameFile,
        targetDir: File,
        progress: DownloadProgress? = null
    ): File {
        val fileName = gameFile.getFileName()
        val targetFile = File(targetDir, fileName)
        
        val downloadUrl = gameFile.manualUrl.ifEmpty {
            throw IOException(authClient.localize(R.string.gog_cannot_get_download_link))
        }
        
        downloadWithAuth(downloadUrl, targetFile, progress)
        return targetFile
    }

    /**
     * 带断点续传的下载
     */
    @Throws(IOException::class)
    fun downloadWithResume(
        urlString: String,
        targetFile: File,
        progress: DownloadProgress? = null
    ) {
        reset()
        
        targetFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IOException(authClient.localize(R.string.gog_error_cannot_create_download_dir, it.absolutePath))
            }
        }

        val existingSize = if (targetFile.exists()) targetFile.length() else 0L

        executeWithRetry({
            val accessToken = authClient.getAccessToken()
            val conn = URL(urlString).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = true
                accessToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                
                // 断点续传
                if (existingSize > 0) {
                    conn.setRequestProperty("Range", "bytes=$existingSize-")
                }
                
                conn.connectTimeout = GogConstants.DOWNLOAD_TIMEOUT_MS
                conn.readTimeout = GogConstants.DOWNLOAD_TIMEOUT_MS

                val code = conn.responseCode
                
                val total: Long
                val append: Boolean
                
                when (code) {
                    206 -> {
                        // 服务器支持断点续传
                        val contentRange = conn.getHeaderField("Content-Range")
                        total = if (contentRange != null && contentRange.contains("/")) {
                            contentRange.substringAfter("/").toLongOrNull() ?: (existingSize + conn.contentLengthLong)
                        } else {
                            existingSize + conn.contentLengthLong
                        }
                        append = true
                    }
                    200 -> {
                        // 服务器不支持断点续传，从头开始
                        total = conn.contentLengthLong
                        append = false
                    }
                    else -> {
                        throw IOException(authClient.localize(R.string.gog_error_download_failed, code))
                    }
                }

                var lastTime = System.currentTimeMillis()
                var lastDownloaded = if (append) existingSize else 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile, append).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = lastDownloaded
                        var len: Int
                        
                        while (input.read(buffer).also { len = it } != -1) {
                            if (isCancelled) {
                                throw IOException(authClient.localize(R.string.gog_download_cancelled))
                            }
                            
                            output.write(buffer, 0, len)
                            downloaded += len
                            
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastTime
                            if (timeDiff >= 1000) {
                                val bytesPerSecond = ((downloaded - lastDownloaded) * 1000) / timeDiff
                                progress?.onProgress(downloaded, total, bytesPerSecond)
                                lastTime = currentTime
                                lastDownloaded = downloaded
                            }
                        }
                        
                        progress?.onProgress(downloaded, total, 0)
                    }
                }
            } finally {
                conn.disconnect()
            }
            null
        }, "download ${targetFile.name}")
    }

    // ==================== 重试机制 ====================

    @Throws(IOException::class)
    private fun <T> executeWithRetry(operation: () -> T, operationName: String): T {
        var retries = 0
        var lastException: IOException? = null

        while (retries <= GogConstants.MAX_RETRIES) {
            try {
                return operation()
            } catch (e: java.net.UnknownHostException) {
                lastException = IOException(authClient.localize(R.string.gog_error_dns_failed), e)
            } catch (e: java.net.SocketTimeoutException) {
                lastException = IOException(authClient.localize(R.string.gog_error_connection_timeout), e)
            } catch (e: java.net.ConnectException) {
                lastException = IOException(authClient.localize(R.string.gog_error_network_failed), e)
            } catch (e: IOException) {
                if (isCancelled) throw e
                lastException = e
            }

            retries++
            if (retries <= GogConstants.MAX_RETRIES) {
                try {
                    Thread.sleep(GogConstants.RETRY_DELAY_MS.toLong())
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(authClient.localize(R.string.gog_error_retry_interrupted), ie)
                }
            }
        }

        AppLogger.error(TAG, "$operationName - All retries failed")
        throw lastException ?: IOException(authClient.localize(R.string.common_unknown_error))
    }

    companion object {
        private const val TAG = "GogDownloader"
        
        /**
         * 格式化下载速度
         */
        fun formatSpeed(bytesPerSecond: Long): String = when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024))
        }
        
        /**
         * 格式化文件大小
         */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
