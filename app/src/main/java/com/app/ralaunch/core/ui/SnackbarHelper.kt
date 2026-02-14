package com.app.ralaunch.core.ui

import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Material Design 3 Snackbar 辅助工具
 */
object SnackbarHelper {

    private const val DEFAULT_ANIMATION_MODE = BaseTransientBottomBar.ANIMATION_MODE_SLIDE

    enum class Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    @JvmStatic
    fun showSuccess(rootView: View, message: String) {
        show(rootView, message, Type.SUCCESS, Snackbar.LENGTH_SHORT, null, null)
    }

    @JvmStatic
    fun showError(rootView: View, message: String) {
        show(rootView, message, Type.ERROR, Snackbar.LENGTH_LONG, null, null)
    }

    @JvmStatic
    fun showInfo(rootView: View, message: String) {
        show(rootView, message, Type.INFO, Snackbar.LENGTH_SHORT, null, null)
    }

    @JvmStatic
    fun showWarning(rootView: View, message: String) {
        show(rootView, message, Type.WARNING, Snackbar.LENGTH_LONG, null, null)
    }

    @JvmStatic
    fun showWithAction(
        rootView: View,
        message: String,
        type: Type,
        actionText: String,
        actionListener: View.OnClickListener
    ) {
        show(rootView, message, type, Snackbar.LENGTH_LONG, actionText, actionListener)
    }

    private fun show(
        rootView: View?,
        message: String?,
        type: Type,
        duration: Int,
        actionText: String?,
        actionListener: View.OnClickListener?
    ) {
        if (rootView == null || message == null) return

        val snackbar = Snackbar.make(rootView, message, duration)
        snackbar.animationMode = DEFAULT_ANIMATION_MODE

        // 设置操作按钮
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText) { v ->
                actionListener.onClick(v)
            }
        }

        snackbar.show()
    }
}
