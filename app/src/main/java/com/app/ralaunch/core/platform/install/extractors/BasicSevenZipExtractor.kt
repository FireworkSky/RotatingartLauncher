package com.app.ralaunch.core.platform.install.extractors

import android.util.Log
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

class BasicSevenZipExtractor : ExtractorCollection.IExtractor {

    companion object {
        private const val TAG = "BasicSevenZipExtractor"

        @Volatile
        private var libraryLoaded = false

        @Synchronized
        fun ensureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true

            try {
                System.loadLibrary("7-Zip-JBinding")
                libraryLoaded = true
                Log.i(TAG, "7-Zip native library loaded successfully via System.loadLibrary")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load 7-Zip native library: ${e.message}")
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

    private lateinit var sourceFile: File
    private var sourceExtractionPrefix: File = File("")
    private lateinit var destinationFile: File
    private var extractionListener: ExtractorCollection.ExtractionListener? = null
    override var state: HashMap<String, Any?> = hashMapOf()

    constructor(sourceFile: File, destinationFile: File) {
        setSourcePath(sourceFile)
        setDestinationPath(destinationFile)
    }

    constructor(sourceFile: File, destinationFile: File, listener: ExtractorCollection.ExtractionListener?) {
        setSourcePath(sourceFile)
        setDestinationPath(destinationFile)
        setExtractionListener(listener)
    }

    constructor(
        sourceFile: File,
        sourceExtractionPrefix: File,
        destinationFile: File,
        listener: ExtractorCollection.ExtractionListener?
    ) {
        setSourcePath(sourceFile)
        setDestinationPath(destinationFile)
        setExtractionListener(listener)
        setSourceExtractionPrefix(sourceExtractionPrefix)
    }

    override fun setSourcePath(sourcePath: File) {
        this.sourceFile = sourcePath
    }

    fun setSourceExtractionPrefix(sourceExtractionPrefix: File) {
        this.sourceExtractionPrefix = sourceExtractionPrefix
    }

    override fun setDestinationPath(destinationPath: File) {
        this.destinationFile = destinationPath
    }

    override fun setExtractionListener(listener: ExtractorCollection.ExtractionListener?) {
        this.extractionListener = listener
    }

    override fun extract(): Boolean {
        if (!ensureLibraryLoaded()) {
            extractionListener?.onError(
                RaLaunchApp.getInstance().getString(R.string.extract_7zip_library_load_failed),
                RuntimeException("Failed to load 7-Zip native library"),
                state
            )
            return false
        }

        return try {
            if (!destinationFile.exists()) {
                destinationFile.mkdirs()
            }

            RandomAccessFile(sourceFile, "r").use { raf ->
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

    private inner class ArchiveExtractCallback(
        private val archive: IInArchive
    ) : IArchiveExtractCallback {

        private var outputStream: SequentialFileOutputStream? = null
        private var currentProcessingFile: File? = null
        private var totalBytes: Long = 0
        private var totalBytesExtracted: Long = 0

        @Throws(SevenZipException::class)
        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            try {
                closeOutputStream()

                val filePath = archive.getStringProperty(index, PropID.PATH)
                val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as Boolean

                // Thay Path.relativize bang xu ly String
                val prefix = sourceExtractionPrefix.path
                val relativeFilePath = if (prefix.isEmpty() || prefix == ".") {
                    filePath
                } else if (filePath.startsWith(prefix)) {
                    filePath.substring(prefix.length).trimStart('/', '\\')
                } else {
                    return null
                }

                // Tinh toan target file va chong path traversal
                val targetFile = File(destinationFile, relativeFilePath).canonicalFile
                if (!targetFile.path.startsWith(destinationFile.canonicalPath)) {
                    throw SevenZipException("Path traversal detected: $targetFile")
                }

                if (isFolder) {
                    targetFile.mkdirs()
                    return null
                }

                currentProcessingFile = targetFile

                // Tao thu muc cha
                targetFile.parentFile?.mkdirs()

                val progress = if (totalBytes > 0) totalBytesExtracted.toFloat() / totalBytes else 0f
                extractionListener?.onProgress(
                    RaLaunchApp.getInstance().getString(R.string.extract_in_progress, filePath),
                    progress,
                    state
                )

                outputStream = SequentialFileOutputStream(targetFile)
                return outputStream
            } catch (e: Exception) {
                throw SevenZipException("Error getting stream for index $index", e)
            }
        }

        @Throws(SevenZipException::class)
        override fun prepareOperation(extractAskMode: ExtractAskMode) {}

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
                    throw SevenZipException("Error closing file: $currentProcessingFile")
                }
            }
        }
    }

    private class SequentialFileOutputStream(targetFile: File) : ISequentialOutStream {
        private val fileStream = FileOutputStream(targetFile)

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
