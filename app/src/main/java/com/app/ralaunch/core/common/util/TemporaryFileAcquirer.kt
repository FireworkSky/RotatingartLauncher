package com.app.ralaunch.core.common.util

import android.content.Context
import org.koin.java.KoinJavaComponent
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

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

    @OptIn(ExperimentalPathApi::class)
    fun cleanupTempFiles() {
        tmpFilePaths.forEach { tmpFilePath ->
            tmpFilePath.deleteRecursively()
        }
        tmpFilePaths.clear()
    }

    override fun close() {
        cleanupTempFiles()
    }
}
