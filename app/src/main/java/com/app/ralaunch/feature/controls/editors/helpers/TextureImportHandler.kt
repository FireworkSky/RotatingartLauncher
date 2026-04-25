package com.app.ralaunch.feature.controls.editors.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.app.ralaunch.core.logging.AppLog
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.editors.ui.ControlEditorActivity
import java.io.File
import java.io.FileOutputStream

/**
 * 纹理导入处理器
 */
class TextureImportHandler(
    private val contextProvider: () -> Context?,
    private val activityProvider: () -> ControlEditorActivity?,
    private val currentDataProvider: () -> ControlData?,
    private val onTextureApplied: () -> Unit
) {
    companion object {
        private const val TAG = "TextureImportHandler"
        val SUPPORTED_TEXTURE_TYPES = arrayOf(
            "image/png", "image/jpeg", "image/webp", "image/bmp", "image/svg+xml"
        )
    }

    /**
     * 创建纹理选择 Intent
     */
    fun createTexturePickerIntent(): Intent? {
        val data = currentDataProvider() ?: return null
        val packId = activityProvider()?.getCurrentPackId()
        if (packId == null) {
            Toast.makeText(contextProvider(), R.string.pack_apply_failed, Toast.LENGTH_SHORT).show()
            return null
        }

        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_TEXTURE_TYPES)
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 导入并应用纹理
     */
    fun importAndApplyTexture(uri: Uri): Boolean {
        AppLog.d(TAG, "importAndApplyTexture: $uri")

        val context = contextProvider() ?: run {
            AppLog.e(TAG, "Context is null")
            return false
        }

        val packManager: ControlPackManager = KoinJavaComponent.get(ControlPackManager::class.java)
        val packId = activityProvider()?.getCurrentPackId() ?: run {
            AppLog.e(TAG, "PackId is null")
            return false
        }

        val data = currentDataProvider() ?: run {
            AppLog.e(TAG, "currentData is null")
            return false
        }

        return try {
            val fileName = getFileNameFromUri(context, uri) ?: "texture_${System.currentTimeMillis()}.png"
            val extension = fileName.substringAfterLast('.', "png").lowercase()

            if (extension !in listOf("png", "jpg", "jpeg", "webp", "bmp", "svg")) {
                Toast.makeText(context, R.string.control_texture_unsupported_format, Toast.LENGTH_SHORT).show()
                return false
            }

            val assetsDir = packManager.getOrCreatePackAssetsDir(packId) ?: run {
                Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
                return false
            }

            // 避免重名
            var targetFile = File(assetsDir, fileName)
            var counter = 1
            val nameWithoutExt = fileName.substringBeforeLast('.')
            while (targetFile.exists()) {
                targetFile = File(assetsDir, "${nameWithoutExt}_$counter.$extension")
                counter++
            }

            // 复制文件
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
                return false
            }

            val relativePath = targetFile.relativeTo(assetsDir).path.replace('\\', '/')
            applyTextureToControl(data, relativePath)

            Toast.makeText(context, R.string.control_texture_applied, Toast.LENGTH_SHORT).show()

            // 保存到文件
            activityProvider()?.let { activity ->
                activity.refreshPackAssetsDir()
                activity.updateControlData(data)
            }

            onTextureApplied()
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to import texture", e)
            Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun applyTextureToControl(data: ControlData, relativePath: String) {
        when (data) {
            is ControlData.Button -> {
                data.texture = data.texture.copy(
                    normal = data.texture.normal.copy(path = relativePath, enabled = true)
                )
            }
            is ControlData.Joystick -> {
                data.texture = data.texture.copy(
                    background = data.texture.background.copy(path = relativePath, enabled = true)
                )
            }
            is ControlData.TouchPad -> {
                data.texture = data.texture.copy(
                    background = data.texture.background.copy(path = relativePath, enabled = true)
                )
            }
            is ControlData.Text -> {
                data.texture = data.texture.copy(
                    background = data.texture.background.copy(path = relativePath, enabled = true)
                )
            }
            else -> {}
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }
}
