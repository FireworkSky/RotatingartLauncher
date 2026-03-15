package com.app.ralaunch.core.platform.runtime.dotnet

import android.util.Log
import java.io.File
// ... REMOVED: import java.nio.file.Paths to prevent Android 7 crash ...

/**
 * .NET Native 库加载器
 */
object DotNetNativeLibraryLoader {
    private const val TAG = "DotNetNativeLibLoader"
    
    @JvmStatic
    var isLoaded: Boolean = false
        private set

    @JvmStatic
    @Synchronized
    fun loadAllLibraries(dotnetRoot: String): Boolean {
        if (isLoaded) {
            Log.i(TAG, "Native libraries already loaded")
            return true
        }
        return try {
            val runtimePath = findRuntimePath(dotnetRoot)
            if (runtimePath == null) {
                Log.e(TAG, "❌ 无法找到 .NET Runtime 路径")
                return false
            }
            loadAllLibrariesInternal(runtimePath)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载 .NET Native 库失败", e)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun loadAllLibraries(dotnetRoot: String, runtimeVersion: String): Boolean {
        if (isLoaded) {
            Log.i(TAG, "Native libraries already loaded")
            return true
        }
        return try {
            // ... REPLACED: Paths.get(...) with standard File constructors ...
            val runtimePath = File(File(dotnetRoot, "shared/Microsoft.NETCore.App"), runtimeVersion).absolutePath
            loadAllLibrariesInternal(runtimePath)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载 .NET Native 库失败", e)
            false
        }
    }

    private fun loadAllLibrariesInternal(runtimePath: String): Boolean {
        Log.i(TAG, "========================================")
        Log.i(TAG, "开始加载 .NET Native 库...")
        Log.i(TAG, "Runtime 路径: $runtimePath")
        Log.i(TAG, "========================================")

        // 加载顺序非常重要
        loadLibrary(runtimePath, "libSystem.Native.so", true)
        loadLibrary(runtimePath, "libSystem.Globalization.Native.so", false)
        loadLibrary(runtimePath, "libSystem.IO.Compression.Native.so", false)
        loadLibrary(runtimePath, "libSystem.Security.Cryptography.Native.Android.so", true)

        Log.i(TAG, "========================================")
        Log.i(TAG, "✅ .NET Native 库加载完成")
        Log.i(TAG, "========================================")

        isLoaded = true
        return true
    }

    private fun loadLibrary(basePath: String, libName: String, required: Boolean) {
        try {
            // ... REPLACED: Paths.get(...) with standard File constructors ...
            val fullPath = File(basePath, libName).absolutePath
            Log.i(TAG, "正在加载: $libName")
            System.load(fullPath)
            Log.i(TAG, "  ✓ $libName 加载成功")
        } catch (e: UnsatisfiedLinkError) {
            if (required) {
                Log.e(TAG, "  ✗ $libName 加载失败 (必需库)", e)
                throw e
            } else {
                Log.w(TAG, "  ⚠ $libName 加载失败 (可选库): ${e.message}")
            }
        }
    }

    private fun findRuntimePath(dotnetRoot: String): String? {
        return try {
            val runtimeDir = File(dotnetRoot, "shared/Microsoft.NETCore.App")
            if (!runtimeDir.exists() || !runtimeDir.isDirectory) {
                Log.e(TAG, "Runtime directory not found: ${runtimeDir.absolutePath}")
                return null
            }

            val versions = runtimeDir.list()
            if (versions.isNullOrEmpty()) {
                Log.e(TAG, "No runtime versions found in: ${runtimeDir.absolutePath}")
                return null
            }

            val version = versions[0]
            Log.i(TAG, "检测到运行时版本: $version")
            
            // ... REPLACED: Paths.get(...) with standard File constructors ...
            File(runtimeDir, version).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "查找运行时路径失败", e)
            null
        }
    }
}
