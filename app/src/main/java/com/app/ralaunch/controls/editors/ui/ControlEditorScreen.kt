package com.app.ralaunch.controls.editors.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.editors.ControlEditorViewModel
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import com.app.ralaunch.controls.views.GridOverlayView
import com.app.ralaunch.controls.bridges.DummyInputBridge
import com.app.ralaunch.ui.compose.dialogs.KeyBindingDialog
import kotlin.math.roundToInt

@Composable
fun ControlEditorScreen(
    viewModel: ControlEditorViewModel,
    layout: ControlLayout?,
    selectedControl: ControlData?,
    isPropertyPanelVisible: Boolean,
    isPaletteVisible: Boolean,
    onExit: () -> Unit
) {
    val menuOffset by viewModel.menuOffset.collectAsState()
    val isGhostMode by viewModel.isGhostMode.collectAsState()
    val isMenuExpanded by viewModel.isMenuExpanded.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()
    val showKeySelector by viewModel.showKeySelector.collectAsState()
    val showJoystickKeyMapping by viewModel.showJoystickKeyMapping.collectAsState()
    val showEditorSettings by viewModel.showEditorSettings.collectAsState()
    val showTextureSelector by viewModel.showTextureSelector.collectAsState()
    val showPolygonEditor by viewModel.showPolygonEditor.collectAsState()
    val isGridVisible by viewModel.isGridVisible.collectAsState()
    
    // 属性面板偏移量（可拖动）
    var propertyPanelOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // 处理返回键
    BackHandler {
        if (!viewModel.requestExit()) {
            // 会显示退出确认对话框
        } else {
            onExit()
        }
    }

    // 适配主题背景色
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // Layer 0: 游戏预览层 (AndroidView)
        AndroidView(
            factory = { context ->
                ControlLayoutView(context).apply {
                    inputBridge = DummyInputBridge()
                    isModifiable = true
                    setEditControlListener { data ->
                        viewModel.selectControl(data)
                    }
                    setOnControlChangedListener(object : ControlLayoutView.OnControlChangedListener {
                        override fun onControlChanged() {
                            viewModel.saveLayout()
                        }
                        override fun onControlDragging(isDragging: Boolean) {
                            viewModel.setGhostMode(isDragging)
                        }
                    })
                }
            },
            update = { view ->
                if (view.currentLayout != layout) {
                    view.loadLayout(layout)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 1: 网格辅助线
        if (isGridVisible) {
            AndroidView(
                factory = { context -> 
                    GridOverlayView(context).apply {
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Layer 2: 自由移动的悬浮球菜单系统
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer { alpha = if (isGhostMode) 0.3f else 1.0f }
        ) {
            // 点击空白区域关闭菜单和属性面板
            if (isMenuExpanded || isPropertyPanelVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.toggleMenu(false)
                            viewModel.selectControl(null)
                        }
                )
            }
            
            // 1. 悬浮球
            FloatingBall(
                isExpanded = isMenuExpanded,
                onClick = { viewModel.toggleMenu(!isMenuExpanded) },
                modifier = Modifier
                    .offset { IntOffset(menuOffset.x.roundToInt(), menuOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { viewModel.setGhostMode(true) },
                            onDragEnd = { viewModel.setGhostMode(false) },
                            onDragCancel = { viewModel.setGhostMode(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                viewModel.updateMenuOffset(dragAmount)
                            }
                        )
                    }
            )

            // 2. 菜单与组件库
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                ) {
                    ActionWindowMenu(
                        isPaletteVisible = isPaletteVisible,
                        isGhostMode = isGhostMode,
                        isGridVisible = isGridVisible,
                        onTogglePalette = { viewModel.togglePalette(!isPaletteVisible) },
                        onToggleGhostMode = { viewModel.setGhostMode(!isGhostMode) },
                        onToggleGrid = { viewModel.toggleGrid(!isGridVisible) },
                        onOpenSettings = { viewModel.toggleEditorSettings(true) },
                        onSave = { viewModel.saveLayout() },
                        onCloseMenu = { viewModel.toggleMenu(false) },
                        onExit = { 
                            if (!viewModel.requestExit()) {
                                // 会显示退出确认对话框
                            } else {
                                onExit()
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isPaletteVisible && isMenuExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -20 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -20 }) + fadeOut()
                ) {
                    ComponentPalette(
                        onAddControl = { viewModel.addNewControl(it) },
                        onClose = { viewModel.togglePalette(false) }
                    )
                }
            }

            // 右侧属性面板
            AnimatedVisibility(
                visible = isPropertyPanelVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(propertyPanelOffset.x.roundToInt(), propertyPanelOffset.y.roundToInt()) }
            ) {
                PropertyPanel(
                    control = selectedControl,
                    onUpdate = { viewModel.updateControl(it) },
                    onClose = { viewModel.selectControl(null) },
                    onOpenKeySelector = { viewModel.showKeySelector(it) },
                    onOpenJoystickKeyMapping = { viewModel.showJoystickKeyMapping(it) },
                    onOpenTextureSelector = { control, type -> viewModel.showTextureSelector(control, type) },
                    onOpenPolygonEditor = { viewModel.showPolygonEditor(it) },
                    onDrag = { delta ->
                        propertyPanelOffset = androidx.compose.ui.geometry.Offset(
                            propertyPanelOffset.x + delta.x,
                            propertyPanelOffset.y + delta.y
                        )
                    },
                    onDuplicate = { viewModel.duplicateSelectedControl() },
                    onDelete = { viewModel.deleteSelectedControl() }
                )
            }
        }

        // Layer 5: 底部操作按钮组
        if (selectedControl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = if (isGhostMode) 0.3f else 1.0f }
            ) {
                FloatingActionButton(
                    onClick = { viewModel.deleteSelectedControl() },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除控件")
                }
            }
        }
    }

    // ========== 对话框组 ==========

    if (showExitDialog) {
        ExitConfirmDialog(
            onSaveAndExit = { viewModel.saveAndExit(onExit) },
            onExitWithoutSaving = { viewModel.exitWithoutSaving(onExit) },
            onDismiss = { viewModel.dismissExitDialog() }
        )
    }

    showKeySelector?.let { button ->
        KeyBindingDialog(
            initialGamepadMode = button.keycode.type == ControlData.KeyType.GAMEPAD,
            onKeySelected = { keyCode, _ -> viewModel.updateButtonKeycode(button, keyCode) },
            onDismiss = { viewModel.dismissKeySelector() }
        )
    }

    showJoystickKeyMapping?.let { joystick ->
        JoystickKeyMappingDialog(
            currentKeys = joystick.joystickKeys,
            onUpdateKeys = { viewModel.updateJoystickKeys(joystick, it) },
            onDismiss = { viewModel.dismissJoystickKeyMapping() }
        )
    }

    if (showEditorSettings) {
        EditorSettingsDialog(
            isGridVisible = isGridVisible,
            onToggleGrid = { viewModel.toggleGrid(it) },
            onDismiss = { viewModel.toggleEditorSettings(false) }
        )
    }

    showTextureSelector?.let { (control, textureType) ->
        TextureSelectorDialog(
            control = control,
            textureType = textureType,
            onUpdateButtonTexture = { btn, type, path, enabled -> 
                viewModel.updateButtonTexture(btn, type, path, enabled) 
            },
            onUpdateJoystickTexture = { js, type, path, enabled -> 
                viewModel.updateJoystickTexture(js, type, path, enabled) 
            },
            onPickImage = { viewModel.requestPickImage() },
            onDismiss = { viewModel.dismissTextureSelector() }
        )
    }
    
    showPolygonEditor?.let { button ->
        PolygonEditorDialog(
            currentPoints = button.polygonPoints,
            onConfirm = { points ->
                viewModel.updatePolygonPoints(button, points)
            },
            onDismiss = { viewModel.dismissPolygonEditor() }
        )
    }
}
