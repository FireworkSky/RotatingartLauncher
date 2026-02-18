package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 悬浮控件菜单模式
 */
enum class FloatingMenuMode {
    /** 独立编辑器模式 */
    EDITOR,
    /** 游戏内模式 */
    IN_GAME
}

/**
 * 联机状态
 */
enum class MultiplayerState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 悬浮菜单状态
 */
@Stable
class FloatingMenuState(
    initialOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    initialExpanded: Boolean = false
) {
    var offset by mutableStateOf(initialOffset)
    var isExpanded by mutableStateOf(initialExpanded)
    var isGhostMode by mutableStateOf(false)
    var isPaletteVisible by mutableStateOf(false)
    var isGridVisible by mutableStateOf(true)
    var isInEditMode by mutableStateOf(false)
    
    // 悬浮球可见性 (可通过返回键切换)
    var isFloatingBallVisible by mutableStateOf(true)
    
    // 控件正在使用中 (自动进入幽灵模式)
    var isControlInUse by mutableStateOf(false)
    
    // 游戏内特有状态
    var isFpsDisplayEnabled by mutableStateOf(false)
    var isTouchEventEnabled by mutableStateOf(true)
    var isControlsVisible by mutableStateOf(true)
    
    // 调试日志状态
    var isDebugLogEnabled by mutableStateOf(false)
    
    // 联机相关状态
    var isMultiplayerPanelVisible by mutableStateOf(false)
    var multiplayerConnectionState by mutableStateOf(MultiplayerState.DISCONNECTED)
    var multiplayerVirtualIp by mutableStateOf<String?>(null)
    var multiplayerPeerCount by mutableStateOf(0)
    var multiplayerIsHost by mutableStateOf(false)  // 是否是房主
    
    // 菜单面板偏移量（可拖动）
    var menuPanelOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    
    // ========== 布局切换相关状态 ==========
    
    /** 可用的控件布局列表 (id, name) */
    var availablePacks by mutableStateOf<List<Pair<String, String>>>(emptyList())
    
    /** 当前激活的控件布局 ID */
    var activePackId by mutableStateOf<String?>(null)
    
    /** 控件布局选择弹窗是否显示 */
    var isLayoutPickerVisible by mutableStateOf(false)
    
    /** 计算实际的幽灵模式状态 (手动幽灵 或 控件使用中) */
    val effectiveGhostMode: Boolean
        get() = isGhostMode || isControlInUse
    
    fun updateOffset(delta: androidx.compose.ui.geometry.Offset) {
        offset = androidx.compose.ui.geometry.Offset(
            offset.x + delta.x,
            offset.y + delta.y
        )
    }
    
    fun updateMenuPanelOffset(delta: androidx.compose.ui.geometry.Offset) {
        menuPanelOffset = androidx.compose.ui.geometry.Offset(
            menuPanelOffset.x + delta.x,
            menuPanelOffset.y + delta.y
        )
    }
    
    fun toggleMenu() {
        isExpanded = !isExpanded
    }
    
    fun togglePalette() {
        isPaletteVisible = !isPaletteVisible
    }
    
    fun toggleGhostMode() {
        isGhostMode = !isGhostMode
    }
    
    fun toggleGrid() {
        isGridVisible = !isGridVisible
    }
    
    fun toggleEditMode() {
        isInEditMode = !isInEditMode
    }
    
    fun toggleFloatingBallVisibility() {
        isFloatingBallVisible = !isFloatingBallVisible
        // 隐藏悬浮球时同时收起菜单
        if (!isFloatingBallVisible) {
            isExpanded = false
        }
    }
}

@Composable
fun rememberFloatingMenuState(
    initialOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    initialExpanded: Boolean = false
): FloatingMenuState {
    return remember { FloatingMenuState(initialOffset, initialExpanded) }
}

/**
 * 悬浮菜单回调接口
 */
interface FloatingMenuCallbacks {
    // 通用回调
    fun onAddButton() {}
    fun onAddJoystick() {}
    fun onAddTouchPad() {}
    fun onAddMouseWheel() {}
    fun onAddText() {}
    fun onAddRadialMenu() {}
    fun onAddDPad() {}
    fun onSave() {}
    fun onOpenSettings() {}
    fun onExit() {}
    
    // 游戏内特有回调
    fun onToggleEditMode() {}
    fun onToggleControls() {}
    fun onFpsDisplayChanged(enabled: Boolean) {}
    fun onTouchEventChanged(enabled: Boolean) {}
    fun onExitGame() {}
    
    // 布局切换回调
    fun onSwitchPack(packId: String) {}
    
    // 调试日志回调
    fun onToggleDebugLog() {}
    
    // 联机相关回调
    fun onMultiplayerConnect(roomName: String, roomPassword: String, isHost: Boolean) {}
    fun onMultiplayerDisconnect() {}
    fun isMultiplayerAvailable(): Boolean = false
    fun getMultiplayerUnavailableReason(): String = ""
    
    /** 检查联机功能是否在设置中启用 */
    fun isMultiplayerFeatureEnabled(): Boolean = false
    
    /** 检查 VPN 权限并请求（如需要） */
    fun prepareVpnPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        // 默认直接调用 onGranted，实际实现在 GameControlsOverlay 中
        onGranted()
    }
    
    /** 检查是否有 VPN 权限 */
    fun hasVpnPermission(): Boolean = true
    
    /**
     * 初始化 VPN 服务（创建 TUN 接口）
     * 在创建房间前调用，VPN 就绪后回调
     */
    fun initVpnService(onReady: () -> Unit, onError: (String) -> Unit) {
        // 默认直接调用 onReady，实际实现在 GameControlsOverlay 中
        onReady()
    }
}

/**
 * 统一的悬浮控件菜单
 * 支持编辑器模式和游戏内模式
 */
@Composable
fun FloatingControlMenu(
    mode: FloatingMenuMode,
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .graphicsLayer { alpha = if (state.effectiveGhostMode) 0.25f else 1.0f }
    ) {
        // 悬浮球 (可通过返回键切换可见性)
        AnimatedVisibility(
            visible = state.isFloatingBallVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FloatingBall(
                isExpanded = state.isExpanded,
                onClick = { state.toggleMenu() },
                modifier = Modifier
                    .offset { IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { state.isGhostMode = true },
                            onDragEnd = { state.isGhostMode = false },
                            onDragCancel = { state.isGhostMode = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                state.updateOffset(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                            }
                        )
                    }
            )
        }

        // 菜单与组件库
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主菜单
            AnimatedVisibility(
                visible = state.isExpanded,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                when (mode) {
                    FloatingMenuMode.EDITOR -> EditorMenu(state, callbacks)
                    FloatingMenuMode.IN_GAME -> InGameMenu(state, callbacks)
                }
            }

            // 组件库面板 (仅编辑模式下显示)
            val showPalette = when (mode) {
                FloatingMenuMode.EDITOR -> state.isPaletteVisible && state.isExpanded
                FloatingMenuMode.IN_GAME -> state.isPaletteVisible && state.isExpanded && state.isInEditMode
            }
            
            AnimatedVisibility(
                visible = showPalette,
                enter = slideInHorizontally(initialOffsetX = { -20 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -20 }) + fadeOut()
            ) {
                // 使用 ControlEditorScreen 中定义的 ComponentPalette
                ComponentPalette(
                    onAddControl = { type ->
                        when (type) {
                            "button" -> callbacks.onAddButton()
                            "joystick" -> callbacks.onAddJoystick()
                            "touchpad" -> callbacks.onAddTouchPad()
                            "mousewheel" -> callbacks.onAddMouseWheel()
                            "text" -> callbacks.onAddText()
                            "radialmenu" -> callbacks.onAddRadialMenu()
                            "dpad" -> callbacks.onAddDPad()
                        }
                    },
                    onClose = { state.isPaletteVisible = false }
                )
            }
        }
    }
    
    // 联机弹窗 (独立显示)
    if (state.isMultiplayerPanelVisible) {
        MultiplayerDialog(
            state = state,
            callbacks = callbacks,
            onDismiss = { state.isMultiplayerPanelVisible = false }
        )
    }
    
    // 控件布局选择弹窗
    if (state.isLayoutPickerVisible) {
        LayoutPickerDialog(
            availablePacks = state.availablePacks,
            activePackId = state.activePackId,
            onSelect = { packId ->
                callbacks.onSwitchPack(packId)
                state.isLayoutPickerVisible = false
            },
            onDismiss = { state.isLayoutPickerVisible = false }
        )
    }
}

// FloatingBall 组件定义在 ControlEditorScreen.kt 中

/**
 * 编辑器模式菜单
 */
@Composable
private fun EditorMenu(
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks
) {
    Surface(
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuHeader(title = "编辑器菜单", onClose = { state.isExpanded = false })
            
            HorizontalDivider()

            // 使用 ControlEditorScreen 中定义的 MenuRowItem
            MenuRowItem(
                icon = Icons.Default.AddCircle,
                label = "组件库",
                isActive = state.isPaletteVisible,
                onClick = { state.togglePalette() }
            )
            
            MenuRowItem(
                icon = if (state.isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                label = "幽灵模式",
                isActive = state.isGhostMode,
                onClick = { state.toggleGhostMode() }
            )

            MenuRowItem(
                icon = if (state.isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                label = "网格显示",
                isActive = state.isGridVisible,
                onClick = { state.toggleGrid() }
            )

            MenuRowItem(
                icon = Icons.Default.Settings,
                label = "编辑器设置",
                isActive = false,
                onClick = { callbacks.onOpenSettings() }
            )

            HorizontalDivider()

            MenuRowItem(
                icon = Icons.Default.Save,
                label = "保存布局",
                isActive = false,
                onClick = { callbacks.onSave() },
                tint = MaterialTheme.colorScheme.primary
            )

            MenuRowItem(
                icon = Icons.Default.ExitToApp,
                label = "退出编辑器",
                isActive = false,
                onClick = { callbacks.onExit() },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 游戏内模式菜单
 */
@Composable
private fun InGameMenu(
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks
) {
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = Modifier
            .width(260.dp)
            .heightIn(max = 450.dp) // 限制最大高度，超出时滚动
            .offset { IntOffset(state.menuPanelOffset.x.roundToInt(), state.menuPanelOffset.y.roundToInt()) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState), // 添加滚动支持
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 可拖动的标题栏
            DraggableMenuHeader(
                title = "游戏菜单", 
                onClose = { state.isExpanded = false },
                onDrag = { delta -> state.updateMenuPanelOffset(delta) }
            )
            
            HorizontalDivider()

            // 编辑模式切换
            MenuRowItem(
                icon = if (state.isInEditMode) Icons.Default.Close else Icons.Default.Edit,
                label = if (state.isInEditMode) "退出编辑模式" else "进入编辑模式",
                isActive = state.isInEditMode,
                onClick = { 
                    state.toggleEditMode()
                    callbacks.onToggleEditMode()
                },
                tint = if (state.isInEditMode) MaterialTheme.colorScheme.error else Color.Unspecified
            )

            // 编辑模式下的选项
            AnimatedVisibility(visible = state.isInEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuRowItem(
                        icon = Icons.Default.AddCircle,
                        label = "组件库",
                        isActive = state.isPaletteVisible,
                        onClick = { state.togglePalette() }
                    )
                    
                    MenuRowItem(
                        icon = if (state.isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                        label = "网格显示",
                        isActive = state.isGridVisible,
                        onClick = { state.toggleGrid() }
                    )

                    MenuRowItem(
                        icon = Icons.Default.Save,
                        label = "保存布局",
                        isActive = false,
                        onClick = { callbacks.onSave() },
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    HorizontalDivider()
                }
            }

            // 非编辑模式下的游戏选项
            AnimatedVisibility(visible = !state.isInEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 控件可见性
                    MenuRowItem(
                        icon = if (state.isControlsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = if (state.isControlsVisible) "隐藏控件" else "显示控件",
                        isActive = !state.isControlsVisible,
                        onClick = { 
                            state.isControlsVisible = !state.isControlsVisible
                            callbacks.onToggleControls()
                        }
                    )

                    // 控件布局切换（如果有多个可用布局）
                    if (state.availablePacks.size > 1) {
                        HorizontalDivider()
                        
                        val activePackName = state.availablePacks
                            .find { it.first == state.activePackId }?.second ?: "未选择"
                        
                        MenuRowItem(
                            icon = Icons.Default.SwapHoriz,
                            label = "控件布局切换",
                            isActive = false,
                            onClick = { state.isLayoutPickerVisible = true }
                        )
                        
                        // 当前布局名称提示
                        Text(
                            text = "当前: $activePackName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 44.dp)
                        )
                    }
                    
                    HorizontalDivider()

                    // FPS 显示开关
                    MenuSwitchItem(
                        icon = Icons.Default.Speed,
                        label = "FPS 显示",
                        checked = state.isFpsDisplayEnabled,
                        onCheckedChange = { 
                            state.isFpsDisplayEnabled = it
                            callbacks.onFpsDisplayChanged(it)
                        }
                    )

                    // 触摸事件开关
                    MenuSwitchItem(
                        icon = Icons.Default.TouchApp,
                        label = "触摸控制",
                        checked = state.isTouchEventEnabled,
                        onCheckedChange = { 
                            state.isTouchEventEnabled = it
                            callbacks.onTouchEventChanged(it)
                        }
                    )
                    
                    HorizontalDivider()

                    // 调试日志
                    MenuSwitchItem(
                        icon = Icons.Default.BugReport,
                        label = "调试日志",
                        checked = state.isDebugLogEnabled,
                        onCheckedChange = {
                            state.isDebugLogEnabled = it
                            callbacks.onToggleDebugLog()
                        }
                    )

                    HorizontalDivider()
                }
            }

            // 联机功能 - 仅在设置中启用时显示
            if (callbacks.isMultiplayerFeatureEnabled()) {
                MenuRowItem(
                    icon = Icons.Default.Wifi,
                    label = when (state.multiplayerConnectionState) {
                        MultiplayerState.CONNECTED -> when {
                            state.multiplayerPeerCount > 0 -> "联机中 (${state.multiplayerPeerCount + 1}人)"
                            state.multiplayerIsHost -> "已连接 (等待加入)"
                            else -> "已连接 (寻找房主)"
                        }
                        MultiplayerState.CONNECTING -> "连接中..."
                        else -> "联机"
                    },
                    isActive = state.multiplayerConnectionState == MultiplayerState.CONNECTED,
                    onClick = { state.isMultiplayerPanelVisible = true },
                    tint = when (state.multiplayerConnectionState) {
                        MultiplayerState.CONNECTED -> MaterialTheme.colorScheme.tertiary
                        MultiplayerState.CONNECTING -> MaterialTheme.colorScheme.secondary
                        MultiplayerState.ERROR -> MaterialTheme.colorScheme.error
                        else -> Color.Unspecified
                    }
                )

                HorizontalDivider()
            }

            // 隐藏悬浮球
            MenuRowItem(
                icon = Icons.Default.VisibilityOff,
                label = "隐藏悬浮球(返回键打开)",
                isActive = false,
                onClick = { state.toggleFloatingBallVisibility() }
            )
            
            Text(
                text = "按返回键可重新显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )

            HorizontalDivider()

            // 退出游戏
            MenuRowItem(
                icon = Icons.Default.ExitToApp,
                label = "退出游戏",
                isActive = false,
                onClick = { callbacks.onExitGame() },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MenuHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 可拖动的菜单标题栏
 */
@Composable
private fun DraggableMenuHeader(
    title: String,
    onClose: () -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 拖动指示器
            Icon(
                Icons.Default.DragIndicator, 
                contentDescription = "拖动",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 带开关的菜单项 (游戏内专用)
 */
@Composable
fun MenuSwitchItem(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

/**
 * 控件布局选择弹窗
 */
@Composable
private fun LayoutPickerDialog(
    availablePacks: List<Pair<String, String>>,
    activePackId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("控件布局切换")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(availablePacks) { (id, name) ->
                    val isSelected = id == activePackId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked
                                              else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
