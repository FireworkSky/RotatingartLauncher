package com.app.ralaunch.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

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

    // 死区（防止漂移） - 优化为更小的死区提升灵敏度
    private static final float DEADZONE_PERCENT = 0.12f;
    
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

        // 设置透明背景，确保只显示绘制的圆形
        setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // 禁用裁剪，让方向指示线可以完整显示
        setClipToOutline(false);
        setClipBounds(null);

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
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 检查触摸点是否在圆形区域内
                float dx = touchX - mCenterX;
                float dy = touchY - mCenterY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                // 只响应圆形区域内的触摸
                if (distance > mRadius) {
                    return false;
                }

                handleMove(touchX, touchY);
                mIsTouching = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mIsTouching) {
                    handleMove(touchX, touchY);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTouching) {
                    handleRelease();
                    mIsTouching = false;
                    return true;
                }
                return false;
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
            // 鼠标移动模式：将摇杆偏移量转换为鼠标移动
            sendMouseMove(dx, dy, distance);
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
        
        // 根据模式执行不同的释放操作
        if (mData.joystickMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
            // 键盘模式：释放按键
            releaseDirection(mCurrentDirection);
            mCurrentDirection = DIR_NONE;
        } else if (mData.joystickMode == ControlData.JOYSTICK_MODE_SDL_CONTROLLER) {
            // SDL控制器模式：摇杆回中
            if (mData.xboxUseRightStick) {
                mInputBridge.sendXboxRightStick(0.0f, 0.0f);
            } else {
                mInputBridge.sendXboxLeftStick(0.0f, 0.0f);
            }
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
     * joystickKeys数组：[上W, 右D, 下S, 左A]
     */
    private void pressDirection(int direction) {
        if (direction == DIR_NONE || mData.joystickKeys == null) return;
        
        String dirName = getDirectionName(direction);
        
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
    
    /**
     * 释放方向对应的按键
     */
    private void releaseDirection(int direction) {
        if (direction == DIR_NONE || mData.joystickKeys == null) return;
        
        String dirName = getDirectionName(direction);
        
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
    
    /**
     * 发送鼠标移动事件（鼠标模式）
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
}

