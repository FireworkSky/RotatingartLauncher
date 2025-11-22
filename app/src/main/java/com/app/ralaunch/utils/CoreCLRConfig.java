package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.data.SettingsManager;

/**
 * CoreCLR 配置工具类
 *
 * 负责将用户设置的 CoreCLR 配置应用到环境变量中，
 * 这些环境变量会在 .NET 运行时启动时被读取。
 *
 * 支持的配置项：
 * - GC 配置：Server GC、Concurrent GC、Heap Count、Retain VM
 * - JIT 配置：Tiered Compilation、Quick JIT、Optimize Type
 */
public class CoreCLRConfig {
    private static final String TAG = "CoreCLRConfig";

    /**
     * 应用 CoreCLR 配置到 native 层
     * 此方法需要在启动 .NET 运行时之前调用
     *
     * @param context Android Context
     */
    public static void applyConfig(Context context) {
        SettingsManager settings = SettingsManager.getInstance(context);

        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "应用 CoreCLR 配置");
        AppLogger.info(TAG, "========================================");

        // GC 配置
        applyGCConfig(settings);

        // JIT 配置
        applyJITConfig(settings);

        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "CoreCLR 配置已应用");
        AppLogger.info(TAG, "========================================");
    }

    /**
     * 应用 GC 配置
     */
    private static void applyGCConfig(SettingsManager settings) {
        // Server GC
        boolean serverGC = settings.isServerGC();
        setNativeEnv("DOTNET_gcServer", serverGC ? "1" : "0");
        AppLogger.info(TAG, "  Server GC: " + (serverGC ? "启用" : "关闭"));

        // Concurrent GC
        boolean concurrentGC = settings.isConcurrentGC();
        setNativeEnv("DOTNET_gcConcurrent", concurrentGC ? "1" : "0");
        AppLogger.info(TAG, "  Concurrent GC: " + (concurrentGC ? "启用" : "关闭"));

        // GC Heap Count
        String heapCount = settings.getGCHeapCount();
        if (!"auto".equals(heapCount)) {
            setNativeEnv("DOTNET_GCHeapCount", heapCount);
            AppLogger.info(TAG, "  GC Heap Count: " + heapCount);
        } else {
            AppLogger.info(TAG, "  GC Heap Count: 自动");
        }

        // Retain VM
        boolean retainVM = settings.isRetainVM();
        setNativeEnv("DOTNET_GCRetainVM", retainVM ? "1" : "0");
        AppLogger.info(TAG, "  Retain VM: " + (retainVM ? "启用" : "关闭"));
    }

    /**
     * 应用 JIT 配置
     */
    private static void applyJITConfig(SettingsManager settings) {
        // Tiered Compilation
        boolean tieredCompilation = settings.isTieredCompilation();
        setNativeEnv("DOTNET_TieredCompilation", tieredCompilation ? "1" : "0");
        AppLogger.info(TAG, "  Tiered Compilation: " + (tieredCompilation ? "启用" : "关闭"));

        // Quick JIT
        boolean quickJIT = settings.isQuickJIT();
        if (tieredCompilation) {
            setNativeEnv("DOTNET_TC_QuickJit", quickJIT ? "1" : "0");
            AppLogger.info(TAG, "  Quick JIT: " + (quickJIT ? "启用" : "关闭"));
        }

        // JIT Optimize Type
        int optimizeType = settings.getJitOptimizeType();
        String optimizeTypeName;
        switch (optimizeType) {
            case 1:
                optimizeTypeName = "size (体积优先)";
                break;
            case 2:
                optimizeTypeName = "speed (速度优先)";
                break;
            case 0:
            default:
                optimizeTypeName = "blended (混合)";
                break;
        }
        setNativeEnv("DOTNET_JitOptimizeType", String.valueOf(optimizeType));
        AppLogger.info(TAG, "  JIT Optimize Type: " + optimizeTypeName);
    }

    /**
     * 通过 JNI 设置 native 层的环境变量
     * 这个方法会调用 C++ 的 setenv 函数
     *
     * @param key 环境变量名
     * @param value 环境变量值
     */
    private static void setNativeEnv(String key, String value) {
        try {
            nativeSetEnv(key, value);
        } catch (UnsatisfiedLinkError e) {
            AppLogger.warn(TAG, "无法设置环境变量 " + key + ": native 方法未找到");
        }
    }

    /**
     * 获取当前 CoreCLR 配置的摘要信息
     *
     * @param context Android Context
     * @return 配置摘要字符串
     */
    public static String getConfigSummary(Context context) {
        SettingsManager settings = SettingsManager.getInstance(context);
        StringBuilder sb = new StringBuilder();

        sb.append("CoreCLR 配置摘要:\n");
        sb.append("  GC:\n");
        sb.append("    Server GC: ").append(settings.isServerGC() ? "启用" : "关闭").append("\n");
        sb.append("    Concurrent GC: ").append(settings.isConcurrentGC() ? "启用" : "关闭").append("\n");
        sb.append("    Heap Count: ").append(settings.getGCHeapCount()).append("\n");
        sb.append("    Retain VM: ").append(settings.isRetainVM() ? "启用" : "关闭").append("\n");
        sb.append("  JIT:\n");
        sb.append("    Tiered Compilation: ").append(settings.isTieredCompilation() ? "启用" : "关闭").append("\n");
        sb.append("    Quick JIT: ").append(settings.isQuickJIT() ? "启用" : "关闭").append("\n");

        int optimizeType = settings.getJitOptimizeType();
        String optimizeTypeName;
        switch (optimizeType) {
            case 1: optimizeTypeName = "体积优先"; break;
            case 2: optimizeTypeName = "速度优先"; break;
            default: optimizeTypeName = "混合"; break;
        }
        sb.append("    Optimize Type: ").append(optimizeTypeName).append("\n");

        return sb.toString();
    }

    /**
     * Native 方法：设置环境变量
     * 这个方法在 main.cpp 或 netcorehost_launcher.cpp 中实现
     */
    private static native void nativeSetEnv(String key, String value);
}
