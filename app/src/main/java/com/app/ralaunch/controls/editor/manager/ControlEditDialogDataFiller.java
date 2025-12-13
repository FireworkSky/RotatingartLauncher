package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;
import com.google.android.material.slider.Slider;

/**
 * 控件编辑对话框数据填充管理器
 * 统一管理所有数据填充逻辑
 */
public class ControlEditDialogDataFiller {
    
    /**
     * UI元素引用接口
     */
    public interface UIReferences {
        ControlData getCurrentData();
        int getScreenWidth();
        int getScreenHeight();
        boolean isAutoSize();
        Context getContext();
    }
    
    /**
     * 填充基本信息数据
     */
    public static void fillBasicInfoData(@NonNull View view, @NonNull UIReferences refs) {
        TextView tvControlType = view.findViewById(R.id.tv_control_type);
        TextView tvControlShape = view.findViewById(R.id.tv_control_shape);
        EditText etName = view.findViewById(R.id.et_control_name);
        
        if (refs.getCurrentData() == null) return;
        
        ControlTypeManager.updateTypeDisplay(refs.getContext(), refs.getCurrentData(), tvControlType);
        ControlShapeManager.updateShapeDisplay(refs.getContext(), refs.getCurrentData(), tvControlShape, 
            view.findViewById(R.id.item_control_shape));
        
        // 摇杆模式显示（仅摇杆类型显示）
        TextView tvJoystickMode = view.findViewById(R.id.tv_joystick_mode);
        if (tvJoystickMode != null && refs.getCurrentData().type == ControlData.TYPE_JOYSTICK) {
            ControlJoystickModeManager.updateModeDisplay(refs.getContext(), refs.getCurrentData(), tvJoystickMode);
        }
        
        if (etName != null && refs.getCurrentData().name != null) {
            etName.setText(refs.getCurrentData().name);
        }
        
        // 文本内容编辑（仅文本控件显示）
        EditText etTextContent = view.findViewById(R.id.et_text_content);
        if (etTextContent != null && refs.getCurrentData().displayText != null) {
            etTextContent.setText(refs.getCurrentData().displayText);
        }
        
        // 更新基本信息选项的可见性
        ControlEditDialogVisibilityManager.updateBasicInfoOptionsVisibility(view, refs.getCurrentData());
        
        // 摇杆左右选择开关（仅摇杆类型且为SDL控制器模式或鼠标模式时显示）
        SwitchCompat switchJoystickStickSelect = view.findViewById(R.id.switch_joystick_stick_select);
        TextView tvJoystickStickSelect = view.findViewById(R.id.tv_joystick_stick_select);
        if (switchJoystickStickSelect != null && refs.getCurrentData().type == ControlData.TYPE_JOYSTICK) {
            switchJoystickStickSelect.setChecked(refs.getCurrentData().xboxUseRightStick);
            // 更新显示文本
            if (tvJoystickStickSelect != null) {
                Context context = refs.getContext();
                String text = refs.getCurrentData().xboxUseRightStick ? 
                    context.getString(R.string.editor_right_stick) : 
                    context.getString(R.string.editor_left_stick);
                tvJoystickStickSelect.setText(text);
            }
        }
        
        // 右摇杆攻击模式 RadioGroup（从全局设置读取）
        // 初始化已在 ControlEditDialogUIBinder 中处理
        
        // 触摸穿透开关
        SwitchCompat switchPassThrough = view.findViewById(R.id.switch_pass_through);
        if (switchPassThrough != null) {
            switchPassThrough.setChecked(refs.getCurrentData().passThrough);
        }
        
        // 鼠标移动范围和速度（从全局设置读取）
        // 初始化已在 ControlEditDialogUIBinder 中处理
        
    }
    
    /**
     * 填充位置大小数据
     */
    public static void fillPositionSizeData(@NonNull View view, @NonNull UIReferences refs) {
        if (refs.getCurrentData() == null) return;
        
        Slider sliderPosX = view.findViewById(R.id.seekbar_pos_x);
        TextView tvPosXValue = view.findViewById(R.id.tv_pos_x_value);
        Slider sliderPosY = view.findViewById(R.id.seekbar_pos_y);
        TextView tvPosYValue = view.findViewById(R.id.tv_pos_y_value);
        Slider sliderWidth = view.findViewById(R.id.seekbar_width);
        TextView tvWidthValue = view.findViewById(R.id.tv_width_value);
        Slider sliderHeight = view.findViewById(R.id.seekbar_height);
        TextView tvHeightValue = view.findViewById(R.id.tv_height_value);
        SwitchCompat switchAutoSize = view.findViewById(R.id.switch_auto_size);
        
        if (sliderPosX != null) {
            int xPercent = (int) (refs.getCurrentData().x / refs.getScreenWidth() * 100);
            sliderPosX.setValue(xPercent);
            if (tvPosXValue != null) tvPosXValue.setText(xPercent + "%");
        }
        
        if (sliderPosY != null) {
            int yPercent = (int) (refs.getCurrentData().y / refs.getScreenHeight() * 100);
            sliderPosY.setValue(yPercent);
            if (tvPosYValue != null) tvPosYValue.setText(yPercent + "%");
        }
        
        if (sliderWidth != null) {
            int widthPercent = (int) (refs.getCurrentData().width / refs.getScreenWidth() * 100);
            sliderWidth.setValue(widthPercent);
            if (tvWidthValue != null) tvWidthValue.setText(widthPercent + "%");
        }
        
        if (sliderHeight != null) {
            int heightPercent = (int) (refs.getCurrentData().height / refs.getScreenHeight() * 100);
            sliderHeight.setValue(heightPercent);
            if (tvHeightValue != null) tvHeightValue.setText(heightPercent + "%");
        }
        
        if (switchAutoSize != null) {
            // 检查当前宽高是否相等，如果相等则默认启用自适应
            boolean isAutoSize = Math.abs(refs.getCurrentData().width - refs.getCurrentData().height) < 1.0f;
            switchAutoSize.setChecked(isAutoSize);
        }
    }
    
    /**
     * 填充外观样式数据
     */
    public static void fillAppearanceData(@NonNull View view, @NonNull UIReferences refs) {
        if (refs.getCurrentData() == null) return;
        
        ControlData data = refs.getCurrentData();
        
        // 根据控件类型更新透明度标题和描述
        TextView tvOpacityTitle = view.findViewById(R.id.tv_opacity_title);
        TextView tvOpacityDesc = view.findViewById(R.id.tv_opacity_desc);
        Context context = refs.getContext();
        if (data.type == ControlData.TYPE_JOYSTICK) {
            if (tvOpacityTitle != null) {
                tvOpacityTitle.setText(context.getString(R.string.editor_joystick_bg_opacity));
            }
            if (tvOpacityDesc != null) {
                tvOpacityDesc.setText(context.getString(R.string.editor_joystick_bg_opacity_desc));
            }
        } else {
            if (tvOpacityTitle != null) {
                tvOpacityTitle.setText(context.getString(R.string.editor_opacity));
            }
            if (tvOpacityDesc != null) {
                tvOpacityDesc.setText(context.getString(R.string.editor_opacity_desc));
            }
        }
        
        // 透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.fillSeekBarSetting(view,
            R.id.seekbar_opacity,
            R.id.tv_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                data,
                new ControlEditDialogSeekBarManager.ValueSetter() {
                    @Override
                    public float get(ControlData d) {
                        return d.opacity;
                    }
                    
                    @Override
                    public void set(ControlData d, float value) {
                        d.opacity = value;
                    }
                },
                () -> {} // 填充时不需要通知更新
            )
        );
        
        // 边框透明度设置（使用统一管理器，完全独立）
        ControlEditDialogSeekBarManager.fillSeekBarSetting(view,
            R.id.seekbar_border_opacity,
            R.id.tv_border_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                data,
                new ControlEditDialogSeekBarManager.ValueSetter() {
                    @Override
                    public float get(ControlData d) {
                        // 边框透明度完全独立，默认1.0（完全不透明）
                        return d.borderOpacity != 0 ? d.borderOpacity : 1.0f;
                    }
                    
                    @Override
                    public void set(ControlData d, float value) {
                        d.borderOpacity = value;
                    }
                },
                () -> {} // 填充时不需要通知更新
            )
        );
        
        // 文本透明度设置（使用统一管理器，仅按钮和文本控件显示）
        View cardTextOpacity = view.findViewById(R.id.card_text_opacity);
        if (cardTextOpacity != null && cardTextOpacity.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(view,
                R.id.seekbar_text_opacity,
                R.id.tv_text_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData d) {
                            // 文本透明度完全独立，默认1.0（完全不透明）
                            return d.textOpacity != 0 ? d.textOpacity : 1.0f;
                        }
                        
                        @Override
                        public void set(ControlData d, float value) {
                            d.textOpacity = value;
                        }
                    },
                    () -> {} // 填充时不需要通知更新
                )
            );
        }
        
        SwitchCompat switchVisible = view.findViewById(R.id.switch_visible);
        if (switchVisible != null) {
            switchVisible.setChecked(data.visible);
        }
        
        // 更新外观选项的可见性（根据控件类型和形状）
        ControlEditDialogVisibilityManager.updateAppearanceOptionsVisibility(view, data);
        
        // 圆角半径设置（仅在矩形形状时显示）
        View cardCornerRadius = view.findViewById(R.id.card_corner_radius);
        if (cardCornerRadius != null && cardCornerRadius.getVisibility() == View.VISIBLE) {
            Slider sliderCornerRadius = view.findViewById(R.id.seekbar_corner_radius);
            TextView tvCornerRadiusValue = view.findViewById(R.id.tv_corner_radius_value);
            if (sliderCornerRadius != null && tvCornerRadiusValue != null) {
                int cornerRadius = (int) data.cornerRadius;
                sliderCornerRadius.setValue(cornerRadius);
                tvCornerRadiusValue.setText(cornerRadius + "dp");
            }
        }
        
        // 摇杆中心透明度（使用统一管理器，仅在摇杆类型时显示）
        View cardStickOpacity = view.findViewById(R.id.card_stick_opacity);
        if (cardStickOpacity != null && cardStickOpacity.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(view,
                R.id.seekbar_stick_opacity,
                R.id.tv_stick_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData d) {
                            return d.stickOpacity != 0 ? d.stickOpacity : 1.0f;
                        }
                        
                        @Override
                        public void set(ControlData d, float value) {
                            d.stickOpacity = value;
                        }
                    },
                    () -> {} // 填充时不需要通知更新
                )
            );
        }
        
        // 摇杆圆心大小（使用统一管理器，仅在摇杆类型时显示）
        View cardStickKnobSize = view.findViewById(R.id.card_stick_knob_size);
        if (cardStickKnobSize != null && cardStickKnobSize.getVisibility() == View.VISIBLE) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(view,
                R.id.seekbar_stick_knob_size,
                R.id.tv_stick_knob_size_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    new ControlEditDialogSeekBarManager.ValueSetter() {
                        @Override
                        public float get(ControlData d) {
                            return d.stickKnobSize != 0 ? d.stickKnobSize : 0.4f;
                        }
                        
                        @Override
                        public void set(ControlData d, float value) {
                            d.stickKnobSize = value;
                        }
                    },
                    () -> {} // 填充时不需要通知更新
                )
            );
        }
        
        
        // 更新颜色视图
        View viewBgColor = view.findViewById(R.id.view_bg_color);
        View viewStrokeColor = view.findViewById(R.id.view_stroke_color);
        if (viewBgColor != null) {
            ControlColorManager.updateColorView(viewBgColor, data.bgColor, 8, 2);
        }
        if (viewStrokeColor != null) {
            ControlColorManager.updateColorView(viewStrokeColor, data.strokeColor, 8, 2);
        }
        
    }
    
    /**
     * 填充键值设置数据
     */
    public static void fillKeymapData(@NonNull View view, @NonNull UIReferences refs) {
        if (refs.getCurrentData() == null) return;
        
        ControlData data = refs.getCurrentData();
        
        // 更新键值设置选项的可见性（根据控件类型）
        ControlEditDialogVisibilityManager.updateKeymapOptionsVisibility(view, data);
        
        // 填充按钮键值数据
        fillButtonKeymap(view, data);
    }
    
    /**
     * 填充普通按钮键值数据
     */
    private static void fillButtonKeymap(@NonNull View view, @NonNull ControlData data) {
        TextView tvKeyName = view.findViewById(R.id.tv_key_name);
        if (tvKeyName != null) {
            String keyName = KeyMapper.getKeyName(data.keycode);
            tvKeyName.setText(keyName);
        }
        
        SwitchCompat switchToggleMode = view.findViewById(R.id.switch_toggle_mode);
        if (switchToggleMode != null) {
            switchToggleMode.setChecked(data.isToggle);
        }
    }
    
}

