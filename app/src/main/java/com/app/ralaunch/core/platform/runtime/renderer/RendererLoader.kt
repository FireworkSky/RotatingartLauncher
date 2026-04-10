package com.app.ralaunch.core.platform.runtime.renderer

import android.content.Context
import android.system.Os
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.shared.core.platform.runtime.renderer.AndroidRendererRegistry
import com.app.ralaunch.shared.core.platform.runtime.renderer.RendererRegistry

/**
 * 渲染器加载器 - 基于环境变量的简化实现
 */
object RendererLoader {
    private const val TAG = "RendererLoader"

    fun loadRenderer(context: Context, renderer: String): Boolean {
        return try {
            val normalizedRenderer = RendererRegistry.normalizeRendererId(renderer)
            val rendererInfo = AndroidRendererRegistry.getRendererInfo(normalizedRenderer)
            if (rendererInfo == null) {
                AppLogger.error(TAG, "Unknown renderer: $renderer")
                return false
            }

            if (!AndroidRendererRegistry.isRendererCompatible(normalizedRenderer)) {
                AppLogger.error(TAG, "Renderer is not compatible with this device")
                return false
            }

            val envMap = AndroidRendererRegistry.buildRendererEnv(normalizedRenderer)
            EnvVarsManager.quickSetEnvVars(envMap)

            if (rendererInfo.needsPreload && rendererInfo.eglLibrary != null) {
                try {
                    val eglLibPath = AndroidRendererRegistry.getRendererLibraryPath(rendererInfo.eglLibrary)
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
                val currentLdPath = Os.getenv("LD_LIBRARY_PATH") ?: ""
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
}
