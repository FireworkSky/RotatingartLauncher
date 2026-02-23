package com.app.ralaunch.feature.controls.packs

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.core.common.JsonHttpRepositoryClient
import com.app.ralaunch.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection

/**
 * 控件包远程仓库服务
 * 负责从远程仓库获取控件包列表、下载控件包等操作
 * 
 * 仓库结构 (GitHub Raw):
 * - repository.json          (仓库索引)
 * - packs/
 *   - {pack_id}/
 *     - manifest.json        (控件包元数据)
 *     - {pack_id}.zip        (打包后的控件包)
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

        val result = JsonHttpRepositoryClient.getJson<ControlPackRepository>(
            urlString = "$repoUrl/$REPO_INDEX_FILE",
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT,
            readTimeoutMs = READ_TIMEOUT
        )

        result.getOrNull()?.let { repository ->
            cachedRepository = repository
            cacheTimestamp = System.currentTimeMillis()
            AppLogger.info(TAG, "Fetched repository: ${repository.packs.size} packs")
        }

        result.exceptionOrNull()?.let { error ->
            AppLogger.error(TAG, "Failed to fetch repository", error)
        }

        return result
    }
    
    /**
     * 获取单个控件包的详细信息
     */
    suspend fun fetchPackInfo(packId: String): Result<ControlPackInfo> {
        val result = JsonHttpRepositoryClient.getJson<ControlPackInfo>(
            urlString = "$repoUrl/packs/$packId/manifest.json",
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT,
            readTimeoutMs = READ_TIMEOUT
        )

        result.exceptionOrNull()?.let { error ->
            AppLogger.error(TAG, "Failed to fetch pack info: $packId", error)
        }
        return result
    }
    
    /**
     * 下载单个文件
     */
    private suspend fun downloadFile(urlString: String, targetFile: File): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = JsonHttpRepositoryClient.openConnection(
                    urlString = urlString,
                    connectTimeoutMs = CONNECT_TIMEOUT,
                    readTimeoutMs = READ_TIMEOUT
                )
                
                try {
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext Result.failure(Exception("HTTP $responseCode"))
                    }

                    targetFile.parentFile?.mkdirs()

                    connection.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    Result.success(targetFile)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 下载控件包（从文件夹结构下载）
     * 仓库结构：packs/{pack_id}/manifest.json, layout.json, assets/...
     */
    suspend fun downloadPack(
        packInfo: ControlPackInfo,
        listener: DownloadProgressListener? = null
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val packManager = ControlPackManager(context)
                val packDir = File(packManager.packsDir, packInfo.id)
                
                // 如果已存在，先删除
                if (packDir.exists()) {
                    packDir.deleteRecursively()
                }
                packDir.mkdirs()
                
                val baseUrl = "$repoUrl/packs/${packInfo.id}"
                var downloadedSize = 0L
                val totalSize = packInfo.fileSize.takeIf { it > 0 } ?: 100L
                
                // 1. 下载 manifest.json
                AppLogger.info(TAG, "Downloading manifest.json...")
                val manifestFile = File(packDir, ControlPackInfo.MANIFEST_FILE_NAME)
                val manifestResult = downloadFile("$baseUrl/${ControlPackInfo.MANIFEST_FILE_NAME}", manifestFile)
                if (manifestResult.isFailure) {
                    packDir.deleteRecursively()
                    listener?.onError(
                        context.getString(R.string.pack_download_failed, ControlPackInfo.MANIFEST_FILE_NAME)
                    )
                    return@withContext Result.failure(manifestResult.exceptionOrNull()!!)
                }
                downloadedSize += manifestFile.length()
                listener?.onProgress(downloadedSize, totalSize, 30)
                
                // 2. 下载 layout.json
                AppLogger.info(TAG, "Downloading layout.json...")
                val layoutFile = File(packDir, ControlPackInfo.LAYOUT_FILE_NAME)
                val layoutResult = downloadFile("$baseUrl/${ControlPackInfo.LAYOUT_FILE_NAME}", layoutFile)
                if (layoutResult.isFailure) {
                    packDir.deleteRecursively()
                    listener?.onError(
                        context.getString(R.string.pack_download_failed, ControlPackInfo.LAYOUT_FILE_NAME)
                    )
                    return@withContext Result.failure(layoutResult.exceptionOrNull()!!)
                }
                downloadedSize += layoutFile.length()
                listener?.onProgress(downloadedSize, totalSize, 70)
                
                // 3. 尝试下载 icon.png（可选）
                val iconFile = File(packDir, ControlPackInfo.ICON_FILE_NAME)
                downloadFile("$baseUrl/${ControlPackInfo.ICON_FILE_NAME}", iconFile)
                
                // 4. 下载预览图（可选）
                packInfo.previewImagePaths.forEach { previewPath ->
                    val previewFile = File(packDir, previewPath)
                    downloadFile("$baseUrl/$previewPath", previewFile)
                }
                
                // 5. 下载 assets 纹理文件
                AppLogger.info(TAG, "assetFiles count: ${packInfo.assetFiles.size}, list: ${packInfo.assetFiles.take(3)}")
                if (packInfo.assetFiles.isNotEmpty()) {
                    AppLogger.info(TAG, "Downloading ${packInfo.assetFiles.size} asset files...")
                    val assetsDir = File(packDir, ControlPackInfo.ASSETS_DIR_NAME)
                    assetsDir.mkdirs()
                    
                    packInfo.assetFiles.forEachIndexed { index, assetPath ->
                        val assetFile = File(assetsDir, assetPath)
                        assetFile.parentFile?.mkdirs()
                        val assetUrl = "$baseUrl/${ControlPackInfo.ASSETS_DIR_NAME}/$assetPath"
                        val result = downloadFile(assetUrl, assetFile)
                        if (result.isSuccess) {
                            AppLogger.info(TAG, "  Downloaded: $assetPath")
                        } else {
                            AppLogger.warn(TAG, "  Failed to download: $assetPath")
                        }
                        
                        // 更新进度 (70% - 100%)
                        val assetProgress = 70 + (30 * (index + 1) / packInfo.assetFiles.size)
                        listener?.onProgress(downloadedSize, totalSize, assetProgress)
                    }
                }
                
                listener?.onProgress(totalSize, totalSize, 100)
                listener?.onComplete(packDir)
                
                AppLogger.info(TAG, "Downloaded pack to folder: ${packDir.absolutePath}")
                Result.success(packDir)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to download pack: ${packInfo.id}", e)
                listener?.onError(e.message ?: context.getString(R.string.common_unknown_error))
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
                val connection = JsonHttpRepositoryClient.openConnection(
                    urlString = "$repoUrl/packs/$packId/$imageName",
                    connectTimeoutMs = CONNECT_TIMEOUT,
                    readTimeoutMs = READ_TIMEOUT
                )
                
                try {
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
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to download preview: $packId/$imageName", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 下载并安装控件包
     * 现在直接下载到安装目录，不需要额外安装步骤
     */
    suspend fun downloadAndInstall(
        packInfo: ControlPackInfo,
        packManager: ControlPackManager,
        listener: DownloadProgressListener? = null
    ): Result<ControlPackInfo> {
        // 下载（直接下载到 packs 目录）
        val downloadResult = downloadPack(packInfo, listener)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull()!!)
        }
        
        // 验证安装是否成功
        val installedInfo = packManager.getPackInfo(packInfo.id)
        if (installedInfo != null) {
            AppLogger.info(TAG, "Pack installed successfully: ${installedInfo.name}")
            return Result.success(installedInfo)
        }
        
        return Result.failure(Exception("Installation verification failed"))
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
