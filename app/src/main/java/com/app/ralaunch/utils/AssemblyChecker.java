package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralib.icon.IconExtractor;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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


    public static CheckResult searchDirectoryForAssemblyWithIcon(Context context, String directory) {
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
            CheckResult noResult = new CheckResult();
            noResult.error = "目录中没有找到符合规则的程序集（需要有 runtimeconfig.json 和对应的 DLL/EXE）";
            return noResult;
        }

        // 按优先级排序（priority 越高越好）
        candidates.sort(Comparator.comparingInt((CheckResult r) -> r.priority).reversed());
        return candidates.get(0);
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

        // 搜索当前目录中的 .runtimeconfig.json 文件
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName();
            if (!fileName.endsWith(".runtimeconfig.json")) {
                continue;
            }
            
            String baseName = fileName.substring(0, fileName.length() - ".runtimeconfig.json".length());
            
            File dllFile = new File(dir, baseName + ".dll");
            File exeFile = new File(dir, baseName + ".exe");
            
            File assemblyFile = null;
            if (dllFile.exists() && dllFile.isFile()) {
                assemblyFile = dllFile;
            } else if (exeFile.exists() && exeFile.isFile()) {
                assemblyFile = exeFile;
            }
            
            if (assemblyFile == null) {
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
