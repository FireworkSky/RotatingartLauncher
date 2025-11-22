package com.app.ralaunch.controls.editor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.*;

import java.io.File;
import java.io.InputStream;

public class ControlEditorActivity extends AppCompatActivity {
    private static final String TAG = "ControlEditorActivity";

    private FrameLayout mEditorContainer;
    private ControlLayout mPreviewLayout;
    private GridOverlayView mGridOverlay;
    private SideEditDialog mSideDialog;
    private EditorSettingsDialog mSettingsDialog;
    private com.google.android.material.button.MaterialButton mModeToggleButton;

    private ControlConfig mCurrentConfig;
    private SDLInputBridge mDummyBridge;
    private DisplayMetrics mMetrics;
    private int mScreenWidth;
    private int mScreenHeight;

    // 当前控制模式：false=键盘/鼠标, true=手柄
    private boolean mIsGamepadMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置全屏沉浸模式并隐藏刘海屏
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Android P+ 隐藏刘海屏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        setContentView(R.layout.activity_control_editor);
        
        // 获取屏幕尺寸
        mMetrics = getResources().getDisplayMetrics();
        mScreenWidth = mMetrics.widthPixels;
        mScreenHeight = mMetrics.heightPixels;
        
        initUI();
        
        // 延迟加载布局
        mEditorContainer.post(() -> loadOrCreateLayout());
    }
    
    private void initUI() {
        mEditorContainer = findViewById(R.id.editor_container);

        // 模式切换按钮
        mModeToggleButton = findViewById(R.id.mode_toggle_button);
        mModeToggleButton.setOnClickListener(v -> toggleControlMode());
        updateModeToggleButton();

        // 设置按钮点击显示 MD3 设置弹窗
        findViewById(R.id.drawer_button).setOnClickListener(v -> {
            if (mSettingsDialog != null) {
                mSettingsDialog.show();
            }
        });

        // 创建侧边编辑对话框
        mSideDialog = new SideEditDialog(this, mEditorContainer, mScreenWidth, mScreenHeight);

        // 设置删除和复制监听器
        mSideDialog.setOnControlDeletedListener(control -> {
            if (mCurrentConfig != null && mCurrentConfig.controls != null) {
                mCurrentConfig.controls.remove(control);
                displayLayout();
            }
        });

        mSideDialog.setOnControlDuplicatedListener(newControl -> {
            if (mCurrentConfig != null && mCurrentConfig.controls != null) {
                mCurrentConfig.controls.add(newControl);
                displayLayout();
            }
        });

        // 创建 MD3 设置弹窗
        mSettingsDialog = new EditorSettingsDialog(this, mEditorContainer, mScreenWidth);
        mSettingsDialog.setOnMenuItemClickListener(new EditorSettingsDialog.OnMenuItemClickListener() {
            @Override
            public void onAddButton() {
                addButton();
            }

            @Override
            public void onAddJoystick() {
                addJoystick();
            }

            @Override
            public void onJoystickModeSettings() {
                showJoystickModeDialog();
            }

            @Override
            public void onSaveLayout() {
                saveLayout();
            }

            @Override
            public void onLoadLayout() {
                loadLayout();
            }

            @Override
            public void onResetDefault() {
                resetToDefault();
            }

            @Override
            public void onSaveAndExit() {
                saveLayout();
                finish();
            }
        });
    }
    
    private void loadOrCreateLayout() {
        File customFile = new File(getFilesDir(), "custom_layout.json");
        
        // 优先加载用户自定义布局
        if (customFile.exists()) {
            try {
                String json = new String(java.nio.file.Files.readAllBytes(customFile.toPath()));
                mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load custom layout", e);
                loadDefaultLayout();
            }
        } else {
            // 加载assets中的默认布局
            try {
                InputStream is = getAssets().open("controls/default_layout.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String json = new String(buffer, "UTF-8");
                mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load default layout from assets", e);
                loadDefaultLayout();
            }
        }
        
        displayLayout();
    }
    
    private void loadDefaultLayout() {
        mCurrentConfig = new ControlConfig();
        mCurrentConfig.name = "默认布局";
        mCurrentConfig.controls = new java.util.ArrayList<>();
        
        // 添加一个默认摇杆
        ControlData joystick = ControlData.createDefaultJoystick();
        // 调整位置为左下角
        joystick.y = mScreenHeight - joystick.height - 50;
        mCurrentConfig.controls.add(joystick);
        
    }
    
    private void displayLayout() {
        // 清除现有视图
        mEditorContainer.removeAllViews();

        // 添加网格覆盖层
        mGridOverlay = new GridOverlayView(this);
        mEditorContainer.addView(mGridOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 创建预览布局
        mDummyBridge = new SDLInputBridge();
        mPreviewLayout = new ControlLayout(this);
        mPreviewLayout.setInputBridge(mDummyBridge);
        mPreviewLayout.loadLayout(mCurrentConfig);
        mPreviewLayout.setControlsVisible(true);

        // 统一禁用裁剪
        disableClippingRecursive(mPreviewLayout);

        mEditorContainer.addView(mPreviewLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 为每个控件设置交互
        setupControlInteractions();

        // 重新创建侧边对话框（因为父布局已更改）
        mSideDialog = new SideEditDialog(this, mEditorContainer, mScreenWidth, mScreenHeight);

    }

    /**
     * 递归禁用所有子视图的裁剪
     * 确保控件边框等绘制内容不会被父容器裁剪
     */
    private void disableClippingRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);

            // 递归处理所有子视图
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableClippingRecursive(viewGroup.getChildAt(i));
            }
        }

        // 对所有视图禁用裁剪边界和轮廓裁剪
        view.setClipToOutline(false);
        view.setClipBounds(null);
    }
    
    private void setupControlInteractions() {
        for (int i = 0; i < mPreviewLayout.getChildCount(); i++) {
            View child = mPreviewLayout.getChildAt(i);
            if (child instanceof ControlView) {
                ControlView controlView = (ControlView) child;
                setupControlViewInteraction(controlView);
            }
        }
    }
    
    private void setupControlViewInteraction(ControlView controlView) {
        if (!(controlView instanceof View)) return;

        View view = (View) controlView;
        final ControlData data = controlView.getData();
        final float[] lastPos = new float[2];
        final boolean[] isDragging = new boolean[1];

        view.setOnTouchListener((v, event) -> {
            float touchX = event.getX();
            float touchY = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 对于摇杆，检查触摸点是否在圆形区域内
                    if (data.type == ControlData.TYPE_JOYSTICK) {
                        float centerX = v.getWidth() / 2f;
                        float centerY = v.getHeight() / 2f;
                        float radius = Math.min(v.getWidth(), v.getHeight()) / 2f;
                        float dx = touchX - centerX;
                        float dy = touchY - centerY;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);

                        // 如果触摸点在圆形外部，不响应
                        if (distance > radius) {
                            return false;
                        }
                    }

                    lastPos[0] = event.getRawX();
                    lastPos[1] = event.getRawY();
                    isDragging[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - lastPos[0];
                    float dy = event.getRawY() - lastPos[1];

                    if (!isDragging[0] && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging[0] = true;
                    }

                    if (isDragging[0]) {
                        // 更新数据
                        data.x += dx;
                        data.y += dy;

                        // 使用 LayoutParams 更新位置（与 ControlLayout 一致）
                        ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
                        if (layoutParams instanceof FrameLayout.LayoutParams) {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
                            params.leftMargin = (int) data.x;
                            params.topMargin = (int) data.y;
                            v.setLayoutParams(params);
                        }

                        lastPos[0] = event.getRawX();
                        lastPos[1] = event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging[0]) {
                        // 点击事件 - 显示侧边对话框
                        mSideDialog.show(data);
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void addButton() {
        ControlData button = new ControlData();
        button.name = "新按键";
        button.type = ControlData.TYPE_BUTTON;
        button.x = mScreenWidth / 2f;
        button.y = mScreenHeight / 2f;
        button.width = 100;
        button.height = 100;
        button.opacity = 0.7f;
        button.visible = true;
        button.keycode = 62; // Space
        
        mCurrentConfig.controls.add(button);
        displayLayout();
        
        Toast.makeText(this, "已添加按键", Toast.LENGTH_SHORT).show();
    }
    
    private void addJoystick() {
        ControlData joystick = ControlData.createDefaultJoystick();
        joystick.x = mScreenWidth / 2f;
        joystick.y = mScreenHeight / 2f;

        mCurrentConfig.controls.add(joystick);
        displayLayout();

        Toast.makeText(this, "已添加摇杆", Toast.LENGTH_SHORT).show();
    }

    private void saveLayout() {
        try {
            File file = new File(getFilesDir(), "custom_layout.json");
            String json = new com.google.gson.Gson().toJson(mCurrentConfig);
            java.nio.file.Files.write(file.toPath(), json.getBytes());
            Toast.makeText(this, "布局已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save layout", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadLayout() {

        Toast.makeText(this, "加载功能待实现", Toast.LENGTH_SHORT).show();
    }
    
    private void resetToDefault() {
        new AlertDialog.Builder(this)
            .setTitle("重置布局")
            .setMessage("确定要重置为默认布局吗？当前布局将丢失。")
            .setPositiveButton("确定", (dialog, which) -> {
                loadDefaultLayout();
                displayLayout();
                Toast.makeText(this, "已重置为默认布局", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 更新模式切换按钮显示
     */
    private void updateModeToggleButton() {
        if (mModeToggleButton == null) return;

        if (mIsGamepadMode) {
            mModeToggleButton.setText("手柄模式");
            mModeToggleButton.setIconResource(R.drawable.ic_gamepad);
        } else {
            mModeToggleButton.setText("键盘模式");
            mModeToggleButton.setIconResource(R.drawable.ic_keyboard);
        }
    }

    /**
     * 切换控制模式（键盘/鼠标 <-> 手柄）
     */
    private void toggleControlMode() {
        if (mIsGamepadMode) {
            // 当前是手柄模式，切换到键盘模式
            new AlertDialog.Builder(this)
                .setTitle("切换为键盘模式")
                .setMessage("将加载默认键盘布局，当前布局将被替换。是否继续？")
                .setPositiveButton("确定", (dialog, which) -> {
                    loadDefaultKeyboardLayout();
                    mIsGamepadMode = false;
                    updateModeToggleButton();
                    displayLayout();
                    Toast.makeText(this, "已切换为键盘模式", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        } else {
            // 当前是键盘模式，切换到手柄模式
            new AlertDialog.Builder(this)
                .setTitle("切换为手柄模式")
                .setMessage("将加载默认手柄布局，当前布局将被替换。是否继续？")
                .setPositiveButton("确定", (dialog, which) -> {
                    loadDefaultGamepadLayout();
                    mIsGamepadMode = true;
                    updateModeToggleButton();
                    displayLayout();
                    Toast.makeText(this, "已切换为手柄模式", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        }
    }

    /**
     * 显示摇杆模式批量设置对话框
     */
    private void showJoystickModeDialog() {
        // 统计当前布局中的摇杆数量
        int joystickCount = 0;
        for (ControlData control : mCurrentConfig.controls) {
            if (control.type == ControlData.TYPE_JOYSTICK) {
                joystickCount++;
            }
        }

        if (joystickCount == 0) {
            Toast.makeText(this, "当前布局中没有摇杆", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建选项列表
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            String.valueOf(ControlData.JOYSTICK_MODE_KEYBOARD),
            "键盘按键模式",
            "使用 WASD 等按键控制移动"
        ));
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            String.valueOf(ControlData.JOYSTICK_MODE_MOUSE),
            "鼠标移动模式",
            "控制鼠标指针移动（用于瞄准）"
        ));
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            String.valueOf(ControlData.JOYSTICK_MODE_SDL_CONTROLLER),
            "SDL 控制器模式",
            "模拟真实游戏手柄摇杆"
        ));

        // 显示 MD3 风格选择对话框
        new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle("摇杆模式设置")
            .setSubtitle("将为所有 " + joystickCount + " 个摇杆设置统一模式")
            .setOptions(options)
            .setAutoCloseOnSelect(true)
            .setOnOptionSelectedListener(value -> {
                int newMode = Integer.parseInt(value);
                String modeName;

                switch (newMode) {
                    case ControlData.JOYSTICK_MODE_KEYBOARD:
                        modeName = "键盘按键模式";
                        break;
                    case ControlData.JOYSTICK_MODE_MOUSE:
                        modeName = "鼠标移动模式";
                        break;
                    case ControlData.JOYSTICK_MODE_SDL_CONTROLLER:
                        modeName = "SDL控制器模式";
                        break;
                    default:
                        return;
                }

                // 批量更新所有摇杆的模式
                int updatedCount = 0;
                for (ControlData control : mCurrentConfig.controls) {
                    if (control.type == ControlData.TYPE_JOYSTICK) {
                        control.joystickMode = newMode;

                        // 根据模式设置合适的默认值
                        if (newMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
                            // 键盘模式：确保有按键映射
                            if (control.joystickKeys == null || control.joystickKeys.length < 4) {
                                control.joystickKeys = new int[]{
                                    ControlData.SDL_SCANCODE_W,  // up
                                    ControlData.SDL_SCANCODE_D,  // right
                                    ControlData.SDL_SCANCODE_S,  // down
                                    ControlData.SDL_SCANCODE_A   // left
                                };
                            }
                        } else if (newMode == ControlData.JOYSTICK_MODE_MOUSE) {
                            // 鼠标模式：清除按键映射
                            control.joystickKeys = null;
                        } else {
                            // SDL控制器模式：清除按键映射，设置默认为左摇杆
                            control.joystickKeys = null;
                            if (!control.name.contains("右")) {
                                control.xboxUseRightStick = false;
                            }
                        }
                        updatedCount++;
                    }
                }

                // 刷新显示
                displayLayout();

                Toast.makeText(this,
                    "已将 " + updatedCount + " 个摇杆设置为" + modeName,
                    Toast.LENGTH_SHORT).show();
            })
            .show(getSupportFragmentManager(), "joystick_mode_selector");
    }

    /**
     * 加载默认键盘布局
     */
    private void loadDefaultKeyboardLayout() {
        mCurrentConfig = new ControlConfig();
        mCurrentConfig.name = "默认键盘布局";
        mCurrentConfig.controls = new java.util.ArrayList<>();

        // 添加移动摇杆（键盘模式）
        ControlData moveJoystick = ControlData.createDefaultJoystick();
        moveJoystick.y = mScreenHeight - moveJoystick.height - 50;
        moveJoystick.joystickMode = ControlData.JOYSTICK_MODE_KEYBOARD;
        mCurrentConfig.controls.add(moveJoystick);

        // 添加跳跃按钮
        ControlData jumpButton = ControlData.createDefaultJumpButton();
        jumpButton.x = mScreenWidth - jumpButton.width - 200;
        jumpButton.y = mScreenHeight - jumpButton.height - 100;
        mCurrentConfig.controls.add(jumpButton);

        // 添加攻击按钮
        ControlData attackButton = ControlData.createDefaultAttackButton();
        attackButton.x = mScreenWidth - attackButton.width - 50;
        attackButton.y = mScreenHeight - attackButton.height - 200;
        mCurrentConfig.controls.add(attackButton);
    }

    /**
     * 加载默认手柄布局
     */
    private void loadDefaultGamepadLayout() {
        mCurrentConfig = new ControlConfig();
        mCurrentConfig.name = "默认手柄布局";
        mCurrentConfig.controls = new java.util.ArrayList<>();

        // 使用 ControlData 提供的完整手柄布局
        ControlData[] gamepadControls = ControlData.createDefaultGamepadLayout();
        for (ControlData control : gamepadControls) {
            mCurrentConfig.controls.add(control);
        }
    }
    
    @Override
    public void onBackPressed() {
        // 先检查设置弹窗
        if (mSettingsDialog != null && mSettingsDialog.isDisplaying()) {
            mSettingsDialog.hide();
            return;
        }

        // 再检查控件编辑弹窗
        if (mSideDialog != null && mSideDialog.isDisplaying()) {
            mSideDialog.hide();
            return;
        }

        // 显示退出确认对话框
        new AlertDialog.Builder(this)
            .setTitle("退出编辑器")
            .setMessage("是否保存当前布局？")
            .setPositiveButton("保存并退出", (dialog, which) -> {
                saveLayout();
                finish();
            })
            .setNegativeButton("直接退出", (dialog, which) -> finish())
            .setNeutralButton("取消", null)
            .show();
    }
}
