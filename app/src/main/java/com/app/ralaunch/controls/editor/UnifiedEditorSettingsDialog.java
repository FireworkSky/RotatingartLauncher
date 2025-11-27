package com.app.ralaunch.controls.editor;

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

import androidx.annotation.DrawableRes;

import com.app.ralaunch.R;

/**
 * 统一的MD3风格编辑器设置对话框（侧边滑动弹窗）
 * 支持两种模式：
 * 1. 编辑器模式（ControlEditorActivity）：最后一项为"保存并退出"
 * 2. 游戏模式（GameActivity）：最后一项为"退出编辑"
 */
public class UnifiedEditorSettingsDialog {
    private final Context mContext;
    private final ViewGroup mParent;
    private final int mScreenWidth;
    private ViewGroup mDialogLayout;
    private View mOverlay;
    private ValueAnimator mAnimator;
    private DialogMode mMode;

    // UI元素
    private TextView mTvDialogTitle;
    private View mItemToggleEditMode;
    private ImageView mIvToggleEditModeIcon;
    private TextView mTvToggleEditModeText;
    private View mItemAddButton;
    private View mItemAddJoystick;
    private View mItemAddText;
    private View mItemAddTextGroup;
    private ViewGroup mAddControlsSection; // 添加控件区域
    private View mItemSaveLayout;
    private View mItemLoadLayout;
    private View mItemResetDefault;
    private View mItemLastAction;
    private ImageView mIvLastActionIcon;
    private TextView mTvLastActionText;
    
    // 编辑模式状态
    private boolean mIsEditModeEnabled = false;

    // 监听器
    private OnMenuItemClickListener mListener;

    /**
     * 对话框模式
     */
    public enum DialogMode {
        /** 编辑器模式：独立的编辑器界面，最后一项为"保存并退出" */
        EDITOR(R.drawable.ic_check, "#4CAF50", "编辑器设置", "保存并退出"),

        /** 游戏模式：游戏内编辑，最后一项为"退出编辑" */
        GAME(R.drawable.ic_close, "#F44336", "编辑控制", "退出编辑");

        private final int iconRes;
        private final String iconColor;
        private final String title;
        private final String lastActionText;

        DialogMode(@DrawableRes int iconRes, String iconColor, String title, String lastActionText) {
            this.iconRes = iconRes;
            this.iconColor = iconColor;
            this.title = title;
            this.lastActionText = lastActionText;
        }
    }

    /**
     * 菜单项点击监听器
     */
    public interface OnMenuItemClickListener {
        void onToggleEditMode(); // 切换编辑模式
        void onAddButton();
        void onAddJoystick();
        void onAddText();
        void onSaveLayout();
        void onLoadLayout();
        void onResetDefault();
        void onLastAction(); // 统一的最后一项回调
    }

    public UnifiedEditorSettingsDialog(Context context, ViewGroup parent, int screenWidth, DialogMode mode) {
        mContext = context;
        mParent = parent;
        mScreenWidth = screenWidth;
        mMode = mode;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mListener = listener;
    }

    /**
     * 显示对话框
     */
    public void show() {
        // 如果对话框布局不存在或已被移除，重新创建
        if (mDialogLayout == null || mDialogLayout.getParent() == null) {
            if (mDialogLayout == null) {
                inflateLayout();
            }
            
            // 先添加遮罩层（底层）
            // 如果遮罩层不存在或已被移除，重新创建
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
                // 将遮罩层插入到对话框之前，确保遮罩层在底层
                int dialogIndex = mParent.indexOfChild(mDialogLayout);
                mParent.addView(mOverlay, dialogIndex, overlayParams);
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
        mDialogLayout.setTranslationX(finalDialogWidth);
        
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
            mDialogLayout.setTranslationX(finalDialogWidth * (1f - progress));
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
            mDialogLayout.setTranslationX(finalDialogWidth * progress);
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
        mDialogLayout = (ViewGroup) inflater.inflate(R.layout.dialog_unified_editor_settings, mParent, false);
        
        // 设置背景和阴影
        mDialogLayout.setBackgroundColor(mContext.getResources().getColor(android.R.color.white, mContext.getTheme()));
        mDialogLayout.setElevation(16f);

        // 绑定UI元素
        mTvDialogTitle = mDialogLayout.findViewById(R.id.tv_dialog_title);
        mItemToggleEditMode = mDialogLayout.findViewById(R.id.item_toggle_edit_mode);
        mIvToggleEditModeIcon = mItemToggleEditMode.findViewById(R.id.iv_toggle_edit_mode_icon);
        mTvToggleEditModeText = mItemToggleEditMode.findViewById(R.id.tv_toggle_edit_mode_text);
        mItemAddButton = mDialogLayout.findViewById(R.id.item_add_button);
        mItemAddJoystick = mDialogLayout.findViewById(R.id.item_add_joystick);
        mItemAddText = mDialogLayout.findViewById(R.id.item_add_text);
        mAddControlsSection = mDialogLayout.findViewById(R.id.section_add_controls);
        mItemSaveLayout = mDialogLayout.findViewById(R.id.item_save_layout);
        mItemLoadLayout = mDialogLayout.findViewById(R.id.item_load_layout);
        mItemResetDefault = mDialogLayout.findViewById(R.id.item_reset_default);
        mItemLastAction = mDialogLayout.findViewById(R.id.item_last_action);
        mIvLastActionIcon = mDialogLayout.findViewById(R.id.iv_last_action_icon);
        mTvLastActionText = mDialogLayout.findViewById(R.id.tv_last_action_text);

        // 初始状态：编辑模式关闭，添加控件区域隐藏
        if (mAddControlsSection != null) {
            mAddControlsSection.setVisibility(View.GONE);
        }

        // 配置UI元素
        configureUIForMode();

        // 设置监听器
        setupListeners();
    }

    /**
     * 根据模式配置UI元素
     */
    private void configureUIForMode() {
        // 设置标题
        mTvDialogTitle.setText(mMode.title);

        // 配置最后一项
        mIvLastActionIcon.setImageResource(mMode.iconRes);
        mIvLastActionIcon.setColorFilter(android.graphics.Color.parseColor(mMode.iconColor));
        mTvLastActionText.setText(mMode.lastActionText);
        mTvLastActionText.setTextColor(android.graphics.Color.parseColor(mMode.iconColor));
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
            // 切换编辑模式
            if (mItemToggleEditMode != null) {
                mItemToggleEditMode.setOnClickListener(v -> {
                    mIsEditModeEnabled = !mIsEditModeEnabled;
                    updateEditModeUI();
                    mListener.onToggleEditMode();
                    // 在游戏模式下，切换编辑模式后不关闭对话框，保持显示状态
                    // 在编辑器模式下也保持显示，方便继续操作
                });
            }

            mItemAddButton.setOnClickListener(v -> {
                mListener.onAddButton();
                hide();
            });

            mItemAddJoystick.setOnClickListener(v -> {
                mListener.onAddJoystick();
                hide();
            });

            mItemAddText.setOnClickListener(v -> {
                mListener.onAddText();
                hide();
            });

            mItemSaveLayout.setOnClickListener(v -> {
                mListener.onSaveLayout();
                hide();
            });

            mItemLoadLayout.setOnClickListener(v -> {
                mListener.onLoadLayout();
                hide();
            });

            mItemResetDefault.setOnClickListener(v -> {
                mListener.onResetDefault();
                hide();
            });

            mItemLastAction.setOnClickListener(v -> {
                mListener.onLastAction();
                // 不自动隐藏，让回调函数决定是否需要隐藏
            });
        }
    }

    /**
     * 更新编辑模式UI（包括按钮文本、图标和添加控件区域的可见性）
     */
    private void updateEditModeUI() {
        // 更新添加控件区域的可见性
        if (mAddControlsSection != null) {
            mAddControlsSection.setVisibility(mIsEditModeEnabled ? View.VISIBLE : View.GONE);
        }

        // 更新"切换编辑模式"按钮的文本和图标
        if (mTvToggleEditModeText != null && mIvToggleEditModeIcon != null) {
            if (mIsEditModeEnabled) {
                // 已进入编辑模式，显示"退出编辑模式"
                mTvToggleEditModeText.setText("退出编辑模式");
                mIvToggleEditModeIcon.setImageResource(R.drawable.ic_close);
                mIvToggleEditModeIcon.setColorFilter(android.graphics.Color.parseColor("#F44336")); // 红色
            } else {
                // 未进入编辑模式，显示"进入编辑模式"
                mTvToggleEditModeText.setText("进入编辑模式");
                mIvToggleEditModeIcon.setImageResource(R.drawable.ic_edit);
                // 恢复默认颜色
                mIvToggleEditModeIcon.setColorFilter(null);
            }
        }
    }

    /**
     * 设置编辑模式状态
     */
    public void setEditModeEnabled(boolean enabled) {
        mIsEditModeEnabled = enabled;
        if (mDialogLayout != null) {
            updateEditModeUI();
        }
    }
}
