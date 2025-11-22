package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.netcore.NetCoreManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 程序集检查器 - Java 包装类
 *
 * 便捷调用 AssemblyChecker.dll 来检测程序集信息
 */
public class AssemblyChecker {

    private static final String TAG = "AssemblyChecker";
    private static final String TOOL_DIR = "tools/AssemblyChecker";

    // 缓存上下文句柄，避免重复加载
    private static long contextHandle = 0;
    private static String loadedToolDir = null;

    /**
     * 检测结果
     */
    public static class CheckResult {
        @SerializedName("AssemblyPath")
        public String assemblyPath;

        @SerializedName("Exists")
        public boolean exists;

        @SerializedName("IsNetAssembly")
        public boolean isNetAssembly;

        @SerializedName("HasEntryPoint")
        public boolean hasEntryPoint;

        @SerializedName("EntryPointToken")
        public String entryPointToken;

        @SerializedName("EntryPointMethod")
        public String entryPointMethod;

        @SerializedName("AssemblyName")
        public String assemblyName;

        @SerializedName("AssemblyVersion")
        public String assemblyVersion;

        @SerializedName("HasIcon")
        public boolean hasIcon;

        @SerializedName("IconExtracted")
        public boolean iconExtracted;

        @SerializedName("IconPath")
        public String iconPath;

        @SerializedName("IconExtractionError")
        public String iconExtractionError;

        @SerializedName("Error")
        public String error;

        @Override
        public String toString() {
            return "CheckResult{" +
                    "assemblyPath='" + assemblyPath + '\'' +
                    ", exists=" + exists +
                    ", isNetAssembly=" + isNetAssembly +
                    ", hasEntryPoint=" + hasEntryPoint +
                    ", entryPointMethod='" + entryPointMethod + '\'' +
                    ", assemblyName='" + assemblyName + '\'' +
                    ", assemblyVersion='" + assemblyVersion + '\'' +
                    ", hasIcon=" + hasIcon +
                    ", error='" + error + '\'' +
                     '}';
        }
    }

    /**
     * 搜索目录中有入口点的程序集（C# 自动搜索所有 .dll 和 .exe）
     * C# 会自动搜索目录，找到第一个有入口点的程序集就返回
     *
     * @param context Android 上下文
     * @param directory 要搜索的目录路径
     * @return 检测结果（C# 找到的第一个有入口点的程序集）
     */
    public static CheckResult searchDirectoryForEntryPoint(Context context, String directory) {
        try {
            // 确保工具已提取
            File toolDir = ensureToolExtracted(context);
            if (toolDir == null) {
                CheckResult errorResult = new CheckResult();
                errorResult.error = "无法提取 AssemblyChecker 工具";
                return errorResult;
            }

            File checkerDll = new File(toolDir, "AssemblyChecker.dll");
            if (!checkerDll.exists()) {
                CheckResult errorResult = new CheckResult();
                errorResult.error = "AssemblyChecker.dll 不存在: " + checkerDll.getAbsolutePath();
                return errorResult;
            }

            AppLogger.info(TAG, "搜索目录中有入口点的程序集: " + directory);

            // 清除旧的 logcat 缓冲区并启动捕获
            StringBuilder output = new StringBuilder();
            Thread logcatThread = new Thread(() -> {
                try {
                    // 清除旧日志
                    Runtime.getRuntime().exec("logcat -c").waitFor();

                    // 启动 logcat 捕获 DOTNET 标签
                    Process process = Runtime.getRuntime().exec("logcat -v raw DOTNET:I *:S");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "Logcat 读取失败", e);
                }
            });
            logcatThread.start();

            // 等待 logcat 启动
            Thread.sleep(100);

            try {
                // 使用 NetCoreHostHelper 调用，传入目录路径
                // C# 会自动搜索目录中的所有 .dll 和 .exe 文件
                int exitCode = NetCoreHostHelper.runAssemblyForExitCode(
                        context,
                        toolDir.getAbsolutePath(),
                        "AssemblyChecker.dll",
                        new String[]{directory}
                );

                // 等待输出完全写入
                Thread.sleep(200);

                // 停止 logcat 捕获
                logcatThread.interrupt();

                // 读取 JSON 输出（最后一行）
                String[] lines = output.toString().split("\n");
                String jsonResult = null;

                // 从最后一行开始往前找 JSON（以 { 开头）
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (line.startsWith("{")) {
                        jsonResult = line;
                        break;
                    }
                }

                if (jsonResult == null) {
                    CheckResult errorResult = new CheckResult();
                    errorResult.error = "无法从输出中解析 JSON 结果";
                    return errorResult;
                }

                // 解析 JSON 结果
                Gson gson = new Gson();
                CheckResult result = gson.fromJson(jsonResult, CheckResult.class);

                if (result != null && result.hasEntryPoint) {
                    AppLogger.info(TAG, "✓ C# 找到有入口点的程序集: " + result.assemblyPath);
                } else {
                    AppLogger.warn(TAG, "✗ C# 未找到有入口点的程序集");
                }

                return result;

            } catch (InterruptedException e) {
                throw new Exception("程序执行被中断", e);
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "搜索目录失败", e);
            CheckResult errorResult = new CheckResult();
            errorResult.error = "搜索失败: " + e.getMessage();
            return errorResult;
        }
    }

    /**
     * 检查程序集是否有入口点
     *
     * @param context Android 上下文
     * @param assemblyPath 程序集路径
     * @return 检测结果
     */
    public static CheckResult checkAssembly(Context context, String assemblyPath) {
        return checkAssembly(context, assemblyPath, null);
    }

    /**
     * 检查程序集并可选提取图标
     *
     * @param context Android 上下文
     * @param assemblyPath 程序集路径
     * @param iconOutputPath 图标输出路径（null 表示不提取）
     * @return 检测结果
     */
    public static CheckResult checkAssembly(Context context, String assemblyPath, String iconOutputPath) {
        try {
            // 确保工具已提取
            File toolDir = ensureToolExtracted(context);
            if (toolDir == null) {
                CheckResult errorResult = new CheckResult();
                errorResult.error = "无法提取 AssemblyChecker 工具";
                return errorResult;
            }

            // 构建命令
            String dotnetRoot = RuntimeManager.getDotnetPath(context);
            File checkerDll = new File(toolDir, "AssemblyChecker.dll");

            if (!checkerDll.exists()) {
                CheckResult errorResult = new CheckResult();
                errorResult.error = "AssemblyChecker.dll 不存在: " + checkerDll.getAbsolutePath();
                return errorResult;
            }

            // 构建参数
            String[] args;
            if (iconOutputPath != null) {
                args = new String[]{
                        assemblyPath,
                        "--extract-icon",
                        iconOutputPath
                };
            } else {
                args = new String[]{assemblyPath};
            }

            // 清除旧的 logcat 缓冲区并启动捕获
            StringBuilder output = new StringBuilder();
            Thread logcatThread = new Thread(() -> {
                try {
                    // 清除旧日志
                    Runtime.getRuntime().exec("logcat -c").waitFor();

                    // 启动 logcat 捕获 DOTNET 标签
                    Process process = Runtime.getRuntime().exec("logcat -v raw DOTNET:I *:S");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "Logcat 读取失败", e);
                }
            });
            logcatThread.start();

            // 等待 logcat 启动
            Thread.sleep(100);

            try {
                // 使用 NetCoreHostHelper 调用（不清理运行时，可以连续检测多个程序集）
                int exitCode = NetCoreHostHelper.runAssemblyForExitCode(
                        context,
                        toolDir.getAbsolutePath(),
                        "AssemblyChecker.dll",
                        args
                );

                // 等待输出完全写入
                Thread.sleep(200);

                // 停止 logcat 捕获
                logcatThread.interrupt();

                // 读取 JSON 输出（最后一行）
                String[] lines = output.toString().split("\n");
                String jsonResult = null;

                // 从最后一行开始往前找 JSON（以 { 开头）
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (line.startsWith("{")) {
                        jsonResult = line;
                        break;
                    }
                }

                if (jsonResult == null) {
                    CheckResult errorResult = new CheckResult();
                    errorResult.error = "无法从输出中解析 JSON 结果";
                    return errorResult;
                }

                // 解析 JSON 结果
                Gson gson = new Gson();
                CheckResult result = gson.fromJson(jsonResult, CheckResult.class);

                AppLogger.debug(TAG, "检查结果: " + result);
                return result;

            } catch (InterruptedException e) {
                throw new Exception("程序执行被中断", e);
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "检查程序集失败", e);
            CheckResult errorResult = new CheckResult();
            errorResult.error = "检查失败: " + e.getMessage();
            return errorResult;
        }
    }

    /**
     * 确保工具已提取到内部存储
     *
     * @param context Android 上下文
     * @return 工具目录，失败返回 null
     */
    private static File ensureToolExtracted(Context context) {
        try {
            File internalToolsDir = new File(context.getFilesDir(), "tools");
            File checkerDir = new File(internalToolsDir, "AssemblyChecker");

            // 检查是否已提取
            File checkerDll = new File(checkerDir, "AssemblyChecker.dll");
            if (checkerDll.exists()) {
                return checkerDir;
            }

            // 创建目录
            if (!checkerDir.exists()) {
                checkerDir.mkdirs();
            }

            // 提取所有文件
            String[] files = context.getAssets().list(TOOL_DIR);
            if (files == null || files.length == 0) {
                AppLogger.error(TAG, "assets 中没有找到 AssemblyChecker 文件");
                return null;
            }

            for (String fileName : files) {
                String assetPath = TOOL_DIR + "/" + fileName;
                File outputFile = new File(checkerDir, fileName);

                try (InputStream in = context.getAssets().open(assetPath);
                     FileOutputStream out = new FileOutputStream(outputFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }

                    AppLogger.debug(TAG, "已提取: " + fileName);
                }
            }

            AppLogger.info(TAG, "AssemblyChecker 工具已提取到: " + checkerDir.getAbsolutePath());
            return checkerDir;

        } catch (IOException e) {
            AppLogger.error(TAG, "提取工具失败", e);
            return null;
        }
    }

    /**
     * 快速检查程序集是否有入口点
     *
     * @param context Android 上下文
     * @param assemblyPath 程序集路径
     * @return true 表示有入口点，false 表示没有或检查失败
     */
    public static boolean hasEntryPoint(Context context, String assemblyPath) {
        CheckResult result = checkAssembly(context, assemblyPath);
        return result != null && result.hasEntryPoint;
    }
}
