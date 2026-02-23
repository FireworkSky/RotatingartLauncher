package com.app.ralaunch.core.platform.install.extractors

import android.util.Log
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 基础 7-Zip 解压器
 */
class BasicSevenZipExtractor : ExtractorCollection.IExtractor {
    
    companion object {
        private const val TAG = "BasicSevenZipExtractor"
        
        @Volatile
        private var libraryLoaded = false
        
        /**
         * 确保 7-Zip 原生库已加载
         * 7-Zip 库保留在 APK 中，直接使用 System.loadLibrary 加载
         */
        @Synchronized
        fun ensureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true
            
            try {
                // 7-Zip 库在 APK 的 lib 目录中，直接加载
                System.loadLibrary("7-Zip-JBinding")
                libraryLoaded = true
                Log.i(TAG, "7-Zip native library loaded successfully via System.loadLibrary")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load 7-Zip native library: ${e.message}")
                // 尝试从 runtime_libs 加载作为备选
                try {
                    val context = RaLaunchApp.getInstance()
                    libraryLoaded = RuntimeLibraryLoader.load7Zip(context)
                    if (libraryLoaded) {
                        Log.i(TAG, "7-Zip native library loaded from runtime_libs")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Cannot load 7-Zip library from runtime_libs: ${e2.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot load 7-Zip library: ${e.message}")
            }
            return libraryLoaded
        }
    }

    private lateinit var sourcePath: Path
    private var sourceExtractionPrefix: Path = Paths.get("")
    private lateinit var destinationPath: Path
    private var extractionListener: ExtractorCollection.ExtractionListener? = null
    override var state: HashMap<String, Any?> = hashMapOf()

    constructor(sourcePath: Path, destinationPath: Path) {
        setSourcePath(sourcePath)
        setDestinationPath(destinationPath)
    }

    constructor(sourcePath: Path, destinationPath: Path, listener: ExtractorCollection.ExtractionListener?) {
        setSourcePath(sourcePath)
        setDestinationPath(destinationPath)
        setExtractionListener(listener)
    }

    constructor(
        sourcePath: Path,
        sourceExtractionPrefix: Path,
        destinationPath: Path,
        listener: ExtractorCollection.ExtractionListener?
    ) {
        setSourcePath(sourcePath)
        setDestinationPath(destinationPath)
        setExtractionListener(listener)
        setSourceExtractionPrefix(sourceExtractionPrefix)
    }

    override fun setSourcePath(sourcePath: Path) {
        this.sourcePath = sourcePath
    }

    fun setSourceExtractionPrefix(sourceExtractionPrefix: Path) {
        this.sourceExtractionPrefix = sourceExtractionPrefix
    }

    override fun setDestinationPath(destinationPath: Path) {
        this.destinationPath = destinationPath
    }

    override fun setExtractionListener(listener: ExtractorCollection.ExtractionListener?) {
        this.extractionListener = listener
    }

    override fun extract(): Boolean {
        // 确保 7-Zip 原生库已加载
        if (!ensureLibraryLoaded()) {
            extractionListener?.onError(
                RaLaunchApp.getInstance().getString(R.string.extract_7zip_library_load_failed),
                RuntimeException("Failed to load 7-Zip native library"),
                state
            )
            return false
        }
        
        return try {
            if (!Files.exists(destinationPath)) {
                Files.createDirectories(destinationPath)
            }

            RandomAccessFile(sourcePath.toString(), "r").use { raf ->
                RandomAccessFileInStream(raf).use { inStream ->
                    SevenZip.openInArchive(null, inStream).use { archive ->
                        val totalItems = archive.numberOfItems
                        Log.d(TAG, "Archive contains $totalItems items")
                        archive.extract(null, false, ArchiveExtractCallback(archive))
                    }
                }
            }

            Log.d(TAG, "SevenZip extraction completed successfully")
            extractionListener?.apply {
                val completeMessage = RaLaunchApp.getInstance().getString(R.string.extract_complete)
                onProgress(completeMessage, 1.0f, state)
                onComplete(completeMessage, state)
            }
            true
        } catch (ex: Exception) {
            extractionListener?.onError(
                RaLaunchApp.getInstance().getString(R.string.extract_7zip_failed),
                ex,
                state
            )
            false
        }
    }

    /**
     * SevenZipJBinding 提取回调实现
     */
    private inner class ArchiveExtractCallback(
        private val archive: IInArchive
    ) : IArchiveExtractCallback {

        private var outputStream: SequentialFileOutputStream? = null
        private var currentProcessingFilePath: Path? = null
        private var totalBytes: Long = 0
        private var totalBytesExtracted: Long = 0

        @Throws(SevenZipException::class)
        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            try {
                closeOutputStream()

                val filePath = Paths.get(archive.getStringProperty(index, PropID.PATH))
                val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as Boolean

                // 跳过非指定前缀的文件
                val relativeFilePath = sourceExtractionPrefix.relativize(filePath).normalize()
                if (relativeFilePath.toString().startsWith("..")) {
                    return null
                }

                // 计算目标文件路径并防止路径遍历攻击
                val targetFilePath = destinationPath.resolve(relativeFilePath).normalize()
                if (destinationPath.relativize(targetFilePath).toString().startsWith("..")) {
                    throw SevenZipException("Attempting to write outside of destination directory: $targetFilePath")
                }

                // 对于文件夹只创建文件夹
                if (isFolder) {
                    Files.createDirectories(targetFilePath)
                    return null
                }

                // 创建文件的父目录
                currentProcessingFilePath = targetFilePath
                val targetFileParentPath = targetFilePath.normalize().parent
                if (!Files.exists(targetFileParentPath)) {
                    Files.createDirectories(targetFileParentPath)
                }

                val progress = if (totalBytes > 0) totalBytesExtracted.toFloat() / totalBytes else 0f
                extractionListener?.onProgress(
                    RaLaunchApp.getInstance().getString(R.string.extract_in_progress, filePath),
                    progress,
                    state
                )

                // 返回输出流
                outputStream = SequentialFileOutputStream(targetFilePath)
                return outputStream
            } catch (e: Exception) {
                throw SevenZipException("Error getting stream for index $index", e)
            }
        }

        @Throws(SevenZipException::class)
        override fun prepareOperation(extractAskMode: ExtractAskMode) {
        }

        @Throws(SevenZipException::class)
        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            closeOutputStream()
        }

        @Throws(SevenZipException::class)
        override fun setTotal(total: Long) {
            totalBytes = total
        }

        @Throws(SevenZipException::class)
        override fun setCompleted(complete: Long) {
            totalBytesExtracted = complete
        }

        @Throws(SevenZipException::class)
        private fun closeOutputStream() {
            outputStream?.let {
                try {
                    it.close()
                    outputStream = null
                } catch (e: IOException) {
                    throw SevenZipException("Error closing file: $currentProcessingFilePath")
                }
            }
        }
    }

    /**
     * SevenZipJBinding 输出流实现
     */
    private class SequentialFileOutputStream(targetFilePath: Path) : ISequentialOutStream {
        private val fileStream = FileOutputStream(targetFilePath.toFile())

        @Throws(SevenZipException::class)
        override fun write(data: ByteArray): Int {
            return try {
                fileStream.write(data)
                data.size
            } catch (e: IOException) {
                throw SevenZipException("Error writing to output stream", e)
            }
        }

        @Throws(IOException::class)
        fun close() {
            fileStream.close()
        }
    }
}
