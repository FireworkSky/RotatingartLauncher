package com.app.ralaunch.renderer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.core.EnvVarsManager
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.utils.GLInfoUtils
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
    const val RENDERER_ZINK: String = "zink" // Zink (Mesa)
    const val RENDERER_ZINK_25: String = "zink25" // Zink (Mesa 25)
    const val RENDERER_VIRGL: String = "virgl" // VirGL
    const val RENDERER_FREEDRENO: String = "freedreno" // Freedreno
    const val RENDERER_DXVK: String = "dxvk" // DXVK (D3D11 -> Vulkan)

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
        // gl4es 渲染器
        RendererInfo(
            RENDERER_GL4ES,
            "GL4ES",
            "最完美，游戏兼容性最强，但帧率稍慢",
            "libEGL_gl4es.so",
            "libGL_gl4es.so",
            true,
            0
        ),
        // gl4es + angle 渲染器
        RendererInfo(
            RENDERER_GL4ES_ANGLE,
            "GL4ES + ANGLE",
            "翻译成Vulkan，速度和兼容性最佳，推荐高通骁龙使用",
            "libEGL_gl4es.so",
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
        // ANGLE 渲染器
        RendererInfo(
            RENDERER_ANGLE,
            "ANGLE (Vulkan Backend)",
            "OpenGL ES over Vulkan (Google官方)",
            "libEGL_angle.so",
            "libGLESv2_angle.so",
            true,
            Build.VERSION_CODES.N // Vulkan 需要 Android 7.0+
        ),
        // Zink 渲染器
        RendererInfo(
            RENDERER_ZINK,
            "Zink (Mesa)",
            "OpenGL 4.6 over Vulkan (Mesa Zink)",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        ),
        // Zink Mesa 25 渲染器
        RendererInfo(
            RENDERER_ZINK_25,
            "Zink (Mesa 25)",
            "OpenGL 4.6 over Vulkan (Mesa 25 - 最新特性支持）",
            "libOSMesa_25.so",
            "libOSMesa_25.so",
            true,
            Build.VERSION_CODES.Q // Mesa 25 需要 Android 10+
        ),
        // VirGL 渲染器
        RendererInfo(
            RENDERER_VIRGL,
            "VirGL Renderer",
            "Gallium3D VirGL (OpenGL 4.3)",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        ),
        // Freedreno 渲染器
        RendererInfo(
            RENDERER_FREEDRENO,
            "Freedreno (Adreno)",
            "Mesa Freedreno for Qualcomm Adreno GPU",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        ),
        // DXVK 渲染器 (D3D11 over Vulkan)
        RendererInfo(
            RENDERER_DXVK,
            "DXVK (D3D11)",
            "Direct3D 11 over Vulkan - FNA3D 使用 D3D11 后端，通过 DXVK 翻译到 Vulkan",
            "libdxvk_dxgi.so",  // DXVK DXGI 库
            "libdxvk_d3d11.so",  // DXVK D3D11 库
            true,
            Build.VERSION_CODES.N // Vulkan 需要 Android 7.0+
        )
    )

    /**
     * 获取所有兼容的渲染器
     */
    @JvmStatic
    fun getCompatibleRenderers(context: Context): MutableList<RendererInfo> {
        val compatible: MutableList<RendererInfo> = ArrayList()
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

        for (renderer in ALL_RENDERERS) {
            if (Build.VERSION.SDK_INT < renderer.minAndroidVersion) {
                continue
            }

            var hasLibraries = true
            if (renderer.eglLibrary != null) {
                val eglLib = File(nativeLibDir, renderer.eglLibrary)
                if (!eglLib.exists()) {
                    hasLibraries = false
                }
            }

            if (hasLibraries && renderer.glesLibrary != null && (renderer.glesLibrary != renderer.eglLibrary)) {
                val glesLib = File(nativeLibDir, renderer.glesLibrary)
                if (!glesLib.exists()) {
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

        // Add Turnip driver settings if needed
        addTurnipSettingsIfNeeded(context, envMap)

        // Add renderer-specific environment variables
        addRendererSpecificEnv(context, rendererId, envMap)

        return envMap
    }

    /**
     * 添加 Turnip 驱动设置（仅适用于 Adreno GPU）
     */
    private fun addTurnipSettingsIfNeeded(context: Context, envMap: MutableMap<String?, String?>) {
        val glInfo = GLInfoUtils.getGlInfo()
        if (glInfo.isAdreno) {
            val settingsManager = SettingsManager.getInstance()
            if (settingsManager.isVulkanDriverTurnip) {
                envMap["RALCORE_LOAD_TURNIP"] = "1"
            }
        }
    }

    /**
     * 添加渲染器特定的环境变量
     */
    private fun addRendererSpecificEnv(context: Context, rendererId: String, envMap: MutableMap<String?, String?>) {
        when (rendererId) {
            RENDERER_GL4ES -> addGl4esEnv(envMap)
            RENDERER_GL4ES_ANGLE -> addGl4esAngleEnv(envMap)
            RENDERER_MOBILEGLUES -> addMobileGluesEnv(envMap)
            RENDERER_ANGLE -> addAngleEnv(envMap)
            RENDERER_ZINK -> addZinkEnv(context, envMap)
            RENDERER_ZINK_25 -> addZink25Env(context, envMap)
            RENDERER_VIRGL -> addVirglEnv(context, envMap)
            RENDERER_FREEDRENO -> addFreedrenoEnv(context, envMap)
            RENDERER_DXVK -> addDxvkEnv(envMap)
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

    private fun addZinkEnv(context: Context, envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "vulkan_zink")
            put("GALLIUM_DRIVER", "zink")
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")
            put("MESA_GLSL_CACHE_DIR", context.cacheDir.absolutePath)
            put("force_glsl_extensions_warn", "true")
            put("allow_higher_compat_version", "true")
            put("allow_glsl_extension_directive_midshader", "true")
            put("ZINK_DESCRIPTORS", "auto")
            // Note: Do NOT set LIBGL_ALWAYS_SOFTWARE=1
            // It forces zink to look for CPU devices which don't exist on Android
        }
    }

    private fun addZink25Env(context: Context, envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "vulkan_zink")
            put("GALLIUM_DRIVER", "zink")
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")
            put("MESA_GLSL_CACHE_DIR", context.cacheDir.absolutePath)
            // Mesa 25 features
            put("ZINK_DESCRIPTORS", "auto")
            put("ZINK_DEBUG", "nir")
            put("force_glsl_extensions_warn", "true")
            put("allow_higher_compat_version", "true")
            put("allow_glsl_extension_directive_midshader", "true")
            put("MESA_EXTENSION_MAX_YEAR", "2025")
            put("SDL_EGL_SKIP_RENDERABLE_TYPE", "1")
            put("LIBGL_ALWAYS_SOFTWARE", "1") // Force Mesa for consistency
        }
    }

    private fun addVirglEnv(context: Context, envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "gallium_virgl")
            put("GALLIUM_DRIVER", "virpipe")
            put("MESA_GL_VERSION_OVERRIDE", "4.3")
            put("MESA_GLSL_VERSION_OVERRIDE", "430")
            put("MESA_GLSL_CACHE_DIR", context.cacheDir.absolutePath)
            put("OSMESA_NO_FLUSH_FRONTBUFFER", "1")
            put("VTEST_SOCKET_NAME", File(context.cacheDir, ".virgl_test").absolutePath)
        }
    }

    private fun addFreedrenoEnv(context: Context, envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "gallium_freedreno")
            put("GALLIUM_DRIVER", "freedreno")
            put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl")
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")
            put("MESA_GLSL_CACHE_DIR", context.cacheDir.absolutePath)
        }
    }

    private fun addDxvkEnv(envMap: MutableMap<String?, String?>) {
        envMap.apply {
            put("RALCORE_RENDERER", "dxvk")
            put("FNA3D_FORCE_DRIVER", "D3D11")
            put("DXVK_WSI_DRIVER", "SDL2")
            put("DXVK_LOG_LEVEL", "info")
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

        // FNA3D Force Driver (D3D11 for DXVK, OpenGL for others)
        envVars["FNA3D_FORCE_DRIVER"] = if (rendererId == RENDERER_DXVK) "D3D11" else "OpenGL"

        // OpenGL version configuration
        val openGlConfig = getOpenGlVersionConfig(rendererId)
        envVars.putAll(openGlConfig)

        // Map buffer range optimization
        val mapBufferRangeValue = getMapBufferRangeValue(context, rendererId)
        envVars["FNA3D_OPENGL_USE_MAP_BUFFER_RANGE"] = mapBufferRangeValue

        // Force VSync
        envVars["FORCE_VSYNC"] = "true"

        return envVars
    }

    /**
     * 获取 OpenGL 版本配置
     */
    private fun getOpenGlVersionConfig(rendererId: String): Map<String, String?> {
        return when {
            // Desktop OpenGL renderers (GL4ES, Zink) - unset ES3 constraints
            rendererId == RENDERER_GL4ES || rendererId == RENDERER_ZINK -> {
                buildMap {
                    put("FNA3D_OPENGL_FORCE_ES3", null)
                    put("FNA3D_OPENGL_FORCE_VER_MAJOR", null)
                    put("FNA3D_OPENGL_FORCE_VER_MINOR", null)

                    // Zink needs special MojoShader profile
                    if (rendererId == RENDERER_ZINK) {
                        put("FNA3D_MOJOSHADER_PROFILE", "glsles3")
                    }
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
            RENDERER_ZINK,
            RENDERER_ZINK_25,
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
            RENDERER_ZINK -> {
                Log.i(TAG, "OpenGL Profile: Desktop OpenGL 4.6")
                Log.i(TAG, "MojoShader Profile: glsles3 (avoiding glspirv)")
            }
            else ->
                Log.i(TAG, "OpenGL Profile: OpenGL ES 3.0")
        }

        val mapBufferRange = envVars["FNA3D_OPENGL_USE_MAP_BUFFER_RANGE"]
        when {
            mapBufferRange == "0" && rendererId in setOf(RENDERER_ZINK, RENDERER_ZINK_25, RENDERER_ANGLE, RENDERER_GL4ES_ANGLE) ->
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

