package com.app.ralaunch.feature.controls

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.bridges.SDLInputBridge
import com.app.ralaunch.feature.game.legacy.GameActivity

object ControlSpecialActionHandler {
    private const val TAG = "ControlSpecialAction"

    fun handlePress(
        context: Context?,
        keycode: ControlData.KeyCode,
        inputBridge: ControlInputBridge
    ): Boolean {
        return when (keycode) {
            ControlData.KeyCode.SPECIAL_KEYBOARD -> {
                showKeyboard(context, inputBridge)
                true
            }
            ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON -> {
                ControlsSharedState.isTouchPadRightButton = !ControlsSharedState.isTouchPadRightButton
                true
            }
            else -> false
        }
    }

    private fun showKeyboard(context: Context?, inputBridge: ControlInputBridge) {
        try {
            val activity = context as? Activity
            if (activity == null) {
                Log.e(TAG, "Context is not an Activity")
                return
            }

            activity.runOnUiThread {
                try {
                    if (inputBridge is SDLInputBridge) {
                        inputBridge.startTextInput()
                    }
                    GameActivity.enableSDLTextInputForIME()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable SDL text input", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show keyboard", e)
        }
    }
}
