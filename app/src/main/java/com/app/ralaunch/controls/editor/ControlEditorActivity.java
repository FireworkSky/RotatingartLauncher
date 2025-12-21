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

import android.content.Context;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.*;
import com.app.ralaunch.controls.ControlDataConverter;
import com.app.ralaunch.utils.LocaleManager;

import java.io.File;
import java.io.InputStream;

/**
 * 控件编辑器 Activity
 * 
 * 独立的控件编辑界面，用于在游戏外编辑控件布局。
 * 使用统一的 ControlEditorManager (MODE_STANDALONE) 管理编辑逻辑。
 */
public class ControlEditorActivity extends AppCompatActivity {
    private static final String TAG = "ControlEditorActivity";

    private FrameLayout mEditorContainer;
    private ControlLayout mPreviewLayout;
    private GridOverlayView mGridOverlay;
    
    // 统一的控件编辑管理器
    private ControlEditorManager mEditorManager;

    private ControlConfig mCurrentConfig;
    private SDLInputBridge mDummyBridge;
    private DisplayMetrics mMetrics;
    private int mScreenWidth;
    private int mScreenHeight;

    // 布局管理
    private com.app.ralaunch.utils.ControlLayoutManager mLayoutManager;
    private String mCurrentLayoutName;

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用语言设置
        super.attachBaseContext(LocaleManager.applyLanguage(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        com.app.ralaunch.utils.DensityAdapter.adapt(this, true);
        
        // 应用动态颜色主题（必须在 super.onCreate 之前，避免闪烁）
        com.app.ralaunch.manager.DynamicColorManager dynamicColorManager = 
            com.app.ralaunch.manager.DynamicColorManager.getInstance();
        com.app.ralaunch.data.SettingsManager settingsManager = 
            com.app.ralaunch.data.SettingsManager.getInstance(this);
        dynamicColorManager.applyCustomThemeColor(this, settingsManager.getThemeColor());
        
        // 应用其他主题设置（深色/浅色模式等）
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            settingsManager.getThemeMode() == 0 ? 
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM :
            settingsManager.getThemeMode() == 1 ? 
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES :
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );
        
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
            mCurrentLayoutName = mLayoutManager.getCurrentLayoutName();
        }

        initUI();

        // 延迟加载布局
        mEditorContainer.post(() -> loadLayoutFromManager());
    }
    
    private void initUI() {
        mEditorContainer = findViewById(R.id.editor_container);

        // 设置按钮点击显示设置弹窗，并支持拖动
        View drawerButton = findViewById(R.id.drawer_button);
        setupDraggableButton(drawerButton, () -> {
            if (mEditorManager != null) {
                mEditorManager.showSettingsDialog();
            }
        });
    }
    
    /**
     * 设置可拖动的按钮
     */
    private void setupDraggableButton(View button, Runnable onClickAction) {
        if (button == null) return;
        
        button.setOnClickListener(v -> {
            if (onClickAction != null) {
                onClickAction.run();
            }
        });
        
        button.setOnTouchListener(new View.OnTouchListener() {
            private float mLastX;
            private float mLastY;
            private float mInitialTouchX;
            private float mInitialTouchY;
            private float mInitialButtonX;
            private float mInitialButtonY;
            private boolean mIsDragging = false;
            private static final float DRAG_THRESHOLD = 10f;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = event.getRawX();
                        mLastY = event.getRawY();
                        mInitialTouchX = event.getRawX();
                        mInitialTouchY = event.getRawY();
                        
                        int[] location = new int[2];
                        v.getLocationOnScreen(location);
                        mInitialButtonX = location[0];
                        mInitialButtonY = location[1];
                        
                        mIsDragging = false;
                        return false;
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - mLastX;
                        float deltaY = event.getRawY() - mLastY;
                        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        
                        if (distance > DRAG_THRESHOLD) {
                            if (!mIsDragging) {
                                mIsDragging = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            
                            float newScreenX = mInitialButtonX + (event.getRawX() - mInitialTouchX);
                            float newScreenY = mInitialButtonY + (event.getRawY() - mInitialTouchY);
                            
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int maxX = metrics.widthPixels - v.getWidth();
                            int maxY = metrics.heightPixels - v.getHeight();
                            newScreenX = Math.max(0, Math.min(newScreenX, maxX));
                            newScreenY = Math.max(0, Math.min(newScreenY, maxY));
                            
                            int[] parentLocation = new int[2];
                            ((View) v.getParent()).getLocationOnScreen(parentLocation);
                            float newX = newScreenX - parentLocation[0];
                            float newY = newScreenY - parentLocation[1];
                            
                            if (v.getParent() instanceof android.widget.FrameLayout) {
                                v.setX(newX);
                                v.setY(newY);
                            } else {
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
     * 从 ControlLayoutManager 加载布局
     */
    private void loadLayoutFromManager() {
        com.app.ralaunch.model.ControlLayout layout = null;
        for (com.app.ralaunch.model.ControlLayout l : mLayoutManager.getLayouts()) {
            if (l.getName().equals(mCurrentLayoutName)) {
                layout = l;
                break;
            }
        }

        if (layout == null || layout.getElements().isEmpty()) {
            loadDefaultLayout();
        } else {
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.name = layout.getName();
            mCurrentConfig.controls = new java.util.ArrayList<>();

            for (com.app.ralaunch.model.ControlElement element : layout.getElements()) {
                ControlData control = ControlDataConverter.elementToData(element, mScreenWidth, mScreenHeight);
                if (control != null) {
                    mCurrentConfig.controls.add(control);
                }
            }
            
            // 如果转换后控件列表为空，回退到默认布局
            if (mCurrentConfig.controls.isEmpty()) {
                android.util.Log.w(TAG, "Layout conversion resulted in empty controls, falling back to default layout");
                loadDefaultLayout();
            }
        }

        displayLayout();
    }
    
    private void loadDefaultLayout() {
        // 尝试从 ControlLayoutManager 加载键盘模式默认布局
        try {
            com.app.ralaunch.model.ControlLayout defaultLayout = mLayoutManager.getLayout("keyboard_mode");
            if (defaultLayout != null && !defaultLayout.getElements().isEmpty()) {
                mCurrentConfig = new ControlConfig();
                mCurrentConfig.name = defaultLayout.getName();
                mCurrentConfig.controls = new java.util.ArrayList<>();

                for (com.app.ralaunch.model.ControlElement element : defaultLayout.getElements()) {
                    ControlData control = ControlDataConverter.elementToData(element, mScreenWidth, mScreenHeight);
                    if (control != null) {
                        mCurrentConfig.controls.add(control);
                    }
                }
                
                // 如果成功加载了默认布局，直接返回
                if (!mCurrentConfig.controls.isEmpty()) {
                    android.util.Log.d(TAG, "Loaded default keyboard layout with " + mCurrentConfig.controls.size() + " controls");
                    return;
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to load default layout from manager", e);
        }
        
        // 如果加载失败，创建一个空的配置（至少保证不会崩溃）
        mCurrentConfig = new ControlConfig();
        mCurrentConfig.name = getString(R.string.control_layout_default_name);
        mCurrentConfig.controls = new java.util.ArrayList<>();
        android.util.Log.w(TAG, "Created empty default layout as fallback");
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

        // 禁用裁剪
        disableClippingRecursive(mPreviewLayout);

        mEditorContainer.addView(mPreviewLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 创建或更新编辑管理器（使用独立模式，自动进入编辑状态）
        if (mEditorManager == null) {
            mEditorManager = new ControlEditorManager(this, mPreviewLayout, mEditorContainer, 
                ControlEditorManager.MODE_STANDALONE);
            mEditorManager.setOnLayoutChangedListener(() -> {
                // 布局变化时的处理（如需要可在此刷新）
            });
        } else {
            // 布局重新创建后更新引用
            mEditorManager.setControlLayout(mPreviewLayout);
        }
        
        // 初始化设置对话框
        mEditorManager.initEditorSettingsDialog();
    }

    /**
     * 递归禁用所有子视图的裁剪
     */
    private void disableClippingRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);

            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableClippingRecursive(viewGroup.getChildAt(i));
            }
        }

        view.setClipToOutline(false);
        view.setClipBounds(null);
    }

    @Override
    public void onBackPressed() {
        // 先检查设置弹窗
        if (mEditorManager != null && mEditorManager.isSettingsDialogShowing()) {
            mEditorManager.hideSettingsDialog();
            return;
        }

        // 再检查控件编辑弹窗
        if (mEditorManager != null && mEditorManager.isEditDialogShowing()) {
            mEditorManager.dismissEditDialog();
            return;
        }

        // 检查是否有未保存的更改
        if (mEditorManager != null && mEditorManager.hasUnsavedChanges()) {
            // 有未保存的更改，显示退出确认对话框
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.editor_exit_title))
                .setMessage(getString(R.string.editor_exit_save_confirm))
                .setPositiveButton(getString(R.string.editor_save_and_exit), (dialog, which) -> {
                    if (mEditorManager != null) {
                        mEditorManager.saveLayout(mCurrentLayoutName);
                    }
                    finish();
                })
                .setNegativeButton(getString(R.string.editor_exit), (dialog, which) -> finish())
                .setNeutralButton(getString(R.string.cancel), null)
                .show();
        } else {
            // 没有未保存的更改，直接退出
            finish();
        }
    }
}
