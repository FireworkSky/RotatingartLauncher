package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.system.Os
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.AndroidRendererRegistry
import com.app.ralaunch.core.platform.runtime.RendererRegistry

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
                AppLog.e(TAG, "Unknown renderer: $renderer")
                return false
            }

            if (!AndroidRendererRegistry.isRendererCompatible(normalizedRenderer)) {
                AppLog.e(TAG, "Renderer is not compatible with this device")
                return false
            }

            val envMap = AndroidRendererRegistry.buildRendererEnv(normalizedRenderer)
            EnvVarsManager.quickSetEnvVars(envMap)

            if (rendererInfo.needsPreload && rendererInfo.eglLibrary != null) {
                try {
                    val eglLibPath = AndroidRendererRegistry.getRendererLibraryPath(rendererInfo.eglLibrary)
                    EnvVarsManager.quickSetEnvVar("FNA3D_OPENGL_LIBRARY", eglLibPath)
                } catch (e: UnsatisfiedLinkError) {
                    AppLog.e(TAG, "Failed to preload renderer library: ${e.message}")
                }
            }

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            EnvVarsManager.quickSetEnvVar("RALCORE_NATIVEDIR", nativeLibDir)
            
            // 设置 runtime_libs 目录路径（从 tar.xz 解压的库）
            val runtimeLibsDir = java.io.File(context.filesDir, "runtime_libs")
            if (runtimeLibsDir.exists()) {
                val runtimePath = runtimeLibsDir.absolutePath
                EnvVarsManager.quickSetEnvVar("RALCORE_RUNTIMEDIR", runtimePath)
                AppLog.i(TAG, "RALCORE_RUNTIMEDIR = $runtimePath")
                
                // 设置 LD_LIBRARY_PATH 包含 runtime_libs 目录，让 dlopen 能找到库
                val currentLdPath = Os.getenv("LD_LIBRARY_PATH") ?: ""
                val newLdPath = if (currentLdPath.isNotEmpty()) {
                    "$runtimePath:$nativeLibDir:$currentLdPath"
                } else {
                    "$runtimePath:$nativeLibDir"
                }
                EnvVarsManager.quickSetEnvVar("LD_LIBRARY_PATH", newLdPath)
                AppLog.i(TAG, "LD_LIBRARY_PATH = $newLdPath")
            }

            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Renderer loading failed: ${e.message}", e)
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
