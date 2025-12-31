package com.app.ralaunch.controls.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import com.app.ralaunch.R
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.isVisible
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.bridges.ControlInputBridge

/**
 * 虚拟键盘视图
 * 显示完整的虚拟键盘布局，用于游戏中的键盘输入
 * 支持拖动和透明度调整
 */
class VirtualKeyboardView : FrameLayout {

    companion object {
        private const val TAG = "VirtualKeyboardView"

        private const val DRAG_THRESHOLD_DP = 10f // 拖动阈值（dp）
    }

    private var inputBridge: ControlInputBridge? = null
    private var keyboardLayout: View? = null
    private val pressedKeys: MutableMap<Int, ControlData.KeyCode> = HashMap()

    // 拖动相关
    private val lastTouchX = 0f
    private val lastTouchY = 0f
    private val initialTouchX = 0f
    private val initialTouchY = 0f
    private val initialTranslationX = 0f
    private val initialTranslationY = 0f
    private val isDragging = false

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        // 加载键盘布局
        keyboardLayout = LayoutInflater.from(context).inflate(
            R.layout.layout_keyboard_selector, this, true
        )


        // 设置透明度 (0.7 = 70% 不透明)
        alpha = 0.7f


        // 关键修复：确保VirtualKeyboardView能够接收触摸事件
        // 设置这些属性后，触摸事件才能正确传递到子视图（按键）
        isClickable = true
        setFocusable(false) // 不需要焦点，只需要接收触摸事件
        isFocusableInTouchMode = false


        // 绑定所有按键
        bindAllKeys()
    }

    fun setInputBridge(bridge: ControlInputBridge?) {
        this.inputBridge = bridge
        Log.d(TAG, "InputBridge set: " + (if (bridge != null) "Success" else "Null"))
    }

    private fun bindAllKeys() {
        // 第一行：功能键
        bindKey(R.id.key_esc, ControlData.KeyCode.KEYBOARD_ESCAPE)
        bindKey(R.id.key_f1, ControlData.KeyCode.KEYBOARD_F1)
        bindKey(R.id.key_f2, ControlData.KeyCode.KEYBOARD_F2)
        bindKey(R.id.key_f3, ControlData.KeyCode.KEYBOARD_F3)
        bindKey(R.id.key_f4, ControlData.KeyCode.KEYBOARD_F4)
        bindKey(R.id.key_f5, ControlData.KeyCode.KEYBOARD_F5)
        bindKey(R.id.key_f6, ControlData.KeyCode.KEYBOARD_F6)
        bindKey(R.id.key_f7, ControlData.KeyCode.KEYBOARD_F7)
        bindKey(R.id.key_f8, ControlData.KeyCode.KEYBOARD_F8)
        bindKey(R.id.key_f9, ControlData.KeyCode.KEYBOARD_F9)
        bindKey(R.id.key_f10, ControlData.KeyCode.KEYBOARD_F10)
        bindKey(R.id.key_f11, ControlData.KeyCode.KEYBOARD_F11)
        bindKey(R.id.key_f12, ControlData.KeyCode.KEYBOARD_F12)
        bindKey(R.id.key_prt, ControlData.KeyCode.KEYBOARD_PRINTSCREEN)
        bindKey(R.id.key_scr, ControlData.KeyCode.KEYBOARD_SCROLLLOCK)
        bindKey(R.id.key_pause, ControlData.KeyCode.KEYBOARD_PAUSE)

        // 第二行：数字行
        bindKey(R.id.key_grave, ControlData.KeyCode.KEYBOARD_GRAVE)
        bindKey(R.id.key_1, ControlData.KeyCode.KEYBOARD_1)
        bindKey(R.id.key_2, ControlData.KeyCode.KEYBOARD_2)
        bindKey(R.id.key_3, ControlData.KeyCode.KEYBOARD_3)
        bindKey(R.id.key_4, ControlData.KeyCode.KEYBOARD_4)
        bindKey(R.id.key_5, ControlData.KeyCode.KEYBOARD_5)
        bindKey(R.id.key_6, ControlData.KeyCode.KEYBOARD_6)
        bindKey(R.id.key_7, ControlData.KeyCode.KEYBOARD_7)
        bindKey(R.id.key_8, ControlData.KeyCode.KEYBOARD_8)
        bindKey(R.id.key_9, ControlData.KeyCode.KEYBOARD_9)
        bindKey(R.id.key_0, ControlData.KeyCode.KEYBOARD_0)
        bindKey(R.id.key_minus, ControlData.KeyCode.KEYBOARD_MINUS)
        bindKey(R.id.key_equals, ControlData.KeyCode.KEYBOARD_EQUALS)
        bindKey(R.id.key_backspace, ControlData.KeyCode.KEYBOARD_BACKSPACE)
        bindKey(R.id.key_ins, ControlData.KeyCode.KEYBOARD_INSERT)
        bindKey(R.id.key_home, ControlData.KeyCode.KEYBOARD_HOME)
        bindKey(R.id.key_pgup, ControlData.KeyCode.KEYBOARD_PAGEUP)

        // 第三行：Tab + QWERTY
        bindKey(R.id.key_tab, ControlData.KeyCode.KEYBOARD_TAB)
        bindKey(R.id.key_q, ControlData.KeyCode.KEYBOARD_Q)
        bindKey(R.id.key_w, ControlData.KeyCode.KEYBOARD_W)
        bindKey(R.id.key_e, ControlData.KeyCode.KEYBOARD_E)
        bindKey(R.id.key_r, ControlData.KeyCode.KEYBOARD_R)
        bindKey(R.id.key_t, ControlData.KeyCode.KEYBOARD_T)
        bindKey(R.id.key_y, ControlData.KeyCode.KEYBOARD_Y)
        bindKey(R.id.key_u, ControlData.KeyCode.KEYBOARD_U)
        bindKey(R.id.key_i, ControlData.KeyCode.KEYBOARD_I)
        bindKey(R.id.key_o, ControlData.KeyCode.KEYBOARD_O)
        bindKey(R.id.key_p, ControlData.KeyCode.KEYBOARD_P)
        bindKey(R.id.key_lbracket, ControlData.KeyCode.KEYBOARD_LEFTBRACKET)
        bindKey(R.id.key_rbracket, ControlData.KeyCode.KEYBOARD_RIGHTBRACKET)
        bindKey(R.id.key_backslash, ControlData.KeyCode.KEYBOARD_BACKSLASH)
        bindKey(R.id.key_del, ControlData.KeyCode.KEYBOARD_DELETE)
        bindKey(R.id.key_end, ControlData.KeyCode.KEYBOARD_END)
        bindKey(R.id.key_pgdn, ControlData.KeyCode.KEYBOARD_PAGEDOWN)

        // 第四行：CapsLock + ASDFGH
        bindKey(R.id.key_capslock, ControlData.KeyCode.KEYBOARD_CAPSLOCK)
        bindKey(R.id.key_a, ControlData.KeyCode.KEYBOARD_A)
        bindKey(R.id.key_s, ControlData.KeyCode.KEYBOARD_S)
        bindKey(R.id.key_d, ControlData.KeyCode.KEYBOARD_D)
        bindKey(R.id.key_f, ControlData.KeyCode.KEYBOARD_F)
        bindKey(R.id.key_g, ControlData.KeyCode.KEYBOARD_G)
        bindKey(R.id.key_h, ControlData.KeyCode.KEYBOARD_H)
        bindKey(R.id.key_j, ControlData.KeyCode.KEYBOARD_J)
        bindKey(R.id.key_k, ControlData.KeyCode.KEYBOARD_K)
        bindKey(R.id.key_l, ControlData.KeyCode.KEYBOARD_L)
        bindKey(R.id.key_semicolon, ControlData.KeyCode.KEYBOARD_SEMICOLON)
        bindKey(R.id.key_quote, ControlData.KeyCode.KEYBOARD_APOSTROPHE)
        bindKey(R.id.key_enter, ControlData.KeyCode.KEYBOARD_RETURN)

        // 第五行：Shift + ZXCVBN
        bindKey(R.id.key_lshift, ControlData.KeyCode.KEYBOARD_LSHIFT)
        bindKey(R.id.key_z, ControlData.KeyCode.KEYBOARD_Z)
        bindKey(R.id.key_x, ControlData.KeyCode.KEYBOARD_X)
        bindKey(R.id.key_c, ControlData.KeyCode.KEYBOARD_C)
        bindKey(R.id.key_v, ControlData.KeyCode.KEYBOARD_V)
        bindKey(R.id.key_b, ControlData.KeyCode.KEYBOARD_B)
        bindKey(R.id.key_n, ControlData.KeyCode.KEYBOARD_N)
        bindKey(R.id.key_m, ControlData.KeyCode.KEYBOARD_M)
        bindKey(R.id.key_comma, ControlData.KeyCode.KEYBOARD_COMMA)
        bindKey(R.id.key_period, ControlData.KeyCode.KEYBOARD_PERIOD)
        bindKey(R.id.key_slash, ControlData.KeyCode.KEYBOARD_SLASH)
        bindKey(R.id.key_rshift, ControlData.KeyCode.KEYBOARD_RSHIFT)
        bindKey(R.id.key_up, ControlData.KeyCode.KEYBOARD_UP)

        // 第六行：Ctrl + 空格行
        bindKey(R.id.key_lctrl, ControlData.KeyCode.KEYBOARD_LCTRL)
        bindKey(R.id.key_lwin, ControlData.KeyCode.KEYBOARD_LGUI)
        bindKey(R.id.key_lalt, ControlData.KeyCode.KEYBOARD_LALT)
        bindKey(R.id.key_space, ControlData.KeyCode.KEYBOARD_SPACE)
        bindKey(R.id.key_ralt, ControlData.KeyCode.KEYBOARD_RALT)
        bindKey(R.id.key_rwin, ControlData.KeyCode.KEYBOARD_RGUI)
        bindKey(R.id.key_menu, ControlData.KeyCode.KEYBOARD_APPLICATION)
        bindKey(R.id.key_rctrl, ControlData.KeyCode.KEYBOARD_RCTRL)
        bindKey(R.id.key_left, ControlData.KeyCode.KEYBOARD_LEFT)
        bindKey(R.id.key_down, ControlData.KeyCode.KEYBOARD_DOWN)
        bindKey(R.id.key_right, ControlData.KeyCode.KEYBOARD_RIGHT)

        // 小键盘
        bindKey(R.id.key_numlock, ControlData.KeyCode.KEYBOARD_NUMLOCKCLEAR)
        bindKey(R.id.key_numdiv, ControlData.KeyCode.KEYBOARD_KP_DIVIDE)
        bindKey(R.id.key_nummul, ControlData.KeyCode.KEYBOARD_KP_MULTIPLY)
        bindKey(R.id.key_numsub, ControlData.KeyCode.KEYBOARD_KP_MINUS)
        bindKey(R.id.key_numadd, ControlData.KeyCode.KEYBOARD_KP_PLUS)
        bindKey(R.id.key_numenter, ControlData.KeyCode.KEYBOARD_KP_ENTER)
        bindKey(R.id.key_numdot, ControlData.KeyCode.KEYBOARD_KP_PERIOD)
        bindKey(R.id.key_0num, ControlData.KeyCode.KEYBOARD_KP_0)
        bindKey(R.id.key_1num, ControlData.KeyCode.KEYBOARD_KP_1)
        bindKey(R.id.key_2num, ControlData.KeyCode.KEYBOARD_KP_2)
        bindKey(R.id.key_3num, ControlData.KeyCode.KEYBOARD_KP_3)
        bindKey(R.id.key_4num, ControlData.KeyCode.KEYBOARD_KP_4)
        bindKey(R.id.key_5num, ControlData.KeyCode.KEYBOARD_KP_5)
        bindKey(R.id.key_6num, ControlData.KeyCode.KEYBOARD_KP_6)
        bindKey(R.id.key_7num, ControlData.KeyCode.KEYBOARD_KP_7)
        bindKey(R.id.key_8num, ControlData.KeyCode.KEYBOARD_KP_8)
        bindKey(R.id.key_9num, ControlData.KeyCode.KEYBOARD_KP_9)

        // 鼠标按键（使用负值表示鼠标按键）
        bindMouseButton(R.id.key_mouse_left, ControlData.KeyCode.MOUSE_LEFT)
        bindMouseButton(R.id.key_mouse_middle, ControlData.KeyCode.MOUSE_MIDDLE)
        bindMouseButton(R.id.key_mouse_right, ControlData.KeyCode.MOUSE_RIGHT)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindKey(viewId: Int, scancode: ControlData.KeyCode) {
        val keyView = findViewById<View?>(viewId)
        if (keyView != null && keyView is AppCompatButton) {
            val button = keyView


            // 确保按钮可以接收触摸事件
            button.isClickable = true
            button.setFocusable(false)
            button.isFocusableInTouchMode = false
            button.isEnabled = true

            button.setOnTouchListener { v: View?, event: MotionEvent? ->
                when (event!!.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Key pressed: scancode=$scancode, viewId=$viewId")
                        sendKey(scancode, true)
                        pressedKeys.put(viewId, scancode)
                        v!!.isPressed = true // 手动设置按下状态
                        // 消费事件，确保后续的UP事件也能接收到
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "Key released: scancode=$scancode, viewId=$viewId")
                        sendKey(scancode, false)
                        pressedKeys.remove(viewId)
                        v!!.isPressed = false // 手动恢复状态
                        // 消费事件
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 如果手指移出按钮区域，取消按下状态
                        val x = event.x
                        val y = event.y
                        if (x < 0 || x > v!!.width || y < 0 || y > v.height) {
                            if (pressedKeys.containsKey(viewId)) {
                                Log.d(TAG, "Key cancelled (moved outside): scancode=$scancode")
                                sendKey(scancode, false)
                                pressedKeys.remove(viewId)
                                v!!.isPressed = false
                            }
                        }
                        return@setOnTouchListener true
                    }
                }
                false
            }
            Log.d(TAG, "Bound key: viewId=$viewId, scancode=$scancode")
        } else {
            Log.w(TAG, "Key view not found or not a button: viewId=$viewId")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindMouseButton(viewId: Int, mouseButton: ControlData.KeyCode) {
        val keyView = findViewById<View?>(viewId)
        if (keyView != null && keyView is AppCompatButton) {
            val button = keyView


            // 确保按钮可以接收触摸事件
            button.isClickable = true
            button.setFocusable(false)
            button.isFocusableInTouchMode = false
            button.isEnabled = true

            button.setOnTouchListener { v: View?, event: MotionEvent? ->
                when (event!!.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Mouse button pressed: $mouseButton, viewId=$viewId")
                        sendMouseButton(mouseButton, true)
                        v!!.isPressed = true
                        return@setOnTouchListener true // 消费事件
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "Mouse button released: $mouseButton, viewId=$viewId")
                        sendMouseButton(mouseButton, false)
                        v!!.isPressed = false
                        return@setOnTouchListener true // 消费事件
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 如果手指移出按钮区域，取消按下状态
                        val x = event.x
                        val y = event.y
                        if (x < 0 || x > v!!.width || y < 0 || y > v.height) {
                            Log.d(TAG, "Mouse button cancelled (moved outside): $mouseButton")
                            sendMouseButton(mouseButton, false)
                            v!!.isPressed = false
                        }
                        return@setOnTouchListener true
                    }
                }
                false
            }
            Log.d(TAG, "Bound mouse button: viewId=$viewId, button=$mouseButton")
        } else {
            Log.w(TAG, "Mouse button view not found or not a button: viewId=$viewId")
        }
    }

    private fun sendKey(scancode: ControlData.KeyCode, isDown: Boolean) {
        if (inputBridge != null) {
            try {
                Log.d(
                    TAG,
                    "VirtualKeyboardView.sendKey: scancode=" + scancode + ", isDown=" + isDown +
                            ", bridge=" + inputBridge!!.javaClass.simpleName
                )
                inputBridge!!.sendKey(scancode, isDown)
                Log.d(TAG, "VirtualKeyboardView.sendKey: successfully called inputBridge.sendKey()")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending key: scancode=$scancode", e)
                e.printStackTrace()
            }
        } else {
            Log.e(TAG, "InputBridge is null! Cannot send key: scancode=$scancode")
        }
    }

    private fun sendMouseButton(button: ControlData.KeyCode, isDown: Boolean) {
        if (inputBridge != null) {
            try {
                // 发送鼠标按键到屏幕中心
                val location = IntArray(2)
                getLocationOnScreen(location)
                val centerX = location[0] + width / 2.0f
                val centerY = location[1] + height / 2.0f

                inputBridge!!.sendMouseButton(button, isDown, centerX, centerY)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending mouse button: $button", e)
            }
        }
    }

    /**
     * 显示键盘
     */
    fun show() {
        Log.d(TAG, "Showing virtual keyboard")
        visibility = VISIBLE
        bringToFront()


        // 确保键盘视图能够接收触摸事件
        isClickable = true
        setFocusable(false)
        isFocusableInTouchMode = false


        // 强制刷新视图
        invalidate()
        requestLayout()

        Log.d(
            TAG,
            "Virtual keyboard visibility: $visibility, clickable: $isClickable"
        )
    }

    /**
     * 隐藏键盘
     */
    fun hide() {
        Log.d(TAG, "Hiding virtual keyboard")
        // 释放所有按下的按键
        for (entry in pressedKeys.entries) {
            sendKey(entry.value!!, false)
        }
        pressedKeys.clear()

        visibility = GONE
    }

    /**
     * 切换键盘显示状态
     */
    fun toggle() {
        Log.d(
            TAG,
            "Toggling virtual keyboard, current visibility: " + (if (isVisible) "VISIBLE" else "GONE")
        )
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    val isShowing: Boolean
        /**
         * 检查键盘是否正在显示
         */
        get() = isVisible


    /**
     * 设置透明度 (0.0 - 1.0)
     */
    fun setKeyboardAlpha(alpha: Float) {
        setAlpha(max(0.3f, min(1.0f, alpha))) // 限制在 0.3 - 1.0 之间
    }

    /**
     * 重置键盘位置到屏幕底部中心
     */
    fun resetPosition() {
        translationX = 0f
        translationY = 0f
    }

    /**
     * 重写 onInterceptTouchEvent 确保触摸事件能正确传递到子视图
     * 只有当触摸事件不在子视图上时，才拦截事件（用于拖动）
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 如果键盘不可见，不拦截事件
        if (visibility != VISIBLE) {
            return false
        }

        Log.d(
            TAG,
            "onInterceptTouchEvent: action=" + ev.action + ", x=" + ev.x + ", y=" + ev.y
        )


        // 不拦截，让子视图（按键）处理触摸事件
        return false
    }

    /**
     * 重写 dispatchTouchEvent 确保事件能正确传递
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 如果键盘不可见，不处理事件
        if (visibility != VISIBLE) {
            return super.dispatchTouchEvent(event)
        }

        Log.d(
            TAG,
            "dispatchTouchEvent: action=" + event.action + ", x=" + event.x + ", y=" + event.y
        )


        // 先让子视图处理
        val handled = super.dispatchTouchEvent(event)

        Log.d(TAG, "dispatchTouchEvent handled: $handled")


        // 如果子视图没有处理，返回false让父视图处理
        // 如果子视图处理了，返回true表示事件已消费
        return handled
    }

    /**
     * 重写 onTouchEvent 作为备用处理
     * 如果子视图没有消费事件，这里可以处理（比如拖动整个键盘）
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果键盘不可见，不处理事件
        if (visibility != VISIBLE) {
            return false
        }

        Log.d(
            TAG,
            "onTouchEvent: action=" + event.action + ", x=" + event.x + ", y=" + event.y
        )


        // 默认不处理，让子视图处理
        // 如果需要拖动整个键盘（除了拖动把手），可以在这里实现
        return super.onTouchEvent(event)
    }
}

