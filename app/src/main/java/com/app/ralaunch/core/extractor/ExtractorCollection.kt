package com.app.ralaunch.core.extractor

import com.app.ralaunch.core.logging.AppLog
import java.nio.file.Path

/**
 * 提取器集合
 */
class ExtractorCollection {
    private val extractors = mutableListOf<IExtractor>()

    fun addExtractor(extractor: IExtractor) {
        extractors.add(extractor)
    }

    fun extractAllInNewThread() {
        Thread {
            try {
                extractors.forEachIndexed { index, extractor ->
                    AppLog.d(TAG, "Starting extraction for extractor: ${extractor.javaClass.simpleName} ($index/${extractors.size})")
                    val state = extractor.state
                    state[STATE_KEY_EXTRACTOR_INDEX] = index
                    state[STATE_KEY_EXTRACTORS] = extractors
                    val success = extractor.extract()
                    if (!success) {
                        AppLog.e(TAG, "Extraction failed for extractor: ${extractor.javaClass.simpleName}")
                        return@Thread
                    }
                }
            } catch (ex: Exception) {
                AppLog.e(TAG, "Extraction error: ", ex)
            }
        }.start()
    }

    /**
     * 提取监听器接口
     */
    interface ExtractionListener {
        fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?)
        fun onComplete(message: String, state: HashMap<String, Any?>?)
        fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?)
    }

    /**
     * 提取器接口
     */
    interface IExtractor {
        fun setSourcePath(sourcePath: Path)
        fun setDestinationPath(destinationPath: Path)
        fun setExtractionListener(listener: ExtractionListener?)
        var state: HashMap<String, Any?>
        fun extract(): Boolean
    }

    /**
     * 构建器
     */
    class Builder {
        private val extractorCollection = ExtractorCollection()
        private val state = HashMap<String, Any?>()

        fun configureState(key: String, value: Any?): Builder {
            state[key] = value
            return this
        }

        @JvmOverloads
        fun addExtractor(extractor: IExtractor, isInjectState: Boolean = true): Builder {
            if (isInjectState) {
                extractor.state = state
            }
            extractorCollection.addExtractor(extractor)
            return this
        }

        fun build(): ExtractorCollection = extractorCollection
    }

    companion object {
        private const val TAG = "ExtractorCollection"

        const val STATE_KEY_EXTRACTOR_INDEX = "extractor_index"
        const val STATE_KEY_EXTRACTORS = "extractors"
    }
}
