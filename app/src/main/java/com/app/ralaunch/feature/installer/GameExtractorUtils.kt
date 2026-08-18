package com.app.ralaunch.feature.installer

import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.extractor.GogShFileExtractor
import com.app.ralaunch.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

/**
 * 游戏解压工具类
 * 封装 ralib 中现有的解压实现
 */
object GameExtractorUtils {
    private const val TAG = "GameExtractorUtils"

    /**
     * 解析 GOG .sh 文件，获取游戏信息
     */
    suspend fun parseGogShFile(shFile: File): GogGameInfo? = withContext(Dispatchers.IO) {
        try {
            val gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(shFile.toPath())
            if (gdzf != null) {
                GogGameInfo(
                    id = gdzf.id ?: "",
                    version = gdzf.version ?: "",
                    build = gdzf.build,
                    locale = gdzf.locale
                )
            } else {
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse GOG .sh file", e)
            null
        }
    }

    /**
     * 解压 GOG .sh 文件
     */
    suspend fun extractGogSh(
        shFile: File,
        outputDir: File,
        progressCallback: (String, Float) -> Unit
    ): ExtractResult = withContext(Dispatchers.IO) {
        try {
            when (val result = GogShFileExtractor.builder()
                .from(shFile.toPath())
                .to(outputDir.toPath())
                .callback { event ->
                    if (event is GogShFileExtractor.Event.Progress) {
                        progressCallback(event.message, event.progress)
                    }
                }
                .build()
                .extract()
            ) {
                is GogShFileExtractor.Result.Success -> ExtractResult.Success(result.gamePath.toFile())
                is GogShFileExtractor.Result.Failure -> ExtractResult.Error(result.message)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to extract GOG .sh file", e)
            ExtractResult.Error(
                e.message ?: Strings.extractor.failed
            )
        }
    }

    /**
     * 解压 ZIP 文件
     * @param zipFile ZIP 文件
     * @param outputDir 输出目录
     * @param progressCallback 进度回调
     * @param sourcePrefix 源路径前缀（用于只解压 ZIP 中的特定目录）
     */
    suspend fun extractZip(
        zipFile: File,
        outputDir: File,
        progressCallback: (String, Float) -> Unit,
        sourcePrefix: String = ""
    ): ExtractResult = withContext(Dispatchers.IO) {
        try {
            when (val result = ArchiveExtractor.builder()
                .from(zipFile.toPath())
                .prefix(Paths.get(sourcePrefix))
                .to(outputDir.toPath())
                .callback { event ->
                    if (event is ArchiveExtractor.Event.Progress) {
                        progressCallback(event.message, event.progress)
                    }
                }
                .build()
                .extract()
            ) {
                is ArchiveExtractor.Result.Success -> ExtractResult.Success(result.destinationPath.toFile())
                is ArchiveExtractor.Result.Failure -> ExtractResult.Error(result.message)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to extract ZIP file", e)
            ExtractResult.Error(
                e.message ?: Strings.extractor.failed
            )
        }
    }

    /**
     * GOG 游戏信息
     */
    data class GogGameInfo(
        val id: String,
        val version: String,
        val build: String? = null,
        val locale: String? = null
    )

    /**
     * 解压结果
     */
    sealed class ExtractResult {
        data class Success(val outputDir: File) : ExtractResult()
        data class Error(val message: String) : ExtractResult()
    }
}
