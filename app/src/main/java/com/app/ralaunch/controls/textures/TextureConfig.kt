package com.app.ralaunch.controls.textures

import kotlinx.serialization.Serializable

/**
 * 纹理配置数据
 * 
 * 定义单个纹理的属性和渲染参数
 */
@Serializable
data class TextureConfig(
    /** 纹理文件路径（相对于 assets 目录） */
    val path: String = "",
    
    /** 是否启用纹理 */
    val enabled: Boolean = false,
    
    /** 纹理缩放模式 */
    val scaleMode: ScaleMode = ScaleMode.FIT,
    
    /** 纹理不透明度 (0.0-1.0) */
    val opacity: Float = 1.0f,
    
    /** 色调/着色颜色 (ARGB, 0 表示不着色) */
    val tintColor: Int = 0,
    
    /** 纹理内边距 (0.0-1.0, 相对于控件大小) */
    val padding: Float = 0.0f,
    
    /** 纹理旋转角度 (度) */
    val rotation: Float = 0.0f,
    
    /** 水平翻转 */
    val flipHorizontal: Boolean = false,
    
    /** 垂直翻转 */
    val flipVertical: Boolean = false,
    
    /** 圆角裁剪 (仅在控件为圆形时生效) */
    val clipToShape: Boolean = true
) {
    /**
     * 纹理缩放模式
     */
    enum class ScaleMode {
        /** 适应容器，保持宽高比，可能有空白 */
        FIT,
        
        /** 填充容器，保持宽高比，可能裁剪 */
        FILL,
        
        /** 拉伸填充，不保持宽高比 */
        STRETCH,
        
        /** 居中显示，不缩放 */
        CENTER,
        
        /** 平铺 */
        TILE
    }
    
    companion object {
        /** 创建空配置 */
        fun empty() = TextureConfig()
        
        /** 创建简单纹理配置 */
        fun simple(path: String, opacity: Float = 1.0f) = TextureConfig(
            path = path,
            enabled = true,
            opacity = opacity
        )
    }
}

/**
 * 按钮纹理配置
 * 
 * 按钮支持多状态纹理：普通、按下、切换
 */
@Serializable
data class ButtonTextureConfig(
    /** 普通状态纹理 */
    val normal: TextureConfig = TextureConfig(),
    
    /** 按下状态纹理 */
    val pressed: TextureConfig = TextureConfig(),
    
    /** 切换开启状态纹理 (仅 Toggle 按钮使用) */
    val toggled: TextureConfig = TextureConfig(),
    
    /** 禁用状态纹理 */
    val disabled: TextureConfig = TextureConfig()
) {
    /** 是否有任何纹理启用 */
    val hasAnyTexture: Boolean
        get() = normal.enabled || pressed.enabled || toggled.enabled || disabled.enabled
    
    companion object {
        fun empty() = ButtonTextureConfig()
        
        /** 创建简单按钮纹理配置（单一纹理适用于所有状态） */
        fun simple(normalPath: String, pressedPath: String? = null) = ButtonTextureConfig(
            normal = TextureConfig.simple(normalPath),
            pressed = if (pressedPath != null) TextureConfig.simple(pressedPath) else TextureConfig()
        )
    }
}

/**
 * 摇杆纹理配置
 * 
 * 摇杆有背景和摇杆头两个部分
 */
@Serializable
data class JoystickTextureConfig(
    /** 背景纹理 */
    val background: TextureConfig = TextureConfig(),
    
    /** 摇杆头/摇杆球纹理 */
    val knob: TextureConfig = TextureConfig(),
    
    /** 按下时的背景纹理 */
    val backgroundPressed: TextureConfig = TextureConfig(),
    
    /** 按下时的摇杆头纹理 */
    val knobPressed: TextureConfig = TextureConfig()
) {
    /** 是否有任何纹理启用 */
    val hasAnyTexture: Boolean
        get() = background.enabled || knob.enabled || backgroundPressed.enabled || knobPressed.enabled
    
    companion object {
        fun empty() = JoystickTextureConfig()
        
        /** 创建简单摇杆纹理配置 */
        fun simple(backgroundPath: String, knobPath: String) = JoystickTextureConfig(
            background = TextureConfig.simple(backgroundPath),
            knob = TextureConfig.simple(knobPath)
        )
    }
}

/**
 * 触控板纹理配置
 */
@Serializable
data class TouchPadTextureConfig(
    /** 背景纹理 */
    val background: TextureConfig = TextureConfig(),
    
    /** 触摸指示器纹理（可选） */
    val touchIndicator: TextureConfig = TextureConfig()
) {
    val hasAnyTexture: Boolean
        get() = background.enabled || touchIndicator.enabled
    
    companion object {
        fun empty() = TouchPadTextureConfig()
    }
}

/**
 * 鼠标滚轮纹理配置
 */
@Serializable
data class MouseWheelTextureConfig(
    /** 背景纹理 */
    val background: TextureConfig = TextureConfig(),

    /** 滚轮指示器纹理（可选） */
    val wheelIndicator: TextureConfig = TextureConfig()
) {
    val hasAnyTexture: Boolean
        get() = background.enabled || wheelIndicator.enabled

    companion object {
        fun empty() = MouseWheelTextureConfig()
    }
}

/**
 * 文本控件纹理配置
 */
@Serializable
data class TextControlTextureConfig(
    /** 背景纹理 */
    val background: TextureConfig = TextureConfig()
) {
    val hasAnyTexture: Boolean
        get() = background.enabled
    
    companion object {
        fun empty() = TextControlTextureConfig()
    }
}

/**
 * D-Pad纹理配置
 *
 * D-Pad由背景和8个方向按钮组成（上、右、下、左、左上、右上、右下、左下）
 */
@Serializable
data class DPadTextureConfig(
    /** 背景纹理 */
    val background: TextureConfig = TextureConfig(),

    /** 上按钮普通状态 */
    val upNormal: TextureConfig = TextureConfig(),
    /** 上按钮按下状态 */
    val upPressed: TextureConfig = TextureConfig(),

    /** 右按钮普通状态 */
    val rightNormal: TextureConfig = TextureConfig(),
    /** 右按钮按下状态 */
    val rightPressed: TextureConfig = TextureConfig(),

    /** 下按钮普通状态 */
    val downNormal: TextureConfig = TextureConfig(),
    /** 下按钮按下状态 */
    val downPressed: TextureConfig = TextureConfig(),

    /** 左按钮普通状态 */
    val leftNormal: TextureConfig = TextureConfig(),
    /** 左按钮按下状态 */
    val leftPressed: TextureConfig = TextureConfig(),

    /** 左上按钮普通状态 */
    val upLeftNormal: TextureConfig = TextureConfig(),
    /** 左上按钮按下状态 */
    val upLeftPressed: TextureConfig = TextureConfig(),

    /** 右上按钮普通状态 */
    val upRightNormal: TextureConfig = TextureConfig(),
    /** 右上按钮按下状态 */
    val upRightPressed: TextureConfig = TextureConfig(),

    /** 右下按钮普通状态 */
    val downRightNormal: TextureConfig = TextureConfig(),
    /** 右下按钮按下状态 */
    val downRightPressed: TextureConfig = TextureConfig(),

    /** 左下按钮普通状态 */
    val downLeftNormal: TextureConfig = TextureConfig(),
    /** 左下按钮按下状态 */
    val downLeftPressed: TextureConfig = TextureConfig()
) {
    val hasAnyTexture: Boolean
        get() = background.enabled ||
                upNormal.enabled || upPressed.enabled ||
                rightNormal.enabled || rightPressed.enabled ||
                downNormal.enabled || downPressed.enabled ||
                leftNormal.enabled || leftPressed.enabled ||
                upLeftNormal.enabled || upLeftPressed.enabled ||
                upRightNormal.enabled || upRightPressed.enabled ||
                downRightNormal.enabled || downRightPressed.enabled ||
                downLeftNormal.enabled || downLeftPressed.enabled

    companion object {
        fun empty() = DPadTextureConfig()
    }
}

