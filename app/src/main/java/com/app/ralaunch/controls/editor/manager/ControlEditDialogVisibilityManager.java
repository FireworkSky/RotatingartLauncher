package com.app.ralaunch.controls.editor.manager;

import android.view.View;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;

/**
 * 控件编辑对话框选项可见性管理器
 * 统一管理根据控件类型和形状动态显示/隐藏选项的逻辑
 */
public class ControlEditDialogVisibilityManager {
    
    /**
     * 选项可见性规则接口
     */
    public interface VisibilityRule {
        /**
         * 判断选项是否应该显示
         * @param data 控件数据
         * @return true 表示显示，false 表示隐藏
         */
        boolean shouldShow(ControlData data);
    }
    
    /**
     * 更新外观选项的可见性
     * @param view 外观视图
     * @param data 控件数据
     */
    public static void updateAppearanceOptionsVisibility(@NonNull View view, @NonNull ControlData data) {
        
        // 圆角半径：仅矩形形状的按钮和文本控件显示（摇杆是圆形，不需要圆角）
        View cardCornerRadius = view.findViewById(R.id.card_corner_radius);
        if (cardCornerRadius != null) {
            boolean isRectangle = (data.shape == ControlData.SHAPE_RECTANGLE);
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            // 摇杆类型不显示圆角半径（摇杆始终是圆形）
            cardCornerRadius.setVisibility((isRectangle && !isJoystick) ? View.VISIBLE : View.GONE);
        }
        
        // 文本透明度：仅按钮和文本控件显示
        View cardTextOpacity = view.findViewById(R.id.card_text_opacity);
        if (cardTextOpacity != null) {
            boolean isButton = (data.type == ControlData.TYPE_BUTTON);
            boolean isText = (data.type == ControlData.TYPE_TEXT);
            cardTextOpacity.setVisibility((isButton || isText) ? View.VISIBLE : View.GONE);
        }
        
        // 摇杆透明度：仅摇杆类型显示
        View cardStickOpacity = view.findViewById(R.id.card_stick_opacity);
        if (cardStickOpacity != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            cardStickOpacity.setVisibility(isJoystick ? View.VISIBLE : View.GONE);
        }
        
        // 摇杆圆心大小：仅摇杆类型显示
        View cardStickKnobSize = view.findViewById(R.id.card_stick_knob_size);
        if (cardStickKnobSize != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            cardStickKnobSize.setVisibility(isJoystick ? View.VISIBLE : View.GONE);
        }
        
    }
    
    /**
     * 更新键值设置选项的可见性
     * @param view 键值设置视图
     * @param data 控件数据
     */
    public static void updateKeymapOptionsVisibility(@NonNull View view, @NonNull ControlData data) {
        // 普通按钮的键值设置：仅按钮类型显示（文本控件和摇杆不支持按键映射）
        View itemKeyMapping = view.findViewById(R.id.item_key_mapping);
        View itemToggleMode = view.findViewById(R.id.item_toggle_mode);
        if (itemKeyMapping != null || itemToggleMode != null) {
            boolean isButton = (data.type == ControlData.TYPE_BUTTON);
            // 文本控件和摇杆不支持按键映射，隐藏键值设置
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
    
    /**
     * 更新基本信息选项的可见性
     * @param view 基本信息视图
     * @param data 控件数据
     */
    public static void updateBasicInfoOptionsVisibility(@NonNull View view, @NonNull ControlData data) {
        // 控件名称：文本控件隐藏（避免与文本内容重复）
        View itemControlName = view.findViewById(R.id.item_control_name);
        if (itemControlName != null) {
            boolean isText = (data.type == ControlData.TYPE_TEXT);
            itemControlName.setVisibility(isText ? View.GONE : View.VISIBLE);
        }
        
        // 文本内容：仅文本控件显示
        View itemTextContent = view.findViewById(R.id.item_text_content);
        if (itemTextContent != null) {
            boolean isText = (data.type == ControlData.TYPE_TEXT);
            itemTextContent.setVisibility(isText ? View.VISIBLE : View.GONE);
        }
        
        // 摇杆模式选择：仅摇杆类型显示
        View itemJoystickMode = view.findViewById(R.id.item_joystick_mode);
        if (itemJoystickMode != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            itemJoystickMode.setVisibility(isJoystick ? View.VISIBLE : View.GONE);
        }
        
        // 摇杆左右选择：仅摇杆类型且为SDL控制器模式或鼠标模式时显示
        // - 鼠标模式：左摇杆=相对移动，右摇杆=八方向攻击（自动鼠标左键）
        // - SDL控制器模式：左摇杆=左摇杆输入，右摇杆=右摇杆输入
        View itemJoystickStickSelect = view.findViewById(R.id.item_joystick_stick_select);
        if (itemJoystickStickSelect != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            boolean isSDLControllerMode = (data.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER);
            boolean isMouseMode = (data.joystickMode == ControlData.JOYSTICK_MODE_MOUSE);
            itemJoystickStickSelect.setVisibility((isJoystick && (isSDLControllerMode || isMouseMode)) ? View.VISIBLE : View.GONE);
        }
        
        // 右摇杆攻击模式：仅摇杆类型且为鼠标模式且为右摇杆时显示
        View itemRightStickAttackMode = view.findViewById(R.id.item_right_stick_attack_mode);
        if (itemRightStickAttackMode != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            boolean isMouseMode = (data.joystickMode == ControlData.JOYSTICK_MODE_MOUSE);
            boolean isRightStick = data.xboxUseRightStick;
            itemRightStickAttackMode.setVisibility((isJoystick && isMouseMode && isRightStick) ? View.VISIBLE : View.GONE);
        }
        
        // 鼠标移动范围：仅摇杆类型且为鼠标模式且为右摇杆时显示
        View itemMouseRange = view.findViewById(R.id.item_mouse_range);
        if (itemMouseRange != null) {
            boolean isJoystick = (data.type == ControlData.TYPE_JOYSTICK);
            boolean isMouseMode = (data.joystickMode == ControlData.JOYSTICK_MODE_MOUSE);
            boolean isRightStick = data.xboxUseRightStick;
            itemMouseRange.setVisibility((isJoystick && isMouseMode && isRightStick) ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * 更新所有选项的可见性
     * @param basicInfoView 基本信息视图
     * @param appearanceView 外观视图
     * @param keymapView 键值设置视图
     * @param data 控件数据
     */
    public static void updateAllOptionsVisibility(@NonNull View basicInfoView,
                                                  @NonNull View appearanceView, 
                                                  @NonNull View keymapView,
                                                  @NonNull ControlData data) {
        updateBasicInfoOptionsVisibility(basicInfoView, data);
        updateAppearanceOptionsVisibility(appearanceView, data);
        updateKeymapOptionsVisibility(keymapView, data);
    }
    
    /**
     * 更新所有选项的可见性（兼容旧方法，不包含基本信息视图）
     * @param appearanceView 外观视图
     * @param keymapView 键值设置视图
     * @param data 控件数据
     */
    public static void updateAllOptionsVisibility(@NonNull View appearanceView, 
                                                  @NonNull View keymapView,
                                                  @NonNull ControlData data) {
        updateAppearanceOptionsVisibility(appearanceView, data);
        updateKeymapOptionsVisibility(keymapView, data);
    }
    
    /**
     * 根据规则设置视图可见性
     * @param view 视图
     * @param data 控件数据
     * @param rule 可见性规则
     */
    public static void setVisibilityByRule(@NonNull View view, 
                                          @NonNull ControlData data,
                                          @NonNull VisibilityRule rule) {
        if (view != null) {
            view.setVisibility(rule.shouldShow(data) ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * 创建基于控件类型的可见性规则
     */
    public static VisibilityRule createTypeRule(int targetType) {
        return data -> data.type == targetType;
    }
    
    /**
     * 创建基于控件形状的可见性规则
     */
    public static VisibilityRule createShapeRule(int targetShape) {
        return data -> data.shape == targetShape;
    }
    
    /**
     * 创建组合规则（AND）
     */
    public static VisibilityRule and(VisibilityRule... rules) {
        return data -> {
            for (VisibilityRule rule : rules) {
                if (!rule.shouldShow(data)) {
                    return false;
                }
            }
            return true;
        };
    }
    
    /**
     * 创建组合规则（OR）
     */
    public static VisibilityRule or(VisibilityRule... rules) {
        return data -> {
            for (VisibilityRule rule : rules) {
                if (rule.shouldShow(data)) {
                    return true;
                }
            }
            return false;
        };
    }
    
    /**
     * 创建取反规则（NOT）
     */
    public static VisibilityRule not(VisibilityRule rule) {
        return data -> !rule.shouldShow(data);
    }
}

