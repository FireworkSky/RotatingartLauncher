package com.app.ralaunch.feature.controls.editors.ui

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData

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
    PropertySection(title = stringResource(R.string.control_editor_button_settings)) {
        // 按键选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.editor_key_mapping), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onOpenKeySelector?.invoke(control) }
            ) {
                Text(control.keycode.name.removePrefix("KEYBOARD_").removePrefix("MOUSE_").removePrefix("GAMEPAD_"))
            }
        }
        
        // 输入模式选择
        Text(stringResource(R.string.control_editor_input_mode), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.KEYBOARD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.mode = ControlData.Button.Mode.KEYBOARD
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.key_selector_keyboard_mode)) }
            )
            FilterChip(
                selected = control.mode == ControlData.Button.Mode.GAMEPAD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.mode = ControlData.Button.Mode.GAMEPAD
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.key_selector_gamepad_mode)) }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_toggle_mode), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.editor_toggle_mode_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Text(stringResource(R.string.editor_control_shape), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.shape == ControlData.Button.Shape.RECTANGLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.shape = ControlData.Button.Shape.RECTANGLE
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_shape_rectangle)) }
            )
            FilterChip(
                selected = control.shape == ControlData.Button.Shape.CIRCLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Button
                    updated.shape = ControlData.Button.Shape.CIRCLE
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_shape_circle)) }
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
                label = { Text(stringResource(R.string.control_editor_polygon)) }
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
                Text(
                    stringResource(
                        R.string.control_editor_polygon_edit_with_vertex_count,
                        control.polygonPoints.size
                    )
                )
            }
        }
    }

    // 按钮纹理设置
    PropertySection(title = stringResource(R.string.control_editor_texture)) {
        TextureSettingItem(
            label = stringResource(R.string.control_texture_normal),
            hasTexture = control.texture.normal.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "normal") }
        )
        TextureSettingItem(
            label = stringResource(R.string.control_texture_pressed),
            hasTexture = control.texture.pressed.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "pressed") }
        )
        if (control.isToggle) {
            TextureSettingItem(
                label = stringResource(R.string.control_texture_toggled),
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
                    Text(stringResource(R.string.control_editor_custom_shape), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.control_editor_custom_shape_hint),
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
    PropertySection(title = stringResource(R.string.control_editor_joystick_settings)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.editor_key_mapping), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { onOpenJoystickKeyMapping?.invoke(control) }
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.control_editor_set))
            }
        }

        PropertySlider(
            label = stringResource(R.string.control_editor_joystick_knob_size),
            value = control.stickKnobSize,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.Joystick
                updated.stickKnobSize = it
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = stringResource(R.string.control_editor_joystick_knob_opacity),
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
                Text(stringResource(R.string.control_editor_right_stick_mode), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.control_editor_right_stick_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        Text(stringResource(R.string.control_editor_input_mode), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.KEYBOARD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.KEYBOARD
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.key_selector_keyboard_mode)) }
            )
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.GAMEPAD,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.GAMEPAD
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.key_selector_gamepad_mode)) }
            )
            FilterChip(
                selected = control.mode == ControlData.Joystick.Mode.MOUSE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Joystick
                    updated.mode = ControlData.Joystick.Mode.MOUSE
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_editor_mouse)) }
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
    PropertySection(title = stringResource(R.string.control_editor_texture)) {
        TextureSettingItem(
            label = stringResource(R.string.control_texture_background),
            hasTexture = control.texture.background.enabled,
            onClick = { onOpenTextureSelector?.invoke(control, "background") }
        )
        TextureSettingItem(
            label = stringResource(R.string.control_texture_knob),
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
    PropertySection(title = stringResource(R.string.control_editor_touchpad_settings)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_double_click_joystick), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.editor_double_click_joystick_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    
    PropertySection(title = stringResource(R.string.control_editor_texture)) {
        TextureSettingItem(
            label = stringResource(R.string.control_texture_background),
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
    PropertySection(title = stringResource(R.string.control_editor_mousewheel_settings)) {
        Text(stringResource(R.string.control_editor_wheel_direction), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.orientation == ControlData.MouseWheel.Orientation.VERTICAL,
                onClick = {
                    val updated = control.deepCopy() as ControlData.MouseWheel
                    updated.orientation = ControlData.MouseWheel.Orientation.VERTICAL
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_editor_vertical)) }
            )
            FilterChip(
                selected = control.orientation == ControlData.MouseWheel.Orientation.HORIZONTAL,
                onClick = {
                    val updated = control.deepCopy() as ControlData.MouseWheel
                    updated.orientation = ControlData.MouseWheel.Orientation.HORIZONTAL
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_editor_horizontal)) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_mousewheel_reverse), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.editor_mousewheel_reverse_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            label = stringResource(R.string.editor_mousewheel_sensitivity),
            value = (control.scrollSensitivity - 10f) / 90f,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.MouseWheel
                updated.scrollSensitivity = 10f + it * 90f
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = stringResource(R.string.editor_mousewheel_ratio),
            value = (control.scrollRatio - 0.1f) / 4.9f,
            onValueChange = {
                val updated = control.deepCopy() as ControlData.MouseWheel
                updated.scrollRatio = 0.1f + it * 4.9f
                onUpdate(updated)
            }
        )
    }
    
    PropertySection(title = stringResource(R.string.control_editor_texture)) {
        TextureSettingItem(
            label = stringResource(R.string.control_texture_background),
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
    PropertySection(title = stringResource(R.string.control_editor_text_settings)) {
        OutlinedTextField(
            value = control.displayText,
            onValueChange = { 
                val updated = control.deepCopy() as ControlData.Text
                updated.displayText = it
                onUpdate(updated)
            },
            label = { Text(stringResource(R.string.editor_text_content)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(stringResource(R.string.editor_control_shape), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.shape == ControlData.Text.Shape.RECTANGLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Text
                    updated.shape = ControlData.Text.Shape.RECTANGLE
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_shape_rectangle)) }
            )
            FilterChip(
                selected = control.shape == ControlData.Text.Shape.CIRCLE,
                onClick = {
                    val updated = control.deepCopy() as ControlData.Text
                    updated.shape = ControlData.Text.Shape.CIRCLE
                    onUpdate(updated)
                },
                label = { Text(stringResource(R.string.control_shape_circle)) }
            )
        }
    }
    
    PropertySection(title = stringResource(R.string.control_editor_texture)) {
        TextureSettingItem(
            label = stringResource(R.string.control_texture_background),
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
    PropertySection(title = stringResource(R.string.control_editor_radial_menu_settings)) {
        // 预览展开开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.control_editor_preview_expanded), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.control_editor_preview_expanded_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = control.editorPreviewExpanded,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.RadialMenu
                    updated.editorPreviewExpanded = it
                    // 关闭预览时重置选中扇区
                    updated.editorSelectedSector = if (it) control.editorSelectedSector else -1
                    onUpdate(updated)
                }
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        
        Text(
            stringResource(R.string.control_editor_radial_sector_count, control.sectorCount),
            style = MaterialTheme.typography.labelMedium
        )
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
                // 保持预览状态
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
                onUpdate(updated)
            },
            valueRange = 4f..12f,
            steps = 7
        )
        
        PropertySlider(
            label = stringResource(R.string.control_editor_expanded_size),
            value = control.expandedScale / 4f,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.expandedScale = newValue * 4f
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
                onUpdate(updated)
            }
        )
        
        PropertySlider(
            label = stringResource(R.string.control_editor_dead_zone),
            value = control.deadZoneRatio,
            onValueChange = { newValue ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.deadZoneRatio = newValue
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
                onUpdate(updated)
            }
        )

        Text(
            stringResource(R.string.control_editor_expand_animation_ms, control.expandDuration),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = control.expandDuration.toFloat(),
            onValueChange = {
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.expandDuration = it.toInt()
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
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
            Text(stringResource(R.string.control_editor_show_dividers), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = control.showDividers,
                onCheckedChange = {
                    val updated = control.deepCopy() as ControlData.RadialMenu
                    updated.showDividers = it
                    updated.editorPreviewExpanded = control.editorPreviewExpanded
                    updated.editorSelectedSector = control.editorSelectedSector
                    onUpdate(updated)
                }
            )
        }
    }

    PropertySection(title = stringResource(R.string.control_editor_radial_menu_colors)) {
        ColorPickerRow(
            label = stringResource(R.string.control_editor_selected_highlight),
            color = Color(control.selectedColor),
            onColorSelected = { color ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.selectedColor = color.toArgb()
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
                onUpdate(updated)
            }
        )
        
        ColorPickerRow(
            label = stringResource(R.string.control_editor_divider_color),
            color = Color(control.dividerColor),
            onColorSelected = { color ->
                val updated = control.deepCopy() as ControlData.RadialMenu
                updated.dividerColor = color.toArgb()
                updated.editorPreviewExpanded = control.editorPreviewExpanded
                updated.editorSelectedSector = control.editorSelectedSector
                onUpdate(updated)
            }
        )
    }
    
    PropertySection(title = stringResource(R.string.control_editor_sector_key_binding)) {
        val sectorCount = control.sectorCount.coerceAtMost(control.sectors.size)
        for (i in 0 until sectorCount) {
            val sector = control.sectors[i]
            val isSectorSelected = control.editorPreviewExpanded && control.editorSelectedSector == i
            RadialMenuSectorRow(
                index = i,
                sector = sector,
                isSelected = isSectorSelected,
                onSelect = if (control.editorPreviewExpanded) {
                    {
                        val updated = control.deepCopy() as ControlData.RadialMenu
                        updated.editorPreviewExpanded = control.editorPreviewExpanded
                        // 切换选中：再次点击取消选中
                        updated.editorSelectedSector = if (control.editorSelectedSector == i) -1 else i
                        onUpdate(updated)
                    }
                } else null,
                onSectorChange = { updatedSector ->
                    val updated = control.deepCopy() as ControlData.RadialMenu
                    updated.sectors[i] = updatedSector
                    updated.editorPreviewExpanded = control.editorPreviewExpanded
                    updated.editorSelectedSector = control.editorSelectedSector
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
    PropertySection(title = stringResource(R.string.control_editor_direction_keys)) {
        DPadKeyRow(
            label = stringResource(R.string.key_arrow_up),
            keycode = control.upKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.upKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = stringResource(R.string.key_arrow_down),
            keycode = control.downKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.downKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = stringResource(R.string.key_arrow_left),
            keycode = control.leftKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.leftKeycode = keycode
                onUpdate(updated)
            }
        )
        
        DPadKeyRow(
            label = stringResource(R.string.key_arrow_right),
            keycode = control.rightKeycode,
            onKeycodeChange = { keycode ->
                val updated = control.deepCopy() as ControlData.DPad
                updated.rightKeycode = keycode
                onUpdate(updated)
            }
        )
    }
    
}
