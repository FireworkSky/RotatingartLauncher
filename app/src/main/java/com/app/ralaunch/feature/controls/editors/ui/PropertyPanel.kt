package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.core.common.SettingsAccess

@Composable
fun PropertyPanel(
    control: ControlData?,
    onUpdate: (ControlData) -> Unit,
    onClose: () -> Unit,
    onOpenKeySelector: ((ControlData.Button) -> Unit)? = null,
    onOpenJoystickKeyMapping: ((ControlData.Joystick) -> Unit)? = null,
    onOpenTextureSelector: ((ControlData, String) -> Unit)? = null,
    onOpenPolygonEditor: ((ControlData.Button) -> Unit)? = null,
    onDrag: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 12.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 可拖动的标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onDrag != null) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                                }
                            }
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onDrag != null) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = stringResource(R.string.control_editor_drag),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.editor_edit_control_properties),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onDuplicate != null) {
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.editor_copy))
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            if (control != null) {
                PropertySection(title = stringResource(R.string.editor_category_basic)) {
                    OutlinedTextField(
                        value = control.name,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { name = it }
                            onUpdate(updated)
                        },
                        label = { Text(stringResource(R.string.editor_control_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    PropertySlider(
                        label = stringResource(R.string.editor_opacity_title),
                        value = control.opacity,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { opacity = it }
                            onUpdate(updated)
                        }
                    )
                }

                // ===== 尺寸与位置 =====
                PropertySection(title = stringResource(R.string.editor_category_position)) {
                    PropertySlider(
                        label = stringResource(R.string.editor_position_x),
                        value = control.x,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { x = it }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = stringResource(R.string.editor_position_y),
                        value = control.y,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { y = it }
                            onUpdate(updated)
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.editor_auto_size), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = control.isSizeRatioLocked,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { 
                                    isSizeRatioLocked = it 
                                }
                                onUpdate(updated)
                            }
                        )
                    }
                    
                    PropertySlider(
                        label = stringResource(R.string.editor_width),
                        value = control.width,
                        valueRange = 0.02f..0.5f,
                        onValueChange = { newWidth ->
                            val updated = control.deepCopy().apply { 
                                width = newWidth
                                if (isSizeRatioLocked) {
                                    height = newWidth
                                }
                            }
                            onUpdate(updated)
                        }
                    )
                    
                    PropertySlider(
                        label = stringResource(R.string.editor_height),
                        value = control.height,
                        valueRange = 0.02f..0.5f,
                        onValueChange = { newHeight ->
                            val updated = control.deepCopy().apply { 
                                height = newHeight
                                if (isSizeRatioLocked) {
                                    width = newHeight
                                }
                            }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = stringResource(R.string.editor_rotation),
                        value = control.rotation / 360f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { rotation = it * 360f }
                            onUpdate(updated)
                        }
                    )
                }

                // ===== 各控件类型专属属性 =====
                when (control) {
                    is ControlData.Button -> {
                        ButtonPropertySection(
                            control = control,
                            onUpdate = onUpdate,
                            onOpenKeySelector = onOpenKeySelector,
                            onOpenTextureSelector = onOpenTextureSelector,
                            onOpenPolygonEditor = onOpenPolygonEditor
                        )
                    }
                    is ControlData.Joystick -> {
                        JoystickPropertySection(
                            control = control,
                            onUpdate = onUpdate,
                            onOpenJoystickKeyMapping = onOpenJoystickKeyMapping,
                            onOpenTextureSelector = onOpenTextureSelector
                        )
                    }
                    is ControlData.TouchPad -> {
                        TouchPadPropertySection(
                            control = control,
                            onUpdate = onUpdate,
                            onOpenTextureSelector = onOpenTextureSelector
                        )
                    }
                    is ControlData.MouseWheel -> {
                        MouseWheelPropertySection(
                            control = control,
                            onUpdate = onUpdate,
                            onOpenTextureSelector = onOpenTextureSelector
                        )
                    }
                    is ControlData.Text -> {
                        TextPropertySection(
                            control = control,
                            onUpdate = onUpdate,
                            onOpenTextureSelector = onOpenTextureSelector
                        )
                    }
                    is ControlData.RadialMenu -> {
                        RadialMenuPropertySection(
                            control = control,
                            onUpdate = onUpdate
                        )
                    }
                    is ControlData.DPad -> {
                        DPadPropertySection(
                            control = control,
                            onUpdate = onUpdate
                        )
                    }
                }

                // ===== 外观设置 =====
                PropertySection(title = stringResource(R.string.editor_category_appearance)) {
                    ColorPickerRow(
                        label = stringResource(R.string.editor_bg_color),
                        color = Color(control.bgColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { bgColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    ColorPickerRow(
                        label = stringResource(R.string.editor_stroke_color),
                        color = Color(control.strokeColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { strokeColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    ColorPickerRow(
                        label = stringResource(R.string.control_editor_text_color),
                        color = Color(control.textColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { textColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    PropertySlider(
                        label = stringResource(R.string.editor_corner_radius),
                        value = control.cornerRadius / 50f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { cornerRadius = it * 50f }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = stringResource(R.string.control_editor_border_width),
                        value = control.strokeWidth / 10f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { strokeWidth = it * 10f }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = stringResource(R.string.editor_border_opacity),
                        value = control.borderOpacity,
                        onValueChange = {
                            val updated = control.deepCopy().apply { borderOpacity = it }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = stringResource(R.string.editor_text_opacity),
                        value = control.textOpacity,
                        onValueChange = {
                            val updated = control.deepCopy().apply { textOpacity = it }
                            onUpdate(updated)
                        }
                    )
                }

                // ===== 高级设置 =====
                PropertySection(title = stringResource(R.string.control_editor_advanced)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.editor_visible_in_game), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.control_editor_visibility_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = control.isVisible,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { isVisible = it }
                                onUpdate(updated)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.editor_pass_through), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.editor_pass_through_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = control.isPassThrough,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { isPassThrough = it }
                                onUpdate(updated)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PropertySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
    }
}

@Composable
fun PropertySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

/**
 * 鼠标模式设置组件 - 用于摇杆和触控板的鼠标速度和范围设置
 */
@Composable
fun MouseModeSettings() {
    val settingsManager = remember { SettingsAccess }
    
    var mouseSpeed by remember { mutableStateOf(settingsManager.mouseRightStickSpeed) }
    var rangeLeft by remember { mutableStateOf(settingsManager.mouseRightStickRangeLeft) }
    var rangeTop by remember { mutableStateOf(settingsManager.mouseRightStickRangeTop) }
    var rangeRight by remember { mutableStateOf(settingsManager.mouseRightStickRangeRight) }
    var rangeBottom by remember { mutableStateOf(settingsManager.mouseRightStickRangeBottom) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_mouse_speed), style = MaterialTheme.typography.labelMedium)
                Text("$mouseSpeed", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = (mouseSpeed - 60f) / 140f,
                onValueChange = { 
                    mouseSpeed = (60 + (it * 140)).toInt()
                    settingsManager.mouseRightStickSpeed = mouseSpeed
                },
                valueRange = 0f..1f
            )
        }
        
        Text(stringResource(R.string.editor_mouse_range), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(R.string.control_editor_mouse_range_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_mouse_range_left), style = MaterialTheme.typography.labelSmall)
                Text("${(rangeLeft * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeLeft,
                onValueChange = { 
                    rangeLeft = it
                    settingsManager.mouseRightStickRangeLeft = it
                },
                valueRange = 0f..1f
            )
        }
        
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_mouse_range_top), style = MaterialTheme.typography.labelSmall)
                Text("${(rangeTop * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeTop,
                onValueChange = { 
                    rangeTop = it
                    settingsManager.mouseRightStickRangeTop = it
                },
                valueRange = 0f..1f
            )
        }
        
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_mouse_range_right), style = MaterialTheme.typography.labelSmall)
                Text("${(rangeRight * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeRight,
                onValueChange = { 
                    rangeRight = it
                    settingsManager.mouseRightStickRangeRight = it
                },
                valueRange = 0f..1f
            )
        }
        
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.editor_mouse_range_bottom), style = MaterialTheme.typography.labelSmall)
                Text("${(rangeBottom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeBottom,
                onValueChange = { 
                    rangeBottom = it
                    settingsManager.mouseRightStickRangeBottom = it
                },
                valueRange = 0f..1f
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = {
                    rangeLeft = 1f; rangeTop = 1f; rangeRight = 1f; rangeBottom = 1f
                    settingsManager.mouseRightStickRangeLeft = 1f
                    settingsManager.mouseRightStickRangeTop = 1f
                    settingsManager.mouseRightStickRangeRight = 1f
                    settingsManager.mouseRightStickRangeBottom = 1f
                },
                label = { Text(stringResource(R.string.control_editor_full_screen)) }
            )
            SuggestionChip(
                onClick = {
                    rangeLeft = 0.5f; rangeTop = 0.5f; rangeRight = 0.5f; rangeBottom = 0.5f
                    settingsManager.mouseRightStickRangeLeft = 0.5f
                    settingsManager.mouseRightStickRangeTop = 0.5f
                    settingsManager.mouseRightStickRangeRight = 0.5f
                    settingsManager.mouseRightStickRangeBottom = 0.5f
                },
                label = { Text(stringResource(R.string.control_editor_half_screen)) }
            )
        }
    }
}

/**
 * 颜色选择行 - 显示颜色预览和触发颜色选择器
 */
@Composable
fun ColorPickerRow(
    label: String,
    color: Color,
    onColorSelected: (Color) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("#%08X", color.toArgb()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 带棋盘格底色的颜色预览（半透明颜色也能看清）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF404040), Color(0xFF606060))
                        )
                    )
                    .clickable { showColorPicker = true }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
    }
    
    if (showColorPicker) {
        com.app.ralaunch.shared.core.component.dialogs.ColorPickerDialog(
            currentColor = color.toArgb(),
            onSelect = { selectedColor ->
                onColorSelected(Color(selectedColor))
            },
            onDismiss = { showColorPicker = false }
        )
    }
}
