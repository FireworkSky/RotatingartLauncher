package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.core.logging.AppLog
import org.koin.java.KoinJavaComponent
import java.io.Closeable
import java.nio.file.Path

/**
 * 临时文件管理器
 */
class TemporaryFileAcquirer : Closeable {

    private val preferredTempDir: Path
    private val tmpFilePaths = mutableListOf<Path>()

    constructor() {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        preferredTempDir = requireNotNull(context.externalCacheDir)
            .toPath()
            .toAbsolutePath()
    }

    constructor(preferredTempDir: Path) {
        this.preferredTempDir = preferredTempDir
    }

    fun acquireTempFilePath(preferredSuffix: String): Path {
        val tempFilePath = preferredTempDir.resolve("${System.currentTimeMillis()}_$preferredSuffix")
        tmpFilePaths.add(tempFilePath)
        return tempFilePath
    }

    fun cleanupTempFiles() {
        tmpFilePaths.forEach { tmpFilePath ->
            val isSuccessful = FileUtils.deleteDirectoryRecursivelyWithinRoot(tmpFilePath, preferredTempDir)
            if (!isSuccessful) {
                AppLog.w(TAG, "Failed to delete temporary file or directory: $tmpFilePath")
            }
        }
        tmpFilePaths.clear()
    }

    override fun close() {
        cleanupTempFiles()
    }

    companion object {
        private const val TAG = "TemporaryFileAcquirer"
    }
}
