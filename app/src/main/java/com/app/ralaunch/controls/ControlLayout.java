package com.app.ralaunch.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.controls.ControlDataConverter;

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
    
    // 多选功能相关（已移除长按触发，保留多选功能代码但不使用）
    private boolean mIsMultiSelectMode = false; // 是否处于多选模式
    private List<ControlView> mSelectedControls = new ArrayList<>(); // 选中的控件列表
    private float mSelectionStartX, mSelectionStartY; // 选择框起始位置
    private float mSelectionEndX, mSelectionEndY; // 选择框结束位置
    
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
        mSelectedControls = new ArrayList<>();
        setWillNotDraw(false);
        
        // 禁用子View裁剪，让控件的绘制效果（如摇杆方向线）完整显示
        setClipChildren(false);
        setClipToPadding(false);
        
        // 启用硬件加速层，以支持 RippleDrawable 等需要硬件加速的动画
        // 在硬件加速的 Activity 上，硬件加速层可以进一步提升性能
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }
    
    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        
        // 如果处于多选模式，绘制选择框
        if (mIsMultiSelectMode && mModifiable) {
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(0x6600AAFF); // 半透明蓝色
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setAlpha(50);
            
            android.graphics.Paint strokePaint = new android.graphics.Paint();
            strokePaint.setColor(0xFF00AAFF); // 蓝色边框
            strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3);
            
            float left = Math.min(mSelectionStartX, mSelectionEndX);
            float top = Math.min(mSelectionStartY, mSelectionEndY);
            float right = Math.max(mSelectionStartX, mSelectionEndX);
            float bottom = Math.max(mSelectionStartY, mSelectionEndY);
            
            android.graphics.RectF rect = new android.graphics.RectF(left, top, right, bottom);
            canvas.drawRect(rect, paint);
            canvas.drawRect(rect, strokePaint);
        }
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
                AppLogger.info(TAG, "Loaded layout from ControlLayoutManager: " + layout.getName());
                return;
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to load layout from ControlLayoutManager, falling back to default", e);
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
                AppLogger.info(TAG, "Loaded default layout from ControlLayoutManager: " + layout.getName());
                return;
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to load default layout from ControlLayoutManager, using fallback", e);
        }
        
        // 如果加载失败，尝试加载键盘模式默认布局（从 JSON）
        try {
            ControlLayoutManager manager = new ControlLayoutManager(getContext());
            com.app.ralaunch.model.ControlLayout keyboardLayout = manager.getLayout("键盘模式");
            
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
                AppLogger.info(TAG, "Loaded keyboard layout as fallback from ControlLayoutManager");
                return;
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to load keyboard layout as fallback", e);
        }
        
        // 如果所有加载都失败，创建一个空的布局
        ControlConfig config = new ControlConfig();
        config.name = "默认布局";
        config.version = 1;
        config.controls = new ArrayList<>();
        loadLayout(config);
        AppLogger.warn(TAG, "All layout loading failed, using empty layout");
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
            
            // 如果处于多选模式，不处理单个控件的触摸事件
            if (mIsMultiSelectMode) {
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
                    if (mSelectedControl == controlView && !mIsMultiSelectMode) {
                        // 拖动单个控件
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
                    
                    if (!mIsMultiSelectMode) {
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
                    }
                    
                    mSelectedControl = null;
                    return true;
            }
            
            return false;
        });
    }
    
    /**
     * 进入多选模式
     */
    private void enterMultiSelectMode(float startX, float startY) {
        mIsMultiSelectMode = true;
        mSelectedControls.clear();
        mSelectionStartX = startX;
        mSelectionStartY = startY;
        mSelectionEndX = startX;
        mSelectionEndY = startY;
        
        // 设置整个布局的触摸监听器来处理框选
        setOnTouchListener((v, event) -> {
            if (!mModifiable || !mIsMultiSelectMode) {
                return false;
            }
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSelectionStartX = event.getX();
                    mSelectionStartY = event.getY();
                    mSelectionEndX = event.getX();
                    mSelectionEndY = event.getY();
                    invalidate();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    mSelectionEndX = event.getX();
                    mSelectionEndY = event.getY();
                    updateSelectedControls();
                    invalidate();
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 完成选择
                    if (mSelectedControls.size() > 0) {
                        // 有选中的控件，准备拖动
                        mLastTouchX = event.getRawX();
                        mLastTouchY = event.getRawY();
                        // 切换到拖动模式
                        setOnTouchListener((view, dragEvent) -> handleMultiSelectDrag(dragEvent));
                    } else {
                        // 没有选中任何控件，退出多选模式
                        exitMultiSelectMode();
                    }
                    return true;
            }
            
            return false;
        });
    }
    
    /**
     * 更新选中的控件列表
     */
    private void updateSelectedControls() {
        // 先恢复所有控件的透明度
        for (ControlView controlView : mControls) {
            View view = (View) controlView;
            view.setAlpha(1.0f);
        }
        
        mSelectedControls.clear();
        
        if (mConfig == null || mConfig.controls == null) {
            return;
        }
        
        float left = Math.min(mSelectionStartX, mSelectionEndX);
        float top = Math.min(mSelectionStartY, mSelectionEndY);
        float right = Math.max(mSelectionStartX, mSelectionEndX);
        float bottom = Math.max(mSelectionStartY, mSelectionEndY);
        
        android.graphics.RectF selectionRect = new android.graphics.RectF(left, top, right, bottom);
        
        // 检查所有控件是否在选择框内
        for (int i = 0; i < mControls.size() && i < mConfig.controls.size(); i++) {
            ControlView controlView = mControls.get(i);
            View view = (View) controlView;
            ControlData data = mConfig.controls.get(i);
            
            if (!data.visible) continue;
            
            // 获取控件在父容器中的位置
            LayoutParams params = (LayoutParams) view.getLayoutParams();
            float controlLeft = params.leftMargin;
            float controlTop = params.topMargin;
            float controlRight = controlLeft + view.getWidth();
            float controlBottom = controlTop + view.getHeight();
            
            android.graphics.RectF controlRect = new android.graphics.RectF(
                controlLeft, controlTop, controlRight, controlBottom);
            
            // 如果控件在选择框内（包含或相交），添加到选中列表
            if (selectionRect.contains(controlRect) || 
                selectionRect.intersect(controlRect)) {
                mSelectedControls.add(controlView);
                view.setAlpha(0.7f); // 高亮选中的控件
            }
        }
    }
    
    /**
     * 处理多选拖动
     */
    private boolean handleMultiSelectDrag(MotionEvent event) {
        if (!mModifiable || !mIsMultiSelectMode || mSelectedControls.isEmpty()) {
            return false;
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = event.getRawX();
                mLastTouchY = event.getRawY();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                // 计算移动距离
                float deltaX = event.getRawX() - mLastTouchX;
                float deltaY = event.getRawY() - mLastTouchY;
                
                // 移动所有选中的控件
                for (ControlView controlView : mSelectedControls) {
                    View view = (View) controlView;
                    LayoutParams params = (LayoutParams) view.getLayoutParams();
                    params.leftMargin += (int) deltaX;
                    params.topMargin += (int) deltaY;
                    view.setLayoutParams(params);
                    
                    // 更新对应的数据
                    for (int i = 0; i < mControls.size(); i++) {
                        if (mControls.get(i) == controlView && mConfig != null && 
                            mConfig.controls != null && i < mConfig.controls.size()) {
                            ControlData data = mConfig.controls.get(i);
                            data.x = params.leftMargin;
                            data.y = params.topMargin;
                            break;
                        }
                    }
                }
                
                mLastTouchX = event.getRawX();
                mLastTouchY = event.getRawY();
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 拖动结束，退出多选模式
                exitMultiSelectMode();
                
                // 通知控件已修改
                if (mOnControlChangedListener != null) {
                    mOnControlChangedListener.onControlChanged();
                }
                return true;
        }
        
        return false;
    }
    
    /**
     * 退出多选模式
     */
    private void exitMultiSelectMode() {
        mIsMultiSelectMode = false;
        mSelectedControls.clear();
        setOnTouchListener(null);
        
        // 恢复所有控件的透明度
        for (ControlView controlView : mControls) {
            View view = (View) controlView;
            view.setAlpha(1.0f);
        }
        
        invalidate();
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
        
        if (!modifiable) {
            // 退出编辑模式时，退出多选模式
            exitMultiSelectMode();
        }
        
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
