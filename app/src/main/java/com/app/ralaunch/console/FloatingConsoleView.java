package com.app.ralaunch.console;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.app.ralaunch.R;
import com.google.android.material.card.MaterialCardView;

/**
 * 悬浮控制台视图
 */
public class FloatingConsoleView implements ConsoleService.ConsoleListener {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private View rootView;
    private MaterialCardView consoleContainer;
    private ViewGroup consoleContent;
    private TextView txtConsoleOutput;
    private TextView txtMessageCount;
    private TextView txtErrorCount;
    private TextView txtWarningCount;
    private EditText etConsoleInput;
    private Button btnSend;
    private ImageButton btnMinimize;
    private ImageButton btnClear;
    private ImageButton btnClose;
    private NestedScrollView scrollView;

    private boolean isShowing = false;
    private final SpannableStringBuilder outputBuffer = new SpannableStringBuilder();
    private final ConsoleService consoleService;

    public FloatingConsoleView(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.consoleService = ConsoleService.getInstance();
        
        initView();
        setupListeners();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        rootView = inflater.inflate(R.layout.floating_console, null);

        // 获取视图引用
        consoleContainer = rootView.findViewById(R.id.consoleContainer);
        consoleContent = rootView.findViewById(R.id.consoleContent);
        txtConsoleOutput = rootView.findViewById(R.id.txtConsoleOutput);
        txtMessageCount = rootView.findViewById(R.id.txtMessageCount);
        txtErrorCount = rootView.findViewById(R.id.txtErrorCount);
        txtWarningCount = rootView.findViewById(R.id.txtWarningCount);
        etConsoleInput = rootView.findViewById(R.id.etConsoleInput);
        btnSend = rootView.findViewById(R.id.btnSend);
        btnMinimize = rootView.findViewById(R.id.btnMinimize);
        btnClear = rootView.findViewById(R.id.btnClear);
        btnClose = rootView.findViewById(R.id.btnClose);
        
        // 查找 NestedScrollView (在 consoleContent LinearLayout 内部)
        View contentView = rootView.findViewById(R.id.consoleContent);
        if (contentView instanceof ViewGroup) {
            ViewGroup contentGroup = (ViewGroup) contentView;
            // NestedScrollView 是 LinearLayout 的第二个子视图（索引1）
            if (contentGroup.getChildCount() > 1 && contentGroup.getChildAt(1) instanceof NestedScrollView) {
                scrollView = (NestedScrollView) contentGroup.getChildAt(1);
            }
        }

        // 点击背景关闭
        rootView.setOnClickListener(v -> hide());
        
        // 阻止点击穿透
        consoleContainer.setOnClickListener(v -> {
            // 不做任何事，仅阻止事件传播
        });
    }

    private void setupListeners() {
        // 发送按钮
        btnSend.setOnClickListener(v -> sendInput());

        // 输入框回车发送
        etConsoleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendInput();
                return true;
            }
            return false;
        });

        // 最小化按钮改为直接隐藏控制台
        btnMinimize.setOnClickListener(v -> hide());

        // 清除按钮
        btnClear.setOnClickListener(v -> {
            consoleService.clearHistory();
            outputBuffer.clear();
            outputBuffer.append("控制台已清除\n");
            txtConsoleOutput.setText(outputBuffer);
            scrollToBottom();
        });

        // 关闭按钮
        btnClose.setOnClickListener(v -> hide());

        // 注册控制台服务监听器
        consoleService.addListener(this);
    }

    private void sendInput() {
        String input = etConsoleInput.getText().toString().trim();
        if (!input.isEmpty()) {
            // 添加到输出显示
            appendColoredText("> " + input + "\n", ContextCompat.getColor(context, R.color.console_accent));
            
            // 发送到控制台服务
            consoleService.sendInput(input);
            
            // 清空输入框
            etConsoleInput.setText("");
            
            scrollToBottom();
        }
    }

    /**
     * 在 Activity 中显示控制台（不使用悬浮窗）
     */
    public void showInActivity(ViewGroup activityRootView) {
        if (isShowing) {
            return;
        }

        try {
            // 设置布局参数
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            
            // 直接添加到 Activity 的根视图
            activityRootView.addView(rootView, params);
            rootView.bringToFront();
            activityRootView.requestLayout();
            activityRootView.invalidate();
            isShowing = true;
            
            // 确保控制台可见
            consoleContainer.setVisibility(View.VISIBLE);
            
            // 加载历史消息
            loadHistory();
            
            // 入场动画
            consoleContainer.setAlpha(0f);
            consoleContainer.setTranslationY(200f);
            consoleContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start();
            
            android.util.Log.i("FloatingConsoleView", "Console added to activity layout");
        } catch (Exception e) {
            android.util.Log.e("FloatingConsoleView", "Failed to show in activity: " + e.getMessage(), e);
        }
    }

    /**
     * 使用悬浮窗显示控制台（需要悬浮窗权限）
     */
    public void show() {
        if (isShowing) {
            return;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        windowManager.addView(rootView, params);
        isShowing = true;

        // 加载历史消息
        loadHistory();

        // 入场动画
        consoleContainer.setAlpha(0f);
        consoleContainer.setTranslationY(200f);
        consoleContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start();
    }

    public void hide() {
        if (!isShowing) {
            return;
        }

        // 退场动画
        rootView.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 从父视图中移除
                        if (rootView.getParent() instanceof ViewGroup) {
                            ((ViewGroup) rootView.getParent()).removeView(rootView);
                        } else {
                            // 如果是悬浮窗
                            try {
                                windowManager.removeView(rootView);
                            } catch (Exception e) {
                                android.util.Log.e("FloatingConsoleView", "Failed to remove from WindowManager: " + e.getMessage());
                            }
                        }
                        isShowing = false;
                        rootView.setAlpha(1f);
                    }
                })
                .start();
    }

    public boolean isShowing() {
        return isShowing;
    }

    private void loadHistory() {
        outputBuffer.clear();
        for (ConsoleMessage message : consoleService.getMessageHistory()) {
            appendMessage(message);
        }
        txtConsoleOutput.setText(outputBuffer);
        scrollToBottom();
    }

    @Override
    public void onMessageReceived(ConsoleMessage message) {
        mainHandler.post(() -> {
            appendMessage(message);
            txtConsoleOutput.setText(outputBuffer);
            scrollToBottom();
        });
    }

    @Override
    public void onStatisticsUpdated(int totalMessages, int errors, int warnings) {
        mainHandler.post(() -> {
            txtMessageCount.setText(String.format("消息: %d", totalMessages));
            txtErrorCount.setText(String.format("错误: %d", errors));
            txtWarningCount.setText(String.format("警告: %d", warnings));
        });
    }

    private void appendMessage(ConsoleMessage message) {
        int color;
        switch (message.getLevel()) {
            case ERROR:
                color = ContextCompat.getColor(context, R.color.console_error);
                break;
            case WARNING:
                color = ContextCompat.getColor(context, R.color.console_warning);
                break;
            case DEBUG:
                color = ContextCompat.getColor(context, R.color.console_info);
                break;
            case INFO:
            default:
                color = ContextCompat.getColor(context, R.color.console_text_primary);
                break;
        }

        appendColoredText(message.toString() + "\n", color);
    }

    private void appendColoredText(String text, int color) {
        int start = outputBuffer.length();
        outputBuffer.append(text);
        outputBuffer.setSpan(
                new ForegroundColorSpan(color),
                start,
                outputBuffer.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // 限制缓冲区大小（最多 10000 字符）
        if (outputBuffer.length() > 10000) {
            outputBuffer.delete(0, outputBuffer.length() - 8000);
        }
    }

    private void scrollToBottom() {
        txtConsoleOutput.post(() -> {
            if (scrollView != null) {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    public void cleanup() {
        consoleService.removeListener(this);
        if (isShowing) {
            windowManager.removeView(rootView);
            isShowing = false;
        }
    }
}

