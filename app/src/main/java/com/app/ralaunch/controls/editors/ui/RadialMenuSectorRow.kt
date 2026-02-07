package com.app.ralaunch.controls.editors.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.ui.compose.dialogs.KeyBindingDialog

/**
 * 轮盘扇区配置行 - 按键绑定（复用 KeyBindingDialog）+ 标签编辑
 */
@Composable
fun RadialMenuSectorRow(
    index: Int,
    sector: ControlData.RadialMenu.Sector,
    onSectorChange: (ControlData.RadialMenu.Sector) -> Unit
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    var isEditingLabel by remember { mutableStateOf(false) }
    var editLabel by remember(sector.label) { mutableStateOf(sector.label) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "扇区 ${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 标签编辑
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("标签", style = MaterialTheme.typography.bodySmall)
            if (isEditingLabel) {
                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it },
                    modifier = Modifier.width(120.dp).height(48.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onSectorChange(sector.copy(label = editLabel))
                                isEditingLabel = false
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "确认", modifier = Modifier.size(16.dp))
                        }
                    }
                )
            } else {
                OutlinedButton(
                    onClick = { isEditingLabel = true },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        sector.label.ifEmpty { "未设置" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 按键绑定
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("按键", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { showKeyDialog = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    sector.keycode.name
                        .removePrefix("KEYBOARD_")
                        .removePrefix("MOUSE_")
                        .removePrefix("XBOX_BUTTON_")
                        .removePrefix("XBOX_TRIGGER_")
                        .removePrefix("SPECIAL_")
                        .let { if (it == "UNKNOWN") "未绑定" else it },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }

    if (showKeyDialog) {
        KeyBindingDialog(
            initialGamepadMode = sector.keycode.type == ControlData.KeyType.GAMEPAD,
            onKeySelected = { keyCode, displayName ->
                onSectorChange(sector.copy(
                    keycode = keyCode,
                    label = if (sector.label.isEmpty()) displayName else sector.label
                ))
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false }
        )
    }
}
