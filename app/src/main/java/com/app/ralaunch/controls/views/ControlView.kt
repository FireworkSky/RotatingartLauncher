package com.app.ralaunch.controls.views

import com.app.ralaunch.controls.data.ControlData

/**
 * 虚拟控制View接口
 * 所有虚拟控制元素（按钮、摇杆等）都实现此接口
 */
interface ControlView {
    /**
     * 控制数据
     */
    var controlData: ControlData
}