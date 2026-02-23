package com.app.ralaunch.core.platform.install.extractors

import android.util.Log
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * GOG .sh 文件提取器
 */
class GogShFileExtractor(
    sourcePath: Path,
    destinationPath: Path,
    listener: ExtractorCollection.ExtractionListener?
) : ExtractorCollection.IExtractor {

    private lateinit var sourcePath: Path
    private lateinit var destinationPath: Path
    private var extractionListener: ExtractorCollection.ExtractionListener? = null
    override var state: HashMap<String, Any?> = hashMapOf()

    init {
        setSourcePath(sourcePath)
        setDestinationPath(destinationPath)
        setExtractionListener(listener)
    }

    override fun setSourcePath(sourcePath: Path) {
        this.sourcePath = sourcePath
    }

    override fun setDestinationPath(destinationPath: Path) {
        this.destinationPath = destinationPath
    }

    override fun setExtractionListener(listener: ExtractorCollection.ExtractionListener?) {
        this.extractionListener = listener
    }

    override fun extract(): Boolean {
        return try {
            TemporaryFileAcquirer().use { tfa ->
                // 获取 MakeSelf SH 文件的头部信息
                extractionListener?.onProgress(
                    RaLaunchApp.getInstance().getString(R.string.extract_gog_script),
                    0.01f,
                    state
                )
                val shFile = MakeSelfShFile.parse(sourcePath)
                    ?: throw IOException("解析 MakeSelf Sh 文件头部失败")

                Log.d(TAG, "Successfully parsed header - offset: ${shFile.offset}, filesize: ${shFile.filesize}")

                FileInputStream(sourcePath.toFile()).use { fis ->
                    Log.d(TAG, "Starting extraction: $sourcePath to $destinationPath")

                    Files.createDirectories(destinationPath)
                    val srcChannel = fis.channel

                    // sanity check
                    if (shFile.offset + shFile.filesize > srcChannel.size()) {
                        throw IOException("MakeSelf Sh 文件头部信息无效，超出文件总大小")
                    }

                    extractionListener?.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.extract_gog_mojosetup),
                        0.02f,
                        state
                    )

                    // 提取 mojosetup.tar.gz
                    val mojosetupPath = tfa.acquireTempFilePath(EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME)
                    FileOutputStream(mojosetupPath.toFile()).use { mojosetupFos ->
                        val mojosetupChannel = mojosetupFos.channel
                        Log.d(TAG, "Extracting mojosetup.tar.gz to $mojosetupPath")
                        srcChannel.transferTo(shFile.offset, shFile.filesize, mojosetupChannel)
                    }

                    extractionListener?.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.extract_gog_game_data),
                        0.03f,
                        state
                    )

                    // 提取 game_data.zip
                    val gameDataPath = tfa.acquireTempFilePath(EXTRACTED_GAME_DATA_ZIP_FILENAME)
                    FileOutputStream(gameDataPath.toFile()).use { gameDataFos ->
                        val gameDataChannel = gameDataFos.channel
                        Log.d(TAG, "Extracting game_data.zip to $gameDataPath")
                        srcChannel.transferTo(
                            shFile.offset + shFile.filesize,
                            srcChannel.size() - (shFile.offset + shFile.filesize),
                            gameDataChannel
                        )
                    }

                    extractionListener?.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.extract_gog_parse_game_data),
                        0.09f,
                        state
                    )
                    Log.d(TAG, "Extraction from MakeSelf SH file completed successfully")

                    // 解压 game_data.zip
                    Log.d(TAG, "Trying to extract game_data.zip...")
                    val gdzf = GameDataZipFile.parse(gameDataPath)
                        ?: throw IOException("解析 game_data.zip 失败")

                    extractionListener?.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.extract_gog_decompress_game_data),
                        0.1f,
                        state
                    )

                    val gamePath = destinationPath.resolve(Paths.get("GoG Games", gdzf.id))
                    val zipExtractor = BasicSevenZipExtractor(
                        gameDataPath,
                        Paths.get("data/noarch/game"),
                        gamePath,
                        object : ExtractorCollection.ExtractionListener {
                            override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {
                                extractionListener?.onProgress(message, 0.1f + progress * 0.9f, state)
                            }

                            override fun onComplete(message: String, state: HashMap<String, Any?>?) {}

                            override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                                throw RuntimeException(message, ex)
                            }
                        }
                    )
                    zipExtractor.state = state
                    val isGameDataExtracted = zipExtractor.extract()
                    if (!isGameDataExtracted) {
                        throw IOException("解压 game_data.zip 失败")
                    }

                    // 提取图标
                    try {
                        val iconExtractor = BasicSevenZipExtractor(
                            gameDataPath,
                            Paths.get("data/noarch/support"),
                            gamePath.resolve("support"),
                            null
                        )
                        iconExtractor.extract()
                    } catch (ignored: Exception) {
                    }

                    val completedMessage = RaLaunchApp.getInstance()
                        .getString(R.string.extract_gog_game_data_complete)
                    extractionListener?.onProgress(completedMessage, 1.0f, state)
                    state[STATE_KEY_GAME_PATH] = gamePath
                    state[STATE_KEY_GAME_DATA_ZIP_FILE] = gdzf
                    extractionListener?.onComplete(completedMessage, state)

                    true
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error when extracting source file", ex)
            extractionListener?.onError(
                RaLaunchApp.getInstance().getString(R.string.extract_gog_sh_failed),
                ex,
                state
            )
            false
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
                    FileInputStream(filePath.toFile()).use { fis ->
                        val bytesRead = fis.read(headerBuffer)
                        Log.d(TAG, "Read $bytesRead bytes from header")
                        headerContent = String(headerBuffer, 0, bytesRead, StandardCharsets.UTF_8)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error when reading MakeSelf SH file", ex)
                    return null
                }

                return parseMakeSelfShFileContent(headerContent)
            }

            private fun parseMakeSelfShFileContent(content: String): MakeSelfShFile? {
                Log.d(TAG, "Parsing makeself file content, content size: ${content.length}")

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
                                    Log.d(TAG, "Found lineOffset from 'head -n': $lineOffset")
                                    foundLineOffset = true
                                }
                            }
                            line.contains("SKIP=") -> {
                                extractNumber(line.substring(line.indexOf("SKIP=") + 5))?.let {
                                    lineOffset = it
                                    Log.d(TAG, "Found lineOffset from 'SKIP=': $lineOffset")
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
                                    Log.d(TAG, "Found filesize from 'filesizes=': $filesize")
                                    foundFilesize = true
                                }
                            }
                            line.contains("SIZE=") -> {
                                extractNumber(line.substring(line.indexOf("SIZE=") + 5))?.let {
                                    filesize = it
                                    Log.d(TAG, "Found filesize from 'SIZE=': $filesize")
                                    foundFilesize = true
                                }
                            }
                        }
                    }

                    if (foundLineOffset && foundFilesize) break
                }

                Log.d(TAG, "Final parse result - lineOffset: $lineOffset, filesize: $filesize")

                return if (foundLineOffset && foundFilesize) {
                    if (lineOffset > lines.size) {
                        Log.e(TAG, "Parsed lineOffset is greater than number of lines, invalid makeself file")
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
                    Log.e(TAG, "MakeSelf SH file is null")
                    return null
                }

                return try {
                    TemporaryFileAcquirer().use { tfa ->
                        val tempZipFile = tfa.acquireTempFilePath("temp_game_data.zip")

                        RandomAccessFile(filePath.toFile(), "r").use { raf ->
                            FileOutputStream(tempZipFile.toFile()).use { fos ->
                                val gameDataStart = shFile.offset + shFile.filesize
                                raf.seek(gameDataStart)

                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (raf.read(buffer).also { bytesRead = it } != -1) {
                                    fos.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        parse(tempZipFile)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error when reading GOG SH file: $filePath", ex)
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
                            Log.w(TAG, "Failed to parse gameinfo content, trying config.lua...")
                        }

                        val configLuaContent = getFileContent(zip, CONFIG_LUA_PATH)
                        if (configLuaContent != null) {
                            if (parseConfigLuaContent(gameDataZipFile, configLuaContent)) {
                                return@use gameDataZipFile
                            }
                            Log.w(TAG, "Failed to parse config.lua content")
                        }

                        Log.e(TAG, "Failed to parse game_data.zip content for id")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception when reading game_data.zip", e)
                    null
                }
            }

            private fun getFileContent(zip: ZipFile, entryPath: String): String? {
                val entry = zip.getEntry(entryPath)
                if (entry == null) {
                    Log.w(TAG, "未在压缩包中找到 $entryPath")
                    return null
                }
                return try {
                    zip.getInputStream(entry).use { stream ->
                        Log.d(TAG, "Reading entry $entryPath...")
                        getFileContentFromStream(stream)
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "IOException when reading $entryPath", e)
                    null
                }
            }

            private fun getFileContentFromStream(inputStream: InputStream): String {
                val contentBuffer = ByteArray(MAX_CONTENT_SIZE)
                val bytesRead = inputStream.read(contentBuffer)
                Log.d(TAG, "Read $bytesRead bytes!")
                return String(contentBuffer, 0, bytesRead, StandardCharsets.UTF_8)
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
                            Log.d(TAG, "Found id from config.lua: ${gameDataZipFile.id}")
                            return true
                        }
                    }
                }
                Log.w(TAG, "cannot extract id from $CONFIG_LUA_PATH")
                return false
            }

            private fun parseGameInfoContent(gameDataZipFile: GameDataZipFile, content: String): Boolean {
                val lines = content.split("\n")
                if (lines.isEmpty()) {
                    Log.w(TAG, "cannot even extract id from $GAMEINFO_PATH")
                    return false
                }

                gameDataZipFile.id = lines.getOrNull(0)?.trim()
                gameDataZipFile.version = lines.getOrNull(1)?.trim()
                gameDataZipFile.build = lines.getOrNull(2)?.trim()
                gameDataZipFile.locale = lines.getOrNull(3)?.trim()
                gameDataZipFile.timestamp1 = lines.getOrNull(4)?.trim()
                gameDataZipFile.timestamp2 = lines.getOrNull(5)?.trim()
                gameDataZipFile.gogId = lines.getOrNull(6)?.trim()

                Log.d(TAG, "Parsed gameinfo - $gameDataZipFile")

                return !gameDataZipFile.id.isNullOrEmpty()
            }

            private const val MAX_CONTENT_SIZE = 20480
        }
    }

    companion object {
        private const val TAG = "GogShFileExtractor"
        private const val EXTRACTED_MOJOSETUP_TAR_GZ_FILENAME = "mojosetup.tar.gz"
        private const val EXTRACTED_GAME_DATA_ZIP_FILENAME = "game_data.zip"

        const val STATE_KEY_GAME_PATH = "GogShFileExtractor.game_path"
        const val STATE_KEY_GAME_DATA_ZIP_FILE = "GogShFileExtractor.game_data_zip_file"
    }
}
