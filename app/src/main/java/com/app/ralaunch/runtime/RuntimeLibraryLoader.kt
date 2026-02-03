package com.app.ralaunch.runtime

import android.content.Context
import com.app.ralaunch.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 运行时库加载器
 * 
 * 负责从 assets 中解压大型 native 库到私有目录。
 * 这些库在编译时不链接到主程序，而是在运行时按需加载。
 * 
 * 支持的库：
 * - libmobileglues.so (8 MB) - GLES 转换层
 * - libSkiaSharp.so (7 MB) - Skia 图形库
 * - libGLESv2_angle.so (5 MB) - ANGLE OpenGL ES
 * - libGL_gl4es.so (4 MB) - GL4ES OpenGL
 * - lib7-Zip-JBinding.so (3 MB) - 7-Zip 解压库
 */
object RuntimeLibraryLoader {
    
    private const val TAG = "RuntimeLibLoader"
    
    // 运行时库存放目录
    private const val RUNTIME_LIBS_DIR = "runtime_libs"
    
    // assets 中的压缩包名
    private const val RUNTIME_LIBS_ARCHIVE = "runtime_libs.tar.xz"
    
    // 版本标记文件，用于判断是否需要重新解压
    private const val VERSION_FILE = ".version"
    
    // 已加载的库集合
    private val loadedLibraries = mutableSetOf<String>()
    
    /**
     * 运行时库枚举
     */
    enum class RuntimeLib(val fileName: String, val description: String) {
        MOBILEGLUES("libmobileglues.so", "MobileGlues 翻译层"),
        SKIASHARP("libSkiaSharp.so", "Skia 图形库"),
        ANGLE_EGL("libEGL_angle.so", "ANGLE EGL"),
        ANGLE_GLES("libGLESv2_angle.so", "ANGLE OpenGL ES"),
        GL4ES_EGL("libEGL_gl4es.so", "GL4ES EGL"),
        GL4ES("libGL_gl4es.so", "GL4ES OpenGL")
        // 7-Zip 保留在 APK 中，通过 System.loadLibrary 加载
    }
    
    /**
     * 必须存在的关键库文件列表
     */
    private val REQUIRED_LIBS = listOf(
        "libGL_gl4es.so",
        "libEGL_gl4es.so"
    )
    
    /**
     * 检查运行时库是否已解压
     */
    fun isExtracted(context: Context): Boolean {
        val runtimeDir = File(context.filesDir, RUNTIME_LIBS_DIR)
        val versionFile = File(runtimeDir, VERSION_FILE)
        
        if (!runtimeDir.exists() || !versionFile.exists()) {
            AppLogger.info(TAG, "Runtime libs not extracted: directory or version file missing")
            return false
        }
        
        // 检查关键库文件是否存在
        for (libName in REQUIRED_LIBS) {
            val libFile = File(runtimeDir, libName)
            if (!libFile.exists() || libFile.length() == 0L) {
                AppLogger.warn(TAG, "Required library missing or empty: $libName")
                return false
            }
        }
        
        // 检查版本是否匹配
        val currentVersion = getAssetVersion(context)
        val extractedVersion = versionFile.readText().trim()
        
        if (currentVersion != extractedVersion) {
            AppLogger.info(TAG, "Version mismatch: current=$currentVersion, extracted=$extractedVersion")
            return false
        }
        
        return true
    }
    
    /**
     * 获取 assets 中压缩包的版本
     * 使用 AssetFileDescriptor 获取真实文件大小
     */
    private fun getAssetVersion(context: Context): String {
        return try {
            context.assets.openFd(RUNTIME_LIBS_ARCHIVE).use { afd ->
                val size = afd.length
                "v2_$size"  // v2 表示新的版本计算方式
            }
        } catch (e: Exception) {
            // openFd 可能对压缩的 assets 失败，回退到读取方式
            try {
                context.assets.open(RUNTIME_LIBS_ARCHIVE).use { stream ->
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        size += read
                    }
                    "v2_$size"
                }
            } catch (e2: Exception) {
                AppLogger.error(TAG, "Failed to get asset version: ${e2.message}")
                "unknown"
            }
        }
    }
    
    /**
     * 获取运行时库目录
     */
    fun getRuntimeLibsDir(context: Context): File {
        return File(context.filesDir, RUNTIME_LIBS_DIR)
    }
    
    /**
     * 获取指定库的路径
     */
    fun getLibraryPath(context: Context, libName: String): String {
        return File(getRuntimeLibsDir(context), libName).absolutePath
    }
    
    /**
     * 解压运行时库
     * 
     * @param context Android Context
     * @param progressCallback 进度回调 (0-100)
     */
    suspend fun extractRuntimeLibs(
        context: Context,
        progressCallback: ((Int, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val runtimeDir = File(context.filesDir, RUNTIME_LIBS_DIR)
        
        try {
            // 检查 assets 中是否存在压缩包
            val assetList = context.assets.list("") ?: emptyArray()
            if (RUNTIME_LIBS_ARCHIVE !in assetList) {
                AppLogger.warn(TAG, "Runtime libs archive not found in assets: $RUNTIME_LIBS_ARCHIVE")
                AppLogger.warn(TAG, "This is expected if libs are bundled in APK directly")
                return@withContext true  // 如果没有压缩包，认为是直接打包模式
            }
            
            AppLogger.info(TAG, "Extracting runtime libraries...")
            progressCallback?.invoke(0, "准备解压运行时库...")
            
            // 清理旧目录
            if (runtimeDir.exists()) {
                runtimeDir.deleteRecursively()
            }
            runtimeDir.mkdirs()
            
            // 解压 tar.xz
            context.assets.open(RUNTIME_LIBS_ARCHIVE).use { assetStream ->
                val bufferedStream = BufferedInputStream(assetStream, 1024 * 1024)
                val xzStream = XZInputStream(bufferedStream)
                val tarStream = TarArchiveInputStream(xzStream)
                
                var entry = tarStream.nextEntry
                var extractedCount = 0
                
                while (entry != null) {
                    val outputFile = File(runtimeDir, entry.name)
                    
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        
                        FileOutputStream(outputFile).use { fos ->
                            tarStream.copyTo(fos)
                        }
                        
                        // 设置库文件权限（可读 + 可执行）
                        if (entry.name.endsWith(".so")) {
                            outputFile.setReadable(true, false)  // 所有用户可读
                            outputFile.setExecutable(true, false) // 所有用户可执行
                            AppLogger.debug(TAG, "Set permissions for: ${entry.name}")
                        }
                        
                        extractedCount++
                        val progress = (extractedCount * 100) / 10  // 假设约10个文件
                        progressCallback?.invoke(minOf(progress, 95), "解压: ${entry.name}")
                        
                        AppLogger.debug(TAG, "Extracted: ${entry.name}")
                    }
                    
                    entry = tarStream.nextEntry
                }
            }
            
            // 验证关键库是否都已解压
            val missingLibs = REQUIRED_LIBS.filter { libName ->
                val libFile = File(runtimeDir, libName)
                !libFile.exists() || libFile.length() == 0L
            }
            
            if (missingLibs.isNotEmpty()) {
                AppLogger.error(TAG, "Missing required libraries after extraction: $missingLibs")
                progressCallback?.invoke(-1, "解压不完整，缺少: ${missingLibs.joinToString()}")
                return@withContext false
            }
            
            // 验证成功后写入版本标记
            val versionFile = File(runtimeDir, VERSION_FILE)
            versionFile.writeText(getAssetVersion(context))
            
            // 列出所有已解压的库
            val extractedLibs = listExtractedLibraries(context)
            AppLogger.info(TAG, "Runtime libraries extracted: ${extractedLibs.joinToString()}")
            
            progressCallback?.invoke(100, "解压完成")
            AppLogger.info(TAG, "Runtime libraries extracted to: ${runtimeDir.absolutePath}")
            
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to extract runtime libs", e)
            progressCallback?.invoke(-1, "解压失败: ${e.message}")
            // 清理可能的不完整解压
            try {
                runtimeDir.deleteRecursively()
            } catch (_: Exception) {}
            false
        }
    }
    
    /**
     * 强制重新解压运行时库
     * 删除版本文件后重新解压
     */
    suspend fun forceReExtract(
        context: Context,
        progressCallback: ((Int, String) -> Unit)? = null
    ): Boolean {
        val runtimeDir = File(context.filesDir, RUNTIME_LIBS_DIR)
        
        // 删除整个目录强制重新解压
        if (runtimeDir.exists()) {
            runtimeDir.deleteRecursively()
        }
        
        return extractRuntimeLibs(context, progressCallback)
    }
    
    /**
     * 列出已解压的运行时库
     */
    fun listExtractedLibraries(context: Context): List<String> {
        val runtimeDir = getRuntimeLibsDir(context)
        if (!runtimeDir.exists()) return emptyList()
        
        return runtimeDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?.map { it.name }
            ?: emptyList()
    }
    
    /**
     * 加载指定的运行时库
     * 
     * @param context Android Context
     * @param lib 要加载的库
     * @return true 如果加载成功
     */
    fun loadLibrary(context: Context, lib: RuntimeLib): Boolean {
        if (loadedLibraries.contains(lib.fileName)) {
            AppLogger.debug(TAG, "${lib.fileName} already loaded")
            return true
        }
        
        val libPath = getLibraryPath(context, lib.fileName)
        val libFile = File(libPath)
        
        if (!libFile.exists()) {
            AppLogger.error(TAG, "${lib.fileName} not found at: $libPath")
            return false
        }
        
        return try {
            AppLogger.info(TAG, "Loading ${lib.description}: ${lib.fileName}")
            System.load(libPath)
            loadedLibraries.add(lib.fileName)
            AppLogger.info(TAG, "${lib.fileName} loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "Failed to load ${lib.fileName}", e)
            false
        }
    }
    
    /**
     * 检查库是否已加载
     */
    fun isLibraryLoaded(lib: RuntimeLib): Boolean {
        return loadedLibraries.contains(lib.fileName)
    }
    
    /**
     * 加载所有渲染器相关的库
     * 用于游戏启动前预加载
     */
    fun loadRendererLibraries(context: Context, useAngle: Boolean): Boolean {
        var success = true
        
        // GL4ES 通常需要
        if (!loadLibrary(context, RuntimeLib.GL4ES)) {
            AppLogger.warn(TAG, "GL4ES load failed, may use fallback")
        }
        
        // ANGLE (可选) - 需要同时加载 EGL 和 GLES
        if (useAngle) {
            if (!loadLibrary(context, RuntimeLib.ANGLE_EGL)) {
                AppLogger.warn(TAG, "ANGLE EGL load failed")
            }
            if (!loadLibrary(context, RuntimeLib.ANGLE_GLES)) {
                AppLogger.warn(TAG, "ANGLE GLES load failed")
                success = false
            }
        }
        
        return success
    }
    
    /**
     * 加载 SkiaSharp 库
     * 用于需要 Skia 渲染的游戏（如某些 .NET 游戏）
     */
    fun loadSkiaSharp(context: Context): Boolean {
        return loadLibrary(context, RuntimeLib.SKIASHARP)
    }
    
    /**
     * 加载 7-Zip 库
     * 用于解压游戏文件
     * 注意：7-Zip 库现在保留在 APK 中，此方法作为备选从 runtime_libs 加载
     */
    fun load7Zip(context: Context): Boolean {
        // 尝试从 runtime_libs 加载（作为备选）
        val libPath = File(getRuntimeLibsDir(context), "lib7-Zip-JBinding.so")
        if (libPath.exists()) {
            return try {
                System.load(libPath.absolutePath)
                loadedLibraries.add("7-Zip-JBinding")
                AppLogger.info(TAG, "7-Zip library loaded from runtime_libs")
                true
            } catch (e: UnsatisfiedLinkError) {
                AppLogger.error(TAG, "Failed to load 7-Zip from runtime_libs: ${e.message}")
                false
            }
        }
        AppLogger.error(TAG, "lib7-Zip-JBinding.so not found at: ${libPath.absolutePath}")
        return false
    }
    
    /**
     * 获取库加载状态摘要
     */
    fun getLoadedLibrariesSummary(): String {
        return if (loadedLibraries.isEmpty()) {
            "No runtime libraries loaded"
        } else {
            "Loaded: ${loadedLibraries.joinToString(", ")}"
        }
    }
}
