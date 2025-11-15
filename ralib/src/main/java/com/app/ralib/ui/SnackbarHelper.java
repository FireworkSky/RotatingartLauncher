package com.app.ralib.ui;

import android.content.Context;
import android.text.SpannableString;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Snackbar 辅助工具
 * 提供统一的 Snackbar 样式和显示方法
 */
public class SnackbarHelper {
    
    /**
     * Snackbar 类型
     */
    public enum Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }
    
    /**
     * 显示成功提示
     */
    public static void showSuccess(View rootView, String message) {
        show(rootView, message, Type.SUCCESS, Snackbar.LENGTH_SHORT);
    }
    
    /**
     * 显示错误提示
     */
    public static void showError(View rootView, String message) {
        show(rootView, message, Type.ERROR, Snackbar.LENGTH_LONG);
    }
    
    /**
     * 显示信息提示
     */
    public static void showInfo(View rootView, String message) {
        show(rootView, message, Type.INFO, Snackbar.LENGTH_SHORT);
    }
    
    /**
     * 显示警告提示
     */
    public static void showWarning(View rootView, String message) {
        show(rootView, message, Type.WARNING, Snackbar.LENGTH_LONG);
    }
    
    /**
     * 显示自定义 Snackbar
     * 
     * @param rootView 根视图
     * @param message 消息文本
     * @param type Snackbar类型
     * @param duration 显示时长
     */
    public static void show(View rootView, String message, Type type, int duration) {
        if (rootView == null || message == null) {
            return;
        }
        
        Context context = rootView.getContext();
        Snackbar snackbar = Snackbar.make(rootView, message, duration);
        
        // 获取 Snackbar View
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        
        // 根据类型设置样式 - Material Design 3
        String icon;
        int backgroundColor;
        int strokeColor;
        int textColor;

        switch (type) {
            case SUCCESS:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_success);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_success_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_success_text);
                break;
            case ERROR:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_error);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_error_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_error_text);
                textView.setMaxLines(3);
                break;
            case WARNING:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_info);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_info_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_info_text);
                break;
            case INFO:
            default:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_info);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_info_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_info_text);
                break;
        }

        // 创建带圆角和描边的背景
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        background.setColor(backgroundColor);
        background.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
        background.setStroke((int) (1 * context.getResources().getDisplayMetrics().density), strokeColor);

        // 应用样式
        snackbarView.setBackground(background);
        textView.setTextColor(textColor);
        textView.setTextSize(14);
        
        if (textView.getMaxLines() == Integer.MAX_VALUE) {
            textView.setMaxLines(2);
        }
        
        // 添加图标
        SpannableString spannableString = new SpannableString(icon + message);
        textView.setText(spannableString);
        
        snackbar.show();
    }
    
    /**
     * 显示带操作按钮的 Snackbar
     */
    public static void showWithAction(View rootView, String message, Type type, 
                                      String actionText, View.OnClickListener actionListener) {
        if (rootView == null || message == null) {
            return;
        }
        
        Context context = rootView.getContext();
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        
        // 设置操作按钮
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText, actionListener);
            snackbar.setActionTextColor(context.getColor(com.app.ralib.R.color.accent_primary));
        }
        
        // 获取 Snackbar View
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);

        // 根据类型设置样式 - Material Design 3
        String icon;
        int backgroundColor;
        int strokeColor;
        int textColor;

        switch (type) {
            case SUCCESS:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_success);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_success_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_success_text);
                break;
            case ERROR:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_error);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_error_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_error_text);
                break;
            case WARNING:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_info);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_info_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_info_text);
                break;
            case INFO:
            default:
                icon = "";
                backgroundColor = context.getColor(com.app.ralib.R.color.snackbar_info);
                strokeColor = context.getColor(com.app.ralib.R.color.snackbar_info_stroke);
                textColor = context.getColor(com.app.ralib.R.color.snackbar_info_text);
                break;
        }

        // 创建带圆角和描边的背景
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        background.setColor(backgroundColor);
        background.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
        background.setStroke((int) (1 * context.getResources().getDisplayMetrics().density), strokeColor);

        // 应用样式
        snackbarView.setBackground(background);
        textView.setTextColor(textColor);
        textView.setTextSize(14);
        textView.setMaxLines(2);
        
        // 添加图标
        SpannableString spannableString = new SpannableString(icon + message);
        textView.setText(spannableString);
        
        snackbar.show();
    }
}

