package com.app.ralaunch.controls.editor;

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
import com.app.ralaunch.controls.editor.manager.ControlDataSyncManager;
import com.app.ralaunch.utils.AppLogger;

/**
 * 统一的控件编辑管理器
 * 
 * 支持两种模式：
 * 1. MODE_STANDALONE (游戏外): 直接进入编辑模式，用于独立的控件编辑器界面
 * 2. MODE_IN_GAME (游戏内): 需要手动进入编辑模式，用于游戏运行时的编辑
 */
public class ControlEditorManager {
    private static final String TAG = "ControlEditorManager";
    
    /** 独立模式：直接进入编辑模式 */
    public static final int MODE_STANDALONE = 0;
    /** 游戏内模式：需要手动进入编辑模式 */
    public static final int MODE_IN_GAME = 1;
    
    private Context mContext;
    private ControlLayout mControlLayout;
    private ViewGroup mContentFrame;
    private int mMode;
    private int mScreenWidth;
    private int mScreenHeight;
    
    private boolean mIsInEditor = false;
    private boolean mHasUnsavedChanges = false;
    
    private ControlEditDialogMD mControlEditDialog;
    private UnifiedEditorSettingsDialog mEditorSettingsDialog;
    
    private OnEditorStateChangedListener mStateListener;
    private OnLayoutChangedListener mLayoutChangedListener;
    private OnFPSDisplayChangedListener mFPSDisplayListener;
    private OnHideControlsListener mOnHideControlsListener;
    private OnExitGameListener mOnExitGameListener;
    
    /**
     * 编辑状态变化监听器
     */
    public interface OnEditorStateChangedListener {
        void onEditorEntered();
        void onEditorExited();
    }
    
    /**
     * 布局变化监听器（用于 Activity 刷新显示）
     */
    public interface OnLayoutChangedListener {
        void onLayoutChanged();
    }

    /**
     * FPS 显示设置变化监听器
     */
    public interface OnFPSDisplayChangedListener {
        void onFPSDisplayChanged(boolean enabled);
    }

    /**
     * 隐藏控件监听器
     */
    public interface OnHideControlsListener {
        void onHideControls();
    }

    /**
     * 退出游戏监听器
     */
    public interface OnExitGameListener {
        void onExitGame();
    }
    
    /**
     * 创建控件编辑管理器
     * @param context 上下文
     * @param controlLayout 控件布局
     * @param contentFrame 内容容器（用于显示对话框）
     * @param mode 模式：MODE_STANDALONE 或 MODE_IN_GAME
     */
    public ControlEditorManager(Context context, ControlLayout controlLayout, 
                               ViewGroup contentFrame, int mode) {
        mContext = context;
        mControlLayout = controlLayout;
        mContentFrame = contentFrame;
        mMode = mode;
        
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        
        // 独立模式下自动进入编辑模式
        if (mMode == MODE_STANDALONE) {
            setupEditMode();
        }
    }
    
    /**
     * 设置编辑状态变化监听器
     */
    public void setOnEditorStateChangedListener(OnEditorStateChangedListener listener) {
        mStateListener = listener;
    }
    
    /**
     * 设置布局变化监听器
     */
    public void setOnLayoutChangedListener(OnLayoutChangedListener listener) {
        mLayoutChangedListener = listener;
    }

    /**
     * 设置 FPS 显示变化监听器
     */
    public void setOnFPSDisplayChangedListener(OnFPSDisplayChangedListener listener) {
        mFPSDisplayListener = listener;
    }

    /**
     * 设置隐藏控件监听器
     */
    public void setOnHideControlsListener(OnHideControlsListener listener) {
        mOnHideControlsListener = listener;
    }

    /**
     * 设置退出游戏监听器
     */
    public void setOnExitGameListener(OnExitGameListener listener) {
        mOnExitGameListener = listener;
    }
    
    /**
     * 设置控件布局（用于布局重新创建后更新引用）
     */
    public void setControlLayout(ControlLayout controlLayout) {
        mControlLayout = controlLayout;
        // 独立模式下始终自动进入编辑模式
        if (mMode == MODE_STANDALONE) {
            setupEditMode();
        } else if (mIsInEditor) {
            // 游戏内模式：只有在已进入编辑模式时才重新设置
            setupEditMode();
        }
    }
    
    /**
     * 配置编辑模式（设置监听器等）
     */
    private void setupEditMode() {
        if (mControlLayout == null) return;
        
        // 独立模式下自动设置为编辑状态
        if (mMode == MODE_STANDALONE) {
            mIsInEditor = true;
        }
        
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
        
        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout);
    }
    
    /**
     * 进入编辑模式（仅游戏内模式使用）
     */
    public void enterEditMode() {
        if (mControlLayout == null) return;
        if (mMode == MODE_STANDALONE) return; // 独立模式始终处于编辑状态
        
        mIsInEditor = true;
        mHasUnsavedChanges = false;
        
        setupEditMode();
        
        // 初始化编辑器设置弹窗
        initEditorSettingsDialog();
        
        // 设置编辑模式为启用状态
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(true);
        }
        
        // 确保控制可见
        mControlLayout.setControlsVisible(true);
        
        Toast.makeText(mContext, R.string.editor_mode_on, Toast.LENGTH_SHORT).show();
        
        if (mStateListener != null) {
            mStateListener.onEditorEntered();
        }
    }
    
    /**
     * 退出编辑模式（仅游戏内模式使用）
     */
    public void exitEditMode() {
        if (mMode == MODE_STANDALONE) return; // 独立模式不支持退出
        
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
        
        // 设置编辑模式为禁用状态
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.setEditModeEnabled(false);
        }
        
        // 重新加载布局
        mControlLayout.loadLayoutFromManager();
        
        // 禁用视图裁剪
        disableClippingRecursive(mControlLayout);
        
        Toast.makeText(mContext, R.string.editor_mode_off, Toast.LENGTH_SHORT).show();
        
        if (mStateListener != null) {
            mStateListener.onEditorExited();
        }
    }
    
    /**
     * 切换编辑模式
     */
    public void toggleEditMode() {
        if (mMode == MODE_STANDALONE) return;
        
        if (mIsInEditor) {
            exitEditMode();
        } else {
            enterEditMode();
        }
    }
    
    /**
     * 初始化控件编辑对话框
     */
    private void initControlEditDialog() {
        if (mControlEditDialog != null) return;
        
        // Context应该已经应用了语言设置（从Activity传递过来的）
        mControlEditDialog = new ControlEditDialogMD(mContext, mScreenWidth, mScreenHeight);
        
        // 设置实时更新监听器
        mControlEditDialog.setOnControlUpdatedListener(control -> {
            if (mControlLayout != null) {
                ControlDataSyncManager.syncControlDataToView(mControlLayout, control);
                mHasUnsavedChanges = true;
            }
        });
        
        // 设置删除监听器
        mControlEditDialog.setOnControlDeletedListener(control -> {
            if (mControlLayout != null) {
                ControlConfig config = mControlLayout.getConfig();
                if (config != null && config.controls != null) {
                    // 遍历列表找到相同的引用并删除
                    for (int i = config.controls.size() - 1; i >= 0; i--) {
                        if (config.controls.get(i) == control) {
                            config.controls.remove(i);
                            break;
                        }
                    }
                    mControlLayout.loadLayout(config);
                    disableClippingRecursive(mControlLayout);
                    mHasUnsavedChanges = true;
                    
                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener.onLayoutChanged();
                    }
                }
            }
        });
        
        // 设置复制监听器
        mControlEditDialog.setOnControlCopiedListener(control -> {
            if (mControlLayout != null) {
                ControlConfig config = mControlLayout.getConfig();
                if (config != null && config.controls != null) {
                    config.controls.add(control);
                    mControlLayout.loadLayout(config);
                    disableClippingRecursive(mControlLayout);
                    mHasUnsavedChanges = true;
                    
                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener.onLayoutChanged();
                    }
                }
            }
        });
    }
    
    /**
     * 初始化编辑器设置弹窗
     */
    public void initEditorSettingsDialog() {
        if (mEditorSettingsDialog != null) return;
        
        UnifiedEditorSettingsDialog.DialogMode dialogMode = 
            (mMode == MODE_STANDALONE) ? 
            UnifiedEditorSettingsDialog.DialogMode.EDITOR : 
            UnifiedEditorSettingsDialog.DialogMode.GAME;
        
        mEditorSettingsDialog = new UnifiedEditorSettingsDialog(
            mContext, mContentFrame, mScreenWidth, dialogMode);
        
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
            public void onToggleEditMode() {
                toggleEditMode();
            }

            @Override
            public void onSaveLayout() {
                saveLayout();
                Toast.makeText(mContext, mContext.getString(R.string.editor_layout_saved), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFPSDisplayChanged(boolean enabled) {
                // FPS 显示设置变化
                if (mFPSDisplayListener != null) {
                    mFPSDisplayListener.onFPSDisplayChanged(enabled);
                }
            }

            @Override
            public void onHideControls() {
                // 隐藏控件
                if (mOnHideControlsListener != null) {
                    mOnHideControlsListener.onHideControls();
                }
            }

            @Override
            public void onExitGame() {
                // 退出游戏
                if (mOnExitGameListener != null) {
                    mOnExitGameListener.onExitGame();
                }
            }
        });
        
        // 独立模式下设置编辑模式为启用状态
        if (mMode == MODE_STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true);
        }
    }
    
    /**
     * 显示设置对话框
     */
    public void showSettingsDialog() {
        if (mEditorSettingsDialog == null) {
            initEditorSettingsDialog();
        }
        // 独立模式下始终确保编辑模式为启用状态
        if (mMode == MODE_STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true);
        } else {
            mEditorSettingsDialog.setEditModeEnabled(mIsInEditor);
        }
        mEditorSettingsDialog.show();
        // 对话框显示后再次确保状态正确（因为布局可能被重新创建）
        if (mMode == MODE_STANDALONE) {
            mEditorSettingsDialog.setEditModeEnabled(true);
        }
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
        
        ControlData button = ControlEditorOperations.addButton(mContext, config, mScreenWidth, mScreenHeight);
        
        if (button != null) {
            mControlLayout.loadLayout(config);
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            Toast.makeText(mContext, mContext.getString(R.string.editor_button_added), Toast.LENGTH_SHORT).show();
            
            if (mLayoutChangedListener != null) {
                mLayoutChangedListener.onLayoutChanged();
            }
        }
    }
    
    /**
     * 添加摇杆
     */
    public void addJoystick() {
        if (mControlLayout == null) return;
        
        final ControlConfig config = mControlLayout.getConfig();
        final ControlConfig finalConfig;
        if (config == null) {
            finalConfig = new ControlConfig();
            finalConfig.controls = new java.util.ArrayList<>();
            mControlLayout.loadLayout(finalConfig);
        } else {
            finalConfig = config;
        }
        
        // 第一步：选择摇杆类型
        String[] joystickTypeOptions = new String[]{
            mContext.getString(R.string.editor_joystick_type_move_aim),
            mContext.getString(R.string.editor_joystick_type_gamepad)
        };
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_type))
            .setItems(joystickTypeOptions, (dialog, which) -> {
                if (which == 0) {
                    // 移动+瞄准摇杆：直接创建键盘左摇杆和鼠标右摇杆
                    ControlData leftJoystick = ControlEditorOperations.addJoystick(
                        finalConfig, mScreenWidth, mScreenHeight, 
                        ControlData.JOYSTICK_MODE_KEYBOARD, false);
                    ControlData rightJoystick = ControlEditorOperations.addJoystick(
                        finalConfig, mScreenWidth, mScreenHeight, 
                        ControlData.JOYSTICK_MODE_MOUSE, true);
                    
                    if (leftJoystick != null && rightJoystick != null) {
                        mControlLayout.loadLayout(finalConfig);
                        disableClippingRecursive(mControlLayout);
                        mHasUnsavedChanges = true;
                        Toast.makeText(mContext, mContext.getString(R.string.editor_joystick_added), Toast.LENGTH_SHORT).show();
                        
                        if (mLayoutChangedListener != null) {
                            mLayoutChangedListener.onLayoutChanged();
                        }
                    }
                } else {
                    // 手柄摇杆模式：选择左摇杆还是右摇杆
                    final int joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                    showStickSideDialog(finalConfig, joystickMode);
                }
            })
            .show();
    }
    
    /**
     * 显示选择摇杆位置的对话框
     */
    private void showStickSideDialog(final ControlConfig finalConfig, final int joystickMode) {
        String[] stickSideOptions = new String[]{
            mContext.getString(R.string.editor_joystick_side_left),
            mContext.getString(R.string.editor_joystick_side_right)
        };
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(mContext)
            .setTitle(mContext.getString(R.string.editor_select_joystick_side))
            .setItems(stickSideOptions, (dialog2, which2) -> {
                boolean isRightStick = (which2 == 1);
                
                // 创建摇杆
                ControlData joystick = ControlEditorOperations.addJoystick(
                    finalConfig, mScreenWidth, mScreenHeight, joystickMode, isRightStick);
                
                if (joystick != null) {
                    mControlLayout.loadLayout(finalConfig);
                    disableClippingRecursive(mControlLayout);
                    mHasUnsavedChanges = true;
                    Toast.makeText(mContext, mContext.getString(R.string.editor_joystick_added), Toast.LENGTH_SHORT).show();
                    
                    if (mLayoutChangedListener != null) {
                        mLayoutChangedListener.onLayoutChanged();
                    }
                }
            })
            .show();
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
        
        ControlData text = ControlEditorOperations.addText(mContext, config, mScreenWidth, mScreenHeight);
        
        if (text != null) {
            mControlLayout.loadLayout(config);
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            Toast.makeText(mContext, mContext.getString(R.string.editor_text_added), Toast.LENGTH_SHORT).show();
            
            if (mLayoutChangedListener != null) {
                mLayoutChangedListener.onLayoutChanged();
            }
        }
    }
    
    /**
     * 保存布局
     */
    public void saveLayout() {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        com.app.ralaunch.utils.ControlLayoutManager manager = 
            new com.app.ralaunch.utils.ControlLayoutManager(mContext);
        String layoutName = manager.getCurrentLayoutName();
        
        if (ControlEditorOperations.saveLayout(mContext, config, layoutName)) {
            mHasUnsavedChanges = false;
        }
    }
    
    /**
     * 使用指定名称保存布局
     */
    public void saveLayout(String layoutName) {
        if (mControlLayout == null) return;
        
        ControlConfig config = mControlLayout.getConfig();
        if (ControlEditorOperations.saveLayout(mContext, config, layoutName)) {
            mHasUnsavedChanges = false;
        }
    }
    
    /**
     * 重置为默认布局
     */
    public void resetToDefaultLayout() {
        ControlEditorOperations.resetToDefaultLayout(mContext, mControlLayout, () -> {
            disableClippingRecursive(mControlLayout);
            mHasUnsavedChanges = true;
            
            if (mLayoutChangedListener != null) {
                mLayoutChangedListener.onLayoutChanged();
            }
        });
    }
    
    /**
     * 隐藏编辑器设置对话框
     */
    public void hideSettingsDialog() {
        if (mEditorSettingsDialog != null) {
            mEditorSettingsDialog.hide();
        }
    }
    
    /**
     * 设置对话框是否正在显示
     */
    public boolean isSettingsDialogShowing() {
        return mEditorSettingsDialog != null && mEditorSettingsDialog.isDisplaying();
    }
    
    /**
     * 编辑对话框是否正在显示
     */
    public boolean isEditDialogShowing() {
        return mControlEditDialog != null && mControlEditDialog.isShowing();
    }
    
    /**
     * 关闭编辑对话框
     */
    public void dismissEditDialog() {
        if (mControlEditDialog != null) {
            mControlEditDialog.dismiss();
        }
    }
    
    /**
     * 是否处于编辑模式
     */
    public boolean isInEditor() {
        return mMode == MODE_STANDALONE || mIsInEditor;
    }
    
    /**
     * 是否有未保存的修改
     */
    public boolean hasUnsavedChanges() {
        return mHasUnsavedChanges;
    }
    
    /**
     * 获取当前模式
     */
    public int getMode() {
        return mMode;
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

