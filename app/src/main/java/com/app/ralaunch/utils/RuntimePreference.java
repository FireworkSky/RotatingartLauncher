package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 运行时框架偏好设置管理
 *
 * 管理 .NET Framework 版本偏好设置：
 * - 保存和读取用户选择的框架版本（net6/net7/net8/net9/net10/auto）
 * - 提供统一的偏好存取接口
 *
 * 注意：此类主要用于兼容旧的框架版本选择方式，
 * 新的运行时管理推荐使用 RuntimeManager
 *
 * 本应用仅支持 ARM64 架构。
 */
public final class RuntimePreference {
    private static final String TAG = "RuntimePreference";
    private static final String PREFS = "app_prefs";
    private static final String KEY_DOTNET = "dotnet_framework";
    private static final String KEY_VERBOSE_LOGGING = "runtime_verbose_logging";
    private static final String KEY_RENDERER = "fna_renderer";
    
    // 渲染器常量
    public static final String RENDERER_OPENGLES3 = "opengles3";        // 原生 OpenGL ES 3（Android 原生支持，推荐）
    public static final String RENDERER_OPENGL_GL4ES = "opengl_gl4es";  // 桌面 OpenGL 通过 gl4es 翻译到 GLES
    public static final String RENDERER_VULKAN = "vulkan";               // Vulkan（实验性）
    public static final String RENDERER_AUTO = "auto";                   // 自动选择（默认 OpenGL ES 3）

    private RuntimePreference() {}

    /**
     * 设置 .NET Framework 版本偏好
     * 
     * @param context Android 上下文
     * @param value 框架版本（net6/net7/net8/net9/net10/auto）
     */
    public static void setDotnetFramework(Context context, String value) {
        if (value == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DOTNET, value)
                .apply();
    }

    /**
     * 获取 .NET Framework 版本偏好
     * 
     * @param context Android 上下文
     * @return 框架版本，默认为 "auto"
     */
    public static String getDotnetFramework(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_DOTNET, "auto");
    }


    /**
     * 设置运行时详细日志开关
     * 
     * @param context Android 上下文
     * @param enabled 是否启用详细日志
     */
    public static void setVerboseLogging(Context context, boolean enabled) {
        SettingsManager.getInstance(context).setVerboseLogging(enabled);
    }

    /**
     * 获取运行时详细日志开关
     * 
     * @param context Android 上下文
     * @return 是否启用详细日志，默认为 false
     */
    public static boolean isVerboseLogging(Context context) {
        return SettingsManager.getInstance(context).isVerboseLogging();
    }

    /**
     * 设置 FNA 渲染器偏好
     * 
     * @param context Android 上下文
     * @param renderer 渲染器（opengl_gl4es/opengl_native/vulkan/auto）
     */
    public static void setRenderer(Context context, String renderer) {
        if (renderer == null) return;
        SettingsManager.getInstance(context).setFnaRenderer(renderer);
    }

    /**
     * 获取 FNA 渲染器偏好
     * 
     * @param context Android 上下文
     * @return 渲染器，默认为 "auto"（自动选择 gl4es）
     */
    public static String getRenderer(Context context) {
        SettingsManager manager = SettingsManager.getInstance(context);
        String raw = manager.getFnaRenderer();
        String normalized = normalizeRendererValue(raw);
        if (!normalized.equals(raw)) {
            manager.setFnaRenderer(normalized);
        }
        return normalized;
    }

    /**
     * 获取实际应该使用的渲染器（考虑 auto 模式）
     * 
     * @param context Android 上下文
     * @return 实际的渲染器
     */
    public static String getEffectiveRenderer(Context context) {
        String renderer = getRenderer(context);
        if (RENDERER_AUTO.equals(renderer)) {
            // 默认使用原生 OpenGL ES 3（Android 原生支持，性能最佳）
            return RENDERER_OPENGLES3;
        }
        return renderer;
    }

    /**
     * 归一化渲染器值，兼容旧版字符串
     */
    public static String normalizeRendererValue(String value) {
        if (value == null || value.isEmpty()) {
            return RENDERER_AUTO;
        }
        if ("opengl_native".equals(value)) {
            return RENDERER_OPENGLES3;
        }
        return value;
    }

    /**
     * 根据设置应用 FNA 渲染器相关的环境变量
     *
     * 新的EGL架构说明：
     * 1. FNA3D_OPENGL_LIBRARY: 指定EGL库路径（默认libEGL.so）
     * 2. FNA3D_OPENGL_DRIVER: 控制OpenGL绑定类型（native/gl4es/desktop）
     * 3. LIBGL_ES: 指定OpenGL ES版本（1/2/3）
     * 4. FORCE_VSYNC: 强制启用垂直同步
     */
    public static void applyRendererEnvironment(Context context) {
        String renderer = getEffectiveRenderer(context);
        boolean useOpenGLPath = true;
        String driverValue = "native";
        String forceDriver = "OpenGL";
        String eglLibrary = "libEGL.so";  // 默认系统EGL库

        switch (renderer) {
            case RENDERER_OPENGL_GL4ES:
                // gl4es渲染器：使用gl4es提供的EGL实现
                // libGL.so 包含了 gl4es 的 EGL + OpenGL ES 实现
                driverValue = "gl4es";
                eglLibrary = context.getApplicationInfo().nativeLibraryDir + "/libGL.so";
                android.util.Log.i(TAG, "使用 gl4es 渲染器: " + eglLibrary);
                break;

            case RENDERER_VULKAN:
                // Vulkan渲染器
                useOpenGLPath = false;
                driverValue = null;
                forceDriver = "Vulkan";
                break;

            case RENDERER_OPENGLES3:
            default:
                // 原生OpenGL ES 3渲染器（Android系统自带）
                driverValue = "native";
                eglLibrary = "libEGL.so";  // 系统的 EGL 实现
                android.util.Log.i(TAG, "使用原生渲染器: " + eglLibrary);
                break;
        }

        // ===== 设置FNA3D核心环境变量 =====

        // 强制使用的驱动类型（OpenGL或Vulkan）
        if (forceDriver != null) {
            setEnv("FNA3D_FORCE_DRIVER", forceDriver);
        } else {
            unsetEnv("FNA3D_FORCE_DRIVER");
        }

        // OpenGL驱动实现类型（native/gl4es/desktop）
        if (driverValue != null) {
            setEnv("FNA3D_OPENGL_DRIVER", driverValue);
        } else {
            unsetEnv("FNA3D_OPENGL_DRIVER");
        }

        // ===== EGL库路径（SDL会读取此变量加载正确的EGL实现） =====
        if (eglLibrary != null && useOpenGLPath) {
            setEnv("FNA3D_OPENGL_LIBRARY", eglLibrary);
        } else {
            unsetEnv("FNA3D_OPENGL_LIBRARY");
        }

        // ===== OpenGL相关环境变量 =====
        if (useOpenGLPath) {
            // FNA3D OpenGL配置
            setEnv("FNA3D_OPENGL_FORCE_CORE_PROFILE", "0");
            setEnv("FNA3D_OPENGL_FORCE_ES3", "1");
            setEnv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3");
            setEnv("FNA3D_OPENGL_FORCE_VER_MINOR", "0");
            setEnv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE", "1");

            // gl4es特定配置（如果使用gl4es）
            if ("gl4es".equals(driverValue)) {
                setEnv("LIBGL_ES", "3");           // OpenGL ES 3.0
                setEnv("LIBGL_GL", "30");          // 模拟OpenGL 3.0
                setEnv("LIBGL_LOGERR", "1");       // 启用错误日志
                setEnv("LIBGL_DEBUG", "0");        // 调试模式（0=关闭，1=基础，2=详细）

                // gl4es性能优化选项
                setEnv("LIBGL_BATCH", "1");        // 启用批处理（提升性能）
                setEnv("LIBGL_NOERROR", "0");      // 不忽略错误
                setEnv("LIBGL_NODEPTHTEX", "0");   // 支持深度纹理
            } else {
                // 原生渲染器：设置OpenGL ES版本
                setEnv("LIBGL_ES", "3");           // 使用OpenGL ES 3.0

                // 清除gl4es特定变量
                unsetEnv("LIBGL_GL");
                unsetEnv("LIBGL_LOGERR");
                unsetEnv("LIBGL_DEBUG");
                unsetEnv("LIBGL_BATCH");
                unsetEnv("LIBGL_NOERROR");
                unsetEnv("LIBGL_NODEPTHTEX");
            }
        } else {
            // Vulkan路径：清除所有OpenGL相关变量
            unsetEnv("FNA3D_OPENGL_FORCE_CORE_PROFILE");
            unsetEnv("FNA3D_OPENGL_FORCE_ES3");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MAJOR");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MINOR");
            unsetEnv("FNA3D_OPENGL_FORCE_COMPATIBILITY_PROFILE");
            unsetEnv("LIBGL_ES");
            unsetEnv("LIBGL_GL");
            unsetEnv("LIBGL_LOGERR");
            unsetEnv("LIBGL_DEBUG");
            unsetEnv("LIBGL_BATCH");
            unsetEnv("LIBGL_NOERROR");
            unsetEnv("LIBGL_NODEPTHTEX");
        }

        // ===== VSync设置 =====
        // 可以从设置中读取VSync偏好
        boolean enableVsync = true;  // 默认启用
        if (enableVsync) {
            setEnv("FORCE_VSYNC", "true");
        } else {
            unsetEnv("FORCE_VSYNC");
        }

        // ===== 同步到 System Property（供 GameLauncher 预加载使用） =====
        System.setProperty("fna.renderer", renderer);

        android.util.Log.i(TAG, "========================================");
        android.util.Log.i(TAG, "渲染器环境变量已应用 (EGL Backend)");
        android.util.Log.i(TAG, "  渲染器: " + renderer);
        android.util.Log.i(TAG, "  驱动类型: " + driverValue);
        android.util.Log.i(TAG, "  EGL库: " + eglLibrary);
        android.util.Log.i(TAG, "  使用OpenGL: " + useOpenGLPath);
        android.util.Log.i(TAG, "  VSync: " + enableVsync);
        android.util.Log.i(TAG, "========================================");
    }

    private static void setEnv(String key, String value) {
        try {
            android.system.Os.setenv(key, value, true);
        } catch (android.system.ErrnoException e) {
            android.util.Log.w(TAG, "无法设置环境变量 " + key + ": " + e.getMessage());
        }
    }

    private static void unsetEnv(String key) {
        try {
            android.system.Os.unsetenv(key);
        } catch (android.system.ErrnoException e) {
            android.util.Log.w(TAG, "无法清除环境变量 " + key + ": " + e.getMessage());
        }
    }
    
    /**
     * 获取 .NET 运行时根目录路径
     *
     * @return .NET 运行时目录路径（/data/data/com.app.ralaunch/files/dotnet）
     */
    public static String getDotnetRootPath() {
        try {
            Context appContext = com.app.ralaunch.RaLaunchApplication.getAppContext();
            if (appContext == null) {
                android.util.Log.w("RuntimePreference", "Application context is null, cannot get dotnet root path");
                return null;
            }
            
            // .NET 运行时目录（默认 ARM64 架构）
            String dotnetDir = "dotnet";
            
            String dotnetPath = appContext.getFilesDir().getAbsolutePath() + "/" + dotnetDir;
            android.util.Log.d("RuntimePreference", "Dotnet root path: " + dotnetPath);
            
            return dotnetPath;
            
        } catch (Exception e) {
            android.util.Log.e("RuntimePreference", "Failed to get dotnet root path", e);
            return null;
        }
    }
    
    /**
     * 获取首选的 .NET Framework 主版本号
     * 
     * @return 框架主版本号（6/7/8/9/10），0 表示自动选择最高版本
     */
    public static int getPreferredFrameworkMajor() {
        try {
            Context appContext = com.app.ralaunch.RaLaunchApplication.getAppContext();
            if (appContext == null) {
                android.util.Log.w("RuntimePreference", "Application context is null, using auto framework");
                return 0; // 自动选择
            }
            
            String framework = getDotnetFramework(appContext);
            
            // 解析框架版本
            switch (framework) {
                case "net6":
                    return 6;
                case "net7":
                    return 7;
                case "net8":
                    return 8;
                case "net9":
                    return 9;
                case "net10":
                    return 10;
                case "auto":
                default:
                    return 0; // 0 表示自动选择最高版本
            }
            
        } catch (Exception e) {
            android.util.Log.e("RuntimePreference", "Failed to get preferred framework major", e);
            return 0;
        }
    }

}
