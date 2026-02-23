package com.app.ralaunch.feature.controls.editors

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.packs.ControlLayout
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 控件编辑器 ViewModel
 * 管理编辑器状态、选中控件以及数据持久化
 */
class ControlEditorViewModel : ViewModel() {
    private val appContext: Context = KoinJavaComponent.get(Context::class.java)
    private val packManager: ControlPackManager = KoinJavaComponent.get(ControlPackManager::class.java)

    private fun nextControlName(baseLabelResId: Int, count: Int): String {
        return "${appContext.getString(baseLabelResId)} $count"
    }

    // 菜单偏移位置 (自由拖拽)
    private val _menuOffset = MutableStateFlow(Offset(100f, 100f))
    val menuOffset: StateFlow<Offset> = _menuOffset.asStateFlow()

    // 菜单是否展开 (悬浮球展开状态)
    private val _isMenuExpanded = MutableStateFlow(false)
    val isMenuExpanded: StateFlow<Boolean> = _isMenuExpanded.asStateFlow()

    // 幽灵模式 (高透明度)
    private val _isGhostMode = MutableStateFlow(false)
    val isGhostMode: StateFlow<Boolean> = _isGhostMode.asStateFlow()

    // 当前编辑的布局数据
    private val _layoutState = MutableStateFlow<ControlLayout?>(null)
    val layoutState: StateFlow<ControlLayout?> = _layoutState.asStateFlow()

    // 当前选中的控件数据
    private val _selectedControl = MutableStateFlow<ControlData?>(null)
    val selectedControl: StateFlow<ControlData?> = _selectedControl.asStateFlow()

    // 属性面板是否展开
    private val _isPropertyPanelVisible = MutableStateFlow(false)
    val isPropertyPanelVisible: StateFlow<Boolean> = _isPropertyPanelVisible.asStateFlow()

    // 组件库是否展开
    private val _isPaletteVisible = MutableStateFlow(false)
    val isPaletteVisible: StateFlow<Boolean> = _isPaletteVisible.asStateFlow()

    // 退出确认对话框是否显示
    private val _showExitDialog = MutableStateFlow(false)
    val showExitDialog: StateFlow<Boolean> = _showExitDialog.asStateFlow()

    // 按键选择器对话框是否显示 (及目标控件)
    private val _showKeySelector = MutableStateFlow<ControlData.Button?>(null)
    val showKeySelector: StateFlow<ControlData.Button?> = _showKeySelector.asStateFlow()

    // 摇杆键位映射对话框是否显示 (及目标控件)
    private val _showJoystickKeyMapping = MutableStateFlow<ControlData.Joystick?>(null)
    val showJoystickKeyMapping: StateFlow<ControlData.Joystick?> = _showJoystickKeyMapping.asStateFlow()

    // 编辑器设置面板是否显示
    private val _showEditorSettings = MutableStateFlow(false)
    val showEditorSettings: StateFlow<Boolean> = _showEditorSettings.asStateFlow()

    // 纹理选择器对话框 (Button, Joystick 等)
    private val _showTextureSelector = MutableStateFlow<Pair<ControlData, String>?>(null)
    val showTextureSelector: StateFlow<Pair<ControlData, String>?> = _showTextureSelector.asStateFlow()

    // 多边形编辑器对话框
    private val _showPolygonEditor = MutableStateFlow<ControlData.Button?>(null)
    val showPolygonEditor: StateFlow<ControlData.Button?> = _showPolygonEditor.asStateFlow()

    // 图片选择请求事件 (由 Activity 消费)
    private val _pickImageRequest = MutableStateFlow(false)
    val pickImageRequest: StateFlow<Boolean> = _pickImageRequest.asStateFlow()

    // 网格显示开关（默认关闭）
    private val _isGridVisible = MutableStateFlow(false)
    val isGridVisible: StateFlow<Boolean> = _isGridVisible.asStateFlow()

    // 未保存变更标记
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // 原始布局快照 (用于检测变更)
    private var originalLayoutSnapshot: String? = null

    private var currentPackId: String? = null

    /**
     * 初始化加载布局
     */
    fun loadLayout(packId: String) {
        currentPackId = packId
        viewModelScope.launch {
            val layout = packManager.getPackLayout(packId)
            _layoutState.value = layout
            // 保存原始快照用于检测变更
            originalLayoutSnapshot = layout?.let { serializeLayout(it) }
            _hasUnsavedChanges.value = false
        }
    }

    /**
     * 序列化布局为字符串 (用于比较)
     */
    private fun serializeLayout(layout: ControlLayout): String {
        return kotlinx.serialization.json.Json.encodeToString(
            ControlLayout.serializer(), layout
        )
    }

    /**
     * 检测是否有未保存的变更
     */
    private fun checkUnsavedChanges() {
        val current = _layoutState.value
        if (current == null) {
            _hasUnsavedChanges.value = false
            return
        }
        val currentSnapshot = serializeLayout(current)
        _hasUnsavedChanges.value = currentSnapshot != originalLayoutSnapshot
    }

    /**
     * 获取当前控件列表
     */
    private fun getActiveControls(): MutableList<ControlData>? {
        val layout = _layoutState.value ?: return null
        return layout.controls
    }
    
    /**
     * 更新布局中的控件列表并刷新状态
     */
    private fun updateLayoutWithControls(newControls: MutableList<ControlData>) {
        val layout = _layoutState.value ?: return
        _layoutState.value = layout.copy(controls = newControls)
        checkUnsavedChanges()
    }

    /**
     * 选中控件
     */
    fun selectControl(data: ControlData?) {
        _selectedControl.value = data
        _isPropertyPanelVisible.value = data != null
    }

    /**
     * 更新控件数据
     */
    fun updateControl(updatedData: ControlData) {
        val controls = getActiveControls() ?: return
        val index = controls.indexOfFirst { it.id == updatedData.id }
        if (index >= 0) {
            val newControls = controls.toMutableList()
            newControls[index] = updatedData
            updateLayoutWithControls(newControls)
            _selectedControl.value = updatedData
        }
    }

    /**
     * 切换属性面板显示
     */
    fun togglePropertyPanel(visible: Boolean) {
        _isPropertyPanelVisible.value = visible
    }

    /**
     * 切换组件库显示
     */
    fun togglePalette(visible: Boolean) {
        _isPaletteVisible.value = visible
    }

    /**
     * 切换菜单展开状态
     */
    fun toggleMenu(expanded: Boolean) {
        _isMenuExpanded.value = expanded
        if (!expanded) {
            _isPaletteVisible.value = false
        }
    }

    /**
     * 更新菜单偏移
     */
    fun updateMenuOffset(delta: Offset) {
        _menuOffset.value = _menuOffset.value + delta
    }

    /**
     * 设置幽灵模式
     */
    fun setGhostMode(active: Boolean) {
        _isGhostMode.value = active
    }

    /**
     * 添加新控件到布局（添加到当前编辑目标）
     */
    fun addNewControl(type: String) {
        val controls = getActiveControls() ?: return
        
        val newControl = when (type) {
            "button" -> ControlData.Button().apply {
                name = nextControlName(R.string.editor_default_button_name, controls.size + 1)
                x = 0.5f
                y = 0.5f
                width = 0.1f
                height = 0.1f
            }
            "joystick" -> ControlData.Joystick().apply {
                name = nextControlName(R.string.editor_control_type_joystick, controls.size + 1)
                x = 0.2f
                y = 0.7f
                width = 0.25f
                height = 0.25f
            }
            "touchpad" -> ControlData.TouchPad().apply {
                name = nextControlName(R.string.editor_default_touchpad_name, controls.size + 1)
                x = 0.7f
                y = 0.7f
                width = 0.3f
                height = 0.3f
            }
            "mousewheel" -> ControlData.MouseWheel().apply {
                name = nextControlName(R.string.editor_default_mousewheel_name, controls.size + 1)
                x = 0.9f
                y = 0.5f
                width = 0.08f
                height = 0.15f
            }
            "text" -> ControlData.Text().apply {
                name = nextControlName(R.string.editor_default_text_name, controls.size + 1)
                displayText = appContext.getString(R.string.editor_default_text_name)
                x = 0.5f
                y = 0.3f
                width = 0.15f
                height = 0.05f
            }
            "radialmenu" -> ControlData.RadialMenu().apply {
                name = nextControlName(R.string.control_editor_radial_menu_label, controls.size + 1)
                x = 0.5f
                y = 0.5f
                width = 0.12f
                height = 0.12f
            }
            "dpad" -> ControlData.DPad().apply {
                name = nextControlName(R.string.control_editor_dpad_label, controls.size + 1)
                x = 0.15f
                y = 0.65f
                width = 0.25f
                height = 0.25f
            }
            else -> return
        }

        val newControls = controls.toMutableList()
        newControls.add(newControl)
        updateLayoutWithControls(newControls)
        selectControl(newControl)
        saveLayout()
    }

    /**
     * 删除当前选中的控件（从当前编辑目标中删除）
     */
    fun deleteSelectedControl() {
        val selected = _selectedControl.value ?: return
        val controls = getActiveControls() ?: return
        
        val newControls = controls.toMutableList()
        newControls.removeAll { it.id == selected.id }
        
        updateLayoutWithControls(newControls)
        selectControl(null)
        saveLayout()
    }

    /**
     * 复制当前选中的控件（在当前编辑目标中复制）
     */
    fun duplicateSelectedControl() {
        val selected = _selectedControl.value ?: return
        val controls = getActiveControls() ?: return
        
        // 深拷贝控件并生成新的 ID 和名称
        val duplicated = selected.deepCopy().apply {
            id = UUID.randomUUID().toString()
            name = "${selected.name}_${appContext.getString(R.string.editor_copy_suffix)}"
            // 稍微偏移位置，避免完全重叠
            x = (x + 0.05f).coerceAtMost(0.95f)
            y = (y + 0.05f).coerceAtMost(0.95f)
        }
        
        val newControls = controls.toMutableList()
        newControls.add(duplicated)
        updateLayoutWithControls(newControls)
        selectControl(duplicated)
        saveLayout()
    }

    /**
     * 保存布局
     */
    fun saveLayout() {
        val packId = currentPackId ?: return
        val layout = _layoutState.value ?: return
        viewModelScope.launch {
            packManager.savePackLayout(packId, layout)
            // 更新原始快照
            originalLayoutSnapshot = serializeLayout(layout)
            _hasUnsavedChanges.value = false
        }
    }

    // ========== 对话框控制方法 ==========

    /**
     * 请求退出 (会检查未保存变更)
     */
    fun requestExit(): Boolean {
        if (_hasUnsavedChanges.value) {
            _showExitDialog.value = true
            return false // 表示需要确认
        }
        return true // 可以直接退出
    }

    /**
     * 关闭退出确认对话框
     */
    fun dismissExitDialog() {
        _showExitDialog.value = false
    }

    /**
     * 保存并退出
     */
    fun saveAndExit(onExit: () -> Unit) {
        saveLayout()
        _showExitDialog.value = false
        onExit()
    }

    /**
     * 不保存直接退出
     */
    fun exitWithoutSaving(onExit: () -> Unit) {
        _showExitDialog.value = false
        onExit()
    }

    /**
     * 显示按键选择器
     */
    fun showKeySelector(button: ControlData.Button) {
        _showKeySelector.value = button
    }

    /**
     * 关闭按键选择器
     */
    fun dismissKeySelector() {
        _showKeySelector.value = null
    }

    /**
     * 更新按钮的按键映射
     */
    fun updateButtonKeycode(button: ControlData.Button, keycode: ControlData.KeyCode) {
        val updated = button.deepCopy() as ControlData.Button
        updated.keycode = keycode
        updateControl(updated)
        _showKeySelector.value = null
    }

    /**
     * 显示摇杆键位映射对话框
     */
    fun showJoystickKeyMapping(joystick: ControlData.Joystick) {
        _showJoystickKeyMapping.value = joystick
    }

    /**
     * 关闭摇杆键位映射对话框
     */
    fun dismissJoystickKeyMapping() {
        _showJoystickKeyMapping.value = null
    }

    /**
     * 更新摇杆键位映射
     */
    fun updateJoystickKeys(joystick: ControlData.Joystick, keys: Array<ControlData.KeyCode>) {
        val updated = joystick.deepCopy() as ControlData.Joystick
        updated.joystickKeys = keys
        updateControl(updated)
        _showJoystickKeyMapping.value = null
    }

    /**
     * 切换编辑器设置面板
     */
    fun toggleEditorSettings(visible: Boolean) {
        _showEditorSettings.value = visible
    }

    /**
     * 切换网格显示
     */
    fun toggleGrid(visible: Boolean) {
        _isGridVisible.value = visible
    }

    /**
     * 显示纹理选择器
     * @param control 目标控件
     * @param textureType 纹理类型 (如 "normal", "pressed", "background", "knob" 等)
     */
    fun showTextureSelector(control: ControlData, textureType: String) {
        _showTextureSelector.value = Pair(control, textureType)
    }

    /**
     * 关闭纹理选择器
     */
    fun dismissTextureSelector() {
        _showTextureSelector.value = null
    }

    /**
     * 显示多边形编辑器
     */
    fun showPolygonEditor(button: ControlData.Button) {
        _showPolygonEditor.value = button
    }

    /**
     * 关闭多边形编辑器
     */
    fun dismissPolygonEditor() {
        _showPolygonEditor.value = null
    }

    /**
     * 更新多边形顶点
     */
    fun updatePolygonPoints(button: ControlData.Button, points: List<ControlData.Button.Point>) {
        val updated = button.deepCopy() as ControlData.Button
        updated.polygonPoints = points
        updateControl(updated)
        _showPolygonEditor.value = null
    }

    /**
     * 请求选择图片
     */
    fun requestPickImage() {
        _pickImageRequest.value = true
    }

    /**
     * 清除图片选择请求
     */
    fun clearPickImageRequest() {
        _pickImageRequest.value = false
    }

    /**
     * 处理图片选择结果
     */
    fun onImagePicked(imagePath: String) {
        val (control, textureType) = _showTextureSelector.value ?: return
        
        when (control) {
            is ControlData.Button -> updateButtonTexture(control, textureType, imagePath, true)
            is ControlData.Joystick -> updateJoystickTexture(control, textureType, imagePath, true)
            else -> {}
        }
    }

    /**
     * 更新按钮纹理
     */
    fun updateButtonTexture(
        button: ControlData.Button,
        textureType: String,
        path: String,
        enabled: Boolean
    ) {
        val updated = button.deepCopy() as ControlData.Button
        val newConfig = com.app.ralaunch.feature.controls.textures.TextureConfig(
            path = path,
            enabled = enabled
        )
        updated.texture = when (textureType) {
            "normal" -> updated.texture.copy(normal = newConfig)
            "pressed" -> updated.texture.copy(pressed = newConfig)
            "toggled" -> updated.texture.copy(toggled = newConfig)
            else -> updated.texture
        }
        updateControl(updated)
        _showTextureSelector.value = null
    }

    /**
     * 更新摇杆纹理
     */
    fun updateJoystickTexture(
        joystick: ControlData.Joystick,
        textureType: String,
        path: String,
        enabled: Boolean
    ) {
        val updated = joystick.deepCopy() as ControlData.Joystick
        val newConfig = com.app.ralaunch.feature.controls.textures.TextureConfig(
            path = path,
            enabled = enabled
        )
        updated.texture = when (textureType) {
            "background" -> updated.texture.copy(background = newConfig)
            "knob" -> updated.texture.copy(knob = newConfig)
            "backgroundPressed" -> updated.texture.copy(backgroundPressed = newConfig)
            "knobPressed" -> updated.texture.copy(knobPressed = newConfig)
            else -> updated.texture
        }
        updateControl(updated)
        _showTextureSelector.value = null
    }
}
