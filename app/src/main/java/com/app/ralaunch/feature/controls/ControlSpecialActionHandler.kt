package com.app.ralaunch.feature.controls

import com.app.ralaunch.feature.controls.bridges.ControlInputBridge

object ControlSpecialActionHandler {
    private const val TAG = "ControlSpecialAction"

    fun handlePress(
        keycode: ControlData.KeyCode,
        inputBridge: ControlInputBridge
    ): Boolean {
        return when (keycode) {
            ControlData.KeyCode.SPECIAL_KEYBOARD -> {
                inputBridge.startTextInput()
                true
            }
            ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON -> {
                ControlsSharedState.isTouchPadRightButton = !ControlsSharedState.isTouchPadRightButton
                true
            }
            else -> false
        }
    }
}
