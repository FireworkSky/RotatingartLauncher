package com.app.ralaunch.core.common.util

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.*
// ... REMOVED java.nio.file IMPORTS completely to prevent crashes on Android 7 ...
import java.util.zip.GZIPInputStream

/**
 * 通用归档文件解压工具
 */
object ArchiveExtractor {
    private const val TAG = "ArchiveExtractor"
    private const val BUFFER_SIZE = 8192

    fun interface ProgressCallback {
        fun onProgress(processedFiles: Int, currentFile: String)
    }

    @JvmStatic
    @JvmOverloads
    fun extractTarGz(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GZIPInputStream(bis).use { gzipIn ->
                    TarArchiveInputStream(gzipIn).use { tarIn ->
                        return extractTarEntries(tarIn, targetDir, stripPrefix, callback)
                    }
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun extractTarXz(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                XZCompressorInputStream(bis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        return extractTarEntries(tarIn, targetDir, stripPrefix, callback)
                    }
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun extractTar(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                TarArchiveInputStream(bis).use { tarIn ->
                    return extractTarEntries(tarIn, targetDir, stripPrefix, callback)
                }
            }
        }
    }

    private fun extractTarEntries(
        tarIn: TarArchiveInputStream, targetDir: File,
        stripPrefix: String?, callback: ProgressCallback?
    ): Int {
        var processedFiles = 0

        // ... Replace sequence with a robust while loop to ensure stability on older devices ...
        while (true) {
            val entry = tarIn.nextTarEntry ?: break
            
            if (!tarIn.canReadEntryData(entry)) continue

            val entryName = normalizeEntryName(entry.name, stripPrefix) ?: continue
            val targetFile = File(targetDir, entryName)

            if (!isPathSafe(targetDir, targetFile)) continue

            when {
                entry.isDirectory -> extractDirectory(targetFile)
                entry.isSymbolicLink -> extractSymlink(targetFile, entry.linkName)
                else -> extractFile(tarIn, targetFile, entry.mode)
            }

            processedFiles++
            if (callback != null && processedFiles % 10 == 0) {
                callback.onProgress(processedFiles, entryName)
            }
        }

        return processedFiles
    }

    private fun normalizeEntryName(entryName: String?, stripPrefix: String?): String? {
        if (entryName.isNullOrEmpty() || entryName == "." || entryName == "..") return null

        var name: String = entryName
        if (name.startsWith("./")) name = name.substring(2)

        if (!stripPrefix.isNullOrEmpty()) {
            name = when {
                name.startsWith("./$stripPrefix") -> name.substring(2 + stripPrefix.length)
                name.startsWith(stripPrefix) -> name.substring(stripPrefix.length)
                name.contains(stripPrefix) -> name.substring(name.indexOf(stripPrefix) + stripPrefix.length)
                else -> name
            }
        }

        while (name.startsWith("/") || name.startsWith("\\")) {
            name = name.substring(1)
        }

        return name.takeIf { it.isNotEmpty() }
    }

    private fun isPathSafe(targetDir: File, targetFile: File): Boolean {
        return try {
            val canonicalDestPath = targetDir.canonicalPath
            val canonicalEntryPath = targetFile.canonicalPath
            canonicalEntryPath.startsWith("$canonicalDestPath${File.separator}") || canonicalEntryPath == canonicalDestPath
        } catch (e: IOException) { false }
    }

    private fun extractDirectory(targetFile: File) {
        if (!targetFile.exists()) targetFile.mkdirs()
    }

    private fun extractSymlink(targetFile: File, linkTarget: String) {
        targetFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
        if (targetFile.exists()) targetFile.delete()

        try {
            android.system.Os.symlink(linkTarget, targetFile.absolutePath)
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to create symlink: ${e.message}")
            targetFile.parentFile?.let { parent ->
                val linkTargetFile = File(parent, linkTarget)
                if (linkTargetFile.exists()) {
                    try {
                        // ... Use standard Kotlin File copy to replace java.nio.file.Files ...
                        linkTargetFile.copyTo(targetFile, overwrite = true)
                    } catch (copyEx: Exception) {
                        AppLogger.warn(TAG, "Failed to copy symlink target: ${copyEx.message}")
                    }
                }
            }
        }
    }

    private fun extractFile(tarIn: TarArchiveInputStream, targetFile: File, mode: Int) {
        targetFile.parentFile?.takeIf { !it.exists() }?.mkdirs()

        FileOutputStream(targetFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                tarIn.copyTo(bos, BUFFER_SIZE)
            }
        }

        if ((mode and 0x40) != 0) targetFile.setExecutable(true, false)
        targetFile.setReadable(true, false)
    }

    @JvmStatic
    fun copyAssetToFile(context: Context, assetFileName: String, targetFile: File) {
        context.assets.open(assetFileName).use { inputStream ->
            FileOutputStream(targetFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    inputStream.copyTo(bos, BUFFER_SIZE)
                }
            }
        }
    }
}
