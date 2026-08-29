package com.app.ralaunch.core.extractor

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.BoundedInputStream
import timber.log.Timber
import com.app.ralaunch.strings.StringsResource.Strings
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream

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

            Timber.d("Successfully parsed header - offset: ${shFile.offset}, filesize: ${shFile.filesize}")
            Timber.d("Starting extraction: ${options.sourcePath} to ${options.destinationPath}")

            options.destinationPath.createDirectories()

            // sanity check
            if (shFile.offset + shFile.filesize > Files.size(options.sourcePath)) {
                throw IOException("MakeSelf Sh 文件头部信息无效，超出文件总大小")
            }

            options.callback?.invoke(
                Event.Progress(
                    options.id,
                    Strings.extractor.gog.mojosetup,
                    0.02f
                )
            )

            // 校验并解析 mojosetup.tar.gz：用 BoundedInputStream 限定 gzip 载荷区间，流式解析，无需落盘
            if (shFile.filesize > 0) {
                parseMojoSetupPayload(shFile)
            }

            options.callback?.invoke(
                Event.Progress(
                    options.id,
                    Strings.extractor.gog.gameData,
                    0.03f
                )
            )

            // game_data.zip 直接用 commons-compress ZipFile 解析完整 .sh 文件：
            // makeself 脚本与 mojosetup.tar.gz 会被识别为 zip 前导数据(preamble)并自动跳过
            ZipFile.builder().setPath(options.sourcePath).get().use { zipFile ->
                Timber.d("Opened game data zip, first local file header at ${zipFile.firstLocalFileHeaderOffset}")

                options.callback?.invoke(
                    Event.Progress(
                        options.id,
                        Strings.extractor.gog.parseGameData,
                        0.09f
                    )
                )
                Timber.d("Parsing game_data.zip from MakeSelf SH file")

                // 解析 game_data.zip
                val gdzf = GameDataZipFile.parse(zipFile)
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
                    .from(zipFile)
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
                    .from(zipFile)
                    .prefix(Path("data/noarch/support"))
                    .to(gamePath / "support")
                    .build()
                    .extract()

                val completedMessage = Strings.extractor.gog.gameDataComplete
                options.callback?.invoke(Event.Progress(options.id, completedMessage, 1f))
                options.callback?.invoke(Event.Complete(options.id, completedMessage))

                Result.Success(gamePath, gdzf)
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Error when extracting source file")
            val message = Strings.extractor.gog.failed
            options.callback?.invoke(Event.Error(options.id, message, ex))
            Result.Failure(message, ex)
        }
    }

    /**
     * 流式解析 makeself 载荷（mojosetup.tar.gz），仅校验结构，不产生临时文件
     */
    private fun parseMojoSetupPayload(shFile: MakeSelfShFile) {
        FileChannel.open(options.sourcePath, StandardOpenOption.READ).use { channel ->
            channel.position(shFile.offset)
            val bounded = BoundedInputStream(Channels.newInputStream(channel), shFile.filesize)
            GzipCompressorInputStream(bounded).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    generateSequence { tar.nextEntry }.forEach { entry ->
                        Timber.d("mojosetup payload entry: %s", entry.name)
                    }
                }
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
                        Timber.d("Read $bytesRead bytes from header")
                        headerContent = String(headerBuffer, 0, bytesRead, Charsets.UTF_8)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error when reading MakeSelf SH file")
                    return null
                }

                return parseMakeSelfShFileContent(headerContent)
            }

            private fun parseMakeSelfShFileContent(content: String): MakeSelfShFile? {
                Timber.d("Parsing makeself file content, content size: ${content.length}")

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
                                    Timber.d("Found lineOffset from 'head -n': $lineOffset")
                                    foundLineOffset = true
                                }
                            }
                            line.contains("SKIP=") -> {
                                extractNumber(line.substring(line.indexOf("SKIP=") + 5))?.let {
                                    lineOffset = it
                                    Timber.d("Found lineOffset from 'SKIP=': $lineOffset")
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
                                    Timber.d("Found filesize from 'filesizes=': $filesize")
                                    foundFilesize = true
                                }
                            }
                            line.contains("SIZE=") -> {
                                extractNumber(line.substring(line.indexOf("SIZE=") + 5))?.let {
                                    filesize = it
                                    Timber.d("Found filesize from 'SIZE=': $filesize")
                                    foundFilesize = true
                                }
                            }
                        }
                    }

                    if (foundLineOffset && foundFilesize) break
                }

                Timber.d("Final parse result - lineOffset: $lineOffset, filesize: $filesize")

                return if (foundLineOffset && foundFilesize) {
                    if (lineOffset > lines.size) {
                        Timber.e("Parsed lineOffset is greater than number of lines, invalid makeself file")
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

            /**
             * 从 GOG .sh 文件解析游戏数据信息。
             *
             * commons-compress ZipFile 可直接打开带 makeself 前导数据的完整 .sh 文件（preamble 自动跳过），
             * 因此无需先将 game_data.zip 解出为临时文件。
             */
            fun parseFromGogShFile(filePath: Path): GameDataZipFile? {
                if (MakeSelfShFile.parse(filePath) == null) {
                    Timber.e("MakeSelf SH file is null")
                    return null
                }
                return parse(filePath)
            }

            fun parse(filePath: Path): GameDataZipFile? {
                return try {
                    ZipFile.builder().setPath(filePath).get().use(::parse)
                } catch (e: Exception) {
                    Timber.e(e, "Exception when reading game_data.zip")
                    null
                }
            }

            fun parse(zipFile: ZipFile): GameDataZipFile? {
                val gameDataZipFile = GameDataZipFile()

                val gameInfoContent = getFileContent(zipFile, GAMEINFO_PATH)
                if (gameInfoContent != null) {
                    if (parseGameInfoContent(gameDataZipFile, gameInfoContent)) {
                        return gameDataZipFile
                    }
                    Timber.w("Failed to parse gameinfo content, trying config.lua...")
                }

                val configLuaContent = getFileContent(zipFile, CONFIG_LUA_PATH)
                if (configLuaContent != null) {
                    if (parseConfigLuaContent(gameDataZipFile, configLuaContent)) {
                        return gameDataZipFile
                    }
                    Timber.w("Failed to parse config.lua content")
                }

                Timber.e("Failed to parse game_data.zip content for id")
                return null
            }

            private fun getFileContent(zip: ZipFile, entryPath: String): String? {
                val entry = zip.getEntry(entryPath)
                if (entry == null) {
                    Timber.w("未在压缩包中找到 $entryPath")
                    return null
                }
                return try {
                    zip.getInputStream(entry).use { stream ->
                        Timber.d("Reading entry $entryPath...")
                        getFileContentFromStream(stream)
                    }
                } catch (e: IOException) {
                    Timber.w(e, "IOException when reading $entryPath")
                    null
                }
            }

            private fun getFileContentFromStream(inputStream: InputStream): String {
                val contentBuffer = ByteArray(MAX_CONTENT_SIZE)
                val bytesRead = inputStream.read(contentBuffer)
                Timber.d("Read $bytesRead bytes!")
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
                            Timber.d("Found id from config.lua: ${gameDataZipFile.id}")
                            return true
                        }
                    }
                }
                Timber.w("cannot extract id from $CONFIG_LUA_PATH")
                return false
            }

            private fun parseGameInfoContent(gameDataZipFile: GameDataZipFile, content: String): Boolean {
                val lines = content.split("\n")
                if (lines.isEmpty()) {
                    Timber.w("cannot even extract id from $GAMEINFO_PATH")
                    return false
                }

                gameDataZipFile.id = lines.getOrNull(0)?.trim()
                gameDataZipFile.version = lines.getOrNull(1)?.trim()
                gameDataZipFile.build = lines.getOrNull(2)?.trim()
                gameDataZipFile.locale = lines.getOrNull(3)?.trim()
                gameDataZipFile.timestamp1 = lines.getOrNull(4)?.trim()
                gameDataZipFile.timestamp2 = lines.getOrNull(5)?.trim()
                gameDataZipFile.gogId = lines.getOrNull(6)?.trim()

                Timber.d("Parsed gameinfo - $gameDataZipFile")

                return !gameDataZipFile.id.isNullOrEmpty()
            }

            private const val MAX_CONTENT_SIZE = 20480
        }
    }

    companion object {
        fun builder() = Builder()
    }
}
