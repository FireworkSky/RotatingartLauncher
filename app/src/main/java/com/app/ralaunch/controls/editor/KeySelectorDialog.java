package com.app.ralaunch.controls.editor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.utils.LocalizedDialog;

/**
 * MD3风格的键值选择对话框
 * 支持在对话框内切换键盘按键和手柄按键模式
 */
public class KeySelectorDialog extends LocalizedDialog {
    private boolean isGamepadMode;
    private OnKeySelectedListener listener;
    private LinearLayout contentContainer;
    private com.google.android.material.button.MaterialButtonToggleGroup toggleGroup;
    private com.google.android.material.button.MaterialButton btnKeyboardMode;
    private com.google.android.material.button.MaterialButton btnGamepadMode;

    public interface OnKeySelectedListener {
        void onKeySelected(int keycode, String keyName);
    }

    public KeySelectorDialog(@NonNull Context context, boolean isGamepadMode) {
        super(context);
        this.isGamepadMode = isGamepadMode;
    }

    /**
     * 默认构造函数 - 默认显示键盘模式
     */
    public KeySelectorDialog(@NonNull Context context) {
        this(context, false);
    }

    public void setOnKeySelectedListener(OnKeySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 布局加载使用原始Context（包含主题），字符串资源使用getLocalizedContext()
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_key_selector, null);
        setContentView(view);

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 创建圆角背景
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dpToPx(28));
        view.setBackground(background);




        // 获取模式切换按钮组
        toggleGroup = view.findViewById(R.id.toggle_input_mode);
        btnKeyboardMode = view.findViewById(R.id.btn_keyboard_mode);
        btnGamepadMode = view.findViewById(R.id.btn_gamepad_mode);

        // 设置初始选中状态
        if (isGamepadMode) {
            toggleGroup.check(R.id.btn_gamepad_mode);
        } else {
            toggleGroup.check(R.id.btn_keyboard_mode);
        }

        // 获取内容容器
        contentContainer = view.findViewById(R.id.content_container);

        // 根据初始模式添加按键
        updateKeyLayout();

        // 监听模式切换
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_keyboard_mode) {
                    isGamepadMode = false;
                    updateKeyLayout();
                } else if (checkedId == R.id.btn_gamepad_mode) {
                    isGamepadMode = true;
                    updateKeyLayout();
                }
            }
        });

        // 设置对话框窗口属性
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,  // 自适应宽度
                ViewGroup.LayoutParams.WRAP_CONTENT   // 自适应高度
            );
            window.setGravity(Gravity.CENTER);
        }
    }

    /**
     * 添加键盘按键 - 从XML布局加载
     */
    private void addKeyboardKeys(LinearLayout container) {
        // 加载键盘布局（使用原始Context，因为布局需要主题资源）
        View keyboardLayout = LayoutInflater.from(getContext()).inflate(
            R.layout.layout_keyboard_selector, container, false);
        container.addView(keyboardLayout);

        // 绑定所有按键事件
        bindKeyboardKeys(keyboardLayout);
    }

    /**
     * 绑定键盘按键事件
     */
    private void bindKeyboardKeys(View layout) {
        // 第一行：功能键
        bindKey(layout, R.id.key_esc, "Esc", ControlData.SDL_SCANCODE_ESCAPE);
        bindKey(layout, R.id.key_f1, "F1", 58);
        bindKey(layout, R.id.key_f2, "F2", 59);
        bindKey(layout, R.id.key_f3, "F3", 60);
        bindKey(layout, R.id.key_f4, "F4", 61);
        bindKey(layout, R.id.key_f5, "F5", 62);
        bindKey(layout, R.id.key_f6, "F6", 63);
        bindKey(layout, R.id.key_f7, "F7", 64);
        bindKey(layout, R.id.key_f8, "F8", 65);
        bindKey(layout, R.id.key_f9, "F9", 66);
        bindKey(layout, R.id.key_f10, "F10", 67);
        bindKey(layout, R.id.key_f11, "F11", 68);
        bindKey(layout, R.id.key_f12, "F12", 69);
        bindKey(layout, R.id.key_prt, "Prt", 70);
        bindKey(layout, R.id.key_scr, "Scr", 71);

        // 第二行：数字行
        bindKey(layout, R.id.key_grave, "`", 53);
        bindKey(layout, R.id.key_1, "1", 30);
        bindKey(layout, R.id.key_2, "2", 31);
        bindKey(layout, R.id.key_3, "3", 32);
        bindKey(layout, R.id.key_4, "4", 33);
        bindKey(layout, R.id.key_5, "5", 34);
        bindKey(layout, R.id.key_6, "6", 35);
        bindKey(layout, R.id.key_7, "7", 36);
        bindKey(layout, R.id.key_8, "8", 37);
        bindKey(layout, R.id.key_9, "9", 38);
        bindKey(layout, R.id.key_0, "0", 39);
        bindKey(layout, R.id.key_minus, "-", 45);
        bindKey(layout, R.id.key_equals, "=", 46);
        bindKey(layout, R.id.key_backspace, "←", 42);
        bindKey(layout, R.id.key_ins, "Ins", 73);

        // 第三行：QWERTY
        bindKey(layout, R.id.key_tab, "Tab", 43);
        bindKey(layout, R.id.key_q, "Q", 20);
        bindKey(layout, R.id.key_w, "W", ControlData.SDL_SCANCODE_W);
        bindKey(layout, R.id.key_e, "E", ControlData.SDL_SCANCODE_E);
        bindKey(layout, R.id.key_r, "R", 21);
        bindKey(layout, R.id.key_t, "T", 23);
        bindKey(layout, R.id.key_y, "Y", 28);
        bindKey(layout, R.id.key_u, "U", 24);
        bindKey(layout, R.id.key_i, "I", 12);
        bindKey(layout, R.id.key_o, "O", 18);
        bindKey(layout, R.id.key_p, "P", 19);
        bindKey(layout, R.id.key_lbracket, "[", 47);
        bindKey(layout, R.id.key_rbracket, "]", 48);
        bindKey(layout, R.id.key_del, "Del", 76);

        // 第四行：ASDFGH
        bindKey(layout, R.id.key_capslock, "CapsLk", 57);
        bindKey(layout, R.id.key_a, "A", ControlData.SDL_SCANCODE_A);
        bindKey(layout, R.id.key_s, "S", ControlData.SDL_SCANCODE_S);
        bindKey(layout, R.id.key_d, "D", ControlData.SDL_SCANCODE_D);
        bindKey(layout, R.id.key_f, "F", 9);
        bindKey(layout, R.id.key_g, "G", 10);
        bindKey(layout, R.id.key_h, "H", ControlData.SDL_SCANCODE_H);
        bindKey(layout, R.id.key_j, "J", 13);
        bindKey(layout, R.id.key_k, "K", 14);
        bindKey(layout, R.id.key_l, "L", 15);
        bindKey(layout, R.id.key_semicolon, ";", 51);
        bindKey(layout, R.id.key_quote, "'", 52);
        bindKey(layout, R.id.key_enter, "Enter", ControlData.SDL_SCANCODE_RETURN);
        bindKey(layout, R.id.key_4num, "4", 92);

        // 第五行：ZXCVBN
        bindKey(layout, R.id.key_lshift, "Shift", ControlData.SDL_SCANCODE_LSHIFT);
        bindKey(layout, R.id.key_z, "Z", 29);
        bindKey(layout, R.id.key_x, "X", 27);
        bindKey(layout, R.id.key_c, "C", 6);
        bindKey(layout, R.id.key_v, "V", 25);
        bindKey(layout, R.id.key_b, "B", 5);
        bindKey(layout, R.id.key_n, "N", 17);
        bindKey(layout, R.id.key_m, "M", 16);
        bindKey(layout, R.id.key_comma, ",", 54);
        bindKey(layout, R.id.key_period, ".", 55);
        bindKey(layout, R.id.key_slash, "/", 56);
        bindKey(layout, R.id.key_rshift, "Shift", ControlData.SDL_SCANCODE_RSHIFT);
        bindKey(layout, R.id.key_up, "↑", 82);
        bindKey(layout, R.id.key_1num, "1", 89);

        // 第六行：空格行
        bindKey(layout, R.id.key_lctrl, "Ctrl", ControlData.SDL_SCANCODE_LCTRL);
        bindKey(layout, R.id.key_lwin, "Win", 227);
        bindKey(layout, R.id.key_lalt, "Alt", 226);
        bindKey(layout, R.id.key_space, "Space", ControlData.SDL_SCANCODE_SPACE);
        bindKey(layout, R.id.key_ralt, "Alt", 230);
        bindKey(layout, R.id.key_rwin, "Win", 231);
        bindKey(layout, R.id.key_menu, "Menu", 101);
        bindKey(layout, R.id.key_rctrl, "Ctrl", ControlData.SDL_SCANCODE_RCTRL);
        bindKey(layout, R.id.key_left, "←", 80);
        bindKey(layout, R.id.key_down, "↓", 81);
        bindKey(layout, R.id.key_right, "→", 79);
        bindKey(layout, R.id.key_0num, "0", 98);

        // 鼠标按键
        bindKey(layout, R.id.key_mouse_left, "LMB", ControlData.MOUSE_LEFT);
        bindKey(layout, R.id.key_mouse_right, "RMB", ControlData.MOUSE_RIGHT);
        bindKey(layout, R.id.key_mouse_middle, "MMB", ControlData.MOUSE_MIDDLE);
        bindKey(layout, R.id.key_mouse_wheel_up, "MW↑", ControlData.MOUSE_WHEEL_UP);
        bindKey(layout, R.id.key_mouse_wheel_down, "MW↓", ControlData.MOUSE_WHEEL_DOWN);
        
        // 弹出系统键盘按钮
        View btnShowIme = layout.findViewById(R.id.key_show_ime);
        if (btnShowIme != null) {
            bindKey(layout, R.id.key_show_ime, "键盘", ControlData.SPECIAL_KEYBOARD);
        }
        


    }

    /**
     * 添加手柄按键 - 从XML布局加载
     */
    private void addGamepadKeys(LinearLayout container) {
        // 加载手柄布局（使用原始Context，因为布局需要主题资源）
        View gamepadLayout = LayoutInflater.from(getContext()).inflate(
            R.layout.layout_gamepad_selector, container, false);
        container.addView(gamepadLayout);

        // 绑定所有按键事件
        bindGamepadKeys(gamepadLayout);
    }

    /**
     * 绑定手柄按键事件
     */
    private void bindGamepadKeys(View layout) {
        // 主按钮
        bindKey(layout, R.id.gamepad_a, "A", ControlData.XBOX_BUTTON_A);
        bindKey(layout, R.id.gamepad_b, "B", ControlData.XBOX_BUTTON_B);
        bindKey(layout, R.id.gamepad_x, "X", ControlData.XBOX_BUTTON_X);
        bindKey(layout, R.id.gamepad_y, "Y", ControlData.XBOX_BUTTON_Y);

        // 肩键和扳机
        bindKey(layout, R.id.gamepad_lb, "LB", ControlData.XBOX_BUTTON_LB);
        bindKey(layout, R.id.gamepad_rb, "RB", ControlData.XBOX_BUTTON_RB);
        bindKey(layout, R.id.gamepad_lt, "LT", ControlData.XBOX_TRIGGER_LEFT);
        bindKey(layout, R.id.gamepad_rt, "RT", ControlData.XBOX_TRIGGER_RIGHT);

        // 摇杆按键
        bindKey(layout, R.id.gamepad_l3, "L3", ControlData.XBOX_BUTTON_LEFT_STICK);
        bindKey(layout, R.id.gamepad_r3, "R3", ControlData.XBOX_BUTTON_RIGHT_STICK);

        // 十字键
        bindKey(layout, R.id.gamepad_dpad_up, "D-Pad ↑", ControlData.XBOX_BUTTON_DPAD_UP);
        bindKey(layout, R.id.gamepad_dpad_down, "D-Pad ↓", ControlData.XBOX_BUTTON_DPAD_DOWN);
        bindKey(layout, R.id.gamepad_dpad_left, "D-Pad ←", ControlData.XBOX_BUTTON_DPAD_LEFT);
        bindKey(layout, R.id.gamepad_dpad_right, "D-Pad →", ControlData.XBOX_BUTTON_DPAD_RIGHT);

        // 系统按键
        bindKey(layout, R.id.gamepad_start, "Start", ControlData.XBOX_BUTTON_START);
        bindKey(layout, R.id.gamepad_back, "Back", ControlData.XBOX_BUTTON_BACK);
        bindKey(layout, R.id.gamepad_guide, "Guide", ControlData.XBOX_BUTTON_GUIDE);
    }

    /**
     * 更新按键布局（切换键盘/手柄模式时调用）
     */
    private void updateKeyLayout() {
        // 清空当前内容
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            
            // 根据当前模式添加对应的按键布局
            if (isGamepadMode) {
                addGamepadKeys(contentContainer);
            } else {
                addKeyboardKeys(contentContainer);
            }
        }
    }

    /**
     * 绑定单个按键的点击事件
     */
    private void bindKey(View layout, int viewId, String keyName, int keycode) {
        View keyView = layout.findViewById(viewId);
        if (keyView != null) {
            keyView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onKeySelected(keycode, keyName);
                }
                dismiss();
            });
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}
