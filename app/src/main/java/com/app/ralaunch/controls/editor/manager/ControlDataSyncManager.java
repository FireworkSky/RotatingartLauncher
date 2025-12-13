package com.app.ralaunch.controls.editor.manager;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlLayout;
import com.app.ralaunch.controls.ControlView;

/**
 * 控件数据同步管理器
 * 统一管理 ControlData 到 ControlView 的同步逻辑
 */
public class ControlDataSyncManager {

    /**
     * 同步控件数据到视图
     * @param layout 控件布局
     * @param controlData 要同步的控件数据
     * @return 是否成功同步
     */
    public static boolean syncControlDataToView(ControlLayout layout, ControlData controlData) {
        if (layout == null || controlData == null) {
            return false;
        }

        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof ControlView) {
                ControlView controlView = (ControlView) child;
                ControlData viewData = controlView.getData();
                
                // 优先使用引用比较，确保匹配到正确的控件
                // 只有在引用不同时才进行名称比较（作为后备方案）
                boolean isMatch = false;
                if (viewData == controlData) {
                    // 引用匹配 - 最可靠的方式
                    isMatch = true;
                } else if (viewData != null && controlData != null) {
                    // 引用不同，尝试名称匹配（仅作为后备，可能存在名称重复的情况）
                    // 但这里我们更倾向于使用引用匹配，所以只在确实需要时才使用名称匹配
                    // 实际上，我们应该避免使用名称匹配，因为名称可能被用户修改
                    // 为了安全，我们只在引用匹配时才更新
                    isMatch = false; // 禁用名称匹配，避免匹配到错误的控件
                }
                
                if (isMatch) {
                    // 更新布局参数
                    ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
                        frameParams.width = (int) controlData.width;
                        frameParams.height = (int) controlData.height;
                        frameParams.leftMargin = (int) controlData.x;
                        frameParams.topMargin = (int) controlData.y;
                        child.setLayoutParams(frameParams);
                    }
                    
                    // 更新视觉属性
                    // 对于摇杆，不设置 View 级别的 alpha，因为它们有独立的透明度控制
                    // View 级别的 alpha 会影响所有绘制内容，导致透明度叠加
                    if (controlData.type == ControlData.TYPE_JOYSTICK) {
                        // 摇杆：View alpha 始终为 1.0，让内部的 Paint alpha 独立控制
                        child.setAlpha(1.0f);
                    } else {
                        // 按钮和文本：可以使用 View 级别的 alpha
                        child.setAlpha(controlData.opacity);
                    }
                    child.setVisibility(controlData.visible ? View.VISIBLE : View.INVISIBLE);
                    
                    // 同步所有字段到原始数据对象，确保数据一致性
                    if (viewData != null) {
                        syncDataFields(viewData, controlData);
                    }
                    
                    // 刷新控件绘制
                    controlView.updateData(controlData);
                    child.invalidate();
                    
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * 同步数据字段
     * 将源数据的所有字段同步到目标数据对象
     * 注意：如果 target 和 source 是同一个对象，则不需要同步（避免不必要的操作）
     */
    private static void syncDataFields(ControlData target, ControlData source) {
        if (target == null || source == null) {
            return;
        }
        
        // 如果 target 和 source 是同一个对象，不需要同步
        if (target == source) {
            return;
        }
        
        // 同步所有关键字段
        target.type = source.type;
        target.buttonMode = source.buttonMode;
        target.shape = source.shape;
        target.keycode = source.keycode;
        target.isToggle = source.isToggle;
        target.opacity = source.opacity;
        target.borderOpacity = source.borderOpacity;
        target.textOpacity = source.textOpacity;
        target.visible = source.visible;
        target.bgColor = source.bgColor;
        target.strokeColor = source.strokeColor;
        target.strokeWidth = source.strokeWidth;
        target.cornerRadius = source.cornerRadius;
        target.name = source.name;
        // 位置信息必须同步，因为用户可能拖动过控件
        target.x = source.x;
        target.y = source.y;
        target.width = source.width;
        target.height = source.height;
        
        // 同步摇杆特有属性
        if (source.type == ControlData.TYPE_JOYSTICK) {
            target.stickOpacity = source.stickOpacity;
            target.stickKnobSize = source.stickKnobSize;
            if (source.joystickKeys != null) {
                target.joystickKeys = source.joystickKeys.clone();
            }
            target.joystickMode = source.joystickMode;
            target.xboxUseRightStick = source.xboxUseRightStick;
        }
        
        // 同步文本控件特有属性
        if (source.type == ControlData.TYPE_TEXT) {
            // 对于文本控件，允许 displayText 为空字符串
            // 如果 source.displayText 为 null，使用空字符串而不是 source.name
            target.displayText = source.displayText != null ? source.displayText : "";
        }
    }
}

