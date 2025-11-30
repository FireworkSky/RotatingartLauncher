package com.app.ralaunch.utils;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏运行时清理器
 * 
 * <p>通过对比启动器安装的 .NET 运行时，删除游戏目录中自带的运行时文件，
 * 确保游戏使用启动器的运行时。
 * 
 * <p>这解决了以下问题：
 * <ul>
 *   <li>游戏自带的运行时可能是 Windows/Linux 专用的，在 Android 上不兼容</li>
 *   <li>Android 的 namespace 限制不允许从外部存储加载 .so 库</li>
 *   <li>运行时版本冲突</li>
 * </ul>
 */
public class GameRuntimeCleaner {
    private static final String TAG = "GameRuntimeCleaner";

    /**
     * 需要删除的运行时目录名（这些目录会被完整删除）
     */
    private static final String[] RUNTIME_DIRS = {
        "runtimes",           // NuGet 运行时目录
    };

    /**
     * 额外需要删除的运行时核心文件（不在启动器运行时中但需要删除的）
     */
    private static final String[] EXTRA_RUNTIME_FILES = {
        // Windows 平台专用
        "coreclr.dll",
        "hostpolicy.dll",
        "hostfxr.dll",
        "clrjit.dll",
        "clretwrc.dll",
        "clrgc.dll",
        "mscorrc.dll",
        "mscordaccore.dll",
        "mscordbi.dll",
        "dbgshim.dll",
        
        // macOS 平台专用
        "libcoreclr.dylib",
        "libhostpolicy.dylib",
        "libhostfxr.dylib",
        "libclrjit.dylib",
        "libSystem.Native.dylib",
        "libSystem.IO.Compression.Native.dylib",
        "libSystem.Globalization.Native.dylib",
        "libSystem.Security.Cryptography.Native.Apple.dylib",
        "libSystem.Net.Security.Native.dylib",
        "libmscordaccore.dylib",
        "libmscordbi.dylib",
        "libdbgshim.dylib",
        
        // Linux 平台专用（可能与 Android 不兼容）
        "libcoreclrtraceptprovider.so",
        "libSystem.Security.Cryptography.Native.OpenSsl.so",
        
        // 可执行文件
        "apphost",
        "dotnet",
    };

    /**
     * 清理游戏目录中的运行时文件
     * 
     * <p>通过对比启动器安装的 .NET 运行时目录，删除游戏目录中与运行时重复的文件。
     * 
     * @param context Android 上下文
     * @param gameDir 游戏目录路径
     * @return 删除的文件数量
     */
    public static int cleanGameRuntime(Context context, String gameDir) {
        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "开始清理游戏目录中的运行时文件");
        AppLogger.info(TAG, "游戏目录: " + gameDir);
        AppLogger.info(TAG, "========================================");

        File gameDirFile = new File(gameDir);
        if (!gameDirFile.exists() || !gameDirFile.isDirectory()) {
            AppLogger.warn(TAG, "游戏目录不存在: " + gameDir);
            return 0;
        }

        // Step 1: 获取启动器运行时目录
        File dotnetRoot = new File(context.getFilesDir(), "dotnet");
        if (!dotnetRoot.exists()) {
            AppLogger.warn(TAG, "启动器运行时目录不存在: " + dotnetRoot.getAbsolutePath());
            return 0;
        }

        // Step 2: 收集启动器运行时中的所有文件名（DLL 和 SO）
        Set<String> launcherRuntimeFiles = new HashSet<>();
        collectLauncherRuntimeFiles(dotnetRoot, launcherRuntimeFiles);
        
        // 添加额外需要删除的文件
        for (String fileName : EXTRA_RUNTIME_FILES) {
            launcherRuntimeFiles.add(fileName.toLowerCase());
        }

        AppLogger.info(TAG, "启动器运行时文件数量: " + launcherRuntimeFiles.size());

        // Step 3: 在游戏目录中搜索重复的文件
        List<File> filesToDelete = new ArrayList<>();
        collectDuplicateFiles(gameDirFile, launcherRuntimeFiles, filesToDelete);

        // Step 4: 收集要删除的运行时目录
        List<File> dirsToDelete = new ArrayList<>();
        collectRuntimeDirs(gameDirFile, dirsToDelete);

        if (filesToDelete.isEmpty() && dirsToDelete.isEmpty()) {
            AppLogger.info(TAG, "✓ 游戏目录中没有发现需要清理的运行时文件");
            return 0;
        }

        // Step 5: 删除重复文件
        int deletedCount = 0;
        
        if (!filesToDelete.isEmpty()) {
            AppLogger.info(TAG, "发现 " + filesToDelete.size() + " 个运行时文件需要删除:");
            for (File file : filesToDelete) {
                try {
                    String relativePath = getRelativePath(gameDirFile, file);
                    if (file.delete()) {
                        AppLogger.info(TAG, "  ✓ 已删除: " + relativePath);
                        deletedCount++;
                    } else {
                        AppLogger.warn(TAG, "  ✗ 删除失败: " + relativePath);
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "  ✗ 删除异常: " + file.getName(), e);
                }
            }
        }

        // Step 6: 删除运行时目录
        if (!dirsToDelete.isEmpty()) {
            AppLogger.info(TAG, "发现 " + dirsToDelete.size() + " 个运行时目录需要删除:");
            for (File dir : dirsToDelete) {
                try {
                    String relativePath = getRelativePath(gameDirFile, dir);
                    int dirDeletedCount = deleteDirectoryRecursive(dir);
                    if (dirDeletedCount > 0) {
                        AppLogger.info(TAG, "  ✓ 已删除目录: " + relativePath + " (" + dirDeletedCount + " 个文件)");
                        deletedCount += dirDeletedCount;
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "  ✗ 删除目录异常: " + dir.getName(), e);
                }
            }
        }

        AppLogger.info(TAG, "========================================");
        AppLogger.info(TAG, "✓ 运行时清理完成，共删除 " + deletedCount + " 个文件");
        AppLogger.info(TAG, "========================================");

        return deletedCount;
    }

    /**
     * 递归收集启动器运行时目录中的所有 DLL 和 SO 文件名
     */
    private static void collectLauncherRuntimeFiles(File dir, Set<String> result) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                // 只收集 .dll 和 .so 文件
                if (fileName.endsWith(".dll") || fileName.endsWith(".so")) {
                    result.add(fileName);
                }
            } else if (file.isDirectory()) {
                // 递归搜索子目录
                collectLauncherRuntimeFiles(file, result);
            }
        }
    }

    /**
     * 递归收集游戏目录中与启动器运行时重复的文件
     */
    private static void collectDuplicateFiles(File dir, Set<String> runtimeFileNames, List<File> result) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                // 检查是否与运行时文件重复
                if (runtimeFileNames.contains(fileName)) {
                    result.add(file);
                }
            } else if (file.isDirectory()) {
                // 不进入要删除的目录，避免重复处理
                String dirName = file.getName().toLowerCase();
                boolean isRuntimeDir = false;
                for (String rtDir : RUNTIME_DIRS) {
                    if (dirName.equals(rtDir.toLowerCase())) {
                        isRuntimeDir = true;
                        break;
                    }
                }
                if (!isRuntimeDir) {
                    collectDuplicateFiles(file, runtimeFileNames, result);
                }
            }
        }
    }

    /**
     * 递归收集运行时目录
     */
    private static void collectRuntimeDirs(File dir, List<File> result) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName().toLowerCase();
                
                // 检查是否是运行时目录
                for (String rtDir : RUNTIME_DIRS) {
                    if (dirName.equals(rtDir.toLowerCase())) {
                        result.add(file);
                        break;
                    }
                }
                
                // 递归搜索子目录
                collectRuntimeDirs(file, result);
            }
        }
    }

    /**
     * 递归删除目录
     * 
     * @return 删除的文件数量
     */
    private static int deleteDirectoryRecursive(File dir) {
        int count = 0;
        if (!dir.exists()) {
            return 0;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += deleteDirectoryRecursive(file);
                } else {
                    if (file.delete()) {
                        count++;
                    }
                }
            }
        }

        // 删除空目录
        dir.delete();
        return count;
    }

    /**
     * 获取相对路径
     */
    private static String getRelativePath(File base, File file) {
        String basePath = base.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(basePath)) {
            String relative = filePath.substring(basePath.length());
            if (relative.startsWith("/") || relative.startsWith("\\")) {
                relative = relative.substring(1);
            }
            return relative;
        }
        return file.getName();
    }

    /**
     * 检查目录是否包含运行时文件
     * 
     * @param context Android 上下文
     * @param dir 要检查的目录
     * @return true 如果包含运行时文件
     */
    public static boolean containsRuntime(Context context, File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        // 获取启动器运行时文件名集合
        File dotnetRoot = new File(context.getFilesDir(), "dotnet");
        Set<String> runtimeFileNames = new HashSet<>();
        if (dotnetRoot.exists()) {
            collectLauncherRuntimeFiles(dotnetRoot, runtimeFileNames);
        }
        
        // 添加额外的运行时文件
        for (String fileName : EXTRA_RUNTIME_FILES) {
            runtimeFileNames.add(fileName.toLowerCase());
        }

        return containsRuntimeRecursive(dir, runtimeFileNames);
    }

    private static boolean containsRuntimeRecursive(File dir, Set<String> runtimeFileNames) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                if (runtimeFileNames.contains(fileName)) {
                    return true;
                }
            } else if (file.isDirectory()) {
                // 检查是否是运行时目录
                String dirName = file.getName().toLowerCase();
                for (String rtDir : RUNTIME_DIRS) {
                    if (dirName.equals(rtDir.toLowerCase())) {
                        return true;
                    }
                }
                
                if (containsRuntimeRecursive(file, runtimeFileNames)) {
                    return true;
                }
            }
        }

        return false;
    }
}
