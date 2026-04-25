package com.app.ralaunch.core.platform.runtime.dotnet

import com.app.ralaunch.core.logging.AppLog
import java.io.File
import java.nio.file.Paths

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
            AppLog.i(TAG, "Native libraries already loaded")
            return true
        }
        return try {
            val runtimePath = findRuntimePath(dotnetRoot)
            if (runtimePath == null) {
                AppLog.e(TAG, "❌ 无法找到 .NET Runtime 路径")
                return false
            }
            loadAllLibrariesInternal(runtimePath)
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ 加载 .NET Native 库失败", e)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun loadAllLibraries(dotnetRoot: String, runtimeVersion: String): Boolean {
        if (isLoaded) {
            AppLog.i(TAG, "Native libraries already loaded")
            return true
        }
        return try {
            val runtimePath = Paths.get(dotnetRoot, "shared/Microsoft.NETCore.App/$runtimeVersion").toString()
            loadAllLibrariesInternal(runtimePath)
        } catch (e: Exception) {
            AppLog.e(TAG, "❌ 加载 .NET Native 库失败", e)
            false
        }
    }

    private fun loadAllLibrariesInternal(runtimePath: String): Boolean {
        AppLog.i(TAG, "========================================")
        AppLog.i(TAG, "开始加载 .NET Native 库...")
        AppLog.i(TAG, "Runtime 路径: $runtimePath")
        AppLog.i(TAG, "========================================")

        // 加载顺序非常重要
        loadLibrary(runtimePath, "libSystem.Native.so", true)
        loadLibrary(runtimePath, "libSystem.Globalization.Native.so", false)
        loadLibrary(runtimePath, "libSystem.IO.Compression.Native.so", false)
        loadLibrary(runtimePath, "libSystem.Security.Cryptography.Native.Android.so", true)

        AppLog.i(TAG, "========================================")
        AppLog.i(TAG, "✅ .NET Native 库加载完成")
        AppLog.i(TAG, "========================================")

        isLoaded = true
        return true
    }

    private fun loadLibrary(basePath: String, libName: String, required: Boolean) {
        try {
            val fullPath = Paths.get(basePath, libName).toString()
            AppLog.i(TAG, "正在加载: $libName")
            System.load(fullPath)
            AppLog.i(TAG, "  ✓ $libName 加载成功")
        } catch (e: UnsatisfiedLinkError) {
            if (required) {
                AppLog.e(TAG, "  ✗ $libName 加载失败 (必需库)", e)
                throw e
            } else {
                AppLog.w(TAG, "  ⚠ $libName 加载失败 (可选库): ${e.message}")
            }
        }
    }

    private fun findRuntimePath(dotnetRoot: String): String? {
        return try {
            val runtimeDir = File(dotnetRoot, "shared/Microsoft.NETCore.App")
            if (!runtimeDir.exists() || !runtimeDir.isDirectory) {
                AppLog.e(TAG, "Runtime directory not found: ${runtimeDir.absolutePath}")
                return null
            }

            val versions = runtimeDir.list()?.sortedWith { left, right ->
                compareVersions(right, left)
            }
            if (versions.isNullOrEmpty()) {
                AppLog.e(TAG, "No runtime versions found in: ${runtimeDir.absolutePath}")
                return null
            }

            val version = versions[0]
            AppLog.i(TAG, "检测到运行时版本: $version")
            Paths.get(dotnetRoot, "shared/Microsoft.NETCore.App/$version").toString()
        } catch (e: Exception) {
            AppLog.e(TAG, "查找运行时路径失败", e)
            null
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxLength) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }
}
