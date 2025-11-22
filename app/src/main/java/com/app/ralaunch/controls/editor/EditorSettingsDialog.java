package com.app.ralaunch.controls.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.app.ralaunch.R;

/**
 * MD3风格的编辑器设置右侧弹窗
 */
public class EditorSettingsDialog {
    private final ViewGroup mParent;
    private ViewGroup mDialogLayout;
    private View mOverlay;
    private ObjectAnimator mAnimator;
    private ObjectAnimator mOverlayAnimator;
    private boolean mDisplaying = false;
    private int mScreenWidth;

    // 菜单项
    private View mItemAddButton;
    private View mItemAddJoystick;
    private View mItemJoystickMode;
    private View mItemSaveLayout;
    private View mItemLoadLayout;
    private View mItemResetDefault;
    private View mItemSaveAndExit;

    // 监听器
    private OnMenuItemClickListener mListener;

    public interface OnMenuItemClickListener {
        void onAddButton();
        void onAddJoystick();
        void onJoystickModeSettings();
        void onSaveLayout();
        void onLoadLayout();
        void onResetDefault();
        void onSaveAndExit();
    }

    public EditorSettingsDialog(Context context, ViewGroup parent, int screenWidth) {
        mParent = parent;
        mScreenWidth = screenWidth;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mListener = listener;
    }

    /**
     * 显示设置弹窗
     */
    public void show() {
        if (mDialogLayout == null) {
            inflateLayout();
        }

        if (!mDisplaying) {
            int dialogWidth = (int)(320 * mParent.getResources().getDisplayMetrics().density);

            // 显示遮罩层
            mOverlay.setVisibility(View.VISIBLE);
            mOverlay.setAlpha(0f);
            mOverlayAnimator.setFloatValues(0f, 1f);
            mOverlayAnimator.start();

            // 显示对话框（从右侧滑入）
            mDialogLayout.setVisibility(View.VISIBLE);
            mDialogLayout.setX(mScreenWidth);

            mAnimator.setFloatValues(mScreenWidth, mScreenWidth - dialogWidth);
            mAnimator.start();
            mDisplaying = true;
        }
    }

    /**
     * 隐藏设置弹窗
     */
    public void hide() {
        if (!mDisplaying || mDialogLayout == null) return;

        // 隐藏遮罩层
        mOverlayAnimator.setFloatValues(1f, 0f);
        mOverlayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mOverlay != null) {
                    mOverlay.setVisibility(View.GONE);
                }
                mOverlayAnimator.removeListener(this);
            }
        });
        mOverlayAnimator.start();

        // 隐藏对话框
        mAnimator.setFloatValues(mDialogLayout.getX(), mScreenWidth);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDialogLayout != null) {
                    mDialogLayout.setVisibility(View.GONE);
                }
                mAnimator.removeListener(this);
            }
        });
        mAnimator.start();
        mDisplaying = false;
    }

    public boolean isDisplaying() {
        return mDisplaying;
    }

    /**
     * 初始化布局
     */
    private void inflateLayout() {
        Context context = mParent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // 创建半透明遮罩层
        mOverlay = new View(context);
        mOverlay.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mOverlay.setBackgroundColor(0x80000000);
        mOverlay.setVisibility(View.GONE);
        mOverlay.setElevation(15);
        mOverlay.setOnClickListener(v -> hide());
        mParent.addView(mOverlay);

        // 加载对话框布局
        mDialogLayout = (ViewGroup) inflater.inflate(R.layout.dialog_editor_settings, mParent, false);

        // 绑定UI元素
        mItemAddButton = mDialogLayout.findViewById(R.id.item_add_button);
        mItemAddJoystick = mDialogLayout.findViewById(R.id.item_add_joystick);
        mItemJoystickMode = mDialogLayout.findViewById(R.id.item_joystick_mode);
        mItemSaveLayout = mDialogLayout.findViewById(R.id.item_save_layout);
        mItemLoadLayout = mDialogLayout.findViewById(R.id.item_load_layout);
        mItemResetDefault = mDialogLayout.findViewById(R.id.item_reset_default);
        mItemSaveAndExit = mDialogLayout.findViewById(R.id.item_save_and_exit);

        // 设置监听器
        setupListeners();

        // 添加到父布局
        mParent.addView(mDialogLayout);
        mDialogLayout.setVisibility(View.GONE);
        mDialogLayout.setElevation(20);

        // 创建动画
        mAnimator = ObjectAnimator.ofFloat(mDialogLayout, "x", 0).setDuration(300);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        mOverlayAnimator = ObjectAnimator.ofFloat(mOverlay, "alpha", 0f).setDuration(250);
    }

    private void setupListeners() {
        // 关闭按钮
        mDialogLayout.findViewById(R.id.btn_close_settings).setOnClickListener(v -> hide());

        // 添加按键
        mItemAddButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onAddButton();
            }
            hide();
        });

        // 添加摇杆
        mItemAddJoystick.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onAddJoystick();
            }
            hide();
        });

        // 摇杆模式设置
        mItemJoystickMode.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onJoystickModeSettings();
            }
            hide();
        });

        // 保存布局
        mItemSaveLayout.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onSaveLayout();
            }
            hide();
        });

        // 加载布局
        mItemLoadLayout.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onLoadLayout();
            }
            hide();
        });

        // 重置为默认
        mItemResetDefault.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onResetDefault();
            }
            hide();
        });

        // 保存并退出
        mItemSaveAndExit.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onSaveAndExit();
            }
        });
    }
}
