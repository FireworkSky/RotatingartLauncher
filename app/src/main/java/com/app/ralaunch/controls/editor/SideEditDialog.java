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
    private SeekBar mSeekbarX, mSeekbarY, mSeekbarSize, mSeekbarOpacity;
    private TextView mTvXValue, mTvYValue, mTvSizeValue, mTvOpacityValue;
    private CheckBox mCheckboxVisible;
    private Button mBtnSelectKey;
    private TextView mTvKeymapLabel;
    
    // 摇杆专用UI元素
    private TextView mTvJoystickModeLabel;
    private RadioGroup mRgJoystickMode;
    private RadioButton mRbKeyboardMode, mRbMouseMode, mRbSdlControllerMode;
    private TextView mTvJoystickKeysLabel;
    private Button mBtnEditJoystickKeys;
    private TextView mTvSdlStickLabel;
    private RadioGroup mRgSdlStick;
    private RadioButton mRbLeftStick, mRbRightStick;

    private ControlData mCurrentData;
    private ControlView mCurrentView;
    private int mScreenWidth, mScreenHeight;
    private OnApplyListener mOnApplyListener;
    
    public interface OnApplyListener {
        void onApply();
    }
    
    public SideEditDialog(Context context, ViewGroup parent, int screenWidth, int screenHeight) {
        mParent = parent;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
    }
    
    public void setOnApplyListener(OnApplyListener listener) {
        mOnApplyListener = listener;
    }
    
    /**
     * 显示编辑对话框
     */
    public void show(ControlData data) {
        if (mDialogLayout == null) {
            inflateLayout();
        }
        
        mCurrentData = data;
        
        // 找到对应的ControlView
        mCurrentView = null;
        for (int i = 0; i < mParent.getChildCount(); i++) {
            View child = mParent.getChildAt(i);
            if (child instanceof ControlView && ((ControlView) child).getData() == data) {
                mCurrentView = (ControlView) child;
                break;
            }
        }
        
        // 填充数据
        mEtName.setText(data.name);
        int xPercent = (int)(data.x / mScreenWidth * 100);
        int yPercent = (int)(data.y / mScreenHeight * 100);
        int sizePercent = (int)(data.width / mScreenHeight * 100);
        int opacityPercent = (int)(data.opacity * 100);
        
        mSeekbarX.setProgress(xPercent);
        mTvXValue.setText(xPercent + "%");
        mSeekbarY.setProgress(yPercent);
        mTvYValue.setText(yPercent + "%");
        mSeekbarSize.setProgress(sizePercent);
        mTvSizeValue.setText(sizePercent + "%");
        mSeekbarOpacity.setProgress(opacityPercent);
        mTvOpacityValue.setText(opacityPercent + "%");
        mCheckboxVisible.setChecked(data.visible);
        
        // 根据控件类型显示不同的编辑选项
        if (data.type == ControlData.TYPE_BUTTON) {
            // 按钮：显示按键映射
            mTvKeymapLabel.setVisibility(View.VISIBLE);
            mBtnSelectKey.setVisibility(View.VISIBLE);
            String keyName = KeyMapper.getKeyName(data.keycode);
            mBtnSelectKey.setText(keyName);
            
            // 隐藏摇杆相关选项
            mTvJoystickModeLabel.setVisibility(View.GONE);
            mRgJoystickMode.setVisibility(View.GONE);
            mTvJoystickKeysLabel.setVisibility(View.GONE);
            mBtnEditJoystickKeys.setVisibility(View.GONE);
            mTvSdlStickLabel.setVisibility(View.GONE);
            mRgSdlStick.setVisibility(View.GONE);

        } else if (data.type == ControlData.TYPE_JOYSTICK) {
            // 摇杆：显示模式选择和按键映射
            mTvJoystickModeLabel.setVisibility(View.VISIBLE);
            mRgJoystickMode.setVisibility(View.VISIBLE);
            
            // 根据模式设置RadioButton
            if (data.joystickMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
                mRbKeyboardMode.setChecked(true);
                // 键盘模式：显示方向键映射
                mTvJoystickKeysLabel.setVisibility(View.VISIBLE);
                mBtnEditJoystickKeys.setVisibility(View.VISIBLE);
                mTvSdlStickLabel.setVisibility(View.GONE);
                mRgSdlStick.setVisibility(View.GONE);
                updateJoystickKeysButtonText();
            } else if (data.joystickMode == ControlData.JOYSTICK_MODE_MOUSE) {
                mRbMouseMode.setChecked(true);
                // 鼠标模式：隐藏方向键映射
                mTvJoystickKeysLabel.setVisibility(View.GONE);
                mBtnEditJoystickKeys.setVisibility(View.GONE);
                mTvSdlStickLabel.setVisibility(View.GONE);
                mRgSdlStick.setVisibility(View.GONE);
            } else {
                mRbSdlControllerMode.setChecked(true);
                // SDL控制器模式：隐藏方向键映射，显示摇杆选择
                mTvJoystickKeysLabel.setVisibility(View.GONE);
                mBtnEditJoystickKeys.setVisibility(View.GONE);
                mTvSdlStickLabel.setVisibility(View.VISIBLE);
                mRgSdlStick.setVisibility(View.VISIBLE);

                // 设置左右摇杆选择
                if (data.xboxUseRightStick) {
                    mRbRightStick.setChecked(true);
                } else {
                    mRbLeftStick.setChecked(true);
                }
            }
            
            // 隐藏按钮相关选项
            mTvKeymapLabel.setVisibility(View.GONE);
            mBtnSelectKey.setVisibility(View.GONE);
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
        mSeekbarSize = mDialogLayout.findViewById(R.id.seekbar_size);
        mTvSizeValue = mDialogLayout.findViewById(R.id.tv_size_value);
        mSeekbarOpacity = mDialogLayout.findViewById(R.id.seekbar_opacity);
        mTvOpacityValue = mDialogLayout.findViewById(R.id.tv_opacity_value);
        mCheckboxVisible = mDialogLayout.findViewById(R.id.checkbox_visible);
        mBtnSelectKey = mDialogLayout.findViewById(R.id.btn_select_key);
        mTvKeymapLabel = mDialogLayout.findViewById(R.id.tv_keymap_label);
        
        // 摇杆专用UI元素
        mTvJoystickModeLabel = mDialogLayout.findViewById(R.id.tv_joystick_mode_label);
        mRgJoystickMode = mDialogLayout.findViewById(R.id.rg_joystick_mode);
        mRbKeyboardMode = mDialogLayout.findViewById(R.id.rb_keyboard_mode);
        mRbMouseMode = mDialogLayout.findViewById(R.id.rb_mouse_mode);
        mRbSdlControllerMode = mDialogLayout.findViewById(R.id.rb_sdl_controller_mode);
        mTvJoystickKeysLabel = mDialogLayout.findViewById(R.id.tv_joystick_keys_label);
        mBtnEditJoystickKeys = mDialogLayout.findViewById(R.id.btn_edit_joystick_keys);
        mTvSdlStickLabel = mDialogLayout.findViewById(R.id.tv_sdl_stick_label);
        mRgSdlStick = mDialogLayout.findViewById(R.id.rg_sdl_stick);
        mRbLeftStick = mDialogLayout.findViewById(R.id.rb_left_stick);
        mRbRightStick = mDialogLayout.findViewById(R.id.rb_right_stick);

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
     * 设置监听器
     */
    private void setupListeners() {
        // 关闭按钮
        mDialogLayout.findViewById(R.id.btn_close_panel).setOnClickListener(v -> hide());
        
        // 应用按钮
        mDialogLayout.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            if (mCurrentData != null) {
                mCurrentData.name = mEtName.getText().toString();
                mCurrentData.visible = mCheckboxVisible.isChecked();
                
                // 通知应用更改
                if (mOnApplyListener != null) {
                    mOnApplyListener.onApply();
                }
                
                // 关闭对话框
                hide();
            }
        });
        
        // 按键选择按钮
        mBtnSelectKey.setOnClickListener(v -> showKeySelectDialog());
        
        // 摇杆模式切换
        mRgJoystickMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (mCurrentData == null) return;
            
            if (checkedId == R.id.rb_keyboard_mode) {
                mCurrentData.joystickMode = ControlData.JOYSTICK_MODE_KEYBOARD;
                // 显示方向键映射
                mTvJoystickKeysLabel.setVisibility(View.VISIBLE);
                mBtnEditJoystickKeys.setVisibility(View.VISIBLE);
                mTvSdlStickLabel.setVisibility(View.GONE);
                mRgSdlStick.setVisibility(View.GONE);
                updateJoystickKeysButtonText();

                // 初始化默认按键映射（如果为空）
                if (mCurrentData.joystickKeys == null) {
                    mCurrentData.joystickKeys = new int[]{
                        ControlData.SDL_SCANCODE_W,  // up
                        ControlData.SDL_SCANCODE_D,  // right
                        ControlData.SDL_SCANCODE_S,  // down
                        ControlData.SDL_SCANCODE_A   // left
                    };
                }
            } else if (checkedId == R.id.rb_mouse_mode) {
                mCurrentData.joystickMode = ControlData.JOYSTICK_MODE_MOUSE;
                // 隐藏方向键映射
                mTvJoystickKeysLabel.setVisibility(View.GONE);
                mBtnEditJoystickKeys.setVisibility(View.GONE);
                mTvSdlStickLabel.setVisibility(View.GONE);
                mRgSdlStick.setVisibility(View.GONE);
            } else {
                mCurrentData.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                // 隐藏方向键映射，显示摇杆选择
                mTvJoystickKeysLabel.setVisibility(View.GONE);
                mBtnEditJoystickKeys.setVisibility(View.GONE);
                mTvSdlStickLabel.setVisibility(View.VISIBLE);
                mRgSdlStick.setVisibility(View.VISIBLE);
            }

            // 更新视图
            if (mCurrentView != null) {
                mCurrentView.updateData(mCurrentData);
            }
        });

        // 编辑摇杆按键映射
        mBtnEditJoystickKeys.setOnClickListener(v -> showJoystickKeyEditDialog());

        // X坐标滑块
        mSeekbarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvXValue.setText(progress + "%");
                if (mCurrentData != null) {
                    mCurrentData.x = mScreenWidth * progress / 100f;
                    if (mCurrentView != null) {
                        ((View)mCurrentView).setX(mCurrentData.x);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Y坐标滑块
        mSeekbarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvYValue.setText(progress + "%");
                if (mCurrentData != null) {
                    mCurrentData.y = mScreenHeight * progress / 100f;
                    if (mCurrentView != null) {
                        ((View)mCurrentView).setY(mCurrentData.y);
                    }
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
                if (mCurrentData != null) {
                    float size = mScreenHeight * progress / 100f;
                    mCurrentData.width = size;
                    mCurrentData.height = size;
                    if (mCurrentView != null) {
                        View v = (View)mCurrentView;
                        ViewGroup.LayoutParams params = v.getLayoutParams();
                        params.width = (int)size;
                        params.height = (int)size;
                        v.setLayoutParams(params);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 不透明度滑块
        mSeekbarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTvOpacityValue.setText(progress + "%");
                if (mCurrentData != null) {
                    mCurrentData.opacity = progress / 100f;
                    if (mCurrentView != null) {
                        ((View)mCurrentView).setAlpha(mCurrentData.opacity);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Xbox 摇杆选择切换
        mRgSdlStick.setOnCheckedChangeListener((group, checkedId) -> {
            if (mCurrentData == null) return;

            if (checkedId == R.id.rb_left_stick) {
                mCurrentData.xboxUseRightStick = false;
            } else if (checkedId == R.id.rb_right_stick) {
                mCurrentData.xboxUseRightStick = true;
            }

            // 更新视图
            if (mCurrentView != null) {
                mCurrentView.updateData(mCurrentData);
            }
        });
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
        
        // 获取所有可用按键
        Map<String, Integer> allKeys = KeyMapper.getAllKeys();
        List<String> keyNames = new ArrayList<>(allKeys.keySet());
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择按键");
        
        // 设置按键列表
        String[] keyArray = keyNames.toArray(new String[0]);
        builder.setItems(keyArray, (dialog, which) -> {
            String selectedKeyName = keyArray[which];
            Integer selectedKeycode = allKeys.get(selectedKeyName);
            
            if (selectedKeycode != null) {
                // 更新按键码
                mCurrentData.keycode = selectedKeycode;
                
                // 更新按钮文本
                mBtnSelectKey.setText(selectedKeyName);
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 更新摇杆按键映射按钮的文本
     */
    private void updateJoystickKeysButtonText() {
        if (mCurrentData == null || mCurrentData.joystickKeys == null) {
            mBtnEditJoystickKeys.setText("未映射");
            return;
        }
        
        // 显示4个方向的按键名称
        String upKey = KeyMapper.getKeyName(mCurrentData.joystickKeys[0]);
        String rightKey = KeyMapper.getKeyName(mCurrentData.joystickKeys[1]);
        String downKey = KeyMapper.getKeyName(mCurrentData.joystickKeys[2]);
        String leftKey = KeyMapper.getKeyName(mCurrentData.joystickKeys[3]);
        
        mBtnEditJoystickKeys.setText(String.format("↑%s ↓%s ←%s →%s", upKey, downKey, leftKey, rightKey));
    }
    
    /**
     * 显示摇杆按键映射编辑对话框
     */
    private void showJoystickKeyEditDialog() {
        if (mCurrentData == null || mCurrentData.joystickKeys == null) return;
        
        Context context = mParent.getContext();
        
        // 创建自定义对话框布局
        View dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        // 创建4个方向的按键选择按钮
        String[] directions = {"上 ↑", "右 →", "下 ↓", "左 ←"};
        Button[] buttons = new Button[4];
        
        for (int i = 0; i < 4; i++) {
            TextView label = new TextView(context);
            label.setText(directions[i]);
            label.setTextSize(14);
            label.setTextColor(0xFF888888);
            label.setPadding(0, 20, 0, 8);
            layout.addView(label);
            
            buttons[i] = new Button(context);
            buttons[i].setText(KeyMapper.getKeyName(mCurrentData.joystickKeys[i]));
            buttons[i].setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            buttons[i].setPadding(40, 20, 40, 20);
            
            int finalI = i;
            buttons[i].setOnClickListener(v -> {
                // 显示按键选择对话框
                showJoystickDirectionKeySelectDialog(finalI, (keycode, keyName) -> {
                    mCurrentData.joystickKeys[finalI] = keycode;
                    buttons[finalI].setText(keyName);
                    updateJoystickKeysButtonText();
                });
            });
            
            layout.addView(buttons[i]);
        }
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("编辑摇杆方向键映射");
        builder.setView(layout);
        builder.setPositiveButton("确定", (dialog, which) -> {
            // 更新视图
            if (mCurrentView != null) {
                mCurrentView.updateData(mCurrentData);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 显示单个方向的按键选择对话框
     */
    private void showJoystickDirectionKeySelectDialog(int direction, OnKeySelectedListener listener) {
        Context context = mParent.getContext();
        
        // 获取所有可用按键
        Map<String, Integer> allKeys = KeyMapper.getAllKeys();
        List<String> keyNames = new ArrayList<>(allKeys.keySet());
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        String[] directionNames = {"上↑", "右→", "下↓", "左←"};
        builder.setTitle("选择 " + directionNames[direction] + " 方向按键");
        
        // 设置按键列表
        String[] keyArray = keyNames.toArray(new String[0]);
        builder.setItems(keyArray, (dialog, which) -> {
            String selectedKeyName = keyArray[which];
            Integer selectedKeycode = allKeys.get(selectedKeyName);
            
            if (selectedKeycode != null && listener != null) {
                listener.onKeySelected(selectedKeycode, selectedKeyName);
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 按键选择监听器
     */
    private interface OnKeySelectedListener {
        void onKeySelected(int keycode, String keyName);
    }
}
