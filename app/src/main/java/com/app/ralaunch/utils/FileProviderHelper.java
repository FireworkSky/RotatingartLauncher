package com.app.ralaunch.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.app.ralaunch.provider.RaLaunchFileProvider;

import java.io.File;

/**
 * 文件提供器帮助类
 * 提供便捷的方法来获取文件的 Content URI 并分享文件
 */
public class FileProviderHelper {
    
    /**
     * 获取文件的 Content URI
     * 
     * @param context 上下文
     * @param file 文件
     * @return Content URI，用于安全地分享文件
     */
    public static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                RaLaunchFileProvider.AUTHORITY,
                file
        );
    }
    
    /**
     * 创建分享文件的 Intent
     * 
     * @param context 上下文
     * @param file 要分享的文件
     * @param mimeType MIME 类型（例如："image/*", "application/zip"）
     * @return 配置好的分享 Intent
     */
    public static Intent createShareIntent(Context context, File file, String mimeType) {
        Uri uri = getUriForFile(context, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "分享文件");
    }
    
    /**
     * 创建查看文件的 Intent
     * 
     * @param context 上下文
     * @param file 要查看的文件
     * @param mimeType MIME 类型
     * @return 配置好的查看 Intent
     */
    public static Intent createViewIntent(Context context, File file, String mimeType) {
        Uri uri = getUriForFile(context, file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
    
    /**
     * 创建安装 APK 的 Intent
     * 
     * @param context 上下文
     * @param apkFile APK 文件
     * @return 配置好的安装 Intent
     */
    public static Intent createInstallIntent(Context context, File apkFile) {
        Uri uri = getUriForFile(context, apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
    
    /**
     * 授予其他应用读取权限
     * 
     * @param context 上下文
     * @param intent Intent
     * @param uri 文件 URI
     */
    public static void grantReadPermission(Context context, Intent intent, Uri uri) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.grantUriPermission(
                intent.getComponent().getPackageName(),
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );
    }
    
    /**
     * 撤销 URI 权限
     * 
     * @param context 上下文
     * @param uri 要撤销权限的 URI
     */
    public static void revokeUriPermission(Context context, Uri uri) {
        context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}

