package com.app.ralaunch.feature.controls.textures

import android.graphics.*
import kotlin.math.min

/**
 * 纹理渲染器
 * 
 * 负责将纹理绘制到 Canvas 上，支持各种缩放模式和变换
 */
object TextureRenderer {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val srcRect = Rect()
    private val dstRectF = RectF()
    
    /**
     * 渲染纹理到 Canvas
     * 
     * @param canvas 目标 Canvas
     * @param bitmap 纹理 Bitmap
     * @param config 纹理配置
     * @param bounds 绘制区域
     * @param clipPath 裁剪路径（可选，用于圆形等形状）
     */
    fun render(
        canvas: Canvas,
        bitmap: Bitmap,
        config: TextureConfig,
        bounds: RectF,
        clipPath: Path? = null,
        opacityMultiplier: Float = 1f
    ) {
        if (!config.enabled || bitmap.isRecycled) return
        
        canvas.save()
        
        // 应用裁剪
        if (config.clipToShape && clipPath != null) {
            canvas.clipPath(clipPath)
        }
        
        // 设置透明度
        paint.alpha = ((config.opacity.coerceIn(0f, 1f) * opacityMultiplier.coerceIn(0f, 1f)) * 255f)
            .toInt()
            .coerceIn(0, 255)
        
        // 设置着色
        if (config.tintColor != 0) {
            paint.colorFilter = PorterDuffColorFilter(config.tintColor, PorterDuff.Mode.SRC_IN)
        } else {
            paint.colorFilter = null
        }
        
        // 计算内边距
        val padding = min(bounds.width(), bounds.height()) * config.padding
        val paddedBounds = RectF(
            bounds.left + padding,
            bounds.top + padding,
            bounds.right - padding,
            bounds.bottom - padding
        )
        
        // 计算绘制矩阵
        calculateMatrix(bitmap, config, paddedBounds)
        
        // 应用旋转（围绕中心点）
        if (config.rotation != 0f) {
            matrix.postRotate(config.rotation, paddedBounds.centerX(), paddedBounds.centerY())
        }
        
        // 应用翻转
        if (config.flipHorizontal || config.flipVertical) {
            val scaleX = if (config.flipHorizontal) -1f else 1f
            val scaleY = if (config.flipVertical) -1f else 1f
            matrix.postScale(scaleX, scaleY, paddedBounds.centerX(), paddedBounds.centerY())
        }
        
        // 绘制
        canvas.drawBitmap(bitmap, matrix, paint)
        
        canvas.restore()
    }
    
    /**
     * 计算变换矩阵
     */
    private fun calculateMatrix(bitmap: Bitmap, config: TextureConfig, bounds: RectF) {
        matrix.reset()
        
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val boundsWidth = bounds.width()
        val boundsHeight = bounds.height()
        
        when (config.scaleMode) {
            TextureConfig.ScaleMode.FIT -> {
                // 适应容器，保持宽高比
                val scale = min(boundsWidth / bitmapWidth, boundsHeight / bitmapHeight)
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                val offsetX = bounds.left + (boundsWidth - scaledWidth) / 2
                val offsetY = bounds.top + (boundsHeight - scaledHeight) / 2
                
                matrix.setScale(scale, scale)
                matrix.postTranslate(offsetX, offsetY)
            }
            
            TextureConfig.ScaleMode.FILL -> {
                // 填充容器，保持宽高比，可能裁剪
                val scale = maxOf(boundsWidth / bitmapWidth, boundsHeight / bitmapHeight)
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                val offsetX = bounds.left + (boundsWidth - scaledWidth) / 2
                val offsetY = bounds.top + (boundsHeight - scaledHeight) / 2
                
                matrix.setScale(scale, scale)
                matrix.postTranslate(offsetX, offsetY)
            }
            
            TextureConfig.ScaleMode.STRETCH -> {
                // 拉伸填充，不保持宽高比
                val scaleX = boundsWidth / bitmapWidth
                val scaleY = boundsHeight / bitmapHeight
                
                matrix.setScale(scaleX, scaleY)
                matrix.postTranslate(bounds.left, bounds.top)
            }
            
            TextureConfig.ScaleMode.CENTER -> {
                // 居中显示，不缩放
                val offsetX = bounds.left + (boundsWidth - bitmapWidth) / 2
                val offsetY = bounds.top + (boundsHeight - bitmapHeight) / 2
                
                matrix.setTranslate(offsetX, offsetY)
            }
            
            TextureConfig.ScaleMode.TILE -> {
                // 平铺 - 需要特殊处理
                matrix.setTranslate(bounds.left, bounds.top)
            }
        }
    }
    
    /**
     * 渲染平铺纹理
     */
    fun renderTiled(
        canvas: Canvas,
        bitmap: Bitmap,
        config: TextureConfig,
        bounds: RectF,
        clipPath: Path? = null,
        opacityMultiplier: Float = 1f
    ) {
        if (!config.enabled || bitmap.isRecycled) return
        
        canvas.save()
        
        if (config.clipToShape && clipPath != null) {
            canvas.clipPath(clipPath)
        }
        
        paint.alpha = ((config.opacity.coerceIn(0f, 1f) * opacityMultiplier.coerceIn(0f, 1f)) * 255f)
            .toInt()
            .coerceIn(0, 255)
        
        if (config.tintColor != 0) {
            paint.colorFilter = PorterDuffColorFilter(config.tintColor, PorterDuff.Mode.SRC_IN)
        } else {
            paint.colorFilter = null
        }
        
        // 创建平铺 Shader
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.shader = shader
        
        canvas.drawRect(bounds, paint)
        
        paint.shader = null
        canvas.restore()
    }
    
    /**
     * 渲染按钮纹理
     */
    fun renderButton(
        canvas: Canvas,
        textureLoader: TextureLoader,
        assetsDir: java.io.File?,
        textureConfig: ButtonTextureConfig,
        bounds: RectF,
        isPressed: Boolean,
        isToggled: Boolean,
        isEnabled: Boolean = true,
        clipPath: Path? = null,
        opacityMultiplier: Float = 1f
    ) {
        if (!textureConfig.hasAnyTexture || assetsDir == null) return
        
        // 选择合适的纹理配置
        val config = when {
            !isEnabled && textureConfig.disabled.enabled -> textureConfig.disabled
            isToggled && textureConfig.toggled.enabled -> textureConfig.toggled
            isPressed && textureConfig.pressed.enabled -> textureConfig.pressed
            textureConfig.normal.enabled -> textureConfig.normal
            else -> return
        }
        
        val bitmap = textureLoader.loadPackTexture(
            assetsDir,
            config.path,
            bounds.width().toInt(),
            bounds.height().toInt()
        ) ?: return
        
        if (config.scaleMode == TextureConfig.ScaleMode.TILE) {
            renderTiled(canvas, bitmap, config, bounds, clipPath, opacityMultiplier)
        } else {
            render(canvas, bitmap, config, bounds, clipPath, opacityMultiplier)
        }
    }
    
    /**
     * 渲染摇杆纹理
     */
    fun renderJoystick(
        canvas: Canvas,
        textureLoader: TextureLoader,
        assetsDir: java.io.File?,
        textureConfig: JoystickTextureConfig,
        backgroundBounds: RectF,
        knobBounds: RectF,
        isPressed: Boolean,
        backgroundClipPath: Path? = null,
        knobClipPath: Path? = null,
        backgroundOpacityMultiplier: Float = 1f,
        knobOpacityMultiplier: Float = 1f
    ) {
        if (!textureConfig.hasAnyTexture || assetsDir == null) return
        
        // 渲染背景
        val bgConfig = if (isPressed && textureConfig.backgroundPressed.enabled) {
            textureConfig.backgroundPressed
        } else {
            textureConfig.background
        }
        
        if (bgConfig.enabled) {
            val bgBitmap = textureLoader.loadPackTexture(
                assetsDir,
                bgConfig.path,
                backgroundBounds.width().toInt(),
                backgroundBounds.height().toInt()
            )
            if (bgBitmap != null) {
                if (bgConfig.scaleMode == TextureConfig.ScaleMode.TILE) {
                    renderTiled(
                        canvas,
                        bgBitmap,
                        bgConfig,
                        backgroundBounds,
                        backgroundClipPath,
                        backgroundOpacityMultiplier
                    )
                } else {
                    render(
                        canvas,
                        bgBitmap,
                        bgConfig,
                        backgroundBounds,
                        backgroundClipPath,
                        backgroundOpacityMultiplier
                    )
                }
            }
        }
        
        // 渲染摇杆头
        val knobConfig = if (isPressed && textureConfig.knobPressed.enabled) {
            textureConfig.knobPressed
        } else {
            textureConfig.knob
        }
        
        if (knobConfig.enabled) {
            val knobBitmap = textureLoader.loadPackTexture(
                assetsDir,
                knobConfig.path,
                knobBounds.width().toInt(),
                knobBounds.height().toInt()
            )
            if (knobBitmap != null) {
                if (knobConfig.scaleMode == TextureConfig.ScaleMode.TILE) {
                    renderTiled(
                        canvas,
                        knobBitmap,
                        knobConfig,
                        knobBounds,
                        knobClipPath,
                        knobOpacityMultiplier
                    )
                } else {
                    render(
                        canvas,
                        knobBitmap,
                        knobConfig,
                        knobBounds,
                        knobClipPath,
                        knobOpacityMultiplier
                    )
                }
            }
        }
    }

    /**
     * 创建圆形裁剪路径
     */
    fun createCircleClipPath(centerX: Float, centerY: Float, radius: Float): Path {
        return Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
        }
    }
    
    /**
     * 创建圆角矩形裁剪路径
     */
    fun createRoundRectClipPath(bounds: RectF, cornerRadius: Float): Path {
        return Path().apply {
            addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        }
    }
}



