package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralib.dialog.ColorPickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 控件颜色管理器
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

        // 获取 FragmentActivity 和 FragmentManager
        FragmentActivity activity = null;
        if (context instanceof FragmentActivity) {
            activity = (FragmentActivity) context;
        } else if (context instanceof android.view.ContextThemeWrapper) {
            Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
            if (baseContext instanceof FragmentActivity) {
                activity = (FragmentActivity) baseContext;
            }
        }

        if (activity == null) {
            // 如果无法获取 FragmentActivity，无法显示调色盘对话框
            return;
        }

        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        
        // 获取当前颜色
        int currentColor = isBackground ? data.bgColor : data.strokeColor;

        // 创建调色盘对话框
        ColorPickerDialog colorPickerDialog = ColorPickerDialog.newInstance(currentColor);
        

        // 设置标题
        String title = isBackground ? context.getString(R.string.editor_select_bg_color) : context.getString(R.string.editor_select_border_color);
        colorPickerDialog.setTitle(title);
        
        // 设置颜色选择监听器
        colorPickerDialog.setOnColorSelectedListener(color -> {
                if (isBackground) {
                    data.bgColor = color;
                } else {
                    data.strokeColor = color;
                }

                if (listener != null) {
                    listener.onColorSelected(data, color, isBackground);
                }
            });

        // 显示对话框
        colorPickerDialog.show(fragmentManager, "ColorPickerDialog");
    }
    

    /**
     * 颜色选择监听器
     */
    public interface OnColorSelectedListener {
        void onColorSelected(ControlData data, int color, boolean isBackground);
    }
}

