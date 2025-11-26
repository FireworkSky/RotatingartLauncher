package com.app.ralaunch.controls.editor.manager;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.app.ralaunch.controls.editor.ControlEditDialogMD;
import com.app.ralaunch.controls.editor.KeySelectorDialog;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;

/**
 * 键值设置管理器
 * 统一管理普通按钮的键值设置逻辑
 */
public class ControlEditDialogKeymapManager {
    
    /**
     * UI元素引用接口
     */
    public interface UIReferences {
        ControlData getCurrentData();
        void notifyUpdate();
    }
    
    /**
     * 绑定键值设置视图
     */
    public static void bindKeymapViews(@NonNull View view, 
                                        @NonNull UIReferences refs,
                                        @NonNull ControlEditDialogMD dialog) {
        View itemKeyMapping = view.findViewById(R.id.item_key_mapping);
        TextView tvKeyName = view.findViewById(R.id.tv_key_name);
        SwitchCompat switchToggleMode = view.findViewById(R.id.switch_toggle_mode);
        
        if (itemKeyMapping != null) {
            itemKeyMapping.setOnClickListener(v -> showKeySelectDialog(dialog, refs, tvKeyName));
        }
        
        if (switchToggleMode != null) {
            switchToggleMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null) {
                    refs.getCurrentData().isToggle = isChecked;
                    refs.notifyUpdate();
                }
            });
        }
    }
    
    /**
     * 显示按键选择对话框
     */
    private static void showKeySelectDialog(@NonNull ControlEditDialogMD dialog,
                                            @NonNull UIReferences refs,
                                            TextView tvKeyName) {
        ControlData data = refs.getCurrentData();
        if (data == null) return;
        
        boolean isGamepadMode = (data.buttonMode == ControlData.BUTTON_MODE_GAMEPAD);
        KeySelectorDialog keyDialog = new KeySelectorDialog(dialog.getContext(), isGamepadMode);
        
        keyDialog.setOnKeySelectedListener((keycode, keyName) -> {
            data.keycode = keycode;
            // 使用 KeyMapper 获取完整的按键名称，确保显示正确
            String fullKeyName = KeyMapper.getKeyName(keycode);
            if (tvKeyName != null) {
                tvKeyName.setText(fullKeyName);
            }
            refs.notifyUpdate();
        });
        
        keyDialog.show();
    }
    
    /**
     * 更新键值设置视图的可见性
     */
    public static void updateKeymapVisibility(@NonNull View keymapView, @NonNull ControlData data) {
        View itemKeyMapping = keymapView.findViewById(R.id.item_key_mapping);
        View itemToggleMode = keymapView.findViewById(R.id.item_toggle_mode);
        
        // 普通按钮：显示单个按键映射（文本控件和摇杆不支持按键映射）
        boolean isButton = (data.type == ControlData.TYPE_BUTTON);
        boolean isText = (data.type == ControlData.TYPE_TEXT);
        boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
        // 只有按钮类型（非文本、非摇杆）才显示键值设置
        boolean shouldShow = isButton && !isText && !isJoystick;
        
        if (itemKeyMapping != null) {
            itemKeyMapping.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
        if (itemToggleMode != null) {
            itemToggleMode.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
    }
}

