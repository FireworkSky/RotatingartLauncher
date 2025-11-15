package com.app.ralaunch.utils;

import java.io.File;
import java.util.function.Predicate;

/**
 * 游戏路径解析器
 *
 * 负责在游戏目录中查找和解析游戏可执行文件路径
 */
public class GamePathResolver {
    private static final String TAG = "GamePathResolver";

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

        // 查找 .dll 文件
        File dllFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".dll"));
        if (dllFile != null) {
            AppLogger.debug(TAG, "Found DLL file: " + dllFile.getAbsolutePath());
            return dllFile.getAbsolutePath();
        }

        // 查找 .exe 文件
        File exeFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".exe"));
        if (exeFile != null) {
            AppLogger.debug(TAG, "Found EXE file: " + exeFile.getAbsolutePath());
            return exeFile.getAbsolutePath();
        }

        AppLogger.warn(TAG, "No executable file found in: " + gamePath);
        return null;
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
