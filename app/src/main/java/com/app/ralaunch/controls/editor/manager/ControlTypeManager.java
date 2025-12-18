package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 控件类型管理器
 * 统一管理控件类型的选择和显示逻辑
 */
public class ControlTypeManager {

    /**
     * 获取控件类型显示名称
     */
    public static String getTypeDisplayName(Context context, ControlData data) {
        if (data == null) {
            return context.getString(R.string.control_unknown);
        }
        
        if (data.type == ControlData.TYPE_JOYSTICK) {
            return context.getString(R.string.control_type_joystick);
        } else if (data.type == ControlData.TYPE_TEXT) {
            return context.getString(R.string.control_type_text);
        } else if (data.type == ControlData.TYPE_BUTTON) {
            // 按钮类型：区分键盘和手柄
            if (data.buttonMode == ControlData.BUTTON_MODE_GAMEPAD) {
                return context.getString(R.string.control_type_button_gamepad);
            } else {
                return context.getString(R.string.control_type_button_keyboard);
            }
        } else if (data.type == ControlData.TYPE_TOUCHPAD) {
            return context.getString(R.string.control_type_touchpad);
        } else {
            return context.getString(R.string.control_type_button);
        }
    }

    /**
     * 更新类型显示
     */
    public static void updateTypeDisplay(Context context, ControlData data, TextView textView) {
        if (data == null || textView == null) {
            return;
        }
        
        String typeName = getTypeDisplayName(context, data);
        textView.setText(typeName);
    }

    /**
     * 显示类型选择对话框
     */
    public static void showTypeSelectDialog(@NonNull Context context, 
                                           ControlData data,
                                           OnTypeSelectedListener listener) {
        if (data == null) {
            return;
        }

        String[] types = {
            context.getString(R.string.control_type_button_keyboard),
            context.getString(R.string.control_type_button_gamepad),
            context.getString(R.string.control_type_joystick),
            context.getString(R.string.control_type_touchpad),
            context.getString(R.string.control_type_text)
        };
        
        // 确定当前选中的索引
        int currentIndex;
        if (data.type == ControlData.TYPE_JOYSTICK) {
            currentIndex = 2; // 摇杆
        } else if (data.type == ControlData.TYPE_TEXT) {
            currentIndex = 4; // 文本
        } else if (data.type == ControlData.TYPE_BUTTON) {
            // 按钮类型：根据 buttonMode 判断
            if (data.buttonMode == ControlData.BUTTON_MODE_GAMEPAD) {
                currentIndex = 1; // 按钮（手柄）
            } else {
                currentIndex = 0; // 按钮（键盘）
            }
        } else if (data.type == ControlData.TYPE_TOUCHPAD) {
            currentIndex = 3; // 触控板
        } else {
            // 默认或未知类型，默认为按钮（键盘）
            currentIndex = 0;
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.editor_select_control_type))
            .setSingleChoiceItems(types, currentIndex, (dialog, which) -> {
                if (which == 0) {
                    // 按钮（键盘）
                    data.type = ControlData.TYPE_BUTTON;
                    data.buttonMode = ControlData.BUTTON_MODE_KEYBOARD;
                } else if (which == 1) {
                    // 按钮（手柄）
                    data.type = ControlData.TYPE_BUTTON;
                    data.buttonMode = ControlData.BUTTON_MODE_GAMEPAD;
                } else if (which == 2) {
                    // 摇杆
                    data.type = ControlData.TYPE_JOYSTICK;
                    // 摇杆类型不需要 buttonMode，但为了数据一致性，重置为默认值
                    data.buttonMode = ControlData.BUTTON_MODE_KEYBOARD;
                } else if (which == 3) {
                    data.type = ControlData.TYPE_TOUCHPAD;
                    // 触控板类型不需要 buttonMode，但为了数据一致性，重置为默认值
                    data.buttonMode = ControlData.BUTTON_MODE_KEYBOARD;
                } else if (which == 4) {
                    // 文本
                    data.type = ControlData.TYPE_TEXT;
                    data.shape = ControlData.SHAPE_RECTANGLE; // 默认方形
                    data.displayText = data.displayText != null && !data.displayText.isEmpty() 
                        ? data.displayText 
                        : context.getString(R.string.control_type_text); // 默认文本
                    data.keycode = ControlData.SDL_SCANCODE_UNKNOWN; // 文本控件不支持按键映射
                }
                
                if (listener != null) {
                    listener.onTypeSelected(data);
                }
                
                dialog.dismiss();
            })
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show();
    }

    /**
     * 类型选择监听器
     */
    public interface OnTypeSelectedListener {
        void onTypeSelected(ControlData data);
    }
}

