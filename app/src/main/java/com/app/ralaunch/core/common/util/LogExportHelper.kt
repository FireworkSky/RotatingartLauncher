package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.shared.core.platform.AppConstants
import java.io.File

internal object LogExportHelper {

    fun getLogsDir(context: Context): File? {
        val baseDir = context.getExternalFilesDir(null) ?: return null
        return File(baseDir, AppConstants.Dirs.LOGS).also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    fun getLogFiles(context: Context): List<File> {
        val logsDir = getLogsDir(context) ?: return emptyList()
        return logsDir
            .listFiles { file -> file.isFile && file.extension.equals("log", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getLatestLogFile(context: Context): File? = getLogFiles(context).firstOrNull()

    fun buildExportContent(context: Context): String {
        val files = getLogFiles(context).sortedBy { it.lastModified() }
        if (files.isEmpty()) return ""

        return buildString {
            files.forEachIndexed { index, file ->
                appendLine("===== ${file.name} =====")
                append(file.readText())
                if (!endsWith("\n")) {
                    appendLine()
                }
                if (index != files.lastIndex) {
                    appendLine()
                }
            }
        }
    }
}
