package com.app.ralaunch.renderer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染器配置类 - 基于 FoldCraftLauncher/PojavLauncher 环境变量方案
 *
 * 核心原理：
 * 1. 通过环境变量控制渲染器选择（POJAV_RENDERER）
 * 2. 库文件通过 LD_LIBRARY_PATH 自动可见
 * 3. 所有渲染器都提供标准的 EGL/OpenGL 接口
 * 4. SDL/FNA3D 读取环境变量并使用相应渲染器
 *
 * 参考实现：
 * - FoldCraftLauncher: 使用 POJAV_RENDERER + 环境变量配置
 * - PojavLauncher: 通过环境变量动态切换渲染器
 */
public class RendererConfig {
    private static final String TAG = "RendererConfig";

    // 渲染器 ID
    public static final String RENDERER_NATIVE_GLES = "native";           // 系统原生 EGL/GLES
    public static final String RENDERER_GL4ES = "gl4es";                  // GL4ES
    public static final String RENDERER_ANGLE = "angle";                  // ANGLE
    public static final String RENDERER_ZINK = "zink";                    // Zink (Mesa)
    public static final String RENDERER_VIRGL = "virgl";                  // VirGL
    public static final String RENDERER_FREEDRENO = "freedreno";          // Freedreno

    // 默认渲染器
    public static final String DEFAULT_RENDERER = RENDERER_NATIVE_GLES;

    /**
     * 渲染器信息
     */
    public static class RendererInfo {
        public final String id;
        public final String displayName;
        public final String description;
        public final String eglLibrary;      // EGL 库文件名 (null = 系统默认)
        public final String glesLibrary;     // GLES 库文件名 (null = 系统默认)
        public final boolean needsPreload;   // 是否需要通过 LD_PRELOAD 加载
        public final int minAndroidVersion;  // 最低 Android 版本

        public RendererInfo(String id, String displayName, String description,
                          String eglLibrary, String glesLibrary,
                          boolean needsPreload, int minAndroidVersion) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.eglLibrary = eglLibrary;
            this.glesLibrary = glesLibrary;
            this.needsPreload = needsPreload;
            this.minAndroidVersion = minAndroidVersion;
        }
    }

    // 所有可用渲染器
    private static final RendererInfo[] ALL_RENDERERS = {
        // 系统原生渲染器（默认）
        new RendererInfo(
            RENDERER_NATIVE_GLES,
            "Native OpenGL ES",
            "使用系统原生 EGL/OpenGL ES（最佳兼容性）",
            null,           // 使用系统 libEGL.so
            null,           // 使用系统 libGLESv2.so
            false,
            0
        ),

        // gl4es 渲染器
        new RendererInfo(
            RENDERER_GL4ES,
            "Holy GL4ES",
            "OpenGL 2.1 翻译到 OpenGL ES 2.0（兼容性最强）",
            "libEGL_gl4es.so",
            "libGL_gl4es.so",
            true,
            0
        ),

        // ANGLE 渲染器
        new RendererInfo(
            RENDERER_ANGLE,
            "ANGLE (Vulkan Backend)",
            "OpenGL ES over Vulkan (Google官方)",
            "libEGL_angle.so",
            "libGLESv2_angle.so",
            true,
            Build.VERSION_CODES.N  // Vulkan 需要 Android 7.0+
        ),

        // Zink 渲染器
        new RendererInfo(
            RENDERER_ZINK,
            "Zink (Mesa)",
            "OpenGL 4.6 over Vulkan (Mesa Zink)",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        ),

        // VirGL 渲染器
        new RendererInfo(
            RENDERER_VIRGL,
            "VirGL Renderer",
            "Gallium3D VirGL (OpenGL 4.3)",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        ),

        // Freedreno 渲染器
        new RendererInfo(
            RENDERER_FREEDRENO,
            "Freedreno (Adreno)",
            "Mesa Freedreno for Qualcomm Adreno GPU",
            "libOSMesa.so",
            "libOSMesa.so",
            true,
            Build.VERSION_CODES.N
        )
    };

    /**
     * 获取所有兼容的渲染器
     */
    public static List<RendererInfo> getCompatibleRenderers(Context context) {
        List<RendererInfo> compatible = new ArrayList<>();
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);

        AppLogger.info(TAG, "========== Checking Compatible Renderers ==========");
        AppLogger.info(TAG, "Native library directory: " + nativeLibDir.getAbsolutePath());
        AppLogger.info(TAG, "Android API Level: " + Build.VERSION.SDK_INT);

        for (RendererInfo renderer : ALL_RENDERERS) {
            AppLogger.info(TAG, "\n--- Checking renderer: " + renderer.id + " ---");
            AppLogger.info(TAG, "  Display Name: " + renderer.displayName);
            AppLogger.info(TAG, "  Min API: " + renderer.minAndroidVersion);

            // 检查 Android 版本
            if (Build.VERSION.SDK_INT < renderer.minAndroidVersion) {
                AppLogger.info(TAG, "  ✗ SKIP: requires Android API " + renderer.minAndroidVersion +
                              " (current: " + Build.VERSION.SDK_INT + ")");
                continue;
            }

            // 检查库文件是否存在
            boolean hasLibraries = true;
            if (renderer.eglLibrary != null) {
                File eglLib = new File(nativeLibDir, renderer.eglLibrary);
                AppLogger.info(TAG, "  EGL Library: " + renderer.eglLibrary);
                AppLogger.info(TAG, "  EGL Path: " + eglLib.getAbsolutePath());
                AppLogger.info(TAG, "  EGL Exists: " + eglLib.exists());

                if (!eglLib.exists()) {
                    AppLogger.info(TAG, "  ✗ SKIP: " + renderer.eglLibrary + " not found");
                    hasLibraries = false;
                }
            } else {
                AppLogger.info(TAG, "  EGL Library: (system default)");
            }

            if (hasLibraries && renderer.glesLibrary != null &&
                !renderer.glesLibrary.equals(renderer.eglLibrary)) {
                File glesLib = new File(nativeLibDir, renderer.glesLibrary);
                AppLogger.info(TAG, "  GLES Library: " + renderer.glesLibrary);
                AppLogger.info(TAG, "  GLES Path: " + glesLib.getAbsolutePath());
                AppLogger.info(TAG, "  GLES Exists: " + glesLib.exists());

                if (!glesLib.exists()) {
                    AppLogger.info(TAG, "  ✗ SKIP: " + renderer.glesLibrary + " not found");
                    hasLibraries = false;
                }
            }

            if (hasLibraries) {
                compatible.add(renderer);
                AppLogger.info(TAG, "  ✓✓✓ COMPATIBLE: " + renderer.id + " added to list");
            }
        }

        AppLogger.info(TAG, "\n========== Summary ==========");
        AppLogger.info(TAG, "Total compatible renderers: " + compatible.size());
        for (RendererInfo r : compatible) {
            AppLogger.info(TAG, "  - " + r.id + " (" + r.displayName + ")");
        }
        AppLogger.info(TAG, "================================\n");

        return compatible;
    }

    /**
     * 根据 ID 获取渲染器信息
     */
    public static RendererInfo getRendererById(String id) {
        for (RendererInfo renderer : ALL_RENDERERS) {
            if (renderer.id.equals(id)) {
                return renderer;
            }
        }
        return null;
    }

    /**
     * 检查渲染器是否兼容
     */
    public static boolean isRendererCompatible(Context context, String rendererId) {
        List<RendererInfo> compatible = getCompatibleRenderers(context);
        for (RendererInfo renderer : compatible) {
            if (renderer.id.equals(rendererId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取渲染器库的完整路径
     */
    public static String getRendererLibraryPath(Context context, String libraryName) {
        if (libraryName == null) {
            return null;
        }
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File libFile = new File(nativeLibDir, libraryName);
        return libFile.getAbsolutePath();
    }

    /**
     * 获取渲染器环境变量配置 (基于 FoldCraftLauncher 实现，使用 RALCORE 前缀)
     */
    public static Map<String, String> getRendererEnv(Context context, String rendererId) {
        Map<String, String> envMap = new HashMap<>();

        switch (rendererId) {
            case RENDERER_GL4ES:
                envMap.put("RALCORE_RENDERER", "opengles2");
                // NG-GL4ES defaults to ES3 backend (DEFAULT_ES=3) for better compatibility
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_NOERROR", "1");
                break;

            case RENDERER_ANGLE:
                envMap.put("RALCORE_EGL", "libEGL_angle.so");
                envMap.put("LIBGL_GLES", "libGLESv2_angle.so");
                break;

            case RENDERER_ZINK:
                envMap.put("RALCORE_RENDERER", "vulkan_zink");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                envMap.put("force_glsl_extensions_warn", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                break;

            case RENDERER_VIRGL:
                envMap.put("RALCORE_RENDERER", "gallium_virgl");
                envMap.put("GALLIUM_DRIVER", "virpipe");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                envMap.put("OSMESA_NO_FLUSH_FRONTBUFFER", "1");
                envMap.put("VTEST_SOCKET_NAME",
                    new File(context.getCacheDir(), ".virgl_test").getAbsolutePath());
                break;

            case RENDERER_FREEDRENO:
                envMap.put("RALCORE_RENDERER", "gallium_freedreno");
                envMap.put("GALLIUM_DRIVER", "freedreno");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                break;

            case RENDERER_NATIVE_GLES:
            default:
                // Native 渲染器不需要额外环境变量
                break;
        }

        return envMap;
    }
}
