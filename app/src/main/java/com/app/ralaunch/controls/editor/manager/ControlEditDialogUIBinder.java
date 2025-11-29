package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.editor.ControlEditDialogMD;
import com.app.ralaunch.controls.editor.manager.ControlColorManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogSeekBarManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;
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
                        ControlTypeManager.updateTypeDisplay(data, tvControlType);
                        refs.notifyUpdate();
                    });
            });
        }
        
        if (itemControlShape != null) {
            itemControlShape.setOnClickListener(v -> {
                ControlShapeManager.showShapeSelectDialog(dialog.getContext(), refs.getCurrentData(), 
                    (data) -> {
                        ControlShapeManager.updateShapeDisplay(data, tvControlShape, itemControlShape);
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
                        ControlJoystickModeManager.updateModeDisplay(data, tvJoystickMode);
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
                    if (refs.getCurrentData() != null) {
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
                    if (refs.getCurrentData() != null && refs.getCurrentData().type == ControlData.TYPE_TEXT) {
                        refs.getCurrentData().displayText = s.toString();
                        refs.notifyUpdate();
                    }
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        // 摇杆左右选择开关（仅摇杆类型且为SDL控制器模式时显示）
        SwitchCompat switchJoystickStickSelect = view.findViewById(R.id.switch_joystick_stick_select);
        TextView tvJoystickStickSelect = view.findViewById(R.id.tv_joystick_stick_select);
        if (switchJoystickStickSelect != null) {
            switchJoystickStickSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (refs.getCurrentData() != null && refs.getCurrentData().type == ControlData.TYPE_JOYSTICK) {
                    refs.getCurrentData().xboxUseRightStick = isChecked;
                    // 更新显示文本
                    if (tvJoystickStickSelect != null) {
                        tvJoystickStickSelect.setText(isChecked ? "右摇杆" : "左摇杆");
                    }
                    refs.notifyUpdate();
                }
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
    }
    
    /**
     * 绑定位置大小视图
     */
    public static void bindPositionSizeViews(@NonNull View view, @NonNull UIReferences refs) {
        SeekBar seekbarPosX = view.findViewById(R.id.seekbar_pos_x);
        TextView tvPosXValue = view.findViewById(R.id.tv_pos_x_value);
        SeekBar seekbarPosY = view.findViewById(R.id.seekbar_pos_y);
        TextView tvPosYValue = view.findViewById(R.id.tv_pos_y_value);
        SeekBar seekbarWidth = view.findViewById(R.id.seekbar_width);
        TextView tvWidthValue = view.findViewById(R.id.tv_width_value);
        SeekBar seekbarHeight = view.findViewById(R.id.seekbar_height);
        TextView tvHeightValue = view.findViewById(R.id.tv_height_value);
        SwitchCompat switchAutoSize = view.findViewById(R.id.switch_auto_size);
        
        if (seekbarPosX != null) {
            seekbarPosX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (tvPosXValue != null) tvPosXValue.setText(progress + "%");
                    if (refs.getCurrentData() != null && fromUser) {
                        refs.getCurrentData().x = refs.getScreenWidth() * progress / 100f;
                        refs.notifyUpdate();
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        if (seekbarPosY != null) {
            seekbarPosY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (tvPosYValue != null) tvPosYValue.setText(progress + "%");
                    if (refs.getCurrentData() != null && fromUser) {
                        refs.getCurrentData().y = refs.getScreenHeight() * progress / 100f;
                        refs.notifyUpdate();
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        if (seekbarWidth != null) {
            seekbarWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (tvWidthValue != null) tvWidthValue.setText(progress + "%");
                    if (refs.getCurrentData() != null && fromUser) {
                        float width = refs.getScreenWidth() * progress / 100f;
                        refs.getCurrentData().width = width;
                        if (refs.isAutoSize()) {
                            refs.getCurrentData().height = width;
                            if (seekbarHeight != null) {
                                int heightPercent = (int) (width / refs.getScreenHeight() * 100);
                                seekbarHeight.setProgress(heightPercent);
                                if (tvHeightValue != null) tvHeightValue.setText(heightPercent + "%");
                            }
                        }
                        refs.notifyUpdate();
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        if (seekbarHeight != null) {
            seekbarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (tvHeightValue != null) tvHeightValue.setText(progress + "%");
                    if (refs.getCurrentData() != null && fromUser) {
                        float height = refs.getScreenHeight() * progress / 100f;
                        refs.getCurrentData().height = height;
                        if (refs.isAutoSize()) {
                            refs.getCurrentData().width = height;
                            if (seekbarWidth != null) {
                                int widthPercent = (int) (height / refs.getScreenWidth() * 100);
                                seekbarWidth.setProgress(widthPercent);
                                if (tvWidthValue != null) tvWidthValue.setText(widthPercent + "%");
                            }
                        }
                        refs.notifyUpdate();
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        if (switchAutoSize != null) {
            switchAutoSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
                refs.setAutoSize(isChecked);
                if (refs.getCurrentData() != null && isChecked) {
                    refs.getCurrentData().height = refs.getCurrentData().width;
                    if (seekbarHeight != null) {
                        int heightPercent = (int) (refs.getCurrentData().height / refs.getScreenHeight() * 100);
                        seekbarHeight.setProgress(heightPercent);
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
        SeekBar seekbarOpacity = view.findViewById(R.id.seekbar_opacity);
        TextView tvOpacityValue = view.findViewById(R.id.tv_opacity_value);
        SwitchCompat switchVisible = view.findViewById(R.id.switch_visible);
        View viewBgColor = view.findViewById(R.id.view_bg_color);
        View viewStrokeColor = view.findViewById(R.id.view_stroke_color);
        SeekBar seekbarCornerRadius = view.findViewById(R.id.seekbar_corner_radius);
        TextView tvCornerRadiusValue = view.findViewById(R.id.tv_corner_radius_value);
        SeekBar seekbarStickOpacity = view.findViewById(R.id.seekbar_stick_opacity);
        TextView tvStickOpacityValue = view.findViewById(R.id.tv_stick_opacity_value);
        SeekBar seekbarStickKnobSize = view.findViewById(R.id.seekbar_stick_knob_size);
        TextView tvStickKnobSizeValue = view.findViewById(R.id.tv_stick_knob_size_value);
        SeekBar seekbarRotation = view.findViewById(R.id.seekbar_rotation);
        TextView tvRotationValue = view.findViewById(R.id.tv_rotation_value);
       
        
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
        
        // 圆角半径设置（使用统一管理器，仅在矩形形状时显示）
        View cardCornerRadius = view.findViewById(R.id.card_corner_radius);
        if (cardCornerRadius != null && cardCornerRadius.getVisibility() == View.VISIBLE) {
            if (seekbarCornerRadius != null && tvCornerRadiusValue != null) {
                seekbarCornerRadius.setMax(100); // 最大圆角半径100dp
                if (refs.getCurrentData() != null) {
                    int cornerRadius = (int) refs.getCurrentData().cornerRadius;
                    seekbarCornerRadius.setProgress(cornerRadius);
                    tvCornerRadiusValue.setText(cornerRadius + "dp");
                }
                
                seekbarCornerRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && refs.getCurrentData() != null) {
                            refs.getCurrentData().cornerRadius = progress;
                            tvCornerRadiusValue.setText(progress + "dp");
                            refs.notifyUpdate();
                        }
                    }
                    
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}
                    
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
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
        
        // 旋转角度设置（0-360度）
        if (seekbarRotation != null && tvRotationValue != null) {
            seekbarRotation.setMax(360);
            if (refs.getCurrentData() != null) {
                int rotation = (int) refs.getCurrentData().rotation;
                seekbarRotation.setProgress(rotation);
                tvRotationValue.setText(rotation + "°");
            }
            
            seekbarRotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && refs.getCurrentData() != null) {
                        refs.getCurrentData().rotation = progress;
                        tvRotationValue.setText(progress + "°");
                        refs.notifyUpdate();
                    }
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
    }
}

