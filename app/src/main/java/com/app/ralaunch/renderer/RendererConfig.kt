package com.app.ralaunch.renderer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.app.ralaunch.core.EnvVarsManager
import com.app.ralaunch.data.SettingsManager
import java.io.File
import kotlin.io.path.Path

/**
 * 渲染器配置类 - 统一的渲染器管理
 *
 * 核心原理：
 * 1. 通过环境变量控制渲染器选择（RALCORE_RENDERER）
 * 2. 库文件通过 LD_LIBRARY_PATH 自动可见
 * 3. 所有渲染器都提供标准的 EGL/OpenGL 接口
 * 4. SDL/FNA3D 读取环境变量并使用相应渲染器
 *
 * 合并了 RendererPreference 的功能，提供完整的渲染器管理
 */
object RendererConfig {
    private const val TAG = "RendererConfig"

    // 渲染器 ID
    const val RENDERER_AUTO: String = "auto" // 自动选择
    const val RENDERER_NATIVE_GLES: String = "native" // 系统原生 EGL/GLES
    const val RENDERER_GL4ES: String = "gl4es" // GL4ES
    const val RENDERER_GL4ES_ANGLE: String = "gl4es+angle" // GL4ES + ANGLE
    const val RENDERER_MOBILEGLUES: String = "mobileglues" // MobileGlues
    const val RENDERER_ANGLE: String = "angle" // ANGLE

    // 已弃用的渲染器 ID（向后兼容）
    @Deprecated("Use RENDERER_NATIVE_GLES instead")
    const val RENDERER_OPENGLES3: String = "opengles3"
    @Deprecated("Use RENDERER_GL4ES instead")
    const val RENDERER_OPENGL_GL4ES: String = "opengl_gl4es"
    @Deprecated("Use RENDERER_NATIVE_GLES instead")
    const val RENDERER_VULKAN: String = "vulkan"

    // 默认渲染器
    val DEFAULT_RENDERER: String = RENDERER_NATIVE_GLES

    // ==================== Renderer Preference Management ====================

    /**
     * 设置渲染器首选项
     */
    @JvmStatic
    fun setRenderer(context: Context?, renderer: String?) {
        if (renderer == null) return
        SettingsManager.getInstance().fnaRenderer = renderer
    }

    /**
     * 获取渲染器首选项（已规范化）
     */
    @JvmStatic
    fun getRenderer(context: Context?): String {
        val manager = SettingsManager.getInstance()
        val raw = manager.fnaRenderer
        val normalized = normalizeRendererValue(raw)
        if (normalized != raw) {
            manager.fnaRenderer = normalized
        }
        return normalized
    }

    /**
     * 获取有效渲染器（自动选择处理）
     */
    @JvmStatic
    fun getEffectiveRenderer(context: Context?): String {
        val renderer = getRenderer(context)
        if (RENDERER_AUTO == renderer) {
            return RENDERER_NATIVE_GLES
        }
        return renderer
    }

    /**
     * 规范化渲染器值（处理旧版本兼容性）
     */
    @JvmStatic
    fun normalizeRendererValue(value: String?): String {
        if (value == null || value.isEmpty()) {
            return RENDERER_AUTO
        }

        return when (value) {
            "opengl_native", RENDERER_OPENGLES3 -> RENDERER_NATIVE_GLES
            RENDERER_OPENGL_GL4ES -> RENDERER_GL4ES
            RENDERER_VULKAN -> RENDERER_NATIVE_GLES
            else -> value
        }
    }

    // ==================== Renderer Information ====================

    private val ALL_RENDERERS = arrayOf<RendererInfo>(
        // 系统原生渲染器（默认）
        RendererInfo(
            RENDERER_NATIVE_GLES,
            "Native OpenGL ES 3",
            "最快，有GPU加速，但可能有渲染错误",
            null,  // 使用系统 libEGL.so
            null,  // 使用系统 libGLESv2.so
            false,
            0
        ),
        // gl4es 渲染器（EGL 和 GL 都在 runtime_libs 中）
        RendererInfo(
            RENDERER_GL4ES,
            "GL4ES",
            "最完美，游戏兼容性最强，但帧率稍慢",
            "libEGL_gl4es.so",  // 现在在 runtime_libs 中
            "libGL_gl4es.so",
            true,
            0
        ),
        // gl4es + angle 渲染器
        RendererInfo(
            RENDERER_GL4ES_ANGLE,
            "GL4ES + ANGLE",
            "翻译成Vulkan，速度和兼容性最佳，推荐高通骁龙使用",
            "libEGL_gl4es.so",  // 现在在 runtime_libs 中
            "libGL_gl4es.so",
            true,
            0
        ),
        // MobileGlues 渲染器
        RendererInfo(
            RENDERER_MOBILEGLUES,
            "MobileGlues 1.3.3",
            "OpenGL 4.6 翻译至 OpenGL ES 3.2（现代化翻译层）",
            "libmobileglues.so",
            "libmobileglues.so",
            true,
            0
        ),
        // ANGLE 渲染器（EGL 和 GLES 都在 runtime_libs 中）
        RendererInfo(
            RENDERER_ANGLE,
            "ANGLE (Vulkan Backend)",
            "OpenGL ES over Vulkan (Google官方)",
            "libEGL_angle.so",  // 现在在 runtime_libs 中
            "libGLESv2_angle.so",
            true,
            Build.VERSION_CODES.N // Vulkan 需要 Android 7.0+
        )
    )

    /**
     * 获取所有兼容的渲染器
     * 检查 APK lib 目录和 runtime_libs 目录
     */
    @JvmStatic
    fun getCompatibleRenderers(context: Context): MutableList<RendererInfo> {
        val compatible: MutableList<RendererInfo> = ArrayList()
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val runtimeLibsDir = File(context.filesDir, "runtime_libs")

        for (renderer in ALL_RENDERERS) {
            if (Build.VERSION.SDK_INT < renderer.minAndroidVersion) {
                continue
            }

            var hasLibraries = true
            if (renderer.eglLibrary != null) {
                val eglLibNative = File(nativeLibDir, renderer.eglLibrary)
                val eglLibRuntime = File(runtimeLibsDir, renderer.eglLibrary)
                if (!eglLibNative.exists() && !eglLibRuntime.exists()) {
                    hasLibraries = false
                }
            }

            if (hasLibraries && renderer.glesLibrary != null && (renderer.glesLibrary != renderer.eglLibrary)) {
                val glesLibNative = File(nativeLibDir, renderer.glesLibrary)
                val glesLibRuntime = File(runtimeLibsDir, renderer.glesLibrary)
                if (!glesLibNative.exists() && !glesLibRuntime.exists()) {
                    hasLibraries = false
                }
            }

            if (hasLibraries) {
                compatible.add(renderer)
            }
        }
        return compatible
    }

    /**
     * 根据 ID 获取渲染器信息
     */
    @JvmStatic
    fun getRendererById(id: String?): RendererInfo? {
        for (renderer in ALL_RENDERERS) {
            if (renderer.id == id) {
                return renderer
            }
        }
        return null
    }

    /**
     * 检查渲染器是否兼容
     */
    @JvmStatic
    fun isRendererCompatible(context: Context, rendererId: String?): Boolean {
        val compatible = getCompatibleRenderers(context)
        for (renderer in compatible) {
            if (renderer.id == rendererId) {
                return true
            }
        }
        return false
    }

    /**
     * 获取渲染器库的完整路径
     */
    @JvmStatic
    fun getRendererLibraryPath(context: Context, libraryName: String?): String? {
        if (libraryName == null) {
            return null
        }
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val libFile = File(nativeLibDir, libraryName)
        return libFile.absolutePath
    }

    /**
     * 获取渲染器环境变量配置（使用 RALCORE 前缀）
     * 返回的 Map 将被应用到进程环境变量
     */
    @JvmStatic
    fun getRendererEnv(context: Context, rendererId: String): MutableMap<String?, String?> {
        val envMap = mutableMapOf<String?, String?>()

        // Add renderer-specific environment variables
        addRendererSpecificEnv(rendererId, envMap)

        return envMap
    }

    /**
     * 添加渲染器特定的环境变量
     */
    private fun addRendererSpecificEnv(rendererId: String, envMap: MutableMap<String?, String?>) {
        when (rendererId) {
            RENDERER_GL4ES -> addGl4esEnv(envMap)
            RENDERER_GL4ES_ANGLE -> addGl4esAngleEnv(envMap)
            RENDERER_MOBILEGLUES -> addMobileGluesEnv(envMap)
            RENDERER_ANGLE -> addAngleEnv(envMap)
            RENDERER_NATIVE_GLES -> { /* No additional env vars needed */ }
        }
    }

    private fun addGl4esEnv(envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "gl4es")
            put("LIBGL_ES", "3")
            put("LIBGL_MIPMAP", "3")
            put("LIBGL_NORMALIZE", "1")
            put("LIBGL_NOINTOVLHACK", "1")
            put("LIBGL_NOERROR", "1")
           
        }
    }

    private fun addGl4esAngleEnv(envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "gl4es")
            put("LIBGL_ES", "3")
            put("LIBGL_MIPMAP", "3")
            put("LIBGL_NORMALIZE", "1")
            put("LIBGL_NOINTOVLHACK", "1")
            put("LIBGL_NOERROR", "1")
           
        }
    }

    private fun addMobileGluesEnv(envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "mobileglues")
            put("FNA3D_OPENGL_DRIVER", "mobileglues")
            put("MOBILEGLUES_GLES_VERSION", "3.2")
            put("FNA3D_MOJOSHADER_PROFILE", "glsles3")
            put("MG_DIR_PATH", Path(Environment.getExternalStorageDirectory().absolutePath).resolve("MG").toString())
        }
    }

    private fun addAngleEnv(envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_EGL", "libEGL_angle.so")
            put("LIBGL_GLES", "libGLESv2_angle.so")
        }
    }

    /**
     * 应用渲染器环境（包括 FNA3D 特定的环境变量）
     * 这是渲染器初始化的主入口
     */
    @JvmStatic
    fun applyRendererEnvironment(context: Context?) {
        val preferredRenderer = getEffectiveRenderer(context)
        val rendererId = preferredRenderer

        // Step 1: Load renderer through RendererLoader (sets RALCORE_* env vars)
        loadRendererLibraries(context, rendererId)

        // Step 2: Apply FNA3D-specific environment variables
        applyFna3dEnvironment(context, rendererId)

        // Step 3: Set system property for FNA renderer
        System.setProperty("fna.renderer", preferredRenderer)

        Log.i(TAG, "Renderer environment applied successfully for: $rendererId")
    }

    /**
     * 加载渲染器库和基础环境变量
     */
    private fun loadRendererLibraries(context: Context?, rendererId: String) {
        if (context == null) return
        val success = RendererLoader.loadRenderer(context, rendererId)

        if (success) {
            Log.i(TAG, "当前渲染器: ${RendererLoader.getCurrentRenderer()}")
        } else {
            Log.e(TAG, "Failed to load renderer: $rendererId")
        }
    }

    /**
     * 应用 FNA3D 特定的环境变量
     */
    private fun applyFna3dEnvironment(context: Context?, rendererId: String) {
        // Build FNA3D environment variables map
        val fna3dEnvVars = buildFna3dEnvVars(context, rendererId)

        // Apply all FNA3D variables at once
        EnvVarsManager.quickSetEnvVars(fna3dEnvVars)

        // Log configuration
        logFna3dConfiguration(rendererId, fna3dEnvVars)
    }

    /**
     * 构建 FNA3D 环境变量映射
     */
    private fun buildFna3dEnvVars(context: Context?, rendererId: String): Map<String, String?> {
        val envVars = mutableMapOf<String, String?>()

        // FNA3D OpenGL driver selection
        envVars["FNA3D_OPENGL_DRIVER"] = rendererId

        // FNA3D Force Driver (OpenGL)
        envVars["FNA3D_FORCE_DRIVER"] = "OpenGL"

        // OpenGL version configuration
        val openGlConfig = getOpenGlVersionConfig(rendererId)
        envVars.putAll(openGlConfig)

        // Map buffer range optimization
        val mapBufferRangeValue = getMapBufferRangeValue(context, rendererId)
        envVars["FNA3D_OPENGL_USE_MAP_BUFFER_RANGE"] = mapBufferRangeValue

        // 画质优化设置
        val qualityConfig = getQualityConfig()
        envVars.putAll(qualityConfig)

        // Force VSync
        envVars["FORCE_VSYNC"] = "true"

        return envVars
    }

    /**
     * 获取画质优化配置
     */
    private fun getQualityConfig(): Map<String, String?> {
        val settings = SettingsManager.getInstance()
        val envVars = mutableMapOf<String, String?>()

        // 画质预设
        val qualityLevel = settings.fnaQualityLevel
        when (qualityLevel) {
            1 -> { // 中画质
                envVars["FNA3D_TEXTURE_LOD_BIAS"] = "1.0"
                envVars["FNA3D_MAX_ANISOTROPY"] = "2"
                envVars["FNA3D_RENDER_SCALE"] = "0.85"
            }
            2 -> { // 低画质
                envVars["FNA3D_TEXTURE_LOD_BIAS"] = "2.0"
                envVars["FNA3D_MAX_ANISOTROPY"] = "1"
                envVars["FNA3D_RENDER_SCALE"] = "0.7"
                envVars["FNA3D_SHADER_LOW_PRECISION"] = "1" // 低画质自动启用低精度 shader
            }
            else -> { // 高画质 (0) - 使用自定义设置
                val lodBias = settings.fnaTextureLodBias
                val maxAnisotropy = settings.fnaMaxAnisotropy
                val renderScale = settings.fnaRenderScale
                val shaderLowPrecision = settings.isFnaShaderLowPrecision
                
                if (lodBias > 0f) {
                    envVars["FNA3D_TEXTURE_LOD_BIAS"] = lodBias.toString()
                }
                if (maxAnisotropy < 16) {
                    envVars["FNA3D_MAX_ANISOTROPY"] = maxAnisotropy.toString()
                }
                if (renderScale < 1.0f) {
                    envVars["FNA3D_RENDER_SCALE"] = renderScale.toString()
                }
                if (shaderLowPrecision) {
                    envVars["FNA3D_SHADER_LOW_PRECISION"] = "1"
                }
            }
        }

        return envVars
    }

    /**
     * 获取 OpenGL 版本配置
     */
    private fun getOpenGlVersionConfig(rendererId: String): Map<String, String?> {
        return when {
            // Desktop OpenGL renderers (GL4ES) - unset ES3 constraints
            rendererId == RENDERER_GL4ES -> {
                buildMap {
                    put("FNA3D_OPENGL_FORCE_ES3", null)
                    put("FNA3D_OPENGL_FORCE_VER_MAJOR", null)
                    put("FNA3D_OPENGL_FORCE_VER_MINOR", null)
                }
            }
            // OpenGL ES 3.0 for all other renderers
            else -> {
                mapOf(
                    "FNA3D_OPENGL_FORCE_ES3" to "1",
                    "FNA3D_OPENGL_FORCE_VER_MAJOR" to "3",
                    "FNA3D_OPENGL_FORCE_VER_MINOR" to "0"
                )
            }
        }
    }

    /**
     * 获取 Map Buffer Range 优化值
     */
    private fun getMapBufferRangeValue(context: Context?, rendererId: String): String? {
        // Vulkan-translated renderers need it disabled for compatibility
        val vulkanTranslatedRenderers = setOf(
            RENDERER_ANGLE,
            RENDERER_GL4ES_ANGLE
        )

        return when {
            rendererId in vulkanTranslatedRenderers -> "0"
            else -> {
                val settings = SettingsManager.getInstance()
                if (settings.isFnaEnableMapBufferRangeOptimization) null else "0"
            }
        }
    }

    /**
     * 记录 FNA3D 配置日志
     */
    private fun logFna3dConfiguration(rendererId: String, envVars: Map<String, String?>) {
        Log.i(TAG, "=== FNA3D Configuration ===")
        Log.i(TAG, "Renderer ID: $rendererId")
        Log.i(TAG, "FNA3D_OPENGL_DRIVER = ${envVars["FNA3D_OPENGL_DRIVER"]}")
        Log.i(TAG, "FNA3D_FORCE_DRIVER = ${envVars["FNA3D_FORCE_DRIVER"]}")

        when (rendererId) {
            RENDERER_GL4ES ->
                Log.i(TAG, "OpenGL Profile: Desktop OpenGL 2.1 Compatibility Profile")
            else ->
                Log.i(TAG, "OpenGL Profile: OpenGL ES 3.0")
        }

        val mapBufferRange = envVars["FNA3D_OPENGL_USE_MAP_BUFFER_RANGE"]
        when {
            mapBufferRange == "0" && rendererId in setOf(RENDERER_ANGLE, RENDERER_GL4ES_ANGLE) ->
                Log.i(TAG, "Map Buffer Range: Disabled (Vulkan-translated renderer)")
            mapBufferRange == "0" ->
                Log.i(TAG, "Map Buffer Range: Disabled (via settings)")
            else ->
                Log.i(TAG, "Map Buffer Range: Enabled by default")
        }

        Log.i(TAG, "VSync: Forced ON")
        Log.i(TAG, "===========================")
    }

    // ==================== Renderer Information ====================

    class RendererInfo(
        @JvmField val id: String,
        @JvmField val displayName: String?,
        @JvmField val description: String?,
        // EGL 库文件名 (null = 系统默认)
        @JvmField val eglLibrary: String?,
        // GLES 库文件名 (null = 系统默认)
        @JvmField val glesLibrary: String?,
        // 是否需要通过 LD_PRELOAD 加载
        @JvmField val needsPreload: Boolean,
        // 最低 Android 版本
        @JvmField val minAndroidVersion: Int
    )
}

