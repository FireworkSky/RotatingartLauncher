package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R

@Composable
fun FloatingBall(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ballScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ballRotation"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .graphicsLayer { rotationZ = rotation }
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ral),
            contentDescription = "菜单",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {}
    }
}

@Composable
fun ActionWindowMenu(
    isPaletteVisible: Boolean,
    isGhostMode: Boolean,
    isGridVisible: Boolean = true,
    onTogglePalette: () -> Unit,
    onToggleGhostMode: () -> Unit,
    onToggleGrid: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSave: () -> Unit,
    onCloseMenu: () -> Unit,
    onExit: () -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .width(240.dp)
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "快捷菜单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onCloseMenu, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
                }
            }
            
            HorizontalDivider(modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .verticalScroll(scrollState)
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuRowItem(
                    icon = Icons.Default.AddCircle,
                    label = "组件库",
                    isActive = isPaletteVisible,
                    onClick = onTogglePalette
                )
                
                MenuRowItem(
                    icon = if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    label = "幽灵模式",
                    isActive = isGhostMode,
                    onClick = onToggleGhostMode
                )

                MenuRowItem(
                    icon = if (isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                    label = "网格显示",
                    isActive = isGridVisible,
                    onClick = onToggleGrid
                )

                MenuRowItem(
                    icon = Icons.Default.Settings,
                    label = "编辑器设置",
                    isActive = false,
                    onClick = onOpenSettings
                )

                HorizontalDivider(modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

                MenuRowItem(
                    icon = Icons.Default.Save,
                    label = "保存布局",
                    isActive = false,
                    onClick = onSave,
                    tint = MaterialTheme.colorScheme.primary
                )

                MenuRowItem(
                    icon = Icons.Default.ExitToApp,
                    label = "退出编辑器",
                    isActive = false,
                    onClick = onExit,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MenuRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (isActive) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ComponentPalette(
    onAddControl: (String) -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .width(240.dp)
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 12.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("组件库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .verticalScroll(scrollState)
                    .weight(1f, fill = false),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PaletteItem(Icons.Default.RadioButtonChecked, "按钮", "button", onAddControl)
                    PaletteItem(Icons.Default.Games, "摇杆", "joystick", onAddControl)
                    PaletteItem(Icons.Default.TouchApp, "触控", "touchpad", onAddControl)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PaletteItem(Icons.Default.Mouse, "滚轮", "mousewheel", onAddControl)
                    PaletteItem(Icons.Default.TextFields, "文本", "text", onAddControl)
                    PaletteItem(Icons.Default.DonutLarge, "轮盘", "radialmenu", onAddControl)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PaletteItem(Icons.Default.Gamepad, "十字键", "dpad", onAddControl)
                }
            }
        }
    }
}

@Composable
fun PaletteItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    type: String,
    onAdd: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAdd(type) }
            .padding(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(12.dp).size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
