package com.app.ralaunch.utils;

import android.graphics.Color;

/**
 * 颜色工具类
 * 提供颜色计算和转换的实用方法
 */
public class ColorUtils {
    
    /**
     * 计算主色调的对比色（用于文本和图标）
     */
    public static int calculateOnPrimaryColor(int color) {
        // 计算颜色的亮度
        double luminance = (0.299 * Color.red(color) +
                           0.587 * Color.green(color) +
                           0.114 * Color.blue(color)) / 255.0;
        
        // 如果颜色较亮，使用深色文本；否则使用浅色文本
        return luminance > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }
    
    /**
     * 判断颜色是否为浅色
     */
    public static boolean isColorLight(int color) {
        double luminance = (0.299 * Color.red(color) +
                           0.587 * Color.green(color) +
                           0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.5;
    }
    
    /**
     * 计算 PrimaryContainer 颜色（主色调的浅色版本）
     */
    public static int calculatePrimaryContainerColor(int primaryColor) {
        // 将主色调与白色混合，创建浅色版本
        int white = 0xFFFFFFFF;
        return blendColors(primaryColor, white, 0.85f); // 85% 白色，15% 主色调
    }
    
    /**
     * 计算 OnPrimaryContainer 颜色（PrimaryContainer 上的文本颜色）
     */
    public static int calculateOnPrimaryContainerColor(int primaryColor) {
        // 使用主色调的深色版本
        int black = 0xFF000000;
        return blendColors(primaryColor, black, 0.3f); // 30% 主色调，70% 黑色
    }
    
    /**
     * 混合两种颜色
     */
    public static int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1.0f - ratio;
        float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRatio);
        float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRatio);
        float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRatio);
        return Color.rgb((int) r, (int) g, (int) b);
    }
    
    /**
     * 检查两个颜色是否相似
     */
    public static boolean isColorSimilar(int color1, int color2, int threshold) {
        int diffR = Math.abs(Color.red(color1) - Color.red(color2));
        int diffG = Math.abs(Color.green(color1) - Color.green(color2));
        int diffB = Math.abs(Color.blue(color1) - Color.blue(color2));
        return (diffR + diffG + diffB) < threshold * 3;
    }
}


