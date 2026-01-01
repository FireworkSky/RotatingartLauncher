package com.app.ralaunch.controls.editors

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.utils.LocalizedDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * MD3风格的键值选择对话框
 * 支持在对话框内切换键盘按键和手柄按键模式
 */
class KeySelectorDialog
/**
 * 默认构造函数 - 默认显示键盘模式
 */ @JvmOverloads constructor(context: Context, private var isGamepadMode: Boolean = false) :
    LocalizedDialog(context) {
    private var listener: OnKeySelectedListener? = null
    private var contentContainer: LinearLayout? = null
    private var toggleGroup: MaterialButtonToggleGroup? = null
    private var btnKeyboardMode: MaterialButton? = null
    private var btnGamepadMode: MaterialButton? = null

    interface OnKeySelectedListener {
        fun onKeySelected(keyCode: ControlData.KeyCode, keyName: String?)
    }

    fun setOnKeySelectedListener(listener: OnKeySelectedListener?) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // 布局加载使用原始Context（包含主题），字符串资源使用getLocalizedContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_key_selector, null)
        setContentView(view)

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 创建圆角背景
        val background = GradientDrawable()
        background.setColor(Color.WHITE)
        background.cornerRadius = dpToPx(28).toFloat()
        view.background = background


        // 获取模式切换按钮组
        toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_input_mode)
        btnKeyboardMode = view.findViewById<MaterialButton?>(R.id.btn_keyboard_mode)
        btnGamepadMode = view.findViewById<MaterialButton?>(R.id.btn_gamepad_mode)

        // 设置初始选中状态
        if (isGamepadMode) {
            toggleGroup!!.check(R.id.btn_gamepad_mode)
        } else {
            toggleGroup!!.check(R.id.btn_keyboard_mode)
        }

        // 获取内容容器
        contentContainer = view.findViewById<LinearLayout?>(R.id.content_container)

        // 根据初始模式添加按键
        updateKeyLayout()

        // 监听模式切换
        toggleGroup!!.addOnButtonCheckedListener { group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                if (checkedId == R.id.btn_keyboard_mode) {
                    isGamepadMode = false
                    updateKeyLayout()
                } else if (checkedId == R.id.btn_gamepad_mode) {
                    isGamepadMode = true
                    updateKeyLayout()
                }
            }
        }

        // 设置对话框窗口属性
        val window = getWindow()
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,  // 自适应宽度
                ViewGroup.LayoutParams.WRAP_CONTENT // 自适应高度
            )
            window.setGravity(Gravity.CENTER)
        }
    }

    /**
     * 添加键盘按键 - 从XML布局加载
     */
    private fun addKeyboardKeys(container: LinearLayout) {
        // 加载键盘布局（使用原始Context，因为布局需要主题资源）
        val keyboardLayout = LayoutInflater.from(context).inflate(
            R.layout.layout_keyboard_selector, container, false
        )
        container.addView(keyboardLayout)

        // 绑定所有按键事件
        bindKeyboardKeys(keyboardLayout)
    }

    /**
     * 绑定键盘按键事件
     */
    private fun bindKeyboardKeys(layout: View) {
        // 第一行：功能键
        bindKey(layout, R.id.key_esc, "Esc", ControlData.KeyCode.KEYBOARD_ESCAPE)
        bindKey(layout, R.id.key_f1, "F1", ControlData.KeyCode.KEYBOARD_F1)
        bindKey(layout, R.id.key_f2, "F2", ControlData.KeyCode.KEYBOARD_F2)
        bindKey(layout, R.id.key_f3, "F3", ControlData.KeyCode.KEYBOARD_F3)
        bindKey(layout, R.id.key_f4, "F4", ControlData.KeyCode.KEYBOARD_F4)
        bindKey(layout, R.id.key_f5, "F5", ControlData.KeyCode.KEYBOARD_F5)
        bindKey(layout, R.id.key_f6, "F6", ControlData.KeyCode.KEYBOARD_F6)
        bindKey(layout, R.id.key_f7, "F7", ControlData.KeyCode.KEYBOARD_F7)
        bindKey(layout, R.id.key_f8, "F8", ControlData.KeyCode.KEYBOARD_F8)
        bindKey(layout, R.id.key_f9, "F9", ControlData.KeyCode.KEYBOARD_F9)
        bindKey(layout, R.id.key_f10, "F10", ControlData.KeyCode.KEYBOARD_F10)
        bindKey(layout, R.id.key_f11, "F11", ControlData.KeyCode.KEYBOARD_F11)
        bindKey(layout, R.id.key_f12, "F12", ControlData.KeyCode.KEYBOARD_F12)
        bindKey(layout, R.id.key_prt, "Prt", ControlData.KeyCode.KEYBOARD_PRINTSCREEN)
        bindKey(layout, R.id.key_scr, "Scr", ControlData.KeyCode.KEYBOARD_SCROLLLOCK)
        bindKey(layout, R.id.key_pause, "Pau", ControlData.KeyCode.KEYBOARD_PAUSE)

        // 第二行：数字行
        bindKey(layout, R.id.key_grave, "`", ControlData.KeyCode.KEYBOARD_GRAVE)
        bindKey(layout, R.id.key_1, "1", ControlData.KeyCode.KEYBOARD_1)
        bindKey(layout, R.id.key_2, "2", ControlData.KeyCode.KEYBOARD_2)
        bindKey(layout, R.id.key_3, "3", ControlData.KeyCode.KEYBOARD_3)
        bindKey(layout, R.id.key_4, "4", ControlData.KeyCode.KEYBOARD_4)
        bindKey(layout, R.id.key_5, "5", ControlData.KeyCode.KEYBOARD_5)
        bindKey(layout, R.id.key_6, "6", ControlData.KeyCode.KEYBOARD_6)
        bindKey(layout, R.id.key_7, "7", ControlData.KeyCode.KEYBOARD_7)
        bindKey(layout, R.id.key_8, "8", ControlData.KeyCode.KEYBOARD_8)
        bindKey(layout, R.id.key_9, "9", ControlData.KeyCode.KEYBOARD_9)
        bindKey(layout, R.id.key_0, "0", ControlData.KeyCode.KEYBOARD_0)
        bindKey(layout, R.id.key_minus, "-", ControlData.KeyCode.KEYBOARD_MINUS)
        bindKey(layout, R.id.key_equals, "=", ControlData.KeyCode.KEYBOARD_EQUALS)
        bindKey(layout, R.id.key_backspace, "←", ControlData.KeyCode.KEYBOARD_BACKSPACE)
        bindKey(layout, R.id.key_ins, "Ins", ControlData.KeyCode.KEYBOARD_INSERT)
        bindKey(layout, R.id.key_home, "Hm", ControlData.KeyCode.KEYBOARD_HOME)
        bindKey(layout, R.id.key_pgup, "PU", ControlData.KeyCode.KEYBOARD_PAGEUP)

        // 第三行：QWERTY
        bindKey(layout, R.id.key_tab, "Tab", ControlData.KeyCode.KEYBOARD_TAB)
        bindKey(layout, R.id.key_q, "Q", ControlData.KeyCode.KEYBOARD_Q)
        bindKey(layout, R.id.key_w, "W", ControlData.KeyCode.KEYBOARD_W)
        bindKey(layout, R.id.key_e, "E", ControlData.KeyCode.KEYBOARD_E)
        bindKey(layout, R.id.key_r, "R", ControlData.KeyCode.KEYBOARD_R)
        bindKey(layout, R.id.key_t, "T", ControlData.KeyCode.KEYBOARD_T)
        bindKey(layout, R.id.key_y, "Y", ControlData.KeyCode.KEYBOARD_Y)
        bindKey(layout, R.id.key_u, "U", ControlData.KeyCode.KEYBOARD_U)
        bindKey(layout, R.id.key_i, "I", ControlData.KeyCode.KEYBOARD_I)
        bindKey(layout, R.id.key_o, "O", ControlData.KeyCode.KEYBOARD_O)
        bindKey(layout, R.id.key_p, "P", ControlData.KeyCode.KEYBOARD_P)
        bindKey(layout, R.id.key_lbracket, "[", ControlData.KeyCode.KEYBOARD_LEFTBRACKET)
        bindKey(layout, R.id.key_rbracket, "]", ControlData.KeyCode.KEYBOARD_RIGHTBRACKET)
        bindKey(layout, R.id.key_backslash, "\\", ControlData.KeyCode.KEYBOARD_BACKSLASH)
        bindKey(layout, R.id.key_del, "Del", ControlData.KeyCode.KEYBOARD_DELETE)
        bindKey(layout, R.id.key_end, "End", ControlData.KeyCode.KEYBOARD_END)
        bindKey(layout, R.id.key_pgdn, "PD", ControlData.KeyCode.KEYBOARD_PAGEDOWN)

        // 第四行：ASDFGH
        bindKey(layout, R.id.key_capslock, "CapsLk", ControlData.KeyCode.KEYBOARD_CAPSLOCK)
        bindKey(layout, R.id.key_a, "A", ControlData.KeyCode.KEYBOARD_A)
        bindKey(layout, R.id.key_s, "S", ControlData.KeyCode.KEYBOARD_S)
        bindKey(layout, R.id.key_d, "D", ControlData.KeyCode.KEYBOARD_D)
        bindKey(layout, R.id.key_f, "F", ControlData.KeyCode.KEYBOARD_F)
        bindKey(layout, R.id.key_g, "G", ControlData.KeyCode.KEYBOARD_G)
        bindKey(layout, R.id.key_h, "H", ControlData.KeyCode.KEYBOARD_H)
        bindKey(layout, R.id.key_j, "J", ControlData.KeyCode.KEYBOARD_J)
        bindKey(layout, R.id.key_k, "K", ControlData.KeyCode.KEYBOARD_K)
        bindKey(layout, R.id.key_l, "L", ControlData.KeyCode.KEYBOARD_L)
        bindKey(layout, R.id.key_semicolon, ";", ControlData.KeyCode.KEYBOARD_SEMICOLON)
        bindKey(layout, R.id.key_quote, "'", ControlData.KeyCode.KEYBOARD_APOSTROPHE)
        bindKey(layout, R.id.key_enter, "Enter", ControlData.KeyCode.KEYBOARD_RETURN)

        // 第五行：ZXCVBN
        bindKey(layout, R.id.key_lshift, "Shift", ControlData.KeyCode.KEYBOARD_LSHIFT)
        bindKey(layout, R.id.key_z, "Z", ControlData.KeyCode.KEYBOARD_Z)
        bindKey(layout, R.id.key_x, "X", ControlData.KeyCode.KEYBOARD_X)
        bindKey(layout, R.id.key_c, "C", ControlData.KeyCode.KEYBOARD_C)
        bindKey(layout, R.id.key_v, "V", ControlData.KeyCode.KEYBOARD_V)
        bindKey(layout, R.id.key_b, "B", ControlData.KeyCode.KEYBOARD_B)
        bindKey(layout, R.id.key_n, "N", ControlData.KeyCode.KEYBOARD_N)
        bindKey(layout, R.id.key_m, "M", ControlData.KeyCode.KEYBOARD_M)
        bindKey(layout, R.id.key_comma, ",", ControlData.KeyCode.KEYBOARD_COMMA)
        bindKey(layout, R.id.key_period, ".", ControlData.KeyCode.KEYBOARD_PERIOD)
        bindKey(layout, R.id.key_slash, "/", ControlData.KeyCode.KEYBOARD_SLASH)
        bindKey(layout, R.id.key_rshift, "Shift", ControlData.KeyCode.KEYBOARD_RSHIFT)
        bindKey(layout, R.id.key_up, "↑", ControlData.KeyCode.KEYBOARD_UP)

        // 第六行：空格行
        bindKey(layout, R.id.key_lctrl, "Ctrl", ControlData.KeyCode.KEYBOARD_LCTRL)
        bindKey(layout, R.id.key_lwin, "Win", ControlData.KeyCode.KEYBOARD_LGUI)
        bindKey(layout, R.id.key_lalt, "Alt", ControlData.KeyCode.KEYBOARD_LALT)
        bindKey(layout, R.id.key_space, "Space", ControlData.KeyCode.KEYBOARD_SPACE)
        bindKey(layout, R.id.key_ralt, "Alt", ControlData.KeyCode.KEYBOARD_RALT)
        bindKey(layout, R.id.key_rwin, "Win", ControlData.KeyCode.KEYBOARD_RGUI)
        bindKey(layout, R.id.key_menu, "Menu", ControlData.KeyCode.KEYBOARD_APPLICATION)
        bindKey(layout, R.id.key_rctrl, "Ctrl", ControlData.KeyCode.KEYBOARD_RCTRL)
        bindKey(layout, R.id.key_left, "←", ControlData.KeyCode.KEYBOARD_LEFT)
        bindKey(layout, R.id.key_down, "↓", ControlData.KeyCode.KEYBOARD_DOWN)
        bindKey(layout, R.id.key_right, "→", ControlData.KeyCode.KEYBOARD_RIGHT)

        // Numpad keys
        bindKey(layout, R.id.key_numlock, "Num", ControlData.KeyCode.KEYBOARD_NUMLOCKCLEAR)
        bindKey(layout, R.id.key_numdiv, "/", ControlData.KeyCode.KEYBOARD_KP_DIVIDE)
        bindKey(layout, R.id.key_nummul, "*", ControlData.KeyCode.KEYBOARD_KP_MULTIPLY)
        bindKey(layout, R.id.key_numsub, "-", ControlData.KeyCode.KEYBOARD_KP_MINUS)
        bindKey(layout, R.id.key_7num, "7", ControlData.KeyCode.KEYBOARD_KP_7)
        bindKey(layout, R.id.key_8num, "8", ControlData.KeyCode.KEYBOARD_KP_8)
        bindKey(layout, R.id.key_9num, "9", ControlData.KeyCode.KEYBOARD_KP_9)
        bindKey(layout, R.id.key_numadd, "+", ControlData.KeyCode.KEYBOARD_KP_PLUS)
        bindKey(layout, R.id.key_4num, "4", ControlData.KeyCode.KEYBOARD_KP_4)
        bindKey(layout, R.id.key_5num, "5", ControlData.KeyCode.KEYBOARD_KP_5)
        bindKey(layout, R.id.key_6num, "6", ControlData.KeyCode.KEYBOARD_KP_6)
        bindKey(layout, R.id.key_1num, "1", ControlData.KeyCode.KEYBOARD_KP_1)
        bindKey(layout, R.id.key_2num, "2", ControlData.KeyCode.KEYBOARD_KP_2)
        bindKey(layout, R.id.key_3num, "3", ControlData.KeyCode.KEYBOARD_KP_3)
        bindKey(layout, R.id.key_0num, "0", ControlData.KeyCode.KEYBOARD_KP_0)
        bindKey(layout, R.id.key_numdot, ".", ControlData.KeyCode.KEYBOARD_KP_PERIOD)
        bindKey(layout, R.id.key_numenter, "↵", ControlData.KeyCode.KEYBOARD_KP_ENTER)

        // 鼠标按键
        bindKey(layout, R.id.key_mouse_left, "LMB", ControlData.KeyCode.MOUSE_LEFT)
        bindKey(layout, R.id.key_mouse_right, "RMB", ControlData.KeyCode.MOUSE_RIGHT)
        bindKey(layout, R.id.key_mouse_middle, "MMB", ControlData.KeyCode.MOUSE_MIDDLE)
        bindKey(layout, R.id.key_mouse_wheel_up, "MW↑", ControlData.KeyCode.MOUSE_WHEEL_UP)
        bindKey(layout, R.id.key_mouse_wheel_down, "MW↓", ControlData.KeyCode.MOUSE_WHEEL_DOWN)

        // 弹出系统键盘按钮
        bindKey(layout, R.id.key_show_ime, localizedContext.getString(R.string.key_keyboard), ControlData.KeyCode.SPECIAL_KEYBOARD)
        // 触摸板左右键
        bindKey(layout, R.id.key_touchpad_button, localizedContext.getString(R.string.key_touchpad_buttons), ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON)
    }

    /**
     * 添加手柄按键 - 从XML布局加载
     */
    private fun addGamepadKeys(container: LinearLayout) {
        // 加载手柄布局（使用原始Context，因为布局需要主题资源）
        val gamepadLayout = LayoutInflater.from(context).inflate(
            R.layout.layout_gamepad_selector, container, false
        )
        container.addView(gamepadLayout)

        // 绑定所有按键事件
        bindGamepadKeys(gamepadLayout)
    }

    /**
     * 绑定手柄按键事件
     */
    private fun bindGamepadKeys(layout: View) {
        // 主按钮
        bindKey(layout, R.id.gamepad_a, "A", ControlData.KeyCode.XBOX_BUTTON_A)
        bindKey(layout, R.id.gamepad_b, "B", ControlData.KeyCode.XBOX_BUTTON_B)
        bindKey(layout, R.id.gamepad_x, "X", ControlData.KeyCode.XBOX_BUTTON_X)
        bindKey(layout, R.id.gamepad_y, "Y", ControlData.KeyCode.XBOX_BUTTON_Y)

        // 肩键和扳机
        bindKey(layout, R.id.gamepad_lb, "LB", ControlData.KeyCode.XBOX_BUTTON_LB)
        bindKey(layout, R.id.gamepad_rb, "RB", ControlData.KeyCode.XBOX_BUTTON_RB)
        bindKey(layout, R.id.gamepad_lt, "LT", ControlData.KeyCode.XBOX_TRIGGER_LEFT)
        bindKey(layout, R.id.gamepad_rt, "RT", ControlData.KeyCode.XBOX_TRIGGER_RIGHT)

        // 摇杆按键
        bindKey(layout, R.id.gamepad_l3, "L3", ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK)
        bindKey(layout, R.id.gamepad_r3, "R3", ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK)

        // 十字键
        bindKey(layout, R.id.gamepad_dpad_up, "D-Pad ↑", ControlData.KeyCode.XBOX_BUTTON_DPAD_UP)
        bindKey(layout, R.id.gamepad_dpad_down, "D-Pad ↓", ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN)
        bindKey(layout, R.id.gamepad_dpad_left, "D-Pad ←", ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT)
        bindKey(layout, R.id.gamepad_dpad_right, "D-Pad →", ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT)

        // 系统按键
        bindKey(layout, R.id.gamepad_start, "Start", ControlData.KeyCode.XBOX_BUTTON_START)
        bindKey(layout, R.id.gamepad_back, "Back", ControlData.KeyCode.XBOX_BUTTON_BACK)
        bindKey(layout, R.id.gamepad_guide, "Guide", ControlData.KeyCode.XBOX_BUTTON_GUIDE)
    }

    /**
     * 更新按键布局（切换键盘/手柄模式时调用）
     */
    private fun updateKeyLayout() {
        // 清空当前内容
        if (contentContainer != null) {
            contentContainer!!.removeAllViews()

            // 根据当前模式添加对应的按键布局
            if (isGamepadMode) {
                addGamepadKeys(contentContainer!!)
            } else {
                addKeyboardKeys(contentContainer!!)
            }
        }
    }

    /**
     * 绑定单个按键的点击事件
     */
    private fun bindKey(layout: View, viewId: Int, keyName: String?, keyCode: ControlData.KeyCode) {
        val keyView = layout.findViewById<View?>(viewId)
        keyView?.setOnClickListener { v: View? ->
            if (listener != null) {
                listener!!.onKeySelected(keyCode, keyName)
            }
            dismiss()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
