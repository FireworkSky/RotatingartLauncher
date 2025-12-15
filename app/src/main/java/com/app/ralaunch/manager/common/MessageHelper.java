package com.app.ralaunch.manager.common;

import android.app.Activity;
import android.view.View;
import com.app.ralib.ui.SnackbarHelper;

/**
 * 消息提示辅助类
 */
public class MessageHelper {
    
    /**
     * 显示成功消息（Snackbar）
     */
    public static void showSuccess(Activity activity, String message) {
        if (activity != null && message != null) {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                SnackbarHelper.showSuccess(rootView, message);
            }
        }
    }
    
    /**
     * 显示错误消息（Snackbar）
     */
    public static void showError(Activity activity, String message) {
        if (activity != null && message != null) {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                SnackbarHelper.showError(rootView, message);
            }
        }
    }
    
    /**
     * 显示信息消息（Snackbar）
     */
    public static void showInfo(Activity activity, String message) {
        if (activity != null && message != null) {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                SnackbarHelper.showInfo(rootView, message);
            }
        }
    }
    
    /**
     * 显示警告消息（Snackbar）
     */
    public static void showWarning(Activity activity, String message) {
        if (activity != null && message != null) {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                SnackbarHelper.showWarning(rootView, message);
            }
        }
    }
    
    /**
     * 显示 Toast 消息
     */
    public static void showToast(Activity activity, String message) {
        if (activity != null && message != null) {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

