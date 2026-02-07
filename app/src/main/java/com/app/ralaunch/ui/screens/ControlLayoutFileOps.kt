package com.app.ralaunch.ui.screens

import android.net.Uri
import android.widget.Toast
import com.app.ralaunch.controls.packs.ControlPackManager
import java.io.File

/**
 * 导出控件包为 ZIP 文件
 */
internal fun exportPackToZip(
    context: android.content.Context,
    packManager: ControlPackManager,
    uri: Uri,
    packId: String
) {
    try {
        val tempFile = File(context.cacheDir, "export_temp.zip")
        
        val result = packManager.exportToFile(packId, tempFile)
        
        result.onSuccess { exportedFile ->
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                exportedFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导出失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
        
        tempFile.delete()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 从 URI 导入控件包
 */
internal fun importPackFromUri(
    context: android.content.Context,
    packManager: ControlPackManager,
    uri: Uri,
    onSuccess: () -> Unit
) {
    try {
        val tempFile = File(context.cacheDir, "import_temp.zip")
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        val result = packManager.installFromFile(tempFile)
        
        result.onSuccess { packInfo ->
            onSuccess()
            Toast.makeText(context, "导入成功: ${packInfo.name}", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导入失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
        
        tempFile.delete()
        
    } catch (e: Exception) {
        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
