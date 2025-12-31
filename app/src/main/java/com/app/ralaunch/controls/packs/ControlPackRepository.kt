package com.app.ralaunch.controls.packs

import android.content.Context
import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 控件包远程仓库服务
 * 负责从远程仓库获取控件包列表、下载控件包等操作
 * 
 * 仓库结构 (GitHub Raw):
 * - repository.json          (仓库索引)
 * - packs/
 *   - {pack_id}/
 *     - manifest.json        (控件包元数据)
 *     - {pack_id}.ralpack    (打包后的控件包)
 *     - preview_1.png        (预览图)
 */
class ControlPackRepositoryService(private val context: Context) {
    
    companion object {
        private const val TAG = "ControlPackRepoService"
        
        /** GitHub 仓库地址 */
        const val REPO_URL_GITHUB = "https://raw.githubusercontent.com/RotatingArtDev/RAL-ControlPacks/main"
        
        /** Gitee 国内镜像地址 */
        const val REPO_URL_GITEE = "https://gitee.com/daohei/RAL-ControlPacks/raw/main"
        
        /** 仓库索引文件名 */
        const val REPO_INDEX_FILE = "repository.json"
        
        /** 连接超时 (毫秒) */
        private const val CONNECT_TIMEOUT = 15000
        
        /** 读取超时 (毫秒) */
        private const val READ_TIMEOUT = 30000
        
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        
        /**
         * 判断是否是中文环境
         */
        fun isChinese(context: Context): Boolean {
            val locale = context.resources.configuration.locales[0]
            return locale.language == "zh"
        }
        
        /**
         * 获取默认仓库 URL（根据语言自动选择）
         */
        fun getDefaultRepoUrl(context: Context): String {
            return if (isChinese(context)) REPO_URL_GITEE else REPO_URL_GITHUB
        }
    }
    
    /** 当前仓库 URL */
    var repoUrl: String = getDefaultRepoUrl(context)
    
    /** 缓存的仓库索引 */
    private var cachedRepository: ControlPackRepository? = null
    private var cacheTimestamp: Long = 0
    private val cacheValidDuration = 5 * 60 * 1000L // 5分钟缓存
    
    /**
     * 下载进度回调
     */
    interface DownloadProgressListener {
        fun onProgress(downloaded: Long, total: Long, percent: Int)
        fun onComplete(file: File)
        fun onError(error: String)
    }
    
    /**
     * 获取仓库索引
     */
    suspend fun fetchRepository(forceRefresh: Boolean = false): Result<ControlPackRepository> {
        // 检查缓存
        if (!forceRefresh && cachedRepository != null && 
            System.currentTimeMillis() - cacheTimestamp < cacheValidDuration) {
            return Result.success(cachedRepository!!)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$repoUrl/$REPO_INDEX_FILE")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }
                
                val content = connection.inputStream.bufferedReader().readText()
                val repository = json.decodeFromString<ControlPackRepository>(content)
                
                // 更新缓存
                cachedRepository = repository
                cacheTimestamp = System.currentTimeMillis()
                
                AppLogger.info(TAG, "Fetched repository: ${repository.packs.size} packs")
                Result.success(repository)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to fetch repository", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取单个控件包的详细信息
     */
    suspend fun fetchPackInfo(packId: String): Result<ControlPackInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$repoUrl/packs/$packId/manifest.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }
                
                val content = connection.inputStream.bufferedReader().readText()
                val info = json.decodeFromString<ControlPackInfo>(content)
                
                Result.success(info)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to fetch pack info: $packId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 下载控件包
     */
    suspend fun downloadPack(
        packInfo: ControlPackInfo,
        listener: DownloadProgressListener? = null
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                // 确定下载 URL
                val downloadUrl = if (packInfo.downloadUrl.isNotEmpty()) {
                    packInfo.downloadUrl
                } else {
                    "$repoUrl/packs/${packInfo.id}/${packInfo.id}${ControlPackManager.PACK_EXTENSION}"
                }
                
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    listener?.onError("HTTP $responseCode")
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }
                
                val totalSize = connection.contentLengthLong
                
                // 保存到下载目录
                val packManager = ControlPackManager(context)
                val downloadFile = File(packManager.downloadsDir, "${packInfo.id}${ControlPackManager.PACK_EXTENSION}")
                
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(downloadFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded: Long = 0
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            
                            val percent = if (totalSize > 0) {
                                (downloaded * 100 / totalSize).toInt()
                            } else {
                                -1
                            }
                            listener?.onProgress(downloaded, totalSize, percent)
                        }
                    }
                }
                
                listener?.onComplete(downloadFile)
                AppLogger.info(TAG, "Downloaded pack to: ${downloadFile.absolutePath}")
                Result.success(downloadFile)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to download pack: ${packInfo.id}", e)
                listener?.onError(e.message ?: "Unknown error")
                Result.failure(e)
            }
        }
    }
    
    /**
     * 下载控件包预览图
     */
    suspend fun downloadPreviewImage(packId: String, imageName: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$repoUrl/packs/$packId/$imageName")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }
                
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val previewDir = File(cacheDir, "pack_previews")
                previewDir.mkdirs()
                
                val previewFile = File(previewDir, "${packId}_$imageName")
                
                connection.inputStream.use { input ->
                    FileOutputStream(previewFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Result.success(previewFile)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to download preview: $packId/$imageName", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 下载并安装控件包
     */
    suspend fun downloadAndInstall(
        packInfo: ControlPackInfo,
        packManager: ControlPackManager,
        listener: DownloadProgressListener? = null
    ): Result<ControlPackInfo> {
        // 下载
        val downloadResult = downloadPack(packInfo, listener)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull()!!)
        }
        
        val packFile = downloadResult.getOrNull()!!
        
        // 安装
        val installResult = packManager.installPack(packFile)
        
        // 清理临时文件
        packFile.delete()
        
        return installResult
    }
    
    /**
     * 检查控件包更新
     */
    suspend fun checkForUpdates(packManager: ControlPackManager): List<ControlPackInfo> {
        val repoResult = fetchRepository()
        if (repoResult.isFailure) return emptyList()
        
        val repository = repoResult.getOrNull()!!
        val updates = mutableListOf<ControlPackInfo>()
        
        val installedPacks = packManager.getInstalledPacks()
        
        for (remotePack in repository.packs) {
            val installedPack = installedPacks.find { it.id == remotePack.id }
            if (installedPack != null && remotePack.versionCode > installedPack.versionCode) {
                updates.add(remotePack)
            }
        }
        
        return updates
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        cachedRepository = null
        cacheTimestamp = 0
        
        // 清除预览图缓存
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val previewDir = File(cacheDir, "pack_previews")
        previewDir.deleteRecursively()
    }
}

