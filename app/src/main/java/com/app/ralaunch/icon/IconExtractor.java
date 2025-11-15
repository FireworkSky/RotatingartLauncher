package com.app.ralaunch.icon;

import android.graphics.Bitmap;

import com.app.ralaunch.utils.AppLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 PE 文件（EXE/DLL）中提取图标
 * 纯 Java 实现，不依赖 C# 或 CoreCLR
 */
public class IconExtractor {
    private static final String TAG = "IconExtractor";
    
    // Windows 资源类型
    private static final int RT_ICON = 3;
    private static final int RT_GROUP_ICON = 14;
    
    /**
     * 从 EXE/DLL 文件中提取最佳质量的图标并保存为 PNG
     * 
     * @param exePath EXE/DLL 文件路径
     * @param outputPath 输出 PNG 文件路径
     * @return true 表示成功，false 表示失败
     */
    public static boolean extractIconToPng(String exePath, String outputPath) {
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(new File(exePath), "r");
            PeReader reader = new PeReader(file);
            
            // 验证 PE 格式
            if (!reader.isPeFormat()) {
                AppLogger.error(TAG, "Not a valid PE file: " + exePath);
                return false;
            }
            
            // 读取 PE Header
            PeReader.PeHeader peHeader = reader.readPeHeader();
            
            // 读取资源 Section
            PeReader.ResourceSection resourceSection = reader.readResourceSection(peHeader);
            if (resourceSection == null) {
                AppLogger.error(TAG, "No resource section found");
                return false;
            }
            
            // 读取根资源目录
            PeReader.ResourceDirectory rootDir = reader.readResourceDirectory(
                resourceSection, resourceSection.resourceFileOffset);
            
            // 查找图标组资源 (RT_GROUP_ICON)
            IconGroup iconGroup = findBestIconGroup(reader, resourceSection, rootDir);
            if (iconGroup == null) {
                AppLogger.error(TAG, "No icon group found");
                return false;
            }
            
            // 选择最佳质量的图标（最大尺寸，最高位深度）
            IconGroupEntry bestEntry = selectBestIcon(iconGroup);
            if (bestEntry == null) {
                AppLogger.error(TAG, "No suitable icon entry found");
                return false;
            }
            
            // 查找并读取图标数据
            byte[] iconData = findIconData(reader, resourceSection, rootDir, bestEntry.id);
            if (iconData == null) {
                AppLogger.error(TAG, "Icon data not found");
                return false;
            }
            
            // 打印前32字节用于调试
            if (iconData.length >= 32) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(32, iconData.length); i++) {
                    hex.append(String.format("%02X ", iconData[i] & 0xFF));
                }

            }
            
            // 检测图标格式并解码
            Bitmap bitmap = decodeIconData(iconData);
            if (bitmap == null) {
                AppLogger.error(TAG, "Failed to decode icon bitmap");
                return false;
            }
            
            // 保存为 PNG
            FileOutputStream out = new FileOutputStream(outputPath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            
            AppLogger.info(TAG, "Icon extracted successfully to: " + outputPath);
            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract icon: " + e.getMessage(), e);
            return false;
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * 从图标组中选择最佳质量的图标
     * 优先级：尺寸越大越好，位深度越高越好
     */
    private static IconGroupEntry selectBestIcon(IconGroup iconGroup) {
        if (iconGroup.entries == null || iconGroup.entries.length == 0) {
            return null;
        }
        
        IconGroupEntry best = iconGroup.entries[0];
        int bestScore = calculateIconScore(best);
        
        for (int i = 0; i < iconGroup.entries.length; i++) {
            IconGroupEntry entry = iconGroup.entries[i];
            int score = calculateIconScore(entry);

            if (score > bestScore) {
                best = entry;
                bestScore = score;
            }
        }
        
        return best;
    }
    
    /**
     * 计算图标质量分数
     * 分数 = 尺寸 × 位深度权重
     */
    private static int calculateIconScore(IconGroupEntry entry) {
        int sizeScore = entry.width * entry.height;
        int bitScore = entry.bitCount;
        
        // 位深度权重：32位 > 24位 > 8位 > 其他
        int bitWeight = 1;
        if (bitScore == 32) {
            bitWeight = 4;  // 32位有透明通道，最佳
        } else if (bitScore == 24) {
            bitWeight = 3;  // 24位真彩色
        } else if (bitScore == 8) {
            bitWeight = 2;  // 256色
        }
        
        return sizeScore * bitWeight;
    }
    
    /**
     * 查找最佳的图标组（选择最大尺寸）
     */
    private static IconGroup findBestIconGroup(PeReader reader, PeReader.ResourceSection resourceSection,
                                               PeReader.ResourceDirectory rootDir) throws IOException {
        // 查找 RT_GROUP_ICON 类型
        PeReader.ResourceDirectoryEntry groupIconType = null;
        for (PeReader.ResourceDirectoryEntry entry : rootDir.entries) {
            if (entry.nameOrId == RT_GROUP_ICON && entry.isDirectory) {
                groupIconType = entry;
                break;
            }
        }
        
        if (groupIconType == null) {
            return null;
        }
        
        // 读取图标组列表
        long groupDirOffset = resourceSection.resourceFileOffset + groupIconType.offset;
        PeReader.ResourceDirectory groupDir = reader.readResourceDirectory(resourceSection, groupDirOffset);
        
        if (groupDir.entries.length == 0) {
            return null;
        }
        
        // 选择第一个图标组
        PeReader.ResourceDirectoryEntry firstGroup = groupDir.entries[0];
        if (!firstGroup.isDirectory) {
            return null;
        }
        
        // 读取语言目录
        long langDirOffset = resourceSection.resourceFileOffset + firstGroup.offset;
        PeReader.ResourceDirectory langDir = reader.readResourceDirectory(resourceSection, langDirOffset);
        
        if (langDir.entries.length == 0) {
            return null;
        }
        
        // 读取数据
        long dataEntryOffset = resourceSection.resourceFileOffset + langDir.entries[0].offset;
        byte[] groupData = reader.readResourceData(resourceSection, dataEntryOffset);
        
        // 解析图标组数据
        return parseIconGroup(groupData);
    }
    
    /**
     * 解析图标组数据
     */
    private static IconGroup parseIconGroup(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // NEWHEADER 结构
        int reserved = buffer.getShort(0) & 0xFFFF;
        int type = buffer.getShort(2) & 0xFFFF;
        int count = buffer.getShort(4) & 0xFFFF;
        
        if (type != 1 || count == 0) { // 1 = ICON
            return null;
        }
        
        IconGroup group = new IconGroup();
        group.entries = new IconGroupEntry[count];
        
        // 读取每个图标条目 (RESDIR 结构，14 bytes each)
        int offset = 6;
        for (int i = 0; i < count; i++) {
            IconGroupEntry entry = new IconGroupEntry();
            entry.width = buffer.get(offset) & 0xFF;
            entry.height = buffer.get(offset + 1) & 0xFF;
            entry.colorCount = buffer.get(offset + 2) & 0xFF;
            entry.reserved = buffer.get(offset + 3) & 0xFF;
            entry.planes = buffer.getShort(offset + 4) & 0xFFFF;
            entry.bitCount = buffer.getShort(offset + 6) & 0xFFFF;
            entry.bytesInRes = buffer.getInt(offset + 8);
            entry.id = buffer.getShort(offset + 12) & 0xFFFF;
            
            // 0 表示 256
            if (entry.width == 0) entry.width = 256;
            if (entry.height == 0) entry.height = 256;
            
            group.entries[i] = entry;
            offset += 14;
        }
        
        return group;
    }
    
    /**
     * 检测并解码图标数据（支持 PNG 和 BMP）
     */
    private static Bitmap decodeIconData(byte[] iconData) {
        if (iconData == null || iconData.length < 4) {
            return null;
        }
        
        // 检测 PNG 魔数: 89 50 4E 47 (0x89 'P' 'N' 'G')
        if (iconData.length >= 4 && 
            (iconData[0] & 0xFF) == 0x89 && 
            (iconData[1] & 0xFF) == 0x50 && 
            (iconData[2] & 0xFF) == 0x4E && 
            (iconData[3] & 0xFF) == 0x47) {
            
            // PNG 格式，使用 BitmapFactory 解码
            return android.graphics.BitmapFactory.decodeByteArray(iconData, 0, iconData.length);
        }
        
        // BMP 格式（BITMAPINFOHEADER 开头应该是 0x28 = 40）
        return BmpDecoder.decodeBmpIcon(iconData);
    }
    
    /**
     * 查找指定 ID 的图标数据
     */
    private static byte[] findIconData(PeReader reader, PeReader.ResourceSection resourceSection,
                                       PeReader.ResourceDirectory rootDir, int iconId) throws IOException {
        // 查找 RT_ICON 类型
        PeReader.ResourceDirectoryEntry iconType = null;
        for (PeReader.ResourceDirectoryEntry entry : rootDir.entries) {
            if (entry.nameOrId == RT_ICON && entry.isDirectory) {
                iconType = entry;
                break;
            }
        }
        
        if (iconType == null) {
            return null;
        }
        
        // 读取图标列表
        long iconDirOffset = resourceSection.resourceFileOffset + iconType.offset;
        PeReader.ResourceDirectory iconDir = reader.readResourceDirectory(resourceSection, iconDirOffset);
        
        // 查找匹配的 ID
        PeReader.ResourceDirectoryEntry targetIcon = null;
        for (PeReader.ResourceDirectoryEntry entry : iconDir.entries) {
            if (entry.nameOrId == iconId && entry.isDirectory) {
                targetIcon = entry;
                break;
            }
        }
        
        if (targetIcon == null) {
            return null;
        }
        
        // 读取语言目录
        long langDirOffset = resourceSection.resourceFileOffset + targetIcon.offset;
        PeReader.ResourceDirectory langDir = reader.readResourceDirectory(resourceSection, langDirOffset);
        
        if (langDir.entries.length == 0) {
            return null;
        }
        
        // 读取数据
        long dataEntryOffset = resourceSection.resourceFileOffset + langDir.entries[0].offset;
        return reader.readResourceData(resourceSection, dataEntryOffset);
    }
    
    /**
     * 图标组
     */
    private static class IconGroup {
        IconGroupEntry[] entries;
    }
    
    /**
     * 图标组条目
     */
    private static class IconGroupEntry {
        int width;
        int height;
        int colorCount;
        int reserved;
        int planes;
        int bitCount;
        int bytesInRes;
        int id;
    }
}
