package com.app.ralaunch.activity;

import android.content.res.Resources;
import android.view.MotionEvent;

import com.app.ralaunch.controls.TouchPointerTracker;
import com.app.ralaunch.utils.AppLogger;

/**
 * 触摸事件桥接，封装指针过滤与原生 touch 数据同步。
 */
public class GameTouchBridge {

    private static final String TAG = "GameTouchBridge";
    private static Boolean sTouchBridgeAvailable = null;
    private final float[] touchX = new float[10];
    private final float[] touchY = new float[10];

    public void handleMotionEvent(MotionEvent event, Resources res) {
        if (!isTouchBridgeAvailable()) {
            return;
        }
        try {
            int action = event.getActionMasked();

            // 获取屏幕尺寸
            android.util.DisplayMetrics metrics = res.getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                GameActivity.nativeClearTouchDataBridge();
                return;
            }

            int pointerCount = event.getPointerCount();
            int actionIndex = event.getActionIndex();
            boolean isPointerUp = (action == MotionEvent.ACTION_POINTER_UP);

            int validCount = 0;
            for (int i = 0; i < pointerCount && validCount < 10; i++) {
                if (isPointerUp && i == actionIndex) {
                    continue;
                }
                int pointerId = event.getPointerId(i);
                if (TouchPointerTracker.isPointerConsumed(pointerId)) {
                    continue;
                }
                touchX[validCount] = event.getX(i) / screenWidth;
                touchY[validCount] = event.getY(i) / screenHeight;
                validCount++;
            }

            GameActivity.nativeSetTouchDataBridge(validCount, touchX, touchY, screenWidth, screenHeight);
        } catch (Exception e) {
            AppLogger.error(TAG, "Error in handleMotionEvent: " + e.getMessage(), e);
        }
    }

    private boolean isTouchBridgeAvailable() {
        if (sTouchBridgeAvailable == null) {
            try {
                GameActivity.nativeClearTouchDataBridge();
                sTouchBridgeAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                sTouchBridgeAvailable = false;
            }
        }
        return sTouchBridgeAvailable;
    }

    private String actionToString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN: return "DOWN";
            case MotionEvent.ACTION_UP: return "UP";
            case MotionEvent.ACTION_MOVE: return "MOVE";
            case MotionEvent.ACTION_POINTER_DOWN: return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP: return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL: return "CANCEL";
            default: return "UNKNOWN(" + action + ")";
        }
    }
}

