package com.app.ralib.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.ralib.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 现代化错误弹窗组件
 * 特性：
 * - Material Design 风格
 * - 支持堆栈跟踪显示/隐藏
 * - 一键复制错误信息
 * - 支持自定义操作按钮
 * - 集成日志系统
 */
public class ErrorDialog extends DialogFragment {

    private TextView tvErrorTitle;
    private TextView tvErrorMessage;
    private TextView tvStackTrace;
    private ImageView ivErrorIcon;
    private ImageButton btnClose;
    private MaterialButton btnCopy;
    private MaterialButton btnDetails;
    private MaterialButton btnCustomAction;
    private View stackTraceContainer;

    private String errorTitle = "错误";
    private String errorMessage = "发生了一个错误";
    private Throwable throwable;
    private String stackTraceText;
    private boolean showStackTrace = false;
    private ErrorSeverity severity = ErrorSeverity.ERROR;

    private String customActionText;
    private OnActionClickListener customActionListener;
    private OnDismissListener onDismissListener;

    /**
     * 错误严重程度
     */
    public enum ErrorSeverity {
        WARNING(R.drawable.ralib_ic_warning, R.color.ralib_warning_color, "警告"),
        ERROR(R.drawable.ralib_ic_error, R.color.ralib_error_color, "错误"),
        FATAL(R.drawable.ralib_ic_fatal, R.color.ralib_fatal_color, "严重错误");

        final int iconRes;
        final int colorRes;
        final String defaultTitle;

        ErrorSeverity(int iconRes, int colorRes, String defaultTitle) {
            this.iconRes = iconRes;
            this.colorRes = colorRes;
            this.defaultTitle = defaultTitle;
        }
    }

    /**
     * 自定义操作监听器
     */
    public interface OnActionClickListener {
        void onActionClick();
    }

    /**
     * 对话框关闭监听器
     */
    public interface OnDismissListener {
        void onDismiss();
    }

    public ErrorDialog() {
        // Required empty constructor
    }

    /**
     * 创建错误对话框（快捷方法）
     */
    public static ErrorDialog create(String title, String message) {
        return new ErrorDialog()
                .setErrorTitle(title)
                .setErrorMessage(message);
    }

    /**
     * 创建错误对话框（带异常）
     */
    public static ErrorDialog create(String title, Throwable throwable) {
        return new ErrorDialog()
                .setErrorTitle(title)
                .setThrowable(throwable);
    }

    /**
     * 创建警告对话框
     */
    public static ErrorDialog createWarning(String title, String message) {
        return new ErrorDialog()
                .setSeverity(ErrorSeverity.WARNING)
                .setErrorTitle(title)
                .setErrorMessage(message);
    }

    /**
     * 创建致命错误对话框
     */
    public static ErrorDialog createFatal(String title, Throwable throwable) {
        return new ErrorDialog()
                .setSeverity(ErrorSeverity.FATAL)
                .setErrorTitle(title)
                .setThrowable(throwable);
    }

    // Builder 方法
    public ErrorDialog setErrorTitle(String title) {
        this.errorTitle = title;
        return this;
    }

    public ErrorDialog setErrorMessage(String message) {
        this.errorMessage = message;
        return this;
    }

    public ErrorDialog setThrowable(Throwable throwable) {
        this.throwable = throwable;
        if (throwable != null) {
            this.errorMessage = throwable.getMessage() != null
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
            this.stackTraceText = getStackTraceString(throwable);
        }
        return this;
    }

    public ErrorDialog setSeverity(ErrorSeverity severity) {
        this.severity = severity;
        if (errorTitle.equals("错误")) {
            this.errorTitle = severity.defaultTitle;
        }
        return this;
    }

    public ErrorDialog setCustomAction(String actionText, OnActionClickListener listener) {
        this.customActionText = actionText;
        this.customActionListener = listener;
        return this;
    }

    public ErrorDialog setOnDismissListener(OnDismissListener listener) {
        this.onDismissListener = listener;
        return this;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.ralib_dialog_error, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化视图
        MaterialCardView cardView = view.findViewById(R.id.errorCardView);
        tvErrorTitle = view.findViewById(R.id.tvErrorTitle);
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        tvStackTrace = view.findViewById(R.id.tvStackTrace);
        ivErrorIcon = view.findViewById(R.id.ivErrorIcon);
        btnClose = view.findViewById(R.id.btnClose);
        btnCopy = view.findViewById(R.id.btnCopy);
        btnDetails = view.findViewById(R.id.btnDetails);
        btnCustomAction = view.findViewById(R.id.btnCustomAction);
        stackTraceContainer = view.findViewById(R.id.stackTraceContainer);

        // 应用严重程度样式
        if (getContext() != null) {
            ivErrorIcon.setImageResource(severity.iconRes);
            ivErrorIcon.setColorFilter(getContext().getColor(severity.colorRes));
            cardView.setStrokeColor(getContext().getColor(severity.colorRes));
        }

        // 设置内容
        tvErrorTitle.setText(errorTitle);
        tvErrorMessage.setText(errorMessage);

        // 堆栈跟踪
        if (stackTraceText != null && !stackTraceText.isEmpty()) {
            tvStackTrace.setText(stackTraceText);
            btnDetails.setVisibility(View.VISIBLE);
        } else {
            btnDetails.setVisibility(View.GONE);
        }

        // 自定义操作按钮
        if (customActionText != null && customActionListener != null) {
            btnCustomAction.setVisibility(View.VISIBLE);
            btnCustomAction.setText(customActionText);
            btnCustomAction.setOnClickListener(v -> {
                customActionListener.onActionClick();
                dismiss();
            });
        } else {
            btnCustomAction.setVisibility(View.GONE);
        }

        // 关闭按钮
        btnClose.setOnClickListener(v -> dismiss());

        // 复制按钮
        btnCopy.setOnClickListener(v -> copyErrorToClipboard());

        // 详情按钮
        btnDetails.setOnClickListener(v -> toggleStackTrace());

        // 进入动画
        animateEnter(view);
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss();
        }
    }

    /**
     * 切换堆栈跟踪显示
     */
    private void toggleStackTrace() {
        showStackTrace = !showStackTrace;
        if (showStackTrace) {
            stackTraceContainer.setVisibility(View.VISIBLE);
            btnDetails.setIconResource(R.drawable.ralib_ic_expand_less);
            btnDetails.setText("隐藏详情");
        } else {
            stackTraceContainer.setVisibility(View.GONE);
            btnDetails.setIconResource(R.drawable.ralib_ic_expand_more);
            btnDetails.setText("显示详情");
        }
    }

    /**
     * 复制错误信息到剪贴板
     */
    private void copyErrorToClipboard() {
        if (getContext() == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(errorTitle).append("】\n");
        sb.append(errorMessage).append("\n");
        if (stackTraceText != null && !stackTraceText.isEmpty()) {
            sb.append("\n堆栈跟踪：\n").append(stackTraceText);
        }

        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("错误信息", sb.toString());
        clipboard.setPrimaryClip(clip);

        // 显示提示
        btnCopy.setText("已复制");
        btnCopy.setIconResource(R.drawable.ralib_ic_check);
        btnCopy.postDelayed(() -> {
            btnCopy.setText("复制");
            btnCopy.setIconResource(R.drawable.ralib_ic_copy);
        }, 2000);
    }

    /**
     * 进入动画
     */
    private void animateEnter(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    /**
     * 获取异常堆栈跟踪字符串
     */
    private static String getStackTraceString(Throwable throwable) {
        if (throwable == null) return "";

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
