package com.app.ralaunch.game;

import android.content.Context;
import android.content.res.AssetManager;

import com.app.ralaunch.model.PatchInfo;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 程序集补丁工具
 *
 * <p>此类负责从 MonoMod_Patch.zip 中提取补丁程序集，
 * 并替换游戏目录中的对应程序集文件
 * 支持通过JSON配置动态启用/禁用补丁
 *
 * @author RA Launcher Team
 */
public class AssemblyPatcher {
    private static final String TAG = "AssemblyPatcher";
    private static final String PATCH_ARCHIVE = "MonoMod_Patch.zip";

    // [WARN] 强制更新版本号：每次修改 MonoMod 后增加此版本号
    // 这会强制删除所有旧的补丁程序集并重新安装
    private static final int PATCH_VERSION = 3; // ← 更新 MonoMod 后增加这个数字（跳过 Mono.Cecil）
    private static final String VERSION_FILE = ".monomod_patch_version";
    
    /**
     * 应用补丁到游戏目录（旧版本，保持向后兼容）
     *
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @return 替换的程序集数量
     */
    public static int applyPatches(Context context, String gameDirectory) {
        return applyPatches(context, gameDirectory, null);
    }

    /**
     * 应用补丁到游戏目录（新版本，支持配置）
     *
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @param enabledPatches 启用的补丁列表（如果为null则应用所有补丁）
     * @return 替换的程序集数量
     */
    public static int applyPatches(Context context, String gameDirectory, List<PatchInfo> enabledPatches) {
        // [OK] 检查是否需要强制更新
        if (shouldForceUpdate(gameDirectory)) {
            AppLogger.warn(TAG, "🔄 检测到补丁版本更新，强制清理旧版本补丁...");
            cleanOldPatches(gameDirectory);
        }
        AppLogger.info(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        AppLogger.info(TAG, "🔧 开始应用补丁");
        AppLogger.info(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        AppLogger.info(TAG, "  游戏目录: " + gameDirectory);

        try {
            // 1. 从 assets 加载 MonoMod 补丁归档
            Map<String, byte[]> monoModAssemblies = loadPatchArchive(context);

            // 2. 加载启用的自定义补丁程序集
            Map<String, byte[]> customPatchAssemblies = loadCustomPatches(context, enabledPatches);

            // 3. 合并所有补丁
            Map<String, byte[]> allPatchAssemblies = new HashMap<>();
            allPatchAssemblies.putAll(monoModAssemblies);
            allPatchAssemblies.putAll(customPatchAssemblies);

            if (allPatchAssemblies.isEmpty()) {
                AppLogger.warn(TAG, "未找到任何补丁程序集");
                return 0;
            }

            AppLogger.info(TAG, "已加载 " + allPatchAssemblies.size() + " 个补丁程序集:");
            for (String assemblyName : monoModAssemblies.keySet()) {
                AppLogger.info(TAG, "   - [MonoMod] " + assemblyName);
            }
            for (String assemblyName : customPatchAssemblies.keySet()) {
                AppLogger.info(TAG, "   - [自定义] " + assemblyName);
            }
            
            // 4. 扫描游戏目录中的程序集
            File gameDir = new File(gameDirectory);
            List<File> gameAssemblies = findGameAssemblies(gameDir);

            AppLogger.info(TAG, "  找到 " + gameAssemblies.size() + " 个游戏程序集");

            // 5. 应用补丁（替换已有的程序集）
            int patchedCount = 0;
            for (File assemblyFile : gameAssemblies) {
                String assemblyName = assemblyFile.getName();


                if (allPatchAssemblies.containsKey(assemblyName)) {
                    if (replaceAssembly(assemblyFile, allPatchAssemblies.get(assemblyName))) {
                        AppLogger.info(TAG, "✅ 已替换: " + assemblyName);
                        patchedCount++;
                    } else {
                        AppLogger.warn(TAG, "❌ 替换失败: " + assemblyName);
                    }
                }
            }

            // 6. 添加缺失的补丁程序集（如果游戏目录中不存在）
            for (Map.Entry<String, byte[]> entry : allPatchAssemblies.entrySet()) {
                String assemblyName = entry.getKey();

                boolean alreadyExists = false;

                for (File assemblyFile : gameAssemblies) {
                    if (assemblyFile.getName().equals(assemblyName)) {
                        alreadyExists = true;
                        break;
                    }
                }

                if (!alreadyExists) {
                    File newAssemblyFile = new File(gameDir, assemblyName);
                    if (replaceAssembly(newAssemblyFile, entry.getValue())) {
                        AppLogger.info(TAG, "➕ 已添加: " + assemblyName);
                        patchedCount++;
                    } else {
                        AppLogger.warn(TAG, "❌ 添加失败: " + assemblyName);
                    }
                }
            }

            AppLogger.info(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            AppLogger.info(TAG, "✅ 补丁应用完成，共处理 " + patchedCount + " 个程序集");
            AppLogger.info(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // [OK] 保存当前补丁版本号
            saveCurrentVersion(gameDirectory);
            
            return patchedCount;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "应用补丁失败", e);
            return -1;
        }
    }
    
    /**
     * 加载自定义补丁程序集
     * 优先从外部存储加载，如果不存在则从 assets 加载
     *
     * @param context Android上下文
     * @param enabledPatches 启用的补丁列表
     * @return 程序集名称 -> 程序集字节数据的映射
     */
    private static Map<String, byte[]> loadCustomPatches(Context context, List<PatchInfo> enabledPatches) {
        Map<String, byte[]> assemblies = new HashMap<>();

        if (enabledPatches == null || enabledPatches.isEmpty()) {
            return assemblies;
        }

        // 获取外部补丁目录
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            externalFilesDir = context.getFilesDir();
        }
        File externalPatchesDir = new File(externalFilesDir, "patches");

        AppLogger.info(TAG, "加载 " + enabledPatches.size() + " 个自定义补丁:");
        AppLogger.info(TAG, "  外部补丁目录: " + externalPatchesDir.getAbsolutePath());

        for (PatchInfo patch : enabledPatches) {
            AppLogger.info(TAG, "  - " + patch.getPatchName() + " (" + patch.getDllFileName() + ")");

            byte[] assemblyData = null;

            // 1. 尝试从外部存储加载（用户自定义补丁）
            File externalPatchFile = new File(externalPatchesDir, patch.getDllFileName());
            if (externalPatchFile.exists()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(externalPatchFile);
                    assemblyData = readAllBytes(fis);
                    fis.close();
                    AppLogger.info(TAG, "    ✅ 从外部存储加载: " + patch.getDllFileName() + " (" + assemblyData.length + " bytes)");
                } catch (IOException e) {
                    AppLogger.warn(TAG, "    ⚠️  外部补丁加载失败，尝试从 assets 加载: " + e.getMessage());
                    assemblyData = null;
                }
            }

            // 2. 如果外部存储不存在，从 assets 加载（内置补丁）
            if (assemblyData == null) {
                try {
                    String assetPath = "patches/" + patch.getDllFileName();
                    InputStream inputStream = context.getAssets().open(assetPath);
                    assemblyData = readAllBytes(inputStream);
                    inputStream.close();
                    AppLogger.info(TAG, "    ✅ 从 assets 加载: " + patch.getDllFileName() + " (" + assemblyData.length + " bytes)");
                } catch (IOException e) {
                    AppLogger.warn(TAG, "    ❌ 无法加载补丁: " + patch.getDllFileName() + " - " + e.getMessage());
                }
            }

            // 3. 添加到映射
            if (assemblyData != null) {
                assemblies.put(patch.getDllFileName(), assemblyData);
            }
        }

        return assemblies;
    }

    /**
     * 从 assets 中加载 MonoMod_Patch.zip
     *
     * @param context Android上下文
     * @return 程序集名称 -> 程序集字节数据的映射
     */
    private static Map<String, byte[]> loadPatchArchive(Context context) {
        Map<String, byte[]> assemblies = new HashMap<>();
        AssetManager assetManager = context.getAssets();
        
        try {
            InputStream inputStream = assetManager.open(PATCH_ARCHIVE);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // 只处理 .dll 文件
                if (!entryName.endsWith(".dll")) {
                    zipInputStream.closeEntry();
                    continue;
                }
                
                // 提取文件名（去除路径）
                String fileName = new File(entryName).getName();
                
                // 读取程序集数据
                byte[] assemblyData = readAllBytes(zipInputStream);
                
                assemblies.put(fileName, assemblyData);
                
                AppLogger.debug(TAG, "  加载补丁: " + fileName + " (" + assemblyData.length + " bytes)");
                
                zipInputStream.closeEntry();
            }
            
            zipInputStream.close();
            inputStream.close();
            
        } catch (IOException e) {
            AppLogger.warn(TAG, "无法加载 " + PATCH_ARCHIVE + ": " + e.getMessage());
        }
        
        return assemblies;
    }
    
    /**
     * 扫描游戏目录，查找所有 .dll 程序集
     * 
     * @param directory 游戏目录
     * @return 程序集文件列表
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
                // 递归扫描子目录
                assemblies.addAll(findGameAssemblies(file));
            } else if (file.getName().endsWith(".dll")) {
                assemblies.add(file);
            }
        }
        
        return assemblies;
    }
    
    /**
     * 替换程序集文件
     * 
     * @param targetFile 目标文件
     * @param assemblyData 新程序集数据
     * @return 是否成功
     */
    private static boolean replaceAssembly(File targetFile, byte[] assemblyData) {
        try {
            // 备份原文件
            File backupFile = new File(targetFile.getAbsolutePath() + ".backup");
            if (targetFile.exists() && !backupFile.exists()) {
                copyFile(targetFile, backupFile);
            }
            
            // 写入新程序集
            FileOutputStream outputStream = new FileOutputStream(targetFile);
            outputStream.write(assemblyData);
            outputStream.close();
            
            return true;
            
        } catch (IOException e) {
            AppLogger.error(TAG, "  替换失败: " + targetFile.getName(), e);
            return false;
        }
    }
    
    /**
     * 从 InputStream 读取所有字节
     */
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        return outputStream.toByteArray();
    }
    
    /**
     * 复制文件
     */
    private static void copyFile(File source, File destination) throws IOException {
        InputStream inputStream = new java.io.FileInputStream(source);
        OutputStream outputStream = new FileOutputStream(destination);
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        inputStream.close();
        outputStream.close();
    }
    
    /**
     * 检查是否需要强制更新补丁
     * 
     * @param gameDirectory 游戏目录路径
     * @return 如果需要强制更新返回 true
     */
    private static boolean shouldForceUpdate(String gameDirectory) {
        File versionFile = new File(gameDirectory, VERSION_FILE);
        
        if (!versionFile.exists()) {
            AppLogger.info(TAG, "  版本文件不存在，需要首次安装补丁");
            return true;
        }
        
        try {
            InputStream is = new java.io.FileInputStream(versionFile);
            byte[] buffer = new byte[16];
            int length = is.read(buffer);
            is.close();
            
            String versionStr = new String(buffer, 0, length).trim();
            int installedVersion = Integer.parseInt(versionStr);
            
            AppLogger.info(TAG, "  已安装补丁版本: " + installedVersion + ", 当前版本: " + PATCH_VERSION);
            
            if (installedVersion < PATCH_VERSION) {
                AppLogger.warn(TAG, "检测到新版本补丁，需要更新！");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            AppLogger.warn(TAG, "  读取版本文件失败，将强制更新", e);
            return true;
        }
    }
    
    /**
     * 清理旧版本的补丁程序集
     * 
     * @param gameDirectory 游戏目录路径
     */
    private static void cleanOldPatches(String gameDirectory) {
        File gameDir = new File(gameDirectory);
        
        // 删除所有 MonoMod 相关的 DLL（但保留 Mono.Cecil，因为 tModLoader 自带特定版本）
        String[] monoModDlls = {
            "MonoMod.RuntimeDetour.dll",
            "MonoMod.Core.dll",
            "MonoMod.Utils.dll",
            "MonoMod.Backports.dll",
            "MonoMod.ILHelpers.dll",
            // 注意：不删除 Mono.Cecil，避免版本冲突
            // "Mono.Cecil.dll",
            // "Mono.Cecil.Pdb.dll",
            // "Mono.Cecil.Mdb.dll",
            // "Mono.Cecil.Rocks.dll",
            "Iced.dll"
        };
        
        int deletedCount = 0;
        for (String dllName : monoModDlls) {
            File dllFile = new File(gameDir, dllName);
            if (dllFile.exists()) {
                if (dllFile.delete()) {
                    AppLogger.info(TAG, "已删除旧版本: " + dllName);
                    deletedCount++;
                } else {
                    AppLogger.warn(TAG, "删除失败: " + dllName);
                }
            }
        }
        
        // 删除版本文件
        File versionFile = new File(gameDir, VERSION_FILE);
        if (versionFile.exists()) {
            versionFile.delete();
        }
        
        AppLogger.info(TAG, "  已清理 " + deletedCount + " 个旧版本补丁文件");
    }
    
    /**
     * 保存当前补丁版本号
     * 
     * @param gameDirectory 游戏目录路径
     */
    private static void saveCurrentVersion(String gameDirectory) {
        try {
            File versionFile = new File(gameDirectory, VERSION_FILE);
            FileOutputStream fos = new FileOutputStream(versionFile);
            fos.write(String.valueOf(PATCH_VERSION).getBytes());
            fos.close();
            AppLogger.info(TAG, "已保存补丁版本: " + PATCH_VERSION);
        } catch (IOException e) {
            AppLogger.warn(TAG, "保存版本文件失败", e);
        }
    }
}

