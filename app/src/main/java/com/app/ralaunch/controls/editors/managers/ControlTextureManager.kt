package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.view.View
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.textures.*
import java.io.File

/**
 * 控件纹理管理器
 * 统一管理控件纹理的选择和显示逻辑
 */
object ControlTextureManager {
    
    /**
     * 获取当前控件包的资源目录
     */
    fun getPackAssetsDir(packId: String?): File? {
        if (packId == null) return null
        return RaLaunchApplication.getControlPackManager().getPackAssetsDir(packId)
    }
    
    /**
     * 获取所有可用纹理文件
     */
    fun getAvailableTextures(packId: String?): List<TextureFileInfo> {
        if (packId == null) return emptyList()
        
        val assetsDir = getPackAssetsDir(packId) ?: return emptyList()
        if (!assetsDir.exists()) return emptyList()
        
        return assetsDir.walkTopDown()
            .filter { it.isFile }
            .filter { isTextureFile(it) }
            .map { file ->
                TextureFileInfo(
                    name = file.name,
                    relativePath = file.relativeTo(assetsDir).path.replace('\\', '/'),
                    absolutePath = file.absolutePath,
                    format = getTextureFormat(file),
                    fileSize = file.length()
                )
            }
            .sortedBy { it.name }
            .toList()
    }
    
    /**
     * 检查是否是纹理文件
     */
    private fun isTextureFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("png", "jpg", "jpeg", "webp", "bmp", "svg")
    }
    
    /**
     * 获取纹理格式
     */
    private fun getTextureFormat(file: File): String {
        return file.extension.uppercase()
    }
    
    /**
     * 检查控件是否支持纹理
     */
    fun supportsTexture(data: ControlData?): Boolean {
        return data is ControlData.Button || 
               data is ControlData.Joystick ||
               data is ControlData.TouchPad ||
               data is ControlData.MouseWheel ||
               data is ControlData.Text ||
               data is ControlData.DPad
    }
    
    /**
     * 检查控件是否配置了纹理
     */
    fun hasTextureEnabled(data: ControlData?): Boolean {
        return when (data) {
            is ControlData.Button -> data.texture.hasAnyTexture
            is ControlData.Joystick -> data.texture.hasAnyTexture
            is ControlData.TouchPad -> data.texture.hasAnyTexture
            is ControlData.MouseWheel -> data.texture.hasAnyTexture
            is ControlData.Text -> data.texture.hasAnyTexture
            is ControlData.DPad -> data.texture.hasAnyTexture
            else -> false
        }
    }
    
    /**
     * 获取纹理状态显示文本
     */
    fun getTextureStatusText(context: Context, data: ControlData?): String {
        if (data == null || !supportsTexture(data)) {
            return context.getString(R.string.control_texture_not_supported)
        }
        
        return if (hasTextureEnabled(data)) {
            context.getString(R.string.control_texture_enabled)
        } else {
            context.getString(R.string.control_texture_disabled)
        }
    }
    
    /**
     * 更新纹理状态显示
     */
    fun updateTextureDisplay(
        context: Context?,
        data: ControlData?,
        textView: TextView?,
        textureItemView: View?
    ) {
        if (context == null || data == null) {
            textureItemView?.visibility = View.GONE
            return
        }
        
        if (supportsTexture(data)) {
            textureItemView?.visibility = View.VISIBLE
            textView?.text = getTextureStatusText(context, data)
        } else {
            textureItemView?.visibility = View.GONE
        }
    }
    
    /**
     * 清除控件的所有纹理配置
     */
    fun clearTextures(data: ControlData?) {
        when (data) {
            is ControlData.Button -> {
                data.texture = ButtonTextureConfig()
            }
            is ControlData.Joystick -> {
                data.texture = JoystickTextureConfig()
            }
            is ControlData.TouchPad -> {
                data.texture = TouchPadTextureConfig()
            }
            is ControlData.MouseWheel -> {
                data.texture = MouseWheelTextureConfig()
            }
            is ControlData.Text -> {
                data.texture = TextControlTextureConfig()
            }
            is ControlData.DPad -> {
                data.texture = DPadTextureConfig()
            }
            null -> {} // 数据为空时不做任何操作
        }
    }
    
    /**
     * 设置按钮普通状态纹理
     */
    fun setButtonNormalTexture(data: ControlData.Button, texturePath: String, enabled: Boolean = true) {
        data.texture = data.texture.copy(
            normal = data.texture.normal.copy(
                path = texturePath,
                enabled = enabled
            )
        )
    }
    
    /**
     * 设置按钮按下状态纹理
     */
    fun setButtonPressedTexture(data: ControlData.Button, texturePath: String, enabled: Boolean = true) {
        data.texture = data.texture.copy(
            pressed = data.texture.pressed.copy(
                path = texturePath,
                enabled = enabled
            )
        )
    }
    
    /**
     * 设置摇杆背景纹理
     */
    fun setJoystickBackgroundTexture(data: ControlData.Joystick, texturePath: String, enabled: Boolean = true) {
        data.texture = data.texture.copy(
            background = data.texture.background.copy(
                path = texturePath,
                enabled = enabled
            )
        )
    }
    
    /**
     * 设置摇杆头纹理
     */
    fun setJoystickKnobTexture(data: ControlData.Joystick, texturePath: String, enabled: Boolean = true) {
        data.texture = data.texture.copy(
            knob = data.texture.knob.copy(
                path = texturePath,
                enabled = enabled
            )
        )
    }
    
    /**
     * 纹理选择监听器
     */
    interface OnTextureSelectedListener {
        fun onTextureSelected(data: ControlData?)
    }
    
    /**
     * 纹理文件信息
     */
    data class TextureFileInfo(
        val name: String,
        val relativePath: String,
        val absolutePath: String,
        val format: String,
        val fileSize: Long
    ) {
        val displayName: String
            get() = "$name ($format)"
        
        val fileSizeText: String
            get() {
                return when {
                    fileSize < 1024 -> "$fileSize B"
                    fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
                    else -> "${fileSize / (1024 * 1024)} MB"
                }
            }
    }
}

