package com.app.ralaunch.controls.editor;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;

/**
 * MD3风格的键值选择对话框
 * 根据模式显示键盘按键或手柄按键的网格布局
 */
public class KeySelectorDialog extends Dialog {
    private final boolean isGamepadMode;
    private OnKeySelectedListener listener;

    public interface OnKeySelectedListener {
        void onKeySelected(int keycode, String keyName);
    }

    public KeySelectorDialog(@NonNull Context context, boolean isGamepadMode) {
        super(context);
        this.isGamepadMode = isGamepadMode;
    }

    public void setOnKeySelectedListener(OnKeySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 创建主布局
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.WHITE);
        mainLayout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

        // 创建圆角背景
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dpToPx(28));
        mainLayout.setBackground(background);

        // 标题
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(isGamepadMode ? "选择手柄按键" : "选择按键");
        tvTitle.setTextSize(24);
        tvTitle.setTextColor(Color.parseColor("#1C1B1F"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dpToPx(16);
        mainLayout.addView(tvTitle, titleParams);

        // 滚动容器
        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        scrollView.setLayoutParams(scrollParams);

        // 内容容器
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout);

        // 根据模式添加按键
        if (isGamepadMode) {
            addGamepadKeys(contentLayout);
        } else {
            addKeyboardKeys(contentLayout);
        }

        mainLayout.addView(scrollView);

        // 取消按钮
        Button btnCancel = new Button(getContext());
        btnCancel.setText("取消");
        btnCancel.setTextSize(16);
        btnCancel.setTextColor(Color.parseColor("#6750A4"));
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(48)
        );
        cancelParams.topMargin = dpToPx(16);
        btnCancel.setOnClickListener(v -> dismiss());
        mainLayout.addView(btnCancel, cancelParams);

        setContentView(mainLayout);

        // 设置对话框窗口属性
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.85),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
        }
    }

    /**
     * 添加键盘按键
     */
    private void addKeyboardKeys(LinearLayout container) {
        // 功能键
        addKeySection(container, "功能键", new KeyItem[]{
            new KeyItem("ESC", ControlData.SDL_SCANCODE_ESCAPE),
            new KeyItem("Tab", 43),
            new KeyItem("空格", ControlData.SDL_SCANCODE_SPACE),
            new KeyItem("回车", ControlData.SDL_SCANCODE_RETURN),
            new KeyItem("Shift", ControlData.SDL_SCANCODE_LSHIFT),
            new KeyItem("Ctrl", ControlData.SDL_SCANCODE_LCTRL)
        }, 3);

        // 字母键
        addKeySection(container, "字母键", new KeyItem[]{
            new KeyItem("W", ControlData.SDL_SCANCODE_W),
            new KeyItem("A", ControlData.SDL_SCANCODE_A),
            new KeyItem("S", ControlData.SDL_SCANCODE_S),
            new KeyItem("D", ControlData.SDL_SCANCODE_D),
            new KeyItem("E", ControlData.SDL_SCANCODE_E),
            new KeyItem("H", ControlData.SDL_SCANCODE_H),
            new KeyItem("Q", 20),
            new KeyItem("R", 21),
            new KeyItem("F", 9)
        }, 3);

        // 数字键
        addKeySection(container, "数字键", new KeyItem[]{
            new KeyItem("1", 30),
            new KeyItem("2", 31),
            new KeyItem("3", 32),
            new KeyItem("4", 33),
            new KeyItem("5", 34),
            new KeyItem("6", 35),
            new KeyItem("7", 36),
            new KeyItem("8", 37),
            new KeyItem("9", 38)
        }, 3);

        // 方向键
        addKeySection(container, "方向键", new KeyItem[]{
            new KeyItem("↑", 82),
            new KeyItem("↓", 81),
            new KeyItem("←", 80),
            new KeyItem("→", 79)
        }, 4);

        // 鼠标按键
        addKeySection(container, "鼠标", new KeyItem[]{
            new KeyItem("左键", ControlData.MOUSE_LEFT),
            new KeyItem("右键", ControlData.MOUSE_RIGHT),
            new KeyItem("中键", ControlData.MOUSE_MIDDLE)
        }, 3);
    }

    /**
     * 添加手柄按键
     */
    private void addGamepadKeys(LinearLayout container) {
        // 主按钮
        addKeySection(container, "主按钮", new KeyItem[]{
            new KeyItem("A", ControlData.XBOX_BUTTON_A),
            new KeyItem("B", ControlData.XBOX_BUTTON_B),
            new KeyItem("X", ControlData.XBOX_BUTTON_X),
            new KeyItem("Y", ControlData.XBOX_BUTTON_Y)
        }, 4);

        // 肩键和扳机
        addKeySection(container, "肩键/扳机", new KeyItem[]{
            new KeyItem("LB", ControlData.XBOX_BUTTON_LB),
            new KeyItem("RB", ControlData.XBOX_BUTTON_RB),
            new KeyItem("LT", ControlData.XBOX_TRIGGER_LEFT),
            new KeyItem("RT", ControlData.XBOX_TRIGGER_RIGHT)
        }, 2);

        // 摇杆按键
        addKeySection(container, "摇杆", new KeyItem[]{
            new KeyItem("L3", ControlData.XBOX_BUTTON_LEFT_STICK),
            new KeyItem("R3", ControlData.XBOX_BUTTON_RIGHT_STICK)
        }, 2);

        // 十字键
        addKeySection(container, "十字键", new KeyItem[]{
            new KeyItem("D-Pad ↑", ControlData.XBOX_BUTTON_DPAD_UP),
            new KeyItem("D-Pad ↓", ControlData.XBOX_BUTTON_DPAD_DOWN),
            new KeyItem("D-Pad ←", ControlData.XBOX_BUTTON_DPAD_LEFT),
            new KeyItem("D-Pad →", ControlData.XBOX_BUTTON_DPAD_RIGHT)
        }, 2);

        // 系统按键
        addKeySection(container, "系统", new KeyItem[]{
            new KeyItem("Start", ControlData.XBOX_BUTTON_START),
            new KeyItem("Back", ControlData.XBOX_BUTTON_BACK),
            new KeyItem("Guide", ControlData.XBOX_BUTTON_GUIDE)
        }, 3);
    }

    /**
     * 添加按键分组
     */
    private void addKeySection(LinearLayout container, String title, KeyItem[] keys, int columns) {
        Context context = getContext();

        // 分组标题
        TextView tvSectionTitle = new TextView(context);
        tvSectionTitle.setText(title);
        tvSectionTitle.setTextSize(14);
        tvSectionTitle.setTextColor(Color.parseColor("#49454F"));
        tvSectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dpToPx(16);
        titleParams.bottomMargin = dpToPx(8);
        container.addView(tvSectionTitle, titleParams);

        // 网格布局
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setColumnCount(columns);
        gridLayout.setRowCount((int) Math.ceil(keys.length / (double) columns));

        for (KeyItem key : keys) {
            Button btnKey = createKeyButton(key);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(56);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            gridLayout.addView(btnKey, params);
        }

        container.addView(gridLayout);
    }

    /**
     * 创建按键按钮
     */
    private Button createKeyButton(KeyItem key) {
        Button button = new Button(getContext());
        button.setText(key.name);
        button.setTextSize(14);
        button.setTextColor(Color.parseColor("#1C1B1F"));
        button.setAllCaps(false);

        // MD3风格的圆角背景
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#E8DEF8"));
        background.setCornerRadius(dpToPx(12));
        button.setBackground(background);

        // 点击事件
        button.setOnClickListener(v -> {
            if (listener != null) {
                listener.onKeySelected(key.keycode, key.name);
            }
            dismiss();
        });

        return button;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * 按键项数据类
     */
    private static class KeyItem {
        String name;
        int keycode;

        KeyItem(String name, int keycode) {
            this.name = name;
            this.keycode = keycode;
        }
    }
}
