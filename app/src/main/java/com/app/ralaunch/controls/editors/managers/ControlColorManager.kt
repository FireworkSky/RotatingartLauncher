package com.app.ralaunch.controls.editors.managers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.app.ralaunch.controls.data.ControlData
import com.app.ralib.dialog.ColorPickerDialog
import com.google.android.material.R

/**
 * 控件颜色管理器
 */
object ControlColorManager {
    /**
     * 将 dp 转换为 px
     */
    private fun dpToPx(context: Context, dp: Float): Float {
        val metrics = context.resources.displayMetrics
        return dp * metrics.density
    }

    /**
     * 更新颜色视图显示
     */
    @JvmStatic
    fun updateColorView(colorView: View?, color: Int, cornerRadiusDp: Float, strokeWidthDp: Float) {
        if (colorView == null) {
            return
        }

        val context = colorView.context
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dpToPx(context, cornerRadiusDp)
        // 使用主题颜色作为边框，支持暗色模式
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorOutline, typedValue, true)
        drawable.setStroke(dpToPx(context, strokeWidthDp).toInt(), typedValue.data)
        colorView.background = drawable
    }

    /**
     * 显示颜色选择对话框
     */
    @JvmStatic
    fun showColorPickerDialog(
        context: Context,
        data: ControlData?,
        isBackground: Boolean,
        listener: OnColorSelectedListener?
    ) {
        if (data == null) {
            return
        }

        // 获取 FragmentActivity 和 FragmentManager
        var activity: FragmentActivity? = null
        if (context is FragmentActivity) {
            activity = context
        } else if (context is ContextThemeWrapper) {
            val baseContext = context.baseContext
            if (baseContext is FragmentActivity) {
                activity = baseContext
            }
        }

        if (activity == null) {
            // 如果无法获取 FragmentActivity，无法显示调色盘对话框
            return
        }

        val fragmentManager = activity.supportFragmentManager


        // 获取当前颜色
        val currentColor = if (isBackground) data.bgColor else data.strokeColor

        // 创建调色盘对话框
        val colorPickerDialog = ColorPickerDialog.newInstance(currentColor)


        // 设置标题
        val title =
            if (isBackground) context.getString(com.app.ralaunch.R.string.editor_select_bg_color) else context.getString(
                com.app.ralaunch.R.string.editor_select_border_color
            )
        colorPickerDialog.setTitle(title)


        // 设置颜色选择监听器
        colorPickerDialog.setOnColorSelectedListener { color: Int ->
            if (isBackground) {
                data.bgColor = color
            } else {
                data.strokeColor = color
            }
            listener?.onColorSelected(data, color, isBackground)
        }

        // 显示对话框
        colorPickerDialog.show(fragmentManager, "ColorPickerDialog")
    }


    /**
     * 颜色选择监听器
     */
    interface OnColorSelectedListener {
        fun onColorSelected(data: ControlData?, color: Int, isBackground: Boolean)
    }
}

