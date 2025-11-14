package com.app.ralaunch.utils;

import android.content.Context;
import android.util.Log;

import com.app.ralib.icon.IconExtractor;

import java.io.File;

/**
 * 图标提取器辅助类
 * 
 * 从EXE/DLL程序集中提取图标
 * 纯Java实现，不依赖C#或CoreCLR
 */
public class IconExtractorHelper {
    private static final String TAG = "IconExtractor";
    
    /**
     * 从游戏程序集中提取图标
     * 
     * @param context Android上下文
     * @param gamePath 游戏程序集路径
     * @return 成功返回图标路径，失败返回null
     */
    public static String extractGameIcon(Context context, String gamePath) {
        if (gamePath == null || gamePath.isEmpty()) {
            Log.w(TAG, "Game path is null or empty");
            return null;
        }
        
        File gameFile = new File(gamePath);
        if (!gameFile.exists()) {
            Log.w(TAG, "Game file not found: " + gamePath);
            return null;
        }
        
        // 生成输出路径：在游戏文件旁边创建 xxx_icon.png
        String nameWithoutExt = gameFile.getName().replaceAll("\\.[^.]+$", "");
        String iconPath = gameFile.getParent() + File.separator + nameWithoutExt + "_icon.png";
        
        try {
            boolean success = IconExtractor.extractIconToPng(gamePath, iconPath);
            
            if (success) {
                File iconFile = new File(iconPath);
                if (iconFile.exists() && iconFile.length() > 0) {
                    Log.i(TAG, "[OK] Icon extracted successfully: " + iconPath);
                    return iconPath;
                } else {
                    Log.e(TAG, "Icon file was not created or is empty");
                    return null;
                }
            } else {
                Log.e(TAG, "Icon extraction failed");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during icon extraction: " + e.getMessage(), e);
            return null;
        }
    }
}
