package com.app.ralaunch.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.app.ralaunch.R;

/**
 * 布局设置侧边弹窗（MD3风格）
 */
public class ControlLayoutSettingsDialog {
    private final Context mContext;
    private final ViewGroup mParent;
    private final int mScreenWidth;
    private ViewGroup mDialogLayout;
    private View mOverlay;
    private ValueAnimator mAnimator;

    // UI元素
    private View mItemImportLayout;
    private View mItemImportPreset;

    // 监听器
    private OnMenuItemClickListener mListener;

    /**
     * 菜单项点击监听器
     */
    public interface OnMenuItemClickListener {
        void onImportLayout(); // 导入布局（从文件）
        void onImportPreset(); // 导入预设
    }

    public ControlLayoutSettingsDialog(Context context, ViewGroup parent, int screenWidth) {
        mContext = context;
        mParent = parent;
        mScreenWidth = screenWidth;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mListener = listener;
    }

    /**
     * 显示对话框
     */
    public void show() {
        if (mDialogLayout == null) {
            inflateLayout();
        }
        
        if (mDialogLayout.getParent() == null) {
            // 先添加遮罩层（底层）
            if (mOverlay == null || mOverlay.getParent() == null) {
                if (mOverlay != null && mOverlay.getParent() != null) {
                    mParent.removeView(mOverlay);
                }
                mOverlay = new View(mContext);
                mOverlay.setBackgroundColor(Color.parseColor("#80000000"));
                mOverlay.setAlpha(0f);
                mOverlay.setClickable(true);
                mOverlay.setFocusable(true);
                mOverlay.setOnClickListener(v -> hide());
                
                FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                mParent.addView(mOverlay, overlayParams);
            }
            
            // 后添加对话框布局（上层，在遮罩层之上）- 使用固定宽度，避免占用过多空间
            int dialogWidth = (int) (mContext.getResources().getDisplayMetrics().density * 320); // 320dp
            FrameLayout.LayoutParams dialogParams = new FrameLayout.LayoutParams(
                dialogWidth,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            dialogParams.gravity = android.view.Gravity.END;
            // 确保对话框在遮罩层之上
            mParent.addView(mDialogLayout, dialogParams);
            
            // 设置对话框布局的触摸监听，检测点击外部区域时关闭对话框
            mDialogLayout.setOnTouchListener((v, event) -> {
                // 如果触摸事件在对话框外部（遮罩层区域），关闭对话框
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    // 如果点击在对话框外部（左侧、上方、下方），关闭对话框
                    if (x < 0 || y < 0 || y > mDialogLayout.getHeight()) {
                        hide();
                        return true;
                    }
                }
                return false; // 让对话框内容正常处理触摸事件
            });
            
            // 等待布局测量完成后再执行动画
            mDialogLayout.post(() -> animateShow());
        } else {
            // 如果已经添加，确保遮罩层也存在
            if (mOverlay == null || mOverlay.getParent() == null) {
                if (mOverlay != null && mOverlay.getParent() != null) {
                    mParent.removeView(mOverlay);
                }
                mOverlay = new View(mContext);
                mOverlay.setBackgroundColor(Color.parseColor("#80000000"));
                mOverlay.setAlpha(0f);
                mOverlay.setClickable(true);
                mOverlay.setFocusable(true);
                mOverlay.setOnClickListener(v -> hide());
                
                FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                mParent.addView(mOverlay, overlayParams);
            }
            // 直接显示动画
            animateShow();
        }
    }

    /**
     * 隐藏对话框
     */
    public void hide() {
        if (mDialogLayout != null && mDialogLayout.getParent() != null) {
            animateHide();
        }
    }

    public boolean isDisplaying() {
        return mDialogLayout != null && mDialogLayout.getParent() != null && mDialogLayout.getVisibility() == View.VISIBLE;
    }
    
    /**
     * 显示动画
     */
    private void animateShow() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        
        // 检查必要的视图是否存在
        if (mDialogLayout == null) {
            return;
        }
        
        float dialogWidth = mDialogLayout.getWidth();
        if (dialogWidth == 0) {
            dialogWidth = mContext.getResources().getDisplayMetrics().density * 320; // 320dp
        }
        final float finalDialogWidth = dialogWidth;
        
        mDialogLayout.setVisibility(View.VISIBLE);
        mDialogLayout.setTranslationX(-finalDialogWidth);
        
        // 确保遮罩层存在
        if (mOverlay != null) {
            mOverlay.setVisibility(View.VISIBLE);
            mOverlay.setAlpha(0f);
        }
        
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(300);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            mDialogLayout.setTranslationX(-finalDialogWidth * (1f - progress));
            if (mOverlay != null) {
                mOverlay.setAlpha(progress * 0.5f);
            }
        });
        mAnimator.start();
    }
    
    /**
     * 隐藏动画
     */
    private void animateHide() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        
        float dialogWidth = mDialogLayout.getWidth();
        if (dialogWidth == 0) {
            dialogWidth = mContext.getResources().getDisplayMetrics().density * 320; // 320dp
        }
        final float finalDialogWidth = dialogWidth;
        
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(300);
        mAnimator.setInterpolator(new AccelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            mDialogLayout.setTranslationX(finalDialogWidth * -progress);
            if (mOverlay != null) {
                mOverlay.setAlpha(0.5f * (1f - progress));
            }
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDialogLayout != null && mDialogLayout.getParent() != null) {
                    mParent.removeView(mDialogLayout);
                }
                if (mOverlay != null && mOverlay.getParent() != null) {
                    mParent.removeView(mOverlay);
                }
                // 清理引用，确保下次打开时重新创建
                mOverlay = null;
            }
        });
        mAnimator.start();
    }

    /**
     * 初始化布局
     */
    private void inflateLayout() {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        // 加载对话框布局
        mDialogLayout = (ViewGroup) inflater.inflate(R.layout.dialog_layout_settings, mParent, false);

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        mDialogLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 设置背景和阴影
        mDialogLayout.setBackgroundColor(mContext.getResources().getColor(android.R.color.white, mContext.getTheme()));
        mDialogLayout.setElevation(16f);

        // 绑定UI元素
        mItemImportLayout = mDialogLayout.findViewById(R.id.item_import_layout);
        mItemImportPreset = mDialogLayout.findViewById(R.id.item_import_preset);

        // 设置监听器
        setupListeners();
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 关闭按钮
        mDialogLayout.findViewById(R.id.btn_close_settings).setOnClickListener(v -> {
            hide();
        });

        // 菜单项点击
        if (mListener != null) {
            mItemImportLayout.setOnClickListener(v -> {
                mListener.onImportLayout();
                hide();
            });

            mItemImportPreset.setOnClickListener(v -> {
                mListener.onImportPreset();
                hide();
            });
        }
    }
}

