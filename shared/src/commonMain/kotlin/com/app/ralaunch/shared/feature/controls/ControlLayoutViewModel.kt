package com.app.ralaunch.shared.feature.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.shared.core.model.domain.ControlLayout
import com.app.ralaunch.shared.core.model.domain.ControlConfig
import com.app.ralaunch.shared.core.contract.repository.ControlLayoutRepositoryV2
import com.app.ralaunch.shared.generated.resources.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 控制布局 UI 状态 - 跨平台
 */
data class ControlLayoutUiState(
    val layouts: List<ControlLayout> = emptyList(),
    val currentLayout: ControlLayout? = null,
    val selectedControl: ControlConfig? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    val layoutToDelete: ControlLayout? = null,
    val showImportDialog: Boolean = false
)

/**
 * 控制布局事件 - 跨平台
 */
sealed class ControlLayoutEvent {
    object LoadLayouts : ControlLayoutEvent()
    data class SelectLayout(val layout: ControlLayout) : ControlLayoutEvent()
    data class SelectControl(val control: ControlConfig?) : ControlLayoutEvent()
    object CreateNewLayout : ControlLayoutEvent()
    data class DuplicateLayout(val layout: ControlLayout) : ControlLayoutEvent()
    data class DeleteLayout(val layout: ControlLayout) : ControlLayoutEvent()
    object ConfirmDelete : ControlLayoutEvent()
    object DismissDeleteDialog : ControlLayoutEvent()
    object StartEditing : ControlLayoutEvent()
    object StopEditing : ControlLayoutEvent()
    data class SaveLayout(val layout: ControlLayout) : ControlLayoutEvent()
    data class UpdateControl(val control: ControlConfig) : ControlLayoutEvent()
    data class RemoveControl(val control: ControlConfig) : ControlLayoutEvent()
    data class AddControl(val control: ControlConfig) : ControlLayoutEvent()
    object ShowImportDialog : ControlLayoutEvent()
    object DismissImportDialog : ControlLayoutEvent()
    data class ImportLayout(val path: String) : ControlLayoutEvent()
    data class ExportLayout(val layout: ControlLayout) : ControlLayoutEvent()
    object ClearError : ControlLayoutEvent()
}

/**
 * 控制布局副作用 - 跨平台
 */
sealed class ControlLayoutEffect {
    data class ShowToast(val message: String) : ControlLayoutEffect()
    object OpenFilePicker : ControlLayoutEffect()
    data class OpenSavePicker(val defaultName: String) : ControlLayoutEffect()
    object LayoutSaved : ControlLayoutEffect()
    data class ExportComplete(val path: String) : ControlLayoutEffect()
    data class ImportComplete(val layout: ControlLayout) : ControlLayoutEffect()
}

/**
 * 控制布局 ViewModel - 跨平台
 */
class ControlLayoutViewModel(
    private val repository: ControlLayoutRepositoryV2
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlLayoutUiState())
    val uiState: StateFlow<ControlLayoutUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ControlLayoutEffect>(extraBufferCapacity = 16)
    val effect: SharedFlow<ControlLayoutEffect> = _effect.asSharedFlow()
    private var isLayoutsObserverStarted = false

    init {
        loadLayouts()
    }

    /**
     * 处理事件
     */
    fun onEvent(event: ControlLayoutEvent) {
        when (event) {
            is ControlLayoutEvent.LoadLayouts -> loadLayouts()
            is ControlLayoutEvent.SelectLayout -> selectLayout(event.layout)
            is ControlLayoutEvent.SelectControl -> selectControl(event.control)
            is ControlLayoutEvent.CreateNewLayout -> createNewLayout()
            is ControlLayoutEvent.DuplicateLayout -> duplicateLayout(event.layout)
            is ControlLayoutEvent.DeleteLayout -> showDeleteDialog(event.layout)
            is ControlLayoutEvent.ConfirmDelete -> confirmDelete()
            is ControlLayoutEvent.DismissDeleteDialog -> dismissDeleteDialog()
            is ControlLayoutEvent.StartEditing -> startEditing()
            is ControlLayoutEvent.StopEditing -> stopEditing()
            is ControlLayoutEvent.SaveLayout -> saveLayout(event.layout)
            is ControlLayoutEvent.UpdateControl -> updateControl(event.control)
            is ControlLayoutEvent.RemoveControl -> removeControl(event.control)
            is ControlLayoutEvent.AddControl -> addControl(event.control)
            is ControlLayoutEvent.ShowImportDialog -> showImportDialog()
            is ControlLayoutEvent.DismissImportDialog -> dismissImportDialog()
            is ControlLayoutEvent.ImportLayout -> importLayout(event.path)
            is ControlLayoutEvent.ExportLayout -> exportLayout(event.layout)
            is ControlLayoutEvent.ClearError -> clearError()
        }
    }

    private fun sendEffect(effect: ControlLayoutEffect) {
        _effect.tryEmit(effect)
    }

    // ==================== 布局管理 ====================

    private fun loadLayouts() {
        if (isLayoutsObserverStarted) return
        isLayoutsObserverStarted = true
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                repository.layouts.collect { layouts ->
                    _uiState.update {
                        it.copy(
                            layouts = layouts,
                            isLoading = false,
                            currentLayout = it.currentLayout ?: layouts.firstOrNull()
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: getString(Res.string.error_operation_failed)
                _uiState.update {
                    it.copy(
                        error = errorMessage,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun selectLayout(layout: ControlLayout) {
        _uiState.update {
            it.copy(
                currentLayout = layout,
                selectedControl = null,
                isEditing = false
            )
        }
    }

    private fun selectControl(control: ControlConfig?) {
        _uiState.update { it.copy(selectedControl = control) }
    }

    private fun createNewLayout() {
        viewModelScope.launch {
            try {
                val newLayout = ControlLayout(
                    id = "layout_${System.currentTimeMillis()}",
                    name = getString(Res.string.control_new_layout),
                    controls = emptyList()
                )
                repository.saveLayout(newLayout)
                _uiState.update {
                    it.copy(
                        currentLayout = newLayout,
                        isEditing = true
                    )
                }
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.control_layout_created)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun duplicateLayout(layout: ControlLayout) {
        viewModelScope.launch {
            try {
                val duplicated = layout.copy(
                    id = "layout_${System.currentTimeMillis()}",
                    name = "${layout.name} ${getString(Res.string.control_layout_copy_suffix)}",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveLayout(duplicated)
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.control_layout_duplicated)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ==================== 删除确认 ====================

    private fun showDeleteDialog(layout: ControlLayout) {
        _uiState.update {
            it.copy(
                showDeleteDialog = true,
                layoutToDelete = layout
            )
        }
    }

    private fun confirmDelete() {
        val layout = _uiState.value.layoutToDelete ?: return

        viewModelScope.launch {
            try {
                repository.deleteLayout(layout.id)
                _uiState.update {
                    it.copy(
                        currentLayout = if (it.currentLayout?.id == layout.id) {
                            it.layouts.firstOrNull { l -> l.id != layout.id }
                        } else {
                            it.currentLayout
                        }
                    )
                }
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.control_layout_deleted)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }

        dismissDeleteDialog()
    }

    private fun dismissDeleteDialog() {
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                layoutToDelete = null
            )
        }
    }

    // ==================== 编辑模式 ====================

    private fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    private fun stopEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                selectedControl = null
            )
        }
    }

    private fun saveLayout(layout: ControlLayout) {
        viewModelScope.launch {
            try {
                repository.saveLayout(layout.copy(updatedAt = System.currentTimeMillis()))
                _uiState.update { it.copy(currentLayout = layout) }
                sendEffect(ControlLayoutEffect.LayoutSaved)
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.editor_layout_saved)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ==================== 控件编辑 ====================

    private fun updateControl(control: ControlConfig) {
        val currentLayout = _uiState.value.currentLayout ?: return
        val updatedControls = currentLayout.controls.map {
            if (it.id == control.id) control else it
        }
        val updatedLayout = currentLayout.copy(controls = updatedControls)
        _uiState.update {
            it.copy(
                currentLayout = updatedLayout,
                selectedControl = control
            )
        }
    }

    private fun removeControl(control: ControlConfig) {
        val currentLayout = _uiState.value.currentLayout ?: return
        val updatedControls = currentLayout.controls.filter { it.id != control.id }
        val updatedLayout = currentLayout.copy(controls = updatedControls)
        _uiState.update {
            it.copy(
                currentLayout = updatedLayout,
                selectedControl = if (it.selectedControl?.id == control.id) null else it.selectedControl
            )
        }
    }

    private fun addControl(control: ControlConfig) {
        val currentLayout = _uiState.value.currentLayout ?: return
        val updatedLayout = currentLayout.copy(
            controls = currentLayout.controls + control
        )
        _uiState.update {
            it.copy(
                currentLayout = updatedLayout,
                selectedControl = control
            )
        }
    }

    // ==================== 导入导出 ====================

    private fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true) }
        sendEffect(ControlLayoutEffect.OpenFilePicker)
    }

    private fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false) }
    }

    private fun importLayout(path: String) {
        viewModelScope.launch {
            val result = repository.importPack(path)
            result.getOrNull()?.let { layout ->
                sendEffect(ControlLayoutEffect.ImportComplete(layout))
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.control_layout_imported)))
            }
            result.exceptionOrNull()?.let { e ->
                val errorMessage = e.message ?: getString(Res.string.error_operation_failed)
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
        dismissImportDialog()
    }

    private fun exportLayout(layout: ControlLayout) {
        sendEffect(ControlLayoutEffect.OpenSavePicker(layout.name))
    }

    /**
     * 执行导出（供平台层调用）
     */
    fun performExport(layout: ControlLayout, path: String) {
        viewModelScope.launch {
            val result = repository.exportLayout(layout.id, path)
            result.getOrNull()?.let { exportPath ->
                sendEffect(ControlLayoutEffect.ExportComplete(exportPath))
                sendEffect(ControlLayoutEffect.ShowToast(getString(Res.string.control_export_success)))
            }
            result.exceptionOrNull()?.let { e ->
                val errorMessage = e.message ?: getString(Res.string.error_operation_failed)
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    // ==================== 错误处理 ====================

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
