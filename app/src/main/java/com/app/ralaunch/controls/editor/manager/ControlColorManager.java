package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.NonNull;

import com.app.ralaunch.controls.ControlData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 控件颜色管理器
 * 统一管理颜色选择和相关UI更新
 */
public class ControlColorManager {

    /**
     * 将 dp 转换为 px
     */
    private static float dpToPx(Context context, float dp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }

    /**
     * 更新颜色视图显示
     */
    public static void updateColorView(View colorView, int color, float cornerRadiusDp, float strokeWidthDp) {
        if (colorView == null) {
            return;
        }
        
        Context context = colorView.getContext();
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(context, cornerRadiusDp));
        drawable.setStroke((int) dpToPx(context, strokeWidthDp), 0xFFD0D0D0);
        colorView.setBackground(drawable);
    }

    /**
     * 显示颜色选择对话框
     */
    public static void showColorPickerDialog(@NonNull Context context,
                                           ControlData data,
                                           boolean isBackground,
                                           OnColorSelectedListener listener) {
        if (data == null) {
            return;
        }

        // 预设颜色
        final int[] presetColors = {
            0xFFFFFFFF, // 白色（默认）
            0xFFEEEEEE, // 浅灰
            0xFFCCCCCC, // 灰色
            0xFF888888, // 深灰
            0xFF000000, // 黑色
            0x80000000, // 半透明黑
            0xFFFF0000, // 红色
            0xFF00FF00, // 绿色
            0xFF0000FF, // 蓝色
            0xFFFF00FF, // 紫色
            0xFFFFFF00, // 黄色
            0xFFFFA500, // 橙色
        };

        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setColumnCount(4);
        int padding = (int) dpToPx(context, 20);
        gridLayout.setPadding(padding, padding, padding, padding);

        // 创建对话框并保存引用
        android.app.Dialog colorDialog = new MaterialAlertDialogBuilder(context)
            .setTitle(isBackground ? "选择背景色" : "选择边框色")
            .setView(gridLayout)
            .setNegativeButton("取消", null)
            .create();

        for (int color : presetColors) {
            MaterialButton colorBtn = new MaterialButton(context);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = (int) dpToPx(context, 60);
            params.height = (int) dpToPx(context, 60);
            int margin = (int) dpToPx(context, 8);
            params.setMargins(margin, margin, margin, margin);
            colorBtn.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dpToPx(context, 8));
            drawable.setStroke((int) dpToPx(context, 2), 0xFF888888);
            colorBtn.setBackground(drawable);

            colorBtn.setOnClickListener(v -> {
                if (isBackground) {
                    data.bgColor = color;
                } else {
                    data.strokeColor = color;
                }

                if (listener != null) {
                    listener.onColorSelected(data, color, isBackground);
                }

                // 点击后关闭对话框
                colorDialog.dismiss();
            });

            gridLayout.addView(colorBtn);
        }

        // 显示对话框
        colorDialog.show();
    }

    /**
     * 颜色选择监听器
     */
    public interface OnColorSelectedListener {
        void onColorSelected(ControlData data, int color, boolean isBackground);
    }
}

