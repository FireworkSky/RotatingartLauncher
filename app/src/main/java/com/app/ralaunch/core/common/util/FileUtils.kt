package com.app.ralaunch.core.common.util

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * File operation utilities
 */
object FileUtils {
    private const val TAG = "FileUtils"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 100L

    /**
     * Recursively delete directory and its contents
     * @param path path to delete
     * @return true if deletion was successful
     */
    @JvmStatic
    fun deleteDirectoryRecursively(path: File?): Boolean {
        if (path == null) return false
        if (!path.exists()) return true
        if (!path.canRead()) return false

        return try {
            path.walkBottomUp().fold(true) { result, file ->
                if (!deleteFileWithRetry(file)) {
                    Log.w(TAG, "Failed to delete: $file")
                    false
                } else {
                    result
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Delete failed: $path")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Delete failed (permission): $path")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed: $path", e)
            false
        }
    }

    /**
     * Recursively delete directory and its contents
     * @param path string path to delete
     * @return true if deletion was successful
     */
    @JvmStatic
    fun deleteDirectoryRecursively(path: String?): Boolean {
        if (path == null) return false
        return deleteDirectoryRecursively(File(path))
    }

    /**
     * Delete file with retry mechanism
     */
    private fun deleteFileWithRetry(file: File): Boolean {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                if (!file.exists()) return true
                if (file.delete()) return true
            } catch (e: SecurityException) {
                Log.w(TAG, "Delete denied (security): $file")
                return false
            } catch (e: IOException) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            } catch (e: Exception) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            }
        }
        Log.w(TAG, "Failed to delete after $MAX_RETRY_ATTEMPTS attempts: $file")
        return false
    }

    /**
     * Check if file exists
     */
    @JvmStatic
    fun exists(path: String?): Boolean {
        if (path == null) return false
        return File(path).exists()
    }

    /**
     * Create directories including parents
     */
    @JvmStatic
    fun createDirectories(path: String?): Boolean {
        if (path == null) return false
        return File(path).mkdirs()
    }

    /**
     * Get file size in bytes
     */
    @JvmStatic
    fun getFileSize(path: String?): Long {
        if (path == null) return 0L
        val file = File(path)
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    /**
     * Copy file from source to destination
     */
    @JvmStatic
    fun copyFile(src: File, dst: File): Boolean {
        return try {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Copy failed: $src -> $dst", e)
            false
        }
    }
}
