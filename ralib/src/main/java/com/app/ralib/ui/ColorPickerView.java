package com.app.ralib.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 自定义颜色选择器视图
 *
 * 布局说明：
 * - 左侧：颜色选择区域（饱和度和明度）
 * - 右侧：色调选择条（垂直）
 * - 底部：透明度选择条（水平）
 */
public class ColorPickerView extends View {
    private static final int HUE_BAR_WIDTH = 40;  // 色调条宽度
    private static final int BAR_MARGIN = 16;  // 间距
    private static final int CURSOR_RADIUS = 12;  // 光标半径
    private static final int CURSOR_STROKE_WIDTH = 3;  // 光标边框宽度

    // HSV 颜色值
    private float hue = 0f;  // 色调 [0, 360)
    private float saturation = 1f;  // 饱和度 [0, 1]
    private float value = 1f;  // 明度 [0, 1]
    private int alpha = 255;  // 透明度 [0, 255]

    // 画笔
    private Paint colorPaint;
    private Paint huePaint;
    private Paint cursorPaint;
    private Paint cursorStrokePaint;
    private Paint checkerPaint;

    // 区域
    private RectF colorRect = new RectF();
    private RectF hueRect = new RectF();

    // 光标位置
    private float colorX = 0f;
    private float colorY = 0f;
    private float hueY = 0f;

    // 交互状态
    private int activeTracker = 0;  // 0=无, 1=颜色, 2=色调

    // 回调
    private OnColorChangedListener listener;

    // 棋盘格背景（用于显示透明度）
    private Bitmap checkerBitmap;

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    public ColorPickerView(Context context) {
        super(context);
        init();
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化画笔
        colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(Color.WHITE);
        cursorPaint.setStyle(Paint.Style.FILL);

        cursorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorStrokePaint.setColor(Color.BLACK);
        cursorStrokePaint.setStyle(Paint.Style.STROKE);
        cursorStrokePaint.setStrokeWidth(CURSOR_STROKE_WIDTH);

        checkerPaint = new Paint();
        checkerPaint.setAntiAlias(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRects();
        updateCursorPositions();
    }

    private void updateRects() {
        int width = getWidth();
        int height = getHeight();

        // 颜色选择区域（左侧，正方形，占据更多空间）
        int colorSize = Math.min(
            width - HUE_BAR_WIDTH - BAR_MARGIN * 3,
            height - BAR_MARGIN * 2
        );
        colorRect.set(
            BAR_MARGIN,
            BAR_MARGIN,
            BAR_MARGIN + colorSize,
            BAR_MARGIN + colorSize
        );

        // 色调条（右侧，垂直）
        hueRect.set(
            colorRect.right + BAR_MARGIN,
            colorRect.top,
            colorRect.right + BAR_MARGIN + HUE_BAR_WIDTH,
            colorRect.bottom
        );
    }

    // 移除不再需要的 createCheckerBitmap 和 checkerBitmap 字段
    private void createCheckerBitmap() {
        // 已弃用
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制颜色选择区域（包含透明度渐变）
        drawColorPicker(canvas);

        // 绘制色调条
        drawHueBar(canvas);

        // 绘制光标
        drawCursors(canvas);
    }

    private void drawColorPicker(Canvas canvas) {
        // 1. 绘制饱和度渐变（从白色到纯色，水平方向）
        // 左边是白色，右边是当前色相的纯色
        LinearGradient saturationGradient = new LinearGradient(
            colorRect.left, 0,
            colorRect.right, 0,
            Color.WHITE,
            Color.HSVToColor(new float[]{hue, 1f, 1f}),
            Shader.TileMode.CLAMP
        );
        colorPaint.setShader(saturationGradient);
        canvas.drawRoundRect(colorRect, 12, 12, colorPaint);

        // 2. 绘制明度渐变（从透明到黑色，垂直方向）
        // 顶部是完全透明（显示底下的饱和度层），底部是黑色
        LinearGradient valueGradient = new LinearGradient(
            0, colorRect.top,
            0, colorRect.bottom,
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        );
        colorPaint.setShader(valueGradient);
        canvas.drawRoundRect(colorRect, 12, 12, colorPaint);
    }

    private void drawHueBar(Canvas canvas) {
        // 创建彩虹渐变
        int[] hueColors = new int[7];
        hueColors[0] = Color.HSVToColor(new float[]{0f, 1f, 1f});      // 红
        hueColors[1] = Color.HSVToColor(new float[]{60f, 1f, 1f});     // 黄
        hueColors[2] = Color.HSVToColor(new float[]{120f, 1f, 1f});    // 绿
        hueColors[3] = Color.HSVToColor(new float[]{180f, 1f, 1f});    // 青
        hueColors[4] = Color.HSVToColor(new float[]{240f, 1f, 1f});    // 蓝
        hueColors[5] = Color.HSVToColor(new float[]{300f, 1f, 1f});    // 品红
        hueColors[6] = Color.HSVToColor(new float[]{360f, 1f, 1f});    // 红

        LinearGradient hueGradient = new LinearGradient(
            0, hueRect.top,
            0, hueRect.bottom,
            hueColors,
            null,
            Shader.TileMode.CLAMP
        );

        huePaint.setShader(hueGradient);
        canvas.drawRoundRect(hueRect, 12, 12, huePaint);
    }


    private void drawCursors(Canvas canvas) {
        // 颜色选择区域光标
        canvas.drawCircle(colorX, colorY, CURSOR_RADIUS, cursorStrokePaint);
        canvas.drawCircle(colorX, colorY, CURSOR_RADIUS - CURSOR_STROKE_WIDTH, cursorPaint);

        // 色调条光标
        float hueLeft = hueRect.left - 6;
        float hueRight = hueRect.right + 6;
        canvas.drawRoundRect(
            new RectF(hueLeft, hueY - 6, hueRight, hueY + 6),
            4, 4, cursorStrokePaint
        );
        canvas.drawRoundRect(
            new RectF(hueLeft + CURSOR_STROKE_WIDTH, hueY - 3,
                     hueRight - CURSOR_STROKE_WIDTH, hueY + 3),
            2, 2, cursorPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 判断触摸的是哪个区域
                if (colorRect.contains(x, y)) {
                    activeTracker = 1;
                    // 请求父视图不要拦截触摸事件
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateColorFromTouch(x, y);
                } else if (hueRect.contains(x, y)) {
                    activeTracker = 2;
                    // 请求父视图不要拦截触摸事件
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateHueFromTouch(y);
                } else {
                    return false;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeTracker == 1) {
                    updateColorFromTouch(x, y);
                } else if (activeTracker == 2) {
                    updateHueFromTouch(y);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeTracker = 0;
                // 恢复父视图的触摸事件拦截
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateColorFromTouch(float x, float y) {
        // 限制在区域内
        x = Math.max(colorRect.left, Math.min(x, colorRect.right));
        y = Math.max(colorRect.top, Math.min(y, colorRect.bottom));

        // X轴：饱和度（从左到右：0到1）
        saturation = (x - colorRect.left) / colorRect.width();
        saturation = Math.max(0f, Math.min(1f, saturation));
        
        // Y轴：明度（从上到下：1到0）
        float yRatio = (y - colorRect.top) / colorRect.height();
        yRatio = Math.max(0f, Math.min(1f, yRatio));
        
        // 明度：上到下从1到0
        value = 1f - yRatio;
        value = Math.max(0f, Math.min(1f, value));
        
        // 注意：在这个选择器中，Y轴不再控制透明度(alpha)，只控制明度(value)
        // alpha 保持不变（或者是 setColor 设置的值）

        // 更新光标位置
        colorX = x;
        colorY = y;

        notifyColorChanged();
    }

    private void updateHueFromTouch(float y) {
        // 限制在区域内
        y = Math.max(hueRect.top, Math.min(y, hueRect.bottom));

        // 更新色调
        hue = (y - hueRect.top) / hueRect.height() * 360f;

        // 更新光标位置
        hueY = y;

        notifyColorChanged();
    }


    private void updateCursorPositions() {
        // 更新颜色选择区域光标
        // X轴：饱和度，Y轴：明度（从上到下：1到0）
        colorX = colorRect.left + saturation * colorRect.width();
        float yRatio = 1f - value; 
        colorY = colorRect.top + yRatio * colorRect.height();

        // 更新色调条光标
        hueY = hueRect.top + (hue / 360f) * hueRect.height();
    }

    private void notifyColorChanged() {
        invalidate();
        if (listener != null) {
            listener.onColorChanged(getColor());
        }
    }

    // ==================== 公开 API ====================

    public void setColor(int color) {
        alpha = Color.alpha(color);
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        

        updateCursorPositions();
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(alpha, new float[]{hue, saturation, value});
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }
}
