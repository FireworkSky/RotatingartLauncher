package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.view.View
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 控件形状管理器
 * 统一管理控件形状的选择和显示逻辑
 */
object ControlShapeManager {
    /**
     * 获取形状显示名称
     */
    fun getShapeDisplayName(context: Context, data: ControlData?): String {
        if (data == null) {
            return context.getString(R.string.control_shape_rectangle)
        }

        return when (data) {
            is ControlData.Button -> {
                if (data.shape == ControlData.Button.Shape.CIRCLE) {
                    context.getString(R.string.control_shape_circle)
                } else {
                    context.getString(R.string.control_shape_rectangle)
                }
            }
            is ControlData.Text -> {
                if (data.shape == ControlData.Text.Shape.CIRCLE) {
                    context.getString(R.string.control_shape_circle)
                } else {
                    context.getString(R.string.control_shape_rectangle)
                }
            }
            else -> context.getString(R.string.control_shape_rectangle)
        }
    }

    /**
     * 更新形状显示
     */
    fun updateShapeDisplay(
        context: Context?,
        data: ControlData?,
        textView: TextView?,
        shapeItemView: View?
    ) {
        if (context == null || data == null) {
            return
        }

        // 按钮和文本控件都支持形状选择
        val supportsShape = data is ControlData.Button || data is ControlData.Text

        if (supportsShape) {
            shapeItemView?.visibility = View.VISIBLE
            if (textView != null) {
                val shapeName = getShapeDisplayName(context, data)
                textView.text = shapeName
            }
        } else {
            shapeItemView?.visibility = View.GONE
        }
    }

    /**
     * 显示形状选择对话框
     */
    fun showShapeSelectDialog(
        context: Context,
        data: ControlData?,
        listener: OnShapeSelectedListener?
    ) {
        if (data == null) {
            return
        }

        // 按钮和文本控件都支持形状选择
        val supportsShape = data is ControlData.Button || data is ControlData.Text
        if (!supportsShape) {
            return
        }

        val shapes = arrayOf(
            context.getString(R.string.control_shape_rectangle),
            context.getString(R.string.control_shape_circle)
        )

        val currentIndex = when (data) {
            is ControlData.Button -> if (data.shape == ControlData.Button.Shape.CIRCLE) 1 else 0
            is ControlData.Text -> if (data.shape == ControlData.Text.Shape.CIRCLE) 1 else 0
            else -> 0
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.control_select_shape))
            .setSingleChoiceItems(shapes, currentIndex) { dialog, which ->
                when (data) {
                    is ControlData.Button -> {
                        data.shape = if (which == 1) {
                            ControlData.Button.Shape.CIRCLE
                        } else {
                            ControlData.Button.Shape.RECTANGLE
                        }
                    }
                    is ControlData.Text -> {
                        data.shape = if (which == 1) {
                            ControlData.Text.Shape.CIRCLE
                        } else {
                            ControlData.Text.Shape.RECTANGLE
                        }
                    }
                    else -> {
                        // Should not happen as we check supportsShape above
                    }
                }

                listener?.onShapeSelected(data)
                dialog.dismiss()
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    /**
     * 形状选择监听器
     */
    interface OnShapeSelectedListener {
        fun onShapeSelected(data: ControlData?)
    }
}

