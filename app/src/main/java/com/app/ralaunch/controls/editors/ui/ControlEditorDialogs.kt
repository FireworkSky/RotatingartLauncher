package com.app.ralaunch.controls.editors.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.KeyMapper
import com.app.ralaunch.ui.compose.dialogs.KeyBindingDialog

/**
 * DPad 按键选择行
 */
@Composable
fun DPadKeyRow(
    label: String,
    keycode: ControlData.KeyCode,
    onKeycodeChange: (ControlData.KeyCode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    keycode.name
                        .removePrefix("KEYBOARD_")
                        .removePrefix("XBOX_BUTTON_"),
                    maxLines = 1
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                val commonKeys = listOf<Pair<ControlData.KeyCode, String>>(
                    ControlData.KeyCode.KEYBOARD_W to "W",
                    ControlData.KeyCode.KEYBOARD_A to "A",
                    ControlData.KeyCode.KEYBOARD_S to "S",
                    ControlData.KeyCode.KEYBOARD_D to "D",
                    ControlData.KeyCode.KEYBOARD_UP to "↑ 上",
                    ControlData.KeyCode.KEYBOARD_DOWN to "↓ 下",
                    ControlData.KeyCode.KEYBOARD_LEFT to "← 左",
                    ControlData.KeyCode.KEYBOARD_RIGHT to "→ 右",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_UP to "手柄 ↑",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN to "手柄 ↓",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT to "手柄 ←",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT to "手柄 →"
                )
                
                commonKeys.forEach { pair ->
                    val code = pair.first
                    val name = pair.second
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onKeycodeChange(code)
                            expanded = false
                        },
                        leadingIcon = {
                            if (code == keycode) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 退出确认对话框
 */
@Composable
fun ExitConfirmDialog(
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("退出编辑器", fontWeight = FontWeight.Bold) },
        text = { Text("您有未保存的更改，是否保存后退出？") },
        confirmButton = {
            Button(
                onClick = onSaveAndExit,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存并退出")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                OutlinedButton(
                    onClick = onExitWithoutSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("不保存")
                }
            }
        }
    )
}

/**
 * 摇杆键位映射对话框
 */
@Composable
fun JoystickKeyMappingDialog(
    currentKeys: Array<ControlData.KeyCode>,
    onUpdateKeys: (Array<ControlData.KeyCode>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val directions = listOf("↑ 上", "→ 右", "↓ 下", "← 左")
    
    var keys by remember { mutableStateOf(currentKeys.clone()) }
    var selectingDirectionIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("摇杆键位映射", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { 
                            keys = arrayOf(
                                ControlData.KeyCode.KEYBOARD_W,
                                ControlData.KeyCode.KEYBOARD_D,
                                ControlData.KeyCode.KEYBOARD_S,
                                ControlData.KeyCode.KEYBOARD_A
                            )
                        },
                        label = { Text("WASD") }
                    )
                    SuggestionChip(
                        onClick = { 
                            keys = arrayOf(
                                ControlData.KeyCode.KEYBOARD_UP,
                                ControlData.KeyCode.KEYBOARD_RIGHT,
                                ControlData.KeyCode.KEYBOARD_DOWN,
                                ControlData.KeyCode.KEYBOARD_LEFT
                            )
                        },
                        label = { Text("方向键 ↑←↓→") }
                    )
                }

                HorizontalDivider()

                directions.forEachIndexed { index, direction ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(direction, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        
                        val keyCode = keys.getOrNull(index)
                        val displayName = keyCode?.let { KeyMapper.getKeyName(context, it) } ?: "未设置"
                        
                        OutlinedButton(
                            onClick = { selectingDirectionIndex = index },
                            colors = if (selectingDirectionIndex == index) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(displayName)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onUpdateKeys(keys) }) {
                        Text("确定")
                    }
                }
            }
        }
    }
    
    selectingDirectionIndex?.let { dirIndex ->
        KeyBindingDialog(
            initialGamepadMode = false,
            onKeySelected = { keyCode, _ ->
                val newKeys = keys.clone()
                newKeys[dirIndex] = keyCode
                keys = newKeys
                selectingDirectionIndex = null
            },
            onDismiss = { selectingDirectionIndex = null }
        )
    }
}

/**
 * 编辑器设置对话框
 */
@Composable
fun EditorSettingsDialog(
    isGridVisible: Boolean,
    onToggleGrid: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑器设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("显示网格", style = MaterialTheme.typography.bodyLarge)
                        Text("辅助对齐控件位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isGridVisible,
                        onCheckedChange = onToggleGrid
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("吸附阈值", style = MaterialTheme.typography.bodyLarge)
                        Text("控件自动对齐灵敏度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("10px", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成")
                }
            }
        }
    }
}

/**
 * 纹理设置项组件
 */
@Composable
fun TextureSettingItem(
    label: String,
    hasTexture: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (hasTexture) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasTexture) Icons.Default.Image else Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasTexture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (hasTexture) "已设置" else "未设置",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasTexture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 纹理选择器对话框
 */
@Composable
fun TextureSelectorDialog(
    control: ControlData,
    textureType: String,
    onUpdateButtonTexture: (ControlData.Button, String, String, Boolean) -> Unit,
    onUpdateJoystickTexture: (ControlData.Joystick, String, String, Boolean) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentConfig = remember(control, textureType) {
        when (control) {
            is ControlData.Button -> when (textureType) {
                "normal" -> control.texture.normal
                "pressed" -> control.texture.pressed
                "toggled" -> control.texture.toggled
                else -> null
            }
            is ControlData.Joystick -> when (textureType) {
                "background" -> control.texture.background
                "knob" -> control.texture.knob
                "backgroundPressed" -> control.texture.backgroundPressed
                "knobPressed" -> control.texture.knobPressed
                else -> null
            }
            else -> null
        }
    }

    var textureEnabled by remember { mutableStateOf(currentConfig?.enabled ?: false) }
    val currentPath = currentConfig?.path ?: ""

    val textureTypeName = when (textureType) {
        "normal" -> "普通状态"
        "pressed" -> "按下状态"
        "toggled" -> "切换状态"
        "background" -> "背景"
        "knob" -> "摇杆球"
        "backgroundPressed" -> "按下背景"
        "knobPressed" -> "按下摇杆球"
        else -> textureType
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("自定义纹理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(textureTypeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("启用自定义纹理", style = MaterialTheme.typography.bodyLarge)
                        Text("替换默认控件外观", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = textureEnabled,
                        onCheckedChange = { textureEnabled = it }
                    )
                }

                if (currentPath.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("已设置纹理", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(currentPath.substringAfterLast("/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Button(
                    onClick = onPickImage,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = textureEnabled
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentPath.isEmpty()) "选择图片" else "更换图片")
                }

                if (currentPath.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            when (control) {
                                is ControlData.Button -> onUpdateButtonTexture(control, textureType, "", false)
                                is ControlData.Joystick -> onUpdateJoystickTexture(control, textureType, "", false)
                                else -> {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清除纹理")
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
