package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.KeyMapper
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.controls.textures.TextureLoader
import org.koin.compose.koinInject
import java.io.File

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
                    ControlData.KeyCode.KEYBOARD_UP to stringResource(R.string.key_arrow_up),
                    ControlData.KeyCode.KEYBOARD_DOWN to stringResource(R.string.key_arrow_down),
                    ControlData.KeyCode.KEYBOARD_LEFT to stringResource(R.string.key_arrow_left),
                    ControlData.KeyCode.KEYBOARD_RIGHT to stringResource(R.string.key_arrow_right),
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_UP to stringResource(R.string.control_editor_gamepad_arrow_up),
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN to stringResource(R.string.control_editor_gamepad_arrow_down),
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT to stringResource(R.string.control_editor_gamepad_arrow_left),
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT to stringResource(R.string.control_editor_gamepad_arrow_right)
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
        title = { Text(stringResource(R.string.editor_exit_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.control_editor_unsaved_changes_exit_confirm)) },
        confirmButton = {
            Button(
                onClick = onSaveAndExit,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.editor_save_and_exit))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                OutlinedButton(
                    onClick = onExitWithoutSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.control_editor_do_not_save))
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
    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.85f
    val contentScrollState = rememberScrollState()
    val directions = listOf(
        stringResource(R.string.key_arrow_up),
        stringResource(R.string.key_arrow_right),
        stringResource(R.string.key_arrow_down),
        stringResource(R.string.key_arrow_left)
    )
    
    var keys by remember { mutableStateOf(currentKeys.clone()) }
    var selectingDirectionIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        val detailsScrollState = rememberScrollState()
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 960.dp)
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.editor_joystick_key_mapping),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            label = { Text(stringResource(R.string.control_editor_wasd_keys)) }
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
                            label = { Text(stringResource(R.string.control_editor_arrow_keys_compact)) }
                        )
                    }

                    HorizontalDivider()

                    directions.forEachIndexed { index, direction ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                direction,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            val keyCode = keys.getOrNull(index)
                            val displayName = keyCode?.let { KeyMapper.getKeyName(context, it) }
                                ?: stringResource(R.string.editor_combo_keys_not_set)

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
                                Text(
                                    text = displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onUpdateKeys(keys) }) {
                        Text(stringResource(R.string.confirm))
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
                    Text(
                        stringResource(R.string.control_editor_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.control_editor_grid_display), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.control_editor_grid_align_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Text(stringResource(R.string.control_editor_snap_threshold), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.control_editor_snap_sensitivity_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.control_editor_snap_threshold_value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.control_editor_done))
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
                text = if (hasTexture) {
                    stringResource(R.string.control_texture_enabled)
                } else {
                    stringResource(R.string.control_texture_disabled)
                },
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
    packId: String?,
    onUpdateButtonTexture: (ControlData.Button, String, String, Boolean) -> Unit,
    onUpdateJoystickTexture: (ControlData.Joystick, String, String, Boolean) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packManager: ControlPackManager = koinInject()
    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.85f
    val detailsScrollState = rememberScrollState()
    val contentScrollState = rememberScrollState()
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
    val previewBitmap = remember(packId, currentPath) {
        val assetsDir = packId?.let { packManager.getPackAssetsDir(it) } ?: return@remember null
        if (currentPath.isBlank()) return@remember null
        val textureFile = File(assetsDir, currentPath)
        if (!textureFile.exists()) return@remember null
        TextureLoader.getInstance(context).loadTexture(
            path = textureFile.absolutePath,
            targetWidth = 640,
            targetHeight = 640
        )?.asImageBitmap()
    }

    val textureTypeName = when (textureType) {
        "normal" -> stringResource(R.string.control_texture_normal)
        "pressed" -> stringResource(R.string.control_texture_pressed)
        "toggled" -> stringResource(R.string.control_texture_toggled)
        "background" -> stringResource(R.string.control_texture_background)
        "knob" -> stringResource(R.string.control_texture_knob)
        "backgroundPressed" -> stringResource(R.string.control_editor_texture_background_pressed)
        "knobPressed" -> stringResource(R.string.control_editor_texture_knob_pressed)
        else -> textureType
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 960.dp)
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.control_editor_custom_texture),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(textureTypeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .verticalScroll(detailsScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.control_editor_texture_set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (currentPath.isNotEmpty()) {
                                        currentPath.substringAfterLast("/")
                                    } else {
                                        stringResource(R.string.control_texture_disabled)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.control_layout_preview),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.15f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap,
                                            contentDescription = currentPath.substringAfterLast("/"),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ImageNotSupported,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (currentPath.isNotEmpty()) {
                                                    currentPath.substringAfterLast("/")
                                                } else {
                                                    stringResource(R.string.control_texture_disabled)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .verticalScroll(contentScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.control_editor_enable_custom_texture), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = textureTypeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = textureEnabled,
                                onCheckedChange = { textureEnabled = it }
                            )
                        }

                        Button(
                            onClick = onPickImage,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = textureEnabled
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (currentPath.isEmpty()) {
                                    stringResource(R.string.control_editor_select_image)
                                } else {
                                    stringResource(R.string.control_editor_change_image)
                                }
                            )
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
                                Text(stringResource(R.string.control_texture_clear))
                            }
                        }

                    }
                }

            }
        }
    }
}
