package com.app.ralaunch.feature.game.input

import com.app.ralaunch.core.logging.AppLog

/**
 * SDL IME/文本输入工具，集中管理反射调用
 */
object GameImeHelper {
    private const val TAG = "GameImeHelper"

    fun sendTextToGame(text: String) {
        try {
            val sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection")
            val nativeCommitText = sdlInputConnectionClass.getDeclaredMethod(
                "nativeCommitText", String::class.java, Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            nativeCommitText.invoke(null, text, 1)
        } catch (e: Exception) {
            AppLog.e(TAG, "发送文本失败", e)
        }
    }

    fun sendBackspaceToGame() {
        try {
            val sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection")
            val onNativeKeyDown = sdlInputConnectionClass.getDeclaredMethod(
                "nativeKeyDown", Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val onNativeKeyUp = sdlInputConnectionClass.getDeclaredMethod(
                "nativeKeyUp", Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val SDL_SCANCODE_BACKSPACE = 42
            onNativeKeyDown.invoke(null, SDL_SCANCODE_BACKSPACE)
            onNativeKeyUp.invoke(null, SDL_SCANCODE_BACKSPACE)
        } catch (e: Exception) {
            AppLog.e(TAG, "发送Backspace失败", e)
        }
    }

    fun enableSDLTextInputForIME() {
        try {
            val sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity")
            val showTextInput = sdlActivityClass.getDeclaredMethod(
                "showTextInput",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            showTextInput.invoke(null, 0, 0, 0, 0)
        } catch (e: Exception) {
            AppLog.e(TAG, "启用SDL文本输入失败", e)
        }
    }

    fun disableSDLTextInput() {
        try {
            val sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity")
            val COMMAND_TEXTEDIT_HIDE = 3

            val mSingletonField = sdlActivityClass.getDeclaredField("mSingleton")
                .apply { isAccessible = true }
            val mSingleton = mSingletonField.get(null) ?: run {
                AppLog.w(TAG, "SDLActivity.mSingleton is null, cannot hide text input")
                return
            }

            val sendCommandMethod = sdlActivityClass.getDeclaredMethod(
                "sendCommand", Int::class.javaPrimitiveType, Any::class.java
            ).apply { isAccessible = true }
            sendCommandMethod.invoke(mSingleton, COMMAND_TEXTEDIT_HIDE, null)
        } catch (e: Exception) {
            AppLog.e(TAG, "禁用SDL文本输入失败", e)
        }
    }
}
