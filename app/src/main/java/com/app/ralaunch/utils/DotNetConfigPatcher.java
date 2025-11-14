package com.app.ralaunch.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * .NET 运行时配置文件修补工具
 * 负责修补 runtimeconfig.json 和 deps.json 以实现版本兼容性和平台适配
 */
public class DotNetConfigPatcher {
    private static final String TAG = "DotNetConfigPatcher";

    /**
     * 修补 .NET 应用的配置文件以实现跨版本和跨平台运行
     * 
     * @param runtimeConfig runtimeconfig.json 文件
     * @param context Android Context
     * @return 是否成功修补
     */
    public static boolean patchConfigs(File runtimeConfig, Context context) {
        if (runtimeConfig == null || !runtimeConfig.exists()) {
            Log.w(TAG, "runtimeconfig.json 文件不存在,跳过修补");
            return false;
        }

        try {
            // 步骤 1: 修补 runtimeconfig.json
            if (!patchRuntimeConfig(runtimeConfig, context)) {
                Log.w(TAG, "runtimeconfig.json 修补失败");
                return false;
            }

            // 步骤 2: 处理 deps.json
            // 对于自包含应用的 deps.json,重命名它以强制使用框架依赖模式
            handleDepsJson(runtimeConfig, context);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "配置文件修补过程出现异常", e);
            return false;
        }
    }

    /**
     * 修补 runtimeconfig.json 文件
     * - 检测应用要求的 .NET 版本
     * - 与已安装版本比对
     * - 必要时修改版本号和框架依赖声明
     * 
     * @param runtimeConfig runtimeconfig.json 文件
     * @param context Android Context
     * @return 是否成功修补
     */
    private static boolean patchRuntimeConfig(File runtimeConfig, Context context) {
        try {
            // 读取 runtimeconfig.json
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(runtimeConfig))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            String originalContent = content.toString();

            // 检测应用要求的 .NET 版本
            String requiredVersion = extractVersion(originalContent);
            if (requiredVersion == null) {
                Log.w(TAG, "  [WARN]  无法检测应用要求的 .NET 版本");
                return false;
            }

            Log.i(TAG, "  🔍 检测到应用要求 .NET 版本: " + requiredVersion);

            // 获取已安装的 .NET 版本
            String installedVersion = RuntimeManager.getSelectedVersion(context);
            if (installedVersion == null || installedVersion.isEmpty()) {
                Log.e(TAG, "  [ERROR] 无法获取已安装的 .NET 版本");
                return false;
            }

            String requiredMajor = requiredVersion.split("\\.")[0];
            String installedMajor = installedVersion.split("\\.")[0];

            // 检查是否有框架依赖声明
            boolean hasFramework = originalContent.contains("includedFrameworks") || 
                                 originalContent.contains("\"framework\"");
            
            // 检查是否有 rollForward 设置
            boolean hasRollForward = originalContent.contains("\"rollForward\"");

            boolean needsPatch = false;
            String modifiedContent = originalContent;

            // 情况1: 版本不匹配（主版本或完整版本）
            if (!requiredVersion.equals(installedVersion)) {
                if (!requiredMajor.equals(installedMajor)) {
                    // 主版本不同：强制替换
                    Log.w(TAG, "  [WARN]  主版本不匹配! 应用要求 .NET " + requiredMajor + 
                            ".x, 但设备只有 .NET " + installedMajor + ".x");
                    Log.i(TAG, "  🔧 强制兼容: 将配置修改为 .NET " + installedVersion);

                    // 替换版本号
                    modifiedContent = modifiedContent.replaceAll(
                            "\"version\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(requiredVersion) + "\"",
                            "\"version\": \"" + installedVersion + "\"");

                    // 替换 tfm (如果存在)
                    modifiedContent = modifiedContent.replaceAll(
                            "\"tfm\"\\s*:\\s*\"net" + requiredMajor + "\\.0\"",
                            "\"tfm\": \"net" + installedMajor + ".0\"");

                    needsPatch = true;
                } else {
                    // 主版本相同但次版本不同：依赖 rollForward
                    Log.i(TAG, "  💡 次版本不同: 应用要求 " + requiredVersion + ", 设备有 " + installedVersion);
                    Log.i(TAG, "  🔧 将依赖 rollForward 策略来兼容运行");
                    // 不修改版本号，让 rollForward 处理
                }
            }

            // 情况2: 缺少框架依赖声明(会被当成自包含应用)
            if (!hasFramework) {
                Log.w(TAG, "  [WARN]  配置文件缺少框架依赖声明,应用会被当成自包含应用!");
                Log.i(TAG, "  🔧 添加框架依赖声明...");

                // 在 runtimeOptions 中添加 includedFrameworks 和 rollForward
                if (modifiedContent.contains("\"runtimeOptions\"")) {
                    modifiedContent = modifiedContent.replaceFirst(
                            "(\"runtimeOptions\"\\s*:\\s*\\{)",
                            "$1\n    \"rollForward\": \"LatestMinor\",\n    \"includedFrameworks\": [\n      {\n        \"name\": \"Microsoft.NETCore.App\",\n        \"version\": \"" + installedVersion + "\"\n      }\n    ],");
                } else {
                    Log.e(TAG, "  [ERROR] 无法找到 runtimeOptions 节点");
                    return false;
                }

                needsPatch = true;
            }
            
            // 情况3: 缺少 rollForward 设置(可能导致运行时错误)
            if (hasFramework && !hasRollForward) {
                Log.w(TAG, "  [WARN]  配置文件缺少 rollForward 设置!");
                Log.i(TAG, "  🔧 添加 rollForward: LatestMinor...");
                
                // 在 runtimeOptions 后添加 rollForward
                modifiedContent = modifiedContent.replaceFirst(
                        "(\"runtimeOptions\"\\s*:\\s*\\{)",
                        "$1\n    \"rollForward\": \"LatestMinor\",");
                
                needsPatch = true;
            }

            if (needsPatch) {
                // 备份原始文件
                File backup = new File(runtimeConfig.getAbsolutePath() + ".backup");
                if (!backup.exists()) {
                    try (java.io.FileWriter writer = new java.io.FileWriter(backup)) {
                        writer.write(originalContent);
                    }
                    Log.i(TAG, "  💾 已备份原始配置到: " + backup.getName());
                }

                // 写入修改后的文件
                try (java.io.FileWriter writer = new java.io.FileWriter(runtimeConfig)) {
                    writer.write(modifiedContent);
                }

                Log.i(TAG, "  [OK] runtimeconfig.json 已修补为框架依赖应用 (.NET " + installedVersion + ")");
            } else {
                Log.i(TAG, "  [OK] runtimeconfig.json 配置正常");
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "  [ERROR] 修补 runtimeconfig.json 失败", e);
            return false;
        }
    }

    /**
     * 处理 deps.json 文件
     * 如果 deps.json 是为自包含应用设计的(缺少正确的 RID),则重命名它
     * 让 hostfxr 只依赖 runtimeconfig.json 来解析框架依赖
     * 
     * @param runtimeConfig runtimeconfig.json 文件(用于定位 deps.json)
     * @param context Android Context
     */
    private static void handleDepsJson(File runtimeConfig, Context context) {
        try {
            // 构造 deps.json 文件路径
            String depsJsonPath = runtimeConfig.getAbsolutePath().replace(".runtimeconfig.json", ".deps.json");
            File depsJson = new File(depsJsonPath);

            if (!depsJson.exists()) {
                Log.i(TAG, "  ℹ️  未找到 deps.json");
                return;
            }

            Log.i(TAG, "  🔧 检查 deps.json...");

            // 读取 deps.json 的开头部分检查 RID
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(depsJson))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 50) {
                    content.append(line).append("\n");
                    lineCount++;
                }
            }

            String snippet = content.toString();
            
            // 检查是否包含错误的 RID (linux-x64)
            boolean hasWrongRid = snippet.contains("linux-x64") && !snippet.contains("linux-bionic-arm64");
            
            if (hasWrongRid) {
                Log.w(TAG, "  [WARN]  deps.json 包含不兼容的 RID (linux-x64)");
                Log.i(TAG, "  🔧 重命名 deps.json → deps.json.disabled");
                Log.i(TAG, "  💡 将使用 runtimeconfig.json 的框架依赖配置");
                
                // 重命名 deps.json 为 .disabled
                File disabledDepsJson = new File(depsJson.getAbsolutePath() + ".disabled");
                
                // 如果已存在 .disabled 文件,先删除
                if (disabledDepsJson.exists()) {
                    disabledDepsJson.delete();
                }
                
                if (depsJson.renameTo(disabledDepsJson)) {
                    Log.i(TAG, "  [OK] deps.json 已禁用,将使用框架依赖模式");
                } else {
                    Log.e(TAG, "  [ERROR] 无法重命名 deps.json");
                }
            } else {
                Log.i(TAG, "  [OK] deps.json RID 正确或已修补");
            }

        } catch (Exception e) {
            Log.e(TAG, "  [ERROR] 处理 deps.json 失败", e);
        }
    }

    /**
     * 修补 deps.json 文件 (已废弃,使用 handleDepsJson 替代)
     * - 修改运行时版本号 (如 6.0 -> 7.0)
     * - 修改 RID 为 Android 兼容的 linux-bionic-arm64
     * - 更新程序集版本号
     * 
     * @param runtimeConfig runtimeconfig.json 文件(用于定位 deps.json)
     * @param context Android Context
     */
    private static void patchDepsJson(File runtimeConfig, Context context) {
        try {
            // 构造 deps.json 文件路径
            String depsJsonPath = runtimeConfig.getAbsolutePath().replace(".runtimeconfig.json", ".deps.json");
            File depsJson = new File(depsJsonPath);

            if (!depsJson.exists()) {
                Log.i(TAG, "  ℹ️  未找到 deps.json，跳过修补");
                return;
            }

            Log.i(TAG, "  🔧 修补 deps.json 运行时版本...");

            // 读取 deps.json
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(depsJson))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            String originalContent = content.toString();
            String modifiedContent = originalContent;

            // 获取已安装的 .NET 版本
            String installedVersion = RuntimeManager.getSelectedVersion(context);
            String installedMajor = installedVersion.split("\\.")[0];

            // 🔍 从 deps.json 本身检测实际的运行时版本号和 RID
            String actualMajor = null;
            String actualRid = null;
            java.util.regex.Pattern runtimeTargetPattern = java.util.regex.Pattern.compile(
                    "\\.NETCoreApp,Version=v(\\d+)\\.0(?:/([-\\w]+))?");
            java.util.regex.Matcher matcher = runtimeTargetPattern.matcher(originalContent);
            if (matcher.find()) {
                actualMajor = matcher.group(1);
                actualRid = matcher.group(2) != null ? matcher.group(2) : "unspecified";
                Log.i(TAG, "  📋 deps.json 当前配置: .NET " + actualMajor + ".x, RID=" + actualRid);
            } else {
                Log.w(TAG, "  [WARN]  无法从 deps.json 检测运行时版本,跳过修补");
                return;
            }

            // 检查是否需要修补 (版本或 RID 不匹配)
            boolean versionMatches = actualMajor.equals(installedMajor);
            boolean ridMatches = "linux-bionic-arm64".equals(actualRid);

            if (versionMatches && ridMatches) {
                Log.i(TAG, "  [OK] deps.json 版本和 RID 均已正确,无需修补");
                return;
            }

            if (!versionMatches) {
                Log.i(TAG, "  [WARN]  版本不匹配: " + actualMajor + ".x → " + installedMajor + ".x");
            }
            if (!ridMatches) {
                Log.i(TAG, "  [WARN]  RID不匹配: " + actualRid + " → linux-bionic-arm64 (Android平台)");
            }

            Log.i(TAG, "  🔧 开始修补 deps.json...");

            // 1. 修改 runtimeTarget 名称和 RID
            modifiedContent = modifiedContent.replaceAll(
                    "\\.NETCoreApp,Version=v" + actualMajor + "\\.0/linux-x64",
                    ".NETCoreApp,Version=v" + installedMajor + ".0/linux-bionic-arm64");

            // 处理 runtimeTarget 中没有 RID 的情况
            modifiedContent = modifiedContent.replaceAll(
                    "\"runtimeTarget\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"\\.NETCoreApp,Version=v" + actualMajor + "\\.0\"",
                    "\"runtimeTarget\": { \"name\": \".NETCoreApp,Version=v" + installedMajor + ".0/linux-bionic-arm64\"");

            // 1.5 修改 targets 中的 key
            modifiedContent = modifiedContent.replaceAll(
                    "\"\\.NETCoreApp,Version=v" + actualMajor + "\\.0/linux-x64\"\\s*:",
                    "\".NETCoreApp,Version=v" + installedMajor + ".0/linux-bionic-arm64\":");

            modifiedContent = modifiedContent.replaceAll(
                    "\"\\.NETCoreApp,Version=v" + actualMajor + "\\.0\"\\s*:",
                    "\".NETCoreApp,Version=v" + installedMajor + ".0\":");

            // 2. 修改 runtimepack 版本号和 RID
            modifiedContent = modifiedContent.replaceAll(
                    "runtimepack\\.Microsoft\\.NETCore\\.App\\.Runtime\\.linux-x64/" + actualMajor + "\\.[0-9]+\\.[0-9]+",
                    "runtimepack.Microsoft.NETCore.App.Runtime.linux-bionic-arm64/" + installedVersion);

            // 3. 修改依赖中的 runtimepack 版本和 RID
            modifiedContent = modifiedContent.replaceAll(
                    "\"runtimepack\\.Microsoft\\.NETCore\\.App\\.Runtime\\.linux-x64\"\\s*:\\s*\"" + actualMajor + "\\.[0-9]+\\.[0-9]+\"",
                    "\"runtimepack.Microsoft.NETCore.App.Runtime.linux-bionic-arm64\": \"" + installedVersion + "\"");

            // 4. 修改程序集版本号 (assemblyVersion)
            modifiedContent = modifiedContent.replaceAll(
                    "\"assemblyVersion\"\\s*:\\s*\"" + actualMajor + "\\.0\\.0\\.0\"",
                    "\"assemblyVersion\": \"" + installedMajor + ".0.0.0\"");

            // 检查修补是否生效
            if (modifiedContent.equals(originalContent)) {
                Log.w(TAG, "  [WARN]  deps.json 修补后内容未改变,可能正则表达式未匹配");
                return;
            }

            // 备份原始 deps.json
            File backup = new File(depsJson.getAbsolutePath() + ".backup");
            if (!backup.exists()) {
                try (java.io.FileWriter writer = new java.io.FileWriter(backup)) {
                    writer.write(originalContent);
                }
                Log.i(TAG, "  💾 已备份原始 deps.json");
            }

            // 写入修改后的 deps.json
            try (java.io.FileWriter writer = new java.io.FileWriter(depsJson)) {
                writer.write(modifiedContent);
            }

            Log.i(TAG, "  [OK] deps.json 已成功修补为 .NET " + installedVersion + " (RID: linux-bionic-arm64)");

        } catch (Exception e) {
            Log.e(TAG, "  [ERROR] 修补 deps.json 失败", e);
            // deps.json 修补失败不影响主流程,只记录错误
        }
    }

    /**
     * 从 runtimeconfig.json 内容中提取 .NET 版本号
     * 
     * @param content runtimeconfig.json 内容
     * @return 版本号,如 "7.0.0",失败返回 null
     */
    private static String extractVersion(String content) {
        // 尝试匹配 "version": "7.0.0"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"version\"\\s*:\\s*\"([0-9]+\\.[0-9]+\\.[0-9]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 尝试匹配 tfm: "net7.0" -> 假设为 7.0.0
        pattern = java.util.regex.Pattern.compile("\"tfm\"\\s*:\\s*\"net([0-9]+)\\.0\"");
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1) + ".0.0";
        }

        return null;
    }
}

