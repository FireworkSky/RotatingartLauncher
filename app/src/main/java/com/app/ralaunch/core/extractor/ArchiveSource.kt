package com.app.ralaunch.core.extractor

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 解压数据源：本地文件路径或 SAF 返回的 content URI。
 *
 * tar/tar.gz 等流式格式通过 [openInputStream] 读取；zip/7z 等需要随机访问的格式
 * 通过 [openSeekableChannel] 读取。每次调用都会打开新的流/通道，由调用方负责关闭。
 */
sealed interface ArchiveSource {

    /** 打开新的只读流。 */
    fun openInputStream(): InputStream

    /** 打开新的可随机访问通道。 */
    fun openSeekableChannel(): SeekableByteChannel

    /** 数据源总大小（字节）。 */
    fun size(): Long

    /** 本地文件数据源。 */
    class PathSource(private val path: Path) : ArchiveSource {
        override fun toString(): String = path.toString()

        override fun openInputStream(): InputStream =
            Files.newInputStream(path, StandardOpenOption.READ)

        override fun openSeekableChannel(): SeekableByteChannel =
            FileChannel.open(path, StandardOpenOption.READ)

        override fun size(): Long = Files.size(path)
    }

    /**
     * SAF content URI 数据源。
     *
     * zip/7z 解析依赖 `ContentResolver.openFileDescriptor` 返回的可寻址通道；
     * 本地文件类 Provider（系统文件选择器、下载、外置存储）均支持。
     */
    class SafUriSource(context: Context, private val uri: Uri) : ArchiveSource {
        private val resolver = context.applicationContext.contentResolver

        override fun toString(): String = uri.toString()

        override fun openInputStream(): InputStream =
            resolver.openInputStream(uri)
                ?: throw IOException("无法打开 SAF URI: $uri")

        override fun openSeekableChannel(): FileChannel {
            val pfd = resolver.openFileDescriptor(uri, "r")
                ?: throw IOException("无法打开 SAF URI: $uri")
            // AutoCloseInputStream 强持有 pfd：通道存活期间（FileChannelImpl 以
            // parent 引用持有 stream），pfd 不会被终结器/CloseGuard 提前关闭；
            // channel.close() 依次联动关闭 stream 与 pfd，共享的 FileDescriptor
            // 在首次关闭后即失效，fd 恰好关闭一次。
            // 注：先前 detachFd 后直接复用 fd 的写法在部分真机 Provider 上
            // 会在通道使用期间被提前关闭（size() 抛 Bad file descriptor）。
            return ParcelFileDescriptor.AutoCloseInputStream(pfd).channel
        }

        override fun size(): Long =
            openSeekableChannel().use { it.size() }
    }

    companion object {
        fun of(path: Path): ArchiveSource = PathSource(path)

        fun of(context: Context, uri: Uri): ArchiveSource = SafUriSource(context, uri)
    }
}
