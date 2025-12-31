package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 控件类型管理器
 * 统一管理控件类型的选择和显示逻辑
 */
object ControlTypeManager {
    /**
     * 获取控件类型显示名称
     */
    fun getTypeDisplayName(context: Context, data: ControlData?): String {
        if (data == null) {
            return context.getString(R.string.control_unknown)
        }

        return when (data) {
            is ControlData.Joystick -> context.getString(R.string.control_type_joystick)
            is ControlData.Text -> context.getString(R.string.control_type_text)
            is ControlData.Button -> {
                // 按钮类型：区分键盘和手柄
                if (data.mode == ControlData.Button.Mode.GAMEPAD) {
                    context.getString(R.string.control_type_button_gamepad)
                } else {
                    context.getString(R.string.control_type_button_keyboard)
                }
            }
            is ControlData.TouchPad -> context.getString(R.string.control_type_touchpad)
        }
    }

    /**
     * 更新类型显示
     */
    fun updateTypeDisplay(context: Context?, data: ControlData?, textView: TextView?) {
        if (context == null || data == null || textView == null) {
            return
        }

        val typeName = getTypeDisplayName(context, data)
        textView.text = typeName
    }

    /**
     * 显示类型选择对话框
     */
    fun showTypeSelectDialog(
        context: Context,
        data: ControlData?,
        listener: OnTypeSelectedListener?
    ) {
        // TODO: 类型转换逻辑复杂，需创建新实例
        Toast.makeText(context, "类型转换功能待实现/Type Conversion is still WIP", Toast.LENGTH_SHORT).show()
        return

//        if (data == null) {
//            return
//        }
//
//        val types = arrayOf(
//            context.getString(R.string.control_type_button_keyboard),
//            context.getString(R.string.control_type_button_gamepad),
//            context.getString(R.string.control_type_joystick),
//            context.getString(R.string.control_type_touchpad),
//            context.getString(R.string.control_type_text)
//        )
//
//        // 确定当前选中的索引
//        val currentIndex = when (data) {
//            is ControlData.Joystick -> 2
//            is ControlData.Text -> 4
//            is ControlData.Button -> {
//                if (data.mode == ControlData.Button.Mode.GAMEPAD) 1 else 0
//            }
//            is ControlData.TouchPad -> 3
//        }
//
//        MaterialAlertDialogBuilder(context)
//            .setTitle(context.getString(R.string.editor_select_control_type))
//            .setSingleChoiceItems(types, currentIndex) { dialog, which ->
//                // Note: Type conversion is complex and may require creating new instances
//                // of the sealed classes. This is a simplified version that assumes
//                // the type can be changed in place (which may not be possible with sealed classes)
//                // TODO: Implement proper type conversion by creating new instances
//
//                when (which) {
//                    0 -> {
//                        // 按钮（键盘）
//                        if (data is ControlData.Button) {
//                            data.mode = ControlData.Button.Mode.KEYBOARD
//                        }
//                        // TODO: Convert other types to Button with KEYBOARD mode
//                    }
//                    1 -> {
//                        // 按钮（手柄）
//                        if (data is ControlData.Button) {
//                            data.mode = ControlData.Button.Mode.GAMEPAD
//                        }
//                        // TODO: Convert other types to Button with GAMEPAD mode
//                    }
//                    2 -> {
//                        // 摇杆
//                        // TODO: Convert to Joystick type
//                    }
//                    3 -> {
//                        // 触控板
//                        // TODO: Convert to TouchPad type
//                    }
//                    4 -> {
//                        // 文本
//                        if (data is ControlData.Text) {
//                            data.shape = ControlData.Text.Shape.RECTANGLE
//                            if (data.displayText.isEmpty()) {
//                                data.displayText = context.getString(R.string.control_type_text)
//                            }
//                        }
//                        // TODO: Convert other types to Text
//                    }
//                }
//
//                listener?.onTypeSelected(data)
//                dialog.dismiss()
//            }
//            .setNegativeButton(context.getString(R.string.cancel), null)
//            .show()
    }

    /**
     * 类型选择监听器
     */
    interface OnTypeSelectedListener {
        fun onTypeSelected(data: ControlData?)
    }
}

