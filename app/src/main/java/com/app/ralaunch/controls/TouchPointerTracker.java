package com.app.ralaunch.controls;

import java.util.HashSet;
import java.util.Set;

/**
 * 触摸点跟踪器
 * 用于跟踪哪些触摸点被虚拟控件使用
 * touch bridge 会排除这些触摸点，避免干扰游戏
 */
public class TouchPointerTracker {
    private static final String TAG = "TouchPointerTracker";
    
    // 被虚拟控件占用的触摸点 ID
    private static final Set<Integer> sConsumedPointers = new HashSet<>();
    
    /**
     * 标记触摸点被虚拟控件占用
     */
    public static synchronized void consumePointer(int pointerId) {
        sConsumedPointers.add(pointerId);
    }
    
    /**
     * 释放触摸点
     */
    public static synchronized void releasePointer(int pointerId) {
        sConsumedPointers.remove(pointerId);
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
     */
    public static synchronized void clearAll() {
        sConsumedPointers.clear();
    }
}



