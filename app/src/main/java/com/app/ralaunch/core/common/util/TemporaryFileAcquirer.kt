package com.app.ralaunch.core.common.util

import android.content.Context
import android.util.Log
import org.koin.java.KoinJavaComponent
import java.io.Closeable
import java.io.File

class TemporaryFileAcquirer : Closeable {

    private val preferredTempDir: File
    private val tmpFiles = mutableListOf<File>()

    constructor() {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        preferredTempDir = requireNotNull(context.externalCacheDir)
    }

    constructor(preferredTempDir: File) {
        this.preferredTempDir = preferredTempDir
    }

    fun acquireTempFilePath(preferredSuffix: String): File {
        val tempFile = File(preferredTempDir, "${System.currentTimeMillis()}_$preferredSuffix")
        tmpFiles.add(tempFile)
        return tempFile
    }

    fun cleanupTempFiles() {
        tmpFiles.forEach { tmpFile ->
            val isSuccessful = FileUtils.deleteDirectoryRecursively(tmpFile)
            if (!isSuccessful) {
                Log.w(TAG, "Failed to delete temporary file or directory: $tmpFile")
            }
        }
        tmpFiles.clear()
    }

    override fun close() {
        cleanupTempFiles()
    }

    companion object {
        private const val TAG = "TemporaryFileAcquirer"
    }
}
