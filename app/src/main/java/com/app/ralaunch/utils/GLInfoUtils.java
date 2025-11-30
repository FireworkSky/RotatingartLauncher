package com.app.ralaunch.utils;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

/**
 * OpenGL ES 信息工具类
 * 用于检测 GPU 信息，特别是 Adreno GPU 检测（用于 Turnip 驱动支持）
 */
public class GLInfoUtils {
    private static final String TAG = "GLInfoUtils";
    public static final String GLES_VERSION_PREFIX = "OpenGL ES ";
    private static GLInfo info;

    private static int getMajorGLVersion(String versionString) {
        if (versionString.startsWith(GLES_VERSION_PREFIX)) {
            versionString = versionString.substring(GLES_VERSION_PREFIX.length());
        }
        int firstDot = versionString.indexOf('.');
        if (firstDot == -1) {
            return 2; // 默认返回 2
        }
        String majorVersion = versionString.substring(0, firstDot).trim();
        try {
            return Integer.parseInt(majorVersion);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse GL version number, falling back to 2", e);
            return 2;
        }
    }

    private static GLInfo queryInfo(int contextGLVersion) {
        String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        String versionString = GLES20.glGetString(GLES30.GL_VERSION);
        int version = 2;
        try {
            version = getMajorGLVersion(versionString);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse GL version number, falling back to 2", e);
        }
        // 确保版本不超过上下文版本
        version = Math.min(version, contextGLVersion);
        return new GLInfo(vendor, renderer, version);
    }

    private static void initDummyInfo() {
        Log.e(TAG, "An error happened during info query. Will use dummy info. This should be investigated.");
        info = new GLInfo("<Unknown>", "<Unknown>", 2);
    }

    private static EGLContext tryCreateContext(EGLDisplay eglDisplay, EGLConfig config, int majorVersion) {
        int[] egl_context_attributes = new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, majorVersion, EGL14.EGL_NONE };
        EGLContext context = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, egl_context_attributes, 0);
        if (context == EGL14.EGL_NO_CONTEXT || context == null) {
            Log.e(TAG, "Failed to create a context with major version " + majorVersion);
            return null;
        }
        return context;
    }

    private static EGLContext tryMakeCurrent(EGLDisplay eglDisplay, EGLConfig config, EGLSurface surface, int majorVersion) {
        EGLContext context = tryCreateContext(eglDisplay, config, majorVersion);
        if (context == null) return null;
        boolean makeCurrentResult = EGL14.eglMakeCurrent(eglDisplay, surface, surface, context);
        if (!makeCurrentResult) {
            Log.i(TAG, "Failed to make context GL version " + majorVersion + " current");
            EGL14.eglDestroyContext(eglDisplay, context);
            return null;
        }
        return context;
    }

    private static boolean initAndQueryInfo() {
        int[] egl_version = new int[2];
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eglDisplay, egl_version, 0, egl_version, 1)) {
            return false;
        }
        int[] egl_attributes = new int[] {
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] config = new EGLConfig[1];
        int[] num_configs = new int[]{0};
        if (!EGL14.eglChooseConfig(eglDisplay, egl_attributes, 0, config, 0, 1, num_configs, 0) || num_configs[0] == 0) {
            EGL14.eglTerminate(eglDisplay);
            Log.e(TAG, "Failed to choose an EGL config");
            return false;
        }

        int[] pbuffer_attributes = new int[] {
                EGL14.EGL_WIDTH, 16,
                EGL14.EGL_HEIGHT, 16,
                EGL14.EGL_NONE
        };

        EGLSurface surface = EGL14.eglCreatePbufferSurface(eglDisplay, config[0], pbuffer_attributes, 0);
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "Failed to create pbuffer surface");
            EGL14.eglTerminate(eglDisplay);
            return false;
        }

        int contextGLVersion = 3;
        EGLContext context = tryMakeCurrent(eglDisplay, config[0], surface, contextGLVersion);
        if (context == null) {
            contextGLVersion = 2;
            context = tryMakeCurrent(eglDisplay, config[0], surface, contextGLVersion);
        }

        if (context == null) {
            Log.e(TAG, "Failed to create and make context current");
            EGL14.eglDestroySurface(eglDisplay, surface);
            EGL14.eglTerminate(eglDisplay);
            return false;
        }

        info = queryInfo(contextGLVersion);

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(eglDisplay, context);
        EGL14.eglDestroySurface(eglDisplay, surface);
        EGL14.eglTerminate(eglDisplay);
        return true;
    }

    /**
     * 获取 OpenGL ES 设备信息
     * @return GLInfo 对象
     */
    public static GLInfo getGlInfo() {
        if (info != null) return info;
        Log.i(TAG, "Querying graphics device info...");
        boolean infoQueryResult = false;
        try {
            infoQueryResult = initAndQueryInfo();
        } catch (Throwable e) {
            Log.e(TAG, "Throwable when trying to initialize GL info", e);
        }
        if (!infoQueryResult) initDummyInfo();
        return info;
    }

    /**
     * OpenGL ES 设备信息
     */
    public static class GLInfo {
        public final String vendor;
        public final String renderer;
        public final int glesMajorVersion;

        protected GLInfo(String vendor, String renderer, int glesMajorVersion) {
            this.vendor = vendor;
            this.renderer = renderer;
            this.glesMajorVersion = glesMajorVersion;
        }

        /**
         * 检查是否为 Qualcomm Adreno GPU
         * @return true 如果是 Adreno GPU
         */
        public boolean isAdreno() {
            return renderer != null && renderer.contains("Adreno") && 
                   vendor != null && vendor.equals("Qualcomm");
        }
    }
}



