package com.app.ralaunch.core.platform.install

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.install.extractors.BasicSevenZipExtractor
import com.app.ralaunch.core.platform.install.extractors.ExtractorCollection
import com.app.ralaunch.core.platform.install.extractors.GogShFileExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 游戏解压工具类
 * 封装 ralib 中现有的解压实现
 */
object GameExtractorUtils {

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
            e.printStackTrace()
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
            val state = HashMap<String, Any>()
            var gamePath: Path? = null
            var success = false
            var errorMsg: String? = null

            val extractor = GogShFileExtractor(
                shFile.toPath(),
                outputDir.toPath(),
                object : ExtractorCollection.ExtractionListener {
                    override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {
                        progressCallback(message, progress)
                    }

                    override fun onComplete(message: String, state: HashMap<String, Any?>?) {
                        success = true
                        gamePath = state?.get(GogShFileExtractor.STATE_KEY_GAME_PATH) as? Path
                    }

                    override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                        errorMsg = message
                    }
                }
            )
            extractor.state = HashMap(state)

            val result = extractor.extract()

            if (result && success) {
                ExtractResult.Success(gamePath?.toFile() ?: outputDir)
            } else {
                ExtractResult.Error(
                    errorMsg ?: RaLaunchApp.getInstance().getString(R.string.extract_failed)
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ExtractResult.Error(
                e.message ?: RaLaunchApp.getInstance().getString(R.string.extract_failed)
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
            val state = HashMap<String, Any>()
            var success = false
            var errorMsg: String? = null

            val listener = object : ExtractorCollection.ExtractionListener {
                override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {
                    progressCallback(message, progress)
                }

                override fun onComplete(message: String, state: HashMap<String, Any?>?) {
                    success = true
                }

                override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                    errorMsg = message
                }
            }

            val extractor = BasicSevenZipExtractor(
                zipFile.toPath(),
                Paths.get(sourcePrefix),
                outputDir.toPath(),
                listener
            )
            extractor.state = HashMap(state)

            val result = extractor.extract()

            if (result && success) {
                ExtractResult.Success(outputDir)
            } else {
                ExtractResult.Error(
                    errorMsg ?: RaLaunchApp.getInstance().getString(R.string.extract_failed)
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ExtractResult.Error(
                e.message ?: RaLaunchApp.getInstance().getString(R.string.extract_failed)
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
