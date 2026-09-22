package com.app.ralaunch.core.extractor

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/**
 * 为本地文件注册一个可通过 content:// URI 访问的 ContentProvider，
 * 模拟 SAF 文件选择器返回的 Uri（openInputStream / openFileDescriptor 均可用）。
 */
object SafUriSources {
    fun register(context: Context, file: File): Uri {
        val authority = "ralaunch.test.${file.nameWithoutExtension}-${file.name.hashCode()}"
        val provider = FileContentProvider(file).apply {
            attachInfo(
                context,
                ProviderInfo().apply {
                    this.authority = authority
                    exported = true
                    grantUriPermissions = true
                }
            )
        }
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/file")
    }

    private class FileContentProvider(private val file: File) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun getType(uri: Uri): String = "application/octet-stream"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}
