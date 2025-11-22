package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.renderer.RendererLoader;

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

    // 渲染器常量（使用 RendererConfig 的 ID）
    public static final String RENDERER_AUTO = "auto";  // 自动选择（默认 native）

    // 旧常量（已弃用，仅用于向后兼容）
    @Deprecated
    public static final String RENDERER_OPENGLES3 = "opengles3";
    @Deprecated
    public static final String RENDERER_OPENGL_GL4ES = "opengl_gl4es";
    @Deprecated
    public static final String RENDERER_VULKAN = "vulkan";

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
            // 默认使用原生渲染器
            return RendererConfig.RENDERER_NATIVE_GLES;
        }
        return renderer;
    }

    /**
     * 归一化渲染器值，将旧的渲染器 ID 映射到新的 RendererConfig ID
     */
    public static String normalizeRendererValue(String value) {
        if (value == null || value.isEmpty()) {
            return RENDERER_AUTO;
        }

        // 映射旧的渲染器名称到新的 RendererConfig ID
        switch (value) {
            case "opengl_native":
            case RENDERER_OPENGLES3:
                return RendererConfig.RENDERER_NATIVE_GLES;
            case RENDERER_OPENGL_GL4ES:
                return RendererConfig.RENDERER_GL4ES;
            case RENDERER_VULKAN:
                return RendererConfig.RENDERER_NATIVE_GLES; // Vulkan 暂不支持
            default:
                return value;  // 已经是新的 ID 或 "auto"
        }
    }

    /**
     * 根据设置应用渲染器环境变量
     *
     * 新架构说明（基于 RALCORE 环境变量）：
     * 1. 使用 RendererConfig 获取渲染器配置
     * 2. 通过 RendererLoader 设置 RALCORE_RENDERER 和 RALCORE_EGL 环境变量
     * 3. SDL 层会读取这些环境变量并加载对应的渲染器
     * 4. 支持多种渲染器：native, gl4es, angle, zink, virgl, freedreno
     */
    public static void applyRendererEnvironment(Context context) {
        // 获取用户选择的渲染器偏好
        String preferredRenderer = getEffectiveRenderer(context);

        // 将渲染器配置映射到 RendererConfig ID
        String rendererId = mapRendererToConfigId(preferredRenderer);

        // 使用新的 RendererLoader 加载渲染器
        boolean success = RendererLoader.loadRenderer(context, rendererId);

        if (success) {
            android.util.Log.i(TAG, "========================================");
            android.util.Log.i(TAG, "渲染器环境变量已应用 (RALCORE Backend)");
            android.util.Log.i(TAG, "  渲染器偏好: " + preferredRenderer);
            android.util.Log.i(TAG, "  渲染器 ID: " + rendererId);
            android.util.Log.i(TAG, "  当前渲染器: " + RendererLoader.getCurrentRenderer());
            android.util.Log.i(TAG, "========================================");
        } else {
            android.util.Log.e(TAG, "Failed to load renderer: " + rendererId);
        }

        // ===== 设置 SDL 渲染器选择环境变量 =====
        // SDL 会在 Android_CreateDevice() 中读取这个变量来决定加载哪个渲染器
        setEnv("FNA3D_OPENGL_DRIVER", rendererId);
        android.util.Log.i(TAG, "FNA3D_OPENGL_DRIVER = " + rendererId);

        // ===== 设置 FNA3D 环境变量（根据渲染器类型） =====
        setEnv("FNA3D_FORCE_DRIVER", "OpenGL");

        // 根据渲染器类型设置 OpenGL 配置
        // gl4es 和 zink 需要桌面 OpenGL，native 和 angle 使用 OpenGL ES 3.0
        if (RendererConfig.RENDERER_GL4ES.equals(rendererId) ||
            RendererConfig.RENDERER_ZINK.equals(rendererId)) {
            // gl4es: OpenGL 2.1 Compatibility Profile
            // zink: OpenGL 4.6 Core/Compatibility Profile
            // 不设置 FNA3D_OPENGL_FORCE_ES3，让 FNA3D 使用桌面 OpenGL
            unsetEnv("FNA3D_OPENGL_FORCE_ES3");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MAJOR");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MINOR");

            if (RendererConfig.RENDERER_GL4ES.equals(rendererId)) {
                android.util.Log.i(TAG, "FNA3D configured for Desktop OpenGL 2.1 Compatibility Profile (renderer: gl4es)");
            } else {
                android.util.Log.i(TAG, "FNA3D configured for Desktop OpenGL 4.6 (renderer: zink)");
            }
        } else {
            // native 和 angle 使用 OpenGL ES 3.0
            setEnv("FNA3D_OPENGL_FORCE_ES3", "1");
            setEnv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3");
            setEnv("FNA3D_OPENGL_FORCE_VER_MINOR", "0");
            android.util.Log.i(TAG, "FNA3D configured for OpenGL ES 3.0 (renderer: " + rendererId + ")");
        }

        // VSync 设置
        setEnv("FORCE_VSYNC", "true");

        // 同步到 System Property
        System.setProperty("fna.renderer", preferredRenderer);
    }

    /**
     * 将用户选择的渲染器配置映射到 RendererConfig ID
     *
     * @param rendererPreference 用户偏好的渲染器
     * @return RendererConfig ID
     */
    private static String mapRendererToConfigId(String rendererPreference) {
        // 优先使用 normalizeRendererValue 进行归一化
        String normalized = normalizeRendererValue(rendererPreference);

        // 如果是 "auto"，返回默认渲染器
        if (RENDERER_AUTO.equals(normalized)) {
            return RendererConfig.RENDERER_NATIVE_GLES;
        }

        // 直接返回归一化后的值（已经是 RendererConfig ID）
        return normalized;
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
