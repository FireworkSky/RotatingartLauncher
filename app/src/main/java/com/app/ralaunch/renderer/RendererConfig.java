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
 * 渲染器配置类 - 基于环境变量的渲染器选择方案
 *
 * 核心原理：
 * 1. 通过环境变量控制渲染器选择（RALCORE_RENDERER）
 * 2. 库文件通过 LD_LIBRARY_PATH 自动可见
 * 3. 所有渲染器都提供标准的 EGL/OpenGL 接口
 * 4. SDL/FNA3D 读取环境变量并使用相应渲染器
 */
public class RendererConfig {
    private static final String TAG = "RendererConfig";

    // 渲染器 ID
    public static final String RENDERER_NATIVE_GLES = "native";             // 系统原生 EGL/GLES
    public static final String RENDERER_GL4ES = "gl4es";                    // GL4ES
    public static final String RENDERER_GL4ES_ANGLE = "gl4es+angle";        // GL4ES + ANGLE
    public static final String RENDERER_MOBILEGL = "mobilegl";              // MobileGlues
    public static final String RENDERER_ANGLE = "angle";                    // ANGLE
    public static final String RENDERER_ZINK = "zink";                      // Zink (Mesa)
    public static final String RENDERER_ZINK_25 = "zink25";                 // Zink (Mesa 25)
    public static final String RENDERER_VIRGL = "virgl";                    // VirGL
    public static final String RENDERER_FREEDRENO = "freedreno";            // Freedreno

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
            "Native OpenGL ES 3",
            "最快，有GPU加速，但可能有渲染错误",
            null,           // 使用系统 libEGL.so
            null,           // 使用系统 libGLESv2.so
            false,
            0
        ),

        // gl4es 渲染器
        new RendererInfo(
            RENDERER_GL4ES,
            "GL4ES",
            "最完美，游戏兼容性最强，但帧率稍慢",
            "libEGL_gl4es.so",
            "libGL_gl4es.so",
            true,
            0
        ),

        // gl4es + angle 渲染器
        new RendererInfo(
            RENDERER_GL4ES_ANGLE,
            "GL4ES + ANGLE",
            "翻译成Vulkan，速度和兼容性最佳，推荐高通骁龙使用",
            "libEGL_gl4es.so",
            "libGL_gl4es.so",
            true,
            0
        ),

        // MobileGlues 渲染器
        new RendererInfo(
            RENDERER_MOBILEGL,
            "MobileGl",
            "OpenGL 4.6 翻译至 OpenGL ES 3.2（现代化翻译层）",
            "libMobileGL.so",
            "libMobileGL.so",
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

        // Zink Mesa 25 渲染器
        new RendererInfo(
            RENDERER_ZINK_25,
            "Zink (Mesa 25)",
            "OpenGL 4.6 over Vulkan (Mesa 25 - 最新特性支持）",
            "libOSMesa_25.so",
            "libOSMesa_25.so",
            true,
            Build.VERSION_CODES.Q  // Mesa 25 需要 Android 10+
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

        for (RendererInfo renderer : ALL_RENDERERS) {
            if (Build.VERSION.SDK_INT < renderer.minAndroidVersion) {
                continue;
            }

            boolean hasLibraries = true;
            if (renderer.eglLibrary != null) {
                File eglLib = new File(nativeLibDir, renderer.eglLibrary);
                if (!eglLib.exists()) {
                    hasLibraries = false;
                }
            }

            if (hasLibraries && renderer.glesLibrary != null &&
                !renderer.glesLibrary.equals(renderer.eglLibrary)) {
                File glesLib = new File(nativeLibDir, renderer.glesLibrary);
                if (!glesLib.exists()) {
                    hasLibraries = false;
                }
            }

            if (hasLibraries) {
                compatible.add(renderer);
            }
        }
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
     * 获取渲染器环境变量配置（使用 RALCORE 前缀）
     */
    public static Map<String, String> getRendererEnv(Context context, String rendererId) {
        Map<String, String> envMap = new HashMap<>();
        
        // 检查是否需要加载 Turnip 驱动（Adreno GPU）
        com.app.ralaunch.utils.GLInfoUtils.GLInfo glInfo = com.app.ralaunch.utils.GLInfoUtils.getGlInfo();
        boolean isAdreno = glInfo.isAdreno();
        if (isAdreno) {
            com.app.ralaunch.data.SettingsManager settingsManager = 
                com.app.ralaunch.data.SettingsManager.getInstance(context);
            boolean useTurnip = settingsManager.isVulkanDriverTurnip();
            if (useTurnip) {
                envMap.put("RALCORE_LOAD_TURNIP", "1");
            }
        }

        switch (rendererId) {
            case RENDERER_GL4ES:
                envMap.put("RALCORE_RENDERER", "gl4es");
                // NG-GL4ES defaults to ES3 backend (DEFAULT_ES=3) for better compatibility
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_EGL", null); // 强制 unset，避免上次启动遗留设置影响
                envMap.put("LIBGL_GLES", null); // 强制 unset，避免上次启动遗留设置影响
                break;

            case RENDERER_GL4ES_ANGLE:
                envMap.put("RALCORE_RENDERER", "gl4es");
                // NG-GL4ES defaults to ES3 backend (DEFAULT_ES=3) for better compatibility
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_EGL", "libEGL_angle.so");
                envMap.put("LIBGL_GLES", "libGLESv2_angle.so");
                break;

            case RENDERER_MOBILEGL:
                envMap.put("RALCORE_RENDERER", "mobilegl");
                // MobileGlues 使用 SPIRV-Cross 进行 shader 翻译
                envMap.put("MOBILEGLUES_GLES_VERSION", "3.2");
                // 启用调试日志（可选）
                // envMap.put("MOBILEGLUES_DEBUG", "1");
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
                // 注意：不要设置 LIBGL_ALWAYS_SOFTWARE=1，因为它会强制 zink 查找 CPU 设备
                // 而 Android 设备通常没有 CPU Vulkan 设备，会导致 "CPU device requested but none found!" 错误
                // zink 本身已经是软件渲染器（通过 Vulkan），不需要 LIBGL_ALWAYS_SOFTWARE
                // 不设置 MESA_VK_DEVICE_SELECT，让 zink 自动选择
                // 但添加 ZINK_DESCRIPTORS 优化
                envMap.put("ZINK_DESCRIPTORS", "auto");
                break;

            case RENDERER_ZINK_25:
                envMap.put("RALCORE_RENDERER", "vulkan_zink");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                // Mesa 25 特性启用
                envMap.put("ZINK_DESCRIPTORS", "auto");
                envMap.put("ZINK_DEBUG", "nir");
                envMap.put("force_glsl_extensions_warn", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                // 启用更多 Mesa 25 新特性
                envMap.put("MESA_EXTENSION_MAX_YEAR", "2025");
                // 修复 OSMesa EGL 配置问题 - 跳过 EGL_RENDERABLE_TYPE
                envMap.put("SDL_EGL_SKIP_RENDERABLE_TYPE", "1");
                // 强制 Mesa 使用 zink 驱动（防止回退到硬件 OpenGL）
                envMap.put("LIBGL_ALWAYS_SOFTWARE", "1"); // 强制软件渲染，确保使用 Mesa
                // 不设置 MESA_VK_DEVICE_SELECT，让 zink 自动选择
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

