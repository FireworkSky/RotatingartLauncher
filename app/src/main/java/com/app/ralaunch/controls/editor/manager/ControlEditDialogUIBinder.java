package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import com.google.android.material.slider.Slider;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.editor.ControlEditDialogMD;
import com.app.ralaunch.controls.editor.manager.ControlColorManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogSeekBarManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.card.MaterialCardView;

/**
 * 控件编辑对话框UI绑定管理器
 * 统一管理所有UI元素的绑定和事件监听器设置
 */
public class ControlEditDialogUIBinder {
    
    /**
     * UI元素引用接口
     * 用于传递UI元素引用给绑定方法
     */
    public interface UIReferences {
        ControlData getCurrentData();
        int getScreenWidth();
        int getScreenHeight();
        boolean isAutoSize();
        void setAutoSize(boolean autoSize);
        void notifyUpdate();
        Context getContext();
        /** 是否正在更新UI（切换控件时），此时不应修改数据 */
        boolean isUpdating();
    }
    
    /**
     * 绑定基本信息视图
     */
    public static void bindBasicInfoViews(@NonNull View view, 
                                         @NonNull UIReferences refs,
                                         @NonNull ControlEditDialogMD dialog) {
        MaterialCardView itemControlType = view.findViewById(R.id.item_control_type);
        TextView tvControlType = view.findViewById(R.id.tv_control_type);
        MaterialCardView itemControlShape = view.findViewById(R.id.item_control_shape);
        TextView tvControlShape = view.findViewById(R.id.tv_control_shape);
        EditText etName = view.findViewById(R.id.et_control_name);
        
        if (itemControlType != null) {
            itemControlType.setOnClickListener(v -> {
                ControlTypeManager.showTypeSelectDialog(dialog.getContext(), refs.getCurrentData(), 
                    (data) -> {
                        ControlTypeManager.updateTypeDisplay(dialog.getContext(), data, tvControlType);
                        refs.notifyUpdate();
                    });
            });
        }
        
        if (itemControlShape != null) {
            itemControlShape.setOnClickListener(v -> {
                ControlShapeManager.showShapeSelectDialog(dialog.getContext(), refs.getCurrentData(), 
                    (data) -> {
                        ControlShapeManager.updateShapeDisplay(dialog.getContext(), data, tvControlShape, itemControlShape);
                        refs.notifyUpdate();
                    });
            });
        }
        
        // 摇杆模式选择（仅摇杆类型显示）
        MaterialCardView itemJoystickMode = view.findViewById(R.id.item_joystick_mode);
        TextView tvJoystickMode = view.findViewById(R.id.tv_joystick_mode);
        if (itemJoystickMode != null) {
            itemJoystickMode.setOnClickListener(v -> {
                ControlJoystickModeManager.showModeSelectDialog(dialog.getContext(), refs.getCurrentData(), 
                    (data) -> {
                        ControlJoystickModeManager.updateModeDisplay(dialog.getContext(), data, tvJoystickMode);
                        // 模式改变时，更新摇杆左右选择的可见性
                        ControlEditDialogVisibilityManager.updateBasicInfoOptionsVisibility(view, data);
                        refs.notifyUpdate();
                    });
            });
        }
        
        if (etName != null) {
            etName.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 切换控件时不修改数据，避免把旧文本写入新控件
                    if (refs.getCurrentData() != null && !refs.isUpdating()) {
                        refs.getCurrentData().name = s.toString();
                        refs.notifyUpdate();
                    }
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        // 文本内容编辑（仅文本控件显示）
        EditText etTextContent = view.findViewById(R.id.et_text_content);
        if (etTextContent != null) {
            etTextContent.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 切换控件时不修改数据，避免把旧文本写入新控件
                    if (refs.getCurrentData() != null && refs.getCurrentData().type == ControlData.TYPE_TEXT && !refs.isUpdating()) {
                        refs.getCurrentData().displayText = s.toString();
                        refs.notifyUpdate();
                    }
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        // 摇杆左右选择开关（仅摇杆类型且为SDL控制器模式或鼠标模式时显示）
        SwitchCompat switchJoystickStickSelect = view.findViewById(R.id.switch_joystick_stick_select);
        TextView tvJoystickStickSelect = view.findViewById(R.id.tv_joystick_stick_select);
        if (switchJoystickStickSelect != null) {
            switchJoystickStickSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null && refs.getCurrentData().type == ControlData.TYPE_JOYSTICK) {
                    refs.getCurrentData().xboxUseRightStick = isChecked;
                    // 更新显示文本
                    if (tvJoystickStickSelect != null) {
                        Context context = refs.getContext();
                        tvJoystickStickSelect.setText(isChecked ? 
                            context.getString(R.string.editor_right_stick) : 
                            context.getString(R.string.editor_left_stick));
                    }
                    // 更新攻击模式选项的可见性
                    ControlEditDialogVisibilityManager.updateBasicInfoOptionsVisibility(view, refs.getCurrentData());
                    refs.notifyUpdate();
                }
            });
        }
        
        // 右摇杆攻击模式 RadioGroup（仅摇杆类型且为鼠标模式且为右摇杆时显示）
        RadioGroup rgAttackMode = view.findViewById(R.id.rg_attack_mode);
        if (rgAttackMode != null) {
            // 从全局设置读取攻击模式
            SettingsManager settingsManager = SettingsManager.getInstance(dialog.getContext());
            int attackMode = settingsManager.getMouseRightStickAttackMode();
            
            // 初始化选中状态
            switch (attackMode) {
                case SettingsManager.ATTACK_MODE_HOLD:
                    rgAttackMode.check(R.id.rb_attack_mode_hold);
                    break;
                case SettingsManager.ATTACK_MODE_CLICK:
                    rgAttackMode.check(R.id.rb_attack_mode_click);
                    break;
                case SettingsManager.ATTACK_MODE_CONTINUOUS:
                    rgAttackMode.check(R.id.rb_attack_mode_continuous);
                    break;
            }
            
            // 监听选择变化，保存到全局设置
            rgAttackMode.setOnCheckedChangeListener((group, checkedId) -> {
                int mode = SettingsManager.ATTACK_MODE_HOLD;
                if (checkedId == R.id.rb_attack_mode_hold) {
                    mode = SettingsManager.ATTACK_MODE_HOLD;
                } else if (checkedId == R.id.rb_attack_mode_click) {
                    mode = SettingsManager.ATTACK_MODE_CLICK;
                } else if (checkedId == R.id.rb_attack_mode_continuous) {
                    mode = SettingsManager.ATTACK_MODE_CONTINUOUS;
                }
                settingsManager.setMouseRightStickAttackMode(mode);
                refs.notifyUpdate();
            });
        }
        
        // 触摸穿透开关
        SwitchCompat switchPassThrough = view.findViewById(R.id.switch_pass_through);
        if (switchPassThrough != null) {
            switchPassThrough.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null) {
                    refs.getCurrentData().passThrough = isChecked;
                    refs.notifyUpdate();
                }
            });
        }
        
        // 鼠标移动范围设置（使用全局设置）
        SettingsManager rangeSettingsManager = SettingsManager.getInstance(dialog.getContext());
        
        Slider sliderMouseRangeLeft = view.findViewById(R.id.seekbar_mouse_range_left);
        TextView tvMouseRangeLeft = view.findViewById(R.id.tv_mouse_range_left);
        Slider sliderMouseRangeTop = view.findViewById(R.id.seekbar_mouse_range_top);
        TextView tvMouseRangeTop = view.findViewById(R.id.tv_mouse_range_top);
        Slider sliderMouseRangeRight = view.findViewById(R.id.seekbar_mouse_range_right);
        TextView tvMouseRangeRight = view.findViewById(R.id.tv_mouse_range_right);
        Slider sliderMouseRangeBottom = view.findViewById(R.id.seekbar_mouse_range_bottom);
        TextView tvMouseRangeBottom = view.findViewById(R.id.tv_mouse_range_bottom);
        
        // 初始化鼠标范围值（从全局设置读取）
        if (sliderMouseRangeLeft != null) {
            int leftProgress = (int) (rangeSettingsManager.getMouseRightStickRangeLeft() * 100);
            sliderMouseRangeLeft.setValue(leftProgress);
            if (tvMouseRangeLeft != null) tvMouseRangeLeft.setText(leftProgress + "%");
            
            sliderMouseRangeLeft.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvMouseRangeLeft != null) tvMouseRangeLeft.setText(progress + "%");
                if (fromUser) {
                    float rangeValue = progress / 100f;
                    rangeSettingsManager.setMouseRightStickRangeLeft(rangeValue);
                    android.util.Log.i("ControlEditDialog", "Saved mouse range LEFT: " + progress + "% -> " + rangeValue);
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderMouseRangeTop != null) {
            int topProgress = (int) (rangeSettingsManager.getMouseRightStickRangeTop() * 100);
            sliderMouseRangeTop.setValue(topProgress);
            if (tvMouseRangeTop != null) tvMouseRangeTop.setText(topProgress + "%");
            
            sliderMouseRangeTop.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvMouseRangeTop != null) tvMouseRangeTop.setText(progress + "%");
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeTop(progress / 100f);
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderMouseRangeRight != null) {
            int rightProgress = (int) (rangeSettingsManager.getMouseRightStickRangeRight() * 100);
            sliderMouseRangeRight.setValue(rightProgress);
            if (tvMouseRangeRight != null) tvMouseRangeRight.setText(rightProgress + "%");
            
            sliderMouseRangeRight.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvMouseRangeRight != null) tvMouseRangeRight.setText(progress + "%");
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeRight(progress / 100f);
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderMouseRangeBottom != null) {
            int bottomProgress = (int) (rangeSettingsManager.getMouseRightStickRangeBottom() * 100);
            sliderMouseRangeBottom.setValue(bottomProgress);
            if (tvMouseRangeBottom != null) tvMouseRangeBottom.setText(bottomProgress + "%");
            
            sliderMouseRangeBottom.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvMouseRangeBottom != null) tvMouseRangeBottom.setText(progress + "%");
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickRangeBottom(progress / 100f);
                    refs.notifyUpdate();
                }
            });
        }
        
        // 鼠标移动速度滑块（使用全局设置，范围60-200，步进10）
        Slider sliderMouseSpeed = view.findViewById(R.id.seekbar_mouse_speed);
        TextView tvMouseSpeed = view.findViewById(R.id.tv_mouse_speed);
        if (sliderMouseSpeed != null) {
            int speedProgress = rangeSettingsManager.getMouseRightStickSpeed();
            // 对齐到步进值（stepSize=10, valueFrom=60）
            float stepSize = 10.0f;
            float valueFrom = 60.0f;
            float alignedValue = valueFrom + Math.round((speedProgress - valueFrom) / stepSize) * stepSize;
            // 确保在范围内
            alignedValue = Math.max(valueFrom, Math.min(200.0f, alignedValue));
            sliderMouseSpeed.setValue(alignedValue);
            int displayValue = (int) alignedValue;
            if (tvMouseSpeed != null) tvMouseSpeed.setText(String.valueOf(displayValue));
            
            sliderMouseSpeed.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvMouseSpeed != null) tvMouseSpeed.setText(String.valueOf(progress));
                if (fromUser) {
                    rangeSettingsManager.setMouseRightStickSpeed(progress);
                    refs.notifyUpdate();
                }
            });
        }
    }
    
    /**
     * 绑定位置大小视图
     */
    public static void bindPositionSizeViews(@NonNull View view, @NonNull UIReferences refs) {
        // 卡片视图
        View cardJoystickSize = view.findViewById(R.id.card_joystick_size);
        View cardWidth = view.findViewById(R.id.card_width);
        View cardHeight = view.findViewById(R.id.card_height);
        
        // 摇杆大小
        Slider sliderJoystickSize = view.findViewById(R.id.seekbar_joystick_size);
        TextView tvJoystickSizeValue = view.findViewById(R.id.tv_joystick_size_value);
        
        // 位置和尺寸
        Slider sliderPosX = view.findViewById(R.id.seekbar_pos_x);
        TextView tvPosXValue = view.findViewById(R.id.tv_pos_x_value);
        Slider sliderPosY = view.findViewById(R.id.seekbar_pos_y);
        TextView tvPosYValue = view.findViewById(R.id.tv_pos_y_value);
        Slider sliderWidth = view.findViewById(R.id.seekbar_width);
        TextView tvWidthValue = view.findViewById(R.id.tv_width_value);
        Slider sliderHeight = view.findViewById(R.id.seekbar_height);
        TextView tvHeightValue = view.findViewById(R.id.tv_height_value);
        SwitchCompat switchAutoSize = view.findViewById(R.id.switch_auto_size);
        
        // 根据控件类型显示/隐藏相应的卡片
        boolean isJoystick = refs.getCurrentData() != null && 
                           refs.getCurrentData().type == ControlData.TYPE_JOYSTICK;
        
        if (cardJoystickSize != null) {
            cardJoystickSize.setVisibility(isJoystick ? View.VISIBLE : View.GONE);
        }
        if (cardWidth != null) {
            cardWidth.setVisibility(isJoystick ? View.GONE : View.VISIBLE);
        }
        if (cardHeight != null) {
            cardHeight.setVisibility(isJoystick ? View.GONE : View.VISIBLE);
        }
        
        // 摇杆大小设置（仅摇杆显示，同时设置宽度和高度）
        if (sliderJoystickSize != null && isJoystick) {
            sliderJoystickSize.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvJoystickSizeValue != null) tvJoystickSizeValue.setText(progress + "%");
                if (refs.getCurrentData() != null && fromUser) {
                    float size = refs.getScreenWidth() * progress / 100f;
                    refs.getCurrentData().width = size;
                    refs.getCurrentData().height = size;
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderPosX != null) {
            sliderPosX.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvPosXValue != null) tvPosXValue.setText(progress + "%");
                if (refs.getCurrentData() != null && fromUser) {
                    refs.getCurrentData().x = refs.getScreenWidth() * progress / 100f;
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderPosY != null) {
            sliderPosY.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvPosYValue != null) tvPosYValue.setText(progress + "%");
                if (refs.getCurrentData() != null && fromUser) {
                    refs.getCurrentData().y = refs.getScreenHeight() * progress / 100f;
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderWidth != null) {
            sliderWidth.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvWidthValue != null) tvWidthValue.setText(progress + "%");
                if (refs.getCurrentData() != null && fromUser) {
                    float width = refs.getScreenWidth() * progress / 100f;
                    refs.getCurrentData().width = width;
                    if (refs.isAutoSize()) {
                        refs.getCurrentData().height = width;
                        if (sliderHeight != null) {
                            int heightPercent = (int) (width / refs.getScreenHeight() * 100);
                            sliderHeight.setValue(heightPercent);
                            if (tvHeightValue != null) tvHeightValue.setText(heightPercent + "%");
                        }
                    }
                    refs.notifyUpdate();
                }
            });
        }
        
        if (sliderHeight != null) {
            sliderHeight.addOnChangeListener((slider, value, fromUser) -> {
                int progress = (int) value;
                if (tvHeightValue != null) tvHeightValue.setText(progress + "%");
                if (refs.getCurrentData() != null && fromUser) {
                    float height = refs.getScreenHeight() * progress / 100f;
                    refs.getCurrentData().height = height;
                    if (refs.isAutoSize()) {
                        refs.getCurrentData().width = height;
                        if (sliderWidth != null) {
                            int widthPercent = (int) (height / refs.getScreenWidth() * 100);
                            sliderWidth.setValue(widthPercent);
                            if (tvWidthValue != null) tvWidthValue.setText(widthPercent + "%");
                        }
                    }
                    refs.notifyUpdate();
                }
            });
        }
        
        if (switchAutoSize != null) {
            switchAutoSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
                refs.setAutoSize(isChecked);
                if (refs.getCurrentData() != null && isChecked) {
                    refs.getCurrentData().height = refs.getCurrentData().width;
                    if (sliderHeight != null) {
                        int heightPercent = (int) (refs.getCurrentData().height / refs.getScreenHeight() * 100);
                        sliderHeight.setValue(heightPercent);
                        if (tvHeightValue != null) tvHeightValue.setText(heightPercent + "%");
                    }
                    refs.notifyUpdate();
                }
            });
        }
    }
    
    /**
     * 绑定外观样式视图
     */
    public static void bindAppearanceViews(@NonNull View view, 
                                           @NonNull UIReferences refs,
                                           @NonNull ControlEditDialogMD dialog) {
        Slider sliderOpacity = view.findViewById(R.id.seekbar_opacity);
        TextView tvOpacityValue = view.findViewById(R.id.tv_opacity_value);
        SwitchCompat switchVisible = view.findViewById(R.id.switch_visible);
        View viewBgColor = view.findViewById(R.id.view_bg_color);
        View viewStrokeColor = view.findViewById(R.id.view_stroke_color);
        Slider sliderCornerRadius = view.findViewById(R.id.seekbar_corner_radius);
        TextView tvCornerRadiusValue = view.findViewById(R.id.tv_corner_radius_value);
        Slider sliderStickOpacity = view.findViewById(R.id.seekbar_stick_opacity);
        TextView tvStickOpacityValue = view.findViewById(R.id.tv_stick_opacity_value);
        Slider sliderStickKnobSize = view.findViewById(R.id.seekbar_stick_knob_size);
        TextView tvStickKnobSizeValue = view.findViewById(R.id.tv_stick_knob_size_value);
       
        
        // 透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.bindSeekBarSetting(view,
            R.id.seekbar_opacity,
            R.id.tv_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                refs.getCurrentData(),
                new ControlEditDialogSeekBarManager.ValueSetter() {
                    @Override
                    public float get(ControlData data) {
                        return data.opacity;
                    }
                    
                    @Override
                    public void set(ControlData data, float value) {
                        data.opacity = value;
                    }
                },
                refs::notifyUpdate
            )
        );
        
        // 边框透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.bindSeekBarSetting(view,
            R.id.seekbar_border_opacity,
            R.id.tv_border_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                refs.getCurrentData(),
                new ControlEditDialogSeekBarManager.ValueSetter() {
                    @Override
                    public float get(ControlData data) {
                        // 如果为0则使用背景透明度作为兼容
                        return data.borderOpacity != 0 ? data.borderOpacity : data.opacity;
                    }
                    
                    @Override
                    public void set(ControlData data, float value) {
                        data.borderOpacity = value;
                    }
                },
                refs::notifyUpdate
            )
        );
        
        // 文本透明度设置（使用统一管理器，仅按钮和文本控件显示）
        View cardTextOpacity = view.findViewById(R.id.card_text_opacity);
        if (cardTextOpacity != null && cardTextOpacity.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(view,
                R.id.seekbar_text_opacity,
                R.id.tv_text_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.getCurrentData(),
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData data) {
                            // 如果为0则使用背景透明度作为兼容
                            return data.textOpacity != 0 ? data.textOpacity : data.opacity;
                        }
                        
                        @Override
                        public void set(ControlData data, float value) {
                            data.textOpacity = value;
                        }
                    },
                    refs::notifyUpdate
                )
            );
        }
        
        if (switchVisible != null) {
            switchVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null) {
                    refs.getCurrentData().visible = isChecked;
                    refs.notifyUpdate();
                }
            });
        }
        
        View itemBgColor = view.findViewById(R.id.item_bg_color);
        if (itemBgColor != null) {
            itemBgColor.setOnClickListener(v -> {
                ControlColorManager.showColorPickerDialog(dialog.getContext(), refs.getCurrentData(), true,
                    (data, color, isBg) -> {
                        int dp8 = (int) (8 * dialog.getContext().getResources().getDisplayMetrics().density);
                        int dp2 = (int) (2 * dialog.getContext().getResources().getDisplayMetrics().density);
                        ControlColorManager.updateColorView(viewBgColor, data.bgColor, dp8, dp2);
                        refs.notifyUpdate();
                    });
            });
        }
        
        View itemStrokeColor = view.findViewById(R.id.item_stroke_color);
        if (itemStrokeColor != null) {
            itemStrokeColor.setOnClickListener(v -> {
                ControlColorManager.showColorPickerDialog(dialog.getContext(), refs.getCurrentData(), false,
                    (data, color, isBg) -> {
                        int dp8 = (int) (8 * dialog.getContext().getResources().getDisplayMetrics().density);
                        int dp2 = (int) (2 * dialog.getContext().getResources().getDisplayMetrics().density);
                        ControlColorManager.updateColorView(viewStrokeColor, data.strokeColor, dp8, dp2);
                        refs.notifyUpdate();
                    });
            });
        }
        
        // 更新外观选项的可见性（根据控件类型和形状）
        ControlEditDialogVisibilityManager.updateAppearanceOptionsVisibility(view, refs.getCurrentData());
        
        // 圆角半径设置（仅在矩形形状时显示）
        View cardCornerRadius = view.findViewById(R.id.card_corner_radius);
        if (cardCornerRadius != null && cardCornerRadius.getVisibility() == View.VISIBLE) {
            if (sliderCornerRadius != null && tvCornerRadiusValue != null) {
                if (refs.getCurrentData() != null) {
                    int cornerRadius = (int) refs.getCurrentData().cornerRadius;
                    sliderCornerRadius.setValue(cornerRadius);
                    tvCornerRadiusValue.setText(cornerRadius + "dp");
                }
                
                sliderCornerRadius.addOnChangeListener((slider, value, fromUser) -> {
                    int progress = (int) value;
                    if (fromUser && refs.getCurrentData() != null) {
                        refs.getCurrentData().cornerRadius = progress;
                        tvCornerRadiusValue.setText(progress + "dp");
                        refs.notifyUpdate();
                    }
                });
            }
        }
        
        // 摇杆透明度设置（使用统一管理器，仅在摇杆类型时显示）
        View cardStickOpacity = view.findViewById(R.id.card_stick_opacity);
        if (cardStickOpacity != null && cardStickOpacity.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(view,
                R.id.seekbar_stick_opacity,
                R.id.tv_stick_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.getCurrentData(),
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData data) {
                            return data.stickOpacity != 0 ? data.stickOpacity : 1.0f;
                        }
                        
                        @Override
                        public void set(ControlData data, float value) {
                            data.stickOpacity = value;
                        }
                    },
                    refs::notifyUpdate
                )
            );
        }
        
        // 摇杆圆心大小设置（使用统一管理器，仅在摇杆类型时显示）
        View cardStickKnobSize = view.findViewById(R.id.card_stick_knob_size);
        if (cardStickKnobSize != null && cardStickKnobSize.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.bindSeekBarSetting(view,
                R.id.seekbar_stick_knob_size,
                R.id.tv_stick_knob_size_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    refs.getCurrentData(),
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData data) {
                            return data.stickKnobSize != 0 ? data.stickKnobSize : 0.4f;
                        }
                        
                        @Override
                        public void set(ControlData data, float value) {
                            data.stickKnobSize = value;
                        }
                    },
                    refs::notifyUpdate
                )
            );
        }
        
    }
}

