package com.app.ralaunch.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.controls.ControlDataConverter;
import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟控制布局管理器
 * 负责管理所有虚拟控制元素的布局和显示
 * 
 * 重要：所有触摸事件都会转发给 SDLSurface，确保游戏能收到完整的多点触控输入
 */
public class ControlLayout extends FrameLayout {
    private static final String TAG = "ControlLayout";
    
    // SDLSurface 引用，用于转发触摸事件
    private View mSDLSurface;
    
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
        
        // 启用硬件加速层，以支持 RippleDrawable 等需要硬件加速的动画
        // 在硬件加速的 Activity 上，硬件加速层可以进一步提升性能
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }
    
    /**
     * 设置 SDLSurface 引用，用于转发触摸事件
     */
    public void setSDLSurface(View sdlSurface) {
        mSDLSurface = sdlSurface;
    }
    
    /**
     * 重写 dispatchTouchEvent 确保 SDLSurface 能收到所有触摸事件
     * 这解决了虚拟控件消费事件后 SDLSurface 收不到多点触控的问题
     * 
     * 事件处理顺序：
     * 1. 先让虚拟控件处理（会调用 TouchPointerTracker.consumePointer 标记被占用的触摸点）
     * 2. 再转发给 SDLSurface（SDL 层会根据 consumed fingers 列表决定是否生成鼠标事件）
     * 
     * 穿透模式（passThrough=true）：虚拟控件不标记触摸点 → SDL 正常生成鼠标事件
     * 非穿透模式（passThrough=false）：虚拟控件标记触摸点 → SDL 跳过鼠标事件
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 先让虚拟控件处理事件
        // 这样 TouchPointerTracker 会先标记被占用的触摸点
        boolean handled = super.dispatchTouchEvent(event);
        
        // 再转发给 SDLSurface（如果存在）
        // SDLSurface 会处理触屏转鼠标的逻辑，SDL 层会根据 consumed fingers 列表过滤
        if (mSDLSurface != null) {
            mSDLSurface.dispatchTouchEvent(event);
        }
        
        // 重要：始终返回 true 表示事件已被消费
        // 这样 Android 系统就不会再次把事件传递给 SDLSurface
        // 避免重复处理导致的问题（如鼠标点击需要多次才能触发）
        return true;
    }
    
    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
    }
    
    /**
     * 设置输入桥接
     */
    public void setInputBridge(ControlInputBridge bridge) {
        mInputBridge = bridge;
    }
    
    /**
     * 加载控制布局配置
     * 
     * 会根据设置自动配置：
     * - MOUSE_RIGHT_STICK_ENABLED: 自动将右摇杆切换为鼠标移动模式
     */
    public void loadLayout(ControlConfig config) {
        if (mInputBridge == null) {
            AppLogger.error(TAG, "InputBridge not set! Call setInputBridge() first.");
            return;
        }
        
        mConfig = config;
        clearControls();
        
        if (config == null || config.controls == null) {
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
     * 从 ControlLayoutManager 加载布局（统一方式）
     * 优先使用当前布局，如果不存在则使用默认布局
     */
    public void loadLayoutFromManager() {
        try {
            ControlLayoutManager manager = new ControlLayoutManager(getContext());
            com.app.ralaunch.model.ControlLayout layout = manager.getCurrentLayout();
            
            if (layout != null && !layout.getElements().isEmpty()) {
                // 转换 ControlElement 列表为 ControlConfig
                ControlConfig config = new ControlConfig();
                config.name = layout.getName();
                config.version = 1;
                config.controls = new ArrayList<>();
                
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int screenWidth = metrics.widthPixels;
                int screenHeight = metrics.heightPixels;
                
                for (ControlElement element : layout.getElements()) {
                    ControlData data = ControlDataConverter.elementToData(element, screenWidth, screenHeight);
                    if (data != null) {
                        config.controls.add(data);
                    }
                }
                
                loadLayout(config);
                return;
            }
        } catch (Exception e) {
        }
        
        // 如果加载失败，使用默认布局
        loadDefaultLayout();
    }
    
    /**
     * 加载自定义布局（如果存在），否则加载默认布局
     * @deprecated 使用 loadLayoutFromManager() 替代，统一使用 ControlLayoutManager
     */
    @Deprecated
    public void loadCustomOrDefaultLayout() {
        // 优先尝试从 ControlLayoutManager 加载
        loadLayoutFromManager();
    }
    
    /**
     * 加载默认控制布局
     * 优先从 ControlLayoutManager 加载当前布局，如果失败则加载键盘模式默认布局
     */
    public void loadDefaultLayout() {
        // 优先尝试从 ControlLayoutManager 加载当前布局
        try {
            ControlLayoutManager manager = new ControlLayoutManager(getContext());
            com.app.ralaunch.model.ControlLayout layout = manager.getCurrentLayout();
            
            if (layout != null && !layout.getElements().isEmpty()) {
                // 转换 ControlElement 列表为 ControlConfig
                ControlConfig config = new ControlConfig();
                config.name = layout.getName();
                config.version = 1;
                config.controls = new ArrayList<>();
                
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int screenWidth = metrics.widthPixels;
                int screenHeight = metrics.heightPixels;
                
                for (ControlElement element : layout.getElements()) {
                    ControlData data = ControlDataConverter.elementToData(element, screenWidth, screenHeight);
                    if (data != null) {
                        config.controls.add(data);
                    }
                }
                
                loadLayout(config);
                return;
            }
        } catch (Exception e) {
        }
        
        // 如果加载失败，尝试加载键盘模式默认布局（从 JSON）
        try {
            ControlLayoutManager manager = new ControlLayoutManager(getContext());
            // 使用固定的内部标识符
            com.app.ralaunch.model.ControlLayout keyboardLayout = manager.getLayout("keyboard_mode");
            
            if (keyboardLayout != null && !keyboardLayout.getElements().isEmpty()) {
                ControlConfig config = new ControlConfig();
                config.name = keyboardLayout.getName();
                config.version = 1;
                config.controls = new ArrayList<>();
                
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int screenWidth = metrics.widthPixels;
                int screenHeight = metrics.heightPixels;
                
                for (ControlElement element : keyboardLayout.getElements()) {
                    ControlData data = ControlDataConverter.elementToData(element, screenWidth, screenHeight);
                    if (data != null) {
                        config.controls.add(data);
                    }
                }
                
                loadLayout(config);
                return;
            }
        } catch (Exception e) {
        }
        
        ControlConfig config = new ControlConfig();
        config.name = "默认布局";
        config.version = 1;
        config.controls = new ArrayList<>();
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
            case ControlData.TYPE_TEXT:
                return new VirtualText(getContext(), data, mInputBridge);
            default:
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
        // 记录按下时的初始位置（用于判断是点击还是拖动）
        final float[] downPos = new float[2];
        final boolean[] isDragging = new boolean[1];
        final float DRAG_THRESHOLD = 15f;
        
        view.setOnTouchListener((v, event) -> {
            if (!mModifiable) {
                // 非编辑模式，让控件自己处理触摸事件
                return false;
            }
            
            // 编辑模式下处理触摸事件
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSelectedControl = controlView;
                    // 记录按下时的初始位置
                    downPos[0] = event.getRawX();
                    downPos[1] = event.getRawY();
                    mLastTouchX = event.getRawX();
                    mLastTouchY = event.getRawY();
                    isDragging[0] = false;
                    
                    // 高亮选中的控件
                    v.setAlpha(0.5f);
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (mSelectedControl == controlView) {
                        float deltaX = event.getRawX() - mLastTouchX;
                        float deltaY = event.getRawY() - mLastTouchY;
                        
                        // 计算从按下位置到当前位置的总移动距离
                        float totalDx = event.getRawX() - downPos[0];
                        float totalDy = event.getRawY() - downPos[1];
                        float totalDistance = (float) Math.sqrt(totalDx * totalDx + totalDy * totalDy);
                        
                        // 如果总移动距离超过阈值，标记为拖动
                        if (totalDistance > DRAG_THRESHOLD) {
                            isDragging[0] = true;
                        }
                        
                        if (isDragging[0]) {
                            // 拖动单个控件
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
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 恢复透明度
                    v.setAlpha(1.0f);
                    
                    // 清除控件的按下状态
                    if (controlView instanceof VirtualButton) {
                        ((VirtualButton) controlView).setPressedState(false);
                    }
                    
                    // 计算从按下到释放的总移动距离
                    float finalDx = event.getRawX() - downPos[0];
                    float finalDy = event.getRawY() - downPos[1];
                    float finalDistance = (float) Math.sqrt(finalDx * finalDx + finalDy * finalDy);
                    
                    // 只有在没有拖动（移动距离小于阈值）时才打开编辑对话框
                    if (finalDistance <= DRAG_THRESHOLD && !isDragging[0]) {
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
