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
    
    // 右摇杆上一帧位置（用于计算位置变化量）
    private float mLastJoystickDx = 0;
    private float mLastJoystickDy = 0;
    
    // 右摇杆移动阈值（像素，只有当摇杆位置变化超过此值时才移动鼠标）
    private static final float JOYSTICK_MOVE_THRESHOLD = 8.0f;

    // 死区（防止漂移） - 优化为更小的死区提升灵敏度
    private static final float DEADZONE_PERCENT = 0.1f;
    
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
        // 使用边框透明度（如果为0则使用背景透明度作为兼容）
        float borderOpacity = mData.borderOpacity != 0 ? mData.borderOpacity : mData.opacity;
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
                // 右摇杆：鼠标移动模式（可精确控制光标位置）
                // 检测是否进入/离开死区，处理组合键和虚拟鼠标启用
                int newDirection = calculateDirection(dx, dy, distance);
                if (newDirection != mCurrentDirection) {
                    int oldDirection = mCurrentDirection;
                    mCurrentDirection = newDirection;
                    
                    // 持续攻击模式：进入有效区域时按下组合键，离开时释放
                    if (oldDirection == DIR_NONE && newDirection != DIR_NONE) {
                        // 从死区进入有效区域：先启用虚拟鼠标，再按下组合键
                        enableVirtualMouse();
                        pressComboKeysForMouseMove();
                        mIsAttacking = true;
                    } else if (oldDirection != DIR_NONE && newDirection == DIR_NONE) {
                        // 从有效区域进入死区：释放组合键并禁用虚拟鼠标
                        releaseComboKeysForMouseMove();
                        disableVirtualMouse();
                        mIsAttacking = false;
                    }
                }
                
                // 发送虚拟鼠标相对移动（必须在 enableVirtualMouse 之后调用）
                sendVirtualMouseMove(dx, dy, distance);
            } else {
                // 左摇杆：将摇杆偏移量转换为鼠标移动
            sendMouseMove(dx, dy, distance);
                // 左摇杆也支持组合键（基于方向）
                int newDirection = calculateDirection(dx, dy, distance);
                if (newDirection != mCurrentDirection) {
                    releaseComboKeys(mCurrentDirection);
                    if (newDirection != DIR_NONE) {
                        pressComboKeys(newDirection);
                    }
                    mCurrentDirection = newDirection;
                }
            }
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
            // SDL控制器模式：发送模拟摇杆输入
            sendSDLStick(dx, dy, distance, maxDistance);
            // SDL控制器模式也支持组合键（基于方向）
            int newDirection = calculateDirection(dx, dy, distance);
            if (newDirection != mCurrentDirection) {
                // 释放旧方向的组合键
                releaseComboKeys(mCurrentDirection);
                // 按下新方向的组合键（如果不在死区内）
                if (newDirection != DIR_NONE) {
                    pressComboKeys(newDirection);
                }
                mCurrentDirection = newDirection;
            }
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
            // 鼠标模式：释放组合键
            if (mData.xboxUseRightStick) {
                // 右摇杆：释放组合键
                releaseComboKeysForMouseMove();
                mIsAttacking = false;
                // 禁用虚拟鼠标
                disableVirtualMouse();
            } else {
                // 左摇杆：释放组合键
                releaseComboKeys(mCurrentDirection);
            }
            mCurrentDirection = DIR_NONE;
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
            // 键盘模式：释放按键和组合键
            releaseDirection(mCurrentDirection);
            mCurrentDirection = DIR_NONE;
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
            // SDL控制器模式：摇杆回中，并释放组合键
            if (mData.xboxUseRightStick) {
                mInputBridge.sendXboxRightStick(0.0f, 0.0f);
            } else {
                mInputBridge.sendXboxLeftStick(0.0f, 0.0f);
            }
            releaseComboKeys(mCurrentDirection);
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
     * joystickComboKeys数组：[方向][组合按钮列表] - 每个方向可以组合多个手柄按钮
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
        
        // 发送统一组合键（键盘、鼠标、手柄按钮和触发器）- 无论joystickKeys是否为null都发送，所有方向共用
        // 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, true);
                }
                // 鼠标按键（-1 到 -3）
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_LEFT) {
                    // 鼠标按键：使用 sendMouseButton，坐标使用摇杆中心位置
                    mInputBridge.sendMouseButton(comboKey, true, mCenterX, mCenterY);
                }
                // 手柄触发器（范围: -221 到 -220）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_TRIGGER_RIGHT && comboKey <= ControlData.XBOX_TRIGGER_LEFT) {
                    // 触发器：使用 sendXboxTrigger，值为 1.0 表示按下
                    mInputBridge.sendXboxTrigger(comboKey, 1.0f);
                }
                // 手柄按钮（范围: -200 到 -214）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_BUTTON_DPAD_RIGHT && comboKey <= ControlData.XBOX_BUTTON_A) {
                    // 普通按钮：使用 sendXboxButton
                    mInputBridge.sendXboxButton(comboKey, true);
                }
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
        
        // 释放统一组合键（键盘、鼠标、手柄按钮和触发器）- 无论joystickKeys是否为null都释放，所有方向共用
        // 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, false);
                }
                // 鼠标按键（-1 到 -3）
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_LEFT) {
                    // 鼠标按键：使用 sendMouseButton，坐标使用摇杆中心位置
                    mInputBridge.sendMouseButton(comboKey, false, mCenterX, mCenterY);
                }
                // 手柄触发器（范围: -221 到 -220）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_TRIGGER_RIGHT && comboKey <= ControlData.XBOX_TRIGGER_LEFT) {
                    // 触发器：使用 sendXboxTrigger，值为 0.0 表示释放
                    mInputBridge.sendXboxTrigger(comboKey, 0.0f);
                }
                // 手柄按钮（范围: -200 到 -214）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_BUTTON_DPAD_RIGHT && comboKey <= ControlData.XBOX_BUTTON_A) {
                    // 普通按钮：使用 sendXboxButton
                    mInputBridge.sendXboxButton(comboKey, false);
                }
            }
        }

    }
    
    /**
     * 仅发送统一组合键（键盘、鼠标、手柄按钮和触发器）- 用于鼠标模式和SDL控制器模式
     * 所有方向共用同一个组合键
     * 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
     */
    private void pressComboKeys(int direction) {
        if (direction == DIR_NONE) return;
        
        // 发送统一组合键（键盘、鼠标、手柄按钮和触发器）- 所有方向共用
        // 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, true);
                }
                // 鼠标按键（-1 到 -3）
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_LEFT) {
                    // 鼠标按键：使用 sendMouseButton，坐标使用摇杆中心位置
                    mInputBridge.sendMouseButton(comboKey, true, mCenterX, mCenterY);
                }
                // 手柄触发器（范围: -221 到 -220）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_TRIGGER_RIGHT && comboKey <= ControlData.XBOX_TRIGGER_LEFT) {
                    // 触发器：使用 sendXboxTrigger，值为 1.0 表示按下
                    mInputBridge.sendXboxTrigger(comboKey, 1.0f);
                }
                // 手柄按钮（范围: -200 到 -214）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_BUTTON_DPAD_RIGHT && comboKey <= ControlData.XBOX_BUTTON_A) {
                    // 普通按钮：使用 sendXboxButton
                    mInputBridge.sendXboxButton(comboKey, true);
                }
            }
        }
    }
    
    /**
     * 仅释放统一组合键（键盘、鼠标、手柄按钮和触发器）- 用于鼠标模式和SDL控制器模式
     * 所有方向共用同一个组合键
     * 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
     */
    private void releaseComboKeys(int direction) {
        if (direction == DIR_NONE) return;
        
        // 释放统一组合键（键盘、鼠标、手柄按钮和触发器）- 所有方向共用
        // 注意：在鼠标模式下，不发送手柄按钮和触发器，避免触发手柄模式切换
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, false);
                }
                // 鼠标按键（-1 到 -3）
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_LEFT) {
                    // 鼠标按键：使用 sendMouseButton，坐标使用摇杆中心位置
                    mInputBridge.sendMouseButton(comboKey, false, mCenterX, mCenterY);
                }
                // 手柄触发器（范围: -221 到 -220）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_TRIGGER_RIGHT && comboKey <= ControlData.XBOX_TRIGGER_LEFT) {
                    // 触发器：使用 sendXboxTrigger，值为 0.0 表示释放
                    mInputBridge.sendXboxTrigger(comboKey, 0.0f);
                }
                // 手柄按钮（范围: -200 到 -214）- 只在非鼠标模式下发送
                else if (mData.joystickMode != ControlData.JOYSTICK_MODE_MOUSE &&
                         comboKey >= ControlData.XBOX_BUTTON_DPAD_RIGHT && comboKey <= ControlData.XBOX_BUTTON_A) {
                    // 普通按钮：使用 sendXboxButton
                    mInputBridge.sendXboxButton(comboKey, false);
                }
            }
        }
    }
    
    /**
     * 右摇杆专用：发送组合键（使用八方向位置）
     * 鼠标左键使用虚拟触屏实现，不影响正常触屏控制
     */
    private void pressComboKeysForRightStick(int direction) {
        if (direction == DIR_NONE) return;
        
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            // 计算当前方向对应的屏幕位置
            float[] pos = calculateDirectionPosition(direction);
            
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, true);
                }
                // 鼠标左键：使用虚拟触屏（游戏使用触屏控制）
                else if (comboKey == ControlData.MOUSE_LEFT) {
                    if (mInputBridge instanceof SDLInputBridge) {
                        ((SDLInputBridge) mInputBridge).sendVirtualTouch(
                            SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, pos[0], pos[1], true);
                    }
                }
                // 其他鼠标按键：使用SDL鼠标
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_RIGHT) {
                    mInputBridge.sendMouseButton(comboKey, true, pos[0], pos[1]);
                }
            }
        }
    }
    
    /**
     * 右摇杆专用：释放组合键
     */
    private void releaseComboKeysForRightStick(int direction) {
        if (direction == DIR_NONE) return;
        
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            // 计算当前方向对应的屏幕位置
            float[] pos = calculateDirectionPosition(direction);
            
            for (int comboKey : mData.joystickComboKeys) {
                // 键盘按键（正数，SDL scancode）
                if (comboKey > 0) {
                    mInputBridge.sendKey(comboKey, false);
                }
                // 鼠标左键：使用虚拟触屏释放
                else if (comboKey == ControlData.MOUSE_LEFT) {
                    if (mInputBridge instanceof SDLInputBridge) {
                        ((SDLInputBridge) mInputBridge).sendVirtualTouch(
                            SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, pos[0], pos[1], false);
                    }
                }
                // 其他鼠标按键：使用SDL鼠标
                else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_RIGHT) {
                    mInputBridge.sendMouseButton(comboKey, false, pos[0], pos[1]);
                }
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
        releaseComboKeysForRightStick(mCurrentDirection);
    }
    
    /**
     * 更新攻击方向（方向改变时调用，用于持续攻击模式）
     * 会自动释放旧方向的虚拟触屏点并按下新方向
     */
    private void updateAttackDirection(int oldDirection, int newDirection) {
        // 释放旧方向
        if (oldDirection != DIR_NONE) {
            releaseComboKeysForRightStick(oldDirection);
        }
        // 按下新方向
        if (newDirection != DIR_NONE) {
            pressComboKeysForRightStick(newDirection);
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
        releaseComboKeysForRightStick(mCurrentDirection);
    }
    
    /**
     * 按下组合键（鼠标移动模式，跟随光标位置）
     */
    private void pressComboKeysForMouseMove() {
        if (mData.joystickComboKeys == null || mData.joystickComboKeys.length == 0) return;
        
        for (int comboKey : mData.joystickComboKeys) {
            // 键盘按键
            if (comboKey > 0) {
                mInputBridge.sendKey(comboKey, true);
            }
            // 鼠标左键：使用虚拟触屏（游戏使用触屏控制）
            else if (comboKey == ControlData.MOUSE_LEFT) {
                if (mInputBridge instanceof SDLInputBridge) {
                    SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
                    // 使用虚拟鼠标当前位置作为触屏位置
                    float touchX = bridge.getVirtualMouseX();
                    float touchY = bridge.getVirtualMouseY();
                    bridge.sendVirtualTouch(SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, touchX, touchY, true);
                }
            }
            // 其他鼠标按键：使用SDL鼠标
            else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_RIGHT) {
                mInputBridge.sendMouseButton(comboKey, true, mScreenWidth / 2.0f, mScreenHeight / 2.0f);
            }
        }
    }
    
    /**
     * 释放组合键（鼠标移动模式）
     */
    private void releaseComboKeysForMouseMove() {
        if (mData.joystickComboKeys == null || mData.joystickComboKeys.length == 0) return;
        
        for (int comboKey : mData.joystickComboKeys) {
            // 键盘按键
            if (comboKey > 0) {
                mInputBridge.sendKey(comboKey, false);
            }
            // 鼠标左键：释放虚拟触屏
            else if (comboKey == ControlData.MOUSE_LEFT) {
                if (mInputBridge instanceof SDLInputBridge) {
                    SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
                    float touchX = bridge.getVirtualMouseX();
                    float touchY = bridge.getVirtualMouseY();
                    bridge.sendVirtualTouch(SDLInputBridge.VIRTUAL_TOUCH_RIGHT_STICK, touchX, touchY, false);
                }
            }
            // 其他鼠标按键
            else if (comboKey >= ControlData.MOUSE_MIDDLE && comboKey <= ControlData.MOUSE_RIGHT) {
                mInputBridge.sendMouseButton(comboKey, false, mScreenWidth / 2.0f, mScreenHeight / 2.0f);
            }
        }
    }
    
    /**
     * 执行一次点击攻击（按下然后短暂后释放）
     */
    private void performClickAttack(int direction) {
        if (direction == DIR_NONE) return;
        
        if (mData.joystickComboKeys != null && mData.joystickComboKeys.length > 0) {
            float[] pos = calculateDirectionPosition(direction);
            
            for (int comboKey : mData.joystickComboKeys) {
                // 鼠标左键：触发点击（按下）
                if (comboKey == ControlData.MOUSE_LEFT) {
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
            }
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
     * 通过 touch_bridge 更新虚拟鼠标位置，C# 可以读取
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
        
        // 如果位置变化不够大，不移动鼠标（需要拖动更远才触发）
        if (deltaDistance < JOYSTICK_MOVE_THRESHOLD) {
            return;
        }
        
        // 保存当前位置供下一帧使用（只有超过阈值才更新）
        mLastJoystickDx = dx;
        mLastJoystickDy = dy;
        
        // 标准化变化量
        float maxDistance = mRadius - mStickRadius;
        float normalizedDeltaX = deltaDx / maxDistance;
        float normalizedDeltaY = deltaDy / maxDistance;
        
        // 应用灵敏度系数（使用用户配置的速度，默认30）
        float sensitivity = (mData.mouseSpeed > 0) ? mData.mouseSpeed : 30.0f;
        float mouseX = normalizedDeltaX * sensitivity;
        float mouseY = normalizedDeltaY * sensitivity;
        
        // 发送虚拟鼠标相对移动
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            bridge.updateVirtualMouseDelta(mouseX, mouseY);
        }
    }
    
    /**
     * 启用虚拟鼠标（右摇杆鼠标移动模式）
     */
    private void enableVirtualMouse() {
        if (mInputBridge instanceof SDLInputBridge) {
            SDLInputBridge bridge = (SDLInputBridge) mInputBridge;
            // 先设置鼠标移动范围（从配置读取）
            float left = mData.mouseRangeLeft;
            float top = mData.mouseRangeTop;
            float right = mData.mouseRangeRight;
            float bottom = mData.mouseRangeBottom;
            
            Log.i(TAG, "enableVirtualMouse: config range=(" + left + "," + top + "," + right + "," + bottom + 
                  "), speed=" + mData.mouseSpeed);
            
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

