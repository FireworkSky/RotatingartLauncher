package com.app.ralaunch.controls.editors.managers

import android.view.View
import com.app.ralaunch.controls.configs.ControlData

/**
 * 控件编辑对话框可见性管理器
 * 使用统一的规则容器管理各个选项的可见性逻辑
 */
object ControlEditDialogVisibilityManager {

    //region Helper Classes

    /**
     * 可见性规则
     * @param isVisible 判断是否可见的lambda函数
     * @param resourceIds 需要应用该规则的资源ID列表
     */
    data class Rule(
        val isVisible: (ControlData) -> Boolean,
        val resourceIds: List<Int>
    )

    /**
     * 规则容器
     * 统一管理所有可见性规则
     */
    class RuleContainer {
        private val rules = mutableListOf<Rule>()

        /**
         * 添加规则
         */
        fun addRule(rule: Rule): RuleContainer {
            rules.add(rule)
            return this
        }

        /**
         * 添加规则（便捷方法）
         */
        fun addRule(
            isVisible: (ControlData) -> Boolean,
            vararg resourceIds: Int
        ): RuleContainer {
            return addRule(Rule(isVisible, resourceIds.toList()))
        }

        /**
         * 应用所有规则到视图
         */
        fun applyRules(view: View, data: ControlData) {
            rules.forEach { rule ->
                val visible = rule.isVisible(data)
                val visibility = if (visible) View.VISIBLE else View.GONE

                rule.resourceIds.forEach { resourceId ->
                    view.findViewById<View?>(resourceId)?.visibility = visibility
                }
            }
        }

        /**
         * 清空所有规则
         */
        fun clear() {
            rules.clear()
        }
    }

    //endregion

    /**
     * 规则容器实例
     */
    private val ruleContainer = RuleContainer().apply {
        // 圆角半径（仅在矩形形状时显示）
        addRule(
            {
                when (it) {
                    is ControlData.Button -> it.shape == ControlData.Button.Shape.RECTANGLE
                    is ControlData.Text -> it.shape == ControlData.Text.Shape.RECTANGLE
                    else -> false
                }
            },
            com.app.ralaunch.R.id.card_corner_radius
        )

        // 摇杆中心透明度（仅摇杆类型显示）
        addRule(
            { it is ControlData.Joystick },
            com.app.ralaunch.R.id.card_stick_opacity
        )

        // 摇杆圆心大小（仅摇杆类型显示）
        addRule(
            { it is ControlData.Joystick },
            com.app.ralaunch.R.id.card_stick_knob_size
        )

        // 形状选择（仅按钮和文本控件显示）
        addRule(
            { it is ControlData.Button || it is ControlData.Text },
            com.app.ralaunch.R.id.item_control_shape
        )

        // 摇杆模式选择（仅摇杆类型显示）
        addRule(
            { it is ControlData.Joystick },
            com.app.ralaunch.R.id.item_joystick_mode
        )

        // 文本内容编辑（仅文本控件显示）
        addRule(
            { it is ControlData.Text },
            com.app.ralaunch.R.id.item_text_content
        )

        // 普通键值设置（仅按钮和文本控件显示）
        addRule(
            { it is ControlData.Button || it is ControlData.Text },
            com.app.ralaunch.R.id.item_key_mapping
        )

        // 摇杆键值设置（仅摇杆显示，且为键盘模式）
        addRule(
            {
                it is ControlData.Joystick && it.mode == ControlData.Joystick.Mode.KEYBOARD
            },
            com.app.ralaunch.R.id.item_joystick_key_mapping
        )

        // 切换模式（仅按钮显示）
        addRule(
            { it is ControlData.Button },
            com.app.ralaunch.R.id.item_toggle_mode
        )

        // 文本透明度（按钮和文本控件显示）
        addRule(
            { it is ControlData.Button || it is ControlData.Text },
            com.app.ralaunch.R.id.card_text_opacity
        )

        // 右摇杆攻击模式（仅摇杆鼠标模式显示）
        addRule(
            {
                it is ControlData.Joystick && it.mode == ControlData.Joystick.Mode.MOUSE
            },
            com.app.ralaunch.R.id.item_right_stick_attack_mode
        )

        // 鼠标范围和速度（摇杆鼠标模式或触摸板显示）
        addRule(
            {
                (it is ControlData.Joystick && it.mode == ControlData.Joystick.Mode.MOUSE) ||
                it is ControlData.TouchPad
            },
            com.app.ralaunch.R.id.item_mouse_range
        )

        // 摇杆左右选择（仅在手柄模式或鼠标模式时显示）
        addRule(
            {
                it is ControlData.Joystick && (
                    it.mode == ControlData.Joystick.Mode.GAMEPAD ||
                    it.mode == ControlData.Joystick.Mode.MOUSE
                )
            },
            com.app.ralaunch.R.id.item_joystick_stick_select
        )
    }

    /**
     * 更新所有选项的可见性
     */
    fun updateAllOptionsVisibility(view: View, data: ControlData) {
        ruleContainer.applyRules(view, data)
    }
}

