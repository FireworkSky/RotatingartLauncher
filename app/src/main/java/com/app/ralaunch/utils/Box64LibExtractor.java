package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * Box64 x64 库检查工具
 * 
 * @deprecated Box64 支持已移除，此类已废弃
 */
@Deprecated
public class Box64LibExtractor {
    private static final String TAG = "Box64LibExtractor";
    
    /**
     * 检查 Box64 x64 库是否已解压（初始化阶段已解压）
     * 
     * @param context Android 上下文
     * @return true 已存在，false 不存在
     */
    public static boolean extractLibsIfNeeded(Context context) {
        File x64LibDir = new File(context.getFilesDir(), "x64lib");
        if (x64LibDir.exists() && x64LibDir.isDirectory()) {
            File[] files = x64LibDir.listFiles();
            if (files != null && files.length > 0) {
                AppLogger.info(TAG, "Box64 x64 libraries found");
                return true;
            }
        }
        AppLogger.warn(TAG, "Box64 x64 libraries not found. Please run initialization first.");
        return false;
    }
    
    /**
     * 获取 Box64 x64 库目录路径
     * 
     * @param context Android 上下文
     * @return x64 库目录路径
     */
    public static String getX64LibDir(Context context) {
        return new File(context.getFilesDir(), "x64lib").getAbsolutePath();
    }
}

