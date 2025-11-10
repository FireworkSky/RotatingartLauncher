package com.app.ralib.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.app.ralib.R;

/**
 * 现代化进度条组件
 * 特点：
 * - 大尺寸、圆角、渐变色
 * - 显示进度百分比和状态文本
 * - 平滑动画
 */
public class ModernProgressBar extends LinearLayout {
    
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    
    private int progressColor;
    private boolean showPercentage = true;
    
    public ModernProgressBar(Context context) {
        super(context);
        init(context, null);
    }
    
    public ModernProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public ModernProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.ralib_modern_progress_bar, this, true);
        
        // 获取视图引用
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        statusText = findViewById(R.id.statusText);
        
        // 读取自定义属性
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ModernProgressBar);
            progressColor = ta.getColor(R.styleable.ModernProgressBar_progressColor, 0xFF4CAF50);
            showPercentage = ta.getBoolean(R.styleable.ModernProgressBar_showPercentage, true);
            ta.recycle();
        }
        
        // 应用属性
        updateUI();
    }
    
    private void updateUI() {
        if (progressColor != 0) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(progressColor));
        }
        
        progressText.setVisibility(showPercentage ? VISIBLE : GONE);
    }
    
    /**
     * 设置进度（0-100）
     */
    public void setProgress(int progress) {
        progressBar.setProgress(progress);
        if (showPercentage) {
            progressText.setText(progress + "%");
        }
    }
    
    /**
     * 获取当前进度
     */
    public int getProgress() {
        return progressBar.getProgress();
    }
    
    /**
     * 设置状态文本
     */
    public void setStatusText(String text) {
        statusText.setText(text);
    }
    
    /**
     * 设置进度颜色
     */
    public void setProgressColor(int color) {
        this.progressColor = color;
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
    }
    
    /**
     * 设置是否显示百分比
     */
    public void setShowPercentage(boolean show) {
        this.showPercentage = show;
        progressText.setVisibility(show ? VISIBLE : GONE);
    }
    
    /**
     * 重置进度
     */
    public void reset() {
        setProgress(0);
        setStatusText("");
    }
}

