package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .NET 运行时管理器
 * 
 * <p>此类负责管理 Android 应用中的 .NET 运行时，包括：
 * <ul>
 *   <li>运行时版本的列举和选择</li>
 *   <li>运行时路径管理</li>
 *   <li>受信程序集列表构建</li>
 *   <li>原生库搜索路径构建</li>
 * </ul>
 * 
 * <p>支持多版本运行时共存（.NET 7/8/9/10），允许用户切换不同版本。
 * 
 * @author RA Launcher Team
 */
public final class RuntimeManager {
    private static final String TAG = "RuntimeManager";
    private static final String PREFS = "app_prefs";
    private static final String KEY_RUNTIME_VERSION = "dotnet_runtime_version";

    /** 私有构造函数，防止实例化 */
    private RuntimeManager() {}

    /**
     * 获取 .NET 运行时根目录（根据架构选择）
     * 
     * @param ctx Android 上下文
     * @return .NET 运行时根目录（如 /data/data/com.app/files/dotnet-arm64）
     */
    public static File getDotnetRoot(Context ctx) {
        String arch = RuntimePreference.getEffectiveArchitecture(ctx);
        String dirName = "dotnet-" + arch;
        
        File archSpecificDir = new File(ctx.getFilesDir(), dirName);
        if (archSpecificDir.exists()) {
            Log.d(TAG, "Using architecture-specific dotnet root: " + dirName);
            return archSpecificDir;
        }
        
        // 回退到默认 dotnet 目录（向后兼容）
        File defaultDir = new File(ctx.getFilesDir(), "dotnet");
        Log.d(TAG, "Using default dotnet root: dotnet");
        return defaultDir;
    }

    /**
     * 获取共享运行时目录（Microsoft.NETCore.App）
     * 
     * <p>此目录包含所有已安装的运行时版本，每个版本一个子目录。
     * 
     * @param ctx Android 上下文
     * @return 共享运行时目录
     */
    public static File getSharedRoot(Context ctx) {
        File dotnetRoot = getDotnetRoot(ctx);
        File sharedRoot = new File(dotnetRoot, "shared/Microsoft.NETCore.App");
        
        if (sharedRoot.exists()) {
            Log.d(TAG, "Using shared root path: " + sharedRoot.getAbsolutePath());
        } else {
            Log.w(TAG, "Shared root not found: " + sharedRoot.getAbsolutePath());
        }
        
        return sharedRoot;
    }

    private static boolean hasAssetsDir(Context ctx, String dir) {
        try { String[] list = ctx.getAssets().list(dir); return list != null && list.length > 0; }
        catch (Exception e) { return false; }
    }

    private static boolean assetExists(Context ctx, String name) {
        try (InputStream is = ctx.getAssets().open(name)) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void copyAssetDirRecursively(Context ctx, String assetDir, File destRoot) throws Exception {
        String[] items = ctx.getAssets().list(assetDir);
        if (items == null) return;
        for (String item : items) {
            String assetPath = assetDir + "/" + item;
            String[] children = ctx.getAssets().list(assetPath);
            if (children != null && children.length > 0) {
                copyAssetDirRecursively(ctx, assetPath, destRoot);
            } else {
                File out = new File(destRoot, assetPath.replaceFirst("^Runtime/?", ""));
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream is = ctx.getAssets().open(assetPath); FileOutputStream fos = new FileOutputStream(out)) {
                    StreamUtils.transferTo(is, fos);
                }
            }
        }
    }

    private static void unzipAsset(Context ctx, String assetPath, File destDir) throws Exception {
        destDir.mkdirs();
        try (InputStream is = ctx.getAssets().open(assetPath); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    java.nio.file.Files.copy(zis, out.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 列出所有已安装的运行时版本
     * @return 已安装版本列表，按版本号排序
     */
    public static List<String> listInstalledVersions(Context ctx) {
        File shared = getSharedRoot(ctx);
        if (!shared.exists()) {
            Log.w(TAG, "Shared runtime directory does not exist: " + shared.getAbsolutePath());
            return Collections.emptyList();
        }
        
        File[] dirs = shared.listFiles(File::isDirectory);
        List<String> res = new ArrayList<>();
        
        if (dirs != null) {
            for (File d : dirs) {
                String name = d.getName();
                // 验证是否为有效的版本号格式（例如 7.0.0, 8.0.1, 9.0.0, 10.0.0）
                if (name.matches("\\d+\\.\\d+\\.\\d+.*")) {
                    res.add(name);
                    Log.d(TAG, "Found runtime version: " + name);
                }
            }
        }
        
        // 按版本号排序
        Collections.sort(res, (v1, v2) -> {
            try {
                String[] parts1 = v1.split("\\.");
                String[] parts2 = v2.split("\\.");
                
                for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
                    int num1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                    int num2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
                    
                    if (num1 != num2) {
                        return Integer.compare(num1, num2);
                    }
                }
                
                return Integer.compare(parts1.length, parts2.length);
            } catch (Exception e) {
                return v1.compareTo(v2);
            }
        });
        
        Log.d(TAG, "Total installed versions: " + res.size());
        return res;
    }

    /**
     * 获取当前选中的运行时版本
     * @return 选中的版本号，如果未设置则返回最新版本
     */
    public static String getSelectedVersion(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String v = sp.getString(KEY_RUNTIME_VERSION, null);
        
        if (v != null && !v.isEmpty()) {
            // 验证选中的版本是否仍然存在
            List<String> installed = listInstalledVersions(ctx);
            if (installed.contains(v)) {
                Log.d(TAG, "Using selected version: " + v);
                return v;
            } else {
                Log.w(TAG, "Selected version not found: " + v + ", falling back to latest");
            }
        }
        
        // 默认返回最新版本
        List<String> vers = listInstalledVersions(ctx);
        if (!vers.isEmpty()) {
            String latest = vers.get(vers.size() - 1);
            Log.d(TAG, "Using latest version: " + latest);
            return latest;
        }
        
        Log.e(TAG, "No runtime versions installed");
        return null;
    }

    /**
     * 设置选中的运行时版本
     * @param version 版本号
     */
    public static void setSelectedVersion(Context ctx, String version) {
        Log.d(TAG, "Setting selected runtime version: " + version);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_RUNTIME_VERSION, version)
           .apply();
    }
    
    /**
     * 获取版本的主版本号（例如 "8.0.1" 返回 8）
     * @param version 完整版本号
     * @return 主版本号，失败返回 -1
     */
    public static int getMajorVersion(String version) {
        if (version == null || version.isEmpty()) return -1;
        try {
            String[] parts = version.split("\\.");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse major version from: " + version, e);
        }
        return -1;
    }
    
    /**
     * 获取指定主版本的最新运行时版本
     * @param majorVersion 主版本号（如 7, 8, 9, 10）
     * @return 该主版本的最新版本号，未找到返回 null
     */
    public static String getLatestVersionForMajor(Context ctx, int majorVersion) {
        List<String> versions = listInstalledVersions(ctx);
        String latest = null;
        
        for (String version : versions) {
            if (getMajorVersion(version) == majorVersion) {
                latest = version; // 因为列表已排序，最后一个就是最新的
            }
        }
        
        Log.d(TAG, "Latest version for major " + majorVersion + ": " + latest);
        return latest;
    }

    /**
     * 构建受信程序集列表（Trusted Platform Assemblies）
     * 
     * <p>此方法递归扫描运行时目录和应用目录，收集所有 .dll 文件的完整路径，
     * 并用冒号（:）分隔返回。这个列表会被传递给 CoreCLR 运行时。
     * 
     * @param runtimeVerDir 运行时版本目录（如 .../Microsoft.NETCore.App/8.0.1）
     * @param appDir 应用程序目录
     * @return 受信程序集路径列表（冒号分隔）
     */
    public static String buildTrustedAssemblies(File runtimeVerDir, File appDir) {
        StringBuilder sb = new StringBuilder();
        appendDlls(sb, runtimeVerDir);
        appendDlls(sb, appDir);
        return sb.toString();
    }

    /**
     * 构建原生库搜索路径
     * 
     * <p>此方法构建一个包含以下目录的搜索路径（冒号分隔）：
     * <ul>
     *   <li>运行时版本目录</li>
     *   <li>应用程序目录</li>
     *   <li>系统库目录（/system/lib64）</li>
     *   <li>厂商库目录（/vendor/lib64）</li>
     * </ul>
     * 
     * @param runtimeVerDir 运行时版本目录
     * @param appDir 应用程序目录
     * @return 原生库搜索路径列表（冒号分隔）
     */
    public static String buildNativeSearchPaths(File runtimeVerDir, File appDir) {
        StringBuilder sb = new StringBuilder();
        appendPath(sb, runtimeVerDir.getAbsolutePath());
        appendPath(sb, appDir.getAbsolutePath());
        
        // 添加 host 库目录
        File hostDir = new File(runtimeVerDir.getParentFile().getParentFile().getParentFile(), "host");
        if (hostDir.exists()) {
            appendPath(sb, hostDir.getAbsolutePath());
            Log.d(TAG, "Added host directory to search paths: " + hostDir.getAbsolutePath());
        }
        
        appendPath(sb, "/system/lib64");
        appendPath(sb, "/vendor/lib64");
        return sb.toString();
    }

    /**
     * 递归扫描目录并收集所有 .dll 文件的绝对路径
     * 
     * @param sb 字符串构建器（用于累积结果）
     * @param dir 要扫描的目录
     */
    private static void appendDlls(StringBuilder sb, File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                appendDlls(sb, f);
            } else if (f.getName().toLowerCase().endsWith(".dll")) {
                if (sb.length() > 0) sb.append(":");
                sb.append(f.getAbsolutePath());
            }
        }
    }

    /**
     * 向路径列表中追加一个路径
     * 
     * @param sb 字符串构建器
     * @param p 要追加的路径
     */
    private static void appendPath(StringBuilder sb, String p) {
        if (p == null || p.isEmpty()) return;
        if (sb.length() > 0) sb.append(":");
        sb.append(p);
    }
}


