package com.app.ralaunch.controls;

/**
 * 输入桥接接口
 * 将虚拟控制器的输入转发到SDL/游戏
 */
public interface ControlInputBridge {
    /**
     * 发送键盘按键事件
     * @param keycode SDL按键码
     * @param isDown true=按下, false=释放
     */
    void sendKey(int keycode, boolean isDown);
    
    /**
     * 发送鼠标按键事件
     * @param button ControlData.MOUSE_LEFT/RIGHT/MIDDLE
     * @param isDown true=按下, false=释放
     * @param x 鼠标X坐标（屏幕坐标）
     * @param y 鼠标Y坐标（屏幕坐标）
     */
    void sendMouseButton(int button, boolean isDown, float x, float y);
    
    /**
     * 发送鼠标移动事件
     * @param deltaX X轴移动量
     * @param deltaY Y轴移动量
     */
    void sendMouseMove(float deltaX, float deltaY);
    
    /**
     * 发送鼠标滚轮事件
     * @param scrollY 滚轮滚动量（正数=向上，负数=向下）
     */
    void sendMouseWheel(float scrollY);
    
    /**
     * 发送绝对鼠标位置（用于右摇杆八方向攻击）
     * @param x 绝对X坐标（屏幕坐标）
     * @param y 绝对Y坐标（屏幕坐标）
     */
    void sendMousePosition(float x, float y);

    /**
     * 设置Xbox虚拟控制器左摇杆
     * @param x X轴值 (-1.0 到 1.0)
     * @param y Y轴值 (-1.0 到 1.0)
     */
    void sendXboxLeftStick(float x, float y);

    /**
     * 设置Xbox虚拟控制器右摇杆
     * @param x X轴值 (-1.0 到 1.0)
     * @param y Y轴值 (-1.0 到 1.0)
     */
    void sendXboxRightStick(float x, float y);

    /**
     * 发送Xbox控制器按钮事件
     * @param xboxButton Xbox按钮代码 (ControlData.XBOX_BUTTON_*)
     * @param isDown true=按下, false=释放
     */
    void sendXboxButton(int xboxButton, boolean isDown);

    /**
     * 发送Xbox控制器触发器事件
     * @param xboxTrigger 触发器代码 (ControlData.XBOX_TRIGGER_LEFT/RIGHT)
     * @param value 触发器值 (0.0 到 1.0)
     */
    void sendXboxTrigger(int xboxTrigger, float value);
}
