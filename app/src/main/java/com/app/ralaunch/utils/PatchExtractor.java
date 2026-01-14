package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.app.ralaunch.core.AssemblyPatcher;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 补丁提取工具
 * 负责首次启动时从 assets 解压并安装补丁和 MonoMod
 */
public class PatchExtractor {
    private static final String TAG = "PatchExtractor";
    private static final String PREFS_NAME = "patch_extractor_prefs";
    private static final String KEY_PATCHES_EXTRACTED = "patches_extracted";
    private static final String KEY_MONOMOD_EXTRACTED = "monomod_extracted";

    /**
     * 检查并提取补丁（如果需要）
     * 这个方法应该在应用启动时调用，会自动判断是否需要提取
     */
    public static void extractPatchesIfNeeded(Context context) {
        // 检查是否已经提取过
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean monomodExtracted = prefs.getBoolean(KEY_MONOMOD_EXTRACTED, false);

        boolean needExtractMonoMod = !monomodExtracted;
        
        if (monomodExtracted) {
            File monoModDir = new File(context.getFilesDir(), "MonoMod");
            if (!monoModDir.exists() || !monoModDir.isDirectory() || 
                monoModDir.listFiles() == null || monoModDir.listFiles().length == 0) {
                needExtractMonoMod = true;
            }
        }

        if (!needExtractMonoMod) {
            return;
        }
        
        // 在后台线程执行提取
        final boolean finalNeedExtractMonoMod = needExtractMonoMod;
        
        new Thread(() -> {
            try {
                if (finalNeedExtractMonoMod) {
                    extractAndApplyMonoMod(context);
                    prefs.edit().putBoolean(KEY_MONOMOD_EXTRACTED, true).apply();
                }
            } catch (Exception e) {
                AppLogger.error(TAG, "提取失败", e);
            }
        }).start();
    }
    
    /**
     * 从 assets/patches.zip 提取补丁到外部存储
     */
    private static void extractPatches(Context context) throws Exception {
        File patchesDir = new File(context.getExternalFilesDir(null), "patches");
        if (patchesDir.exists()) {
            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(patchesDir);
        }
        patchesDir.mkdirs();
        
        // 使用 Apache Commons Compress 解压 zip 文件（更稳定，支持更多编码）
        int fileCount = 0;
        try (InputStream is = context.getAssets().open("patches.zip");
             BufferedInputStream bis = new BufferedInputStream(is, 16384);
             ZipArchiveInputStream zis = new ZipArchiveInputStream(bis, "UTF-8", true, true)) {
            
            // 设置更大的缓冲区以提高性能
            ZipArchiveEntry entry;
            
            while ((entry = zis.getNextZipEntry()) != null) {
                String entryName = entry.getName();
                
                // 跳过顶层 patches 目录
                if (entryName.startsWith("patches/") || entryName.startsWith("patches\\")) {
                    entryName = entryName.substring(8);
                }
                
                if (entryName.isEmpty()) {
                    continue;
                }
                
                File targetFile = new File(patchesDir, entryName);
                
                // 安全检查：防止路径遍历攻击
                String canonicalDestPath = patchesDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        if (!parent.mkdirs() && !parent.exists()) {
                            continue;
                        }
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos, 16384)) {
                        byte[] buffer = new byte[16384];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                        bos.flush();
                    } catch (Exception e) {
                        AppLogger.error(TAG, "解压文件失败: " + entryName, e);
                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        continue;
                    }
                    
                    fileCount++;
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "解压 patches.zip 失败", e);
            throw e;
        }
        
        installPatchZips(context, patchesDir);
    }
    
    /**
     * 安装目录中的所有补丁 ZIP 文件
     */
    private static void installPatchZips(Context context, File patchesDir) {
        try {
            com.app.ralib.patch.PatchManager patchManager = 
                com.app.ralaunch.RaLaunchApplication.getPatchManager();
            
            if (patchManager == null) {
                return;
            }
            
            File[] zipFiles = patchesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
            if (zipFiles == null || zipFiles.length == 0) {
                return;
            }
            
            for (File zipFile : zipFiles) {
                try {
                    java.nio.file.Path zipPath = zipFile.toPath();
                    patchManager.installPatch(zipPath);
                } catch (Exception e) {
                    AppLogger.error(TAG, "安装补丁失败: " + zipFile.getName(), e);
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "安装补丁失败", e);
        }
    }
    
    /**
     * 提取并应用 MonoMod 补丁到所有游戏
     */
    private static void extractAndApplyMonoMod(Context context) throws Exception {
        File monoModDir = new File(context.getFilesDir(), "MonoMod");
        if (monoModDir.exists()) {
            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(monoModDir);
        }
        monoModDir.mkdirs();
        
        // 使用 Apache Commons Compress 解压 MonoMod.zip
        try (InputStream is = context.getAssets().open("MonoMod.zip");
             BufferedInputStream bis = new BufferedInputStream(is, 16384);
             ZipArchiveInputStream zis = new ZipArchiveInputStream(bis, "UTF-8", true, true)) {
            
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                String entryName = entry.getName();
                
                // 跳过顶层 MonoMod 目录（如果存在）
                if (entryName.startsWith("MonoMod/") || entryName.startsWith("MonoMod\\")) {
                    entryName = entryName.substring(8);
                }
                
                if (entryName.isEmpty()) {
                    continue;
                }
                
                File targetFile = new File(monoModDir, entryName);
                String canonicalDestPath = monoModDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    try (FileOutputStream tfos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(tfos)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }
        
        applyMonoModToAllGames(context, monoModDir);
    }
    
    /**
     * 应用 MonoMod 补丁到所有已安装的游戏
     * 复用 AssemblyPatcher 的逻辑，避免代码重复
     */
    private static void applyMonoModToAllGames(Context context, File monoModDir) {
        try {
            com.app.ralaunch.data.GameDataManager gameDataManager = 
                com.app.ralaunch.RaLaunchApplication.getGameDataManager();
            
            if (gameDataManager == null) {
                return;
            }
            
            java.util.List<com.app.ralaunch.model.GameItem> games = gameDataManager.loadGameList();
            if (games.isEmpty()) {
                return;
            }
            
            for (com.app.ralaunch.model.GameItem game : games) {
                String gameDir = getGameDirectory(game.getGamePath());
                if (gameDir == null) {
                    continue;
                }
                
                AssemblyPatcher.applyMonoModPatches(context, gameDir, false);
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "应用 MonoMod 补丁失败", e);
        }
    }
    
  
    private static String getGameDirectory(String gamePath) {
        if (gamePath == null || gamePath.isEmpty()) {
            return null;
        }
        
        File gameFile = new File(gamePath);
        File parentDir = gameFile.getParentFile();
        
        if (parentDir != null && parentDir.exists()) {
            return parentDir.getAbsolutePath();
        }
        
        return null;
    }

    
    /**
     * 重置提取状态（用于测试或重新安装）
     */
    public static void resetExtractionStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_PATCHES_EXTRACTED)
            .remove(KEY_MONOMOD_EXTRACTED)
            .apply();
    }
}

