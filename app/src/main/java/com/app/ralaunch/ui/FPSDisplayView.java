package com.app.ralaunch.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.app.ralaunch.controls.SDLInputBridge;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.AppLogger;

/**
 * FPS 显示视图
 */
public class FPSDisplayView extends View {
    private static final String TAG = "FPSDisplayView";
    
    private Paint mTextPaint;
    private Paint mBackgroundPaint;
    private float mCurrentFPS = 0f;
    private int mDrawCalls = 0;
    private int mStateChanges = 0;
    private int mTexChanges = 0;
    private boolean mCursorHidden = false;
    private boolean mShowExtendedStats = false; // 显示扩展统计
    private Handler mHandler;
    private Runnable mUpdateRunnable;
    private SDLInputBridge mInputBridge;
    private SettingsManager mSettingsManager;
    
    // 拖动相关
    private float mLastX;
    private float mLastY;
    private float mInitialTouchX;
    private float mInitialTouchY;
    private float mInitialViewX;
    private float mInitialViewY;
    private boolean mIsDragging = false;
    private static final float DRAG_THRESHOLD = 10f; // 拖动阈值（像素）
    
    // 固定位置（-1 表示跟随鼠标）
    private float mFixedX = -1f;
    private float mFixedY = -1f;
    
    // FPS 文本的边界区域（用于判断触摸是否在文本上）
    private float mTextLeft = 0f;
    private float mTextTop = 0f;
    private float mTextRight = 0f;
    private float mTextBottom = 0f;
    
    // 更新间隔（毫秒）
    private static final long UPDATE_INTERVAL = 500;
    
    public FPSDisplayView(Context context) {
        super(context);
        init();
    }
    
    public FPSDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // 初始化文本画笔
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(48f); // 较大的字体
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
        
        // 初始化背景画笔（可选，用于背景）
        mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint.setColor(Color.argb(128, 0, 0, 0)); // 半透明黑色背景
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        
        // 设置为不可见初始状态
        setVisibility(GONE);
        
        // 初始化设置管理器
        mSettingsManager = SettingsManager.getInstance(getContext());
        
        // 加载保存的位置
        mFixedX = mSettingsManager.getFPSDisplayX();
        mFixedY = mSettingsManager.getFPSDisplayY();
        
        // 初始化 Handler
        mHandler = new Handler(Looper.getMainLooper());
        
        // 设置可拖动
        setupDraggable();
        
        // 创建更新任务
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateFPSData();
                invalidate(); // 触发重绘
                mHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }
    
    /**
     * 设置输入桥接（用于获取鼠标位置）
     */
    public void setInputBridge(SDLInputBridge bridge) {
        mInputBridge = bridge;
    }
    
    /**
     * 开始更新
     */
    public void start() {
        // 立即更新可见性
        updateVisibility();
        // 始终启动更新任务（即使没有文件，也尝试更新）
        mHandler.post(mUpdateRunnable);
    }
    
    /**
     * 停止更新
     */
    public void stop() {
        mHandler.removeCallbacks(mUpdateRunnable);
    }

    /**
     * 强制刷新可见性（当设置改变时调用）
     */
    public void refreshVisibility() {
        updateVisibility();
        invalidate();
    }
    
    /**
     * 从环境变量读取 FPS 数据（通过 Os.getenv 获取 native 层设置的环境变量）
     */
    private void updateFPSData() {
        try {
            // 使用 Os.getenv 读取 native 层设置的环境变量
            String fpsStr = Os.getenv("RALCORE_FPS");
            String cursorHiddenStr = Os.getenv("RALCORE_CURSOR_HIDDEN");
            String drawCallsStr = Os.getenv("RALCORE_DRAWCALLS");
            String stateChangesStr = Os.getenv("RALCORE_STATECHANGES");
            String texChangesStr = Os.getenv("RALCORE_TEXCHANGES");
            
            if (fpsStr != null && !fpsStr.isEmpty()) {
                try {
                    mCurrentFPS = Float.parseFloat(fpsStr);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            
            if (cursorHiddenStr != null) {
                mCursorHidden = "1".equals(cursorHiddenStr);
            }
            
            // 读取扩展统计信息
            if (drawCallsStr != null && !drawCallsStr.isEmpty()) {
                try {
                    mDrawCalls = Integer.parseInt(drawCallsStr);
                    mShowExtendedStats = true;
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            
            if (stateChangesStr != null && !stateChangesStr.isEmpty()) {
                try {
                    mStateChanges = Integer.parseInt(stateChangesStr);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
            
            if (texChangesStr != null && !texChangesStr.isEmpty()) {
                try {
                    mTexChanges = Integer.parseInt(texChangesStr);
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        updateVisibility();
    }
    
    /**
     * 更新可见性
     */
    private void updateVisibility() {
        // 重新从设置管理器读取（确保获取最新值）
        boolean shouldShow = mSettingsManager.isFPSDisplayEnabled();
        if (shouldShow) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
    }
    
    /**
     * 设置可拖动（已移除，改用 dispatchTouchEvent）
     */
    private void setupDraggable() {
        // 不设置 clickable/focusable，避免拦截不相关的触摸事件
    }
    
    /**
     * 检查触摸点是否在 FPS 文本区域内
     */
    private boolean isTouchInTextArea(float touchX, float touchY) {
        // 如果还没有绘制过，使用默认区域
        if (mTextRight <= mTextLeft || mTextBottom <= mTextTop) {
            return false;
        }
        
        float touchPadding = 30f;
        return touchX >= mTextLeft - touchPadding && 
               touchX <= mTextRight + touchPadding &&
               touchY >= mTextTop - touchPadding && 
               touchY <= mTextBottom + touchPadding;
    }
    
    // 标记是否正在跟踪触摸
    private boolean mIsTrackingTouch = false;
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 只有在 FPS 文本区域内按下才开始跟踪
                if (!isTouchInTextArea(touchX, touchY)) {
                    // 不在文本区域内，不处理，让事件传递给下层
                    mIsTrackingTouch = false;
                    return false;
                }
                
                mIsTrackingTouch = true;
                mLastX = event.getRawX();
                mLastY = event.getRawY();
                mInitialTouchX = event.getRawX();
                mInitialTouchY = event.getRawY();
                
                // 获取当前绘制位置作为初始位置
                mInitialViewX = mFixedX >= 0 ? mFixedX : 100f;
                mInitialViewY = mFixedY >= 0 ? mFixedY : 100f;
                
                mIsDragging = false;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (!mIsTrackingTouch) {
                    return false;
                }
                
                float deltaX = event.getRawX() - mLastX;
                float deltaY = event.getRawY() - mLastY;
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                
                // 如果移动距离超过阈值，开始拖动
                if (distance > DRAG_THRESHOLD || mIsDragging) {
                    if (!mIsDragging) {
                        mIsDragging = true;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    
                    // 计算新位置
                    float newX = mInitialViewX + (event.getRawX() - mInitialTouchX);
                    float newY = mInitialViewY + (event.getRawY() - mInitialTouchY);
                    
                    // 限制在屏幕范围内（留出边距）
                    float margin = 50f;
                    newX = Math.max(margin, Math.min(newX, getWidth() - margin));
                    newY = Math.max(margin, Math.min(newY, getHeight() - margin));
                    
                    // 更新固定位置并触发重绘
                    mFixedX = newX;
                    mFixedY = newY;
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mIsTrackingTouch) {
                    return false;
                }
                
                mIsTrackingTouch = false;
                if (mIsDragging) {
                    mIsDragging = false;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    
                    // 保存位置到设置
                    mSettingsManager.setFPSDisplayX(mFixedX);
                    mSettingsManager.setFPSDisplayY(mFixedY);
                }
                return true;
        }
        return super.dispatchTouchEvent(event);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 只要开启了 FPS 显示就绘制
        if (!mSettingsManager.isFPSDisplayEnabled()) {
            return;
        }
        
        // FPS 文本（如果没有数据，显示 -- FPS）
        String fpsText = mCurrentFPS > 0 ? String.format("%.1f FPS", mCurrentFPS) : "-- FPS";
        
        // 扩展统计文本（如果有）
        String extText = null;
        if (mShowExtendedStats && mDrawCalls > 0) {
            extText = String.format("DC:%d TC:%d", mDrawCalls, mTexChanges);
        }
        
        // 计算文本尺寸
        Rect textBounds = new Rect();
        mTextPaint.getTextBounds(fpsText, 0, fpsText.length(), textBounds);
        float textWidth = mTextPaint.measureText(fpsText);
        float textHeight = textBounds.height();
        float padding = 10f;
        
        // 如果有扩展统计，计算其宽度
        float extTextWidth = 0f;
        if (extText != null) {
            extTextWidth = mTextPaint.measureText(extText);
            textWidth = Math.max(textWidth, extTextWidth);
        }
        
        // 使用固定位置（如果未设置，使用默认位置：屏幕左上角）
        float baseX = mFixedX >= 0 ? mFixedX : 100f; // 默认左上角 x=100
        float baseY = mFixedY >= 0 ? mFixedY : 100f; // 默认左上角 y=100
        
        // 计算文本绘制位置
        float textX = baseX + textWidth / 2 + padding; // 加上文本宽度的一半（因为文本居中对齐）
        float textY = baseY + textHeight + padding; // 加上文本高度
        
        // 确保不超出屏幕
        if (textX - textWidth / 2 < padding) {
            textX = textWidth / 2 + padding;
        } else if (textX + textWidth / 2 > getWidth() - padding) {
            textX = getWidth() - textWidth / 2 - padding;
        }
        
        if (textY - textHeight < padding) {
            textY = textHeight + padding;
        } else if (textY > getHeight() - padding) {
            textY = getHeight() - padding;
        }
        
        // 计算背景高度（如果有扩展统计，需要更高）
        float totalHeight = textHeight;
        if (extText != null) {
            totalHeight += textHeight + 5; // 额外一行的高度 + 间距
        }
        
        // 绘制背景
        float bgLeft = textX - textWidth / 2 - padding;
        float bgTop = textY - textHeight - padding;
        float bgRight = textX + textWidth / 2 + padding;
        float bgBottom = textY + padding;
        
        // 如果有扩展统计，扩展背景高度
        if (extText != null) {
            bgBottom += textHeight + 5;
        }
        
        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, mBackgroundPaint);
        
        // 保存文本边界（用于触摸检测）
        mTextLeft = bgLeft;
        mTextTop = bgTop;
        mTextRight = bgRight;
        mTextBottom = bgBottom;
        
        // 绘制 FPS 文本
        canvas.drawText(fpsText, textX, textY, mTextPaint);
        
        // 绘制扩展统计（如果有）
        if (extText != null) {
            float extTextY = textY + textHeight + 5;
            // 使用较小的画笔绘制扩展信息
            Paint smallPaint = new Paint(mTextPaint);
            smallPaint.setTextSize(mTextPaint.getTextSize() * 0.8f);
            smallPaint.setColor(Color.rgb(200, 200, 200)); // 稍暗的颜色
            canvas.drawText(extText, textX, extTextY, smallPaint);
        }
    }
}

