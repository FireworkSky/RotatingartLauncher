package com.app.ralaunch.feature.controls.ui

import com.app.ralaunch.core.logging.AppLog
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.bridges.ControlInputBridge
import com.app.ralaunch.feature.controls.ControlData

private const val TAG = "VirtualKeyboardCompose"

/**
 * 虚拟键盘组件 - Compose 版本
 * 用于游戏中显示完整的虚拟键盘布局
 */
@Composable
fun VirtualKeyboardCompose(
    inputBridge: ControlInputBridge?,
    isVisible: Boolean,
    alpha: Float = 0.7f,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    Box(
        modifier = modifier
            .wrapContentSize()
            .alpha(alpha)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 第一行：ESC + F功能键
            KeyboardRow {
                VirtualKey("Esc", 30.dp, ControlData.KeyCode.KEYBOARD_ESCAPE, inputBridge)
                Spacer(modifier = Modifier.width(8.dp))
                VirtualKey("F1", 28.dp, ControlData.KeyCode.KEYBOARD_F1, inputBridge)
                VirtualKey("F2", 28.dp, ControlData.KeyCode.KEYBOARD_F2, inputBridge)
                VirtualKey("F3", 28.dp, ControlData.KeyCode.KEYBOARD_F3, inputBridge)
                VirtualKey("F4", 28.dp, ControlData.KeyCode.KEYBOARD_F4, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("F5", 28.dp, ControlData.KeyCode.KEYBOARD_F5, inputBridge)
                VirtualKey("F6", 28.dp, ControlData.KeyCode.KEYBOARD_F6, inputBridge)
                VirtualKey("F7", 28.dp, ControlData.KeyCode.KEYBOARD_F7, inputBridge)
                VirtualKey("F8", 28.dp, ControlData.KeyCode.KEYBOARD_F8, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("F9", 28.dp, ControlData.KeyCode.KEYBOARD_F9, inputBridge)
                VirtualKey("F10", 28.dp, ControlData.KeyCode.KEYBOARD_F10, inputBridge)
                VirtualKey("F11", 28.dp, ControlData.KeyCode.KEYBOARD_F11, inputBridge)
                VirtualKey("F12", 28.dp, ControlData.KeyCode.KEYBOARD_F12, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("Prt", 28.dp, ControlData.KeyCode.KEYBOARD_PRINTSCREEN, inputBridge)
                VirtualKey("Scr", 28.dp, ControlData.KeyCode.KEYBOARD_SCROLLLOCK, inputBridge)
                VirtualKey("Pau", 28.dp, ControlData.KeyCode.KEYBOARD_PAUSE, inputBridge)
            }
            
            // 第二行：数字行
            KeyboardRow {
                VirtualKey("`", 28.dp, ControlData.KeyCode.KEYBOARD_GRAVE, inputBridge)
                VirtualKey("1", 28.dp, ControlData.KeyCode.KEYBOARD_1, inputBridge)
                VirtualKey("2", 28.dp, ControlData.KeyCode.KEYBOARD_2, inputBridge)
                VirtualKey("3", 28.dp, ControlData.KeyCode.KEYBOARD_3, inputBridge)
                VirtualKey("4", 28.dp, ControlData.KeyCode.KEYBOARD_4, inputBridge)
                VirtualKey("5", 28.dp, ControlData.KeyCode.KEYBOARD_5, inputBridge)
                VirtualKey("6", 28.dp, ControlData.KeyCode.KEYBOARD_6, inputBridge)
                VirtualKey("7", 28.dp, ControlData.KeyCode.KEYBOARD_7, inputBridge)
                VirtualKey("8", 28.dp, ControlData.KeyCode.KEYBOARD_8, inputBridge)
                VirtualKey("9", 28.dp, ControlData.KeyCode.KEYBOARD_9, inputBridge)
                VirtualKey("0", 28.dp, ControlData.KeyCode.KEYBOARD_0, inputBridge)
                VirtualKey("-", 28.dp, ControlData.KeyCode.KEYBOARD_MINUS, inputBridge)
                VirtualKey("=", 28.dp, ControlData.KeyCode.KEYBOARD_EQUALS, inputBridge)
                VirtualKey("⌫", 44.dp, ControlData.KeyCode.KEYBOARD_BACKSPACE, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("Ins", 28.dp, ControlData.KeyCode.KEYBOARD_INSERT, inputBridge)
                VirtualKey("Hm", 28.dp, ControlData.KeyCode.KEYBOARD_HOME, inputBridge)
                VirtualKey("PU", 28.dp, ControlData.KeyCode.KEYBOARD_PAGEUP, inputBridge)
            }
            
            // 第三行：Tab + QWERTY
            KeyboardRow {
                VirtualKey("Tab", 40.dp, ControlData.KeyCode.KEYBOARD_TAB, inputBridge)
                VirtualKey("Q", 28.dp, ControlData.KeyCode.KEYBOARD_Q, inputBridge)
                VirtualKey("W", 28.dp, ControlData.KeyCode.KEYBOARD_W, inputBridge)
                VirtualKey("E", 28.dp, ControlData.KeyCode.KEYBOARD_E, inputBridge)
                VirtualKey("R", 28.dp, ControlData.KeyCode.KEYBOARD_R, inputBridge)
                VirtualKey("T", 28.dp, ControlData.KeyCode.KEYBOARD_T, inputBridge)
                VirtualKey("Y", 28.dp, ControlData.KeyCode.KEYBOARD_Y, inputBridge)
                VirtualKey("U", 28.dp, ControlData.KeyCode.KEYBOARD_U, inputBridge)
                VirtualKey("I", 28.dp, ControlData.KeyCode.KEYBOARD_I, inputBridge)
                VirtualKey("O", 28.dp, ControlData.KeyCode.KEYBOARD_O, inputBridge)
                VirtualKey("P", 28.dp, ControlData.KeyCode.KEYBOARD_P, inputBridge)
                VirtualKey("[", 28.dp, ControlData.KeyCode.KEYBOARD_LEFTBRACKET, inputBridge)
                VirtualKey("]", 28.dp, ControlData.KeyCode.KEYBOARD_RIGHTBRACKET, inputBridge)
                VirtualKey("\\", 32.dp, ControlData.KeyCode.KEYBOARD_BACKSLASH, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("Del", 28.dp, ControlData.KeyCode.KEYBOARD_DELETE, inputBridge)
                VirtualKey("End", 28.dp, ControlData.KeyCode.KEYBOARD_END, inputBridge)
                VirtualKey("PD", 28.dp, ControlData.KeyCode.KEYBOARD_PAGEDOWN, inputBridge)
            }
            
            // 第四行：CapsLock + ASDFGH
            KeyboardRow {
                VirtualKey("Caps", 48.dp, ControlData.KeyCode.KEYBOARD_CAPSLOCK, inputBridge)
                VirtualKey("A", 28.dp, ControlData.KeyCode.KEYBOARD_A, inputBridge)
                VirtualKey("S", 28.dp, ControlData.KeyCode.KEYBOARD_S, inputBridge)
                VirtualKey("D", 28.dp, ControlData.KeyCode.KEYBOARD_D, inputBridge)
                VirtualKey("F", 28.dp, ControlData.KeyCode.KEYBOARD_F, inputBridge)
                VirtualKey("G", 28.dp, ControlData.KeyCode.KEYBOARD_G, inputBridge)
                VirtualKey("H", 28.dp, ControlData.KeyCode.KEYBOARD_H, inputBridge)
                VirtualKey("J", 28.dp, ControlData.KeyCode.KEYBOARD_J, inputBridge)
                VirtualKey("K", 28.dp, ControlData.KeyCode.KEYBOARD_K, inputBridge)
                VirtualKey("L", 28.dp, ControlData.KeyCode.KEYBOARD_L, inputBridge)
                VirtualKey(";", 28.dp, ControlData.KeyCode.KEYBOARD_SEMICOLON, inputBridge)
                VirtualKey("'", 28.dp, ControlData.KeyCode.KEYBOARD_APOSTROPHE, inputBridge)
                VirtualKey("Enter", 52.dp, ControlData.KeyCode.KEYBOARD_RETURN, inputBridge)
            }
            
            // 第五行：Shift + ZXCVBN
            KeyboardRow {
                VirtualKey("Shift", 60.dp, ControlData.KeyCode.KEYBOARD_LSHIFT, inputBridge)
                VirtualKey("Z", 28.dp, ControlData.KeyCode.KEYBOARD_Z, inputBridge)
                VirtualKey("X", 28.dp, ControlData.KeyCode.KEYBOARD_X, inputBridge)
                VirtualKey("C", 28.dp, ControlData.KeyCode.KEYBOARD_C, inputBridge)
                VirtualKey("V", 28.dp, ControlData.KeyCode.KEYBOARD_V, inputBridge)
                VirtualKey("B", 28.dp, ControlData.KeyCode.KEYBOARD_B, inputBridge)
                VirtualKey("N", 28.dp, ControlData.KeyCode.KEYBOARD_N, inputBridge)
                VirtualKey("M", 28.dp, ControlData.KeyCode.KEYBOARD_M, inputBridge)
                VirtualKey(",", 28.dp, ControlData.KeyCode.KEYBOARD_COMMA, inputBridge)
                VirtualKey(".", 28.dp, ControlData.KeyCode.KEYBOARD_PERIOD, inputBridge)
                VirtualKey("/", 28.dp, ControlData.KeyCode.KEYBOARD_SLASH, inputBridge)
                VirtualKey("Shift", 60.dp, ControlData.KeyCode.KEYBOARD_RSHIFT, inputBridge)
                Spacer(modifier = Modifier.width(34.dp))
                VirtualKey("↑", 28.dp, ControlData.KeyCode.KEYBOARD_UP, inputBridge)
            }
            
            // 第六行：Ctrl + 空格行
            KeyboardRow {
                VirtualKey("Ctrl", 38.dp, ControlData.KeyCode.KEYBOARD_LCTRL, inputBridge)
                VirtualKey("Win", 32.dp, ControlData.KeyCode.KEYBOARD_LGUI, inputBridge)
                VirtualKey("Alt", 32.dp, ControlData.KeyCode.KEYBOARD_LALT, inputBridge)
                VirtualKey("Space", 168.dp, ControlData.KeyCode.KEYBOARD_SPACE, inputBridge)
                VirtualKey("Alt", 32.dp, ControlData.KeyCode.KEYBOARD_RALT, inputBridge)
                VirtualKey("Win", 32.dp, ControlData.KeyCode.KEYBOARD_RGUI, inputBridge)
                VirtualKey("Fn", 32.dp, ControlData.KeyCode.KEYBOARD_APPLICATION, inputBridge)
                VirtualKey("Ctrl", 38.dp, ControlData.KeyCode.KEYBOARD_RCTRL, inputBridge)
                Spacer(modifier = Modifier.width(6.dp))
                VirtualKey("←", 28.dp, ControlData.KeyCode.KEYBOARD_LEFT, inputBridge)
                VirtualKey("↓", 28.dp, ControlData.KeyCode.KEYBOARD_DOWN, inputBridge)
                VirtualKey("→", 28.dp, ControlData.KeyCode.KEYBOARD_RIGHT, inputBridge)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 鼠标按键行
            KeyboardRow {
                VirtualMouseKey(stringResource(R.string.key_mouse_left), 50.dp, ControlData.KeyCode.MOUSE_LEFT, inputBridge)
                VirtualMouseKey(stringResource(R.string.key_mouse_middle), 50.dp, ControlData.KeyCode.MOUSE_MIDDLE, inputBridge)
                VirtualMouseKey(stringResource(R.string.key_mouse_right), 50.dp, ControlData.KeyCode.MOUSE_RIGHT, inputBridge)
                VirtualKey("MW↑", 50.dp, ControlData.KeyCode.MOUSE_WHEEL_UP, inputBridge)
                VirtualKey("MW↓", 50.dp, ControlData.KeyCode.MOUSE_WHEEL_DOWN, inputBridge)
            }
        }
    }
}

@Composable
private fun KeyboardRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.height(28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun VirtualKey(
    text: String,
    width: Dp,
    keyCode: ControlData.KeyCode,
    inputBridge: ControlInputBridge?
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .width(width)
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPressed) MaterialTheme.colorScheme.outline else Color.White
            )
            .pointerInput(keyCode) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        AppLog.d(TAG, "Key pressed: $keyCode")
                        inputBridge?.sendKey(keyCode, true)
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                            AppLog.d(TAG, "Key released: $keyCode")
                            inputBridge?.sendKey(keyCode, false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun VirtualMouseKey(
    text: String,
    width: Dp,
    mouseButton: ControlData.KeyCode,
    inputBridge: ControlInputBridge?
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .width(width)
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPressed) MaterialTheme.colorScheme.outline else Color.White
            )
            .pointerInput(mouseButton) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        AppLog.d(TAG, "Mouse button pressed: $mouseButton")
                        inputBridge?.sendMouseButton(mouseButton, true)
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                            AppLog.d(TAG, "Mouse button released: $mouseButton")
                            inputBridge?.sendMouseButton(mouseButton, false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
