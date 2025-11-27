package com.app.ralaunch.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlConfig;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlLayout;
import com.app.ralaunch.controls.ControlView;
import com.app.ralaunch.controls.editor.UnifiedEditorSettingsDialog;
import com.app.ralaunch.controls.editor.ControlEditDialogMD;
import com.app.ralaunch.controls.editor.ControlEditorOperations;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;

/**
 * 游戏内控件编辑器管理器
 * 
 * 统一管理游戏内控件编辑功能，包括：
 * - 编辑模式进入/退出
 * - 控件添加/删除/编辑
 * - 布局保存/加载
 * - 编辑对话框管理
 * 
 * 减少 GameActivity 的代码耦合
 */
public class GameControlEditorManager {
    private static final String TAG = "GameControlEditorManager";
    
    private Context mContext;
    private ControlLayout mControlLayout;
    private ViewGroup mContentFrame;
    private View mEditorSettingsButton;
    private View mDrawerButton;
    
    private boolean mIsInEditor = false;
    private boolean mHasUnsavedChanges = false;
    
    private ControlEditDialogMD mControlEditDialog;
    private UnifiedEditorSettingsDialog mEditorSettingsDialog;
    
    private OnEditorStateChangedListener mStateListener;
    
    /**
     * 编辑状态变化监听器
     */
    public interface OnEditorStateChangedListener {
        void onEditorEntered();
        void onEditorExited();
    }
    
    public GameControlEditorManager(Context context, ControlLayout controlLayout, 
                                   ViewGroup contentFrame, View editorSettingsButton, 
                                   View drawerButton) {
        mContext = context;
        mControlLayout = controlLayout;
        mContentFrame = contentFrame;
        mEditorSettingsButton = editorSettingsButton;
        mDrawerButton = drawerButton;
    }
    
    /**
     * 设置编辑状态变化监听器
     */
    public void setOnEditorStateChangedListener(OnEditorStateChangedListener listener) {
        mStateListener = listener;
    }
    
    /**
     * 进入编辑模式
     */
    public void enterEditMode() {
        if (mControlLayout == null) return;
        
        mIsInEditor = true;
        mHasUnsavedChanges = false;
        mControlLayout.setModifiable(true);
        
        // 初始化编辑对话框
        initControlEditDialog();

        // 设置控件点击监听器
        mControlLayout.setEditControlListener(data -> {
            if (mControlEditDialog != null) {
                mControlEditDialog.show(data);
            }
        });
        
        // 设置控件修改监听器（拖动时）
        mControlLayout.setOnControlChangedListener(() -> {
            mHasUnsavedChanges = true;
        });
        
        // 初始化编辑器设置弹窗（如果还没有初始化）
        initEditorSettingsDialog();
        
        // 设置编辑模式为启用状态，显示添加控件区域
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(true);
        }
        
        // 统一使用 game_drawer_button，不再切换按钮
        // 编辑模式和普通模式都使用同一个按钮（game_drawer_button）
        if (mEditorSettingsButton != null) {
            mEditorSettingsButton.setVisibility(View.GONE); // 隐藏编辑模式专用按钮
        }
        if (mDrawerButton != null) {
            mDrawerButton.setVisibility(View.VISIBLE); // 始终显示统一的工具栏按钮
        }
        
        // 确保控制可见
        mControlLayout.setControlsVisible(true);
        
        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout);
        
        Toast.makeText(mContext, R.string.editor_mode_on, Toast.LENGTH_SHORT).show();
        
        if (mStateListener != null) {
            mStateListener.onEditorEntered();
        }
    }
    
    /**
     * 退出编辑模式
     */
    public void exitEditMode() {
        // 如果有未保存的修改，弹出确认对话框
        if (mHasUnsavedChanges) {
            new AlertDialog.Builder(mContext)
                .setTitle(R.string.editor_exit_confirm)
                .setMessage(R.string.editor_exit_message)
                .setPositiveButton(R.string.game_menu_yes, (dialog, which) -> {
                    performExitEditMode();
                })
                .setNegativeButton(R.string.game_menu_no, null)
                .show();
        } else {
            performExitEditMode();
        }
    }
    
    /**
     * 执行退出编辑模式
     */
    private void performExitEditMode() {
        if (mControlLayout == null) return;
        
        mIsInEditor = false;
        mControlLayout.setModifiable(false);
        
        // 设置编辑模式为禁用状态，隐藏添加控件区域
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(false);
        }
        
        // 重新加载布局
        mControlLayout.loadLayoutFromManager();
        
        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout);
        
        // 统一使用 game_drawer_button，始终显示
        if (mEditorSettingsButton != null) {
            mEditorSettingsButton.setVisibility(View.GONE); // 隐藏编辑模式专用按钮
        }
        if (mDrawerButton != null) {
            mDrawerButton.setVisibility(View.VISIBLE); // 始终显示统一的工具栏按钮
        }
        
        Toast.makeText(mContext, R.string.editor_mode_off, Toast.LENGTH_SHORT).show();
        
        if (mStateListener != null) {
            mStateListener.onEditorExited();
        }
    }
    
    /**
     * 初始化控件编辑对话框
     */
    private void initControlEditDialog() {
        if (mControlEditDialog != null) return;

        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();

        mControlEditDialog = new ControlEditDialogMD(mContext,
            metrics.widthPixels, metrics.heightPixels);

        // 设置实时更新监听器
        mControlEditDialog.setOnControlUpdatedListener(control -> {
            if (mControlLayout != null) {
                // 实时更新视图（避免重新加载整个布局）
                for (int i = 0; i < mControlLayout.getChildCount(); i++) {
                    View child = mControlLayout.getChildAt(i);
                    if (child instanceof ControlView) {
                        ControlView controlView = (ControlView) child;
                        if (controlView.getData() == control) {
                            // 更新布局参数
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            if (layoutParams instanceof FrameLayout.LayoutParams) {
                                FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
                                frameParams.width = (int) control.width;
                                frameParams.height = (int) control.height;
                                frameParams.leftMargin = (int) control.x;
                                frameParams.topMargin = (int) control.y;
                                child.setLayoutParams(frameParams);
                            }
                            // 更新视觉属性
                            child.setAlpha(control.opacity);
                            child.setVisibility(control.visible ? View.VISIBLE : View.INVISIBLE);
                            // 刷新控件绘制
                            controlView.updateData(control);
                            child.invalidate();
                            break;
                        }
                    }
                }
                mHasUnsavedChanges = true;
            }
        });

        // 设置删除监听器
        mControlEditDialog.setOnControlDeletedListener(control -> {
            if (mControlLayout != null) {
                ControlConfig config = mControlLayout.getConfig();
                if (config != null && config.controls != null) {
                    config.controls.remove(control);
                    mControlLayout.loadLayout(config);
                    disableClippingRecursive(mControlLayout);
                    mHasUnsavedChanges = true;
                }
            }
        });
    }
    
    /**
     * 初始化编辑器设置弹窗
     */
    private void initEditorSettingsDialog() {
        if (mEditorSettingsDialog != null) return;

        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        mEditorSettingsDialog = new UnifiedEditorSettingsDialog(
            mContext, mContentFrame, metrics.widthPixels, UnifiedEditorSettingsDialog.DialogMode.EDITOR);

        mEditorSettingsDialog.setOnMenuItemClickListener(new UnifiedEditorSettingsDialog.OnMenuItemClickListener() {
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
                addText();
            }

            @Override
            public void onSaveLayout() {
                saveControlLayout();
            }

            @Override
            public void onLoadLayout() {
                loadControlLayout();
            }

            @Override
            public void onResetDefault() {
                resetToDefaultLayout();
            }

            @Override
            public void onLastAction() {
                exitEditMode();
            }

            @Override
            public void onToggleEditMode() {
                // 切换编辑模式：根据当前状态进入或退出
                if (mIsInEditor) {
                    exitEditMode();
                } else {
                    enterEditMode();
                }
            }
        });
    }
    
    /**
     * 添加按钮
     */
    public void addButton() {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        if (config == null) {
            config = new ControlConfig();
            config.controls = new java.util.ArrayList<>();
            mControlLayout.loadLayout(config);
        }
        
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        ControlData button = ControlEditorOperations.addButton(config, 
            metrics.widthPixels, metrics.heightPixels);
        
        if (button != null) {
            mControlLayout.loadLayout(config);
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            Toast.makeText(mContext, "已添加按钮", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 添加摇杆
     */
    public void addJoystick() {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        if (config == null) {
            config = new ControlConfig();
            config.controls = new java.util.ArrayList<>();
            mControlLayout.loadLayout(config);
        }
        
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        ControlData joystick = ControlEditorOperations.addJoystick(config, 
            metrics.widthPixels, metrics.heightPixels);
        
        if (joystick != null) {
            mControlLayout.loadLayout(config);
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            Toast.makeText(mContext, "已添加摇杆", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 添加文本控件
     */
    public void addText() {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        if (config == null) {
            config = new ControlConfig();
            config.controls = new java.util.ArrayList<>();
            mControlLayout.loadLayout(config);
        }
        
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        ControlData text = ControlEditorOperations.addText(config, 
            metrics.widthPixels, metrics.heightPixels);
        
        if (text != null) {
            mControlLayout.loadLayout(config);
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            Toast.makeText(mContext, "已添加文本", Toast.LENGTH_SHORT).show();
        }
    }
    
    
    /**
     * 保存控制布局
     */
    public void saveControlLayout() {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        com.app.ralaunch.utils.ControlLayoutManager manager = new com.app.ralaunch.utils.ControlLayoutManager(mContext);
        String layoutName = manager.getCurrentLayoutName();
        
        if (ControlEditorOperations.saveLayout(mContext, config, layoutName)) {
            mHasUnsavedChanges = false;
        }
    }
    
    /**
     * 加载控制布局
     */
    public void loadControlLayout() {
        Toast.makeText(mContext, "加载布局功能开发中...", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 重置为默认控制布局
     */
    public void resetToDefaultLayout() {
        ControlEditorOperations.resetToDefaultLayout(mContext, mControlLayout, () -> {
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
        });
    }
    
    /**
     * 显示编辑器设置对话框
     * 不自动进入编辑模式，用户需要在弹窗中手动点击"进入编辑模式"
     */
    public void showEditorSettingsDialog() {
        // 初始化编辑器设置对话框（如果还没有初始化）
        initEditorSettingsDialog();

        // 同步编辑模式状态到对话框
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(mIsInEditor);
            mEditorSettingsDialog.show();
        }
    }
    
    /**
     * 隐藏编辑器设置对话框
     */
    public void hideEditorSettingsDialog() {
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.hide();
        }
    }
    
    /**
     * 是否处于编辑模式
     */
    public boolean isInEditor() {
        return mIsInEditor;
    }
    
    /**
     * 是否有未保存的修改
     */
    public boolean hasUnsavedChanges() {
        return mHasUnsavedChanges;
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
}

