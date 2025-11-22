package com.app.ralaunch.controls.editor;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.google.android.material.button.MaterialButton;

/**
 * MD3风格的控件编辑对话框
 * 左侧分类导航，右侧内容区域
 */
public class ControlEditDialogMD extends Dialog {

    private ControlData mCurrentData;
    private int mScreenWidth, mScreenHeight;
    private boolean mIsUpdating = false;

    // 分类导航UI元素
    private TextView mCategoryBasic, mCategoryPosition, mCategoryAppearance, mCategoryKeymap;
    private ViewGroup mContentContainer;
    private View mCurrentContentView;
    private int mCurrentCategory = 0; // 0=基本信息, 1=位置大小, 2=外观样式, 3=键值设置

    // 基本信息区UI元素
    private LinearLayout mItemControlType;
    private TextView mTvControlType;
    private EditText mEtName;
    private SeekBar mSeekbarPosX, mSeekbarPosY, mSeekbarSize, mSeekbarOpacity;
    private TextView mTvPosXValue, mTvPosYValue, mTvSizeValue, mTvOpacityValue;
    private SwitchCompat mSwitchVisible;
    private View mViewBgColor, mViewStrokeColor;

    // 键值设置区UI元素
    private LinearLayout mItemKeyMapping, mItemToggleMode, mKeymapCard;
    private TextView mTvKeyName, mTvKeymapSection;
    private View mDividerToggle;
    private SwitchCompat mSwitchToggleMode;

    // 回调接口
    private OnControlUpdatedListener mUpdateListener;
    private OnControlDeletedListener mDeleteListener;

    public interface OnControlUpdatedListener {
        void onControlUpdated(ControlData data);
    }

    public interface OnControlDeletedListener {
        void onControlDeleted(ControlData data);
    }

    public ControlEditDialogMD(@NonNull Context context, int screenWidth, int screenHeight) {
        super(context);
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 加载布局
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_control_edit_md, null);
        setContentView(view);

        // 设置对话框样式
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.90),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // 绑定UI元素
        initViews(view);

        // 设置监听器
        setupListeners();
    }

    /**
     * 初始化UI元素
     */
    private void initViews(View view) {
        // 分类导航元素
        mCategoryBasic = view.findViewById(R.id.category_basic);
        mCategoryPosition = view.findViewById(R.id.category_position);
        mCategoryAppearance = view.findViewById(R.id.category_appearance);
        mCategoryKeymap = view.findViewById(R.id.category_keymap);
        mContentContainer = view.findViewById(R.id.content_container);

        // 默认加载第一个分类（基本信息）
        switchCategory(0);
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 关闭按钮
        findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

        // 删除按钮
        findViewById(R.id.btn_delete).setOnClickListener(v -> deleteControl());

        // 保存按钮
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (mUpdateListener != null && mCurrentData != null) {
                mUpdateListener.onControlUpdated(mCurrentData);
            }
            dismiss();
        });

        // 分类导航按钮
        mCategoryBasic.setOnClickListener(v -> switchCategory(0));
        mCategoryPosition.setOnClickListener(v -> switchCategory(1));
        mCategoryAppearance.setOnClickListener(v -> switchCategory(2));
        mCategoryKeymap.setOnClickListener(v -> switchCategory(3));
    }

    /**
     * 切换分类
     */
    private void switchCategory(int category) {
        mCurrentCategory = category;
        updateCategorySelection();
        loadCategoryContent();
    }

    /**
     * 更新分类选中状态
     */
    private void updateCategorySelection() {
        // 重置所有分类按钮的状态
        mCategoryBasic.setBackgroundResource(android.R.drawable.list_selector_background);
        mCategoryPosition.setBackgroundResource(android.R.drawable.list_selector_background);
        mCategoryAppearance.setBackgroundResource(android.R.drawable.list_selector_background);
        mCategoryKeymap.setBackgroundResource(android.R.drawable.list_selector_background);

        mCategoryBasic.setTextColor(0xFF49454F);
        mCategoryPosition.setTextColor(0xFF49454F);
        mCategoryAppearance.setTextColor(0xFF49454F);
        mCategoryKeymap.setTextColor(0xFF49454F);

        mCategoryBasic.setTypeface(null, android.graphics.Typeface.NORMAL);
        mCategoryPosition.setTypeface(null, android.graphics.Typeface.NORMAL);
        mCategoryAppearance.setTypeface(null, android.graphics.Typeface.NORMAL);
        mCategoryKeymap.setTypeface(null, android.graphics.Typeface.NORMAL);

        // 设置选中分类的状态
        TextView selectedCategory = null;
        switch (mCurrentCategory) {
            case 0: selectedCategory = mCategoryBasic; break;
            case 1: selectedCategory = mCategoryPosition; break;
            case 2: selectedCategory = mCategoryAppearance; break;
            case 3: selectedCategory = mCategoryKeymap; break;
        }

        if (selectedCategory != null) {
            selectedCategory.setBackgroundResource(R.drawable.bg_category_selected);
            selectedCategory.setTextColor(0xFF6750A4);
            selectedCategory.setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

    /**
     * 加载分类内容
     */
    private void loadCategoryContent() {
        // 移除旧的内容视图
        if (mCurrentContentView != null) {
            mContentContainer.removeView(mCurrentContentView);
        }

        // 加载新的内容视图
        int layoutId;
        switch (mCurrentCategory) {
            case 0: layoutId = R.layout.content_basic_info; break;
            case 1: layoutId = R.layout.content_position_size; break;
            case 2: layoutId = R.layout.content_appearance; break;
            case 3: layoutId = R.layout.content_keymap; break;
            default: return;
        }

        mCurrentContentView = LayoutInflater.from(getContext()).inflate(layoutId, mContentContainer, false);
        mContentContainer.addView(mCurrentContentView);

        // 绑定当前分类的UI元素并设置监听器
        bindCategoryViews();

        // 填充数据
        fillCategoryData();
    }

    /**
     * 绑定当前分类的UI元素
     */
    private void bindCategoryViews() {
        if (mCurrentContentView == null) return;

        switch (mCurrentCategory) {
            case 0: // 基本信息
                mItemControlType = mCurrentContentView.findViewById(R.id.item_control_type);
                mTvControlType = mCurrentContentView.findViewById(R.id.tv_control_type);
                mEtName = mCurrentContentView.findViewById(R.id.et_control_name);

                // 设置监听器
                mItemControlType.setOnClickListener(v -> showTypeSelectDialog());
                mEtName.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (mCurrentData != null) {
                            mCurrentData.name = s.toString();
                            notifyUpdate();
                        }
                    }
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
                break;

            case 1: // 位置大小
                mSeekbarPosX = mCurrentContentView.findViewById(R.id.seekbar_pos_x);
                mTvPosXValue = mCurrentContentView.findViewById(R.id.tv_pos_x_value);
                mSeekbarPosY = mCurrentContentView.findViewById(R.id.seekbar_pos_y);
                mTvPosYValue = mCurrentContentView.findViewById(R.id.tv_pos_y_value);
                mSeekbarSize = mCurrentContentView.findViewById(R.id.seekbar_size);
                mTvSizeValue = mCurrentContentView.findViewById(R.id.tv_size_value);

                // X坐标滑块
                mSeekbarPosX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        mTvPosXValue.setText(progress + "%");
                        if (mCurrentData != null && fromUser) {
                            mCurrentData.x = mScreenWidth * progress / 100f;
                            notifyUpdate();
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

                // Y坐标滑块
                mSeekbarPosY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        mTvPosYValue.setText(progress + "%");
                        if (mCurrentData != null && fromUser) {
                            mCurrentData.y = mScreenHeight * progress / 100f;
                            notifyUpdate();
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

                // 尺寸滑块
                mSeekbarSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        mTvSizeValue.setText(progress + "%");
                        if (mCurrentData != null && fromUser) {
                            float size = mScreenHeight * progress / 100f;
                            mCurrentData.width = size;
                            mCurrentData.height = size;
                            notifyUpdate();
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
                break;

            case 2: // 外观样式
                mSeekbarOpacity = mCurrentContentView.findViewById(R.id.seekbar_opacity);
                mTvOpacityValue = mCurrentContentView.findViewById(R.id.tv_opacity_value);
                mSwitchVisible = mCurrentContentView.findViewById(R.id.switch_visible);
                mViewBgColor = mCurrentContentView.findViewById(R.id.view_bg_color);
                mViewStrokeColor = mCurrentContentView.findViewById(R.id.view_stroke_color);

                // 透明度滑块
                mSeekbarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        mTvOpacityValue.setText(progress + "%");
                        if (mCurrentData != null && fromUser) {
                            mCurrentData.opacity = progress / 100f;
                            notifyUpdate();
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

                // 可见性开关
                mSwitchVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (mCurrentData != null) {
                        mCurrentData.visible = isChecked;
                        notifyUpdate();
                    }
                });

                // 背景颜色选择
                mCurrentContentView.findViewById(R.id.item_bg_color).setOnClickListener(v -> showColorPickerDialog(true));

                // 边框颜色选择
                mCurrentContentView.findViewById(R.id.item_stroke_color).setOnClickListener(v -> showColorPickerDialog(false));
                break;

            case 3: // 键值设置
                mItemKeyMapping = mCurrentContentView.findViewById(R.id.item_key_mapping);
                mTvKeyName = mCurrentContentView.findViewById(R.id.tv_key_name);
                mSwitchToggleMode = mCurrentContentView.findViewById(R.id.switch_toggle_mode);

                // 按键映射选择
                mItemKeyMapping.setOnClickListener(v -> showKeySelectDialog());

                // 切换模式开关
                mSwitchToggleMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (mCurrentData != null) {
                        mCurrentData.isToggle = isChecked;
                        notifyUpdate();
                    }
                });
                break;
        }
    }

    /**
     * 填充当前分类的数据
     */
    private void fillCategoryData() {
        if (mCurrentData == null) return;

        switch (mCurrentCategory) {
            case 0: // 基本信息
                updateTypeDisplay();
                if (mEtName != null) {
                    mEtName.setText(mCurrentData.name);
                }
                break;

            case 1: // 位置大小
                int xPercent = (int)(mCurrentData.x / mScreenWidth * 100);
                int yPercent = (int)(mCurrentData.y / mScreenHeight * 100);
                int sizePercent = (int)((mCurrentData.width + mCurrentData.height) / 2 / mScreenHeight * 100);

                if (mSeekbarPosX != null) {
                    mSeekbarPosX.setProgress(xPercent);
                    mTvPosXValue.setText(xPercent + "%");
                }
                if (mSeekbarPosY != null) {
                    mSeekbarPosY.setProgress(yPercent);
                    mTvPosYValue.setText(yPercent + "%");
                }
                if (mSeekbarSize != null) {
                    mSeekbarSize.setProgress(sizePercent);
                    mTvSizeValue.setText(sizePercent + "%");
                }
                break;

            case 2: // 外观样式
                int opacityPercent = (int)(mCurrentData.opacity * 100);
                if (mSeekbarOpacity != null) {
                    mSeekbarOpacity.setProgress(opacityPercent);
                    mTvOpacityValue.setText(opacityPercent + "%");
                }
                if (mSwitchVisible != null) {
                    mSwitchVisible.setChecked(mCurrentData.visible);
                }
                updateColorViews();
                break;

            case 3: // 键值设置
                if (mTvKeyName != null) {
                    String keyName = KeyMapper.getKeyName(mCurrentData.keycode);
                    mTvKeyName.setText(keyName);
                }
                if (mSwitchToggleMode != null) {
                    mSwitchToggleMode.setChecked(mCurrentData.isToggle);
                }
                break;
        }
    }

    /**
     * 显示控件数据
     */
    public void show(ControlData data) {
        mCurrentData = data;

        if (data == null) return;

        // 先显示对话框（触发onCreate初始化视图）
        super.show();

        // 根据控件类型决定是否显示键值设置分类
        updateKeymapCategoryVisibility();

        // 重新加载当前分类的内容（填充数据）
        fillCategoryData();
    }

    /**
     * 更新键值设置分类的可见性
     */
    private void updateKeymapCategoryVisibility() {
        if (mCurrentData == null || mCategoryKeymap == null) return;

        if (mCurrentData.type == ControlData.TYPE_BUTTON) {
            // 按钮类型：显示键值设置分类
            mCategoryKeymap.setVisibility(View.VISIBLE);
        } else {
            // 摇杆类型：隐藏键值设置分类
            mCategoryKeymap.setVisibility(View.GONE);
            // 如果当前正在查看键值设置，切换到基本信息
            if (mCurrentCategory == 3) {
                switchCategory(0);
            }
        }
    }

    /**
     * 更新颜色视图显示
     */
    private void updateColorViews() {
        if (mCurrentData == null) return;

        // 背景颜色
        if (mViewBgColor != null) {
            GradientDrawable bgDrawable = new GradientDrawable();
            bgDrawable.setColor(mCurrentData.bgColor);
            bgDrawable.setCornerRadius(dpToPx(8));
            bgDrawable.setStroke(dpToPx(2), 0xFFD0D0D0);
            mViewBgColor.setBackground(bgDrawable);
        }

        // 边框颜色
        if (mViewStrokeColor != null) {
            GradientDrawable strokeDrawable = new GradientDrawable();
            strokeDrawable.setColor(mCurrentData.strokeColor);
            strokeDrawable.setCornerRadius(dpToPx(8));
            strokeDrawable.setStroke(dpToPx(2), 0xFFD0D0D0);
            mViewStrokeColor.setBackground(strokeDrawable);
        }
    }

    /**
     * 更新类型显示
     */
    private void updateTypeDisplay() {
        if (mCurrentData == null || mTvControlType == null) return;
        String typeName = mCurrentData.type == ControlData.TYPE_BUTTON ? "按钮" : "摇杆";
        mTvControlType.setText(typeName);
    }

    /**
     * 显示类型选择对话框
     */
    private void showTypeSelectDialog() {
        if (mCurrentData == null) return;

        String[] types = {"按钮", "摇杆"};
        int currentType = mCurrentData.type;

        new android.app.AlertDialog.Builder(getContext())
            .setTitle("选择控件类型")
            .setSingleChoiceItems(types, currentType, (dialog, which) -> {
                mCurrentData.type = which;
                updateTypeDisplay();

                // 类型改变时，更新键值设置分类的可见性
                updateKeymapCategoryVisibility();

                notifyUpdate();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 显示按键选择对话框
     */
    private void showKeySelectDialog() {
        if (mCurrentData == null) return;

        boolean isGamepadMode = (mCurrentData.buttonMode == ControlData.BUTTON_MODE_GAMEPAD);
        KeySelectorDialog dialog = new KeySelectorDialog(getContext(), isGamepadMode);

        dialog.setOnKeySelectedListener((keycode, keyName) -> {
            mCurrentData.keycode = keycode;
            mTvKeyName.setText(keyName);
            notifyUpdate();
        });

        dialog.show();
    }

    /**
     * 显示颜色选择对话框
     */
    private void showColorPickerDialog(boolean isBackground) {
        if (mCurrentData == null) return;

        // 预设颜色
        final int[] presetColors = {
            0xFFFFFFFF, // 白色（默认）
            0xFFEEEEEE, // 浅灰
            0xFFCCCCCC, // 灰色
            0xFF888888, // 深灰
            0xFF000000, // 黑色
            0x80000000, // 半透明黑
            0xFFFF0000, // 红色
            0xFF00FF00, // 绿色
            0xFF0000FF, // 蓝色
            0xFFFFFF00, // 黄色
            0xFFFF8800, // 橙色
            0xFF8800FF  // 紫色
        };

        // 创建颜色网格
        android.widget.GridLayout gridLayout = new android.widget.GridLayout(getContext());
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        for (int color : presetColors) {
            android.widget.Button colorBtn = new android.widget.Button(getContext());
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = dpToPx(60);
            params.height = dpToPx(60);
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            colorBtn.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
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
                updateColorViews();
                notifyUpdate();
            });

            gridLayout.addView(colorBtn);
        }

        new android.app.AlertDialog.Builder(getContext())
            .setTitle(isBackground ? "选择背景色" : "选择边框色")
            .setView(gridLayout)
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 删除控件
     */
    private void deleteControl() {
        if (mCurrentData == null) return;

        new android.app.AlertDialog.Builder(getContext())
            .setTitle("删除控件")
            .setMessage("确定要删除这个控件吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                if (mDeleteListener != null) {
                    mDeleteListener.onControlDeleted(mCurrentData);
                }
                dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 通知数据更新
     */
    private void notifyUpdate() {
        if (mUpdateListener != null && mCurrentData != null && !mIsUpdating) {
            mUpdateListener.onControlUpdated(mCurrentData);
        }
    }

    public void setOnControlUpdatedListener(OnControlUpdatedListener listener) {
        mUpdateListener = listener;
    }

    public void setOnControlDeletedListener(OnControlDeletedListener listener) {
        mDeleteListener = listener;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}
