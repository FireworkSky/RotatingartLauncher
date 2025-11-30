package com.app.ralaunch.ui.model

import android.annotation.SuppressLint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val extension: String = "",
    val isHidden: Boolean = false
) {
    val formattedSize: String
        get() = if (isDirectory) "" else formatFileSize(size)

    val formattedDate: String
        get() = formatDate(modifiedTime)

    companion object {
        fun fromFile(file: File): FileItem {
            return FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                modifiedTime = file.lastModified(),
                extension = if (file.isFile) {
                    file.name.substringAfterLast('.', "").lowercase()
                } else "",
                isHidden = file.isHidden || file.name.startsWith(".")
            )
        }

        @SuppressLint("DefaultLocale")
        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
                size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
                else -> "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }

        private fun formatDate(timestamp: Long): String {
            val date = Date(timestamp)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return format.format(date)
        }
    }
}