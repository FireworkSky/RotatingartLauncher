package com.app.ralaunch.feature.sponsor

import android.content.Context
import com.app.ralaunch.core.common.JsonHttpRepositoryClient
import com.app.ralaunch.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 赞助商仓库服务
 * 负责从远程仓库获取赞助者列表
 */
class SponsorRepositoryService(private val context: Context) {

    companion object {
        private const val TAG = "SponsorRepoService"

        /** GitHub 仓库地址 */
        const val REPO_URL_GITHUB = "https://raw.githubusercontent.com/RotatingArtDev/RAL-Sponsors/main"

        /** Gitee 国内镜像地址 */
        const val REPO_URL_GITEE = "https://gitee.com/daohei/RAL-Sponsors/raw/main"

        /** 仓库索引文件名 */
        const val REPO_INDEX_FILE = "sponsors.json"

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
    private var cachedRepository: SponsorRepository? = null
    private var cacheTimestamp: Long = 0
    private val cacheValidDuration = 10 * 60 * 1000L // 10分钟缓存

    /**
     * 获取赞助商仓库数据
     * 自动回退：如果首选源失败，尝试备用源
     */
    suspend fun fetchSponsors(forceRefresh: Boolean = false): Result<SponsorRepository> {
        // 检查缓存
        if (!forceRefresh && cachedRepository != null &&
            System.currentTimeMillis() - cacheTimestamp < cacheValidDuration
        ) {
            return Result.success(cachedRepository!!)
        }

        return withContext(Dispatchers.IO) {
            // 首选源
            val primaryUrl = repoUrl
            // 备用源
            val fallbackUrl = if (primaryUrl == REPO_URL_GITEE) REPO_URL_GITHUB else REPO_URL_GITEE
            
            // 先尝试首选源
            var result = tryFetchFrom(primaryUrl)
            
            // 如果失败，尝试备用源
            if (result.isFailure) {
                AppLog.i(TAG, "Primary source failed, trying fallback: $fallbackUrl")
                result = tryFetchFrom(fallbackUrl)
            }
            
            result
        }
    }
    
    private suspend fun tryFetchFrom(baseUrl: String): Result<SponsorRepository> {
        val result = JsonHttpRepositoryClient.getJson<SponsorRepository>(
            urlString = "$baseUrl/$REPO_INDEX_FILE",
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT,
            readTimeoutMs = READ_TIMEOUT
        )

        result.getOrNull()?.let { repository ->
            cachedRepository = repository
            cacheTimestamp = System.currentTimeMillis()
            AppLog.i(TAG, "Fetched sponsors from $baseUrl: ${repository.sponsors.size} sponsors")
        }

        result.exceptionOrNull()?.let { error ->
            AppLog.e(TAG, "Failed to fetch from $baseUrl", error)
        }

        return result
    }

    /**
     * 获取按级别分组的赞助者列表
     */
    suspend fun getSponsorsByTier(forceRefresh: Boolean = false): Result<Map<SponsorTier, List<Sponsor>>> {
        val result = fetchSponsors(forceRefresh)
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }

        val repository = result.getOrNull()!!
        val tierMap = repository.tiers.associateBy { it.id }
        
        // 按级别分组
        val groupedSponsors = repository.sponsors
            .filter { tierMap.containsKey(it.tier) }
            .groupBy { tierMap[it.tier]!! }
            .toSortedMap(compareByDescending { it.order })

        return Result.success(groupedSponsors)
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cachedRepository = null
        cacheTimestamp = 0
    }
}
