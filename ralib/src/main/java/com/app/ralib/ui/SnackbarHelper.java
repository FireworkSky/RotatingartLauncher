package com.app.ralib.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * 简洁的 Snackbar 辅助工具
 * 跟随系统主题，易于管理
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
     * 显示自定义 Snackbar - 简洁版本，跟随系统主题
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

        // 设置消息
        TextView messageView = customView.findViewById(com.app.ralib.R.id.snackbar_message);
        Button actionButton = customView.findViewById(com.app.ralib.R.id.snackbar_action);

        messageView.setText(message);

        // 根据类型设置背景
        int backgroundRes;
        switch (type) {
            case SUCCESS:
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_success;
                break;
            case ERROR:
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_error;
                messageView.setMaxLines(3);
                break;
            case WARNING:
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_warning;
                break;
            case INFO:
            default:
                backgroundRes = com.app.ralib.R.drawable.ralib_bg_snackbar_info;
                break;
        }

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

        // 设置 Snackbar 的 margin，使其不贴边
        android.view.ViewGroup.MarginLayoutParams params =
            (android.view.ViewGroup.MarginLayoutParams) snackbarLayout.getLayoutParams();
        params.setMargins(16, 0, 16, 80);
        snackbarLayout.setLayoutParams(params);

        snackbar.show();
    }
}
