package com.app.ralaunch.controls.editors.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.app.ralaunch.console.ConsoleManager
import com.app.ralaunch.console.DebugLogOverlay
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.controls.textures.TextureConfig
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import com.app.ralaunch.controls.views.GridOverlayView
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.easytier.EasyTierConnectionState
import com.app.ralaunch.easytier.EasyTierManager
import com.app.ralaunch.ui.compose.dialogs.KeyBindingDialog
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * 游戏内控制 Overlay
 * 整合悬浮菜单、控件编辑、属性面板
 */
@Composable
fun GameControlsOverlay(
    controlLayoutView: ControlLayoutView,
    packManager: ControlPackManager,
    settingsManager: SettingsAccess,
    toggleFloatingBallEvent: SharedFlow<Unit>,
    onExitGame: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit = {},
    onActiveAreaChanged: (android.graphics.RectF?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 菜单状态
    val menuState = rememberFloatingMenuState()
    val context = LocalContext.current
    
    // EasyTier 管理器
    val easyTierManager = remember { EasyTierManager.getInstance() }
    val connectionState by easyTierManager.connectionState.collectAsState()
    val virtualIp by easyTierManager.virtualIp.collectAsState()
    val peers by easyTierManager.peers.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 同步联机状态到菜单
    LaunchedEffect(connectionState, virtualIp, peers) {
        menuState.multiplayerConnectionState = when (connectionState) {
            EasyTierConnectionState.DISCONNECTED -> MultiplayerState.DISCONNECTED
            EasyTierConnectionState.CONNECTING -> MultiplayerState.CONNECTING
            EasyTierConnectionState.FINDING_HOST -> MultiplayerState.CONNECTING  // 寻找房主也显示为连接中
            EasyTierConnectionState.CONNECTED -> MultiplayerState.CONNECTED
            EasyTierConnectionState.ERROR -> MultiplayerState.ERROR
        }
        menuState.multiplayerVirtualIp = virtualIp
        menuState.multiplayerPeerCount = peers.size
    }
    
    // 调试日志状态
    val debugLogVisible by ConsoleManager.debugLogVisible.collectAsState()
    
    // 初始化菜单状态 & 启动日志收集
    LaunchedEffect(Unit) {
        menuState.isFpsDisplayEnabled = settingsManager.isFPSDisplayEnabled
        menuState.isTouchEventEnabled = settingsManager.isTouchEventEnabled
        ConsoleManager.start()
        
        // 初始化布局切换状态（优先使用快速切换列表，如果为空则显示全部）
        val quickSwitchPacks = packManager.getQuickSwitchPacks()
        val packsToShow = quickSwitchPacks.ifEmpty { packManager.getInstalledPacks() }
        menuState.availablePacks = packsToShow.map { it.id to it.name }
        menuState.activePackId = packManager.getSelectedPackId()
        
        // 初始化子布局状态
        val layout = controlLayoutView.currentLayout
        if (layout != null && layout.hasSubLayouts()) {
            menuState.subLayouts = layout.subLayouts.map { it.id to it.name }
            menuState.activeSubLayoutId = layout.activeSubLayoutId
                ?: layout.subLayouts.firstOrNull()?.id
        }
    }
    
    // 同步调试日志状态到菜单
    LaunchedEffect(debugLogVisible) {
        menuState.isDebugLogEnabled = debugLogVisible
    }
    
    // 监听返回键切换悬浮球可见性
    LaunchedEffect(toggleFloatingBallEvent) {
        toggleFloatingBallEvent.collect {
            menuState.toggleFloatingBallVisibility()
        }
    }
    
    // 通知外部编辑模式变化
    LaunchedEffect(menuState.isInEditMode) {
        onEditModeChanged(menuState.isInEditMode)
    }
    
    // 当前选中的控件
    var selectedControl by remember { mutableStateOf<ControlData?>(null) }
    
    // 布局数据
    var currentLayout by remember { mutableStateOf<ControlLayout?>(controlLayoutView.currentLayout) }
    
    // 是否有未保存的更改
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    
    // 对话框状态
    var showKeySelector by remember { mutableStateOf<ControlData.Button?>(null) }
    var showJoystickKeyMapping by remember { mutableStateOf<ControlData.Joystick?>(null) }
    var showTextureSelector by remember { mutableStateOf<Pair<ControlData, String>?>(null) }
    var showPolygonEditor by remember { mutableStateOf<ControlData.Button?>(null) }
    
    // 属性面板偏移量（可拖动）
    var propertyPanelOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 处理选择的图片
            showTextureSelector?.let { (control, textureType) ->
                handleImagePicked(
                    context = context,
                    uri = selectedUri,
                    packManager = packManager,
                    control = control,
                    textureType = textureType,
                    controlLayoutView = controlLayoutView,
                    onControlUpdated = { updated ->
                        selectedControl = updated
                        hasUnsavedChanges = true
                    }
                )
                showTextureSelector = null
            }
        }
    }
    
    // 获取屏幕尺寸用于计算活跃区域
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // 更新 Compose 活跃区域（菜单展开或属性面板显示时）
    LaunchedEffect(menuState.isExpanded, selectedControl, menuState.isInEditMode, propertyPanelOffset) {
        if (menuState.isExpanded || (menuState.isInEditMode && selectedControl != null)) {
            // 计算活跃区域：左侧菜单 + 右侧属性面板
            val menuWidth = with(density) { 300.dp.toPx() }  // 菜单大约 260dp + padding
            val panelWidth = with(density) { 350.dp.toPx() } // 属性面板大约 320dp + padding
            
            // 创建一个覆盖活跃 UI 的矩形
            val rect = android.graphics.RectF()
            if (menuState.isExpanded) {
                rect.union(0f, 0f, menuWidth, screenHeightPx)
            }
            if (menuState.isInEditMode && selectedControl != null) {
                rect.union(screenWidthPx - panelWidth + propertyPanelOffset.x, 0f, screenWidthPx, screenHeightPx)
            }
            onActiveAreaChanged(rect)
        } else {
            onActiveAreaChanged(null)
        }
    }
    
    // 回调实现
    val callbacks = remember(controlLayoutView, packManager, settingsManager) {
        object : FloatingMenuCallbacks {
            override fun onAddButton() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addButton(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddJoystick() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addJoystick(layout, ControlData.Joystick.Mode.KEYBOARD, false)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddTouchPad() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addTouchPad(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddMouseWheel() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addMouseWheel(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddText() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addText(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddRadialMenu() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addRadialMenu(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onAddDPad() {
                val layout = controlLayoutView.currentLayout ?: ControlLayout().also {
                    it.controls = mutableListOf()
                }
                InGameControlOperations.addDPad(layout)
                controlLayoutView.loadLayout(layout)
                currentLayout = layout
                hasUnsavedChanges = true
            }
            
            override fun onSave() {
                val layout = controlLayoutView.currentLayout ?: return
                val packId = packManager.getSelectedPackId() ?: return
                packManager.savePackLayout(packId, layout)
                hasUnsavedChanges = false
            }
            
            override fun onToggleEditMode() {
                // isModifiable 已在 LaunchedEffect 中设置
                if (!menuState.isInEditMode) {
                    // 退出编辑模式时重新加载布局
                    controlLayoutView.loadLayoutFromPackManager()
                    selectedControl = null
                }
            }
            
            override fun onToggleControls() {
                controlLayoutView.isControlsVisible = menuState.isControlsVisible
            }
            
            override fun onFpsDisplayChanged(enabled: Boolean) {
                settingsManager.isFPSDisplayEnabled = enabled
            }
            
            override fun onTouchEventChanged(enabled: Boolean) {
                settingsManager.isTouchEventEnabled = enabled
            }
            
            override fun onExitGame() {
                onExitGame()
            }
            
            // 调试日志回调
            override fun onToggleDebugLog() {
                ConsoleManager.toggleDebugLog()
            }
            
            // 联机相关回调
            override fun onMultiplayerConnect(roomName: String, roomPassword: String, isHost: Boolean) {
                menuState.multiplayerIsHost = isHost  // 记录是否是房主
                scope.launch {
                    easyTierManager.connect(roomName, roomPassword, isHost = isHost)
                }
            }
            
            override fun onMultiplayerDisconnect() {
                menuState.multiplayerIsHost = false  // 重置房主标记
                easyTierManager.disconnect(context)
            }
            
            override fun isMultiplayerAvailable(): Boolean {
                return easyTierManager.isAvailable()
            }
            
            override fun getMultiplayerUnavailableReason(): String {
                return easyTierManager.getUnavailableReason()
            }
            
            override fun isMultiplayerFeatureEnabled(): Boolean {
                return settingsManager.isMultiplayerEnabled
            }
            
            // no_tun 模式下 VPN 权限不再需要，保留接口兼容
            override fun prepareVpnPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
                onGranted()
            }
            
            override fun hasVpnPermission(): Boolean = true
            
            override fun initVpnService(onReady: () -> Unit, onError: (String) -> Unit) {
                onReady()
            }
            
            // ========== 布局切换回调 ==========
            
            override fun onSwitchPack(packId: String) {
                // 切换控件包
                packManager.setSelectedPackId(packId)
                controlLayoutView.loadLayoutFromPackManager()
                
                // 更新菜单状态
                menuState.activePackId = packId
                val newLayout = packManager.getPackLayout(packId)
                currentLayout = newLayout
                if (newLayout != null && newLayout.hasSubLayouts()) {
                    menuState.subLayouts = newLayout.subLayouts.map { it.id to it.name }
                    menuState.activeSubLayoutId = newLayout.activeSubLayoutId
                        ?: newLayout.subLayouts.firstOrNull()?.id
                } else {
                    menuState.subLayouts = emptyList()
                    menuState.activeSubLayoutId = null
                }
                
                hasUnsavedChanges = false
                selectedControl = null
            }
            
            override fun onSwitchSubLayout(subLayoutId: String) {
                // 切换子布局
                controlLayoutView.switchSubLayout(subLayoutId)
                menuState.activeSubLayoutId = subLayoutId
                currentLayout = controlLayoutView.currentLayout
            }
            
            override fun onLayoutSwitcherVisibilityChanged(visible: Boolean) {
                menuState.isLayoutSwitcherVisible = visible
            }
        }
    }
    
    // 设置控件编辑监听
    LaunchedEffect(controlLayoutView) {
        controlLayoutView.setEditControlListener { data ->
            if (menuState.isInEditMode) {
                selectedControl = data
            }
        }
        controlLayoutView.setOnControlChangedListener(object : ControlLayoutView.OnControlChangedListener {
            override fun onControlChanged() {
                hasUnsavedChanges = true
            }
            override fun onControlDragging(isDragging: Boolean) {
                menuState.isGhostMode = isDragging
            }
            override fun onControlInUse(inUse: Boolean) {
                // 控件正在被使用时，悬浮菜单进入幽灵模式
                menuState.isControlInUse = inUse
            }
        })
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 点击空白区域关闭菜单和属性面板
        if (menuState.isExpanded || (menuState.isInEditMode && selectedControl != null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        menuState.isExpanded = false
                        selectedControl = null
                    }
            )
        }
        
        // 网格辅助线 (仅编辑模式，不拦截触摸事件)
        if (menuState.isGridVisible && menuState.isInEditMode) {
            AndroidView(
                factory = { context -> 
                    GridOverlayView(context).apply {
                        // 禁用触摸事件，让事件穿透到下层
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 悬浮菜单
        FloatingControlMenu(
            mode = FloatingMenuMode.IN_GAME,
            state = menuState,
            callbacks = callbacks
        )

        // 右侧属性面板 (仅编辑模式下选中控件时显示，可拖动)
        AnimatedVisibility(
            visible = menuState.isInEditMode && selectedControl != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(propertyPanelOffset.x.roundToInt(), propertyPanelOffset.y.roundToInt()) }
        ) {
            PropertyPanel(
                control = selectedControl,
                onUpdate = { updatedControl ->
                    // 更新控件数据 - 使用 id 作为标识
                    val layout = controlLayoutView.currentLayout ?: return@PropertyPanel
                    val index = layout.controls.indexOfFirst { it.id == updatedControl.id }
                    if (index >= 0) {
                        layout.controls[index] = updatedControl
                        controlLayoutView.loadLayout(layout)
                        currentLayout = layout
                        hasUnsavedChanges = true
                    }
                    selectedControl = updatedControl
                },
                onClose = { selectedControl = null },
                onOpenKeySelector = { button -> showKeySelector = button },
                onOpenJoystickKeyMapping = { joystick -> showJoystickKeyMapping = joystick },
                onOpenTextureSelector = { control, type -> showTextureSelector = control to type },
                onOpenPolygonEditor = { button -> showPolygonEditor = button },
                onDrag = { delta -> 
                    propertyPanelOffset = androidx.compose.ui.geometry.Offset(
                        propertyPanelOffset.x + delta.x,
                        propertyPanelOffset.y + delta.y
                    )
                },
                onDuplicate = {
                    val layout = controlLayoutView.currentLayout ?: return@PropertyPanel
                    val controlToDuplicate = selectedControl ?: return@PropertyPanel
                    // 深拷贝控件并生成新的 ID 和名称
                    val duplicated = controlToDuplicate.deepCopy().apply {
                        id = java.util.UUID.randomUUID().toString()
                        name = "${controlToDuplicate.name}_副本"
                        x = (x + 0.05f).coerceAtMost(0.95f)
                        y = (y + 0.05f).coerceAtMost(0.95f)
                    }
                    layout.controls.add(duplicated)
                    controlLayoutView.loadLayout(layout)
                    currentLayout = layout
                    hasUnsavedChanges = true
                    selectedControl = duplicated
                },
                onDelete = {
                    val layout = controlLayoutView.currentLayout ?: return@PropertyPanel
                    val controlToDelete = selectedControl ?: return@PropertyPanel
                    layout.controls.removeAll { it.id == controlToDelete.id }
                    controlLayoutView.loadLayout(layout)
                    currentLayout = layout
                    hasUnsavedChanges = true
                    selectedControl = null
                }
            )
        }

        // 子布局快捷切换覆盖层（非编辑模式 + 有子布局时显示）
        if (menuState.subLayouts.isNotEmpty() 
            && menuState.isLayoutSwitcherVisible 
            && !menuState.isInEditMode
        ) {
            LayoutSwitcherOverlay(
                subLayouts = menuState.subLayouts,
                activeSubLayoutId = menuState.activeSubLayoutId,
                onSwitch = { callbacks.onSwitchSubLayout(it) }
            )
        }

        // 调试日志覆盖层（不拦截触摸）
        DebugLogOverlay(visible = debugLogVisible)

        // 删除按钮 (仅编辑模式下选中控件时显示)
        if (menuState.isInEditMode && selectedControl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = if (menuState.isGhostMode) 0.3f else 1.0f }
            ) {
                FloatingActionButton(
                    onClick = {
                        val layout = controlLayoutView.currentLayout ?: return@FloatingActionButton
                        val controlToDelete = selectedControl ?: return@FloatingActionButton
                        layout.controls.removeAll { it.id == controlToDelete.id }
                        controlLayoutView.loadLayout(layout)
                        currentLayout = layout
                        hasUnsavedChanges = true
                        selectedControl = null
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除控件")
                }
            }
        }
    }

    // ========== 对话框 ==========
    
    // 按键绑定选择器对话框
    showKeySelector?.let { button ->
        KeyBindingDialog(
            initialGamepadMode = button.keycode.type == ControlData.KeyType.GAMEPAD,
            onKeySelected = { keyCode, _ ->
                val updated = button.deepCopy() as ControlData.Button
                updated.keycode = keyCode
                // 更新到布局 - 使用 id 作为标识
                val layout = controlLayoutView.currentLayout ?: return@KeyBindingDialog
                val index = layout.controls.indexOfFirst { it.id == button.id }
                if (index >= 0) {
                    layout.controls[index] = updated
                    controlLayoutView.loadLayout(layout)
                    hasUnsavedChanges = true
                }
                if (selectedControl?.id == button.id) {
                    selectedControl = updated
                }
                showKeySelector = null
            },
            onDismiss = { showKeySelector = null }
        )
    }

    // 摇杆键位映射
    showJoystickKeyMapping?.let { joystick ->
        JoystickKeyMappingDialog(
            currentKeys = joystick.joystickKeys,
            onUpdateKeys = { keys ->
                val updated = joystick.deepCopy() as ControlData.Joystick
                updated.joystickKeys = keys
                // 更新到布局 - 使用 id 作为标识
                val layout = controlLayoutView.currentLayout ?: return@JoystickKeyMappingDialog
                val index = layout.controls.indexOfFirst { it.id == joystick.id }
                if (index >= 0) {
                    layout.controls[index] = updated
                    controlLayoutView.loadLayout(layout)
                    hasUnsavedChanges = true
                }
                if (selectedControl?.id == joystick.id) {
                    selectedControl = updated
                }
                showJoystickKeyMapping = null
            },
            onDismiss = { showJoystickKeyMapping = null }
        )
    }
    
    // 纹理选择对话框
    showTextureSelector?.let { (control, textureType) ->
        TextureSelectorDialog(
            control = control,
            textureType = textureType,
            onUpdateButtonTexture = { btn, type, path, enabled -> 
                val updated = btn.deepCopy() as ControlData.Button
                val newConfig = TextureConfig(path = path, enabled = enabled)
                updated.texture = when (type) {
                    "normal" -> updated.texture.copy(normal = newConfig)
                    "pressed" -> updated.texture.copy(pressed = newConfig)
                    "toggled" -> updated.texture.copy(toggled = newConfig)
                    else -> updated.texture
                }
                // 清除纹理缓存
                if (path.isNotEmpty()) {
                    val assetsDir = packManager.getPackAssetsDir(controlLayoutView.currentLayout?.id ?: "")
                    assetsDir?.let {
                        val textureLoader = TextureLoader.getInstance(context)
                        textureLoader.evictFromCache(File(it, path).absolutePath)
                    }
                }
                // 更新到布局
                val layout = controlLayoutView.currentLayout ?: return@TextureSelectorDialog
                val index = layout.controls.indexOfFirst { it.id == btn.id }
                if (index >= 0) {
                    layout.controls[index] = updated
                    controlLayoutView.loadLayout(layout)
                    controlLayoutView.invalidate()
                    hasUnsavedChanges = true
                }
                if (selectedControl?.id == btn.id) {
                    selectedControl = updated
                }
                showTextureSelector = null
            },
            onUpdateJoystickTexture = { js, type, path, enabled -> 
                val updated = js.deepCopy() as ControlData.Joystick
                val newConfig = TextureConfig(path = path, enabled = enabled)
                updated.texture = when (type) {
                    "background" -> updated.texture.copy(background = newConfig)
                    "knob" -> updated.texture.copy(knob = newConfig)
                    "backgroundPressed" -> updated.texture.copy(backgroundPressed = newConfig)
                    "knobPressed" -> updated.texture.copy(knobPressed = newConfig)
                    else -> updated.texture
                }
                // 清除纹理缓存
                if (path.isNotEmpty()) {
                    val assetsDir = packManager.getPackAssetsDir(controlLayoutView.currentLayout?.id ?: "")
                    assetsDir?.let {
                        val textureLoader = TextureLoader.getInstance(context)
                        textureLoader.evictFromCache(File(it, path).absolutePath)
                    }
                }
                // 更新到布局
                val layout = controlLayoutView.currentLayout ?: return@TextureSelectorDialog
                val index = layout.controls.indexOfFirst { it.id == js.id }
                if (index >= 0) {
                    layout.controls[index] = updated
                    controlLayoutView.loadLayout(layout)
                    controlLayoutView.invalidate()
                    hasUnsavedChanges = true
                }
                if (selectedControl?.id == js.id) {
                    selectedControl = updated
                }
                showTextureSelector = null
            },
            onPickImage = { imagePickerLauncher.launch("image/*") },
            onDismiss = { showTextureSelector = null }
        )
    }
    
    // 多边形编辑器对话框
    showPolygonEditor?.let { button ->
        PolygonEditorDialog(
            currentPoints = button.polygonPoints,
            onConfirm = { points ->
                val updated = button.deepCopy() as ControlData.Button
                updated.polygonPoints = points
                // 更新到布局
                val layout = controlLayoutView.currentLayout ?: return@PolygonEditorDialog
                val index = layout.controls.indexOfFirst { it.id == button.id }
                if (index >= 0) {
                    layout.controls[index] = updated
                    controlLayoutView.loadLayout(layout)
                    hasUnsavedChanges = true
                }
                if (selectedControl?.id == button.id) {
                    selectedControl = updated
                }
                showPolygonEditor = null
            },
            onDismiss = { showPolygonEditor = null }
        )
    }
}

