package com.app.ralaunch.core.platform.runtime.renderer

import android.content.Context
import android.system.Os
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import java.io.File

object RendererLoader {
    private const val TAG = "RendererLoader"
    private const val RUNTIME_LIBS_DIR = "runtime_libs"

    fun loadRenderer(context: Context, renderer: String): Boolean {
        return try {
            val normalizedRenderer = RendererRegistry.normalizeRendererId(renderer)
            val rendererInfo = RendererRegistry.getRendererInfo(normalizedRenderer)
            if (rendererInfo == null) {
                AppLogger.error(TAG, "Unknown renderer: $renderer")
                return false
            }

            if (!RendererRegistry.isRendererCompatible(normalizedRenderer)) {
                AppLogger.error(TAG, "Renderer is not compatible with this device: $normalizedRenderer")
                return false
            }

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val runtimeLibsDir = File(context.filesDir, RUNTIME_LIBS_DIR)

            EnvVarsManager.quickSetEnvVar("RALCORE_NATIVEDIR", nativeLibDir)

            if (runtimeLibsDir.exists()) {
                val runtimePath = runtimeLibsDir.absolutePath
                EnvVarsManager.quickSetEnvVar("RALCORE_RUNTIMEDIR", runtimePath)

                val currentLdPath = Os.getenv("LD_LIBRARY_PATH") ?: ""
                val newLdPath = buildString {
                    append(runtimePath)
                    append(":")
                    append(nativeLibDir)
                    if (currentLdPath.isNotEmpty()) {
                        append(":")
                        append(currentLdPath)
                    }
                }
                EnvVarsManager.quickSetEnvVar("LD_LIBRARY_PATH", newLdPath)
                AppLogger.info(TAG, "RALCORE_RUNTIMEDIR = $runtimePath")
                AppLogger.info(TAG, "LD_LIBRARY_PATH = $newLdPath")
            }

            val envMap = RendererRegistry.buildRendererEnv(normalizedRenderer).toMutableMap()

            val eglPath = RendererRegistry.getRendererLibraryPath(rendererInfo.eglLibrary)
            val glesPath = RendererRegistry.getRendererLibraryPath(rendererInfo.glesLibrary)
            val preloadPaths = RendererRegistry.getRendererPreloadLibraryPaths(normalizedRenderer)

            if (!eglPath.isNullOrEmpty()) {
                envMap["RALCORE_EGL_PATH"] = eglPath
            }

            if (!glesPath.isNullOrEmpty()) {
                envMap["RALCORE_GLES_PATH"] = glesPath
            }

            when (normalizedRenderer) {
                RendererRegistry.ID_ANGLE -> {
                    if (!glesPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = glesPath
                    }
                }

                RendererRegistry.ID_GL4ES -> {
                    val gl4esPath = RendererRegistry.getRendererLibraryPath("libGL_gl4es.so")
                    if (!gl4esPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = gl4esPath
                    }
                }

                RendererRegistry.ID_GL4ES_ANGLE -> {
                    val gl4esPath = RendererRegistry.getRendererLibraryPath("libGL_gl4es.so")
                    val angleEglPath = RendererRegistry.getRendererLibraryPath("libEGL_angle.so")
                    val angleGlesPath = RendererRegistry.getRendererLibraryPath("libGLESv2_angle.so")

                    if (!gl4esPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = gl4esPath
                    }
                    if (!angleEglPath.isNullOrEmpty()) {
                        envMap["RALCORE_EGL"] = "libEGL_angle.so"
                        envMap["RALCORE_EGL_PATH"] = angleEglPath
                    }
                    if (!angleGlesPath.isNullOrEmpty()) {
                        envMap["LIBGL_GLES"] = "libGLESv2_angle.so"
                        envMap["RALCORE_ANGLE_GLES_PATH"] = angleGlesPath
                    }
                }

                RendererRegistry.ID_MOBILEGLUES -> {
                    val mobilegluesPath = RendererRegistry.getRendererLibraryPath("libmobileglues.so")
                    if (!mobilegluesPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = mobilegluesPath
                    }
                }

                RendererRegistry.ID_ZINK -> {
                    val osMesaPath = RendererRegistry.getRendererLibraryPath("libOSMesa.so")
                    if (!osMesaPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = osMesaPath
                    }
                }

                else -> {
                    val fallbackPath = glesPath ?: eglPath
                    if (!fallbackPath.isNullOrEmpty()) {
                        envMap["FNA3D_OPENGL_LIBRARY"] = fallbackPath
                    }
                }
            }

            if (preloadPaths.isNotEmpty()) {
                envMap["RALCORE_PRELOAD_LIBS"] = preloadPaths.joinToString(":")
                AppLogger.info(TAG, "RALCORE_PRELOAD_LIBS = ${envMap["RALCORE_PRELOAD_LIBS"]}")
            }

            EnvVarsManager.quickSetEnvVars(envMap)

            AppLogger.info(TAG, "Renderer loaded: $normalizedRenderer")
            AppLogger.info(TAG, "RALCORE_RENDERER = ${Os.getenv("RALCORE_RENDERER")}")
            AppLogger.info(TAG, "RALCORE_EGL = ${Os.getenv("RALCORE_EGL")}")
            AppLogger.info(TAG, "LIBGL_GLES = ${Os.getenv("LIBGL_GLES")}")
            AppLogger.info(TAG, "FNA3D_OPENGL_LIBRARY = ${Os.getenv("FNA3D_OPENGL_LIBRARY")}")

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
        val fnaLib = Os.getenv("FNA3D_OPENGL_LIBRARY")

        return when {
            ralcoreRenderer == "gl4es" && ralcoreEgl?.contains("angle") == true -> RendererRegistry.ID_GL4ES_ANGLE
            ralcoreRenderer == "gl4es" -> RendererRegistry.ID_GL4ES
            ralcoreRenderer == "mobileglues" -> RendererRegistry.ID_MOBILEGLUES
            ralcoreRenderer == "vulkan_zink" -> RendererRegistry.ID_ZINK
            ralcoreRenderer == "angle" -> RendererRegistry.ID_ANGLE
            ralcoreEgl?.contains("angle") == true -> RendererRegistry.ID_ANGLE
            fnaLib?.contains("gl4es") == true -> RendererRegistry.ID_GL4ES
            fnaLib?.contains("mobileglues") == true -> RendererRegistry.ID_MOBILEGLUES
            fnaLib?.contains("OSMesa") == true -> RendererRegistry.ID_ZINK
            else -> RendererRegistry.ID_NATIVE
        }
    }
}
