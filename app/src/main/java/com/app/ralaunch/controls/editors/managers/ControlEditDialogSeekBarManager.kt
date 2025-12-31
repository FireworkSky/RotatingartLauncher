package com.app.ralaunch.controls.editors.managers

import android.view.View
import android.widget.TextView
import com.app.ralaunch.controls.data.ControlData
import com.google.android.material.slider.Slider
import kotlin.math.max
import kotlin.math.min

/**
 * Slider 设置项统一管理器 (MD3 风格)
 * 统一管理所有 Slider 类型的设置项，避免重复代码
 */
object ControlEditDialogSeekBarManager {
    /**
     * 绑定 Slider 设置项
     * @param view 父视图
     * @param sliderId Slider 的 ID
     * @param textViewId 显示值的 TextView 的 ID
     * @param config 配置接口
     */
    fun bindSeekBarSetting(
        view: View,
        sliderId: Int,
        textViewId: Int,
        config: SeekBarConfig
    ) {
        val slider = view.findViewById<Slider?>(sliderId)
        val textView = view.findViewById<TextView?>(textViewId)

        if (slider == null || textView == null) {
            return
        }


        // 清除旧的监听器，避免重复绑定导致的问题
        slider.clearOnChangeListeners()


        // 设置初始值（从配置中获取当前值，确保使用最新的数据）
        var currentValue = config.currentValue
        // 确保值在有效范围内，防止 Slider 崩溃
        currentValue = max(0f, min(1f, currentValue))
        var sliderValue = currentValue * config.maxValue
        // 确保 sliderValue 在 Slider 的 valueFrom 和 valueTo 范围内
        sliderValue = max(slider.getValueFrom(), min(slider.getValueTo(), sliderValue))
        slider.setValue(sliderValue)
        textView.setText(config.getDisplayText(currentValue))


        // 设置监听器
        slider.addOnChangeListener(Slider.OnChangeListener { s: Slider?, value: Float, fromUser: Boolean ->
            if (fromUser) {
                val normalizedValue = value / config.maxValue
                textView.setText(config.getDisplayText(normalizedValue))
                config.setValue(normalizedValue)
                config.notifyUpdate()
            }
        })
    }

    /**
     * 填充 Slider 设置项的值
     * @param view 父视图
     * @param sliderId Slider 的 ID
     * @param textViewId 显示值的 TextView 的 ID
     * @param config 配置接口
     */
    fun fillSeekBarSetting(
        view: View,
        sliderId: Int,
        textViewId: Int,
        config: SeekBarConfig
    ) {
        val slider = view.findViewById<Slider?>(sliderId)
        val textView = view.findViewById<TextView?>(textViewId)

        if (slider == null || textView == null) {
            return
        }

        var currentValue = config.currentValue
        // 确保值在有效范围内，防止 Slider 崩溃
        currentValue = max(0f, min(1f, currentValue))
        var sliderValue = currentValue * config.maxValue
        // 确保 sliderValue 在 Slider 的 valueFrom 和 valueTo 范围内
        sliderValue = max(slider.getValueFrom(), min(slider.getValueTo(), sliderValue))
        slider.setValue(sliderValue)
        textView.setText(config.getDisplayText(currentValue))
    }

    /**
     * 创建百分比类型的配置（0.0-1.0 映射到 0-100）
     */
    fun createPercentConfig(
        data: ControlData,
        setter: ValueSetter,
        notifier: UpdateNotifier
    ): SeekBarConfig {
        return object : SeekBarConfig {
            override val currentValue: Float
                get() = setter.get(data)

            override val maxValue: Int
                get() = 100

            override fun setValue(value: Float) {
                setter.set(data, value)
            }

            override fun getDisplayText(value: Float): String {
                return (value * 100).toInt().toString() + "%"
            }


            override fun notifyUpdate() {
                notifier.notifyUpdate()
            }
        }
    }

    /**
     * 创建整数类型的配置（直接使用整数值）
     */
    fun createIntConfig(
        data: ControlData,
        setter: IntValueSetter,
        notifier: UpdateNotifier,
        maxValue: Int
    ): SeekBarConfig {
        return object : SeekBarConfig {
            override val currentValue: Float
                get() = setter.get(data).toFloat()

            override val maxValue: Int
                get() = maxValue

            override fun setValue(value: Float) {
                setter.set(data, value.toInt())
            }

            override fun getDisplayText(value: Float): String {
                return value.toInt().toString()
            }


            override fun notifyUpdate() {
                notifier.notifyUpdate()
            }
        }
    }

    /**
     * Slider 配置接口
     */
    interface SeekBarConfig {
        /**
         * 获取当前值（0.0-1.0 或具体数值）
         */
        val currentValue: Float

        /**
         * 设置新值
         */
        fun setValue(value: Float)

        /**
         * 获取显示文本
         */
        fun getDisplayText(value: Float): String?

        /**
         * 获取最大值（用于百分比类型，通常是100）
         */
        val maxValue: Int

        /**
         * 是否需要通知更新
         */
        fun notifyUpdate()
    }

    /**
     * 值获取和设置接口（百分比类型）
     */
    interface ValueSetter {
        fun get(data: ControlData?): Float
        fun set(data: ControlData?, value: Float)
    }

    /**
     * 值获取和设置接口（整数类型）
     */
    interface IntValueSetter {
        fun get(data: ControlData?): Int
        fun set(data: ControlData?, value: Int)
    }

    /**
     * 更新通知接口
     */
    interface UpdateNotifier {
        fun notifyUpdate()
    }
}
