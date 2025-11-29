package com.app.ralaunch.controls;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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

import com.app.ralaunch.RaLaunchApplication;
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
    private int mActivePointerId = -1; // 跟踪的触摸点 ID
    
    /**
     * 设置按下状态（用于编辑模式的选择反馈）
     */
    public void setPressedState(boolean pressed) {
        if (mIsPressed != pressed) {
            mIsPressed = pressed;
            invalidate(); // 刷新绘制
        }
    }
    
    /**
     * 获取按下状态
     */
    public boolean isPressedState() {
        return mIsPressed;
    }
    
    public VirtualButton(Context context, ControlData data, ControlInputBridge bridge) {
        super(context);
        mData = data;
        mInputBridge = bridge;
        mRectF = new RectF();
        initPaints();
    }
    
    private void initPaints() {
       
       
        if (mData.buttonMode == ControlData.BUTTON_MODE_GAMEPAD) {
         
            int normalColor = 0x7D7D7D7D; // 半透明灰色
            int pressedColor = 0xFF7D7D7D; // 不透明灰色（按下）
            int textColor = 0x7DFFFFFF; // 半透明白色（文字）
            int backgroundColor = 0x327D7D7D; // 很淡的灰色（背景）
            
            mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBackgroundPaint.setColor(normalColor);
            mBackgroundPaint.setStyle(Paint.Style.FILL);
            mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
            
          
            mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mStrokePaint.setColor(0x00000000); // 透明
            mStrokePaint.setStyle(Paint.Style.STROKE);
            mStrokePaint.setStrokeWidth(0);
            
            mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setColor(textColor);
            mTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); // 粗体
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setAlpha((int) (mData.opacity * 255));
        } else {
            // 键盘模式保持原有逻辑
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
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRectF.set(0, 0, w, h);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 应用旋转（只旋转绘制内容，不改变控件形状和边界）
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        if (mData.rotation != 0) {
            canvas.save();
            canvas.rotate(mData.rotation, centerX, centerY);
        }
        
        // 根据形状类型绘制背景
        int shape = mData.shape;
        float centerXDraw = mRectF.centerX();
        float centerYDraw = mRectF.centerY();
        float radius = Math.min(mRectF.width(), mRectF.height()) / 2f;
        
      
        if (shape == ControlData.SHAPE_CIRCLE && mData.buttonMode == ControlData.BUTTON_MODE_GAMEPAD) {
          
            float margin = 0.15f; // DEFAULT_MARGIN
            float outerRadius = radius * (1.0f - margin); // 85% 半径（外圈）
            float innerRadius = radius * (1.0f - 2 * margin); // 70% 半径（内圈）
            
            // 绘制外圈（背景）
            Paint backgroundPaint = new Paint(mBackgroundPaint);
            backgroundPaint.setColor(0x327D7D7D); // backgroundColor
            backgroundPaint.setAlpha((int) (mData.opacity * 255));
            canvas.drawCircle(centerXDraw, centerYDraw, outerRadius, backgroundPaint);
            
            // 绘制内圈（前景，按下时使用 pressedColor）
            Paint foregroundPaint = new Paint(mBackgroundPaint);
            if (mIsPressed || mIsToggled) {
                foregroundPaint.setColor(0xFF7D7D7D); // pressedColor
            } else {
                foregroundPaint.setColor(0x7D7D7D7D); // normalColor
            }
            foregroundPaint.setAlpha((int) (mData.opacity * 255));
            canvas.drawCircle(centerXDraw, centerYDraw, innerRadius, foregroundPaint);
        } else if (shape == ControlData.SHAPE_CIRCLE) {
            // 普通圆形（键盘模式）
            // 根据状态调整颜色
            int alpha = mBackgroundPaint.getAlpha();
            if (mIsPressed || mIsToggled) {
                mBackgroundPaint.setAlpha(Math.min(255, (int) (alpha * 1.5f)));
            } else {
                mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
            }
            canvas.drawCircle(centerXDraw, centerYDraw, radius, mBackgroundPaint);
            canvas.drawCircle(centerXDraw, centerYDraw, radius, mStrokePaint);
        } else if (shape == ControlData.SHAPE_DOUBLE_CIRCLE) {
           
            float margin = 0.15f; // 边距比例
            float outerRadius = radius * (1.0f - margin);
            float innerRadius = radius * (1.0f - 2 * margin);
            
            // 绘制外圈（背景）
            canvas.drawCircle(centerXDraw, centerYDraw, outerRadius, mBackgroundPaint);
            canvas.drawCircle(centerXDraw, centerYDraw, outerRadius, mStrokePaint);
            
            // 绘制内圈（前景，根据按下状态调整颜色）
            Paint innerPaint = new Paint(mBackgroundPaint);
            if (mIsPressed || mIsToggled) {
                innerPaint.setAlpha(Math.min(255, (int) (mData.opacity * 255 * 1.3f)));
            }
            canvas.drawCircle(centerXDraw, centerYDraw, innerRadius, innerPaint);
            
            // 内圈边框
            if (mStrokePaint.getColor() != 0) {
                Paint innerStrokePaint = new Paint(mStrokePaint);
                innerStrokePaint.setStrokeWidth(mStrokePaint.getStrokeWidth() * 0.7f);
                canvas.drawCircle(centerXDraw, centerYDraw, innerRadius, innerStrokePaint);
            }
        } else if (shape == ControlData.SHAPE_CROSS) {
            // 绘制十字键（矩形十字）
            float armWidth = Math.min(mRectF.width(), mRectF.height()) * 0.3f; // 十字臂宽度
            float armLength = Math.min(mRectF.width(), mRectF.height()) * 0.4f; // 十字臂长度
            
            // 绘制垂直臂
            android.graphics.RectF verticalArm = new android.graphics.RectF(
                centerXDraw - armWidth / 2,
                centerYDraw - armLength,
                centerXDraw + armWidth / 2,
                centerYDraw + armLength
            );
            canvas.drawRoundRect(verticalArm, armWidth / 4, armWidth / 4, mBackgroundPaint);
            
            // 绘制水平臂
            android.graphics.RectF horizontalArm = new android.graphics.RectF(
                centerXDraw - armLength,
                centerYDraw - armWidth / 2,
                centerXDraw + armLength,
                centerYDraw + armWidth / 2
            );
            canvas.drawRoundRect(horizontalArm, armWidth / 4, armWidth / 4, mBackgroundPaint);
            
            // 绘制边框
            canvas.drawRoundRect(verticalArm, armWidth / 4, armWidth / 4, mStrokePaint);
            canvas.drawRoundRect(horizontalArm, armWidth / 4, armWidth / 4, mStrokePaint);
        } else if (shape == ControlData.SHAPE_ARROW_CROSS) {
          
            float size = Math.min(mRectF.width(), mRectF.height());
            float arrowSize = size * 0.4f;
            
            // 绘制4个方向的箭头
            for (int i = 0; i < 4; i++) {
                float angle = (float) (i * Math.PI / 2.0);
                canvas.save();
                canvas.translate(centerXDraw, centerYDraw);
                canvas.rotate((float) Math.toDegrees(angle));
                
                Path arrowPath = buildArrowPath(arrowSize);
                canvas.drawPath(arrowPath, mBackgroundPaint);
                canvas.drawPath(arrowPath, mStrokePaint);
                
                canvas.restore();
            }
        } else if (shape == ControlData.SHAPE_BACKGROUND_CIRCLE) {
            // 绘制背景圈（仅背景，无内容）
            canvas.drawCircle(centerXDraw, centerYDraw, radius, mBackgroundPaint);
            canvas.drawCircle(centerXDraw, centerYDraw, radius, mStrokePaint);
            // 恢复旋转
            if (mData.rotation != 0) {
                canvas.restore();
            }
            // 背景圈不绘制内容，直接返回
            return;
        } else {
            // 绘制矩形（圆角矩形）
            float cornerRadius = dpToPx(mData.cornerRadius);
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint);
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint);
        }
        
        // 绘制文字（名称 + 按键）
        String keyName = KeyMapper.getKeyName(mData.keycode);
        String displayText = mData.name;
        
        // RadialGamePad 风格：自动计算文字大小以适应区域
        if (mData.buttonMode == ControlData.BUTTON_MODE_GAMEPAD && displayText != null && !displayText.isEmpty()) {
            // 计算文字宽高比
            mTextPaint.setTextSize(20f); // 临时设置用于测量
            android.graphics.Rect textBounds = new android.graphics.Rect();
            mTextPaint.getTextBounds(displayText, 0, displayText.length(), textBounds);
            float textAspectRatio = textBounds.width() / (float) Math.max(textBounds.height(), 1);
            
            // 自动计算文字大小：minOf(height / 2, width / textAspectRatio)
            float textSize = Math.min(
                getHeight() / 2f,
                getWidth() / Math.max(textAspectRatio, 1f)
            );
            mTextPaint.setTextSize(textSize);
        } else {
            // 键盘模式保持原有大小
            if (mData.buttonMode != ControlData.BUTTON_MODE_GAMEPAD) {
                mTextPaint.setTextSize(dpToPx(16));
            }
        }
        
        // 只显示名称（居中）
        // 显示真实按键会挡视野
        float textY = getHeight() / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
        canvas.drawText(displayText, getWidth() / 2f, textY, mTextPaint);
        
        // 恢复旋转
        if (mData.rotation != 0) {
            canvas.restore();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerId = event.getPointerId(event.getActionIndex());
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 如果已经在跟踪一个触摸点，忽略新的
                if (mActivePointerId != -1) {
                    return false;
                }
                
                // 记录触摸点
                mActivePointerId = pointerId;
                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!mData.passThrough) {
                    TouchPointerTracker.consumePointer(pointerId);
                }
                
                handlePress();
                triggerVibration(true);
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                // 检查是否是我们跟踪的触摸点
                if (pointerId == mActivePointerId) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!mData.passThrough) {
                        TouchPointerTracker.releasePointer(mActivePointerId);
                    }
                    mActivePointerId = -1;
                    
                    triggerVibration(false);
                    handleRelease();
                    return true;
                }
                return false;
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
    
    /**
    
     * @param size 箭头大小
     * @return 箭头路径
     */
    private Path buildArrowPath(float size) {
        Path path = new Path();
        
       
        float xStart = size * 0.05f;
        float xEnd = size * 0.15f;
        float xMid = size * 0.33f;
        float ySpacing = size * 0.20f;
        
        float xLeft = xStart;
        float xMidPoint = xMid;
        float xRight = size - xEnd;
        float xRightControl = size;
        float yTop = ySpacing;
        float yMid = size / 2f;
        float yBottom = size - ySpacing;
        
        // 构建箭头路径（向上指向）
        path.moveTo(xLeft, yMid);
        path.lineTo(xMidPoint, yTop);
        path.lineTo(xRight, yTop);
        path.quadTo(xRightControl, yMid, xRight, yBottom);
        path.lineTo(xMidPoint, yBottom);
        path.close();
        
        // 平移路径使其居中
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postTranslate(-size / 2f, -size / 2f);
        path.transform(matrix);
        
        return path;
    }

    private static void triggerVibration(boolean isPress) {
        if (isPress) {
            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
        }
        else {
            // 释放时不振动
//            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
        }
    }
}
