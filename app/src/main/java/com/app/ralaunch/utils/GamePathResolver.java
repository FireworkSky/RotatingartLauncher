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
     * 
     * 通过查找 xxxx.runtimeconfig.json 文件来确定启动程序集
     * 规则：如果有 xxxx.runtimeconfig.json，且有对应的 xxxx.dll/exe 和图标，则 xxxx.dll/exe 就是启动程序集
     * 
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

        // 通过 runtimeconfig.json 查找程序集
        File assemblyFile = findAssemblyByRuntimeConfig(gameDir);
        if (assemblyFile != null) {
            return assemblyFile.getAbsolutePath();
        }

        File dllFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".dll"));
        if (dllFile != null) {
            return dllFile.getAbsolutePath();
        }

        File exeFile = findFirstFileRecursively(gameDir, name -> name.endsWith(".exe"));
        if (exeFile != null) {
            return exeFile.getAbsolutePath();
        }

        return null;
    }

    /**
     * 通过 runtimeconfig.json 查找程序集
     * 
     * 查找 xxxx.runtimeconfig.json 文件，检查是否有对应的 xxxx.dll/exe 和图标
     * 
     * @param dir 要搜索的目录
     * @return 找到的程序集文件，如果没有找到则返回 null
     */
    private static File findAssemblyByRuntimeConfig(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        // 先搜索当前目录
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName();
            if (!fileName.endsWith(".runtimeconfig.json")) {
                continue;
            }

            // 提取基础名称（例如 "Stardew Valley.runtimeconfig.json" -> "Stardew Valley"）
            String baseName = fileName.substring(0, fileName.length() - ".runtimeconfig.json".length());
            
            File parentDir = file.getParentFile();
            
            // 检查是否有对应的 DLL 或 EXE（优先 DLL）
            File dllFile = new File(parentDir, baseName + ".dll");
            File exeFile = new File(parentDir, baseName + ".exe");
            
            File assemblyFile = null;
            if (dllFile.exists() && dllFile.isFile()) {
                assemblyFile = dllFile;
            } else if (exeFile.exists() && exeFile.isFile()) {
                assemblyFile = exeFile;
            }
            
            if (assemblyFile == null) {
                continue;
            }
            
            boolean hasIcon = IconExtractor.hasIcon(assemblyFile.getAbsolutePath());
            if (!hasIcon) {
                continue;
            }
            
            return assemblyFile;
        }

        // 递归搜索子目录
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findAssemblyByRuntimeConfig(file);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
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

        return gameDir;
    }
}
