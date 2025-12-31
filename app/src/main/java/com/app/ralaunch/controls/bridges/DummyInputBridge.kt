package com.app.ralaunch.controls.bridges

import com.app.ralaunch.controls.data.ControlData

class DummyInputBridge : ControlInputBridge {
    override fun sendKey(
        keycode: ControlData.KeyCode,
        isDown: Boolean
    ) {
    }

    override fun sendMouseButton(
        button: ControlData.KeyCode,
        isDown: Boolean,
        x: Float,
        y: Float
    ) {
    }

    override fun sendMouseMove(deltaX: Float, deltaY: Float) {
    }

    override fun sendMouseWheel(scrollY: Float) {
    }

    override fun sendMousePosition(x: Float, y: Float) {
    }

    override fun sendXboxLeftStick(x: Float, y: Float) {
    }

    override fun sendXboxRightStick(x: Float, y: Float) {}

    override fun sendXboxButton(
        xboxButton: ControlData.KeyCode,
        isDown: Boolean
    ) {
    }

    override fun sendXboxTrigger(
        xboxTrigger: ControlData.KeyCode,
        value: Float
    ) {
    }
}