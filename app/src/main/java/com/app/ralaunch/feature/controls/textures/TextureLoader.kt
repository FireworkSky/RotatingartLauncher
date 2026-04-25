package com.app.ralaunch.feature.controls.textures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.util.LruCache
import com.app.ralaunch.core.logging.AppLog
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference

/**
 * 纹理加载器
 * 
 * 负责加载、缓存和管理纹理资源
 * 支持格式: PNG, JPG, JPEG, WebP, SVG
 * 
 * 特性:
 * - LRU 内存缓存
 * - 自动缩放以适应目标尺寸
 * - SVG 矢量图支持（可缩放到任意尺寸）
 * - 线程安全
 */
class TextureLoader private constructor(context: Context) {
    
    companion object {
        private const val TAG = "TextureLoader"
        
        /** 缓存大小: 可用内存的 1/8 */
        private val MAX_CACHE_SIZE = (Runtime.getRuntime().maxMemory() / 8).toInt()
        
        /** 支持的图片格式 */
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")
        
        /** SVG 格式 */
        private const val SVG_EXTENSION = "svg"
        
        @Volatile
        private var instance: TextureLoader? = null
        
        fun getInstance(context: Context): TextureLoader {
            return instance ?: synchronized(this) {
                instance ?: TextureLoader(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val contextRef = WeakReference(context.applicationContext)
    
    /** Bitmap 缓存 */
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
        
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                // 可选：回收旧 Bitmap
                // oldValue.recycle()
            }
        }
    }
    
    /** SVG 缓存（缓存 SVG 对象，渲染时按需生成 Bitmap） */
    private val svgCache = LruCache<String, SVG>(50)
    
    /**
     * 加载纹理
     * 
     * @param path 纹理文件的绝对路径
     * @param targetWidth 目标宽度（用于缩放，0 表示原始尺寸）
     * @param targetHeight 目标高度（用于缩放，0 表示原始尺寸）
     * @return 加载的 Bitmap，失败返回 null
     */
    fun loadTexture(path: String, targetWidth: Int = 0, targetHeight: Int = 0): Bitmap? {
        if (path.isEmpty()) return null
        
        val file = File(path)
        if (!file.exists()) {
            AppLog.w(TAG, "Texture file not found: $path")
            return null
        }
        
        val extension = file.extension.lowercase()
        val cacheKey = "$path|${targetWidth}x${targetHeight}"
        
        // 检查缓存
        bitmapCache.get(cacheKey)?.let { 
            if (!it.isRecycled) return it 
        }
        
        return try {
            val bitmap = when {
                extension == SVG_EXTENSION -> loadSvg(file, targetWidth, targetHeight)
                extension in SUPPORTED_IMAGE_EXTENSIONS -> loadBitmap(file, targetWidth, targetHeight)
                else -> {
                    AppLog.w(TAG, "Unsupported texture format: $extension")
                    null
                }
            }
            
            bitmap?.also { bitmapCache.put(cacheKey, it) }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load texture: $path", e)
            null
        }
    }
    
    /**
     * 从控件包加载纹理
     * 
     * @param packAssetsDir 控件包的 assets 目录
     * @param relativePath 纹理相对路径
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     */
    fun loadPackTexture(
        packAssetsDir: File,
        relativePath: String,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        val file = File(packAssetsDir, relativePath)
        return loadTexture(file.absolutePath, targetWidth, targetHeight)
    }
    
    /**
     * 加载 Bitmap 图片
     */
    private fun loadBitmap(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            // 首先获取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            FileInputStream(file).use { 
                BitmapFactory.decodeStream(it, null, options)
            }
            
            // 计算缩放比例
            options.inSampleSize = calculateInSampleSize(
                options.outWidth, 
                options.outHeight, 
                targetWidth, 
                targetHeight
            )
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            // 加载 Bitmap
            FileInputStream(file).use { 
                BitmapFactory.decodeStream(it, null, options)
            }?.let { bitmap ->
                // 如果需要精确缩放
                if (targetWidth > 0 && targetHeight > 0 && 
                    (bitmap.width != targetWidth || bitmap.height != targetHeight)) {
                    Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                        if (it != bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load bitmap: ${file.path}", e)
            null
        }
    }
    
    /**
     * 加载 SVG 矢量图
     */
    private fun loadSvg(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            // 尝试从缓存获取 SVG
            val cacheKey = file.absolutePath
            var svg = svgCache.get(cacheKey)
            
            if (svg == null) {
                svg = FileInputStream(file).use { SVG.getFromInputStream(it) }
                svgCache.put(cacheKey, svg)
            }
            
            // 确定渲染尺寸
            val width = if (targetWidth > 0) targetWidth else svg.documentWidth.toInt().coerceAtLeast(100)
            val height = if (targetHeight > 0) targetHeight else svg.documentHeight.toInt().coerceAtLeast(100)
            
            // 渲染 SVG 到 Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 设置 SVG 视口
            svg.documentWidth = width.toFloat()
            svg.documentHeight = height.toFloat()
            
            svg.renderToCanvas(canvas)
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load SVG: ${file.path}", e)
            null
        }
    }
    
    /**
     * 计算采样率
     */
    private fun calculateInSampleSize(
        srcWidth: Int, 
        srcHeight: Int, 
        reqWidth: Int, 
        reqHeight: Int
    ): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        
        var inSampleSize = 1
        
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && 
                   (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 预加载控件包的所有纹理
     */
    fun preloadPackTextures(packAssetsDir: File) {
        if (!packAssetsDir.exists() || !packAssetsDir.isDirectory) return
        
        Thread {
            packAssetsDir.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS + SVG_EXTENSION }
                .forEach { file ->
                    loadTexture(file.absolutePath)
                }
            AppLog.i(TAG, "Preloaded textures from: ${packAssetsDir.path}")
        }.start()
    }
    
    /**
     * 清除指定路径的缓存
     */
    fun evictFromCache(path: String) {
        // 移除所有与该路径相关的缓存
        val keysToRemove = mutableListOf<String>()
        bitmapCache.snapshot().keys.forEach { key ->
            if (key.startsWith(path)) {
                keysToRemove.add(key)
            }
        }
        keysToRemove.forEach { bitmapCache.remove(it) }
        svgCache.remove(path)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearCache() {
        bitmapCache.evictAll()
        svgCache.evictAll()
        AppLog.i(TAG, "Texture cache cleared")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): String {
        return "Bitmap Cache: ${bitmapCache.size()}/${bitmapCache.maxSize()} bytes, " +
               "SVG Cache: ${svgCache.size()}/50 items"
    }
    
    /**
     * 检查文件是否是支持的纹理格式
     */
    fun isSupportedFormat(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in SUPPORTED_IMAGE_EXTENSIONS || ext == SVG_EXTENSION
    }
    
    /**
     * 获取纹理信息
     */
    fun getTextureInfo(path: String): TextureInfo? {
        val file = File(path)
        if (!file.exists()) return null
        
        val extension = file.extension.lowercase()
        
        return try {
            when {
                extension == SVG_EXTENSION -> {
                    val svg = FileInputStream(file).use { SVG.getFromInputStream(it) }
                    TextureInfo(
                        width = svg.documentWidth.toInt(),
                        height = svg.documentHeight.toInt(),
                        format = TextureFormat.SVG,
                        fileSize = file.length()
                    )
                }
                extension in SUPPORTED_IMAGE_EXTENSIONS -> {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    FileInputStream(file).use { BitmapFactory.decodeStream(it, null, options) }
                    TextureInfo(
                        width = options.outWidth,
                        height = options.outHeight,
                        format = TextureFormat.fromExtension(extension),
                        fileSize = file.length()
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 纹理信息
     */
    data class TextureInfo(
        val width: Int,
        val height: Int,
        val format: TextureFormat,
        val fileSize: Long
    )
    
    /**
     * 纹理格式
     */
    enum class TextureFormat {
        PNG, JPG, WEBP, BMP, SVG, UNKNOWN;
        
        companion object {
            fun fromExtension(ext: String): TextureFormat {
                return when (ext.lowercase()) {
                    "png" -> PNG
                    "jpg", "jpeg" -> JPG
                    "webp" -> WEBP
                    "bmp" -> BMP
                    "svg" -> SVG
                    else -> UNKNOWN
                }
            }
        }
    }
}

