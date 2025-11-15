package com.app.ralaunch.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.app.ralaunch.utils.AppLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟控制布局管理器
 * 负责管理所有虚拟控制元素的布局和显示
 */
public class ControlLayout extends FrameLayout {
    private static final String TAG = "ControlLayout";
    
    private List<ControlView> mControls;
    private ControlInputBridge mInputBridge;
    private ControlConfig mConfig;
    private boolean mVisible = true;
    private boolean mModifiable = false; // 是否可编辑模式
    private ControlView mSelectedControl; // 当前选中的控件
    private float mLastTouchX, mLastTouchY; // 上次触摸位置
    private EditControlListener mEditControlListener; // 编辑监听器
    private OnControlChangedListener mOnControlChangedListener; // 控件修改监听器
    
    public ControlLayout(Context context) {
        super(context);
        init();
    }
    
    public ControlLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        mControls = new ArrayList<>();
        setWillNotDraw(false);
        
        // 禁用子View裁剪，让控件的绘制效果（如摇杆方向线）完整显示
        setClipChildren(false);
        setClipToPadding(false);
    }
    
    /**
     * 设置输入桥接
     */
    public void setInputBridge(ControlInputBridge bridge) {
        mInputBridge = bridge;
    }
    
    /**
     * 加载控制布局配置
     */
    public void loadLayout(ControlConfig config) {
        if (mInputBridge == null) {
            AppLogger.error(TAG, "InputBridge not set! Call setInputBridge() first.");
            return;
        }
        
        mConfig = config;
        clearControls();
        
        if (config == null || config.controls == null) {
            AppLogger.warn(TAG, "Empty config, loading default layout");
            loadDefaultLayout();
            return;
        }
        
        // 创建虚拟控制元素
        for (ControlData data : config.controls) {
            if (!data.visible) continue;
            
            ControlView controlView = createControlView(data);
            if (controlView != null) {
                addControlView(controlView, data);
            }
        }
        
    }
    
    /**
     * 加载自定义布局（如果存在），否则加载默认布局
     */
    public void loadCustomOrDefaultLayout() {
        try {
            // 尝试加载自定义布局
            java.io.File customFile = new java.io.File(getContext().getFilesDir(), "custom_layout.json");
            if (customFile.exists()) {
                String json = new String(java.nio.file.Files.readAllBytes(customFile.toPath()));
                ControlConfig config = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
                loadLayout(config);
                return;
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to load custom layout, falling back to default", e);
        }
        
        // 如果自定义布局不存在或加载失败，使用默认布局
        loadDefaultLayout();
    }
    
    /**
     * 加载默认控制布局
     */
    public void loadDefaultLayout() {
        
        ControlConfig config = new ControlConfig();
        config.name = "Terraria默认布局";
        config.version = 1;
        config.controls = new ArrayList<>();
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float screenWidth = metrics.widthPixels;
        float screenHeight = metrics.heightPixels;
        
        // 1. 移动摇杆（左下角，450x450大尺寸）
        ControlData joystick = ControlData.createDefaultJoystick();
        // 使用自适应位置，确保在不同屏幕上都能正常显示
        joystick.y = screenHeight - 550;  // 450 + 100边距
        config.controls.add(joystick);
        
        // 2. 跳跃按钮（右下角）
        ControlData jump = ControlData.createDefaultJumpButton();
        jump.x = screenWidth - 250;
        jump.y = screenHeight - 200;
        config.controls.add(jump);
        
        // 3. 瞄准摇杆（右下角，鼠标移动模式，用于攻击方向控制）
        ControlData attackJoystick = ControlData.createDefaultAttackJoystick();
        attackJoystick.x = screenWidth - 500;  // 右侧，避开其他按钮
        attackJoystick.y = screenHeight - 550;  // 450 + 100边距，与左摇杆对称
        config.controls.add(attackJoystick);
        
        // 4. 攻击按钮（鼠标左键，右上方）
        ControlData attack = ControlData.createDefaultAttackButton();
        attack.x = screenWidth - 150;
        attack.y = screenHeight - 900;
        config.controls.add(attack);
        
        // 6. 使用物品按钮（鼠标右键）
        ControlData use = new ControlData("使用", ControlData.TYPE_BUTTON);
        use.x = screenWidth - 400;
        use.y = screenHeight - 200;
        use.width = 100;
        use.height = 100;
        use.keycode = ControlData.MOUSE_RIGHT;
        config.controls.add(use);
        
        // 7. 钩爪按钮（E键）
        ControlData hook = new ControlData("钩爪", ControlData.TYPE_BUTTON);
        hook.x = screenWidth - 280;
        hook.y = screenHeight - 900;
        hook.width = 100;
        hook.height = 100;
        hook.keycode = ControlData.SDL_SCANCODE_E;
        config.controls.add(hook);
        
        // 8. 药水按钮（H键）
        ControlData potion = new ControlData("药水", ControlData.TYPE_BUTTON);
        potion.x = screenWidth - 400;
        potion.y = screenHeight - 400;
        potion.width = 100;
        potion.height = 100;
        potion.keycode = ControlData.SDL_SCANCODE_H;
        config.controls.add(potion);
        
        // 9. ESC菜单按钮（左上角）
        ControlData menu = new ControlData("菜单", ControlData.TYPE_BUTTON);
        menu.x = 50;
        menu.y = 50;
        menu.width = 100;
        menu.height = 60;
        menu.keycode = ControlData.SDL_SCANCODE_ESCAPE;
        config.controls.add(menu);
        
        loadLayout(config);
    }
    
    /**
     * 创建控制View
     */
    private ControlView createControlView(ControlData data) {
        switch (data.type) {
            case ControlData.TYPE_JOYSTICK:
                return new VirtualJoystick(getContext(), data, mInputBridge);
            case ControlData.TYPE_BUTTON:
                return new VirtualButton(getContext(), data, mInputBridge);
            default:
                AppLogger.warn(TAG, "Unknown control type: " + data.type);
                return null;
        }
    }
    
    /**
     * 添加控制View到布局
     */
    private void addControlView(ControlView controlView, ControlData data) {
        View view = (View) controlView;
        
        // 设置布局参数（x, y, width, height都是像素值，不需要转换）
        LayoutParams params = new LayoutParams(
            (int) data.width,
            (int) data.height
        );
        params.leftMargin = (int) data.x;
        params.topMargin = (int) data.y;
        
        view.setLayoutParams(params);
        
        // 在编辑模式下添加触摸监听器
        setupEditModeListeners(view, controlView, data);
        
        addView(view);
        mControls.add(controlView);
    }
    
    /**
     * 设置编辑模式的触摸监听器
     */
    private void setupEditModeListeners(View view, ControlView controlView, ControlData data) {
        view.setOnTouchListener((v, event) -> {
            if (!mModifiable) {
                // 非编辑模式，让控件自己处理触摸事件
                return false;
            }
            
            // 编辑模式下处理触摸事件
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSelectedControl = controlView;
                    mLastTouchX = event.getRawX();
                    mLastTouchY = event.getRawY();
                    
                    // 高亮选中的控件
                    v.setAlpha(0.5f);
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (mSelectedControl == controlView) {
                        // 拖动控件
                        float deltaX = event.getRawX() - mLastTouchX;
                        float deltaY = event.getRawY() - mLastTouchY;
                        
                        LayoutParams params = (LayoutParams) v.getLayoutParams();
                        params.leftMargin += (int) deltaX;
                        params.topMargin += (int) deltaY;
                        v.setLayoutParams(params);
                        
                        // 更新数据
                        data.x = params.leftMargin;
                        data.y = params.topMargin;
                        
                        mLastTouchX = event.getRawX();
                        mLastTouchY = event.getRawY();
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 恢复透明度
                    v.setAlpha(1.0f);
                    
                    // 判断是点击还是拖动
                    float totalDelta = Math.abs(event.getRawX() - mLastTouchX) + 
                                     Math.abs(event.getRawY() - mLastTouchY);
                    
                    if (totalDelta < 10) {
                        // 点击事件 - 通知监听器显示编辑对话框
                        if (mEditControlListener != null) {
                            mEditControlListener.onEditControl(data);
                        }
                    } else {
                        // 拖动事件 - 通知控件已修改
                        if (mOnControlChangedListener != null) {
                            mOnControlChangedListener.onControlChanged();
                        }
                    }
                    
                    mSelectedControl = null;
                    return true;
            }
            
            return false;
        });
    }
    
    /**
     * 清除所有控制元素
     */
    public void clearControls() {
        removeAllViews();
        mControls.clear();
    }
    
    /**
     * 显示/隐藏控制布局
     */
    public void setControlsVisible(boolean visible) {
        mVisible = visible;
        setVisibility(visible ? VISIBLE : GONE);
    }
    
    public boolean isControlsVisible() {
        return mVisible;
    }
    
    /**
     * 切换控制布局显示状态
     */
    public void toggleControlsVisible() {
        setControlsVisible(!mVisible);
    }
    
    /**
     * 获取当前配置
     */
    public ControlConfig getConfig() {
        return mConfig;
    }
    
    /**
     * 重置所有切换按钮状态
     */
    public void resetAllToggles() {
        for (ControlView control : mControls) {
            if (control instanceof VirtualButton) {
                ((VirtualButton) control).resetToggle();
            }
        }
    }
    
    /**
     * 设置编辑模式
     */
    public void setModifiable(boolean modifiable) {
        mModifiable = modifiable;
        
        // 重新设置所有控件的触摸监听器
        if (mConfig != null && mConfig.controls != null) {
            for (int i = 0; i < mControls.size(); i++) {
                ControlView controlView = mControls.get(i);
                ControlData data = mConfig.controls.get(i);
                View view = (View) controlView;
                setupEditModeListeners(view, controlView, data);
            }
        }
    }
    
    /**
     * 是否处于编辑模式
     */
    public boolean isModifiable() {
        return mModifiable;
    }
    
    /**
     * 设置编辑控件监听器
     */
    public void setEditControlListener(EditControlListener listener) {
        mEditControlListener = listener;
    }
    
    /**
     * 设置控件修改监听器
     */
    public void setOnControlChangedListener(OnControlChangedListener listener) {
        mOnControlChangedListener = listener;
    }
    
    /**
     * 编辑控件监听器接口
     */
    public interface EditControlListener {
        void onEditControl(ControlData data);
    }
    
    /**
     * 控件修改监听器接口
     */
    public interface OnControlChangedListener {
        void onControlChanged();
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
