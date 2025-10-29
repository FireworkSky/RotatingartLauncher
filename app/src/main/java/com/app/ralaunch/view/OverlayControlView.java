package com.app.ralaunch.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.model.ControlLayout;

import java.util.*;

/**
 * 游戏内控制覆盖层视图
 * 这是游戏运行时实际显示和响应的虚拟控制器
 */
public class OverlayControlView extends View {

    private ControlLayout controlLayout;
    private Map<Integer, ActiveTouch> activeTouches;  // 当前活跃的触摸
    private Set<ControlElement> pressedElements;      // 当前按下的元素
    private Paint elementPaint, pressedPaint, textPaint, joystickPaint;

    // 回调接口
    public interface OnControlEventListener {
        void onButtonDown(ControlElement element);
        void onButtonUp(ControlElement element);
        void onJoystickMove(ControlElement element, float deltaX, float deltaY);
        void onMouseMove(float deltaX, float deltaY);
        void onMouseScroll(float deltaX, float deltaY);
    }

    private OnControlEventListener eventListener;

    // 活跃触摸信息
    private static class ActiveTouch {
        int pointerId;
        ControlElement element;
        float startX, startY;
        float currentX, currentY;
        long startTime;
        boolean isRepeating;

        ActiveTouch(int pointerId, ControlElement element, float x, float y) {
            this.pointerId = pointerId;
            this.element = element;
            this.startX = x;
            this.startY = y;
            this.currentX = x;
            this.currentY = y;
            this.startTime = System.currentTimeMillis();
        }
    }

    public OverlayControlView(Context context) {
        super(context);
        init();
    }

    public OverlayControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        activeTouches = new HashMap<>();
        pressedElements = new HashSet<>();

        elementPaint = new Paint();
        elementPaint.setAntiAlias(true);
        elementPaint.setStyle(Paint.Style.FILL);

        pressedPaint = new Paint();
        pressedPaint.setAntiAlias(true);
        pressedPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        joystickPaint = new Paint();
        joystickPaint.setAntiAlias(true);
        joystickPaint.setStyle(Paint.Style.FILL);
    }

    public void setControlLayout(ControlLayout layout) {
        this.controlLayout = layout;
        invalidate();
    }

    public void setOnControlEventListener(OnControlEventListener listener) {
        this.eventListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (controlLayout == null) return;

        int width = getWidth();
        int height = getHeight();

        // 绘制所有可见的控制元素
        for (ControlElement element : controlLayout.getElements()) {
            // 检查可见性
            if (element.getVisibility() == ControlElement.Visibility.HIDDEN) {
                continue;
            }

            boolean isPressed = pressedElements.contains(element);

            switch (element.getType()) {
                case BUTTON:
                case TRIGGER_BUTTON:
                case MACRO_BUTTON:
                    drawButton(canvas, element, isPressed, width, height);
                    break;

                case JOYSTICK:
                    drawJoystick(canvas, element, width, height);
                    break;

                case CROSS_KEY:
                    drawCrossKey(canvas, element, isPressed, width, height);
                    break;

                case TOUCHPAD:
                case MOUSE_AREA:
                    drawTouchpad(canvas, element, isPressed, width, height);
                    break;
            }
        }
    }

    private void drawButton(Canvas canvas, ControlElement element, boolean isPressed, int width, int height) {
        RectF rect = getElementRect(element, width, height);

        // 设置颜色和透明度
        int color = isPressed ? element.getPressedColor() : element.getBackgroundColor();
        int alpha = (int) (element.getOpacity() * 255);
        elementPaint.setColor(color);
        elementPaint.setAlpha(alpha);

        // 绘制背景
        canvas.drawRoundRect(rect, element.getCornerRadius(), element.getCornerRadius(), elementPaint);

        // 绘制边框
        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(element.getBorderColor());
        borderPaint.setStrokeWidth(element.getBorderWidth());
        borderPaint.setAlpha(alpha);
        canvas.drawRoundRect(rect, element.getCornerRadius(), element.getCornerRadius(), borderPaint);

        // 绘制文本
        textPaint.setColor(element.getTextColor());
        textPaint.setTextSize(element.getTextSize() * getResources().getDisplayMetrics().density);
        textPaint.setAlpha(alpha);

        String text = element.getDisplayText() != null ? element.getDisplayText() : element.getName();
        float centerX = rect.centerX();
        float centerY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(text, centerX, centerY, textPaint);
    }

    private void drawJoystick(Canvas canvas, ControlElement element, int width, int height) {
        RectF rect = getElementRect(element, width, height);
<<<<<<< Updated upstream
        
        // 使用最小边作为直径，确保摇杆是圆形的
        float diameter = Math.min(rect.width(), rect.height());
        float radius = diameter / 2;
        
=======

        // 使用最小边作为直径，确保摇杆是圆形的
        float diameter = Math.min(rect.width(), rect.height());
        float radius = diameter / 2;

>>>>>>> Stashed changes
        // 绘制外圈
        int alpha = (int) (element.getOpacity() * 255);
        elementPaint.setColor(element.getBackgroundColor());
        elementPaint.setAlpha(alpha);
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, elementPaint);
<<<<<<< Updated upstream
        
=======

>>>>>>> Stashed changes
        // 绘制边框
        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(element.getBorderColor());
        borderPaint.setStrokeWidth(element.getBorderWidth());
        borderPaint.setAlpha(alpha);
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, borderPaint);
<<<<<<< Updated upstream
        
=======

>>>>>>> Stashed changes
        // 查找活跃触摸
        ActiveTouch activeTouch = findActiveTouchForElement(element);
        float knobX = rect.centerX();
        float knobY = rect.centerY();

        if (activeTouch != null) {
            // 计算摇杆偏移
            float deltaX = activeTouch.currentX - rect.centerX();
            float deltaY = activeTouch.currentY - rect.centerY();
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            float maxDistance = radius * 0.7f;  // 70% 半径
<<<<<<< Updated upstream
            
=======

>>>>>>> Stashed changes
            if (distance > maxDistance) {
                float scale = maxDistance / distance;
                deltaX *= scale;
                deltaY *= scale;
            }

            knobX = rect.centerX() + deltaX;
            knobY = rect.centerY() + deltaY;
        }

        // 绘制内圈（摇杆）
        joystickPaint.setColor(element.getPressedColor());
        joystickPaint.setAlpha(alpha);
        canvas.drawCircle(knobX, knobY, radius / 2, joystickPaint);
    }

    private void drawCrossKey(Canvas canvas, ControlElement element, boolean isPressed, int width, int height) {
        // 十字键绘制（简化版，可以扩展为4个独立按钮）
        drawButton(canvas, element, isPressed, width, height);

        RectF rect = getElementRect(element, width, height);
        textPaint.setColor(element.getTextColor());
        textPaint.setTextSize(element.getTextSize() * getResources().getDisplayMetrics().density);

        // 绘制十字标记
        canvas.drawText("↑", rect.centerX(), rect.top + 30, textPaint);
        canvas.drawText("↓", rect.centerX(), rect.bottom - 10, textPaint);
        canvas.drawText("←", rect.left + 20, rect.centerY(), textPaint);
        canvas.drawText("→", rect.right - 20, rect.centerY(), textPaint);
    }

    private void drawTouchpad(Canvas canvas, ControlElement element, boolean isPressed, int width, int height) {
        RectF rect = getElementRect(element, width, height);

        // 绘制半透明背景
        int alpha = (int) (element.getOpacity() * 0.5f * 255);
        elementPaint.setColor(element.getBackgroundColor());
        elementPaint.setAlpha(alpha);
        canvas.drawRoundRect(rect, element.getCornerRadius(), element.getCornerRadius(), elementPaint);

        // 绘制边框
        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(element.getBorderColor());
        borderPaint.setStrokeWidth(element.getBorderWidth());
        borderPaint.setAlpha(alpha);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));  // 虚线边框
        canvas.drawRoundRect(rect, element.getCornerRadius(), element.getCornerRadius(), borderPaint);
    }

    private RectF getElementRect(ControlElement element, int width, int height) {
        float left = element.getX() * width;
        float top = element.getY() * height;
        float right = left + element.getWidth();
        float bottom = top + element.getHeight();
        return new RectF(left, top, right, bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (controlLayout == null) return false;

        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleTouchDown(pointerId, x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    float moveX = event.getX(i);
                    float moveY = event.getY(i);
                    handleTouchMove(id, moveX, moveY);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleTouchUp(pointerId);
                break;
        }

        invalidate();
        return true;
    }

    private void handleTouchDown(int pointerId, float x, float y) {
        ControlElement element = findElementAt(x, y);
        if (element == null) return;

        ActiveTouch touch = new ActiveTouch(pointerId, element, x, y);
        activeTouches.put(pointerId, touch);

        switch (element.getType()) {
            case BUTTON:
            case TRIGGER_BUTTON:
            case CROSS_KEY:
            case MACRO_BUTTON:
                pressedElements.add(element);
                if (eventListener != null) {
                    eventListener.onButtonDown(element);
                }
                break;

            case JOYSTICK:
                // 摇杆开始移动在 onMove 中处理
                break;

            case TOUCHPAD:
            case MOUSE_AREA:
                // 触摸板开始在 onMove 中处理
                break;
        }
    }

    private void handleTouchMove(int pointerId, float x, float y) {
        ActiveTouch touch = activeTouches.get(pointerId);
        if (touch == null) return;

        touch.currentX = x;
        touch.currentY = y;

        ControlElement element = touch.element;

        switch (element.getType()) {
            case JOYSTICK:
                handleJoystickMove(touch);
                break;

            case TOUCHPAD:
            case MOUSE_AREA:
                handleTouchpadMove(touch);
                break;
        }
    }

    private void handleTouchUp(int pointerId) {
        ActiveTouch touch = activeTouches.remove(pointerId);
        if (touch == null) return;

        ControlElement element = touch.element;

        switch (element.getType()) {
            case BUTTON:
            case TRIGGER_BUTTON:
            case CROSS_KEY:
            case MACRO_BUTTON:
                pressedElements.remove(element);
                if (eventListener != null) {
                    eventListener.onButtonUp(element);
                }
                break;

            case JOYSTICK:
                // 摇杆回中
                if (eventListener != null) {
                    eventListener.onJoystickMove(element, 0, 0);
                }
                break;
        }
    }

    private void handleJoystickMove(ActiveTouch touch) {
        if (eventListener == null) return;

        ControlElement element = touch.element;
        RectF rect = getElementRect(element, getWidth(), getHeight());

        float deltaX = touch.currentX - rect.centerX();
        float deltaY = touch.currentY - rect.centerY();
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float maxDistance = rect.width() / 2;

        // 应用死区
        if (distance < maxDistance * element.getDeadzone()) {
            eventListener.onJoystickMove(element, 0, 0);
            return;
        }

        // 归一化到 -1 到 1
        float normalizedX = (deltaX / maxDistance) * element.getSensitivity();
        float normalizedY = (deltaY / maxDistance) * element.getSensitivity();

        // 限制范围
        normalizedX = Math.max(-1, Math.min(1, normalizedX));
        normalizedY = Math.max(-1, Math.min(1, normalizedY));

        eventListener.onJoystickMove(element, normalizedX, normalizedY);
    }

    private void handleTouchpadMove(ActiveTouch touch) {
        if (eventListener == null) return;

        ControlElement element = touch.element;
        float deltaX = touch.currentX - touch.startX;
        float deltaY = touch.currentY - touch.startY;

        // 应用灵敏度
        deltaX *= element.getScrollSensitivity();
        deltaY *= element.getScrollSensitivity();

        // 应用反转
        if (element.isInvertX()) deltaX = -deltaX;
        if (element.isInvertY()) deltaY = -deltaY;

        if (element.getType() == ControlElement.ElementType.TOUCHPAD) {
            eventListener.onMouseScroll(deltaX, deltaY);
        } else {
            eventListener.onMouseMove(deltaX, deltaY);
        }

        // 重置起点，实现相对移动
        touch.startX = touch.currentX;
        touch.startY = touch.currentY;
    }

    private ControlElement findElementAt(float x, float y) {
        int width = getWidth();
        int height = getHeight();

        // 从后往前遍历（后面的元素在上层）
        List<ControlElement> elements = controlLayout.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            ControlElement element = elements.get(i);

            if (element.getVisibility() == ControlElement.Visibility.HIDDEN) {
                continue;
            }

            RectF rect = getElementRect(element, width, height);
            if (rect.contains(x, y)) {
                return element;
            }
        }

        return null;
    }

    private ActiveTouch findActiveTouchForElement(ControlElement element) {
        for (ActiveTouch touch : activeTouches.values()) {
            if (touch.element == element) {
                return touch;
            }
        }
        return null;
    }

    // 清理所有触摸状态
    public void resetAllControls() {
        activeTouches.clear();
        pressedElements.clear();
        invalidate();
    }
}