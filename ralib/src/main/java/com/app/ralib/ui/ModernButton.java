package com.app.ralib.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.app.ralib.R;

/**
 * 现代化按钮组件
 * 特点：
 * - 大尺寸、高对比度
 * - 支持图标和文本
 * - 渐变背景和阴影效果
 * - 点击动画
 */
public class ModernButton extends LinearLayout {
    
    private com.google.android.material.card.MaterialCardView containerView;
    private ImageView iconView;
    private TextView textView;
    
    private String buttonText;
    private Drawable buttonIcon;
    private int buttonColor;
    
    public ModernButton(Context context) {
        super(context);
        init(context, null);
    }
    
    public ModernButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public ModernButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.ralib_modern_button, this, true);
        
        // 获取视图引用
        containerView = findViewById(R.id.buttonContainer);
        iconView = findViewById(R.id.buttonIcon);
        textView = findViewById(R.id.buttonText);
        
        // 读取自定义属性
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ModernButton);
            buttonText = ta.getString(R.styleable.ModernButton_buttonText);
            buttonIcon = ta.getDrawable(R.styleable.ModernButton_buttonIcon);
            buttonColor = ta.getColor(R.styleable.ModernButton_buttonColor, 0xFF4CAF50);
            ta.recycle();
        }
        
        // 应用属性
        updateUI();
        
        // 设置点击效果
        setClickable(true);
        setFocusable(true);
        containerView.setOnClickListener(v -> {
            // 点击缩放动画
            containerView.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        containerView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .withEndAction(() -> {
                                    // 触发父级点击事件
                                    performClick();
                                })
                                .start();
                    })
                    .start();
        });
    }
    
    private void updateUI() {
        if (buttonText != null) {
            textView.setText(buttonText);
        }
        
        if (buttonIcon != null) {
            iconView.setImageDrawable(buttonIcon);
            iconView.setVisibility(VISIBLE);
        } else {
            iconView.setVisibility(GONE);
        }
        
        if (buttonColor != 0) {
            containerView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor));
        }
    }
    
    public void setText(String text) {
        this.buttonText = text;
        textView.setText(text);
    }
    
    public void setIcon(Drawable icon) {
        this.buttonIcon = icon;
        if (icon != null) {
            iconView.setImageDrawable(icon);
            iconView.setVisibility(VISIBLE);
        } else {
            iconView.setVisibility(GONE);
        }
    }
    
    public void setButtonColor(int color) {
        this.buttonColor = color;
        containerView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        containerView.setAlpha(enabled ? 1.0f : 0.5f);
        containerView.setClickable(enabled);
    }
}

