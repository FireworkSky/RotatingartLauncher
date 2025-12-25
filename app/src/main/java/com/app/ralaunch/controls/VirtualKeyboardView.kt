package com.app.ralaunch.controls

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
    private val pressedKeys: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()

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
        keyboardLayout = LayoutInflater.from(getContext()).inflate(
            R.layout.layout_keyboard_selector, this, true
        )


        // 设置透明度 (0.7 = 70% 不透明)
        setAlpha(0.7f)


        // 关键修复：确保VirtualKeyboardView能够接收触摸事件
        // 设置这些属性后，触摸事件才能正确传递到子视图（按键）
        setClickable(true)
        setFocusable(false) // 不需要焦点，只需要接收触摸事件
        setFocusableInTouchMode(false)


        // 绑定所有按键
        bindAllKeys()
    }

    fun setInputBridge(bridge: ControlInputBridge?) {
        this.inputBridge = bridge
        Log.d(TAG, "InputBridge set: " + (if (bridge != null) "Success" else "Null"))
    }

    private fun bindAllKeys() {
        // 第一行：功能键
        bindKey(R.id.key_esc, ControlData.SDL_SCANCODE_ESCAPE)
        bindKey(R.id.key_f1, 58)
        bindKey(R.id.key_f2, 59)
        bindKey(R.id.key_f3, 60)
        bindKey(R.id.key_f4, 61)
        bindKey(R.id.key_f5, 62)
        bindKey(R.id.key_f6, 63)
        bindKey(R.id.key_f7, 64)
        bindKey(R.id.key_f8, 65)
        bindKey(R.id.key_f9, 66)
        bindKey(R.id.key_f10, 67)
        bindKey(R.id.key_f11, 68)
        bindKey(R.id.key_f12, 69)
        bindKey(R.id.key_prt, 70)
        bindKey(R.id.key_scr, 71)
        bindKey(R.id.key_pause, 72)

        // 第二行：数字行
        bindKey(R.id.key_grave, 53)
        bindKey(R.id.key_1, 30)
        bindKey(R.id.key_2, 31)
        bindKey(R.id.key_3, 32)
        bindKey(R.id.key_4, 33)
        bindKey(R.id.key_5, 34)
        bindKey(R.id.key_6, 35)
        bindKey(R.id.key_7, 36)
        bindKey(R.id.key_8, 37)
        bindKey(R.id.key_9, 38)
        bindKey(R.id.key_0, 39)
        bindKey(R.id.key_minus, 45)
        bindKey(R.id.key_equals, 46)
        bindKey(R.id.key_backspace, 42)
        bindKey(R.id.key_ins, 73)
        bindKey(R.id.key_home, 74)
        bindKey(R.id.key_pgup, 75)

        // 第三行：Tab + QWERTY
        bindKey(R.id.key_tab, 43)
        bindKey(R.id.key_q, 20)
        bindKey(R.id.key_w, 26)
        bindKey(R.id.key_e, 8)
        bindKey(R.id.key_r, 21)
        bindKey(R.id.key_t, 23)
        bindKey(R.id.key_y, 28)
        bindKey(R.id.key_u, 24)
        bindKey(R.id.key_i, 12)
        bindKey(R.id.key_o, 18)
        bindKey(R.id.key_p, 19)
        bindKey(R.id.key_lbracket, 47)
        bindKey(R.id.key_rbracket, 48)
        bindKey(R.id.key_backslash, 49)
        bindKey(R.id.key_del, 76)
        bindKey(R.id.key_end, 77)
        bindKey(R.id.key_pgdn, 78)

        // 第四行：CapsLock + ASDFGH
        bindKey(R.id.key_capslock, 57)
        bindKey(R.id.key_a, 4)
        bindKey(R.id.key_s, 22)
        bindKey(R.id.key_d, 7)
        bindKey(R.id.key_f, 9)
        bindKey(R.id.key_g, 10)
        bindKey(R.id.key_h, 11)
        bindKey(R.id.key_j, 13)
        bindKey(R.id.key_k, 14)
        bindKey(R.id.key_l, 15)
        bindKey(R.id.key_semicolon, 51)
        bindKey(R.id.key_quote, 52)
        bindKey(R.id.key_enter, 40)

        // 第五行：Shift + ZXCVBN
        bindKey(R.id.key_lshift, 225)
        bindKey(R.id.key_z, 29)
        bindKey(R.id.key_x, 27)
        bindKey(R.id.key_c, 6)
        bindKey(R.id.key_v, 25)
        bindKey(R.id.key_b, 5)
        bindKey(R.id.key_n, 17)
        bindKey(R.id.key_m, 16)
        bindKey(R.id.key_comma, 54)
        bindKey(R.id.key_period, 55)
        bindKey(R.id.key_slash, 56)
        bindKey(R.id.key_rshift, 229)
        bindKey(R.id.key_up, 82)

        // 第六行：Ctrl + 空格行
        bindKey(R.id.key_lctrl, 224)
        bindKey(R.id.key_lwin, 227)
        bindKey(R.id.key_lalt, 226)
        bindKey(R.id.key_space, 44)
        bindKey(R.id.key_ralt, 230)
        bindKey(R.id.key_rwin, 231)
        bindKey(R.id.key_menu, 101)
        bindKey(R.id.key_rctrl, 228)
        bindKey(R.id.key_left, 80)
        bindKey(R.id.key_down, 81)
        bindKey(R.id.key_right, 79)

        // 小键盘
        bindKey(R.id.key_numlock, 83)
        bindKey(R.id.key_numdiv, 84)
        bindKey(R.id.key_nummul, 85)
        bindKey(R.id.key_numsub, 86)
        bindKey(R.id.key_numadd, 87)
        bindKey(R.id.key_numenter, 88)
        bindKey(R.id.key_numdot, 99)
        bindKey(R.id.key_0num, 98)
        bindKey(R.id.key_1num, 89)
        bindKey(R.id.key_2num, 90)
        bindKey(R.id.key_3num, 91)
        bindKey(R.id.key_4num, 92)
        bindKey(R.id.key_5num, 93)
        bindKey(R.id.key_6num, 94)
        bindKey(R.id.key_7num, 95)
        bindKey(R.id.key_8num, 96)
        bindKey(R.id.key_9num, 97)

        // 鼠标按键
        bindMouseButton(R.id.key_mouse_left, ControlData.MOUSE_LEFT)
        bindMouseButton(R.id.key_mouse_middle, ControlData.MOUSE_MIDDLE)
        bindMouseButton(R.id.key_mouse_right, ControlData.MOUSE_RIGHT)
    }

    private fun bindKey(viewId: Int, scancode: Int) {
        val keyView = findViewById<View?>(viewId)
        if (keyView != null && keyView is AppCompatButton) {
            val button = keyView


            // 确保按钮可以接收触摸事件
            button.setClickable(true)
            button.setFocusable(false)
            button.setFocusableInTouchMode(false)
            button.setEnabled(true)

            button.setOnTouchListener { v: View?, event: MotionEvent? ->
                when (event!!.getAction()) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Key pressed: scancode=" + scancode + ", viewId=" + viewId)
                        sendKey(scancode, true)
                        pressedKeys.put(viewId, scancode)
                        v!!.setPressed(true) // 手动设置按下状态
                        // 消费事件，确保后续的UP事件也能接收到
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "Key released: scancode=" + scancode + ", viewId=" + viewId)
                        sendKey(scancode, false)
                        pressedKeys.remove(viewId)
                        v!!.setPressed(false) // 手动恢复状态
                        // 消费事件
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 如果手指移出按钮区域，取消按下状态
                        val x = event.getX()
                        val y = event.getY()
                        if (x < 0 || x > v!!.getWidth() || y < 0 || y > v.getHeight()) {
                            if (pressedKeys.containsKey(viewId)) {
                                Log.d(TAG, "Key cancelled (moved outside): scancode=" + scancode)
                                sendKey(scancode, false)
                                pressedKeys.remove(viewId)
                                v!!.setPressed(false)
                            }
                        }
                        return@setOnTouchListener true
                    }
                }
                false
            }
            Log.d(TAG, "Bound key: viewId=" + viewId + ", scancode=" + scancode)
        } else {
            Log.w(TAG, "Key view not found or not a button: viewId=" + viewId)
        }
    }

    private fun bindMouseButton(viewId: Int, mouseButton: Int) {
        val keyView = findViewById<View?>(viewId)
        if (keyView != null && keyView is AppCompatButton) {
            val button = keyView


            // 确保按钮可以接收触摸事件
            button.setClickable(true)
            button.setFocusable(false)
            button.setFocusableInTouchMode(false)
            button.setEnabled(true)

            button.setOnTouchListener { v: View?, event: MotionEvent? ->
                when (event!!.getAction()) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Mouse button pressed: " + mouseButton + ", viewId=" + viewId)
                        sendMouseButton(mouseButton, true)
                        v!!.setPressed(true)
                        return@setOnTouchListener true // 消费事件
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "Mouse button released: " + mouseButton + ", viewId=" + viewId)
                        sendMouseButton(mouseButton, false)
                        v!!.setPressed(false)
                        return@setOnTouchListener true // 消费事件
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // 如果手指移出按钮区域，取消按下状态
                        val x = event.getX()
                        val y = event.getY()
                        if (x < 0 || x > v!!.getWidth() || y < 0 || y > v.getHeight()) {
                            Log.d(TAG, "Mouse button cancelled (moved outside): " + mouseButton)
                            sendMouseButton(mouseButton, false)
                            v!!.setPressed(false)
                        }
                        return@setOnTouchListener true
                    }
                }
                false
            }
            Log.d(TAG, "Bound mouse button: viewId=" + viewId + ", button=" + mouseButton)
        } else {
            Log.w(TAG, "Mouse button view not found or not a button: viewId=" + viewId)
        }
    }

    private fun sendKey(scancode: Int, isDown: Boolean) {
        if (inputBridge != null) {
            try {
                Log.d(
                    TAG,
                    "VirtualKeyboardView.sendKey: scancode=" + scancode + ", isDown=" + isDown +
                            ", bridge=" + inputBridge!!.javaClass.getSimpleName()
                )
                inputBridge!!.sendKey(scancode, isDown)
                Log.d(TAG, "VirtualKeyboardView.sendKey: successfully called inputBridge.sendKey()")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending key: scancode=" + scancode, e)
                e.printStackTrace()
            }
        } else {
            Log.e(TAG, "InputBridge is null! Cannot send key: scancode=" + scancode)
        }
    }

    private fun sendMouseButton(button: Int, isDown: Boolean) {
        if (inputBridge != null) {
            try {
                // 发送鼠标按键到屏幕中心
                val location = IntArray(2)
                getLocationOnScreen(location)
                val centerX = location[0] + getWidth() / 2.0f
                val centerY = location[1] + getHeight() / 2.0f
                inputBridge!!.sendMouseButton(button, isDown, centerX, centerY)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending mouse button: " + button, e)
            }
        }
    }

    /**
     * 显示键盘
     */
    fun show() {
        Log.d(TAG, "Showing virtual keyboard")
        setVisibility(VISIBLE)
        bringToFront()


        // 确保键盘视图能够接收触摸事件
        setClickable(true)
        setFocusable(false)
        setFocusableInTouchMode(false)


        // 强制刷新视图
        invalidate()
        requestLayout()

        Log.d(
            TAG,
            "Virtual keyboard visibility: " + getVisibility() + ", clickable: " + isClickable()
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

        setVisibility(GONE)
    }

    /**
     * 切换键盘显示状态
     */
    fun toggle() {
        Log.d(
            TAG,
            "Toggling virtual keyboard, current visibility: " + (if (getVisibility() == VISIBLE) "VISIBLE" else "GONE")
        )
        if (getVisibility() == VISIBLE) {
            hide()
        } else {
            show()
        }
    }

    val isShowing: Boolean
        /**
         * 检查键盘是否正在显示
         */
        get() = getVisibility() == VISIBLE


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
        setTranslationX(0f)
        setTranslationY(0f)
    }

    /**
     * 重写 onInterceptTouchEvent 确保触摸事件能正确传递到子视图
     * 只有当触摸事件不在子视图上时，才拦截事件（用于拖动）
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 如果键盘不可见，不拦截事件
        if (getVisibility() != VISIBLE) {
            return false
        }

        Log.d(
            TAG,
            "onInterceptTouchEvent: action=" + ev.getAction() + ", x=" + ev.getX() + ", y=" + ev.getY()
        )


        // 不拦截，让子视图（按键）处理触摸事件
        return false
    }

    /**
     * 重写 dispatchTouchEvent 确保事件能正确传递
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 如果键盘不可见，不处理事件
        if (getVisibility() != VISIBLE) {
            return super.dispatchTouchEvent(event)
        }

        Log.d(
            TAG,
            "dispatchTouchEvent: action=" + event.getAction() + ", x=" + event.getX() + ", y=" + event.getY()
        )


        // 先让子视图处理
        val handled = super.dispatchTouchEvent(event)

        Log.d(TAG, "dispatchTouchEvent handled: " + handled)


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
        if (getVisibility() != VISIBLE) {
            return false
        }

        Log.d(
            TAG,
            "onTouchEvent: action=" + event.getAction() + ", x=" + event.getX() + ", y=" + event.getY()
        )


        // 默认不处理，让子视图处理
        // 如果需要拖动整个键盘（除了拖动把手），可以在这里实现
        return super.onTouchEvent(event)
    }
}

