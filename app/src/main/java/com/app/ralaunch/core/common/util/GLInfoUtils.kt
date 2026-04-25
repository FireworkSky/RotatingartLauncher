package com.app.ralaunch.core.common.util

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import com.app.ralaunch.core.logging.AppLog

/**
 * OpenGL ES 信息工具类
 */
object GLInfoUtils {
    private const val TAG = "GLInfoUtils"
    const val GLES_VERSION_PREFIX = "OpenGL ES "
    
    private var info: GLInfo? = null

    data class GLInfo(
        val vendor: String,
        val renderer: String,
        val glesMajorVersion: Int
    ) {
        val isAdreno: Boolean
            get() = renderer.contains("Adreno") && vendor == "Qualcomm"
    }

    @JvmStatic
    fun getGlInfo(): GLInfo {
        info?.let { return it }
        
        AppLog.i(TAG, "Querying graphics device info...")
        val success = try {
            initAndQueryInfo()
        } catch (e: Throwable) {
            AppLog.e(TAG, "Throwable when trying to initialize GL info", e)
            false
        }
        
        if (!success) initDummyInfo()
        return info!!
    }

    private fun getMajorGLVersion(versionString: String): Int {
        val version = versionString.removePrefix(GLES_VERSION_PREFIX)
        val firstDot = version.indexOf('.')
        if (firstDot == -1) return 2
        
        return try {
            version.substring(0, firstDot).trim().toInt()
        } catch (e: NumberFormatException) {
            AppLog.w(TAG, "Failed to parse GL version number, falling back to 2", e)
            2
        }
    }

    private fun queryInfo(contextGLVersion: Int): GLInfo {
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "<Unknown>"
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "<Unknown>"
        val versionString = GLES20.glGetString(GLES30.GL_VERSION) ?: ""
        
        val version = try {
            minOf(getMajorGLVersion(versionString), contextGLVersion)
        } catch (e: NumberFormatException) {
            AppLog.w(TAG, "Failed to parse GL version number, falling back to 2", e)
            2
        }
        
        return GLInfo(vendor, renderer, version)
    }

    private fun initDummyInfo() {
        AppLog.e(TAG, "An error happened during info query. Will use dummy info.")
        info = GLInfo("<Unknown>", "<Unknown>", 2)
    }

    private fun tryCreateContext(eglDisplay: EGLDisplay, config: EGLConfig, majorVersion: Int): EGLContext? {
        val attrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, majorVersion, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, attrs, 0)
        if (context == EGL14.EGL_NO_CONTEXT || context == null) {
            AppLog.e(TAG, "Failed to create a context with major version $majorVersion")
            return null
        }
        return context
    }

    private fun tryMakeCurrent(eglDisplay: EGLDisplay, config: EGLConfig, surface: EGLSurface, majorVersion: Int): EGLContext? {
        val context = tryCreateContext(eglDisplay, config, majorVersion) ?: return null
        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, context)) {
            AppLog.i(TAG, "Failed to make context GL version $majorVersion current")
            EGL14.eglDestroyContext(eglDisplay, context)
            return null
        }
        return context
    }

    private fun initAndQueryInfo(): Boolean {
        val eglVersion = IntArray(2)
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eglDisplay, eglVersion, 0, eglVersion, 1)) {
            return false
        }

        val eglAttrs = intArrayOf(
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
        )
        
        val config = arrayOfNulls<EGLConfig>(1)
        val numConfigs = intArrayOf(0)
        
        if (!EGL14.eglChooseConfig(eglDisplay, eglAttrs, 0, config, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            EGL14.eglTerminate(eglDisplay)
            AppLog.e(TAG, "Failed to choose an EGL config")
            return false
        }

        val pbufferAttrs = intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(eglDisplay, config[0], pbufferAttrs, 0)
        
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            AppLog.e(TAG, "Failed to create pbuffer surface")
            EGL14.eglTerminate(eglDisplay)
            return false
        }

        var contextGLVersion = 3
        var context = tryMakeCurrent(eglDisplay, config[0]!!, surface, contextGLVersion)
        if (context == null) {
            contextGLVersion = 2
            context = tryMakeCurrent(eglDisplay, config[0]!!, surface, contextGLVersion)
        }

        if (context == null) {
            AppLog.e(TAG, "Failed to create and make context current")
            EGL14.eglDestroySurface(eglDisplay, surface)
            EGL14.eglTerminate(eglDisplay)
            return false
        }

        info = queryInfo(contextGLVersion)

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(eglDisplay, context)
        EGL14.eglDestroySurface(eglDisplay, surface)
        EGL14.eglTerminate(eglDisplay)
        return true
    }
}
