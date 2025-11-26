package com.app.ralib.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Material Design 3 风格的 Snackbar 辅助工具
 * 统一主题色调，支持图标和操作按钮
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
        show(rootView, message, Type.SUCCESS, Snackbar.LENGTH_SHORT, null, null, null);
    }

    /**
     * 显示错误提示
     */
    public static void showError(View rootView, String message) {
        show(rootView, message, Type.ERROR, Snackbar.LENGTH_LONG, null, null, null);
    }

    /**
     * 显示信息提示
     */
    public static void showInfo(View rootView, String message) {
        show(rootView, message, Type.INFO, Snackbar.LENGTH_SHORT, null, null, null);
    }

    /**
     * 显示警告提示
     */
    public static void showWarning(View rootView, String message) {
        show(rootView, message, Type.WARNING, Snackbar.LENGTH_LONG, null, null, null);
    }

    /**
     * 显示带操作按钮的 Snackbar
     */
    public static void showWithAction(View rootView, String message, Type type,
                                      String actionText, View.OnClickListener actionListener) {
        show(rootView, message, type, Snackbar.LENGTH_LONG, actionText, actionListener, null);
    }

    /**
     * 显示带图标和操作按钮的 Snackbar
     */
    public static void showWithIconAndAction(View rootView, String message, Type type,
                                             String actionText, View.OnClickListener actionListener,
                                             Drawable icon) {
        show(rootView, message, type, Snackbar.LENGTH_LONG, actionText, actionListener, icon);
    }

    /**
     * 显示 Material Design 3 风格的 Snackbar
     *
     * @param rootView 根视图
     * @param message 消息文本
     * @param type Snackbar类型
     * @param duration 显示时长
     * @param actionText 操作按钮文本（可选）
     * @param actionListener 操作按钮监听器（可选）
     * @param icon 图标（可选）
     */
    private static void show(View rootView, String message, Type type, int duration,
                            String actionText, View.OnClickListener actionListener, Drawable icon) {
        if (rootView == null || message == null) {
            return;
        }

        Context context = rootView.getContext();
        Snackbar snackbar = Snackbar.make(rootView, "", duration);

        // 获取 Snackbar 的布局
        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();

        // 隐藏默认的 TextView 和背景
        TextView defaultTextView = snackbarLayout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (defaultTextView != null) {
            defaultTextView.setVisibility(View.INVISIBLE);
        }

        // 移除默认背景
        snackbarLayout.setBackground(null);
        snackbarLayout.setPadding(0, 0, 0, 0);

        // 加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(context);
        View customView = inflater.inflate(com.app.ralib.R.layout.ralib_snackbar_layout, null);

        // 获取视图元素
        TextView messageView = customView.findViewById(com.app.ralib.R.id.snackbar_message);
        MaterialButton actionButton = customView.findViewById(com.app.ralib.R.id.snackbar_action);
        ImageView iconView = customView.findViewById(com.app.ralib.R.id.snackbar_icon);

        // 设置消息
        messageView.setText(message);

        // 根据类型设置样式
        switch (type) {
            case SUCCESS:
                // 成功：使用主题色边框
                break;
            case ERROR:
                // 错误：允许更多行显示
                messageView.setMaxLines(3);
                break;
            case WARNING:
                // 警告
                break;
            case INFO:
            default:
                // 信息：默认样式
                break;
        }

        // 设置图标
        if (icon != null) {
            iconView.setImageDrawable(icon);
            iconView.setVisibility(View.VISIBLE);
        } else {
            iconView.setVisibility(View.GONE);
        }

        // 设置操作按钮
        if (actionText != null && actionListener != null) {
            actionButton.setText(actionText);
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(v -> {
                actionListener.onClick(v);
                snackbar.dismiss();
            });
        } else {
            actionButton.setVisibility(View.GONE);
        }

        // 添加自定义视图到 Snackbar
        snackbarLayout.addView(customView, 0);

        // 设置 Snackbar 的 margin，使其不贴边（MD3 风格）
        android.view.ViewGroup.MarginLayoutParams params =
            (android.view.ViewGroup.MarginLayoutParams) snackbarLayout.getLayoutParams();
        if (params != null) {
            params.setMargins(16, 0, 16, 16);
            snackbarLayout.setLayoutParams(params);
        }

        snackbar.show();
    }
}
