package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralib.icon.IconExtractor;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 程序集检查器 - 通过 runtimeconfig.json 查找程序集
 *
 * 通过查找 xxxx.runtimeconfig.json 文件来确定启动程序集
 * 
 * 优先级规则（从高到低）：
 * 1. 有 xx.runtimeconfig.json + xx.dll + xx（无扩展名可执行文件/apphost）- 表示已正确安装的跨平台应用
 * 2. 有 xx.runtimeconfig.json + xx.dll + 图标 - 有图标的程序集
 * 3. 有 xx.runtimeconfig.json + xx.dll - 基本的 .NET 程序集
 * 
 * 排除规则：
 * - 目录或其子目录包含自带运行时（libcoreclr.so/libhostpolicy.so）的会被排除
 * - 这些通常是安装程序自带的运行时，应使用启动器安装的运行时
 */
public class AssemblyChecker {

    private static final String TAG = "AssemblyChecker";

    /**
     * 检测结果
     */
    public static class CheckResult {
        public String assemblyPath;
        public boolean exists;
        public boolean hasIcon;
        public boolean hasExecutable; // 是否有对应的无扩展名可执行文件（apphost）
        public String assemblyName;
        public String error;
        public int priority; // 优先级分数（越高越好）

        @Override
        public String toString() {
            return "CheckResult{" +
                    "assemblyPath='" + assemblyPath + '\'' +
                    ", exists=" + exists +
                    ", hasIcon=" + hasIcon +
                    ", hasExecutable=" + hasExecutable +
                    ", assemblyName='" + assemblyName + '\'' +
                    ", priority=" + priority +
                    ", error='" + error + '\'' +
                     '}';
        }
    }

    /**
     * 搜索目录中符合规则的程序集（通过 runtimeconfig.json）
     * 
     * 递归遍历目录及其子目录，查找 xxxx.runtimeconfig.json 文件
     * 按优先级选择最合适的程序集：
     * 1. 优先级最高：有 xx.dll + xx（无扩展名可执行文件）- 表示已正确安装的跨平台应用
     * 2. 优先级次高：有 xx.dll + 图标
     * 3. 优先级最低：只有 xx.dll
     *
     * @param context Android 上下文（未使用，保留以兼容现有调用）
     * @param directory 要搜索的目录路径
     * @return 检测结果（优先级最高的程序集），如果没有找到则返回 null
     */
    public static CheckResult searchDirectoryForAssemblyWithIcon(Context context, String directory) {
        AppLogger.info(TAG, "搜索目录中符合规则的程序集（通过 runtimeconfig.json）: " + directory);
        
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            CheckResult errorResult = new CheckResult();
            errorResult.error = "目录不存在或不是目录: " + directory;
            return errorResult;
        }

        // 收集所有候选程序集
        List<CheckResult> candidates = new ArrayList<>();
        collectCandidatesRecursive(dir, candidates);
        
        if (candidates.isEmpty()) {
            AppLogger.warn(TAG, "✗ 未找到符合规则的程序集（已搜索所有子目录）");
            CheckResult noResult = new CheckResult();
            noResult.error = "目录中没有找到符合规则的程序集（需要有 runtimeconfig.json 和对应的 DLL/EXE）";
            return noResult;
        }

        // 按优先级排序（priority 越高越好）
        candidates.sort(Comparator.comparingInt((CheckResult r) -> r.priority).reversed());
        
        // 记录所有候选项
        AppLogger.info(TAG, "找到 " + candidates.size() + " 个候选程序集:");
        for (int i = 0; i < candidates.size(); i++) {
            CheckResult c = candidates.get(i);
            String flags = "";
            if (c.hasExecutable) flags += "[有可执行文件] ";
            if (c.hasIcon) flags += "[有图标] ";
            AppLogger.info(TAG, "  " + (i + 1) + ". " + c.assemblyName + " (优先级: " + c.priority + ") " + flags);
        }

        CheckResult best = candidates.get(0);
        AppLogger.info(TAG, "✓ 选择优先级最高的程序集: " + best.assemblyPath);
        return best;
    }

    /**
     * 递归收集目录中所有符合条件的程序集候选项
     *
     * @param dir 要搜索的目录
     * @param candidates 收集候选项的列表
     */
    private static void collectCandidatesRecursive(File dir, List<CheckResult> candidates) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        AppLogger.debug(TAG, "搜索目录: " + dir.getAbsolutePath() + " (文件数: " + files.length + ")");

        // 搜索当前目录中的 .runtimeconfig.json 文件
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName();
            if (!fileName.endsWith(".runtimeconfig.json")) {
                continue;
            }

            AppLogger.debug(TAG, "找到 runtimeconfig.json: " + file.getAbsolutePath());
            
            String baseName = fileName.substring(0, fileName.length() - ".runtimeconfig.json".length());
            
            // 检查是否有对应的 DLL 或 EXE（在同一目录中）
            File dllFile = new File(dir, baseName + ".dll");
            File exeFile = new File(dir, baseName + ".exe");
            
            File assemblyFile = null;
            if (dllFile.exists() && dllFile.isFile()) {
                assemblyFile = dllFile;
                AppLogger.debug(TAG, "找到对应的 DLL: " + dllFile.getAbsolutePath());
            } else if (exeFile.exists() && exeFile.isFile()) {
                assemblyFile = exeFile;
                AppLogger.debug(TAG, "找到对应的 EXE: " + exeFile.getAbsolutePath());
            }
            
            if (assemblyFile == null) {
                AppLogger.debug(TAG, "找到 runtimeconfig.json 但没有对应的 DLL/EXE: " + fileName);
                continue;
            }
            
            // 检查是否有无扩展名的可执行文件（apphost）
            // 这表示是已正确安装的跨平台应用
            File executableFile = new File(dir, baseName);
            boolean hasExecutable = executableFile.exists() && executableFile.isFile() && executableFile.canExecute();
            
            // 如果不能执行，也检查文件是否存在（Android 可能没有执行权限）
            if (!hasExecutable) {
                hasExecutable = executableFile.exists() && executableFile.isFile() && executableFile.length() > 0;
            }
            
            // 检查是否有图标
            boolean hasIcon = IconExtractor.hasIcon(assemblyFile.getAbsolutePath());
            
            // 计算优先级分数
            // 优先级 1: 有可执行文件（apphost）- 最重要，+100 分
            // 优先级 2: 有图标 - +10 分
            // 优先级 3: 基本分数 - 1 分
            int priority = 1;
            if (hasExecutable) {
                priority += 100;
            }
            if (hasIcon) {
                priority += 10;
            }
            
            // 创建候选结果
            CheckResult result = new CheckResult();
            result.assemblyPath = assemblyFile.getAbsolutePath();
            result.exists = true;
            result.hasIcon = hasIcon;
            result.hasExecutable = hasExecutable;
            result.assemblyName = baseName;
            result.priority = priority;
            result.error = null;
            
            String flags = "";
            if (hasExecutable) flags += "[有可执行文件] ";
            if (hasIcon) flags += "[有图标] ";
            AppLogger.debug(TAG, "添加候选程序集: " + baseName + " (优先级: " + priority + ") " + flags);
            
            candidates.add(result);
        }

        // 递归搜索所有子目录
        for (File file : files) {
            if (file.isDirectory()) {
                collectCandidatesRecursive(file, candidates);
            }
        }
    }




}
