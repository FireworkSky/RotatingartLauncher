package com.app.ralaunch.controls;

import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import org.libsdl.app.SDLActivity;

/**
 * 触摸点跟踪器
 * 用于跟踪哪些触摸点被虚拟控件使用
 * 同时通知 SDL 层，让被占用的触摸点不会转换为鼠标事件
 */
public class TouchPointerTracker {
    private static final String TAG = "TouchPointerTracker";
    
    // 被虚拟控件占用的触摸点 ID
    private static final Set<Integer> sConsumedPointers = new HashSet<>();
    
    /**
     * 标记触摸点被虚拟控件占用
     * 同时通知 SDL 层，让此触摸点不会转换为鼠标事件
     */
    public static synchronized void consumePointer(int pointerId) {
        sConsumedPointers.add(pointerId);
        // 通知 SDL 层
        try {
            SDLActivity.nativeConsumeFingerTouch(pointerId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to notify SDL about consumed pointer: " + e.getMessage());
        }
    }
    
    /**
     * 释放触摸点
     * 同时通知 SDL 层
     */
    public static synchronized void releasePointer(int pointerId) {
        sConsumedPointers.remove(pointerId);
        // 通知 SDL 层
        try {
            SDLActivity.nativeReleaseFingerTouch(pointerId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to notify SDL about released pointer: " + e.getMessage());
        }
    }
    
    /**
     * 检查触摸点是否被占用
     */
    public static synchronized boolean isPointerConsumed(int pointerId) {
        return sConsumedPointers.contains(pointerId);
    }
    
    /**
     * 获取被占用的触摸点数量
     */
    public static synchronized int getConsumedCount() {
        return sConsumedPointers.size();
    }
    
    /**
     * 清除所有占用
     * 同时通知 SDL 层
     */
    public static synchronized void clearAll() {
        sConsumedPointers.clear();
        // 通知 SDL 层
        try {
            SDLActivity.nativeClearConsumedFingers();
        } catch (Exception e) {
            Log.w(TAG, "Failed to notify SDL about cleared pointers: " + e.getMessage());
        }
    }
}



