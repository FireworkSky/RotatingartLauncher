package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.app.ralaunch.R
import com.app.ralaunch.controls.KeyMapper
import com.app.ralaunch.controls.configs.ControlData
import com.google.android.material.slider.Slider
import kotlin.math.max
import kotlin.math.min

/**
 * 控件编辑对话框数据填充管理器
 * 统一管理所有数据填充逻辑
 */
object ControlEditDialogDataFiller {
    /**
     * 填充基本信息数据
     */
    @JvmStatic
    fun fillBasicInfoData(view: View, refs: UIReferences) {
        val tvControlType = view.findViewById<TextView?>(R.id.tv_control_type)
        val tvControlShape = view.findViewById<TextView?>(R.id.tv_control_shape)
        val etName = view.findViewById<EditText?>(R.id.et_control_name)

        val data = refs.currentData ?: return

        ControlTypeManager.updateTypeDisplay(refs.context, data, tvControlType)
        ControlShapeManager.updateShapeDisplay(
            refs.context, data, tvControlShape,
            view.findViewById(R.id.item_control_shape)
        )

        // 摇杆模式显示（仅摇杆类型显示）
        val tvJoystickMode = view.findViewById<TextView?>(R.id.tv_joystick_mode)
        if (tvJoystickMode != null && data is ControlData.Joystick) {
            ControlJoystickModeManager.updateModeDisplay(refs.context, data, tvJoystickMode)
        }

        etName?.setText(data.name)

        // 文本内容编辑（仅文本控件显示）
        val etTextContent = view.findViewById<EditText?>(R.id.et_text_content)
        if (etTextContent != null && data is ControlData.Text) {
            etTextContent.setText(data.displayText)
        }

        // 更新基本信息选项的可见性
        ControlEditDialogVisibilityManager.updateAllOptionsVisibility(view, data)

        // 摇杆左右选择开关（仅摇杆类型且为SDL控制器模式或鼠标模式时显示）
        val switchJoystickStickSelect = view.findViewById<SwitchCompat?>(R.id.switch_joystick_stick_select)
        val tvJoystickStickSelect = view.findViewById<TextView?>(R.id.tv_joystick_stick_select)
        if (switchJoystickStickSelect != null && data is ControlData.Joystick) {
            switchJoystickStickSelect.isChecked = data.isRightStick
            // 更新显示文本
            tvJoystickStickSelect?.let {
                val text = if (data.isRightStick) {
                    refs.context.getString(R.string.editor_right_stick)
                } else {
                    refs.context.getString(R.string.editor_left_stick)
                }
                it.text = text
            }
        }

        // 触摸穿透开关
        val switchPassThrough = view.findViewById<SwitchCompat?>(R.id.switch_pass_through)
        switchPassThrough?.isChecked = data.isPassThrough
    }

    /**
     * 填充位置大小数据
     */
    @JvmStatic
    fun fillPositionSizeData(view: View, refs: UIReferences) {
        val data = refs.currentData ?: return

        // 摇杆大小
        val sliderJoystickSize = view.findViewById<Slider?>(R.id.seekbar_joystick_size)
        val tvJoystickSizeValue = view.findViewById<TextView?>(R.id.tv_joystick_size_value)

        val sliderPosX = view.findViewById<Slider?>(R.id.seekbar_pos_x)
        val tvPosXValue = view.findViewById<TextView?>(R.id.tv_pos_x_value)
        val sliderPosY = view.findViewById<Slider?>(R.id.seekbar_pos_y)
        val tvPosYValue = view.findViewById<TextView?>(R.id.tv_pos_y_value)
        val sliderWidth = view.findViewById<Slider?>(R.id.seekbar_width)
        val tvWidthValue = view.findViewById<TextView?>(R.id.tv_width_value)
        val sliderHeight = view.findViewById<Slider?>(R.id.seekbar_height)
        val tvHeightValue = view.findViewById<TextView?>(R.id.tv_height_value)
        val switchAutoSize = view.findViewById<SwitchCompat?>(R.id.switch_auto_size)

        // 填充摇杆大小数据（仅摇杆类型）
        if (sliderJoystickSize != null && data is ControlData.Joystick) {
            val sizePercent = max(1, min(100, (data.width * 100).toInt()))
            sliderJoystickSize.value = sizePercent.toFloat()
            tvJoystickSizeValue?.text = "$sizePercent%"
        }

        sliderPosX?.let {
            val xPercent = max(0, min(100, (data.x * 100).toInt()))
            it.value = xPercent.toFloat()
            tvPosXValue?.text = "$xPercent%"
        }

        sliderPosY?.let {
            val yPercent = max(0, min(100, (data.y * 100).toInt()))
            it.value = yPercent.toFloat()
            tvPosYValue?.text = "$yPercent%"
        }

        sliderWidth?.let {
            val widthPercent = max(1, min(100, (data.width * 100).toInt()))
            it.value = widthPercent.toFloat()
            tvWidthValue?.text = "$widthPercent%"
        }

        sliderHeight?.let {
            val heightPercent = max(1, min(100, (data.height * 100).toInt()))
            it.value = heightPercent.toFloat()
            tvHeightValue?.text = "$heightPercent%"
        }

        switchAutoSize?.let {
            it.isChecked = data.isSizeRatioLocked
        }
    }

    /**
     * 填充外观样式数据
     */
    @JvmStatic
    fun fillAppearanceData(view: View, refs: UIReferences) {
        val data = refs.currentData ?: return

        // 根据控件类型更新透明度标题和描述
        val tvOpacityTitle = view.findViewById<TextView?>(R.id.tv_opacity_title)
        val tvOpacityDesc = view.findViewById<TextView?>(R.id.tv_opacity_desc)
        val context = refs.context
        if (data is ControlData.Joystick) {
            tvOpacityTitle?.setText(context.getString(R.string.editor_joystick_bg_opacity))
            tvOpacityDesc?.setText(context.getString(R.string.editor_joystick_bg_opacity_desc))
        } else {
            tvOpacityTitle?.setText(context.getString(R.string.editor_opacity))
            tvOpacityDesc?.setText(context.getString(R.string.editor_opacity_desc))
        }

        // 透明度设置（使用统一管理器）
        ControlEditDialogSeekBarManager.fillSeekBarSetting(
            view,
            R.id.seekbar_opacity,
            R.id.tv_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                data,
                object : ControlEditDialogSeekBarManager.ValueSetter {
                    override fun get(data: ControlData?): Float = data?.opacity ?: 0.5f
                    override fun set(data: ControlData?, value: Float) {
                        data?.let { it.opacity = value }
                    }
                },
                object : ControlEditDialogSeekBarManager.UpdateNotifier {
                    override fun notifyUpdate() {} // 填充时不需要通知更新
                }
            )
        )

        // 边框透明度设置（使用统一管理器，完全独立）
        ControlEditDialogSeekBarManager.fillSeekBarSetting(
            view,
            R.id.seekbar_border_opacity,
            R.id.tv_border_opacity_value,
            ControlEditDialogSeekBarManager.createPercentConfig(
                data,
                object : ControlEditDialogSeekBarManager.ValueSetter {
                    override fun get(data: ControlData?): Float {
                        // 边框透明度完全独立，默认1.0（完全不透明）
                        return if (data != null && data.borderOpacity != 0f) data.borderOpacity else 1.0f
                    }

                    override fun set(data: ControlData?, value: Float) {
                        data?.let { it.borderOpacity = value }
                    }
                },
                object : ControlEditDialogSeekBarManager.UpdateNotifier {
                    override fun notifyUpdate() {} // 填充时不需要通知更新
                }
            )
        )

        // 文本透明度设置（使用统一管理器，仅按钮和文本控件显示）
        val cardTextOpacity = view.findViewById<View?>(R.id.card_text_opacity)
        if (cardTextOpacity != null && cardTextOpacity.visibility == View.VISIBLE) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(
                view,
                R.id.seekbar_text_opacity,
                R.id.tv_text_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    object : ControlEditDialogSeekBarManager.ValueSetter {
                        override fun get(data: ControlData?): Float {
                            // 文本透明度完全独立，默认1.0（完全不透明）
                            return if (data != null && data.textOpacity != 0f) data.textOpacity else 1.0f
                        }

                        override fun set(data: ControlData?, value: Float) {
                            data?.let { it.textOpacity = value }
                        }
                    },
                    object : ControlEditDialogSeekBarManager.UpdateNotifier {
                        override fun notifyUpdate() {} // 填充时不需要通知更新
                    }
                )
            )
        }

        val switchVisible = view.findViewById<SwitchCompat?>(R.id.switch_visible)
        switchVisible?.isChecked = data.isVisible

        // 更新外观选项的可见性（根据控件类型和形状）
        ControlEditDialogVisibilityManager.updateAllOptionsVisibility(view, data)

        // 圆角半径设置（仅在矩形形状时显示）
        val cardCornerRadius = view.findViewById<View?>(R.id.card_corner_radius)
        if (cardCornerRadius != null && cardCornerRadius.visibility == View.VISIBLE) {
            val sliderCornerRadius = view.findViewById<Slider?>(R.id.seekbar_corner_radius)
            val tvCornerRadiusValue = view.findViewById<TextView?>(R.id.tv_corner_radius_value)
            if (sliderCornerRadius != null && tvCornerRadiusValue != null) {
                val cornerRadius = max(
                    sliderCornerRadius.valueFrom,
                    min(sliderCornerRadius.valueTo, data.cornerRadius)
                ).toInt()
                sliderCornerRadius.value = cornerRadius.toFloat()
                tvCornerRadiusValue.text = "${cornerRadius}dp"
            }
        }

        // 摇杆中心透明度（使用统一管理器，仅在摇杆类型时显示）
        val cardStickOpacity = view.findViewById<View?>(R.id.card_stick_opacity)
        if (cardStickOpacity != null && cardStickOpacity.visibility == View.VISIBLE && data is ControlData.Joystick) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(
                view,
                R.id.seekbar_stick_opacity,
                R.id.tv_stick_opacity_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    object : ControlEditDialogSeekBarManager.ValueSetter {
                        override fun get(data: ControlData?): Float {
                            return if (data is ControlData.Joystick && data.stickOpacity != 0f) data.stickOpacity else 1.0f
                        }

                        override fun set(data: ControlData?, value: Float) {
                            if (data is ControlData.Joystick) {
                                data.stickOpacity = value
                            }
                        }
                    },
                    object : ControlEditDialogSeekBarManager.UpdateNotifier {
                        override fun notifyUpdate() {} // 填充时不需要通知更新
                    }
                )
            )
        }

        // 摇杆圆心大小（使用统一管理器，仅在摇杆类型时显示）
        val cardStickKnobSize = view.findViewById<View?>(R.id.card_stick_knob_size)
        if (cardStickKnobSize != null && cardStickKnobSize.visibility == View.VISIBLE && data is ControlData.Joystick) {
            ControlEditDialogSeekBarManager.fillSeekBarSetting(
                view,
                R.id.seekbar_stick_knob_size,
                R.id.tv_stick_knob_size_value,
                ControlEditDialogSeekBarManager.createPercentConfig(
                    data,
                    object : ControlEditDialogSeekBarManager.ValueSetter {
                        override fun get(data: ControlData?): Float {
                            return if (data is ControlData.Joystick && data.stickKnobSize != 0f) data.stickKnobSize else 0.4f
                        }

                        override fun set(data: ControlData?, value: Float) {
                            if (data is ControlData.Joystick) {
                                data.stickKnobSize = value
                            }
                        }
                    },
                    object : ControlEditDialogSeekBarManager.UpdateNotifier {
                        override fun notifyUpdate() {} // 填充时不需要通知更新
                    }
                )
            )
        }

        // 更新颜色视图
        val viewBgColor = view.findViewById<View?>(R.id.view_bg_color)
        val viewStrokeColor = view.findViewById<View?>(R.id.view_stroke_color)
        viewBgColor?.let {
            ControlColorManager.updateColorView(it, data.bgColor, 8f, 2f)
        }
        viewStrokeColor?.let {
            ControlColorManager.updateColorView(it, data.strokeColor, 8f, 2f)
        }
    }

    /**
     * 填充键值设置数据
     */
    @JvmStatic
    fun fillKeymapData(view: View, refs: UIReferences) {
        val data = refs.currentData ?: return

        // 更新键值设置选项的可见性（根据控件类型）
        ControlEditDialogVisibilityManager.updateAllOptionsVisibility(view, data)

        // 填充按钮键值数据
        fillButtonKeymap(view, data)
    }

    /**
     * 填充普通按钮键值数据
     */
    private fun fillButtonKeymap(view: View, data: ControlData) {
        val tvKeyName = view.findViewById<TextView?>(R.id.tv_key_name)
        if (tvKeyName != null && data is ControlData.Button) {
            val keyName = KeyMapper.getKeyName(data.keycode)
            tvKeyName.text = keyName
        }

        val switchToggleMode = view.findViewById<SwitchCompat?>(R.id.switch_toggle_mode)
        if (switchToggleMode != null && data is ControlData.Button) {
            switchToggleMode.isChecked = data.isToggle
        }
    }

    /**
     * UI元素引用接口
     */
    interface UIReferences {
        val currentData: ControlData?
        val screenWidth: Int
        val screenHeight: Int
        val context: Context
    }
}

