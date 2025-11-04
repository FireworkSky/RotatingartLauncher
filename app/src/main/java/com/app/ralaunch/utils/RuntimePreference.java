package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 运行时框架偏好设置管理
 * 
 * 管理 .NET Framework 版本偏好设置：
 * - 保存和读取用户选择的框架版本（net6/net7/net8/net9/net10/auto）
 * - 保存和读取用户选择的 CPU 架构（arm64/x86_64/auto）
 * - 提供统一的偏好存取接口
 * 
 * 注意：此类主要用于兼容旧的框架版本选择方式，
 * 新的运行时管理推荐使用 RuntimeManager
 */
public final class RuntimePreference {
    private static final String PREFS = "app_prefs";
    private static final String KEY_DOTNET = "dotnet_framework";
    private static final String KEY_ARCHITECTURE = "runtime_architecture";
    private static final String KEY_VERBOSE_LOGGING = "runtime_verbose_logging";
    private static final String KEY_RENDERER = "fna_renderer";
    private static final String KEY_PERFORMANCE_MONITOR = "performance_monitor_enabled";
    
    // CPU 架构常量
    public static final String ARCH_ARM64 = "arm64";
    public static final String ARCH_X86_64 = "x86_64";
    public static final String ARCH_AUTO = "auto";
    
            // 渲染器常量
            public static final String RENDERER_OPENGLES3 = "opengles3";        // 原生 OpenGL ES 3（Android 原生支持，推荐）
            public static final String RENDERER_OPENGL_GL4ES = "opengl_gl4es";  // 桌面 OpenGL 通过 gl4es 翻译到 GLES
            public static final String RENDERER_VULKAN = "vulkan";               // Vulkan（实验性）
            public static final String RENDERER_AUTO = "auto";                   // 自动选择（默认 OpenGL ES 3）

    // 加载native库以支持架构检测
    static {
        try {
            System.loadLibrary("SDL2");
            android.util.Log.d("RuntimePreference", "Native library loaded for architecture detection");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.w("RuntimePreference", "Failed to load native library: " + e.getMessage());
        }
    }

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
     * 设置运行时 CPU 架构偏好
     * 
     * @param context Android 上下文
     * @param architecture 架构（arm64/x86_64/auto）
     */
    public static void setArchitecture(Context context, String architecture) {
        if (architecture == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ARCHITECTURE, architecture)
                .apply();
    }

    /**
     * 获取运行时 CPU 架构偏好
     * 
     * @param context Android 上下文
     * @return 架构，默认为 "auto"（自动检测设备架构）
     */
    public static String getArchitecture(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_ARCHITECTURE, ARCH_AUTO); // 默认自动检测
    }

    /**
     * Native方法：获取真实的CPU架构（在Native层检测）
     * 
     * @return arm64, x86_64, arm, x86, 或 unknown
     */
    private static native String getNativeArchitecture();
    
    /**
     * 获取当前设备的 CPU 架构
     * 
     * @return arm64 或 x86_64
     * 
     * 注意：此方法现在使用Native层检测，比Build.SUPPORTED_ABIS更可靠。
     * 特别是在x86模拟器+ARM翻译层的情况下，Build.SUPPORTED_ABIS会返回错误的架构。
     */
    public static String getDeviceArchitecture() {
        try {
            // 优先使用Native层检测（最可靠）
            String nativeArch = getNativeArchitecture();
            if (nativeArch != null && !nativeArch.equals("unknown")) {
                android.util.Log.d("RuntimePreference", "Using native architecture: " + nativeArch);
                return nativeArch;
            }
        } catch (UnsatisfiedLinkError e) {
            // Native库未加载，回退到Java检测
            android.util.Log.w("RuntimePreference", "Native arch detection failed, falling back to Java detection");
        }
        
        // 回退：使用Java层检测
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) {
            String primaryAbi = abis[0];
            if (primaryAbi.startsWith("arm64") || primaryAbi.startsWith("armeabi")) {
                return ARCH_ARM64;
            } else if (primaryAbi.startsWith("x86_64")) {
                return ARCH_X86_64;
            }
        }
        return ARCH_ARM64; // 默认返回 ARM64
    }

    /**
     * 获取实际应该使用的架构（考虑 auto 模式）
     * 
     * @param context Android 上下文
     * @return 实际的架构（arm64 或 x86_64）
     */
    public static String getEffectiveArchitecture(Context context) {
        String arch = getArchitecture(context);
        if (ARCH_AUTO.equals(arch)) {
            return getDeviceArchitecture();
        }
        return arch;
    }

    /**
     * 设置运行时详细日志开关
     * 
     * @param context Android 上下文
     * @param enabled 是否启用详细日志
     */
    public static void setVerboseLogging(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VERBOSE_LOGGING, enabled)
                .apply();
    }

    /**
     * 获取运行时详细日志开关
     * 
     * @param context Android 上下文
     * @return 是否启用详细日志，默认为 false
     */
    public static boolean isVerboseLogging(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_VERBOSE_LOGGING, false);
    }

    /**
     * 设置 FNA 渲染器偏好
     * 
     * @param context Android 上下文
     * @param renderer 渲染器（opengl_gl4es/opengl_native/vulkan/auto）
     */
    public static void setRenderer(Context context, String renderer) {
        if (renderer == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RENDERER, renderer)
                .apply();
    }

    /**
     * 获取 FNA 渲染器偏好
     * 
     * @param context Android 上下文
     * @return 渲染器，默认为 "auto"（自动选择 gl4es）
     */
    public static String getRenderer(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_RENDERER, RENDERER_AUTO);
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
     * 设置性能监控开关
     * 
     * @param context Android 上下文
     * @param enabled 是否启用性能监控
     */
    public static void setPerformanceMonitorEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERFORMANCE_MONITOR, enabled)
                .apply();
    }

    /**
     * 获取性能监控开关
     * 
     * @param context Android 上下文
     * @return 是否启用性能监控，默认为 false（关闭）
     */
    public static boolean isPerformanceMonitorEnabled(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_PERFORMANCE_MONITOR, false);
    }
}


