package com.app.ralaunch.controls.bridges

import com.app.ralaunch.controls.data.ControlData

/**
 * 输入桥接接口
 * 将虚拟控制器的输入转发到SDL/游戏
 */
interface ControlInputBridge {
    /**
     * 发送键盘按键事件
     * @param keycode SDL按键码
     * @param isDown true=按下, false=释放
     */
    fun sendKey(keycode: ControlData.KeyCode, isDown: Boolean)

    /**
     * 发送鼠标按键事件
     * @param button ControlData.MOUSE_LEFT/RIGHT/MIDDLE
     * @param isDown true=按下, false=释放
     * @param x 鼠标X坐标（屏幕坐标）
     * @param y 鼠标Y坐标（屏幕坐标）
     */
    fun sendMouseButton(button: ControlData.KeyCode, isDown: Boolean, x: Float, y: Float)

    /**
     * 发送鼠标移动事件
     * @param deltaX X轴移动量
     * @param deltaY Y轴移动量
     */
    fun sendMouseMove(deltaX: Float, deltaY: Float)

    /**
     * 发送鼠标滚轮事件
     * @param scrollY 滚轮滚动量（正数=向上，负数=向下）
     */
    fun sendMouseWheel(scrollY: Float)

    /**
     * 发送绝对鼠标位置（用于右摇杆八方向攻击）
     * @param x 绝对X坐标（屏幕坐标）
     * @param y 绝对Y坐标（屏幕坐标）
     */
    fun sendMousePosition(x: Float, y: Float)

    /**
     * 设置Xbox虚拟控制器左摇杆
     * @param x X轴值 (-1.0 到 1.0)
     * @param y Y轴值 (-1.0 到 1.0)
     */
    fun sendXboxLeftStick(x: Float, y: Float)

    /**
     * 设置Xbox虚拟控制器右摇杆
     * @param x X轴值 (-1.0 到 1.0)
     * @param y Y轴值 (-1.0 到 1.0)
     */
    fun sendXboxRightStick(x: Float, y: Float)

    /**
     * 发送Xbox控制器按钮事件
     * @param xboxButton Xbox按钮代码 (ControlData.XBOX_BUTTON_*)
     * @param isDown true=按下, false=释放
     */
    fun sendXboxButton(xboxButton: ControlData.KeyCode, isDown: Boolean)

    /**
     * 发送Xbox控制器触发器事件
     * @param xboxTrigger 触发器代码 (ControlData.XBOX_TRIGGER_LEFT/RIGHT)
     * @param value 触发器值 (0.0 到 1.0)
     */
    fun sendXboxTrigger(xboxTrigger: ControlData.KeyCode, value: Float)
}
