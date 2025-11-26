package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.controls.ControlData;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 控件形状管理器
 * 统一管理控件形状的选择和显示逻辑
 */
public class ControlShapeManager {

    /**
     * 获取形状显示名称
     */
    public static String getShapeDisplayName(ControlData data) {
        if (data == null) {
            return "矩形";
        }
        
        if (data.shape == ControlData.SHAPE_CIRCLE) {
            return "圆形";
        } else {
            return "矩形";
        }
    }

    /**
     * 更新形状显示
     */
    public static void updateShapeDisplay(ControlData data, TextView textView, View shapeItemView) {
        if (data == null) {
            return;
        }
        
        // 按钮和文本控件都支持形状选择
        boolean supportsShape = (data.type == ControlData.TYPE_BUTTON || data.type == ControlData.TYPE_TEXT);
        
        if (supportsShape) {
            if (shapeItemView != null) {
                shapeItemView.setVisibility(View.VISIBLE);
            }
            if (textView != null) {
                String shapeName = getShapeDisplayName(data);
                textView.setText(shapeName);
            }
        } else {
            if (shapeItemView != null) {
                shapeItemView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 显示形状选择对话框
     */
    public static void showShapeSelectDialog(@NonNull Context context,
                                           ControlData data,
                                           OnShapeSelectedListener listener) {
        if (data == null) {
            return;
        }
        
        // 按钮和文本控件都支持形状选择
        boolean supportsShape = (data.type == ControlData.TYPE_BUTTON || data.type == ControlData.TYPE_TEXT);
        if (!supportsShape) {
            return;
        }

        String[] shapes = {"矩形", "圆形"};
        int currentIndex = (data.shape == ControlData.SHAPE_CIRCLE) ? 1 : 0;

        new MaterialAlertDialogBuilder(context)
            .setTitle("选择控件形状")
            .setSingleChoiceItems(shapes, currentIndex, (dialog, which) -> {
                data.shape = which;
                
                if (listener != null) {
                    listener.onShapeSelected(data);
                }
                
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 形状选择监听器
     */
    public interface OnShapeSelectedListener {
        void onShapeSelected(ControlData data);
    }
}

