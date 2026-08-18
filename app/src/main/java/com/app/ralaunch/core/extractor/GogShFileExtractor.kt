package com.app.ralaunch.core.extractor

import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.strings.StringsResource.Strings
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.apache.commons.compress.archivers.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * GOG .sh 文件提取器
 */
class GogShFileExtractor private constructor(private val options: Options) {
    data class Options(
        val id: String,
        val sourcePath: Path,
        val destinationPath: Path,
        val callback: ((Event) -> Unit)?
    )

    sealed interface Event {
        val id: String
        val message: String

        data class Progress(
            override val id: String,
            override val message: String,
            val progress: Float
        ) : Event

        data class Complete(
            override val id: String,
            override val message: String
        ) : Event

        data class Error(
            override val id: String,
            override val message: String,
            val cause: Throwable
        ) : Event
    }

    sealed class Result {
        data class Success(val gamePath: Path, val gameDataZipFile: GameDataZipFile) : Result()
        data class Failure(val message: String, val cause: Throwable) : Result()
    }

    class Builder {
        private var id = ""
        private var sourcePath: Path? = null
        private var destinationPath: Path? = null
        private var callback: ((Event) -> Unit)? = null

        fun id(id: String) = apply { this.id = id }

        fun from(sourcePath: Path) = apply { this.sourcePath = sourcePath }

        fun to(destinationPath: Path) = apply { this.destinationPath = destinationPath }

        fun callback(callback: ((Event) -> Unit)?) = apply { this.callback = callback }

        fun build() = GogShFileExtractor(
            Options(
                id = id,
                sourcePath = requireNotNull(sourcePath) { "sourcePath is required" },
                destinationPath = requireNotNull(destinationPath) { "destinationPath is required" },
                callback = callback
            )
        )
    }

    fun extract(): Result {
        return try {
            TemporaryFileAcquirer().use { tfa ->
                // 获取 MakeSelf SH 文件的头部信息
                options.callback?.invoke(
                    Event.Progress(
                        options.id,
                        Strings.extractor.gog.script,
                        0.01f
                    )
                )
                val shFile = MakeSelfShFile.parse(options.sourcePath)
                    ?: throw IOException("解析 MakeSelf Sh 文件头部失败")

                AppLog.d(TAG, "Successfully parsed header - offset: ${shFile.offset}, filesize: ${shFile.filesize}")

                FileChannel.open(options.sourcePath, StandardOpenOption.READ).use { srcChannel ->
                    AppLog.d(TAG, "Starting extraction: ${options.sourcePath} to ${options.destinationPath}")

                    options.destinationPath.createDirectories()

                    // sanity check
                    if (shFile.offset + shFile.filesize > srcChannel.size()) {
                        throw IOException("MakeSelf Sh 文件头部信息无效，超出文件总大小")
                    }

                    options.callback?.invoke(
                        Event.Progress(
                            options.id,
                            Strings.extractor.gog.mojosetup,
                            0.02f
                        )
                    )

                    // 提取 mojosetup.tar.gz
                    val mojosetupPath = tfa.acquireTempFilePath(EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME)
                    AppLog.d(TAG, "Extracting mojosetup.tar.gz to $mojosetupPath")
                    srcChannel.copyRangeTo(shFile.offset, shFile.filesize, mojosetupPath)

                    options.callback?.invoke(
                        Event.Progress(
                            options.id,
                            Strings.extractor.gog.gameData,
                            0.03f
                        )
                    )

                    // 提取 game_data.zip
                    val gameDataPath = tfa.acquireTempFilePath(EXTRACTED_GAME_DATA_ZIP_FILENAME)
                    AppLog.d(TAG, "Extracting game_data.zip to $gameDataPath")
                    srcChannel.copyRangeTo(
                        shFile.offset + shFile.filesize,
                        srcChannel.size() - (shFile.offset + shFile.filesize),
                        gameDataPath
                    )

                    options.callback?.invoke(
                        Event.Progress(
                            options.id,
                            Strings.extractor.gog.parseGameData,
                            0.09f
                        )
                    )
                    AppLog.d(TAG, "Extraction from MakeSelf SH file completed successfully")

                    // 解压 game_data.zip
                    AppLog.d(TAG, "Trying to extract game_data.zip...")
                    val gdzf = GameDataZipFile.parse(gameDataPath)
                        ?: throw IOException("解析 game_data.zip 失败")

                    options.callback?.invoke(
                        Event.Progress(
                            options.id,
                            Strings.extractor.gog.decompressGameData,
                            0.1f
                        )
                    )

                    val gamePath = options.destinationPath / Path("GoG Games", requireNotNull(gdzf.id))
                    val gameDataResult = ArchiveExtractor.builder()
                        .id(options.id)
                        .from(gameDataPath)
                        .prefix(Path("data/noarch/game"))
                        .to(gamePath)
                        .callback { event ->
                            when (event) {
                                is ArchiveExtractor.Event.Progress -> options.callback?.invoke(
                                    Event.Progress(options.id, event.message, 0.1f + event.progress * 0.9f)
                                )
                                is ArchiveExtractor.Event.Complete -> Unit
                                is ArchiveExtractor.Event.Error -> Unit
                            }
                        }
                        .build()
                        .extract()
                    if (gameDataResult is ArchiveExtractor.Result.Failure) {
                        throw IOException(gameDataResult.message, gameDataResult.cause)
                    }

                    // 提取图标
                    ArchiveExtractor.builder()
                        .from(gameDataPath)
                        .prefix(Path("data/noarch/support"))
                        .to(gamePath / "support")
                        .build()
                        .extract()

                    val completedMessage = Strings.extractor.gog.gameDataComplete
                    options.callback?.invoke(Event.Progress(options.id, completedMessage, 1f))
                    options.callback?.invoke(Event.Complete(options.id, completedMessage))

                    Result.Success(gamePath, gdzf)
                }
            }
        } catch (ex: Exception) {
            AppLog.e(TAG, "Error when extracting source file", ex)
            val message = Strings.extractor.gog.failed
            options.callback?.invoke(Event.Error(options.id, message, ex))
            Result.Failure(message, ex)
        }
    }

    private fun FileChannel.copyRangeTo(offset: Long, length: Long, target: Path) {
        position(offset)
        target.outputStream().use { output ->
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val bytesRead = read(buffer)
                if (bytesRead < 0) throw IOException("Unexpected end of MakeSelf archive")
                output.write(buffer.array(), 0, bytesRead)
                remaining -= bytesRead
            }
        }
    }

    /**
     * MakeSelf SH 文件解析器
     */
    data class MakeSelfShFile(
        val offset: Long,
        val filesize: Long
    ) {
        companion object {
            fun parse(filePath: Path): MakeSelfShFile? {
                val headerBuffer = ByteArray(HEADER_SIZE)
                val headerContent: String

                try {
                    filePath.inputStream().use { input ->
                        val bytesRead = input.read(headerBuffer)
                        AppLog.d(TAG, "Read $bytesRead bytes from header")
                        headerContent = String(headerBuffer, 0, bytesRead, Charsets.UTF_8)
                    }
                } catch (ex: Exception) {
                    AppLog.e(TAG, "Error when reading MakeSelf SH file", ex)
                    return null
                }

                return parseMakeSelfShFileContent(headerContent)
            }

            private fun parseMakeSelfShFileContent(content: String): MakeSelfShFile? {
                AppLog.d(TAG, "Parsing makeself file content, content size: ${content.length}")

                val lines = content.split("\n")
                var lineOffset = 0L
                var filesize = 0L
                var foundLineOffset = false
                var foundFilesize = false

                for (line in lines) {
                    if (!foundLineOffset) {
                        when {
                            line.contains("head -n") -> {
                                extractNumber(line.substring(line.indexOf("head -n") + 7))?.let {
                                    lineOffset = it
                                    AppLog.d(TAG, "Found lineOffset from 'head -n': $lineOffset")
                                    foundLineOffset = true
                                }
                            }
                            line.contains("SKIP=") -> {
                                extractNumber(line.substring(line.indexOf("SKIP=") + 5))?.let {
                                    lineOffset = it
                                    AppLog.d(TAG, "Found lineOffset from 'SKIP=': $lineOffset")
                                    foundLineOffset = true
                                }
                            }
                        }
                    }

                    if (!foundFilesize) {
                        when {
                            line.contains("filesizes=") -> {
                                extractNumber(line.substring(line.indexOf("filesizes=") + 10))?.let {
                                    filesize = it
                                    AppLog.d(TAG, "Found filesize from 'filesizes=': $filesize")
                                    foundFilesize = true
                                }
                            }
                            line.contains("SIZE=") -> {
                                extractNumber(line.substring(line.indexOf("SIZE=") + 5))?.let {
                                    filesize = it
                                    AppLog.d(TAG, "Found filesize from 'SIZE=': $filesize")
                                    foundFilesize = true
                                }
                            }
                        }
                    }

                    if (foundLineOffset && foundFilesize) break
                }

                AppLog.d(TAG, "Final parse result - lineOffset: $lineOffset, filesize: $filesize")

                return if (foundLineOffset && foundFilesize) {
                    if (lineOffset > lines.size) {
                        AppLog.e(TAG, "Parsed lineOffset is greater than number of lines, invalid makeself file")
                        return null
                    }
                    val offset = lines.take(lineOffset.toInt()).sumOf { it.length + 1 }
                    MakeSelfShFile(offset.toLong(), filesize)
                } else {
                    null
                }
            }

            private fun extractNumber(str: String): Long? {
                val sb = StringBuilder()
                for (c in str) {
                    if (c.isDigit()) {
                        sb.append(c)
                    } else if (sb.isNotEmpty()) {
                        break
                    }
                }
                return if (sb.isNotEmpty()) sb.toString().toLongOrNull() else null
            }

            private const val HEADER_SIZE = 20480
        }
    }

    /**
     * 游戏数据 ZIP 文件解析器
     */
    data class GameDataZipFile(
        var id: String? = null,
        var version: String? = null,
        var build: String? = null,
        var locale: String? = null,
        var timestamp1: String? = null,
        var timestamp2: String? = null,
        var gogId: String? = null
    ) {
        override fun toString(): String {
            return "GameDataZipFile(id='$id', version='$version', build='$build', locale='$locale', " +
                    "timestamp1='$timestamp1', timestamp2='$timestamp2', gogId='$gogId')"
        }

        companion object {
            const val CONFIG_LUA_PATH = "scripts/config.lua"
            const val GAMEINFO_PATH = "data/noarch/gameinfo"
            const val ICON_PATH = "data/noarch/support/icon.png"

            fun parseFromGogShFile(filePath: Path): GameDataZipFile? {
                val shFile = MakeSelfShFile.parse(filePath) ?: run {
                    AppLog.e(TAG, "MakeSelf SH file is null")
                    return null
                }

                return try {
                    TemporaryFileAcquirer().use { tfa ->
                        val tempZipFile = tfa.acquireTempFilePath("temp_game_data.zip")

                        RandomAccessFile(filePath.toFile(), "r").use { raf ->
                            tempZipFile.outputStream().use { output ->
                                val gameDataStart = shFile.offset + shFile.filesize
                                raf.seek(gameDataStart)

                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (raf.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        parse(tempZipFile)
                    }
                } catch (ex: Exception) {
                    AppLog.e(TAG, "Error when reading GOG SH file: $filePath", ex)
                    null
                }
            }

            fun parse(filePath: Path): GameDataZipFile? {
                return try {
                    ZipFile(filePath.toFile()).use { zip ->
                        val gameDataZipFile = GameDataZipFile()

                        val gameInfoContent = getFileContent(zip, GAMEINFO_PATH)
                        if (gameInfoContent != null) {
                            if (parseGameInfoContent(gameDataZipFile, gameInfoContent)) {
                                return@use gameDataZipFile
                            }
                            AppLog.w(TAG, "Failed to parse gameinfo content, trying config.lua...")
                        }

                        val configLuaContent = getFileContent(zip, CONFIG_LUA_PATH)
                        if (configLuaContent != null) {
                            if (parseConfigLuaContent(gameDataZipFile, configLuaContent)) {
                                return@use gameDataZipFile
                            }
                            AppLog.w(TAG, "Failed to parse config.lua content")
                        }

                        AppLog.e(TAG, "Failed to parse game_data.zip content for id")
                        null
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Exception when reading game_data.zip", e)
                    null
                }
            }

            private fun getFileContent(zip: ZipFile, entryPath: String): String? {
                val entry = zip.getEntry(entryPath)
                if (entry == null) {
                    AppLog.w(TAG, "未在压缩包中找到 $entryPath")
                    return null
                }
                return try {
                    zip.getInputStream(entry).use { stream ->
                        AppLog.d(TAG, "Reading entry $entryPath...")
                        getFileContentFromStream(stream)
                    }
                } catch (e: IOException) {
                    AppLog.w(TAG, "IOException when reading $entryPath", e)
                    null
                }
            }

            private fun getFileContentFromStream(inputStream: InputStream): String {
                val contentBuffer = ByteArray(MAX_CONTENT_SIZE)
                val bytesRead = inputStream.read(contentBuffer)
                AppLog.d(TAG, "Read $bytesRead bytes!")
                return String(contentBuffer, 0, bytesRead, Charsets.UTF_8)
            }

            private fun parseConfigLuaContent(gameDataZipFile: GameDataZipFile, content: String): Boolean {
                for (line in content.split("\n")) {
                    if (line.contains("id = ")) {
                        val idBuilder = StringBuilder()
                        var inQuotes = false
                        for (c in line) {
                            if (c == '"' || c == '\'') {
                                if (inQuotes) break else inQuotes = true
                            } else if (inQuotes) {
                                idBuilder.append(c)
                            }
                        }
                        if (idBuilder.isNotEmpty()) {
                            gameDataZipFile.id = idBuilder.toString()
                            AppLog.d(TAG, "Found id from config.lua: ${gameDataZipFile.id}")
                            return true
                        }
                    }
                }
                AppLog.w(TAG, "cannot extract id from $CONFIG_LUA_PATH")
                return false
            }

            private fun parseGameInfoContent(gameDataZipFile: GameDataZipFile, content: String): Boolean {
                val lines = content.split("\n")
                if (lines.isEmpty()) {
                    AppLog.w(TAG, "cannot even extract id from $GAMEINFO_PATH")
                    return false
                }

                gameDataZipFile.id = lines.getOrNull(0)?.trim()
                gameDataZipFile.version = lines.getOrNull(1)?.trim()
                gameDataZipFile.build = lines.getOrNull(2)?.trim()
                gameDataZipFile.locale = lines.getOrNull(3)?.trim()
                gameDataZipFile.timestamp1 = lines.getOrNull(4)?.trim()
                gameDataZipFile.timestamp2 = lines.getOrNull(5)?.trim()
                gameDataZipFile.gogId = lines.getOrNull(6)?.trim()

                AppLog.d(TAG, "Parsed gameinfo - $gameDataZipFile")

                return !gameDataZipFile.id.isNullOrEmpty()
            }

            private const val MAX_CONTENT_SIZE = 20480
        }
    }

    companion object {
        private const val TAG = "GogShFileExtractor"
        private const val BUFFER_SIZE = 8192
        private const val EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME = "mojosetup.tar.gz"
        private const val EXTRACTED_GAME_DATA_ZIP_FILENAME = "game_data.zip"

        fun builder() = Builder()
    }
}
