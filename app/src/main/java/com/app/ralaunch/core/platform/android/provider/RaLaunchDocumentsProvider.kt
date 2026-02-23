package com.app.ralaunch.core.platform.android.provider

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.Os
import android.webkit.MimeTypeMap
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths

/**
 * RaLaunch 文档提供器
 * 在系统文件管理器中显示启动器的所有文件目录
 *
 * 显示的目录结构：
 * - data/          内部数据目录 (/data/data/com.app.ralaunch/)
 * - android_data/  外部数据目录 (/Android/data/com.app.ralaunch/files/)
 * - android_obb/   OBB 目录 (/Android/obb/com.app.ralaunch/)
 * - user_de_data/  设备加密数据目录（如果存在）
 */
class RaLaunchDocumentsProvider : DocumentsProvider() {

    private lateinit var mPackageName: String
    private lateinit var mDataDir: File
    private var mUserDeDataDir: File? = null
    private var mAndroidDataDir: File? = null
    private var mAndroidObbDir: File? = null

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)

        mPackageName = context.packageName
        mDataDir = context.filesDir.parentFile!!

        // 设备加密数据目录（Android 7.0+）
        val dataDirPath = mDataDir.path
        if (dataDirPath.startsWith("/data/user/")) {
            mUserDeDataDir = File("/data/user_de/" + dataDirPath.substring(11))
        }

        // 外部数据目录
        context.getExternalFilesDir(null)?.let {
            mAndroidDataDir = it.parentFile
        }

        // OBB 目录
        mAndroidObbDir = context.obbDir
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val appInfo = context!!.applicationInfo
        val appName = appInfo.loadLabel(context!!.packageManager).toString()

        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, mPackageName)
            add(Root.COLUMN_DOCUMENT_ID, mPackageName)
            add(Root.COLUMN_SUMMARY, context!!.getString(R.string.documents_provider_root_summary))
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, appName)
            add(Root.COLUMN_MIME_TYPES, "*/*")
            add(Root.COLUMN_ICON, appInfo.icon)
        }

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        var docId = parentDocumentId
        if (docId.endsWith("/")) {
            docId = docId.substring(0, docId.length - 1)
        }

        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(docId)

        if (parent == null) {
            // 根目录：显示所有主要目录
            includeFile(result, "$docId/data", mDataDir)

            mAndroidDataDir?.takeIf { it.exists() }?.let {
                includeFile(result, "$docId/android_data", it)
            }

            mAndroidObbDir?.takeIf { it.exists() }?.let {
                includeFile(result, "$docId/android_obb", it)
            }

            mUserDeDataDir?.takeIf { it.exists() }?.let {
                includeFile(result, "$docId/user_de_data", it)
            }
        } else {
            parent.listFiles()?.forEach { file ->
                includeFile(result, "$docId/${file.name}", file)
            }
        }

        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId, checkExists = false)
            ?: throw FileNotFoundException("$documentId not found")
        val fileMode = parseFileMode(mode)
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
            ?: throw FileNotFoundException("Failed to create document in $parentDocumentId")

        var newFile = File(parent, displayName)
        var noConflictId = 2
        while (newFile.exists()) {
            newFile = File(parent, "$displayName ($noConflictId)")
            noConflictId++
        }

        val succeeded = if (Document.MIME_TYPE_DIR == mimeType) {
            newFile.mkdir()
        } else {
            try {
                newFile.createNewFile()
            } catch (e: IOException) {
                false
            }
        }

        if (succeeded) {
            return if (parentDocumentId.endsWith("/")) {
                parentDocumentId + newFile.name
            } else {
                "$parentDocumentId/${newFile.name}"
            }
        }
        throw FileNotFoundException("Failed to create document in $parentDocumentId")
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
            ?: throw FileNotFoundException("Failed to delete document $documentId")

        // 对于符号链接，直接删除
        if (isSymbolicLink(file)) {
            if (!file.delete()) {
                throw FileNotFoundException("Failed to delete document $documentId")
            }
            return
        }

        // 使用 ralib 的 FileUtils 删除目录
        val success = FileUtils.deleteDirectoryRecursively(Paths.get(file.absolutePath))
        if (!success) {
            throw FileNotFoundException("Failed to delete document $documentId")
        }
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
            ?: throw FileNotFoundException("Failed to rename document $documentId")

        val target = File(file.parentFile, displayName)
        if (file.renameTo(target)) {
            val i = documentId.lastIndexOf('/', documentId.length - 2)
            return documentId.substring(0, i + 1) + displayName
        }
        throw FileNotFoundException("Failed to rename document $documentId")
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val sourceFile = getFileForDocId(sourceDocumentId)
        val targetDir = getFileForDocId(targetParentDocumentId)

        if (sourceFile != null && targetDir != null) {
            val targetFile = File(targetDir, sourceFile.name)
            if (!targetFile.exists() && sourceFile.renameTo(targetFile)) {
                return if (targetParentDocumentId.endsWith("/")) {
                    targetParentDocumentId + targetFile.name
                } else {
                    "$targetParentDocumentId/${targetFile.name}"
                }
            }
        }
        throw FileNotFoundException("Failed to move document $sourceDocumentId")
    }

    override fun getDocumentType(documentId: String): String {
        val file = getFileForDocId(documentId)
        return if (file == null) Document.MIME_TYPE_DIR else getMimeTypeForFile(file)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith(parentDocumentId)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = super.call(method, arg, extras)
        if (result != null) return result

        if (!method.startsWith("ralaunch:")) return null

        val out = Bundle()
        try {
            val uri = extras?.getParcelable<Uri>("uri") ?: return out
            val pathSegments = uri.pathSegments
            val documentId = if (pathSegments.size >= 4) pathSegments[3] else pathSegments[1]

            when (method) {
                METHOD_SET_LAST_MODIFIED -> {
                    val file = getFileForDocId(documentId)
                    if (file == null) {
                        out.putBoolean("result", false)
                    } else {
                        val time = extras.getLong("time")
                        out.putBoolean("result", file.setLastModified(time))
                    }
                }
                METHOD_SET_PERMISSIONS -> {
                    val file = getFileForDocId(documentId)
                    if (file == null) {
                        out.putBoolean("result", false)
                    } else {
                        val permissions = extras.getInt("permissions")
                        try {
                            Os.chmod(file.path, permissions)
                            out.putBoolean("result", true)
                        } catch (e: ErrnoException) {
                            out.putBoolean("result", false)
                            out.putString("message", e.message)
                        }
                    }
                }
                METHOD_CREATE_SYMLINK -> {
                    val file = getFileForDocId(documentId, checkExists = false)
                    if (file == null) {
                        out.putBoolean("result", false)
                    } else {
                        val path = extras.getString("path")
                        try {
                            Os.symlink(path, file.path)
                            out.putBoolean("result", true)
                        } catch (e: ErrnoException) {
                            out.putBoolean("result", false)
                            out.putString("message", e.message)
                        }
                    }
                }
                else -> {
                    out.putBoolean("result", false)
                    out.putString("message", "Unsupported method: $method")
                }
            }
        } catch (e: Exception) {
            out.putBoolean("result", false)
            out.putString("message", e.toString())
        }
        return out
    }

    @Suppress("SameParameterValue")
    private fun includeFile(result: MatrixCursor, docId: String, file: File?) {
        var targetFile = file
        if (targetFile == null) {
            targetFile = getFileForDocId(docId)
        }

        // 根目录
        if (targetFile == null) {
            result.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, mPackageName)
                add(Document.COLUMN_DISPLAY_NAME, mPackageName)
                add(Document.COLUMN_SIZE, 0L)
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_LAST_MODIFIED, 0)
                add(Document.COLUMN_FLAGS, 0)
            }
            return
        }

        // 计算标志位
        var flags = 0
        if (targetFile.isDirectory) {
            if (targetFile.canWrite()) {
                flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
            }
        } else if (targetFile.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }

        targetFile.parentFile?.takeIf { it.canWrite() }?.let {
            flags = flags or Document.FLAG_SUPPORTS_DELETE
            flags = flags or Document.FLAG_SUPPORTS_RENAME
            flags = flags or Document.FLAG_SUPPORTS_MOVE
        }

        // 确定显示名称
        val path = targetFile.path
        var addExtras = false
        val displayName = when (path) {
            mDataDir.path -> "data"
            mAndroidDataDir?.path -> "android_data"
            mAndroidObbDir?.path -> "android_obb"
            mUserDeDataDir?.path -> "user_de_data "
            else -> {
                addExtras = true
                targetFile.name
            }
        }

        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docId)
            add(Document.COLUMN_DISPLAY_NAME, displayName)
            add(Document.COLUMN_SIZE, targetFile.length())
            add(Document.COLUMN_MIME_TYPE, getMimeTypeForFile(targetFile))
            add(Document.COLUMN_LAST_MODIFIED, targetFile.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(COLUMN_FILE_PATH, targetFile.absolutePath)

            // 添加扩展信息
            if (addExtras) {
                try {
                    val stat = Os.lstat(path)
                    val sb = StringBuilder()
                    sb.append(stat.st_mode)
                        .append("|").append(stat.st_uid)
                        .append("|").append(stat.st_gid)

                    // 如果是符号链接，添加链接目标
                    @Suppress("OctalInteger")
                    if ((stat.st_mode and 0x1F000) == 0xA000) { // S_IFLNK
                        sb.append("|").append(Os.readlink(path))
                    }
                    add(COLUMN_FILE_EXTRAS, sb.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getFileForDocId(docId: String, checkExists: Boolean = true): File? {
        var filename = docId

        // 移除包名前缀
        if (filename.startsWith(mPackageName)) {
            filename = filename.substring(mPackageName.length)
        } else {
            throw FileNotFoundException("$docId not found")
        }

        // 移除开头的斜杠
        if (filename.startsWith("/")) {
            filename = filename.substring(1)
        }

        // 根目录
        if (filename.isEmpty()) {
            return null
        }

        // 解析路径类型和子路径
        val i = filename.indexOf('/')
        val type: String
        val subPath: String
        if (i == -1) {
            type = filename
            subPath = ""
        } else {
            type = filename.substring(0, i)
            subPath = filename.substring(i + 1)
        }

        // 根据类型获取基础目录
        val file: File? = when {
            type.equals("data", ignoreCase = true) -> File(mDataDir, subPath)
            type.equals("android_data", ignoreCase = true) && mAndroidDataDir != null ->
                File(mAndroidDataDir!!, subPath)
            type.equals("android_obb", ignoreCase = true) && mAndroidObbDir != null ->
                File(mAndroidObbDir!!, subPath)
            type.equals("user_de_data", ignoreCase = true) && mUserDeDataDir != null ->
                File(mUserDeDataDir!!, subPath)
            else -> null
        }

        if (file == null) {
            throw FileNotFoundException("$docId not found")
        }

        // 检查文件是否存在
        if (checkExists) {
            try {
                Os.lstat(file.path)
            } catch (e: Exception) {
                throw FileNotFoundException("$docId not found: ${e.message}")
            }
        }

        return file
    }

    companion object {
        const val AUTHORITY = "com.app.ralaunch.documents"
        const val COLUMN_FILE_PATH = "ralaunch_file_path"
        const val COLUMN_FILE_EXTRAS = "ralaunch_file_extras"
        const val METHOD_SET_LAST_MODIFIED = "ralaunch:setLastModified"
        const val METHOD_SET_PERMISSIONS = "ralaunch:setPermissions"
        const val METHOD_CREATE_SYMLINK = "ralaunch:createSymlink"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            COLUMN_FILE_EXTRAS
        )

        private fun parseFileMode(mode: String): Int = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_APPEND
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE
            "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            else -> throw IllegalArgumentException("Invalid mode: $mode")
        }

        @Suppress("OctalInteger")
        private fun isSymbolicLink(file: File): Boolean {
            return try {
                val stat = Os.lstat(file.path)
                (stat.st_mode and 0x1F000) == 0xA000 // S_IFLNK = 0120000
            } catch (e: ErrnoException) {
                false
            }
        }

        private fun getMimeTypeForFile(file: File): String {
            if (file.isDirectory) {
                return Document.MIME_TYPE_DIR
            }

            val name = file.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) {
                    return mime
                }
            }

            return "application/octet-stream"
        }
    }
}
