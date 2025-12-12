package com.app.ralaunch.provider;

import androidx.core.content.FileProvider;

/**
 * RaLaunch 文件提供器
 * 用于在应用间安全地分享文件
 * 
 * 主要用途：
 * - 分享游戏图标
 * - 分享游戏文件
 * - 导出日志文件
 * - 临时文件访问
 */
public class RaLaunchFileProvider extends FileProvider {
    /**
     * FileProvider Authority
     * 必须与 AndroidManifest.xml 中的 authorities 属性一致
     */
    public static final String AUTHORITY = "com.app.ralaunch.fileprovider";
}

