package com.app.ralaunch.core.common.util

import com.app.ralaunch.core.logging.AppLog
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * 文件操作工具类
 */
object FileUtils {
    private const val TAG = "FileUtils"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 100L

    /**
     * 递归删除目录及其内容
     * @param path 要删除的路径
     * @return 删除是否成功
     */
    @JvmStatic
    fun deleteDirectoryRecursively(path: Path?): Boolean {
        if (path == null) return false
        val normalizedPath = path.absolute().normalize()
        if (!Files.exists(normalizedPath, LinkOption.NOFOLLOW_LINKS)) return true
        if (!Files.isReadable(normalizedPath)) return false

        val allDeleted = AtomicBoolean(true)

        return try {
            Files.walkFileTree(
                normalizedPath,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (!deletePathWithRetry(file)) {
                            allDeleted.set(false)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                        if (!deletePathWithRetry(file)) {
                            allDeleted.set(false)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (exc != null) {
                            allDeleted.set(false)
                        }
                        if (!deletePathWithRetry(dir)) {
                            allDeleted.set(false)
                        }
                        return FileVisitResult.CONTINUE
                    }
                }
            )
            allDeleted.get() && !Files.exists(normalizedPath, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            true
        } catch (e: AccessDeniedException) {
            AppLog.w(TAG, "删除失败（权限）: $normalizedPath")
            false
        } catch (e: SecurityException) {
            AppLog.w(TAG, "删除失败（权限）: $normalizedPath")
            false
        } catch (e: IOException) {
            AppLog.w(TAG, "删除失败: $normalizedPath")
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "删除失败: $normalizedPath", e)
            false
        }
    }

    /**
     * 仅当目标路径位于指定根目录内时才递归删除。
     * 根目录本身不会被删除。
     */
    @JvmStatic
    fun deleteDirectoryRecursivelyWithinRoot(path: Path?, allowedRoot: Path?): Boolean {
        val normalizedTarget = normalizePath(path) ?: return false
        val normalizedRoot = normalizePath(allowedRoot) ?: return false
        if (!isStrictChildOf(normalizedTarget, normalizedRoot)) return false
        return deleteDirectoryRecursively(normalizedTarget)
    }

    @JvmStatic
    fun deleteDirectoryRecursivelyWithinRoot(directory: File?, allowedRoot: File?): Boolean {
        val targetPath = directory?.toPath()
        val rootPath = allowedRoot?.toPath()
        return deleteDirectoryRecursivelyWithinRoot(targetPath, rootPath)
    }

    /**
     * 仅当文件位于指定根目录内时才删除该文件。
     * 目录不会通过此接口删除。
     */
    @JvmStatic
    fun deleteFileWithinRoot(path: Path?, allowedRoot: Path?): Boolean {
        val normalizedTarget = normalizePath(path) ?: return false
        val normalizedRoot = normalizePath(allowedRoot) ?: return false
        if (!isStrictChildOf(normalizedTarget, normalizedRoot)) return false
        if (normalizedTarget.isDirectory(LinkOption.NOFOLLOW_LINKS)) return false
        return deletePathWithRetry(normalizedTarget)
    }

    @JvmStatic
    fun deleteFileWithinRoot(file: File?, allowedRoot: File?): Boolean {
        val targetPath = file?.toPath()
        val rootPath = allowedRoot?.toPath()
        return deleteFileWithinRoot(targetPath, rootPath)
    }

    /**
     * 清空目录内容，但保留目录本身。
     */
    @JvmStatic
    fun clearDirectory(directory: File?): Boolean {
        val normalizedDirectory = directory?.toPath()?.absolute()?.normalize() ?: return false
        if (!normalizedDirectory.exists(LinkOption.NOFOLLOW_LINKS)) return true
        if (!normalizedDirectory.isDirectory(LinkOption.NOFOLLOW_LINKS)) return false

        return normalizedDirectory.listDirectoryEntries().all { child ->
            if (child.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
                deleteDirectoryRecursivelyWithinRoot(child, normalizedDirectory)
            } else {
                deleteFileWithinRoot(child, normalizedDirectory)
            }
        }
    }

    /**
     * 带重试机制的路径删除
     */
    private fun deletePathWithRetry(path: Path): Boolean {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
                Files.delete(path)
                return true
            } catch (e: AccessDeniedException) {
                return false
            } catch (e: SecurityException) {
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
        return false
    }

    /**
     * 递归删除目录及其内容（File 参数版本）
     * @param directory 要删除的目录
     * @return 删除是否成功
     */
    @JvmStatic
    fun deleteDirectoryRecursively(directory: File?): Boolean {
        if (directory == null) return false
        if (!directory.exists()) return true
        if (!directory.canRead()) return false

        return try {
            val path = directory.toPath().toAbsolutePath().normalize()
            deleteDirectoryRecursively(path)
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizePath(path: Path?): Path? = path?.absolute()?.normalize()

    private fun isStrictChildOf(path: Path, root: Path): Boolean = path != root && path.startsWith(root)
}
