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
import com.app.ralaunch.controls.ControlDataConverter;
import com.app.ralaunch.controls.editor.ControlEditorOperations;
import com.app.ralaunch.controls.editor.manager.ControlDataSyncManager;

import java.io.File;
import java.io.InputStream;

public class ControlEditorActivity extends AppCompatActivity {
    private static final String TAG = "ControlEditorActivity";

    private FrameLayout mEditorContainer;
    private ControlLayout mPreviewLayout;
    private GridOverlayView mGridOverlay;
    private ControlEditDialogMD mEditDialog;
    private UnifiedEditorSettingsDialog mSettingsDialog;
    private ControlView mSelectedControl = null; // 当前选中的控件

    private ControlConfig mCurrentConfig;
    private SDLInputBridge mDummyBridge;
    private DisplayMetrics mMetrics;
    private int mScreenWidth;
    private int mScreenHeight;

    // 布局管理
    private com.app.ralaunch.utils.ControlLayoutManager mLayoutManager;
    private String mCurrentLayoutName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题设置（必须在 super.onCreate 之前）
        com.app.ralaunch.manager.ThemeManager themeManager = 
            new com.app.ralaunch.manager.ThemeManager(this);
        themeManager.applyThemeFromSettings();
        
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

        // 初始化布局管理器
        mLayoutManager = new com.app.ralaunch.utils.ControlLayoutManager(this);

        // 获取要编辑的布局名称
        mCurrentLayoutName = getIntent().getStringExtra("layout_name");
        if (mCurrentLayoutName == null) {
            // 如果没有传入布局名称，使用当前默认布局
            mCurrentLayoutName = mLayoutManager.getCurrentLayoutName();
        }

        initUI();

        // 延迟加载布局
        mEditorContainer.post(() -> loadLayoutFromManager());
    }
    
    private void initUI() {
        mEditorContainer = findViewById(R.id.editor_container);

        // 设置按钮点击显示 MD3 设置弹窗，并支持拖动
        View drawerButton = findViewById(R.id.drawer_button);
        setupDraggableButton(drawerButton, () -> {
            if (mSettingsDialog != null) {
                mSettingsDialog.show();
            }
        });

        // 初始化对话框
        setupDialogs();
    }
    
    /**
     * 设置可拖动的按钮
     */
    private void setupDraggableButton(View button, Runnable onClickAction) {
        if (button == null) return;
        
        // 点击事件
        button.setOnClickListener(v -> {
            if (onClickAction != null) {
                onClickAction.run();
            }
        });
        
        // 拖动功能
        button.setOnTouchListener(new View.OnTouchListener() {
            private float mLastX;
            private float mLastY;
            private float mInitialTouchX;
            private float mInitialTouchY;
            private float mInitialButtonX;
            private float mInitialButtonY;
            private boolean mIsDragging = false;
            private static final float DRAG_THRESHOLD = 10f; // 拖动阈值（像素）
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = event.getRawX();
                        mLastY = event.getRawY();
                        mInitialTouchX = event.getRawX();
                        mInitialTouchY = event.getRawY();
                        
                        // 获取按钮的初始屏幕位置
                        int[] location = new int[2];
                        v.getLocationOnScreen(location);
                        mInitialButtonX = location[0];
                        mInitialButtonY = location[1];
                        
                        mIsDragging = false;
                        return false; // 允许点击事件继续
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - mLastX;
                        float deltaY = event.getRawY() - mLastY;
                        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        
                        // 如果移动距离超过阈值，开始拖动
                        if (distance > DRAG_THRESHOLD) {
                            if (!mIsDragging) {
                                mIsDragging = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            
                            // 计算按钮的新屏幕位置
                            float newScreenX = mInitialButtonX + (event.getRawX() - mInitialTouchX);
                            float newScreenY = mInitialButtonY + (event.getRawY() - mInitialTouchY);
                            
                            // 限制在屏幕范围内（允许拖动到最左边x=0）
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int maxX = metrics.widthPixels - v.getWidth();
                            int maxY = metrics.heightPixels - v.getHeight();
                            newScreenX = Math.max(0, Math.min(newScreenX, maxX));
                            newScreenY = Math.max(0, Math.min(newScreenY, maxY));
                            
                            // 将屏幕坐标转换为相对于父容器的坐标
                            int[] parentLocation = new int[2];
                            ((View) v.getParent()).getLocationOnScreen(parentLocation);
                            float newX = newScreenX - parentLocation[0];
                            float newY = newScreenY - parentLocation[1];
                            
                            // 更新位置（优先使用setX/setY，如果父容器是FrameLayout）
                            if (v.getParent() instanceof android.widget.FrameLayout) {
                                v.setX(newX);
                                v.setY(newY);
                            } else {
                                // 使用MarginLayoutParams
                                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                                params.leftMargin = (int) newX;
                                params.topMargin = (int) newY;
                                v.setLayoutParams(params);
                            }
                            
                            mLastX = event.getRawX();
                            mLastY = event.getRawY();
                            return true;
                        }
                        return false;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mIsDragging) {
                            mIsDragging = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }

    /**
     * 初始化或重新初始化对话框
     * 由于 displayLayout() 会清除所有视图，需要重新创建对话框
     */
    private void setupDialogs() {
        // 创建MD风格编辑对话框
        mEditDialog = new ControlEditDialogMD(this, mScreenWidth, mScreenHeight);

        // 设置更新监听器 - 使用统一的数据同步管理器
        mEditDialog.setOnControlUpdatedListener(control -> {
            // 使用统一的数据同步管理器更新视图
            ControlDataSyncManager.syncControlDataToView(mPreviewLayout, control);
        });
        
        // 对话框关闭时清除选中状态
        mEditDialog.setOnDismissListener(dialog -> {
            clearSelectedControlState();
        });

        // 设置删除监听器
        mEditDialog.setOnControlDeletedListener(control -> {
            if (mCurrentConfig != null && mCurrentConfig.controls != null) {
                // 使用引用比较删除（因为 ControlData 没有实现 equals）
                // 遍历列表找到相同的引用并删除
                boolean removed = false;
                for (int i = mCurrentConfig.controls.size() - 1; i >= 0; i--) {
                    if (mCurrentConfig.controls.get(i) == control) {
                        mCurrentConfig.controls.remove(i);
                        removed = true;
                        break;
                    }
                }
                if (!removed) {
                    // 如果引用比较失败，尝试使用 remove(Object)（虽然可能失败）
                    mCurrentConfig.controls.remove(control);
                }
                displayLayout();
                // 删除后立即保存布局，确保导出时使用最新数据
                saveLayout();
            }
        });

        // 设置复制监听器
        mEditDialog.setOnControlCopiedListener(control -> {
            if (mCurrentConfig != null && mCurrentConfig.controls != null) {
                mCurrentConfig.controls.add(control);
                displayLayout();
            }
        });

        // 创建 MD3 设置弹窗
        mSettingsDialog = new UnifiedEditorSettingsDialog(this, mEditorContainer, mScreenWidth, UnifiedEditorSettingsDialog.DialogMode.EDITOR);
        mSettingsDialog.setOnMenuItemClickListener(new UnifiedEditorSettingsDialog.OnMenuItemClickListener() {
            @Override
            public void onAddButton() {
                addButton();
            }

            @Override
            public void onAddJoystick() {
                addJoystick();
            }

            @Override
            public void onAddText() {
                // 文本功能已移除
            }

            @Override
            public void onToggleEditMode() {
                // 外部编辑器始终处于编辑模式，无需切换
            }

            @Override
            public void onHideCursorChanged(boolean hide) {
                // 隐藏鼠标光标设置变化
            }
        });
    }
    
    /**
     * 从 ControlLayoutManager 加载布局
     */
    private void loadLayoutFromManager() {
        // 根据布局名称查找布局
        com.app.ralaunch.model.ControlLayout layout = null;
        for (com.app.ralaunch.model.ControlLayout l : mLayoutManager.getLayouts()) {
            if (l.getName().equals(mCurrentLayoutName)) {
                layout = l;
                break;
            }
        }

        if (layout == null || layout.getElements().isEmpty()) {
            // 如果布局为空或不存在，加载默认布局
            loadDefaultLayout();
        } else {
            // 使用统一的转换器转换 ControlElement 列表为 ControlData 列表
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.name = layout.getName();
            mCurrentConfig.controls = new java.util.ArrayList<>();

            for (com.app.ralaunch.model.ControlElement element : layout.getElements()) {
                ControlData control = ControlDataConverter.elementToData(element, mScreenWidth, mScreenHeight);
                if (control != null) {
                    mCurrentConfig.controls.add(control);
                }
            }
        }

        displayLayout();
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
        // 清除选中状态
        clearSelectedControlState();
        
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

        // 重新创建对话框（因为 removeAllViews 清除了它们的视图）
        setupDialogs();

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
    
    /**
     * 清除之前选中控件的按下状态
     */
    private void clearSelectedControlState() {
        if (mSelectedControl != null) {
            if (mSelectedControl instanceof VirtualButton) {
                ((VirtualButton) mSelectedControl).setPressedState(false);
            }
            mSelectedControl = null;
        }
    }
    
    /**
     * 设置当前选中的控件
     */
    private void setSelectedControl(ControlView controlView) {
        mSelectedControl = controlView;
        if (controlView instanceof VirtualButton) {
            ((VirtualButton) controlView).setPressedState(true);
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
                        // 清除之前选中控件的按下状态
                        clearSelectedControlState();
                        
                        // 设置当前控件为选中状态
                        setSelectedControl(controlView);
                        
                        // 点击事件 - 显示MD风格编辑对话框
                        mEditDialog.show(data);
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void addButton() {
        if (mCurrentConfig == null) {
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.controls = new java.util.ArrayList<>();
        }
        
        ControlData button = ControlEditorOperations.addButton(mCurrentConfig, mScreenWidth, mScreenHeight);
        if (button != null) {
            displayLayout();
            Toast.makeText(this, "已添加按键", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addJoystick() {
        if (mCurrentConfig == null) {
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.controls = new java.util.ArrayList<>();
        }
        
        ControlData joystick = ControlEditorOperations.addJoystick(mCurrentConfig, mScreenWidth, mScreenHeight);
        if (joystick != null) {
            displayLayout();
            Toast.makeText(this, "已添加摇杆", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLayout() {
        ControlEditorOperations.saveLayout(this, mCurrentConfig, mCurrentLayoutName);
    }
    
    private void loadLayout() {
        // 显示选择对话框
        String[] options = {"键盘模式布局", "手柄模式布局"};
        new AlertDialog.Builder(this)
            .setTitle("选择布局")
            .setItems(options, (dialog, which) -> {
                String layoutFile = which == 0 ? "default_layout.json" : "gamepad_layout.json";
                try {
                    InputStream is = getAssets().open("controls/" + layoutFile);
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");
                    mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);

                    // 重新显示布局
                    displayLayout();

                    Toast.makeText(this, "已加载" + options[which], Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load layout: " + layoutFile, e);
                    Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void resetToDefault() {
        ControlEditorOperations.resetToDefaultLayout(this, mPreviewLayout, () -> {
            displayLayout();
        });
    }


    /**
     * 显示摇杆模式批量设置对话框
     */
    
    @Override
    public void onBackPressed() {
        // 先检查设置弹窗
        if (mSettingsDialog != null && mSettingsDialog.isDisplaying()) {
            mSettingsDialog.hide();
            return;
        }

        // 再检查控件编辑弹窗
        if (mEditDialog != null && mEditDialog.isShowing()) {
            mEditDialog.dismiss();
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
