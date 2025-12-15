package com.app.ralaunch.activity;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import com.app.ralaunch.controls.SDLInputBridge;
import com.app.ralaunch.controls.TouchPointerTracker;
import com.app.ralaunch.utils.AppLogger;

/**
 * 长按屏幕触发鼠标右键检测器
 */
public class LongPressRightClickDetector {
    
    private static final String TAG = "LongPressRightClick";
    private static final long LONG_PRESS_TIMEOUT = 1000; // 1秒
    private static final float MOVEMENT_THRESHOLD = 20; // 移动阈值（像素）
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SDLInputBridge inputBridge;
    
    private Runnable longPressRunnable;
    private boolean longPressTriggered = false;
    private float initialX = 0;
    private float initialY = 0;
    private float currentX = 0;
    private float currentY = 0;
    private int trackedPointerId = -1;
    
    private boolean enabled = false;
    
    public LongPressRightClickDetector(SDLInputBridge inputBridge) {
        this.inputBridge = inputBridge;
    }
    
    /**
     * 启用/禁用长按右键功能
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cancelLongPress();
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 处理触摸事件
     */
    public void handleTouchEvent(MotionEvent event) {
        if (!enabled) {
            return;
        }
        
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 只在第一个未被消费的触摸点上启动检测
                if (trackedPointerId == -1) {
                    int pointerId = event.getPointerId(actionIndex);
                    // 检查这个触摸点是否被虚拟控件消费
                    if (!TouchPointerTracker.isPointerConsumed(pointerId)) {
                        startLongPressDetection(event, actionIndex);
                    }
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                // 检查移动距离，如果移动太多则取消长按检测
                if (trackedPointerId != -1) {
                    int pointerIndex = event.findPointerIndex(trackedPointerId);
                    if (pointerIndex >= 0) {
                        currentX = event.getX(pointerIndex);
                        currentY = event.getY(pointerIndex);
                        
                        float deltaX = currentX - initialX;
                        float deltaY = currentY - initialY;
                        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        
                        if (distance > MOVEMENT_THRESHOLD) {
                            cancelLongPress();
                        }
                    }
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                // 检查是否是我们跟踪的触摸点
                int pointerId = event.getPointerId(actionIndex);
                if (trackedPointerId == pointerId) {
                    if (longPressTriggered) {
                        // 如果长按已触发，释放右键
                        releaseLongPressRightClick();
                    }
                    cancelLongPress();
                }
                break;
        }
    }
    
    /**
     * 开始长按检测
     */
    private void startLongPressDetection(MotionEvent event, int pointerIndex) {
        trackedPointerId = event.getPointerId(pointerIndex);
        initialX = event.getX(pointerIndex);
        initialY = event.getY(pointerIndex);
        currentX = initialX;
        currentY = initialY;
        longPressTriggered = false;
        
        longPressRunnable = () -> {
            longPressTriggered = true;
            triggerLongPressRightClick();
        };
        
        handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
    }
    
    /**
     * 取消长按检测
     */
    private void cancelLongPress() {
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
        trackedPointerId = -1;
        longPressTriggered = false;
    }
    
    /**
     * 触发长按右键
     */
    private void triggerLongPressRightClick() {
        if (inputBridge != null) {
            // 发送鼠标右键按下
            inputBridge.sendMouseButton(
                com.app.ralaunch.controls.ControlData.MOUSE_RIGHT, 
                true, 
                currentX, 
                currentY
            );
        }
    }
    
    /**
     * 释放长按右键
     */
    private void releaseLongPressRightClick() {
        if (inputBridge != null) {
            
            // 发送鼠标右键释放
            inputBridge.sendMouseButton(
                com.app.ralaunch.controls.ControlData.MOUSE_RIGHT, 
                false, 
                currentX, 
                currentY
            );
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        cancelLongPress();
        handler.removeCallbacksAndMessages(null);
    }
}

