package com.app.ralaunch.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.app.ralaunch.RaLaunchApplication;

/**
 * 虚拟触控板控件View
 * 支持触摸滑动操作，使用按钮的所有外观功能
 */
public class VirtualTouchPad extends View implements ControlView {
    private static final String TAG = "VirtualTouchPad";

    private ControlData mData;
    private ControlInputBridge mInputBridge;

    private float mScreenWidth;
    private float mScreenHeight;

    private Handler mIdleDelayHandler = new Handler();
    private Handler mLeftClickDelayHandler = new Handler();
    private Handler mRightClickDelayHandler = new Handler();
    private int mCurrentState = 0;

    public final int TOUCHPAD_STATE_IDLE = 0;
    public final int TOUCHPAD_STATE_PENDING = 1;
    public final int TOUCHPAD_STATE_DOUBLE_CLICK = 2;
    public final int TOUCHPAD_STATE_MOVING = 3;

    private static int TOUCHPAD_STATE_IDLE_TIMEOUT = 200; // 毫秒
    private static int TOUCHPAD_CLICK_TIMEOUT = 50; // 毫秒
    private static int TOUCHPAD_MOVE_THRESHOLD = 5; // dp, 移动超过这个距离视为移动操作, 应该用dpToPx转换
    private static float TOUCHPAD_MOVE_RATIO = 2.0f; // 移动距离放大倍数

    // 绘制相关
    private Paint mBackgroundPaint;
    private Paint mStrokePaint;
    private TextPaint mTextPaint;
    private RectF mRectF;

    // 按钮状态
    private boolean mIsPressed = false;
    private int mActivePointerId = -1; // 跟踪的触摸点 ID
    private float mCenterX;
    private float mCenterY;
    private float mLastX;
    private float mLastY;
    private float mCurrentX;
    private float mCurrentY;
    private float mDeltaX;
    private float mDeltaY;
    private float mCenteredDeltaX;
    private float mCenteredDeltaY;
    private float mInitialTouchX;
    private float mInitialTouchY;

    public VirtualTouchPad(Context context, ControlData data, ControlInputBridge bridge) {
        super(context);
        mData = data;
        mInputBridge = bridge;
        mRectF = new RectF();
        mCenterX = mData.width / 2;
        mCenterY = mData.height / 2;
        mCurrentX = mCenterX;
        mCurrentY = mCenterY;
        mDeltaX = 0;
        mDeltaY = 0;

        // 获取屏幕尺寸（用于右摇杆绝对位置计算）
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

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
        // 边框透明度完全独立，默认1.0（完全不透明）
        float borderOpacity = mData.borderOpacity != 0 ? mData.borderOpacity : 1.0f;
        mStrokePaint.setAlpha((int) (borderOpacity * 255));

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(dpToPx(16));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        // 文本透明度完全独立，默认1.0（完全不透明）
        float textOpacity = mData.textOpacity != 0 ? mData.textOpacity : 1.0f;
        mTextPaint.setAlpha((int) (textOpacity * 255));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRectF.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制矩形（圆角矩形）
        float cornerRadius = dpToPx(mData.cornerRadius);
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint);
        canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint);
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

                mLastX = event.getX();
                mLastY = event.getY();
                mCurrentX = mLastX;
                mCurrentY = mLastY;
                mCenteredDeltaX = mCurrentX - mCenterX;
                mCenteredDeltaY = mCurrentY - mCenterY;
                mInitialTouchX = mCurrentX;
                mInitialTouchY = mCurrentY;

                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!mData.passThrough) {
                    TouchPointerTracker.consumePointer(pointerId);
                }

                // Trigger Press!
                handlePress();
                triggerVibration(true);

                return true;

            case MotionEvent.ACTION_MOVE:
                if (pointerId != mActivePointerId) {
                    return false;
                }

                mCurrentX = event.getX();
                mCurrentY = event.getY();
                mDeltaX = mCurrentX - mLastX;
                mDeltaY = mCurrentY - mLastY;
                mLastX = mCurrentX;
                mLastY = mCurrentY;
                mCenteredDeltaX = mCurrentX - mCenterX;
                mCenteredDeltaY = mCurrentY - mCenterY;

                // Trigger Move!
                handleMove();

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

                    // Trigger Release!
                    handleRelease();
                    triggerVibration(false);

                    return true;
                }
                return false;
        }
        return super.onTouchEvent(event);
    }

    private void handleMove() {
        // 处理触摸移动逻辑

        if (mCurrentState == TOUCHPAD_STATE_IDLE) {
            // Do nothing
        } else if (mCurrentState == TOUCHPAD_STATE_PENDING) {
            // Check if movement exceeds threshold
            float moveDistance = (float) Math.sqrt(Math.pow(mCurrentX - mInitialTouchX, 2) + Math.pow(mCurrentY - mInitialTouchY, 2));
            if (moveDistance > dpToPx(TOUCHPAD_MOVE_THRESHOLD)) {
                mCurrentState = TOUCHPAD_STATE_MOVING;
                mIdleDelayHandler.removeCallbacksAndMessages(null);
                // Send this move so that MOVE_THRESHOLD is not skipped
                float multipliedDeltaX = (mCurrentX - mInitialTouchX) * TOUCHPAD_MOVE_RATIO;
                float multipliedDeltaY = (mCurrentY - mInitialTouchY) * TOUCHPAD_MOVE_RATIO;
                sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true); // in ACTION_MOVE, button value doesn't matter
            }
        } else if (mCurrentState == TOUCHPAD_STATE_DOUBLE_CLICK) {
            // Double Click! Trigger centered movement and click!
            float multipliedDeltaX = mDeltaX * TOUCHPAD_MOVE_RATIO;
            float multipliedDeltaY = mDeltaY * TOUCHPAD_MOVE_RATIO;
            sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true); // in ACTION_MOVE, button value doesn't matter
        } else if (mCurrentState == TOUCHPAD_STATE_MOVING) {
            // Send movement data
            float multipliedDeltaX = mDeltaX * TOUCHPAD_MOVE_RATIO;
            float multipliedDeltaY = mDeltaY * TOUCHPAD_MOVE_RATIO;
            sdlOnNativeMouseDirect(0, MotionEvent.ACTION_MOVE, multipliedDeltaX, multipliedDeltaY, true); // in ACTION_MOVE, button value doesn't matter
        }

        invalidate();
    }

    private void handlePress() {
        mIsPressed = true;

        if (mCurrentState == TOUCHPAD_STATE_IDLE) {
            mCurrentState = TOUCHPAD_STATE_PENDING;
            mIdleDelayHandler.postDelayed(() -> {
                if (mCurrentState == TOUCHPAD_STATE_PENDING) { // No double click detected, no movement detected
                    mCurrentState = TOUCHPAD_STATE_IDLE;
                    performPendingStateToIdleStateClick();
                }
                mIdleDelayHandler.removeCallbacksAndMessages(null);
            }, TOUCHPAD_STATE_IDLE_TIMEOUT);
        } else if (mCurrentState == TOUCHPAD_STATE_PENDING) {
            mCurrentState = TOUCHPAD_STATE_DOUBLE_CLICK;
            mIdleDelayHandler.removeCallbacksAndMessages(null);
            // Double Click! Trigger centered movement and click!
            // Calculate on-screen centered position
            float onScreenMouseX = (mScreenWidth / 2) + (mCenteredDeltaX * TOUCHPAD_MOVE_RATIO);
            float onScreenMouseY = (mScreenHeight / 2) + (mCenteredDeltaY * TOUCHPAD_MOVE_RATIO);
            // click left mouse button and send centered movement
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_DOWN, onScreenMouseX, onScreenMouseY, false);
            // The rest of the movements would be handled by handleMove()
        } else if (mCurrentState == TOUCHPAD_STATE_DOUBLE_CLICK) {
            // Already in double click, ignore
        } else if (mCurrentState == TOUCHPAD_STATE_MOVING) {
            // Already moving, ignore
        }

        invalidate();
    }

    private void handleRelease() {
        mIsPressed = false;

        if (mCurrentState == TOUCHPAD_STATE_IDLE) {
            // Do nothing
        } else if (mCurrentState == TOUCHPAD_STATE_PENDING) {
            // Still pending, wait for timeout to confirm single click
        } else if (mCurrentState == TOUCHPAD_STATE_DOUBLE_CLICK) {
            // After double click, go back to idle
            mCurrentState = TOUCHPAD_STATE_IDLE;
            // Release mouse button
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0, 0, true);
        } else if (mCurrentState == TOUCHPAD_STATE_MOVING) {
            // After moving, go back to idle
            mCurrentState = TOUCHPAD_STATE_IDLE;
        }

        invalidate();
    }

    private void sdlOnNativeMouseDirect(int button, int action, float x, float y, boolean relative) {
        if (mInputBridge instanceof SDLInputBridge bridge) {
            bridge.sdlOnNativeMouseDirect(button, action, x, y, relative);
        }
    }

    private void performPendingStateToIdleStateClick() {
        if (mIsPressed) {
            // Long Press! Trigger right click!
            mRightClickDelayHandler.removeCallbacksAndMessages(null);
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0, 0, true);
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_SECONDARY, MotionEvent.ACTION_DOWN, 0, 0, true);
            mRightClickDelayHandler.postDelayed(() -> {
                sdlOnNativeMouseDirect(MotionEvent.BUTTON_SECONDARY, MotionEvent.ACTION_UP, 0, 0, true);
            }, TOUCHPAD_CLICK_TIMEOUT);
        } else {
            // Single Press! Trigger left click!
            mLeftClickDelayHandler.removeCallbacksAndMessages(null);
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0, 0, true);
            sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_DOWN, 0, 0, true);
            mLeftClickDelayHandler.postDelayed(() -> {
                sdlOnNativeMouseDirect(MotionEvent.BUTTON_PRIMARY, MotionEvent.ACTION_UP, 0, 0, true);
            }, TOUCHPAD_CLICK_TIMEOUT);
        }
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

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
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
