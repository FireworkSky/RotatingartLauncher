package com.app.ralaunch.renderer

import android.content.Context
import android.system.Os
import com.app.ralaunch.core.EnvVarsManager
import com.app.ralaunch.utils.AppLogger

/**
 * 渲染器加载器 - 基于环境变量的简化实现
 */
object RendererLoader {
    private const val TAG = "RendererLoader"

    fun loadRenderer(context: Context, rendererId: String): Boolean {
        return try {
            val renderer = RendererConfig.getRendererById(rendererId)
            if (renderer == null) {
                AppLogger.error(TAG, "Unknown renderer: $rendererId")
                return false
            }

            if (!RendererConfig.isRendererCompatible(context, rendererId)) {
                AppLogger.error(TAG, "Renderer is not compatible with this device")
                return false
            }

            val envMap = RendererConfig.getRendererEnv(context, rendererId)
            envMap.forEach { (k, v) -> k?.let { EnvVarsManager.quickSetEnvVar(it, v) } }

            if (renderer.needsPreload && renderer.eglLibrary != null) {
                try {
                    val eglLibPath = RendererConfig.getRendererLibraryPath(context, renderer.eglLibrary!!)
                    EnvVarsManager.quickSetEnvVar("FNA3D_OPENGL_LIBRARY", eglLibPath)
                } catch (e: UnsatisfiedLinkError) {
                    AppLogger.error(TAG, "Failed to preload renderer library: ${e.message}")
                }
            }

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            EnvVarsManager.quickSetEnvVar("RALCORE_NATIVEDIR", nativeLibDir)
            
            // 设置 runtime_libs 目录路径（从 tar.xz 解压的库）
            val runtimeLibsDir = java.io.File(context.filesDir, "runtime_libs")
            if (runtimeLibsDir.exists()) {
                val runtimePath = runtimeLibsDir.absolutePath
                EnvVarsManager.quickSetEnvVar("RALCORE_RUNTIMEDIR", runtimePath)
                AppLogger.info(TAG, "RALCORE_RUNTIMEDIR = $runtimePath")
                
                // 设置 LD_LIBRARY_PATH 包含 runtime_libs 目录，让 dlopen 能找到库
                val currentLdPath = android.system.Os.getenv("LD_LIBRARY_PATH") ?: ""
                val newLdPath = if (currentLdPath.isNotEmpty()) {
                    "$runtimePath:$nativeLibDir:$currentLdPath"
                } else {
                    "$runtimePath:$nativeLibDir"
                }
                EnvVarsManager.quickSetEnvVar("LD_LIBRARY_PATH", newLdPath)
                AppLogger.info(TAG, "LD_LIBRARY_PATH = $newLdPath")
            }

            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Renderer loading failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun getCurrentRenderer(): String {
        val ralcoreRenderer = Os.getenv("RALCORE_RENDERER")
        val ralcoreEgl = Os.getenv("RALCORE_EGL")
        return when {
            !ralcoreRenderer.isNullOrEmpty() -> ralcoreRenderer
            ralcoreEgl?.contains("angle") == true -> "angle"
            else -> "native"
        }
    }

    @JvmStatic
    fun clearRendererEnv() {
        listOf(
            "RALCORE_RENDERER", "RALCORE_EGL", "LIBGL_GLES", "LIBGL_ES",
            "LIBGL_MIPMAP", "LIBGL_NORMALIZE", "LIBGL_NOINTOVLHACK", "LIBGL_NOERROR"
        ).forEach { EnvVarsManager.quickSetEnvVar(it, null) }
    }
}
