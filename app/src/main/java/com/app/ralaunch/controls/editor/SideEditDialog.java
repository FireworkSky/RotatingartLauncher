package com.app.ralaunch.controls.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlView;
import com.app.ralaunch.controls.KeyMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SideEditDialog {
    private final ViewGroup mParent;
    private ViewGroup mDialogLayout;
    private View mOverlay; // 半透明遮罩层
    private ObjectAnimator mAnimator;
    private ObjectAnimator mOverlayAnimator;
    private boolean mDisplaying = false;

    // UI 元素
    private EditText mEtName;
    private SeekBar mSeekbarX, mSeekbarY, mSeekbarWidth, mSeekbarHeight, mSeekbarSize, mSeekbarOpacity;
    private TextView mTvXValue, mTvYValue, mTvWidthValue, mTvHeightValue, mTvSizeValue, mTvOpacityValue;
    private CheckBox mCheckboxVisible;
    private CheckBox mCheckboxKeepAspectRatio;
    private Button mBtnSelectKey;
    private TextView mTvKeymapLabel;

    // 新增UI元素
    private CheckBox mCheckboxToggle;
    private TextView mTvToggleModeLabel;
    private Button mBtnBgColor, mBtnStrokeColor;
    private SeekBar mSeekbarStrokeWidth, mSeekbarCornerRadius;
    private TextView mTvStrokeWidthValue, mTvCornerRadiusValue;
    private Button mBtnDuplicate, mBtnDelete;

    private ControlData mCurrentData;
    private ControlView mCurrentView;
    private int mScreenWidth, mScreenHeight;
    private boolean mIsUpdating = false; // 防止递归更新

    public SideEditDialog(Context context, ViewGroup parent, int screenWidth, int screenHeight) {
        mParent = parent;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
    }

    /**
     * 【核心方法】统一的实时更新方法 - 同时更新数据和视图
     * 所有属性修改都通过这个方法来确保视图同步
     */
    private void applyRealtimeUpdate() {
        if (mCurrentData == null || mCurrentView == null || mIsUpdating) {
            return;
        }

        mIsUpdating = true;
        try {
            View view = (View) mCurrentView;

            // 1. 更新布局参数（位置和大小）
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                // 使用 FrameLayout.LayoutParams（ControlLayout 使用的类型）
                FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
                frameParams.width = (int) mCurrentData.width;
                frameParams.height = (int) mCurrentData.height;
                frameParams.leftMargin = (int) mCurrentData.x;
                frameParams.topMargin = (int) mCurrentData.y;
                view.setLayoutParams(frameParams);
            } else if (layoutParams != null) {
                // 备用方案：使用通用的 MarginLayoutParams
                layoutParams.width = (int) mCurrentData.width;
                layoutParams.height = (int) mCurrentData.height;
                if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
                    marginParams.leftMargin = (int) mCurrentData.x;
                    marginParams.topMargin = (int) mCurrentData.y;
                }
                view.setLayoutParams(layoutParams);
            }

            // 2. 更新视觉属性
            view.setAlpha(mCurrentData.opacity);
            view.setVisibility(mCurrentData.visible ? View.VISIBLE : View.INVISIBLE);

            // 3. 通知控件视图更新数据（刷新绘制，如颜色、边框等）
            mCurrentView.updateData(mCurrentData);

            // 4. 强制重新测量、布局和绘制
            view.forceLayout();
            view.requestLayout();
            view.invalidate();

            // 5. 通知父布局重新布局
            if (view.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) view.getParent();
                parent.requestLayout();
                parent.invalidate();
            }

        } finally {
            mIsUpdating = false;
        }
    }

    /**
     * 递归查找 ControlView
     * 解决控件在嵌套的 ControlLayout 中找不到的问题
     */
    private ControlView findControlViewRecursive(ViewGroup parent, ControlData data) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ControlView && ((ControlView) child).getData() == data) {
                return (ControlView) child;
            }
            // 递归查找子 ViewGroup
            if (child instanceof ViewGroup) {
                ControlView found = findControlViewRecursive((ViewGroup) child, data);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 显示编辑对话框
     */
    public void show(ControlData data) {
        if (mDialogLayout == null) {
            inflateLayout();
        }

        mCurrentData = data;

        // 递归查找对应的 ControlView（解决控件在嵌套布局中找不到的问题）
        mCurrentView = findControlViewRecursive(mParent, data);

        // 填充数据
        mEtName.setText(data.name);
        int xPercent = (int)(data.x / mScreenWidth * 100);
        int yPercent = (int)(data.y / mScreenHeight * 100);
        int widthPercent = (int)(data.width / mScreenWidth * 100);
        int heightPercent = (int)(data.height / mScreenHeight * 100);
        int opacityPercent = (int)(data.opacity * 100);

        mSeekbarX.setProgress(xPercent);
        mTvXValue.setText(xPercent + "%");
        mSeekbarY.setProgress(yPercent);
        mTvYValue.setText(yPercent + "%");
        mSeekbarWidth.setProgress(widthPercent);
        mTvWidthValue.setText(widthPercent + "%");
        mSeekbarHeight.setProgress(heightPercent);
        mTvHeightValue.setText(heightPercent + "%");

        // 保持宽高比复选框（摇杆默认勾选）
        mCheckboxKeepAspectRatio.setChecked(data.type == ControlData.TYPE_JOYSTICK);

        // 尺寸滑块（用于等比例调整）- 使用平均值
        int sizePercent = (int)((widthPercent + heightPercent) / 2);
        mSeekbarSize.setProgress(sizePercent);
        mTvSizeValue.setText(sizePercent + "%");

        mSeekbarOpacity.setProgress(opacityPercent);
        mTvOpacityValue.setText(opacityPercent + "%");
        mCheckboxVisible.setChecked(data.visible);

        // 填充新增控件的数据
        mSeekbarStrokeWidth.setProgress((int)data.strokeWidth);
        mTvStrokeWidthValue.setText(String.valueOf((int)data.strokeWidth));
        mSeekbarCornerRadius.setProgress((int)data.cornerRadius);
        mTvCornerRadiusValue.setText(String.valueOf((int)data.cornerRadius));
        updateColorButtons();

        // 根据控件类型显示不同的编辑选项
        if (data.type == ControlData.TYPE_BUTTON) {
            // 按钮：显示按键映射和切换模式
            mTvKeymapLabel.setVisibility(View.VISIBLE);
            mBtnSelectKey.setVisibility(View.VISIBLE);
            String keyName = KeyMapper.getKeyName(data.keycode);
            mBtnSelectKey.setText(keyName);

            // 显示切换模式选项
            mTvToggleModeLabel.setVisibility(View.VISIBLE);
            mCheckboxToggle.setVisibility(View.VISIBLE);
            mCheckboxToggle.setChecked(data.isToggle);

        } else if (data.type == ControlData.TYPE_JOYSTICK) {
            // 摇杆模式设置已移至编辑器设置中的"摇杆模式设置"菜单

            // 隐藏按钮相关选项
            mTvKeymapLabel.setVisibility(View.GONE);
            mBtnSelectKey.setVisibility(View.GONE);
            mTvToggleModeLabel.setVisibility(View.GONE);
            mCheckboxToggle.setVisibility(View.GONE);
        }

        // 显示动画
        if (!mDisplaying) {
            int screenWidth = mParent.getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = (int)(320 * mParent.getResources().getDisplayMetrics().density);

            // 显示遮罩层（淡入）
            mOverlay.setVisibility(View.VISIBLE);
            mOverlay.setAlpha(0f);
            mOverlayAnimator.setFloatValues(0f, 1f);
            mOverlayAnimator.start();

            // 显示对话框（滑入）
            mDialogLayout.setVisibility(View.VISIBLE);
            mDialogLayout.setX(screenWidth);

            mAnimator.setFloatValues(screenWidth, screenWidth - dialogWidth);
            mAnimator.start();
            mDisplaying = true;
        }
    }

    /**
     * 隐藏编辑对话框
     */
    public void hide() {
        if (!mDisplaying || mDialogLayout == null) return;

        // 隐藏遮罩层（淡出）
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

        // 隐藏对话框（滑出）
        int screenWidth = mParent.getResources().getDisplayMetrics().widthPixels;
        mAnimator.setFloatValues(mDialogLayout.getX(), screenWidth);
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
        mOverlay.setBackgroundColor(0x80000000); // 半透明黑色
        mOverlay.setVisibility(View.GONE);
        mOverlay.setElevation(15);
        mOverlay.setOnClickListener(v -> hide()); // 点击遮罩关闭
        mParent.addView(mOverlay);

        // 加载对话框布局
        mDialogLayout = (ViewGroup) inflater.inflate(R.layout.dialog_side_edit, mParent, false);

        // 绑定UI元素
        mEtName = mDialogLayout.findViewById(R.id.et_name);
        mSeekbarX = mDialogLayout.findViewById(R.id.seekbar_x);
        mTvXValue = mDialogLayout.findViewById(R.id.tv_x_value);
        mSeekbarY = mDialogLayout.findViewById(R.id.seekbar_y);
        mTvYValue = mDialogLayout.findViewById(R.id.tv_y_value);
        mSeekbarWidth = mDialogLayout.findViewById(R.id.seekbar_width);
        mTvWidthValue = mDialogLayout.findViewById(R.id.tv_width_value);
        mSeekbarHeight = mDialogLayout.findViewById(R.id.seekbar_height);
        mTvHeightValue = mDialogLayout.findViewById(R.id.tv_height_value);
        mCheckboxKeepAspectRatio = mDialogLayout.findViewById(R.id.checkbox_keep_aspect_ratio);
        mSeekbarSize = mDialogLayout.findViewById(R.id.seekbar_size);
        mTvSizeValue = mDialogLayout.findViewById(R.id.tv_size_value);
        mSeekbarOpacity = mDialogLayout.findViewById(R.id.seekbar_opacity);
        mTvOpacityValue = mDialogLayout.findViewById(R.id.tv_opacity_value);
        mCheckboxVisible = mDialogLayout.findViewById(R.id.checkbox_visible);
        mBtnSelectKey = mDialogLayout.findViewById(R.id.btn_select_key);
        mTvKeymapLabel = mDialogLayout.findViewById(R.id.tv_keymap_label);

        // 新增UI元素
        mCheckboxToggle = mDialogLayout.findViewById(R.id.checkbox_toggle);
        mTvToggleModeLabel = mDialogLayout.findViewById(R.id.tv_toggle_mode_label);
        mBtnBgColor = mDialogLayout.findViewById(R.id.btn_bg_color);
        mBtnStrokeColor = mDialogLayout.findViewById(R.id.btn_stroke_color);
        mSeekbarStrokeWidth = mDialogLayout.findViewById(R.id.seekbar_stroke_width);
        mTvStrokeWidthValue = mDialogLayout.findViewById(R.id.tv_stroke_width_value);
        mSeekbarCornerRadius = mDialogLayout.findViewById(R.id.seekbar_corner_radius);
        mTvCornerRadiusValue = mDialogLayout.findViewById(R.id.tv_corner_radius_value);
        mBtnDuplicate = mDialogLayout.findViewById(R.id.btn_duplicate);
        mBtnDelete = mDialogLayout.findViewById(R.id.btn_delete);

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

    /**
     * 设置监听器 - 所有修改都调用统一的 applyRealtimeUpdate() 方法
     */
    private void setupListeners() {
        // 关闭按钮
        mDialogLayout.findViewById(R.id.btn_close_panel).setOnClickListener(v -> hide());

        // 隐藏应用按钮（所有更改都已实时生效）
        View btnApply = mDialogLayout.findViewById(R.id.btn_apply);
        if (btnApply != null) {
            btnApply.setVisibility(View.GONE);
        }

        // 按键选择按钮
        mBtnSelectKey.setOnClickListener(v -> showKeySelectDialog());

        // 名称输入框 - 实时更新
        mEtName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mCurrentData != null) {
                    mCurrentData.name = s.toString();
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 可见性复选框 - 实时更新
        mCheckboxVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mCurrentData != null) {
                mCurrentData.visible = isChecked;
                applyRealtimeUpdate(); // 统一更新
            }
        });

        // X坐标滑块 - 实时更新
        mSeekbarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvXValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    mCurrentData.x = mScreenWidth * progress / 100f;
                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Y坐标滑块 - 实时更新
        mSeekbarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvYValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    mCurrentData.y = mScreenHeight * progress / 100f;
                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 宽度滑块 - 实时更新
        mSeekbarWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvWidthValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    float width = mScreenWidth * progress / 100f;
                    mCurrentData.width = width;

                    // 保持宽高比时同步高度
                    if (mCheckboxKeepAspectRatio.isChecked()) {
                        mCurrentData.height = width;
                        // 同步更新高度滑块和尺寸滑块显示
                        if (!mIsUpdating) {
                            mIsUpdating = true;
                            int heightPercent = (int)(width / mScreenHeight * 100);
                            mSeekbarHeight.setProgress(heightPercent);
                            mTvHeightValue.setText(heightPercent + "%");
                            mSeekbarSize.setProgress((progress + heightPercent) / 2);
                            mTvSizeValue.setText(((progress + heightPercent) / 2) + "%");
                            mIsUpdating = false;
                        }
                    }

                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 高度滑块 - 实时更新
        mSeekbarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvHeightValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    float height = mScreenHeight * progress / 100f;
                    mCurrentData.height = height;

                    // 保持宽高比时同步宽度
                    if (mCheckboxKeepAspectRatio.isChecked()) {
                        mCurrentData.width = height;
                        // 同步更新宽度滑块和尺寸滑块显示
                        if (!mIsUpdating) {
                            mIsUpdating = true;
                            int widthPercent = (int)(height / mScreenWidth * 100);
                            mSeekbarWidth.setProgress(widthPercent);
                            mTvWidthValue.setText(widthPercent + "%");
                            mSeekbarSize.setProgress((widthPercent + progress) / 2);
                            mTvSizeValue.setText(((widthPercent + progress) / 2) + "%");
                            mIsUpdating = false;
                        }
                    }

                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 保持宽高比复选框
        mCheckboxKeepAspectRatio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 复选框状态改变时不需要额外操作，宽高滑块会根据状态自动处理
        });

        // 尺寸滑块 - 等比例调整宽高
        mSeekbarSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvSizeValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    // 计算新的尺寸（使用屏幕高度作为基准）
                    float size = mScreenHeight * progress / 100f;

                    // 同时更新宽度和高度
                    mCurrentData.width = size;
                    mCurrentData.height = size;

                    // 同步更新宽度和高度滑块显示
                    if (!mIsUpdating) {
                        mIsUpdating = true;
                        int widthPercent = (int)(size / mScreenWidth * 100);
                        int heightPercent = progress;
                        mSeekbarWidth.setProgress(widthPercent);
                        mTvWidthValue.setText(widthPercent + "%");
                        mSeekbarHeight.setProgress(heightPercent);
                        mTvHeightValue.setText(heightPercent + "%");
                        mIsUpdating = false;
                    }

                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 不透明度滑块 - 实时更新
        mSeekbarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvOpacityValue.setText(progress + "%");
                if (mCurrentData != null && fromUser) {
                    mCurrentData.opacity = progress / 100f;
                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 切换模式复选框 - 实时更新
        mCheckboxToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mCurrentData != null) {
                mCurrentData.isToggle = isChecked;
                applyRealtimeUpdate(); // 统一更新
            }
        });

        // 边框宽度滑块 - 实时更新
        mSeekbarStrokeWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvStrokeWidthValue.setText(String.valueOf(progress));
                if (mCurrentData != null && fromUser) {
                    mCurrentData.strokeWidth = progress;
                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 圆角半径滑块 - 实时更新
        mSeekbarCornerRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvCornerRadiusValue.setText(String.valueOf(progress));
                if (mCurrentData != null && fromUser) {
                    mCurrentData.cornerRadius = progress;
                    applyRealtimeUpdate(); // 统一更新
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 背景色按钮
        mBtnBgColor.setOnClickListener(v -> showColorPickerDialog(true));

        // 边框色按钮
        mBtnStrokeColor.setOnClickListener(v -> showColorPickerDialog(false));

        // 复制控件按钮
        mBtnDuplicate.setOnClickListener(v -> duplicateControl());

        // 删除控件按钮
        mBtnDelete.setOnClickListener(v -> deleteControl());
    }

    public boolean isDisplaying() {
        return mDisplaying;
    }

    /**
     * 显示按键选择对话框
     */
    private void showKeySelectDialog() {
        if (mCurrentData == null) return;

        Context context = mParent.getContext();

        // 创建MD3风格的键值选择对话框
        boolean isGamepadMode = (mCurrentData.buttonMode == ControlData.BUTTON_MODE_GAMEPAD);
        KeySelectorDialog dialog = new KeySelectorDialog(context, isGamepadMode);

        dialog.setOnKeySelectedListener((keycode, keyName) -> {
            // 更新按键码
            mCurrentData.keycode = keycode;

            // 更新按钮文本
            mBtnSelectKey.setText(keyName);

            // 统一更新
            applyRealtimeUpdate();
        });

        dialog.show();
    }

    /**
     * 更新颜色按钮显示
     */
    private void updateColorButtons() {
        if (mCurrentData == null) return;

        // 更新背景色按钮
        android.graphics.drawable.GradientDrawable bgDrawable = new android.graphics.drawable.GradientDrawable();
        bgDrawable.setColor(mCurrentData.bgColor);
        bgDrawable.setCornerRadius(dpToPx(8));
        bgDrawable.setStroke(dpToPx(2), 0xFF888888);
        mBtnBgColor.setBackground(bgDrawable);

        // 更新边框色按钮
        android.graphics.drawable.GradientDrawable strokeDrawable = new android.graphics.drawable.GradientDrawable();
        strokeDrawable.setColor(mCurrentData.strokeColor);
        strokeDrawable.setCornerRadius(dpToPx(8));
        strokeDrawable.setStroke(dpToPx(2), 0xFF888888);
        mBtnStrokeColor.setBackground(strokeDrawable);
    }

    /**
     * 显示颜色选择对话框
     */
    private void showColorPickerDialog(boolean isBackground) {
        if (mCurrentData == null) return;

        Context context = mParent.getContext();

        // 预设颜色
        final int[] presetColors = {
            0x80000000, // 半透明黑
            0xFFFF0000, // 红色
            0xFF00FF00, // 绿色
            0xFF0000FF, // 蓝色
            0xFFFFFF00, // 黄色
            0xFFFF00FF, // 洋红
            0xFF00FFFF, // 青色
            0xFFFFFFFF, // 白色
            0x80FFFFFF, // 半透明白
            0xFF888888, // 灰色
            0xFFFF8800, // 橙色
            0xFF8800FF  // 紫色
        };

        // 创建颜色网格
        android.widget.GridLayout gridLayout = new android.widget.GridLayout(context);
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        for (int color : presetColors) {
            android.widget.Button colorBtn = new android.widget.Button(context);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = dpToPx(60);
            params.height = dpToPx(60);
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            colorBtn.setLayoutParams(params);

            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dpToPx(8));
            drawable.setStroke(dpToPx(2), 0xFF888888);
            colorBtn.setBackground(drawable);

            colorBtn.setOnClickListener(v -> {
                if (isBackground) {
                    mCurrentData.bgColor = color;
                } else {
                    mCurrentData.strokeColor = color;
                }
                updateColorButtons();
                applyRealtimeUpdate(); // 统一更新
            });

            gridLayout.addView(colorBtn);
        }

        new android.app.AlertDialog.Builder(context)
            .setTitle(isBackground ? "选择背景色" : "选择边框色")
            .setView(gridLayout)
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 复制控件
     */
    private void duplicateControl() {
        if (mCurrentData == null) return;

        // 创建副本
        ControlData duplicate = new ControlData(mCurrentData);
        duplicate.x += 50; // 偏移位置避免重叠
        duplicate.y += 50;

        // 通知父布局添加新控件
        if (mOnControlDuplicatedListener != null) {
            mOnControlDuplicatedListener.onControlDuplicated(duplicate);
        }

        android.widget.Toast.makeText(mParent.getContext(), "已复制控件", android.widget.Toast.LENGTH_SHORT).show();
        hide();
    }

    /**
     * 删除控件
     */
    private void deleteControl() {
        if (mCurrentData == null) return;

        Context context = mParent.getContext();
        new android.app.AlertDialog.Builder(context)
            .setTitle("删除控件")
            .setMessage("确定要删除这个控件吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                // 通知父布局删除控件
                if (mOnControlDeletedListener != null) {
                    mOnControlDeletedListener.onControlDeleted(mCurrentData);
                }
                android.widget.Toast.makeText(context, "已删除控件", android.widget.Toast.LENGTH_SHORT).show();
                hide();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * mParent.getResources().getDisplayMetrics().density);
    }

    // 控件操作监听器
    private OnControlDuplicatedListener mOnControlDuplicatedListener;
    private OnControlDeletedListener mOnControlDeletedListener;

    public interface OnControlDuplicatedListener {
        void onControlDuplicated(ControlData newControl);
    }

    public interface OnControlDeletedListener {
        void onControlDeleted(ControlData control);
    }

    public void setOnControlDuplicatedListener(OnControlDuplicatedListener listener) {
        mOnControlDuplicatedListener = listener;
    }

    public void setOnControlDeletedListener(OnControlDeletedListener listener) {
        mOnControlDeletedListener = listener;
    }
}
