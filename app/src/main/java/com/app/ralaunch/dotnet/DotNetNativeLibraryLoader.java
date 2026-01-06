package com.app.ralaunch.dotnet;

import android.util.Log;
import java.nio.file.Paths;

/**
 * .NET Native 库加载器
 * 负责按正确顺序加载所有必要的 .NET native 库
 */
public class DotNetNativeLibraryLoader {
    private static final String TAG = "DotNetNativeLibLoader";
    private static boolean isLoaded = false;

    /**
     * 加载所有 .NET native 库（自动检测版本）
     * @param dotnetRoot .NET Runtime 根目录
     * @return true 如果成功，false 如果失败
     */
    public static synchronized boolean loadAllLibraries(String dotnetRoot) {
        if (isLoaded) {
            Log.i(TAG, "Native libraries already loaded");
            return true;
        }

        try {
            // 自动检测运行时版本
            String runtimePath = findRuntimePath(dotnetRoot);
            if (runtimePath == null) {
                Log.e(TAG, "❌ 无法找到 .NET Runtime 路径");
                return false;
            }

            return loadAllLibrariesInternal(runtimePath);
        } catch (Exception e) {
            Log.e(TAG, "❌ 加载 .NET Native 库失败", e);
            return false;
        }
    }

    /**
     * 加载所有 .NET native 库（指定版本）
     * @param dotnetRoot .NET Runtime 根目录
     * @param runtimeVersion 运行时版本（如 "10.0.0"）
     * @return true 如果成功，false 如果失败
     */
    public static synchronized boolean loadAllLibraries(String dotnetRoot, String runtimeVersion) {
        if (isLoaded) {
            Log.i(TAG, "Native libraries already loaded");
            return true;
        }

        try {
            String runtimePath = Paths.get(dotnetRoot,
                "shared/Microsoft.NETCore.App/" + runtimeVersion).toString();

            return loadAllLibrariesInternal(runtimePath);
        } catch (Exception e) {
            Log.e(TAG, "❌ 加载 .NET Native 库失败", e);
            return false;
        }
    }

    /**
     * 内部方法：加载所有库
     * @param runtimePath 运行时路径
     * @return true 如果成功，false 如果失败
     */
    private static boolean loadAllLibrariesInternal(String runtimePath) {
        try {

            Log.i(TAG, "========================================");
            Log.i(TAG, "开始加载 .NET Native 库...");
            Log.i(TAG, "Runtime 路径: " + runtimePath);
            Log.i(TAG, "========================================");

            // ========================================
            // ⚠️ 加载顺序非常重要！
            // 依赖关系：System.Native <- System.Security.Cryptography <- 网络功能
            // ========================================

            // 1. System.Native.so - ⭐ 最重要！必须最先加载！
            //    包含所有 Unix 系统调用封装：socket, connect, bind, listen, accept 等
            //    Socket 和所有网络操作都依赖这个库
            loadLibrary(runtimePath, "libSystem.Native.so", true);

            // 2. Globalization - 用于本地化和字符编码 (可选)
            loadLibrary(runtimePath, "libSystem.Globalization.Native.so", false);

            // 3. IO.Compression - 用于数据压缩 (可选)
            loadLibrary(runtimePath, "libSystem.IO.Compression.Native.so", false);

            // 4. Security.Cryptography - TLS/SSL 加密库 (必需)
            //    依赖 System.Native 提供的基础设施
            loadLibrary(runtimePath, "libSystem.Security.Cryptography.Native.Android.so", true);

            // 5. Net.Security - 网络安全层 (通常集成在 Cryptography 中，所以是可选的)
            //loadLibrary(runtimePath, "libSystem.Net.Security.Native.so", false);

            Log.i(TAG, "========================================");
            Log.i(TAG, "✅ .NET Native 库加载完成");
            Log.i(TAG, "========================================");

            isLoaded = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 加载 .NET Native 库失败", e);
            throw e;
        }
    }

    /**
     * 加载单个库
     * @param basePath 基础路径
     * @param libName 库名称
     * @param required 是否必需（必需的库加载失败会抛出异常）
     */
    private static void loadLibrary(String basePath, String libName, boolean required) {
        try {
            String fullPath = Paths.get(basePath, libName).toString();
            Log.i(TAG, "正在加载: " + libName);
            System.load(fullPath);
            Log.i(TAG, "  ✓ " + libName + " 加载成功");
        } catch (UnsatisfiedLinkError e) {
            if (required) {
                Log.e(TAG, "  ✗ " + libName + " 加载失败 (必需库)", e);
                throw e;
            } else {
                Log.w(TAG, "  ⚠ " + libName + " 加载失败 (可选库): " + e.getMessage());
            }
        }
    }

    /**
     * 检查库是否已加载
     */
    public static boolean isLoaded() {
        return isLoaded;
    }

    /**
     * 查找运行时路径（自动检测版本）
     * @param dotnetRoot .NET Runtime 根目录
     * @return 运行时路径，失败返回 null
     */
    private static String findRuntimePath(String dotnetRoot) {
        try {
            java.io.File runtimeDir = new java.io.File(dotnetRoot, "shared/Microsoft.NETCore.App");
            if (!runtimeDir.exists() || !runtimeDir.isDirectory()) {
                Log.e(TAG, "Runtime directory not found: " + runtimeDir.getAbsolutePath());
                return null;
            }

            // 列出所有版本目录
            String[] versions = runtimeDir.list();
            if (versions == null || versions.length == 0) {
                Log.e(TAG, "No runtime versions found in: " + runtimeDir.getAbsolutePath());
                return null;
            }

            // 使用第一个找到的版本（通常只有一个）
            String version = versions[0];
            String runtimePath = Paths.get(dotnetRoot,
                "shared/Microsoft.NETCore.App/" + version).toString();

            Log.i(TAG, "检测到运行时版本: " + version);
            return runtimePath;
        } catch (Exception e) {
            Log.e(TAG, "查找运行时路径失败", e);
            return null;
        }
    }
}
