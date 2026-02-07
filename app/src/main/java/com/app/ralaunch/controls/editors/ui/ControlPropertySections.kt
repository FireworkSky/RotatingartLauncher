package com.app.ralaunch.controls.editors.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.controls.data.ControlData

/**
 * 按钮控件属性区
 */
@Composable
fun ButtonPropertySection(
    control: ControlData.Button,
    onUpdate: (ControlData) -> Unit,
    onOpenKeySelector: ((ControlData.Button) -> Unit)?,
    onOpenTextureSelector: ((ControlData, String) -> Unit)?,
    onOpenPolygonEditor: ((ControlData.Button) -> Unit)?
) {
    PropertySection(title = "按钮设置") {
        // 按键选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("绑定按键", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onOpenKeySelector?.invoke(control) }
            ) {
                Text(control.keycode.name.removePrefix("KEYBOARD_").removePrefix("MOUSE_").removePrefix("GAMEPAD_"))
            }
        }
        
        // 输入模式选择
        Text("输入模式", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.KEYBOARD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.mode = ControlData.Button.Mode.KEYBOARD
                    onUpdate(updated)
                },
                label = { Text("键盘") }
            )
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.GAMEPAD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.mode = ControlData.Button.Mode.GAMEPAD
                    onUpdate(updated)
                },
                label = { Text("手柄") }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("切换模式 (Toggle)", style = MaterialTheme.typography.bodyMedium)
                Text("按下后保持状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = control.isToggle,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.isToggle = it
                    onUpdate(updated)
                }
            )
        }

        Text("形状", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.shape == ControlData.Button.Shape.RECTANGLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.shape = ControlData.Button.Shape.RECTANGLE
                    onUpdate(updated)
                },
                label = { Text("矩形") }
            )
            FilterChip(
                selected = control.shape == ControlData.Button.Shape.CIRCLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.shape = ControlData.Button.Shape.CIRCLE
                    onUpdate(updated)
                },
                label = { Text("圆形") }
            )
            FilterChip(
                selected = control.shape == ControlData.Button.Shape.POLYGON,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.shape = ControlData.Button.Shape.POLYGON
                    if (updated.polygonPoints.isEmpty()) {
                        updated.polygonPoints = listOf(
                            ControlData.Button.Point(0.5f, 0.1f),
                            ControlData.Button.Point(0.9f, 0.9f),
                            ControlData.Button.Point(0.1f, 0.9f)
                        )
                    }
                    onUpdate(updated)
                },
                label = { Text("多边形") }
            )
        }
        
        if (control.shape == ControlData.Button.Shape.POLYGON) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenPolygonEditor?.invoke(control) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("编辑多边形 (${control.polygonPoints.size}个顶点)")
            }
        }
    }

    // 按钮纹理设置
    PropertySection(title = "纹理") {
        TextureSettingItem(
            label = "普通状态",
            hasTexture = control.texture.normal.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "normal") }
        )
        TextureSettingItem(
            label = "按下状态",
            hasTexture = control.texture.pressed.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "pressed") }
        )
        if (control.isToggle) {
            TextureSettingItem(
                label = "切换状态",
                hasTexture = control.texture.toggled.enabled,
                onClick = { onOpenTextureSelector?.invoke(control, "toggled") }
            )
        }
        
        if (control.texture.normal.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自定义形状", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "使用纹理透明度作为控件形状，透明区域不响应点击",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = control.useTextureAlphaHitTest,
                    onCheckedChange = {
                        val updated = control.deepCopy() as ControlData.Button
                        updated.useTextureAlphaHitTest = it
                        onUpdate(updated)
                    }
                )
            }
        }
    }
}

/**
 * 摇杆控件属性区
 */
@Composable
fun JoystickPropertySection(
    control: ControlData.Joystick,
    onUpdate: (ControlData) -> Unit,
    onOpenJoystickKeyMapping: ((ControlData.Joystick) -> Unit)?,
    onOpenTextureSelector: ((ControlData, String) -> Unit)?
) {
    PropertySection(title = "摇杆设置") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("键位映射", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onOpenJoystickKeyMapping?.invoke(control) }
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("设置")
            }
        }

        PropertySlider(
            label = "摇杆球大小",
            value = control.stickKnobSize,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.Joystick
                updated.stickKnobSize = it
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = "摇杆球透明度",
            value = control.stickOpacity,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.Joystick
                updated.stickOpacity = it
                onUpdate(updated)
            }
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("右摇杆模式", style = MaterialTheme.typography.bodyMedium)
                Text("手柄模式下用于右摇杆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = control.isRightStick,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.isRightStick = it
                    onUpdate(updated)
                }
            )
        }

        Text("输入模式", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.KEYBOARD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.KEYBOARD
                    onUpdate(updated)
                },
                label = { Text("键盘") }
            )
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.GAMEPAD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.GAMEPAD
                    onUpdate(updated)
                },
                label = { Text("手柄") }
            )
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.MOUSE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.MOUSE
                    onUpdate(updated)
                },
                label = { Text("鼠标") }
            )
        }
        
        if (control.mode == ControlData.Joystick.Mode.MOUSE) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            MouseModeSettings()
        }
    }

    // 摇杆纹理设置
    PropertySection(title = "纹理") {
        TextureSettingItem(
            label = "背景",
            hasTexture = control.texture.background.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "background") }
        )
        TextureSettingItem(
            label = "摇杆球",
            hasTexture = control.texture.knob.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "knob") }
        )
    }
}

/**
 * 触控板控件属性区
 */
@Composable
fun TouchPadPropertySection(
    control: ControlData.TouchPad,
    onUpdate: (ControlData) -> Unit,
    onOpenTextureSelector: ((ControlData, String) -> Unit)?
) {
    PropertySection(title = "触控板设置") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("双指点击模拟摇杆", style = MaterialTheme.typography.bodyMedium)
                Text("双指点击时模拟摇杆移动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = control.isDoubleClickSimulateJoystick,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.TouchPad
                    updated.isDoubleClickSimulateJoystick = it
                    onUpdate(updated)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        MouseModeSettings()
    }
    
    PropertySection(title = "纹理") {
        TextureSettingItem(
            label = "背景",
            hasTexture = control.texture.background.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "background") }
        )
    }
}

/**
 * 滚轮控件属性区
 */
@Composable
fun MouseWheelPropertySection(
    control: ControlData.MouseWheel,
    onUpdate: (ControlData) -> Unit,
    onOpenTextureSelector: ((ControlData, String) -> Unit)?
) {
    PropertySection(title = "滚轮设置") {
        Text("滚轮方向", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.orientation == ControlData.MouseWheel.Orientation.VERTICAL,
                onClick = {
                    val updated = control.deepCopy() as ControlData.MouseWheel
                    updated.orientation = ControlData.MouseWheel.Orientation.VERTICAL
                    onUpdate(updated)
                },
                label = { Text("垂直") }
            )
            FilterChip(
                selected = control.orientation == ControlData.MouseWheel.Orientation.HORIZONTAL,
                onClick = {
                    val updated = control.deepCopy() as ControlData.MouseWheel
                    updated.orientation = ControlData.MouseWheel.Orientation.HORIZONTAL
                    onUpdate(updated)
                },
                label = { Text("水平") }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("反转滚动方向", style = MaterialTheme.typography.bodyMedium)
                Text("上滑变下滚，左滑变右滚", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = control.reverseDirection,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.MouseWheel
                    updated.reverseDirection = it
                    onUpdate(updated)
                }
            )
        }
        
        PropertySlider(
            label = "灵敏度",
            value = (control.scrollSensitivity - 10f) / 90f,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.MouseWheel
                updated.scrollSensitivity = 10f + it * 90f
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = "速度倍率",
            value = (control.scrollRatio - 0.1f) / 4.9f,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.MouseWheel
                updated.scrollRatio = 0.1f + it * 4.9f
                onUpdate(updated)
            }
        )
    }
    
    PropertySection(title = "纹理") {
        TextureSettingItem(
            label = "背景",
            hasTexture = control.texture.background.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "background") }
        )
    }
}

/**
 * 文本控件属性区
 */
@Composable
fun TextPropertySection(
    control: ControlData.Text,
    onUpdate: (ControlData) -> Unit,
    onOpenTextureSelector: ((ControlData, String) -> Unit)?
) {
    PropertySection(title = "文本设置") {
        OutlinedTextField(
            value = control.displayText,
            onValueChange = { 
                val updated = control.deepCopy() as ControlData.Text
                updated.displayText = it
                onUpdate(updated)
            },
            label = { Text("显示文本") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("形状", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.shape == ControlData.Text.Shape.RECTANGLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Text
                    updated.shape = ControlData.Text.Shape.RECTANGLE
                    onUpdate(updated)
                },
                label = { Text("矩形") }
            )
            FilterChip(
                selected = control.shape == ControlData.Text.Shape.CIRCLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Text
                    updated.shape = ControlData.Text.Shape.CIRCLE
                    onUpdate(updated)
                },
                label = { Text("圆形") }
            )
        }
    }
    
    PropertySection(title = "纹理") {
        TextureSettingItem(
            label = "背景",
            hasTexture = control.texture.background.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "background") }
        )
    }
}

/**
 * 轮盘控件属性区
 */
@Composable
fun RadialMenuPropertySection(
    control: ControlData.RadialMenu,
    onUpdate: (ControlData) -> Unit
) {
    PropertySection(title = "轮盘设置") {
        Text("扇区数量: ${control.sectorCount}", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = control.sectorCount.toFloat(),
            onValueChange = {
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.sectorCount = it.toInt().coerceIn(4, 12)
                while (updated.sectors.size < updated.sectorCount) {
                    updated.sectors.add(ControlData.RadialMenu.Sector(
                        keycode = ControlData.KeyCode.UNKNOWN,
                        label = "${updated.sectors.size + 1}"
                    ))
                }
                onUpdate(updated)
            },
            valueRange = 4f..12f,
            steps = 7
        )
        
        PropertySlider(
            label = "展开大小",
            value = control.expandedScale / 4f,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.expandedScale = newValue * 4f
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = "中心死区",
            value = control.deadZoneRatio,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.deadZoneRatio = newValue
                onUpdate(updated)
            }
        )

        Text("展开动画: ${control.expandDuration}ms", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = control.expandDuration.toFloat(),
            onValueChange = {
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.expandDuration = it.toInt()
                onUpdate(updated)
            },
            valueRange = 50f..500f,
            steps = 8
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("显示分隔线", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = control.showDividers,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.RadialMenu
                    updated.showDividers = it
                    onUpdate(updated)
                }
            )
        }
    }

    PropertySection(title = "颜色") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选中高亮", style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(control.selectedColor))
                    .clickable {
                        // TODO: 接入颜色选择器
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("分隔线颜色", style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(control.dividerColor))
                    .clickable {
                        // TODO: 接入颜色选择器
                    }
            )
        }
    }
    
    PropertySection(title = "扇区按键绑定") {
        val sectorCount = control.sectorCount.coerceAtMost(control.sectors.size)
        for (i in 0 until sectorCount) {
            val sector = control.sectors[i]
            RadialMenuSectorRow(
                index = i,
                sector = sector,
                onSectorChange = { updatedSector ->
                    val updated = control.deepCopy() as ControlData.RadialMenu
                    updated.sectors[i] = updatedSector
                    onUpdate(updated)
                }
            )
            if (i < sectorCount - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * 十字键控件属性区
 */
@Composable
fun DPadPropertySection(
    control: ControlData.DPad,
    onUpdate: (ControlData) -> Unit
) {
    PropertySection(title = "十字键设置") {
        Text("样式", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlData.DPad.Style.entries.forEach { style ->
                FilterChip(
                    selected = control.style == style,
                    onClick = {
                        val updated = control.deepCopy() as ControlData.DPad
                        updated.style = style
                        onUpdate(updated)
                    },
                    label = {
                        Text(
                            when (style) {
                                ControlData.DPad.Style.CROSS -> "十字"
                                ControlData.DPad.Style.ROUND -> "圆形"
                                ControlData.DPad.Style.SQUARE -> "方形"
                            }
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        PropertySlider(
            label = "按钮大小",
            value = control.buttonSize,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.buttonSize = newValue
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = "按钮间距",
            value = control.buttonSpacing / 0.2f,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.buttonSpacing = newValue * 0.2f
                onUpdate(updated)
            }
        )
        
        if (control.style == ControlData.DPad.Style.ROUND) {
            PropertySlider(
                label = "中心死区",
                value = control.deadZone / 0.5f,
                onValueChange = { newValue ->
                    val updated = control.deepCopy() as ControlData.DPad
                    updated.deadZone = newValue * 0.5f
                    onUpdate(updated)
                }
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("允许斜向", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = control.allowDiagonal,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.DPad
                    updated.allowDiagonal = it
                    onUpdate(updated)
                }
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("显示方向标签", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = control.showLabels,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.DPad
                    updated.showLabels = it
                    onUpdate(updated)
                }
            )
        }
    }
    
    PropertySection(title = "方向按键") {
        DPadKeyRow(
            label = "↑ 上",
            keycode = control.upKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.upKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = "↓ 下",
            keycode = control.downKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.downKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = "← 左",
            keycode = control.leftKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.leftKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = "→ 右",
            keycode = control.rightKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.rightKeycode = keycode
                onUpdate(updated)
            }
        )
    }
    
    PropertySection(title = "输入模式") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.KEYBOARD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.DPad
                    updated.mode = ControlData.Button.Mode.KEYBOARD
                    onUpdate(updated)
                },
                label = { Text("键盘") }
            )
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.GAMEPAD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.DPad
                    updated.mode = ControlData.Button.Mode.GAMEPAD
                    onUpdate(updated)
                },
                label = { Text("手柄") }
            )
        }
    }
}
