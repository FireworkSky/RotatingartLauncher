package com.app.ralaunch.core.common.util

import android.content.Context
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.zip.GZIPInputStream

object ArchiveExtractor {
    private const val TAG = "ArchiveExtractor"
    private const val BUFFER_SIZE = 65536 

    fun interface ProgressCallback {
        fun onProgress(processedFiles: Int, currentFile: String)
    }

    @JvmStatic
    @JvmOverloads
    fun extractTarGz(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        return FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                GZIPInputStream(bis, BUFFER_SIZE).use { gzipIn ->
                    extractTarEntries(gzipIn, targetDir, stripPrefix, callback)
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun extractTarXz(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        return FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                XZInputStream(bis).use { xzIn ->
                    extractTarEntries(xzIn, targetDir, stripPrefix, callback)
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun extractTar(archiveFile: File, targetDir: File, stripPrefix: String?, callback: ProgressCallback? = null): Int {
        return FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                extractTarEntries(bis, targetDir, stripPrefix, callback)
            }
        }
    }

    private fun extractTarEntries(
        inStream: InputStream, targetDir: File,
        stripPrefix: String?, callback: ProgressCallback?
    ): Int {
        var processedFiles = 0
        var lastCallbackTime = 0L 
        val tarReader = MiniTarReader(inStream)

        while (true) {
            val entry = tarReader.nextEntry() ?: break
            val entryName = normalizeEntryName(entry.name, stripPrefix) ?: continue
            val targetFile = File(targetDir, entryName)

            if (!isPathSafe(targetDir, targetFile)) continue

            when {
                entry.isDirectory -> extractDirectory(targetFile)
                entry.isSymbolicLink -> extractSymlink(targetFile, entry.linkName)
                else -> extractFile(tarReader, targetFile, entry.mode)
            }

            processedFiles++
            
            if (callback != null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCallbackTime > 100) {
                    callback.onProgress(processedFiles, entryName)
                    lastCallbackTime = currentTime
                }
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
            targetFile.parentFile?.let { parent ->
                val linkTargetFile = File(parent, linkTarget)
                if (linkTargetFile.exists()) {
                    try {
                        linkTargetFile.copyTo(targetFile, overwrite = true)
                    } catch (copyEx: Exception) {}
                }
            }
        }
    }

    private fun extractFile(tarReader: MiniTarReader, targetFile: File, mode: Int) {
        targetFile.parentFile?.takeIf { !it.exists() }?.mkdirs()

        FileOutputStream(targetFile).use { fos ->
            BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = tarReader.readData(buffer)
                    if (read <= 0) break
                    bos.write(buffer, 0, read)
                }
            }
        }

        if ((mode and 0x40) != 0) targetFile.setExecutable(true, false)
        targetFile.setReadable(true, false)
    }

    @JvmStatic
    fun copyAssetToFile(context: Context, assetFileName: String, targetFile: File) {
        context.assets.open(assetFileName).use { inputStream ->
            FileOutputStream(targetFile).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        bos.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    class MiniTarReader(private val inStream: InputStream) {
        private var currentEntrySize: Long = 0
        private var bytesReadForEntry: Long = 0
        private val headerBuffer = ByteArray(512)

        class TarEntry(
            val name: String,
            val size: Long,
            val typeFlag: Char,
            val linkName: String,
            val mode: Int
        ) {
            val isDirectory: Boolean get() = typeFlag == '5' || name.endsWith("/")
            val isSymbolicLink: Boolean get() = typeFlag == '2'
        }

        fun nextEntry(): TarEntry? {
            val padding = (512 - (bytesReadForEntry % 512)) % 512
            if (padding > 0L) skipFully(padding)

            val read = readFully(headerBuffer)
            if (read < 512) return null
            if (headerBuffer.all { it == 0.toByte() }) return null

            var parsedName = parseString(0, 100)
            var realName: String? = null

            if (parsedName == "././@LongLink") {
                val nameSize = parseOctal(124, 12).toInt()
                val nameBytes = ByteArray(nameSize)
                readFully(nameBytes)
                realName = String(nameBytes).trim('\u0000')

                val namePadding = (512 - (nameSize % 512)) % 512
                if (namePadding > 0) skipFully(namePadding.toLong())

                readFully(headerBuffer)
                parsedName = parseString(0, 100)
            }

            val prefix = parseString(345, 155)
            val finalName = realName ?: if (prefix.isNotEmpty()) "$prefix/$parsedName" else parsedName

            currentEntrySize = parseOctal(124, 12)
            val typeFlag = headerBuffer[156].toInt().toChar()
            val linkName = parseString(157, 100)
            val mode = parseOctal(100, 8).toInt()

            bytesReadForEntry = 0
            return TarEntry(finalName, currentEntrySize, typeFlag, linkName, mode)
        }

        fun readData(buffer: ByteArray): Int {
            if (bytesReadForEntry >= currentEntrySize) return -1
            val toRead = minOf(buffer.size.toLong(), currentEntrySize - bytesReadForEntry).toInt()
            val read = inStream.read(buffer, 0, toRead)
            if (read > 0) bytesReadForEntry += read
            return read
        }

        private fun parseString(offset: Int, length: Int): String {
            var end = offset
            val limit = offset + length
            while (end < limit && headerBuffer[end] != 0.toByte()) end++
            return String(headerBuffer, offset, end - offset).trim()
        }

        private fun parseOctal(offset: Int, length: Int): Long {
            val str = String(headerBuffer, offset, length).trim('\u0000', ' ')
            return if (str.isNotEmpty()) str.toLongOrNull(8) ?: 0L else 0L
        }

        private fun readFully(buffer: ByteArray): Int {
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = inStream.read(buffer, totalRead, buffer.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            return totalRead
        }

        private fun skipFully(n: Long) {
            var totalSkipped = 0L
            while (totalSkipped < n) {
                val skipped = inStream.skip(n - totalSkipped)
                if (skipped == 0L) {
                    if (inStream.read() == -1) break else totalSkipped++
                } else {
                    totalSkipped += skipped
                }
            }
        }
    }
}
