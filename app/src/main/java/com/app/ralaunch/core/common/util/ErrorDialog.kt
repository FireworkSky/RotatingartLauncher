package com.app.ralaunch.core.common.util

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.app.ralaunch.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 错误弹窗对话框 - 使用 MaterialAlertDialogBuilder
 * 
 * 保留静态工厂方法以兼容 ErrorHandler 的反射调用
 */
class ErrorDialog private constructor(
    private val context: Context,
    private val title: String,
    private val message: String,
    private val details: String?,
    private val isFatal: Boolean
) {
    
    private var dialog: Dialog? = null
    private var detailsExpanded = false
    
    fun show() {
        val localizedContext = LocaleManager.applyLanguage(context) ?: context
        
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setCancelable(!isFatal)
        
        // 构建消息内容
        val displayMessage = if (detailsExpanded && !details.isNullOrEmpty()) {
            "$message\n\n${localizedContext.getString(R.string.error_details_title)}:\n$details"
        } else {
            message
        }
        builder.setMessage(displayMessage)
        
        // 确定按钮
        builder.setPositiveButton(localizedContext.getString(R.string.ok)) { _, _ ->
            if (isFatal) {
                (context as? Activity)?.takeUnless { it.isFinishing || it.isDestroyed }?.finishAffinity()
            }
        }
        
        // 复制按钮
        if (!details.isNullOrEmpty()) {
            builder.setNeutralButton(localizedContext.getString(R.string.error_copy)) { _, _ ->
                copyErrorToClipboard(localizedContext)
                // 显示后重新弹出对话框
                show()
            }
            
            // 显示/隐藏详情按钮
            val toggleText = localizedContext.getString(
                if (detailsExpanded) R.string.error_hide_details else R.string.error_show_details
            )
            builder.setNegativeButton(toggleText) { _, _ ->
                detailsExpanded = !detailsExpanded
                show()
            }
        }
        
        dialog = builder.create()
        dialog?.show()
    }
    
    fun dismiss() {
        dialog?.dismiss()
    }
    
    private fun copyErrorToClipboard(localizedContext: Context?) {
        val ctx = localizedContext ?: context
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager 
                ?: return
            val text = buildString {
                append(title).append("\n\n")
                append(message)
                if (!details.isNullOrEmpty()) {
                    append("\n\n").append(ctx.getString(R.string.error_details_title)).append(":\n")
                    append(details)
                }
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.error_details_title), text))
            Toast.makeText(context, ctx.getString(R.string.error_copy_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ErrorDialog", "Failed to copy to clipboard", e)
        }
    }
    
    companion object {
        @JvmStatic
        fun create(
            context: Context,
            title: String,
            message: String,
            throwable: Throwable? = null,
            isFatal: Boolean = false
        ): ErrorDialog {
            val details = throwable?.let {
                StringWriter().also { sw -> it.printStackTrace(PrintWriter(sw)) }.toString()
            }
            return ErrorDialog(context, title, message, details, isFatal)
        }
        
        @JvmStatic
        fun create(context: Context, title: String, throwable: Throwable, isFatal: Boolean = false): ErrorDialog {
            val message = throwable.message?.takeIf { it.isNotEmpty() } ?: throwable.javaClass.simpleName
            return create(context, title, message, throwable, isFatal)
        }
        
        @JvmStatic
        fun create(context: Context, title: String, message: String): ErrorDialog {
            return ErrorDialog(context, title, message, null, false)
        }
    }
}
