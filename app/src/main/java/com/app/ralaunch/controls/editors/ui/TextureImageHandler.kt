package com.app.ralaunch.controls.editors.ui

import android.content.Context
import android.net.Uri
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.controls.textures.TextureConfig
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import java.io.File
import java.io.FileOutputStream

/**
 * 处理图片选择结果，将图片复制到控件包资源目录并更新纹理配置
 */
internal fun handleImagePicked(
    context: Context,
    uri: Uri,
    packManager: ControlPackManager,
    control: ControlData,
    textureType: String,
    controlLayoutView: ControlLayoutView,
    onControlUpdated: (ControlData) -> Unit
) {
    try {
        val layout = controlLayoutView.currentLayout ?: return
        val packId = layout.id
        
        val assetsDir = packManager.getOrCreatePackAssetsDir(packId) ?: return
        
        val fileName = "texture_${System.currentTimeMillis()}.png"
        val targetFile = File(assetsDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        val relativePath = targetFile.name
        
        val textureLoader = TextureLoader.getInstance(context)
        textureLoader.evictFromCache(targetFile.absolutePath)
        
        val updated = when (control) {
            is ControlData.Button -> {
                val btn = control.deepCopy() as ControlData.Button
                val newConfig = TextureConfig(path = relativePath, enabled = true)
                btn.texture = when (textureType) {
                    "normal" -> btn.texture.copy(normal = newConfig)
                    "pressed" -> btn.texture.copy(pressed = newConfig)
                    "toggled" -> btn.texture.copy(toggled = newConfig)
                    else -> btn.texture
                }
                btn
            }
            is ControlData.Joystick -> {
                val js = control.deepCopy() as ControlData.Joystick
                val newConfig = TextureConfig(path = relativePath, enabled = true)
                js.texture = when (textureType) {
                    "background" -> js.texture.copy(background = newConfig)
                    "knob" -> js.texture.copy(knob = newConfig)
                    "backgroundPressed" -> js.texture.copy(backgroundPressed = newConfig)
                    "knobPressed" -> js.texture.copy(knobPressed = newConfig)
                    else -> js.texture
                }
                js
            }
            is ControlData.TouchPad -> {
                val tp = control.deepCopy() as ControlData.TouchPad
                val newConfig = TextureConfig(path = relativePath, enabled = true)
                tp.texture = tp.texture.copy(background = newConfig)
                tp
            }
            is ControlData.MouseWheel -> {
                val mw = control.deepCopy() as ControlData.MouseWheel
                val newConfig = TextureConfig(path = relativePath, enabled = true)
                mw.texture = mw.texture.copy(background = newConfig)
                mw
            }
            is ControlData.Text -> {
                val txt = control.deepCopy() as ControlData.Text
                val newConfig = TextureConfig(path = relativePath, enabled = true)
                txt.texture = txt.texture.copy(background = newConfig)
                txt
            }
            else -> return
        }
        
        val index = layout.controls.indexOfFirst { it.id == control.id }
        if (index >= 0) {
            layout.controls[index] = updated
            controlLayoutView.setPackAssetsDir(assetsDir)
            controlLayoutView.loadLayout(layout)
            controlLayoutView.invalidate()
            onControlUpdated(updated)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
