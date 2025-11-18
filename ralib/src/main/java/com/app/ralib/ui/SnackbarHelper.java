package com.app.ralib.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Snackbar 辅助工具
 * 提供统一的 Snackbar 样式和显示方法
 * 所有样式通过 XML 资源控制，自动跟随深浅色主题
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
        show(rootView, message, Type.SUCCESS, Snackbar.LENGTH_SHORT, null, null);
    }

    /**
     * 显示错误提示
     */
    public static void showError(View rootView, String message) {
        show(rootView, message, Type.ERROR, Snackbar.LENGTH_LONG, null, null);
    }

    /**
     * 显示信息提示
     */
    public static void showInfo(View rootView, String message) {
        show(rootView, message, Type.INFO, Snackbar.LENGTH_SHORT, null, null);
    }

    /**
     * 显示警告提示
     */
    public static void showWarning(View rootView, String message) {
        show(rootView, message, Type.WARNING, Snackbar.LENGTH_LONG, null, null);
    }

    /**
     * 显示带操作按钮的 Snackbar
     */
    public static void showWithAction(View rootView, String message, Type type,
                                      String actionText, View.OnClickListener actionListener) {
        show(rootView, message, type, Snackbar.LENGTH_LONG, actionText, actionListener);
    }

    /**
     * 显示自定义 Snackbar
     *
     * @param rootView 根视图
     * @param message 消息文本
     * @param type Snackbar类型
     * @param duration 显示时长
     * @param actionText 操作按钮文本（可选）
     * @param actionListener 操作按钮监听器（可选）
     */
    private static void show(View rootView, String message, Type type, int duration,
                            String actionText, View.OnClickListener actionListener) {
        if (rootView == null || message == null) {
            return;
        }

        Context context = rootView.getContext();
        Snackbar snackbar = Snackbar.make(rootView, "", duration);

        // 获取 Snackbar 的布局
        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();

        // 隐藏默认的 TextView
        TextView textView = snackbarLayout.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setVisibility(View.INVISIBLE);

        // 加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(context);
        View customView = inflater.inflate(com.app.ralib.R.layout.ralib_snackbar_layout, null);

        // 设置图标、消息和颜色
        TextView iconView = customView.findViewById(com.app.ralib.R.id.snackbar_icon);
        TextView messageView = customView.findViewById(com.app.ralib.R.id.snackbar_message);
        Button actionButton = customView.findViewById(com.app.ralib.R.id.snackbar_action);

        // 根据类型设置样式 - 所有资源从 XML 读取
        String icon;
        int backgroundRes;
        int textColorRes;

        switch (type) {
            case SUCCESS:
                icon = "✓";
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_success;
                textColorRes = com.app.ralib.R.color.snackbar_success_text;
                break;
            case ERROR:
                icon = "✕";
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_error;
                textColorRes = com.app.ralib.R.color.snackbar_error_text;
                messageView.setMaxLines(3);
                break;
            case WARNING:
                icon = "⚠";
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_warning;
                textColorRes = com.app.ralib.R.color.snackbar_warning_text;
                break;
            case INFO:
            default:
                icon = "ℹ";
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_info;
                textColorRes = com.app.ralib.R.color.snackbar_info_text;
                break;
        }

        // 应用样式
        iconView.setText(icon);
        iconView.setTextColor(context.getColor(textColorRes));
        messageView.setText(message);
        messageView.setTextColor(context.getColor(textColorRes));
        snackbarLayout.setBackground(context.getDrawable(backgroundRes));

        // 设置操作按钮
        if (actionText != null && actionListener != null) {
            actionButton.setText(actionText);
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(v -> {
                actionListener.onClick(v);
                snackbar.dismiss();
            });
        }

        // 添加自定义视图到 Snackbar
        snackbarLayout.setPadding(0, 0, 0, 0);
        snackbarLayout.addView(customView, 0);

        snackbar.show();
    }
}
