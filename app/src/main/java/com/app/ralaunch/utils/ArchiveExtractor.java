package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * 通用归档文件解压工具
 * 
 * 支持 tar.gz 格式的解压
 */
public class ArchiveExtractor {
    private static final String TAG = "ArchiveExtractor";
    

    public static int extractTarGz(File archiveFile, File targetDir, String stripPrefix) throws Exception {
        try (FileInputStream fis = new FileInputStream(archiveFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn = 
                 new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzipIn)) {
            
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            int processedFiles = 0;
            
            while ((entry = tarIn.getNextTarEntry()) != null) {
                String entryName = entry.getName();
                
                // 跳过根目录和空条目
                if (entryName.isEmpty() || entryName.equals(".") || entryName.equals("..")) {
                    continue;
                }
                
                // 移除前导的 ./
                if (entryName.startsWith("./")) {
                    entryName = entryName.substring(2);
                }
                
                // 移除指定的前缀
                if (stripPrefix != null && !stripPrefix.isEmpty()) {
                    // 处理 ./usr/lib/ 或 usr/lib/ 前缀
                    if (entryName.startsWith("./" + stripPrefix)) {
                        entryName = entryName.substring(2 + stripPrefix.length());
                    } else if (entryName.startsWith(stripPrefix)) {
                        entryName = entryName.substring(stripPrefix.length());
                    } else if (entryName.contains(stripPrefix)) {
                        // 如果前缀在路径中间，提取前缀之后的部分
                        int idx = entryName.indexOf(stripPrefix);
                        entryName = entryName.substring(idx + stripPrefix.length());
                    } else {
                        // 如果没有找到前缀，跳过不相关的文件（仅当指定了前缀时）
                    }
                }
                
                if (entryName.isEmpty()) {
                    continue;
                }
                
                File targetFile = new File(targetDir, entryName);
                
                // 安全检查：防止路径遍历攻击
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator) && 
                    !canonicalEntryPath.equals(canonicalDestPath)) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    // 写入文件
                    try (FileOutputStream fos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = tarIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // 设置权限（如果是可执行文件）
                    if ((entry.getMode() & 0100) != 0) {
                        targetFile.setExecutable(true, false);
                    }
                    
                    // 设置读取权限
                    targetFile.setReadable(true, false);
                }
                
                processedFiles++;
            }
            
            return processedFiles;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract tar.gz", e);
            throw e;
        }
    }
    
    /**
     * 从 assets 复制文件到临时文件
     * 
     * @param context Android 上下文
     * @param assetFileName assets 中的文件名
     * @param targetFile 目标临时文件
     * @throws IOException IO 错误
     */
    public static void copyAssetToFile(Context context, String assetFileName, File targetFile) throws IOException {
        try (InputStream is = context.getAssets().open(assetFileName);
             FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
        }
    }
    
    /**
     * 递归删除目录
     * 
     * @param directory 要删除的目录
     */
    public static void deleteDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

