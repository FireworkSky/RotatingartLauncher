package com.app.ralaunch.core;

import android.content.Context;

import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.Shared;
import com.app.ralib.extractors.BasicSevenZipExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.utils.TemporaryFileAcquirer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 程序集补丁工具
 * 
 * 从已解压的 MonoMod 目录加载补丁程序集，并替换游戏目录中的对应文件
 * 统一使用 MonoMod.zip 作为 MonoMod 库源
 * 
 * @author RA Launcher Team
 */
public class AssemblyPatcher {
    private static final String TAG = "AssemblyPatcher";
    
    // MonoMod 目录名
    public static final String MONOMOD_DIR = "monomod";
    
    // Assets 中的压缩包名
    private static final String ASSETS_MONOMOD_ZIP = "MonoMod.zip";

    /**
     * 获取 MonoMod 安装目录路径
     * 路径为: /storage/emulated/0/Android/data/com.app.ralaunch/files/monomod
     * 
     * @return MonoMod 安装目录路径
     */
    public static Path getMonoModInstallPath() {
        Context context = Shared.getContext();
        File externalFilesDir = context.getExternalFilesDir(null);
        return Paths.get(externalFilesDir.getAbsolutePath(), MONOMOD_DIR);
    }
    
    /**
     * 解压 MonoMod 到目录
     * 会覆盖已存在的文件
     * 
     * @param context Android上下文
     * @return 是否成功
     */
    public static boolean extractMonoMod(Context context) {
        Path targetDir = getMonoModInstallPath();
        
        AppLogger.info(TAG, "正在解压 MonoMod 到 " + targetDir);
        
        try (TemporaryFileAcquirer tfa = new TemporaryFileAcquirer()) {
            // 确保目标目录存在
            Files.createDirectories(targetDir);
            
            // 将 assets 中的 zip 复制到临时文件
            Path tempZip = tfa.acquireTempFilePath("monomod.zip");
            
            try (InputStream is = context.getAssets().open(ASSETS_MONOMOD_ZIP)) {
                Files.copy(is, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 解压到目标目录（覆盖已存在的文件）
            new BasicSevenZipExtractor(
                tempZip,
                Paths.get(""),  // 从根目录解压
                targetDir,
                new ExtractorCollection.ExtractionListener() {
                    @Override
                    public void onProgress(String message, float progress, HashMap<String, Object> state) {
                        AppLogger.debug(TAG, "解压中: " + message + " (" + (int)(progress * 100) + "%)");
                    }

                    @Override
                    public void onComplete(String message, HashMap<String, Object> state) {
                        AppLogger.info(TAG, "MonoMod 解压完成");
                    }

                    @Override
                    public void onError(String message, Exception ex, HashMap<String, Object> state) {
                        AppLogger.error(TAG, "解压错误: " + message, ex);
                    }
                }
            ).extract();
            
            AppLogger.info(TAG, "MonoMod 已解压到 " + targetDir);
            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "解压 MonoMod 失败", e);
            return false;
        }
    }

    /**
     * 应用 MonoMod 补丁到游戏目录
     *
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @return 替换的程序集数量，失败返回 -1
     */
    public static int applyMonoModPatches(Context context, String gameDirectory) {
        return applyMonoModPatches(context, gameDirectory, true);
    }

    /**
     * 应用 MonoMod 补丁到游戏目录（可控制日志级别）
     *
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @param verboseLog 是否输出详细日志
     * @return 替换的程序集数量，失败返回 -1
     */
    public static int applyMonoModPatches(Context context, String gameDirectory, boolean verboseLog) {
        try {
            Map<String, byte[]> patchAssemblies = loadPatchArchive(context);
            if (patchAssemblies.isEmpty()) {
                if (verboseLog) {
                    AppLogger.warn(TAG, "MonoMod 目录为空或不存在");
                }
                return 0;
            }

            // 扫描游戏目录中的程序集
            File gameDir = new File(gameDirectory);
            List<File> gameAssemblies = findGameAssemblies(gameDir);

            int patchedCount = 0;
            for (File assemblyFile : gameAssemblies) {
                String assemblyName = assemblyFile.getName();

                if (patchAssemblies.containsKey(assemblyName)) {
                    if (replaceAssembly(assemblyFile, patchAssemblies.get(assemblyName))) {
                        if (verboseLog) {
                            AppLogger.debug(TAG, "已替换: " + assemblyName);
                        }
                        patchedCount++;
                    }
                }
            }
            
            if (verboseLog) {
                AppLogger.info(TAG, "已应用 MonoMod 补丁，替换了 " + patchedCount + " 个文件");
            }
            return patchedCount;

        } catch (Exception e) {
            AppLogger.error(TAG, "应用补丁失败", e);
            return -1;
        }
    }

    /**
     * 从 MonoMod 目录加载补丁程序集
     * 
     * @param context Android上下文
     * @return 程序集名称到字节数组的映射
     */
    private static Map<String, byte[]> loadPatchArchive(Context context) {
        Map<String, byte[]> assemblies = new HashMap<>();

        try {
            Path monoModPath = getMonoModInstallPath();
            File monoModDir = monoModPath.toFile();

            if (!monoModDir.exists() || !monoModDir.isDirectory()) {
                AppLogger.warn(TAG, "MonoMod 目录不存在: " + monoModPath);
                return assemblies;
            }

            List<File> dllFiles = findDllFiles(monoModDir);
            AppLogger.debug(TAG, "从 " + monoModPath + " 找到 " + dllFiles.size() + " 个 DLL 文件");

            for (File dllFile : dllFiles) {
                try {
                    byte[] assemblyData = Files.readAllBytes(dllFile.toPath());
                    String fileName = dllFile.getName();
                    assemblies.put(fileName, assemblyData);
                } catch (IOException e) {
                    AppLogger.warn(TAG, "读取 DLL 失败: " + dllFile.getName(), e);
                }
            }

        } catch (Exception e) {
            AppLogger.error(TAG, "加载 MonoMod 补丁失败", e);
        }

        return assemblies;
    }

    /**
     * 递归查找目录中的所有 .dll 文件
     */
    private static List<File> findDllFiles(File directory) {
        List<File> dllFiles = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    dllFiles.addAll(findDllFiles(file));
                } else if (file.getName().endsWith(".dll")) {
                    dllFiles.add(file);
                }
            }
        }

        return dllFiles;
    }

    /**
     * 扫描游戏目录，查找所有 .dll 程序集
     */
    private static List<File> findGameAssemblies(File directory) {
        List<File> assemblies = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return assemblies;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return assemblies;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                assemblies.addAll(findGameAssemblies(file));
            } else if (file.getName().endsWith(".dll")) {
                assemblies.add(file);
            }
        }

        return assemblies;
    }

    /**
     * 替换程序集文件
     */
    private static boolean replaceAssembly(File targetFile, byte[] assemblyData) {
        try {
            FileOutputStream outputStream = new FileOutputStream(targetFile);
            outputStream.write(assemblyData);
            outputStream.close();
            return true;
        } catch (IOException e) {
            AppLogger.error(TAG, "替换失败: " + targetFile.getName(), e);
            return false;
        }
    }
}
