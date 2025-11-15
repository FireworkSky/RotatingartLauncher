package com.app.ralaunch.controls;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;

import com.app.ralaunch.activity.GameActivity;

/**
 * 虚拟按钮View
 * 支持普通按钮和切换按钮（Toggle）
 */
public class VirtualButton extends View implements ControlView {
    private static final String TAG = "VirtualButton";
    
    private ControlData mData;
    private ControlInputBridge mInputBridge;
    
    // 绘制相关
    private Paint mBackgroundPaint;
    private Paint mStrokePaint;
    private TextPaint mTextPaint;
    private RectF mRectF;
    
    // 按钮状态
    private boolean mIsPressed = false;
    private boolean mIsToggled = false;
    
    public VirtualButton(Context context, ControlData data, ControlInputBridge bridge) {
        super(context);
        mData = data;
        mInputBridge = bridge;
        mRectF = new RectF();
        initPaints();
    }
    
    private void initPaints() {
        mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint.setColor(mData.bgColor);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
        
        mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStrokePaint.setColor(mData.strokeColor);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dpToPx(mData.strokeWidth));
        mStrokePaint.setAlpha((int) (mData.opacity * 255));
        
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(dpToPx(16));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setAlpha((int) (mData.opacity * 255));
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRectF.set(0, 0, w, h);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 根据状态调整颜色
        int alpha = mBackgroundPaint.getAlpha();
        if (mIsPressed || mIsToggled) {
            mBackgroundPaint.setAlpha(Math.min(255, (int) (alpha * 1.5f)));
        } else {
            mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
        }
        
        // 绘制背景（圆角矩形）
        float cornerRadius = dpToPx(mData.cornerRadius);
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint);
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint);
        
        // 绘制文字（名称 + 按键）
        String keyName = KeyMapper.getKeyName(mData.keycode);
        String displayText = mData.name;
        
//        // 如果按键不是"未知"，则在名称下方显示按键
//        if (!keyName.startsWith("未知")) {
//            // 绘制名称（上半部分）
//            float nameY = getHeight() / 2f - dpToPx(4);
//            canvas.drawText(displayText, getWidth() / 2f, nameY, mTextPaint);
//
//            // 绘制按键（下半部分，较小字体）
//            TextPaint keyPaint = new TextPaint(mTextPaint);
//            keyPaint.setTextSize(dpToPx(12));
//            keyPaint.setAlpha((int) (mData.opacity * 200)); // 稍微透明一点
//            float keyY = getHeight() / 2f + dpToPx(12);
//            canvas.drawText(keyName, getWidth() / 2f, keyY, keyPaint);
//        } else {
//            // 只显示名称（居中）
//            float textY = getHeight() / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
//            canvas.drawText(displayText, getWidth() / 2f, textY, mTextPaint);
//        }
        // 只显示名称（居中）
        // 显示真实按键会挡视野
        float textY = getHeight() / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
        canvas.drawText(displayText, getWidth() / 2f, textY, mTextPaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handlePress();
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handleRelease();
                return true;
        }
        return super.onTouchEvent(event);
    }
    
    private void handlePress() {
        mIsPressed = true;
        
        // 处理特殊功能按键
        if (mData.keycode == ControlData.SPECIAL_KEYBOARD) {
            // 弹出Android键盘
            showKeyboard();
            invalidate();
            return;
        }
        
        if (mData.isToggle) {
            // 切换按钮：切换状态
            mIsToggled = !mIsToggled;
            sendInput(mIsToggled);
        } else {
            // 普通按钮：按下
            sendInput(true);
        }
        
        invalidate();
    }
    
    private void handleRelease() {
        mIsPressed = false;
        
        if (!mData.isToggle) {
            // 普通按钮：释放
            sendInput(false);
        }
        // 切换按钮不在释放时发送事件
        
        invalidate();
    }
    
    /**
     * 显示Android软键盘并实时发送文本到游戏
     *
     * 工作原理：
     * 1. 先调用SDL.showTextInput()启用SDL文本输入模式
     * 2. 创建透明EditText激活系统IME
     * 3. 每输入一个字符立即通过SDL_TEXTINPUT发送
     * 4. Terraria的FnaIme会接收并转发文本到游戏
     */
    private void showKeyboard() {
        try {
            if (!(getContext() instanceof Activity)) {
                Log.e(TAG, "Context is not an Activity");
                return;
            }
            
            Activity activity = (Activity) getContext();
            
            activity.runOnUiThread(() -> {
                try {
                    // 第一步：启用SDL文本输入模式（通过GameActivity）
                    GameActivity.enableSDLTextInputForIME();
                    
                    // 等待100ms让SDL准备好（使用静态Handler避免内存泄漏）
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new KeyboardShowRunnable(activity, new WeakReference<>(getContext())), 100);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to enable SDL text input", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show keyboard", e);
        }
    }
    
    private void sendInput(boolean isDown) {
        if (mData.keycode >= 0) {
            // 键盘按键
            mInputBridge.sendKey(mData.keycode, isDown);
        } else if (mData.keycode >= ControlData.XBOX_TRIGGER_RIGHT && mData.keycode <= ControlData.XBOX_TRIGGER_LEFT) {
            // Xbox控制器触发器 (范围: -220 到 -221)
            float triggerValue = isDown ? 1.0f : -1.0f;
            mInputBridge.sendXboxTrigger(mData.keycode, triggerValue);
        } else if (mData.keycode >= ControlData.XBOX_BUTTON_DPAD_RIGHT && mData.keycode <= ControlData.XBOX_BUTTON_A) {
            // Xbox控制器按钮 (范围: -200 到 -214)
            mInputBridge.sendXboxButton(mData.keycode, isDown);
        } else if (mData.keycode >= ControlData.MOUSE_MIDDLE && mData.keycode <= ControlData.MOUSE_LEFT) {
            // 鼠标按键 (范围: -1 到 -3)
            // 计算按钮中心点的屏幕坐标
            int[] location = new int[2];
            getLocationOnScreen(location);
            float centerX = location[0] + getWidth() / 2.0f;
            float centerY = location[1] + getHeight() / 2.0f;
            
            mInputBridge.sendMouseButton(mData.keycode, isDown, centerX, centerY);
        }
        // SPECIAL_KEYBOARD (-100) 不在这里处理，在handlePress中特殊处理
    }
    
    @Override
    public ControlData getData() {
        return mData;
    }
    
    @Override
    public void updateData(ControlData data) {
        mData = data;
        initPaints();
        invalidate();
    }
    
    /**
     * 重置切换按钮状态
     */
    public void resetToggle() {
        if (mIsToggled) {
            mIsToggled = false;
            sendInput(false);
            invalidate();
        }
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    
    private int adjustColorBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * 静态内部类，避免隐式持有外部类引用，防止内存泄漏
     */
    private static class KeyboardShowRunnable implements Runnable {
        private final WeakReference<Activity> activityRef;
        private final WeakReference<Context> contextRef;
        
        KeyboardShowRunnable(Activity activity, WeakReference<Context> contextRef) {
            this.activityRef = new WeakReference<>(activity);
            this.contextRef = contextRef;
        }
        
        @Override
        public void run() {
            Activity activity = activityRef.get();
            Context context = contextRef.get();
            if (activity == null || activity.isFinishing() || context == null) {
                Log.w(TAG, "Activity已销毁，取消键盘显示");
                return;
            }
            
            try {
                // 第二步：创建透明EditText激活IME
                final EditText dummyInput = new EditText(activity);
                dummyInput.setAlpha(0f);
                dummyInput.setWidth(1);
                dummyInput.setHeight(1);
                
                // 添加到根视图
                android.view.ViewGroup rootView = (android.view.ViewGroup)
                    activity.findViewById(android.R.id.content);
                if (rootView == null) {
                    Log.e(TAG, "Root view not found");
                    return;
                }
                rootView.addView(dummyInput);
                
                // 标志位防止重复清理
                final boolean[] isCleanedUp = {false};
                
                // 创建Handler用于30秒超时
                final Handler timeoutHandler = new Handler(Looper.getMainLooper());
                final Runnable[] timeoutRunnable = new Runnable[1]; // 数组是为了能在lambda中修改
                
                // 创建清理函数
                final Runnable cleanup = () -> {
                    if (isCleanedUp[0]) {
                        return; // 已经清理过了，不要重复执行
                    }
                    isCleanedUp[0] = true;
                    
                    try {
                        // 移除30秒超时
                        if (timeoutRunnable[0] != null) {
                            timeoutHandler.removeCallbacks(timeoutRunnable[0]);
                        }
                        
                        // 先禁用SDL文本输入（最重要！）
                        GameActivity.disableSDLTextInput();
                        
                        // 隐藏键盘
                        InputMethodManager imm = (InputMethodManager)
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(dummyInput.getWindowToken(), 0);
                        }
                        
                        // 移除EditText
                        rootView.removeView(dummyInput);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to cleanup input", e);
                    }
                };
                
                // 监听键盘隐藏事件
                final android.view.ViewTreeObserver.OnGlobalLayoutListener layoutListener = 
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        private boolean wasKeyboardVisible = false;
                        
                        @Override
                        public void onGlobalLayout() {
                            android.graphics.Rect r = new android.graphics.Rect();
                            rootView.getWindowVisibleDisplayFrame(r);
                            int screenHeight = rootView.getRootView().getHeight();
                            int keypadHeight = screenHeight - r.bottom;
                            
                            boolean isKeyboardVisible = keypadHeight > screenHeight * 0.15;
                            
                            if (wasKeyboardVisible && !isKeyboardVisible) {
                                // 键盘从显示变为隐藏
                                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                cleanup.run();
                            }
                            
                            wasKeyboardVisible = isKeyboardVisible;
                        }
                    };
                
                rootView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                
                // 显示键盘
                dummyInput.requestFocus();
                InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(dummyInput, InputMethodManager.SHOW_FORCED);
                }
                
                // 实时发送每个字符和删除操作
                dummyInput.addTextChangedListener(new android.text.TextWatcher() {
                    private String lastText = "";
                    
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String currentText = s.toString();
                        
                        if (currentText.length() > lastText.length()) {
                            // 文本增加：发送新字符
                            String newChars = currentText.substring(lastText.length());
                            GameActivity.sendTextToGame(newChars);
                        } else if (currentText.length() < lastText.length()) {
                            // 文本减少：发送退格符
                            int deleteCount = lastText.length() - currentText.length();
                            for (int i = 0; i < deleteCount; i++) {
                                GameActivity.sendBackspace();
                            }
                        }
                        
                        lastText = currentText;
                    }
                    
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
                
                // 回车键关闭键盘
                dummyInput.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
                        cleanup.run();
                        return true;
                    }
                    return false;
                });
                
                // 30秒自动关闭
                timeoutRunnable[0] = () -> {
                    rootView.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
                    cleanup.run();
                };
                timeoutHandler.postDelayed(timeoutRunnable[0], 30000);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to show IME", e);
            }
        }
    }
}
