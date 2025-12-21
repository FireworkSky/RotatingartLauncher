package com.app.ralaunch.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.ralaunch.R;
import com.google.android.material.button.MaterialButton;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 现代化错误弹窗对话框
 * 
 * 特性：
 * - Material Design 3 风格
 * - 支持显示错误标题和消息
 * - 可展开的错误详情（包含堆栈跟踪）
 * - 复制错误信息到剪贴板
 * - 自动适配语言设置
 */
public class ErrorDialog extends LocalizedDialog {
    
    private String title;
    private String message;
    private String details;
    private boolean isFatal;
    
    private TextView titleView;
    private TextView messageView;
    private TextView detailsView;
    private View detailsContainer;
    private MaterialButton toggleDetailsButton;
    private MaterialButton copyButton;
    private MaterialButton okButton;
    private ImageView errorIcon;
    
    private boolean detailsExpanded = false;

    private ErrorDialog(@NonNull Context context) {
        super(context, R.style.ErrorDialogStyle);
    }

    /**
     * 创建错误对话框
     * 
     * @param context Context
     * @param title 错误标题
     * @param message 错误消息
     * @param throwable 异常对象（可选）
     * @param isFatal 是否为致命错误
     * @return ErrorDialog 实例
     */
    public static ErrorDialog create(@NonNull Context context, 
                                     @NonNull String title, 
                                     @NonNull String message,
                                     @Nullable Throwable throwable,
                                     boolean isFatal) {
        ErrorDialog dialog = new ErrorDialog(context);
        dialog.title = title;
        dialog.message = message;
        dialog.isFatal = isFatal;
        
        // 生成错误详情
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            dialog.details = sw.toString();
        } else {
            dialog.details = null;
        }
        
        return dialog;
    }

    /**
     * 创建错误对话框（使用异常的消息作为错误消息）
     */
    public static ErrorDialog create(@NonNull Context context, 
                                     @NonNull String title, 
                                     @NonNull Throwable throwable,
                                     boolean isFatal) {
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        return create(context, title, message, throwable, isFatal);
    }

    /**
     * 创建错误对话框（仅消息，无异常）
     */
    public static ErrorDialog create(@NonNull Context context, 
                                     @NonNull String title, 
                                     @NonNull String message) {
        return create(context, title, message, null, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 使用本地化的 Context
        Context localizedContext = getLocalizedContext();
        setContentView(R.layout.dialog_error);
        
        // 设置窗口属性
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // 初始化视图
        initViews();
        
        // 应用背景透明度
        try {
            float dialogAlpha = OpacityHelper.getDialogAlphaFromSettings(getContext());
            
            // 应用到根视图
            View rootView = findViewById(android.R.id.content);
            if (rootView instanceof android.view.ViewGroup) {
                 android.view.ViewGroup viewGroup = (android.view.ViewGroup) rootView;
                 if (viewGroup.getChildCount() > 0) {
                     viewGroup.getChildAt(0).setAlpha(dialogAlpha);
                 }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        // 设置内容
        setupContent();
        
        // 设置点击事件
        setupClickListeners();
    }

    private void initViews() {
        titleView = findViewById(R.id.error_title);
        messageView = findViewById(R.id.error_message);
        detailsView = findViewById(R.id.error_details);
        detailsContainer = findViewById(R.id.error_details_container);
        toggleDetailsButton = findViewById(R.id.btn_toggle_details);
        copyButton = findViewById(R.id.btn_copy);
        okButton = findViewById(R.id.btn_ok);
        errorIcon = findViewById(R.id.error_icon);
    }

    private void setupContent() {
        Context context = getLocalizedContext();
        
        // 设置标题
        if (titleView != null) {
            titleView.setText(title);
        }
        
        // 设置消息
        if (messageView != null) {
            messageView.setText(message);
        }
        
        // 设置错误详情
        if (details != null && !details.isEmpty() && detailsView != null) {
            detailsView.setText(details);
            // 如果有详情，显示切换按钮
            if (toggleDetailsButton != null) {
                toggleDetailsButton.setVisibility(View.VISIBLE);
            }
        } else {
            // 没有详情，隐藏切换按钮和详情容器
            if (toggleDetailsButton != null) {
                toggleDetailsButton.setVisibility(View.GONE);
            }
            if (detailsContainer != null) {
                detailsContainer.setVisibility(View.GONE);
            }
        }
        
    
        
    
        if (okButton != null) {
            okButton.setText(context.getString(R.string.ok));
        }
    }

    private void setupClickListeners() {
        Context context = getLocalizedContext();
        
        // 切换详情显示
        if (toggleDetailsButton != null) {
            toggleDetailsButton.setOnClickListener(v -> {
                detailsExpanded = !detailsExpanded;
                if (detailsContainer != null) {
                    detailsContainer.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
                }
                if (toggleDetailsButton != null) {
                    String text = detailsExpanded 
                        ? context.getString(R.string.error_hide_details)
                        : context.getString(R.string.error_show_details);
                    toggleDetailsButton.setText(text);
                }
            });
        }
        
        // 复制错误信息
        if (copyButton != null) {
            copyButton.setOnClickListener(v -> copyErrorToClipboard(context));
        }
        
        // 确定按钮
        if (okButton != null) {
            okButton.setOnClickListener(v -> {
                dismiss();
                // 如果是致命错误，可以考虑退出应用
                if (isFatal) {
                    if (getContext() instanceof android.app.Activity) {
                        android.app.Activity activity = (android.app.Activity) getContext();
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            activity.finishAffinity();
                        }
                    }
                }
            });
        }
    }

    private void copyErrorToClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return;
            }
            
            // 构建要复制的文本
            StringBuilder textToCopy = new StringBuilder();
            textToCopy.append(title).append("\n\n");
            textToCopy.append(message);
            
            if (details != null && !details.isEmpty()) {
                textToCopy.append("\n\n").append(context.getString(R.string.error_details_title)).append(":\n");
                textToCopy.append(details);
            }
            
            ClipData clip = ClipData.newPlainText("Error Details", textToCopy.toString());
            clipboard.setPrimaryClip(clip);
            
            // 显示复制成功提示
            Toast.makeText(context, context.getString(R.string.error_copy_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 复制失败时静默处理
            android.util.Log.e("ErrorDialog", "Failed to copy to clipboard", e);
        }
    }
}

