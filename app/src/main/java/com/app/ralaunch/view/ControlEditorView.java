// ControlEditorView.java
package com.app.ralaunch.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.app.ralaunch.fragment.ControlEditorFragment;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;

public class ControlEditorView extends View {
    private ControlLayout controlLayout;
    private ControlElement selectedElement;
    private Paint elementPaint, selectedPaint, borderPaint;
    private static final int LONG_PRESS_TIME = 500; // 长按时间阈值
    private long downTime = 0;
    private boolean isLongPress = false;
    private float scaleFactor = 1.0f;
    private ControlEditorFragment.OnElementLongPressListener longPressListener;

    public ControlEditorView(Context context) {
        super(context);
        init();
    }

    public ControlEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public void setOnElementLongPressListener(ControlEditorFragment.OnElementLongPressListener listener) {
        this.longPressListener = listener;
    }
    private void init() {
        elementPaint = new Paint();
        elementPaint.setAntiAlias(true);
        elementPaint.setStyle(Paint.Style.FILL);
        elementPaint.setColor(Color.argb(128, 255, 255, 255));

        selectedPaint = new Paint();
        selectedPaint.setAntiAlias(true);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setColor(Color.YELLOW);
        selectedPaint.setStrokeWidth(3);

        borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(2);
    }

    public void setControlLayout(ControlLayout layout) {
        this.controlLayout = layout;
        invalidate();
    }

    public void setSelectedElement(ControlElement element) {
        this.selectedElement = element;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (controlLayout == null) return;

        // 绘制网格背景
        drawGrid(canvas);

        // 绘制所有控制元素
        for (ControlElement element : controlLayout.getElements()) {
            drawElement(canvas, element);
        }

        // 绘制选中元素的边框
        if (selectedElement != null) {
            drawSelectionBorder(canvas, selectedElement);
        }
    }

    private void drawGrid(Canvas canvas) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.argb(50, 255, 255, 255));
        gridPaint.setStrokeWidth(1);

        int width = getWidth();
        int height = getHeight();
        int gridSize = 50;

        // 绘制垂直线
        for (int x = 0; x < width; x += gridSize) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }

        // 绘制水平线
        for (int y = 0; y < height; y += gridSize) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }
    }

    private void drawElement(Canvas canvas, ControlElement element) {
        float left = element.getX() * getWidth() / scaleFactor;
        float top = element.getY() * getHeight() / scaleFactor;
        float right = left + element.getWidth();
        float bottom = top + element.getHeight();

        RectF rect = new RectF(left, top, right, bottom);

        // 绘制背景
        elementPaint.setColor(element.getBackgroundColor());
        canvas.drawRoundRect(rect, 10, 10, elementPaint);

        // 绘制边框
        borderPaint.setColor(element.getBorderColor());
        borderPaint.setStrokeWidth(element.getBorderWidth());
        canvas.drawRoundRect(rect, 10, 10, borderPaint);

        // 绘制文本
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20);
        textPaint.setTextAlign(Paint.Align.CENTER);

        float textY = top + element.getHeight() / 2;
        canvas.drawText(element.getName(), left + element.getWidth() / 2, textY, textPaint);
    }

    private void drawSelectionBorder(Canvas canvas, ControlElement element) {
        float left = element.getX() * getWidth() / scaleFactor;
        float top = element.getY() * getHeight() / scaleFactor;
        float right = left + element.getWidth();
        float bottom = top + element.getHeight();

        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, 10, 10, selectedPaint);

        // 绘制调整手柄
        float handleSize = 20;
        canvas.drawCircle(left, top, handleSize, selectedPaint);
        canvas.drawCircle(right, top, handleSize, selectedPaint);
        canvas.drawCircle(left, bottom, handleSize, selectedPaint);
        canvas.drawCircle(right, bottom, handleSize, selectedPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (controlLayout == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis();
                isLongPress = false;
                // 检查是否点击了控制元素
                selectedElement = findElementAt(x, y);
                invalidate();
                // 如果选中了元素，则启动长按检查
                if (selectedElement != null) {
                    postDelayed(longPressRunnable, LONG_PRESS_TIME);
                }
                return selectedElement != null;

            case MotionEvent.ACTION_MOVE:
                // 如果移动距离过大，则取消长按
                if (selectedElement != null && !isLongPress) {
                    float dx = x - downX;
                    float dy = y - downY;
                    if (dx * dx + dy * dy > 100) { // 移动阈值
                        removeCallbacks(longPressRunnable);
                    }
                }
                if (selectedElement != null && !isLongPress) {
                    // 移动元素
                    selectedElement.setX(x / getWidth() * scaleFactor);
                    selectedElement.setY(y / getHeight() * scaleFactor);
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                if (selectedElement != null && !isLongPress) {
                    // 如果是点击，则只选中，不弹出对话框
                    // 这里我们什么都不做，因为已经在ACTION_DOWN中选中了
                }
                break;
        }

        return true;
    }
    private float downX, downY;

    // 长按运行器
    private Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            isLongPress = true;
            // 触发长按，弹出属性对话框
            if (selectedElement != null) {
                // 通过Fragment来显示属性对话框
                if (getContext() instanceof ControlEditorFragment.OnElementLongPressListener) {
                    ((ControlEditorFragment.OnElementLongPressListener) getContext()).onElementLongPress(selectedElement);
                }
            }
        }
    };
    private ControlElement findElementAt(float x, float y) {
        for (ControlElement element : controlLayout.getElements()) {
            float left = element.getX() * getWidth() / scaleFactor;
            float top = element.getY() * getHeight() / scaleFactor;
            float right = left + element.getWidth();
            float bottom = top + element.getHeight();

            if (x >= left && x <= right && y >= top && y <= bottom) {
                return element;
            }
        }
        return null;
    }
}