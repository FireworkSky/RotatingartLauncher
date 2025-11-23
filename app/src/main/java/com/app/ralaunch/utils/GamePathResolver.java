package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralib.icon.IconExtractor;

import java.io.File;
import java.util.function.Predicate;

/**
 * 游戏路径解析器
 *
 * 负责在游戏目录中查找和解析游戏可执行文件路径
 */
public class GamePathResolver {
    private static final String TAG = "GamePathResolver";
    private static Context sContext;

    /**
     * 初始化上下文（需要在使用前调用一次）
     */
    public static void initialize(Context context) {
        sContext = context.getApplicationContext();
    }

    /**
     * 查找游戏可执行文件路径
     * @param gamePath 游戏基础路径
     * @return 游戏可执行文件的完整路径,如果未找到则返回 null
     */
    public static String findGameBodyPath(String gamePath) {
        if (gamePath == null) {
            return null;
        }

        File gameDir = new File(gamePath);
        if (!gameDir.exists() || !gameDir.isDirectory()) {
            // 如果是文件,直接返回
            if (new File(gamePath).isFile()) {
                return gamePath;
            }
            return null;
        }

        // 查找具有入口点和图标的 DLL 文件（游戏主程序）
        File gameDll = findGameExecutable(gameDir, ".dll");
        if (gameDll != null) {
            AppLogger.info(TAG, "Found game DLL with entry point and icon: " + gameDll.getAbsolutePath());
            return gameDll.getAbsolutePath();
        }

        // 查找具有入口点和图标的 EXE 文件
        File gameExe = findGameExecutable(gameDir, ".exe");
        if (gameExe != null) {
            AppLogger.info(TAG, "Found game EXE with entry point and icon: " + gameExe.getAbsolutePath());
            return gameExe.getAbsolutePath();
        }

        // 如果找不到符合条件的文件，降级为查找任意 DLL/EXE
        File dllFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".dll"));
        if (dllFile != null) {
            AppLogger.debug(TAG, "Found DLL file (no entry point check): " + dllFile.getAbsolutePath());
            return dllFile.getAbsolutePath();
        }

        File exeFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".exe"));
        if (exeFile != null) {
            AppLogger.debug(TAG, "Found EXE file (no entry point check): " + exeFile.getAbsolutePath());
            return exeFile.getAbsolutePath();
        }

        AppLogger.warn(TAG, "No executable file found in: " + gamePath);
        return null;
    }

    /**
     * 查找游戏可执行文件（必须同时具有入口点和图标）
     */
    private static File findGameExecutable(File dir, String extension) {
        return findFirstFileRecursively(dir, name -> {
            if (!name.endsWith(extension)) {
                return false;
            }

            File file = new File(dir, name);
            String filePath = file.getAbsolutePath();

            // 检查是否有图标（必须条件）
            boolean hasIcon = IconExtractor.hasIcon(filePath);
            if (!hasIcon) {
                return false;
            }

            // 检查是否有入口点（如果 runtime 可用）
            // ⚠️ 暂时禁用：AssemblyChecker 会初始化 CoreCLR，导致后续游戏启动失败
            // 因为 CoreCLR 不允许在同一进程中多次初始化
            // TODO: 未来可以通过检查文件是否在游戏启动流程中来决定是否调用
            boolean hasEntryPoint = false; // 假设所有有图标的文件都有入口点

            /*
            if (sContext != null && isRuntimeAvailable()) {
                try {
                    hasEntryPoint = AssemblyChecker.hasEntryPoint(sContext, filePath);
                } catch (Exception e) {
                    // 如果检查失败，记录日志但不影响结果（降级为只检查图标）
                    AppLogger.debug(TAG, "Failed to check entry point for " + name + ": " + e.getMessage());
                    hasEntryPoint = true; // 假设有入口点
                }
            } else {
                // runtime 不可用时，假设有入口点（只要有图标就认为是游戏）
                hasEntryPoint = true;
            }
            */

            if (hasEntryPoint && hasIcon) {
                AppLogger.debug(TAG, String.format("File %s has entry point: %b, has icon: %b",
                    name, hasEntryPoint, hasIcon));
                return true;
            }

            return false;
        });
    }

    /**
     * 检查 .NET runtime 是否可用
     */
    private static boolean isRuntimeAvailable() {
        if (sContext == null) {
            return false;
        }

        try {
            String dotnetPath = RuntimeManager.getDotnetPath(sContext);
            File dotnetExe = new File(dotnetPath, "dotnet");
            return dotnetExe.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 递归查找指定文件
     */
    public static File findFileRecursively(File dir, String targetName) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        // 先在当前目录查找
        for (File file : files) {
            if (file.isFile() && file.getName().equals(targetName)) {
                return file;
            }
        }

        // 递归查找子目录
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, targetName);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * 递归查找第一个匹配的文件
     */
    public static File findFirstFileRecursively(File dir, Predicate<String> predicate) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        // 先在当前目录查找
        for (File file : files) {
            if (file.isFile() && predicate.test(file.getName())) {
                return file;
            }
        }

        // 递归查找子目录
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFirstFileRecursively(file, predicate);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * 创建游戏目录
     */
    public static File createGameDirectory(File baseDir, String baseName) {
        File gameDir = new File(baseDir, baseName);
        int counter = 1;

        // 如果目录已存在,添加数字后缀
        while (gameDir.exists()) {
            gameDir = new File(baseDir, baseName + "_" + counter);
            counter++;
        }

        if (!gameDir.mkdirs()) {
            AppLogger.error(TAG, "Failed to create game directory: " + gameDir.getAbsolutePath());
            return null;
        }

        AppLogger.info(TAG, "Created game directory: " + gameDir.getAbsolutePath());
        return gameDir;
    }
}
