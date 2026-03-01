package com.app.ralaunch.core.platform.install

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.install.extractors.BasicSevenZipExtractor
import com.app.ralaunch.core.platform.install.extractors.ExtractorCollection
import com.app.ralaunch.core.platform.install.extractors.GogShFileExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Game extraction utility class
 */
object GameExtractorUtils {

    /**
     * Parse GOG .sh file to get game info
     */
    suspend fun parseGogShFile(shFile: File): GogGameInfo? = withContext(Dispatchers.IO) {
        try {
            val gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(shFile)
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
     * Extract GOG .sh file
     */
    suspend fun extractGogSh(
        shFile: File,
        outputDir: File,
        progressCallback: (String, Float) -> Unit
    ): ExtractResult = withContext(Dispatchers.IO) {
        try {
            val state = HashMap<String, Any>()
            var gamePath: File? = null
            var success = false
            var errorMsg: String? = null

            val extractor = GogShFileExtractor(
                shFile,
                outputDir,
                object : ExtractorCollection.ExtractionListener {
                    override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {
                        progressCallback(message, progress)
                    }

                    override fun onComplete(message: String, state: HashMap<String, Any?>?) {
                        success = true
                        gamePath = state?.get(GogShFileExtractor.STATE_KEY_GAME_PATH) as? File
                    }

                    override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                        errorMsg = message
                    }
                }
            )
            extractor.state = HashMap(state)

            val result = extractor.extract()

            if (result && success) {
                ExtractResult.Success(gamePath ?: outputDir)
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
     * Extract ZIP file
     * @param zipFile ZIP file
     * @param outputDir Output directory
     * @param progressCallback Progress callback
     * @param sourcePrefix Source path prefix
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
                zipFile,
                File(sourcePrefix),
                outputDir,
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

    data class GogGameInfo(
        val id: String,
        val version: String,
        val build: String? = null,
        val locale: String? = null
    )

    sealed class ExtractResult {
        data class Success(val outputDir: File) : ExtractResult()
        data class Error(val message: String) : ExtractResult()
    }
}
