package com.app.ralaunch.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

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
 * 
 * @author RA Launcher Team
 */
public class AssemblyPatcher {
    private static final String TAG = "AssemblyPatcher";
    private static final String PATCH_ARCHIVE = "MonoMod_Patch.zip";
    
    /**
     * 应用补丁到游戏目录
     * 
     * @param context Android上下文
     * @param gameDirectory 游戏目录路径
     * @return 替换的程序集数量
     */
    public static int applyPatches(Context context, String gameDirectory) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "🔧 开始应用 MonoMod 补丁");
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "  游戏目录: " + gameDirectory);
        
        try {
            // 1. 从 assets 加载补丁归档
            Map<String, byte[]> patchAssemblies = loadPatchArchive(context);
            
            if (patchAssemblies.isEmpty()) {
                Log.w(TAG, "⚠️  未找到补丁程序集");
                return 0;
            }
            
            Log.i(TAG, "✅ 已加载 " + patchAssemblies.size() + " 个补丁程序集:");
            for (String assemblyName : patchAssemblies.keySet()) {
                Log.i(TAG, "   - " + assemblyName);
            }
            
            // 2. 扫描游戏目录中的程序集
            File gameDir = new File(gameDirectory);
            List<File> gameAssemblies = findGameAssemblies(gameDir);
            
            Log.i(TAG, "  找到 " + gameAssemblies.size() + " 个游戏程序集");
            
            // 3. 应用补丁
            int patchedCount = 0;
            for (File assemblyFile : gameAssemblies) {
                String assemblyName = assemblyFile.getName();
                
                if (patchAssemblies.containsKey(assemblyName)) {
                    if (replaceAssembly(assemblyFile, patchAssemblies.get(assemblyName))) {
                        Log.i(TAG, "✅ 已替换: " + assemblyName);
                        patchedCount++;
                    } else {
                        Log.w(TAG, "⚠️  替换失败: " + assemblyName);
                    }
                }
            }
            
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.i(TAG, "✅ 补丁应用完成，共替换 " + patchedCount + " 个程序集");
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            return patchedCount;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 应用补丁失败", e);
            return -1;
        }
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
                
                Log.d(TAG, "  加载补丁: " + fileName + " (" + assemblyData.length + " bytes)");
                
                zipInputStream.closeEntry();
            }
            
            zipInputStream.close();
            inputStream.close();
            
        } catch (IOException e) {
            Log.w(TAG, "⚠️  无法加载 " + PATCH_ARCHIVE + ": " + e.getMessage());
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
            Log.e(TAG, "  替换失败: " + targetFile.getName(), e);
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
}

