package com.app.ralaunch.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * 虚拟文本控件View
 * 显示文本内容，不支持按键映射，使用按钮的所有外观功能
 */
public class VirtualText extends View implements ControlView {
    private static final String TAG = "VirtualText";
    
    private ControlData mData;
    private ControlInputBridge mInputBridge;
    
    // 绘制相关
    private Paint mBackgroundPaint;
    private Paint mStrokePaint;
    private TextPaint mTextPaint;
    private RectF mRectF;
    
    public VirtualText(Context context, ControlData data, ControlInputBridge bridge) {
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
    
    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRectF.set(0, 0, w, h);
    }
    
    @Override
    public void updateData(ControlData data) {
        mData = data;
        initPaints();
        invalidate();
    }
    
    @Override
    public ControlData getData() {
        return mData;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mData == null || !mData.visible) {
            return;
        }
        
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        // 应用旋转
        if (mData.rotation != 0) {
            canvas.save();
            canvas.rotate(mData.rotation, centerX, centerY);
        }
        
        int shape = mData.shape;
        float radius = Math.min(mRectF.width(), mRectF.height()) / 2f;
        
        // 绘制背景
        if (shape == ControlData.SHAPE_CIRCLE) {
            canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint);
            canvas.drawCircle(centerX, centerY, radius, mStrokePaint);
        } else {
            // 绘制矩形（圆角矩形）- 文本控件默认方形
            float cornerRadius = dpToPx(mData.cornerRadius);
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mBackgroundPaint);
            canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mStrokePaint);
        }
        
        // 绘制文本
        String displayText = mData.displayText != null ? mData.displayText : "";
        
        // 自动计算文字大小以适应区域
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
        
        // 居中显示文本
        float textY = getHeight() / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
        canvas.drawText(displayText, getWidth() / 2f, textY, mTextPaint);
        
        // 恢复旋转
        if (mData.rotation != 0) {
            canvas.restore();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 文本控件不支持触摸事件（不处理按键映射）
        return false;
    }
}

