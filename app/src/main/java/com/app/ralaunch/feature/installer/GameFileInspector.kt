package com.app.ralaunch.feature.installer

import com.app.ralaunch.core.extractor.ArchiveSource
import com.app.ralaunch.core.extractor.GogShFileExtractor
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber

/**
 * 游戏文件容器识别：基于文件内部特征判断，不依赖文件名。
 *
 * 支持两类容器：
 * - zip 压缩包（含被 makeself 前导数据包裹前的普通 zip），通过魔数 `PK` 识别，
 *   记录全部条目路径供插件按内部条目签名匹配
 * - GOG makeself 安装器（.sh），通过 `#!` 脚本头识别，
 *   解析内嵌 game_data.zip 中 `data/noarch/gameinfo` 的游戏名/版本
 */
object GameFileInspector {

    /**
     * 识别出的容器结构
     */
    sealed interface Container {

        /**
         * 常规 zip 压缩包
         *
         * @param entryPaths 规范化后的条目路径（目录条目已去掉尾部 '/'，保留原始大小写）
         */
        data class Zip(val entryPaths: List<String>) : Container {

            /**
             * 是否存在完整路径为 [path] 的条目（大小写不敏感；[path] 尾部 '/' 可省略）
             */
            fun hasPath(path: String): Boolean {
                val normalized = path.trimEnd('/')
                if (normalized.isEmpty()) return false
                return entryPaths.any { it.equals(normalized, ignoreCase = true) }
            }

            /**
             * 返回文件名（路径最后一段）为 [fileName] 的首个条目完整路径；不存在返回 null
             */
            fun findEntryNamed(fileName: String): String? =
                entryPaths.firstOrNull { it.substringAfterLast('/').equals(fileName, ignoreCase = true) }

            /**
             * 是否存在文件名为 [fileName] 的条目（任意层级，大小写不敏感）
             */
            fun hasEntryNamed(fileName: String): Boolean = findEntryNamed(fileName) != null
        }

        /**
         * GOG makeself 安装器（.sh），[gameInfo] 为 gameinfo 解析结果
         * （id=游戏名，version=版本号）
         */
        data class GogInstaller(val gameInfo: GogShFileExtractor.GameDataZipFile) : Container
    }

    /**
     * 识别数据源容器；不是 zip 也不是 GOG 安装器时返回 null
     */
    fun inspect(source: ArchiveSource): Container? = try {
        when {
            isZip(source) -> inspectZip(source)
            isShellScript(source) -> inspectGogInstaller(source)
            else -> null
        }
    } catch (e: Exception) {
        Timber.e(e, "识别游戏文件容器失败: %s", source)
        null
    }

    /** zip 魔数：PK\x03\x04（本地文件头）/ PK\x05\x06（空包 EOCD）/ PK\x07\x08（分卷） */
    private fun isZip(source: ArchiveSource): Boolean {
        val header = readHeader(source) ?: return false
        return header.size >= 4 &&
            header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
    }

    /** 脚本头 `#!`，GOG makeself 安装器是 shell 脚本 */
    private fun isShellScript(source: ArchiveSource): Boolean {
        val header = readHeader(source) ?: return false
        return header.size >= 2 &&
            header[0] == '#'.code.toByte() &&
            header[1] == '!'.code.toByte()
    }

    private fun readHeader(source: ArchiveSource): ByteArray? {
        return try {
            source.openInputStream().use { input ->
                val header = ByteArray(4)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read == 0) null else header.copyOf(read)
            }
        } catch (e: Exception) {
            Timber.e(e, "读取文件头失败: %s", source)
            null
        }
    }

    private fun inspectZip(source: ArchiveSource): Container.Zip? {
        return ZipFile.builder()
            .setSeekableByteChannel(source.openSeekableChannel())
            .get()
            .use { zip ->
                Container.Zip(
                    zip.entriesInPhysicalOrder.toList()
                        .map { it.name.trimEnd('/') }
                        .filter { it.isNotEmpty() }
                )
            }
    }

    private fun inspectGogInstaller(source: ArchiveSource): Container.GogInstaller? {
        // 解析 makeself 头 + 内嵌 game_data.zip 的 gameinfo（结构非法时返回 null）
        return GogShFileExtractor.GameDataZipFile.parseFromGogShFile(source)
            ?.let { Container.GogInstaller(it) }
    }
}
