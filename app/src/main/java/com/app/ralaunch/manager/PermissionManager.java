package com.app.ralaunch.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * 权限管理器
 */
public class PermissionManager {
    private final ComponentActivity activity;
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private PermissionCallback currentPermissionCallback;
    
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }
    
    public PermissionManager(ComponentActivity activity) {
        this.activity = activity;
    }
    
    /**
     * 初始化权限请求器
     */
    public void initialize() {
        requestPermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (currentPermissionCallback != null) {
                    if (allGranted) {
                        currentPermissionCallback.onPermissionsGranted();
                    } else {
                        currentPermissionCallback.onPermissionsDenied();
                    }
                    currentPermissionCallback = null;
                }
            });
        
        manageAllFilesLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (currentPermissionCallback != null) {
                        if (Environment.isExternalStorageManager()) {
                            currentPermissionCallback.onPermissionsGranted();
                        } else {
                            currentPermissionCallback.onPermissionsDenied();
                        }
                        currentPermissionCallback = null;
                    }
                }
            });
    }
    
    /**
     * 检查是否具有必要的权限（仅检查存储权限，通知权限为可选）
     */
    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 检查是否具有通知权限（Android 13+）
     */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 13以下不需要通知权限
        return true;
    }
    
    /**
     * 请求必要的权限（存储权限 + 可选的通知权限）
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        this.currentPermissionCallback = callback;
        // 先请求存储权限，这是必需的
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                manageAllFilesLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageAllFilesLauncher.launch(intent);
            }
        } else {
            requestPermissionLauncher.launch(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }
    }
    
    /**
     * 请求通知权限（Android 13+）
     * 这是可选权限，用户拒绝不影响应用核心功能
     */
    public void requestNotificationPermission(PermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.currentPermissionCallback = callback;
            requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        } else {
            // Android 13以下不需要请求，直接回调成功
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
}

