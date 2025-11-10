package com.app.ralib.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.app.ralib.R;

/**
 * 现代化的视图切换按钮组件
 * 
 * 特性：
 * - 支持两种状态的图标和文本
 * - 平滑的旋转动画
 * - 可自定义样式
 * - Material Design 风格
 */
public class ViewSwitcherButton extends LinearLayout {
    
    private ImageView iconView;
    private TextView labelView;
    private View containerView;
    
    private Drawable primaryIcon;
    private Drawable secondaryIcon;
    private String primaryLabel;
    private String secondaryLabel;
    
    private boolean isSecondaryState = false;
    private OnStateChangedListener listener;
    private boolean animating = false;
    
    public interface OnStateChangedListener {
        void onStateChanged(boolean isSecondary);
    }
    
    public ViewSwitcherButton(Context context) {
        super(context);
        init(context, null);
    }
    
    public ViewSwitcherButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public ViewSwitcherButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_switcher_button, this, true);
        
        iconView = findViewById(R.id.switcherIcon);
        labelView = findViewById(R.id.switcherLabel);
        containerView = findViewById(R.id.switcherContainer);
        
        // 读取自定义属性
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewSwitcherButton);
            
            primaryIcon = a.getDrawable(R.styleable.ViewSwitcherButton_primaryIcon);
            secondaryIcon = a.getDrawable(R.styleable.ViewSwitcherButton_secondaryIcon);
            primaryLabel = a.getString(R.styleable.ViewSwitcherButton_primaryLabel);
            secondaryLabel = a.getString(R.styleable.ViewSwitcherButton_secondaryLabel);
            
            a.recycle();
        }
        
        // 设置初始状态
        updateUI();
        
        // 点击事件 - 绑定到容器而不是整个 LinearLayout
        containerView.setOnClickListener(v -> toggle());
        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * 切换状态
     */
    public void toggle() {
        if (animating) return;
        
        isSecondaryState = !isSecondaryState;
        animateTransition();
        
        if (listener != null) {
            listener.onStateChanged(isSecondaryState);
        }
    }
    
    /**
     * 设置状态（无动画）
     */
    public void setState(boolean isSecondary) {
        if (isSecondaryState != isSecondary) {
            isSecondaryState = isSecondary;
            updateUI();
        }
    }
    
    /**
     * 设置状态（带动画）
     */
    public void setStateAnimated(boolean isSecondary) {
        if (isSecondaryState != isSecondary) {
            isSecondaryState = isSecondary;
            animateTransition();
        }
    }
    
    private void animateTransition() {
        animating = true;
        
        // 图标旋转动画
        ObjectAnimator rotateAnimator = ObjectAnimator.ofFloat(iconView, "rotation", 0f, 360f);
        rotateAnimator.setDuration(300);
        rotateAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        
        // 缩放动画
        containerView.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150)
                .withEndAction(() -> {
                    updateUI();
                    containerView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .withEndAction(() -> animating = false)
                            .start();
                })
                .start();
        
        rotateAnimator.start();
    }
    
    private void updateUI() {
        if (isSecondaryState) {
            if (secondaryIcon != null) iconView.setImageDrawable(secondaryIcon);
            if (secondaryLabel != null) labelView.setText(secondaryLabel);
        } else {
            if (primaryIcon != null) iconView.setImageDrawable(primaryIcon);
            if (primaryLabel != null) labelView.setText(primaryLabel);
        }
    }
    
    // Getter & Setter
    
    public void setPrimaryIcon(Drawable icon) {
        this.primaryIcon = icon;
        if (!isSecondaryState) updateUI();
    }
    
    public void setSecondaryIcon(Drawable icon) {
        this.secondaryIcon = icon;
        if (isSecondaryState) updateUI();
    }
    
    public void setPrimaryLabel(String label) {
        this.primaryLabel = label;
        if (!isSecondaryState) updateUI();
    }
    
    public void setSecondaryLabel(String label) {
        this.secondaryLabel = label;
        if (isSecondaryState) updateUI();
    }
    
    public void setOnStateChangedListener(OnStateChangedListener listener) {
        this.listener = listener;
    }
    
    public boolean isSecondaryState() {
        return isSecondaryState;
    }
}

