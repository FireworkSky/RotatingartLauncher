package com.app.ralaunch.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.app.ralaunch.RaLaunchApplication;

/**
 * 虚拟摇杆View
 * 支持8方向移动，触摸拖拽控制
 */
public class VirtualJoystick extends View implements ControlView {
    private static final String TAG = "VirtualJoystick";
    
    // 8个方向常量（对应游戏中的实际方向）
    public static final int DIR_NONE = -1;
    public static final int DIR_UP = 0;        // 上 (W)
    public static final int DIR_UP_RIGHT = 1;  // 右上 (W+D)
    public static final int DIR_RIGHT = 2;     // 右 (D)
    public static final int DIR_DOWN_RIGHT = 3;// 右下 (S+D)
    public static final int DIR_DOWN = 4;      // 下 (S)
    public static final int DIR_DOWN_LEFT = 5; // 左下 (S+A)
    public static final int DIR_LEFT = 6;      // 左 (A)
    public static final int DIR_UP_LEFT = 7;   // 左上 (W+A)
    
    private ControlData mData;
    private ControlInputBridge mInputBridge;
    
    // 绘制相关
    private Paint mBackgroundPaint;
    private Paint mStickPaint;
    private Paint mStrokePaint;
    
    // 摇杆状态
    private float mCenterX;
    private float mCenterY;
    private float mStickX;
    private float mStickY;
    private float mRadius;
    private float mStickRadius;
    private int mCurrentDirection = DIR_NONE;
    private boolean mIsTouching = false;
    private int mActivePointerId = -1; // 跟踪的触摸点 ID
    
    // 屏幕尺寸（用于右摇杆绝对位置计算）
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    
    // 右摇杆攻击状态
    private boolean mIsAttacking = false;
    private android.os.Handler mClickAttackHandler;
    private Runnable mClickAttackRunnable;
    private static final int CLICK_ATTACK_INTERVAL_MS = 150; // 点击攻击间隔（毫秒）
    
    // 鼠标模式右摇杆攻击状态
    private boolean mMouseLeftPressed = false; // 鼠标左键是否按下
    private Runnable mMouseClickRunnable; // 点击模式 Runnable
    private static final int MOUSE_CLICK_INTERVAL_MS = 100; // 鼠标点击间隔（毫秒）
    private int mAttackMode = 0; // 攻击模式：0=长按, 1=点击, 2=持续
    
    // 鼠标移动状态
    private Runnable mMouseMoveRunnable; // 鼠标移动 Runnable
    private static final int MOUSE_MOVE_INTERVAL_MS = 16; // 鼠标移动更新间隔（约60fps）
    private float mCurrentMouseDx = 0; // 当前摇杆 X 方向偏移
    private float mCurrentMouseDy = 0; // 当前摇杆 Y 方向偏移
    private boolean mMouseMoveActive = false; // 鼠标移动是否激活
    
    // 右摇杆上一帧位置（用于计算位置变化量）
    private float mLastJoystickDx = 0;
    private float mLastJoystickDy = 0;
    
    // 右摇杆移动阈值（像素，只有当摇杆位置变化超过此值时才移动鼠标）
    private static final float JOYSTICK_MOVE_THRESHOLD = 20.0f;

    // 死区（防止漂移）
    private static final float DEADZONE_PERCENT = 0.15f;
    
    // 运行时从全局设置读取的鼠标速度和范围
    private float mGlobalMouseSpeed = 80.0f;
    private float mGlobalMouseRangeLeft = 0.0f;
    private float mGlobalMouseRangeTop = 0.0f;
    private float mGlobalMouseRangeRight = 1.0f;
    private float mGlobalMouseRangeBottom = 1.0f;
    
    // 8方向角度映射表（从角度计算结果映射到实际方向）
    // 角度计算：0度=正右, 90度=正上, 180度=正左, 270度=正下
    private static final int[] ANGLE_TO_DIR = {
        DIR_RIGHT,       // 0: 正右 (0度)
        DIR_UP_RIGHT,    // 1: 右上 (45度)
        DIR_UP,          // 2: 正上 (90度)
        DIR_UP_LEFT,     // 3: 左上 (135度)
        DIR_LEFT,        // 4: 正左 (180度)
        DIR_DOWN_LEFT,   // 5: 左下 (225度)
        DIR_DOWN,        // 6: 正下 (270度)
        DIR_DOWN_RIGHT   // 7: 右下 (315度)
    };
    
    public VirtualJoystick(Context context, ControlData data, ControlInputBridge bridge) {
        super(context);
        mData = data;
        mInputBridge = bridge;

        // 获取屏幕尺寸（用于右摇杆绝对位置计算）
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        
        // 读取全局设置（攻击模式、鼠标速度、鼠标范围）
        try {
            com.app.ralaunch.data.SettingsManager settingsManager = 
                com.app.ralaunch.data.SettingsManager.getInstance(context);
            mAttackMode = settingsManager.getMouseRightStickAttackMode();
            mGlobalMouseSpeed = settingsManager.getMouseRightStickSpeed();
            mGlobalMouseRangeLeft = settingsManager.getMouseRightStickRangeLeft();
            mGlobalMouseRangeTop = settingsManager.getMouseRightStickRangeTop();
            mGlobalMouseRangeRight = settingsManager.getMouseRightStickRangeRight();
            mGlobalMouseRangeBottom = settingsManager.getMouseRightStickRangeBottom();
            
            // 自动修正异常的范围设置（避免鼠标移动受限）
            boolean needsReset = false;
            if (mGlobalMouseRangeLeft > 0.1f || mGlobalMouseRangeTop > 0.1f || 
                mGlobalMouseRangeRight < 0.9f || mGlobalMouseRangeBottom < 0.9f) {
                Log.w(TAG, "Detected restricted mouse range, resetting to full screen");
                mGlobalMouseRangeLeft = 0.0f;
                mGlobalMouseRangeTop = 0.0f;
                mGlobalMouseRangeRight = 1.0f;
                mGlobalMouseRangeBottom = 1.0f;
                needsReset = true;
            }
            
            // 保存修正后的值到设置
            if (needsReset) {
                settingsManager.setMouseRightStickRangeLeft(mGlobalMouseRangeLeft);
                settingsManager.setMouseRightStickRangeTop(mGlobalMouseRangeTop);
                settingsManager.setMouseRightStickRangeRight(mGlobalMouseRangeRight);
                settingsManager.setMouseRightStickRangeBottom(mGlobalMouseRangeBottom);
            }
            
            Log.i(TAG, "Global settings loaded: speed=" + mGlobalMouseSpeed + 
                  ", range=(" + mGlobalMouseRangeLeft + "," + mGlobalMouseRangeTop + 
                  "," + mGlobalMouseRangeRight + "," + mGlobalMouseRangeBottom + ")");
        } catch (Exception e) {
            mAttackMode = 0; // 默认长按模式
            mGlobalMouseSpeed = 80.0f; // 默认速度80（范围60-200）
            mGlobalMouseRangeLeft = 0.0f;
            mGlobalMouseRangeTop = 0.0f;
            mGlobalMouseRangeRight = 1.0f;
            mGlobalMouseRangeBottom = 1.0f;
        }

        // 设置透明背景，确保只显示绘制的圆形
        setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // 禁用裁剪，让方向指示线可以完整显示
        setClipToOutline(false);
        setClipBounds(null);

        // 初始化点击攻击 Handler（仅用于非鼠标移动模式）
        mClickAttackHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mClickAttackRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsAttacking && mCurrentDirection != DIR_NONE && !mData.rightStickContinuous) {
                    // 点击模式：触发一次点击（释放然后按下）
                    performClickAttack(mCurrentDirection);
                    // 继续下一次点击
                    mClickAttackHandler.postDelayed(this, CLICK_ATTACK_INTERVAL_MS);
                }
            }
        };
        
        // 初始化鼠标模式点击 Runnable（仅点击模式使用）
        mMouseClickRunnable = new Runnable() {
            @Override
            public void run() {
                // 仅在点击模式下执行连续点击
                if (mIsAttacking && mMouseLeftPressed && mAttackMode == 1 && mInputBridge instanceof SDLInputBridge) {
                    SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
                    // 使用屏幕中心作为点击位置
                    float mouseX = mScreenWidth / 2.0f;
                    float mouseY = mScreenHeight / 2.0f;
                    
                    // 发送鼠标左键点击（按下-释放）
                    bridge.sendMouseButton(ControlData.MOUSE_LEFT, true, mouseX, mouseY);
                    // 短暂延迟后释放
                    mClickAttackHandler.postDelayed(() -> {
                        if (mIsAttacking && mMouseLeftPressed && mAttackMode == 1) {
                            bridge.sendMouseButton(ControlData.MOUSE_LEFT, false, mouseX, mouseY);
                        }
                    }, 50); // 50ms 按下时间
                    
                    // 继续下一次点击
                    mClickAttackHandler.postDelayed(mMouseClickRunnable, MOUSE_CLICK_INTERVAL_MS);
                }
            }
        };

        initPaints();
    }
    
    private void initPaints() {
        // RadialGamePad 风格的颜色系统
        // 背景圆：使用不透明的颜色值，通过 setAlpha 控制透明度，避免颜色值本身的透明度影响
        mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint.setColor(0xFF7D7D7D); // 不透明的灰色（RGB: 125, 125, 125）
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        // 背景透明度只使用 opacity，不受 stickOpacity 影响
        // 直接使用用户设置的 opacity，让变化更明显
        mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
        
        // 摇杆圆心：使用不透明的颜色值，通过 setAlpha 控制透明度
        mStickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStickPaint.setColor(0xFF7D7D7D); // 不透明的灰色（RGB: 125, 125, 125）
        mStickPaint.setStyle(Paint.Style.FILL);
        // 摇杆圆心透明度只使用 stickOpacity，如果没有设置则使用默认值 1.0（完全不透明），不受 opacity 影响
        // 直接使用用户设置的 stickOpacity，让变化更明显（0.0-1.0 全范围）
        float stickKnobAlpha = mData.stickOpacity != 0 ? mData.stickOpacity : 1.0f;
        mStickPaint.setAlpha((int) (stickKnobAlpha * 255));
        
        // 描边默认透明（RadialGamePad 风格）
        mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStrokePaint.setColor(0x00000000); // 透明
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(0);
        // 边框透明度完全独立，默认1.0（完全不透明）
        float borderOpacity = mData.borderOpacity != 0 ? mData.borderOpacity : 1.0f;
        mStrokePaint.setAlpha((int) (borderOpacity * 255));
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // 强制保持正方形（取最小边）
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 由于已经强制为正方形，w 和 h 应该相等
        mCenterX = w / 2f;
        mCenterY = h / 2f;
        mRadius = Math.min(w, h) / 2f;
        
        // RadialGamePad 风格：摇杆圆心是半径的 50%（0.5f * radius）
        // 如果配置了 stickKnobSize，则使用配置值，否则使用 RadialGamePad 默认值 0.5
        float knobSizeRatio = (mData.stickKnobSize != 0) ? mData.stickKnobSize : 0.5f;
        mStickRadius = mRadius * knobSizeRatio;
        resetStick();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        // 应用旋转
        if (mData.rotation != 0) {
            canvas.save();
            canvas.rotate(mData.rotation, centerX, centerY);
        }
        
        // RadialGamePad 风格：背景圆使用 75% 半径（STICK_BACKGROUND_SIZE = 0.75f）
        float backgroundRadius = mRadius * 0.75f;
        // 背景透明度只使用 opacity，不受 stickOpacity 影响
        // 直接使用用户设置的 opacity，让变化更明显
        mBackgroundPaint.setAlpha((int) (mData.opacity * 255));
        canvas.drawCircle(mCenterX, mCenterY, backgroundRadius, mBackgroundPaint);
        
        // 更新摇杆圆心透明度（如果数据已更新）
        // 摇杆圆心透明度只使用 stickOpacity，如果没有设置则使用默认值 1.0（完全不透明），不受 opacity 影响
        // 直接使用用户设置的 stickOpacity，让变化更明显（0.0-1.0 全范围）
        float stickKnobAlpha = mData.stickOpacity != 0 ? mData.stickOpacity : 1.0f;
        mStickPaint.setAlpha((int) (stickKnobAlpha * 255));
        
        // 绘制摇杆圆心（前景圆，根据触摸位置移动）
        // RadialGamePad 风格：摇杆圆心是背景半径的 50%（0.5f * radius）
        // 但我们已经根据 mStickRadius 计算了，这里直接使用
        canvas.drawCircle(mStickX, mStickY, mStickRadius, mStickPaint);
        
        // 恢复旋转
        if (mData.rotation != 0) {
            canvas.restore();
        }
    }
    
    /**
     * 获取方向对应的角度（用于绘制指示线）
     */
    private float getAngleForDirection(int direction) {
        switch (direction) {
            case DIR_RIGHT: return 0;
            case DIR_UP_RIGHT: return 45;
            case DIR_UP: return 90;
            case DIR_UP_LEFT: return 135;
            case DIR_LEFT: return 180;
            case DIR_DOWN_LEFT: return 225;
            case DIR_DOWN: return 270;
            case DIR_DOWN_RIGHT: return 315;
            default: return -1;
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerId = event.getPointerId(event.getActionIndex());

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 如果已经在跟踪一个触摸点，忽略新的
                if (mIsTouching) {
                    return false;
                }
                
                int pointerIndex = event.getActionIndex();
                float touchX = event.getX(pointerIndex);
                float touchY = event.getY(pointerIndex);
                
                // 检查触摸点是否在圆形区域内
                float dx = touchX - mCenterX;
                float dy = touchY - mCenterY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                // 只响应圆形区域内的触摸
                if (distance > mRadius) {
                    return false;
                }

                // 记录触摸点
                mActivePointerId = pointerId;
                // 如果不穿透，标记这个触摸点被占用（不传递给游戏）
                if (!mData.passThrough) {
                    TouchPointerTracker.consumePointer(pointerId);
                }

                // 如果是右摇杆鼠标模式，启用虚拟鼠标系统
                if (mData.joystickMode == ControlData.JOYSTICK_MODE_MOUSE && mData.xboxUseRightStick) {
                    enableVirtualMouse();
                }

                handleMove(touchX, touchY);
                mIsTouching = true;
                triggerVibration(true);
                // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                return !mData.passThrough;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!mIsTouching || mActivePointerId == -1) {
                    return false;
                }
                
                // 找到我们跟踪的触摸点
                int pointerIndex = event.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                return false;
                }
                
                float touchX = event.getX(pointerIndex);
                float touchY = event.getY(pointerIndex);
                    handleMove(touchX, touchY);
                // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                return !mData.passThrough;
                }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP: {
                // 检查是否是我们跟踪的触摸点
                if (pointerId == mActivePointerId && mIsTouching) {
                    // 释放触摸点标记（如果之前标记了）
                    if (!mData.passThrough) {
                        TouchPointerTracker.releasePointer(mActivePointerId);
                    }
                    mActivePointerId = -1;
                    
                    handleRelease();
                    mIsTouching = false;
                    triggerVibration(false);
                    // 如果设置了穿透，返回 false 让事件继续传递；否则返回 true 消费事件
                    return !mData.passThrough;
                }
                return false;
            }
        }
        return super.onTouchEvent(event);
    }
    
    private void handleMove(float touchX, float touchY) {
        float dx = touchX - mCenterX;
        float dy = touchY - mCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        
        // 计算摇杆位置（限制在圆内）
        float maxDistance = mRadius - mStickRadius;
        if (distance > maxDistance) {
            float ratio = maxDistance / distance;
            mStickX = mCenterX + dx * ratio;
            mStickY = mCenterY + dy * ratio;
        } else {
            mStickX = touchX;
            mStickY = touchY;
        }
        
        // 根据模式发送不同的输入事件
        if (mData.joystickMode == ControlData.JOYSTICK_MODE_MOUSE) {
            // 鼠标模式：根据xboxUseRightStick区分左右摇杆
            if (mData.xboxUseRightStick) {
                // 右摇杆：鼠标移动模式 + 持续点击攻击
                // 检测是否进入/离开死区
                int newDirection = calculateDirection(dx, dy, distance);
                if (newDirection != mCurrentDirection) {
                    int oldDirection = mCurrentDirection;
                    mCurrentDirection = newDirection;
                    
                    // 进入/离开死区时启动/停止持续点击
                    if (oldDirection == DIR_NONE && newDirection != DIR_NONE) {
                        // 从死区进入有效区域：开始持续鼠标左键点击
                        startMouseClick();
                        mIsAttacking = true;
                    } else if (oldDirection != DIR_NONE && newDirection == DIR_NONE) {
                        // 从有效区域进入死区：停止持续点击
                        stopMouseClick();
                        mIsAttacking = false;
                    }
                }
                
                // 发送鼠标相对移动（只有当摇杆位置变化时才移动）
                sendVirtualMouseMove(dx, dy, distance);
            } else {
                // 左摇杆：将摇杆偏移量转换为鼠标移动
                sendMouseMove(dx, dy, distance);
            }
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
            // SDL控制器模式：发送模拟摇杆输入
            sendSDLStick(dx, dy, distance, maxDistance);
        } else {
            // 键盘模式：计算方向并发送按键事件
            int newDirection = calculateDirection(dx, dy, distance);
            if (newDirection != mCurrentDirection) {
                // 释放旧方向的按键
                releaseDirection(mCurrentDirection);
                // 按下新方向的按键
                pressDirection(newDirection);
                mCurrentDirection = newDirection;
            }
        }
        
        invalidate();
    }
    
    private void handleRelease() {
        resetStick();
        
        // 重置右摇杆上一帧位置
        mLastJoystickDx = 0;
        mLastJoystickDy = 0;
        
        // 根据模式执行不同的释放操作
        if (mData.joystickMode == ControlData.JOYSTICK_MODE_MOUSE) {
            // 鼠标模式
            if (mData.xboxUseRightStick) {
                // 右摇杆：停止持续点击
                stopMouseClick();
                mIsAttacking = false;
                
                // 重要：保存当前虚拟鼠标位置，防止松开时鼠标位置被重置
                // 因为触摸事件可能会被SDL转换为鼠标事件，导致鼠标位置跳回触摸位置
                // 使用延迟执行确保在SDL处理完触摸事件后再设置鼠标位置
                if (mInputBridge instanceof SDLInputBridge) {
                    final SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
                    final float currentX = bridge.getVirtualMouseX();
                    final float currentY = bridge.getVirtualMouseY();
                    
                    // 延迟50ms执行，确保SDL处理完触摸事件
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            bridge.setVirtualMousePosition(currentX, currentY);
                            Log.d(TAG, "Restored mouse position after release: (" + currentX + ", " + currentY + ")");
                        }
                    }, 50);
                }
            }
            mCurrentDirection = DIR_NONE;
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
            // 键盘模式：释放按键和组合键
            releaseDirection(mCurrentDirection);
            mCurrentDirection = DIR_NONE;
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
            // SDL控制器模式：摇杆回中
            if (mData.xboxUseRightStick) {
                mInputBridge.sendXboxRightStick(0.0f, 0.0f);
            } else {
                mInputBridge.sendXboxLeftStick(0.0f, 0.0f);
            }
            mCurrentDirection = DIR_NONE;
        }
        
        invalidate();
    }
    
    private void resetStick() {
        mStickX = mCenterX;
        mStickY = mCenterY;
    }
    
    /**
     * 计算摇杆方向（8方向）
     * 优化算法：使用角度映射表确保方向准确
     */
    private int calculateDirection(float dx, float dy, float distance) {
        // 死区检测 - 小范围内不触发方向
        if (distance < mRadius * DEADZONE_PERCENT) {
            return DIR_NONE;
        }
        
        // 计算角度（0度为正右方，逆时针）
        // 注意：屏幕坐标系y轴向下为正，所以dy取负使得向上为负角度
        double angle = Math.atan2(-dy, dx) * 180 / Math.PI;
        
        // 标准化角度到0-360度
        if (angle < 0) angle += 360;
        
        // 将360度分成8个扇区（每个45度）
        // 添加22.5度偏移使得每个方向占据45度的中心区域
        // 例如：正右方(0度)占据[-22.5, 22.5]度，右上方(45度)占据[22.5, 67.5]度
        int angleIndex = (int) ((angle + 22.5) / 45.0) % 8;
        
        // 使用映射表转换为实际方向
        int direction = ANGLE_TO_DIR[angleIndex];
        
        return direction;
    }
    
    /**
     * 按下方向对应的按键
     * joystickKeys数组：[上W, 右D, 下S, 左A]（可选，如果为null则不发送键盘按键）
     */
    private void pressDirection(int direction) {
        if (direction == DIR_NONE) return;
        
        // 发送方向键（如果joystickKeys不为null）
        if (mData.joystickKeys != null) {
        switch (direction) {
            case DIR_UP:  // 上 -> W
                mInputBridge.sendKey(mData.joystickKeys[0], true);
                break;
            case DIR_UP_RIGHT:  // 右上 -> W+D
                mInputBridge.sendKey(mData.joystickKeys[0], true); // W (up)
                mInputBridge.sendKey(mData.joystickKeys[1], true); // D (right)
                break;
            case DIR_RIGHT:  // 右 -> D
                mInputBridge.sendKey(mData.joystickKeys[1], true);
                break;
            case DIR_DOWN_RIGHT:  // 右下 -> S+D
                mInputBridge.sendKey(mData.joystickKeys[2], true); // S (down)
                mInputBridge.sendKey(mData.joystickKeys[1], true); // D (right)
                break;
            case DIR_DOWN:  // 下 -> S
                mInputBridge.sendKey(mData.joystickKeys[2], true);
                break;
            case DIR_DOWN_LEFT:  // 左下 -> S+A
                mInputBridge.sendKey(mData.joystickKeys[2], true); // S (down)
                mInputBridge.sendKey(mData.joystickKeys[3], true); // A (left)
                break;
            case DIR_LEFT:  // 左 -> A
                mInputBridge.sendKey(mData.joystickKeys[3], true);
                break;
            case DIR_UP_LEFT:  // 左上 -> W+A
                mInputBridge.sendKey(mData.joystickKeys[0], true); // W (up)
                mInputBridge.sendKey(mData.joystickKeys[3], true); // A (left)
                break;
            }
        }
        
    }
    
    /**
     * 释放方向对应的按键
     */
    private void releaseDirection(int direction) {
        if (direction == DIR_NONE) return;
        
        // 释放方向键（如果joystickKeys不为null）
        if (mData.joystickKeys != null) {
        switch (direction) {
            case DIR_UP:
                mInputBridge.sendKey(mData.joystickKeys[0], false);
                break;
            case DIR_UP_RIGHT:
                mInputBridge.sendKey(mData.joystickKeys[0], false);
                mInputBridge.sendKey(mData.joystickKeys[1], false);
                break;
            case DIR_RIGHT:
                mInputBridge.sendKey(mData.joystickKeys[1], false);
                break;
            case DIR_DOWN_RIGHT:
                mInputBridge.sendKey(mData.joystickKeys[2], false);
                mInputBridge.sendKey(mData.joystickKeys[1], false);
                break;
            case DIR_DOWN:
                mInputBridge.sendKey(mData.joystickKeys[2], false);
                break;
            case DIR_DOWN_LEFT:
                mInputBridge.sendKey(mData.joystickKeys[2], false);
                mInputBridge.sendKey(mData.joystickKeys[3], false);
                break;
            case DIR_LEFT:
                mInputBridge.sendKey(mData.joystickKeys[3], false);
                break;
            case DIR_UP_LEFT:
                mInputBridge.sendKey(mData.joystickKeys[0], false);
                mInputBridge.sendKey(mData.joystickKeys[3], false);
                break;
            }
        }
        
    }
    
    /**
     * 停止持续攻击 - 清除虚拟触屏点
     */
    private void stopContinuousAttack() {
        if (!mIsAttacking) return;
        mIsAttacking = false;
        // 释放攻击状态
        // 组合键已移除
    }
    
    /**
     * 更新攻击方向（方向改变时调用，用于持续攻击模式）
     * 会自动释放旧方向的虚拟触屏点并按下新方向
     */
    private void updateAttackDirection(int oldDirection, int newDirection) {
        // 释放旧方向
        if (oldDirection != DIR_NONE) {
            // 组合键已移除
        }
        // 按下新方向
        if (newDirection != DIR_NONE) {
            // 组合键已移除
        }
    }
    
    /**
     * 启动点击攻击模式
     */
    private void startClickAttack() {
        if (mIsAttacking) return;
        mIsAttacking = true;
        // 立即触发第一次点击
        performClickAttack(mCurrentDirection);
        // 启动持续点击循环
        mClickAttackHandler.postDelayed(mClickAttackRunnable, CLICK_ATTACK_INTERVAL_MS);
    }
    
    /**
     * 停止点击攻击模式
     */
    private void stopClickAttack() {
        if (!mIsAttacking) return;
        mIsAttacking = false;
        mClickAttackHandler.removeCallbacks(mClickAttackRunnable);
        // 确保释放最后的虚拟触屏点
        // 组合键已移除
    }
    
    /**
     * 启动鼠标攻击（鼠标模式右摇杆）
     * 
     * 根据攻击模式执行不同行为：
     * - 长按模式 (0)：按下鼠标左键，保持按住
     * - 点击模式 (1)：快速连续点击
     * - 持续模式 (2)：按下鼠标左键，保持按住（同长按）
     */
    private void startMouseClick() {
        if (mMouseLeftPressed) return;
        mMouseLeftPressed = true;
        
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            // 使用当前虚拟鼠标位置作为点击位置，避免鼠标跳动
            float mouseX = bridge.getVirtualMouseX();
            float mouseY = bridge.getVirtualMouseY();
            
            // Log.v(TAG, "Mouse attack started at (" + mouseX + "," + mouseY + "), mode=" + mAttackMode);
            
            switch (mAttackMode) {
                case 0: // 长按模式：按下鼠标左键，不释放
                case 2: // 持续模式：同长按
                    bridge.sendMouseButton(ControlData.MOUSE_LEFT, true, mouseX, mouseY);
                    // Log.v(TAG, "Mouse left button pressed (hold mode)");
                    break;
                    
                case 1: // 点击模式：启动连续点击
                    // 立即发送第一次点击
                    bridge.sendMouseButton(ControlData.MOUSE_LEFT, true, mouseX, mouseY);
                    // 启动连续点击循环
                    mClickAttackHandler.postDelayed(mMouseClickRunnable, MOUSE_CLICK_INTERVAL_MS);
                    // Log.v(TAG, "Mouse click loop started");
                    break;
            }
        }
    }
    
    /**
     * 停止鼠标攻击
     */
    private void stopMouseClick() {
        if (!mMouseLeftPressed) return;
        mMouseLeftPressed = false;
        
        // 停止点击循环（点击模式）
        mClickAttackHandler.removeCallbacks(mMouseClickRunnable);
        
        // 释放鼠标左键（所有模式都需要）
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            // 使用当前虚拟鼠标位置作为释放位置
            float mouseX = bridge.getVirtualMouseX();
            float mouseY = bridge.getVirtualMouseY();
            bridge.sendMouseButton(ControlData.MOUSE_LEFT, false, mouseX, mouseY);
            
            // Log.v(TAG, "Mouse attack stopped at (" + mouseX + "," + mouseY + ")");
        }
    }
    
    /**
     * 执行一次点击攻击（按下然后短暂后释放）
     */
    private void performClickAttack(int direction) {
        if (direction == DIR_NONE) return;
        
        // 组合键已移除，直接使用鼠标左键点击
        float[] pos = calculateDirectionPosition(direction);
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            // 按下
            bridge.sendVirtualTouch(SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, pos[0], pos[1], true);
            // 短暂延迟后释放
            mClickAttackHandler.postDelayed(() -> {
                if (mIsAttacking) {
                    bridge.sendVirtualTouch(SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, pos[0], pos[1], false);
                }
            }, 50); // 50ms 按下时间
        }
    }
    
    /**
     * 计算八方向对应的屏幕位置（用于右摇杆八方向攻击）
     * @param direction 方向常量
     * @return float[] {x, y} 屏幕坐标
     */
    private float[] calculateDirectionPosition(int direction) {
        // 计算瞄准距离（200像素）
        float aimDistance = 200f;
        float centerX = mScreenWidth / 2.0f;
        float centerY = mScreenHeight / 2.0f;
        
        float targetX = centerX;
        float targetY = centerY;
        
        // 计算对角线距离（45度方向使用 0.707 = cos(45°)）
        float diagonalOffset = aimDistance * 0.707f;
        
        switch (direction) {
            case DIR_UP:
                targetY = centerY - aimDistance;
                break;
            case DIR_DOWN:
                targetY = centerY + aimDistance;
                break;
            case DIR_LEFT:
                targetX = centerX - aimDistance;
                break;
            case DIR_RIGHT:
                targetX = centerX + aimDistance;
                break;
            case DIR_UP_LEFT:
                targetX = centerX - diagonalOffset;
                targetY = centerY - diagonalOffset;
                break;
            case DIR_UP_RIGHT:
                targetX = centerX + diagonalOffset;
                targetY = centerY - diagonalOffset;
                break;
            case DIR_DOWN_LEFT:
                targetX = centerX - diagonalOffset;
                targetY = centerY + diagonalOffset;
                break;
            case DIR_DOWN_RIGHT:
                targetX = centerX + diagonalOffset;
                targetY = centerY + diagonalOffset;
                break;
        }
        
        // 限制在屏幕范围内
        targetX = Math.max(0, Math.min(mScreenWidth, targetX));
        targetY = Math.max(0, Math.min(mScreenHeight, targetY));
        
        return new float[] {targetX, targetY};
    }
    
    /**
     * 发送鼠标移动事件（鼠标模式，用于左摇杆）
     * 将摇杆偏移量转换为鼠标移动增量
     */
    private void sendMouseMove(float dx, float dy, float distance) {
        // 死区检测
        if (distance < mRadius * DEADZONE_PERCENT) {
            return;
        }
        
        // 标准化偏移量到 [-1, 1] 范围
        float maxDistance = mRadius - mStickRadius;
        float normalizedX = dx / maxDistance;
        float normalizedY = dy / maxDistance;
        
        // 应用灵敏度系数（可调整）
        float sensitivity = 15.0f; // 鼠标移动速度倍数
        float mouseX = normalizedX * sensitivity;
        float mouseY = normalizedY * sensitivity;
        
        // 发送鼠标移动事件
        mInputBridge.sendMouseMove(mouseX, mouseY);
    }
    
    /**
     * 发送虚拟鼠标移动事件（用于右摇杆鼠标移动模式）
     * 
     * 功能：
     * - 摇杆静止不动时 → 鼠标不移动
     * - 摇杆位置变化时 → 鼠标跟随移动
     * - 使用 sendMouseMove 发送真正的鼠标相对移动事件
     * - 同时更新虚拟鼠标位置追踪，以便松开时恢复位置
     */
    private void sendVirtualMouseMove(float dx, float dy, float distance) {
        // 死区检测：在死区内时，重置上一帧位置
        if (distance < mRadius * DEADZONE_PERCENT) {
            mLastJoystickDx = 0;
            mLastJoystickDy = 0;
            return;
        }
        
        // 计算摇杆位置的变化量（当前位置 - 上一帧位置）
        float deltaDx = dx - mLastJoystickDx;
        float deltaDy = dy - mLastJoystickDy;
        
        // 计算变化距离
        float deltaDistance = (float) Math.sqrt(deltaDx * deltaDx + deltaDy * deltaDy);
        
        // 如果摇杆位置没有变化，不移动鼠标（摇杆静止不动）
        if (deltaDistance < JOYSTICK_MOVE_THRESHOLD) {
            // 摇杆静止不动，鼠标也不移动
            return;
        }
        
        // 保存当前位置供下一帧使用（只有超过阈值才更新）
        mLastJoystickDx = dx;
        mLastJoystickDy = dy;
        
        // 标准化变化量
        float maxDistance = mRadius - mStickRadius;
        float normalizedDeltaX = deltaDx / maxDistance;
        float normalizedDeltaY = deltaDy / maxDistance;
        
        // 应用灵敏度系数（使用全局设置的速度）
        float sensitivity = mGlobalMouseSpeed;
        float mouseX = normalizedDeltaX * sensitivity;
        float mouseY = normalizedDeltaY * sensitivity;
        
        // 发送真正的鼠标相对移动事件（使用 SDL onNativeMouse）
        mInputBridge.sendMouseMove(mouseX, mouseY);
        
        // 同时更新虚拟鼠标位置追踪（用于松开时恢复位置）
        if (mInputBridge instanceof SDLInputBridge) {
            ((SDLInputBridge) mInputBridge).updateVirtualMouseDelta(mouseX, mouseY);
        }
        
        // Log.v(TAG, "Mouse move: delta=(" + mouseX + ", " + mouseY + "), speed=" + sensitivity);
    }
    
    /**
     * 启用虚拟鼠标（右摇杆鼠标移动模式）
     */
    private void enableVirtualMouse() {
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            // 先设置鼠标移动范围（从全局设置读取）
            float left = mGlobalMouseRangeLeft;
            float top = mGlobalMouseRangeTop;
            float right = mGlobalMouseRangeRight;
            float bottom = mGlobalMouseRangeBottom;
            
            Log.i(TAG, "enableVirtualMouse: global range=(" + left + "," + top + "," + right + "," + bottom + 
                  "), speed=" + mGlobalMouseSpeed);
            
            bridge.setVirtualMouseRange(left, top, right, bottom);
            // 然后启用虚拟鼠标
            bridge.enableVirtualMouse();
        }
    }
    
    /**
     * 禁用虚拟鼠标
     */
    private void disableVirtualMouse() {
        if (mInputBridge instanceof SDLInputBridge) {
            ((SDLInputBridge) mInputBridge).disableVirtualMouse();
        }
    }
    
    /**
     * 发送SDL控制器摇杆输入（SDL控制器模式）
     * 将摇杆偏移量转换为标准化的模拟摇杆值
     */
    private void sendSDLStick(float dx, float dy, float distance, float maxDistance) {
        // 标准化偏移量到 [-1, 1] 范围
        float normalizedX = 0.0f;
        float normalizedY = 0.0f;

        // 死区处理：在死区内返回0，死区外进行平滑映射
        float deadzone = mRadius * DEADZONE_PERCENT;
        if (distance > deadzone) {
            // 计算超出死区的距离比例
            float adjustedDistance = distance - deadzone;
            float adjustedMax = maxDistance - deadzone;
            float ratio = adjustedDistance / adjustedMax;

            // 限制在 [0, 1] 范围
            if (ratio > 1.0f) ratio = 1.0f;

            // 应用方向
            normalizedX = (dx / distance) * ratio;
            normalizedY = (dy / distance) * ratio;
        }

        // 发送到对应的Xbox摇杆（左或右）
        if (mData.xboxUseRightStick) {
            mInputBridge.sendXboxRightStick(normalizedX, normalizedY);
        } else {
            mInputBridge.sendXboxLeftStick(normalizedX, normalizedY);
        }
    }

    /**
     * 获取方向名称（用于日志）
     */
    private String getDirectionName(int direction) {
        switch (direction) {
            case DIR_UP: return "UP";
            case DIR_UP_RIGHT: return "UP_RIGHT";
            case DIR_RIGHT: return "RIGHT";
            case DIR_DOWN_RIGHT: return "DOWN_RIGHT";
            case DIR_DOWN: return "DOWN";
            case DIR_DOWN_LEFT: return "DOWN_LEFT";
            case DIR_LEFT: return "LEFT";
            case DIR_UP_LEFT: return "UP_LEFT";
            default: return "NONE";
        }
    }

    @Override
    public ControlData getData() {
        return mData;
    }

    @Override
    public void updateData(ControlData data) {
        mData = data;
        initPaints();
        // 重新计算摇杆圆心大小（因为 stickKnobSize 可能已改变）
        if (mRadius > 0) {
            float knobSizeRatio = (mData.stickKnobSize != 0) ? mData.stickKnobSize : 0.5f;
            mStickRadius = mRadius * knobSizeRatio;
        }
        invalidate();
    }

    /**
     * 设置Xbox控制器模式下控制哪个摇杆
     * @param useRightStick true=右摇杆, false=左摇杆（默认）
     */
    public void setXboxStickMode(boolean useRightStick) {
        mData.xboxUseRightStick = useRightStick;
    }

    /**
     * 获取当前控制的Xbox摇杆类型
     * @return true=右摇杆, false=左摇杆
     */
    public boolean isXboxRightStick() {
        return mData.xboxUseRightStick;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static void triggerVibration(boolean isPress) {
        if (isPress) {
            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
        }
        else {
            // 释放时不振动
//            RaLaunchApplication.getVibrationManager().vibrateOneShot(50, 30);
        }
    }
}

