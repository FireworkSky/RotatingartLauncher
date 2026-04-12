package com.app.ralaunch.feature.controls.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.editors.ui.ControlEditorActivity
import com.app.ralaunch.feature.controls.packs.ControlPackInfo
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.core.ui.component.AnchoredActionItem
import com.app.ralaunch.core.ui.component.AnchoredActionMenu
import com.app.ralaunch.core.ui.component.AnchoredActionMenuStyle
import com.app.ralaunch.feature.controls.vm.ControlLayoutViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 控制布局管理 Screen - 纯 Composable 版本
 * 
 * 从 ControlLayoutComposeFragment 提取，去除 Fragment 依赖
 */
@Composable
fun ControlLayoutScreenWrapper(
    onBack: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    val context = LocalContext.current
    val packManager: ControlPackManager = koinInject()
    val viewModel: ControlLayoutViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var exportingPackId by remember { mutableStateOf<String?>(null) }

    // Activity Result Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportUri ->
            exportingPackId?.let { packId ->
                exportPackToZip(context, packManager, exportUri, packId)
                viewModel.loadLayouts()
            }
        }
        exportingPackId = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            importPackFromUri(context, packManager, it) { viewModel.loadLayouts() }
        }
    }

    // UI
    ControlLayoutScreen(
        layouts = uiState.layouts,
        selectedPackId = uiState.selectedPackId,
        quickSwitchIds = uiState.quickSwitchIds,
        selectedLayout = uiState.selectedLayout,
        showCreateDialog = uiState.showCreateDialog,
        showDeleteDialog = uiState.deleteDialogPack,
        showRenameDialog = uiState.renameDialogPack,
        showMoreMenu = uiState.moreMenuPack,
        onBack = onBack,
        onOpenStore = onOpenStore,
        onLayoutSelect = viewModel::selectLayout,
        onCreateClick = viewModel::showCreateDialog,
        onCreateConfirm = { name ->
            viewModel.createNewLayout(name)
                .onSuccess { newPack ->
                    ControlEditorActivity.start(context, newPack.id)
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "", Toast.LENGTH_SHORT).show()
                }
        },
        onCreateDismiss = viewModel::dismissCreateDialog,
        onLayoutClick = { pack -> ControlEditorActivity.start(context, pack.id) },
        onSetDefault = {
            viewModel.setDefaultLayout(it)
            Toast.makeText(context, context.getString(R.string.control_set_as_default), Toast.LENGTH_SHORT).show()
        },
        onToggleQuickSwitch = { packId, enabled ->
            viewModel.toggleQuickSwitch(packId, enabled)
        },
        onShowMoreMenu = viewModel::showMoreMenu,
        onDismissMoreMenu = viewModel::dismissMoreMenu,
        onRenameClick = viewModel::showRenameDialog,
        onRenameConfirm = { pack, name ->
            viewModel.renameLayout(pack, name)
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "", Toast.LENGTH_SHORT).show()
                }
        },
        onRenameDismiss = viewModel::dismissRenameDialog,
        onDeleteClick = viewModel::showDeleteDialog,
        onDeleteConfirm = {
            if (viewModel.deleteLayout(it)) {
                Toast.makeText(context, context.getString(R.string.control_layout_deleted), Toast.LENGTH_SHORT).show()
            }
        },
        onDeleteDismiss = viewModel::dismissDeleteDialog,
        onExportClick = { pack ->
            exportingPackId = pack.id
            exportLauncher.launch("${pack.name}.zip")
            viewModel.dismissMoreMenu()
        },
        onImportClick = { importLauncher.launch(arrayOf("application/zip")) },
        onPreviewClick = viewModel::showPreview
    )
    
    // 本地布局预览对话框
    uiState.previewPack?.let { pack ->
        LocalLayoutPreviewDialog(
            pack = pack,
            previewImages = viewModel.getPreviewImages(pack),
            onDismiss = viewModel::dismissPreview,
            onEditClick = { 
                viewModel.dismissPreview()
                ControlEditorActivity.start(context, pack.id)
            }
        )
    }
}

// ==================== UI 组件 ====================

@Composable
private fun ControlLayoutScreen(
    layouts: List<ControlPackInfo>,
    selectedPackId: String?,
    quickSwitchIds: List<String>,
    selectedLayout: ControlPackInfo?,
    showCreateDialog: Boolean,
    showDeleteDialog: ControlPackInfo?,
    showRenameDialog: ControlPackInfo?,
    showMoreMenu: ControlPackInfo?,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onLayoutSelect: (ControlPackInfo) -> Unit,
    onCreateClick: () -> Unit,
    onCreateConfirm: (String) -> Unit,
    onCreateDismiss: () -> Unit,
    onLayoutClick: (ControlPackInfo) -> Unit,
    onSetDefault: (ControlPackInfo) -> Unit,
    onToggleQuickSwitch: (String, Boolean) -> Unit,
    onShowMoreMenu: (ControlPackInfo) -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: (ControlPackInfo) -> Unit,
    onRenameConfirm: (ControlPackInfo, String) -> Unit,
    onRenameDismiss: () -> Unit,
    onDeleteClick: (ControlPackInfo) -> Unit,
    onDeleteConfirm: (ControlPackInfo) -> Unit,
    onDeleteDismiss: () -> Unit,
    onExportClick: (ControlPackInfo) -> Unit,
    onImportClick: () -> Unit,
    onPreviewClick: (ControlPackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 双栏布局
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：布局列表
            LayoutListPanel(
                layouts = layouts,
                selectedPackId = selectedPackId,
                quickSwitchIds = quickSwitchIds,
                selectedLayout = selectedLayout,
                showMoreMenu = showMoreMenu,
                onLayoutSelect = onLayoutSelect,
                onLayoutClick = onLayoutClick,
                onSetDefault = onSetDefault,
                onShowMoreMenu = onShowMoreMenu,
                onDismissMoreMenu = onDismissMoreMenu,
                onRenameClick = onRenameClick,
                onDeleteClick = onDeleteClick,
                onExportClick = onExportClick,
                onCreateClick = onCreateClick,
                onOpenStore = onOpenStore,
                onImportClick = onImportClick,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )

            // 分隔线
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 右侧：布局详情/预览
            LayoutDetailPanel(
                layout = selectedLayout,
                selectedPackId = selectedPackId,
                quickSwitchIds = quickSwitchIds,
                onEditClick = { selectedLayout?.let { onLayoutClick(it) } },
                onSetDefault = { selectedLayout?.let { onSetDefault(it) } },
                onToggleQuickSwitch = { enabled ->
                    selectedLayout?.let { onToggleQuickSwitch(it.id, enabled) }
                },
                onPreviewClick = { selectedLayout?.let { onPreviewClick(it) } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }

    // 对话框
    if (showCreateDialog) {
        CreateLayoutDialog(onConfirm = onCreateConfirm, onDismiss = onCreateDismiss)
    }

    showRenameDialog?.let { pack ->
        RenameLayoutDialog(
            currentName = pack.name,
            onConfirm = { onRenameConfirm(pack, it) },
            onDismiss = onRenameDismiss
        )
    }

    showDeleteDialog?.let { pack ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.control_delete_layout)) },
            text = { Text(stringResource(R.string.control_layout_delete_irreversible_confirm, pack.name)) },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteConfirm(pack) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * 左侧布局列表面板
 */
@Composable
private fun LayoutListPanel(
    layouts: List<ControlPackInfo>,
    selectedPackId: String?,
    quickSwitchIds: List<String>,
    selectedLayout: ControlPackInfo?,
    showMoreMenu: ControlPackInfo?,
    onLayoutSelect: (ControlPackInfo) -> Unit,
    onLayoutClick: (ControlPackInfo) -> Unit,
    onSetDefault: (ControlPackInfo) -> Unit,
    onShowMoreMenu: (ControlPackInfo) -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: (ControlPackInfo) -> Unit,
    onDeleteClick: (ControlPackInfo) -> Unit,
    onExportClick: (ControlPackInfo) -> Unit,
    onCreateClick: () -> Unit,
    onOpenStore: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fabMenuExpanded by remember { mutableStateOf(false) }
    val mainFabSize = 56.dp
    val fabOuterMargin = 12.dp
    val safeGap = 8.dp
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomReserve = mainFabSize + fabOuterMargin + safeGap + navBarBottom
    val fabItems = listOf(
        AnchoredActionItem(
            key = "store",
            icon = Icons.Default.Storefront,
            contentDescription = stringResource(R.string.pack_store),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onOpenStore
        ),
        AnchoredActionItem(
            key = "import",
            icon = Icons.Default.FileOpen,
            contentDescription = stringResource(R.string.import_layout),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = onImportClick
        ),
        AnchoredActionItem(
            key = "create",
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.control_new_layout_button),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = onCreateClick
        )
    )

    LaunchedEffect(showMoreMenu) {
        if (showMoreMenu != null) {
            fabMenuExpanded = false
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        if (layouts.isEmpty()) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.control_layout_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.control_new_layout_button))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onImportClick) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_layout))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenStore) {
                    Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pack_store))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp + bottomReserve
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(layouts, key = { it.id }) { pack ->
                    LayoutListItem(
                        pack = pack,
                        isDefault = pack.id == selectedPackId,
                        isQuickSwitch = pack.id in quickSwitchIds,
                        isSelected = pack.id == selectedLayout?.id,
                        showMoreMenu = showMoreMenu?.id == pack.id,
                        onClick = {
                            fabMenuExpanded = false
                            onLayoutSelect(pack)
                        },
                        onDoubleClick = {
                            fabMenuExpanded = false
                            onLayoutClick(pack)
                        },
                        onSetDefault = { onSetDefault(pack) },
                        onShowMoreMenu = {
                            fabMenuExpanded = false
                            onShowMoreMenu(pack)
                        },
                        onDismissMoreMenu = onDismissMoreMenu,
                        onRenameClick = { onRenameClick(pack) },
                        onDeleteClick = { onDeleteClick(pack) },
                        onExportClick = { onExportClick(pack) }
                    )
                }
            }
        }

        if (layouts.isNotEmpty()) {
            AnchoredActionMenu(
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                items = fabItems,
                modifier = Modifier
                    .matchParentSize(),
                style = AnchoredActionMenuStyle.Fab,
                anchorAlignment = Alignment.BottomEnd,
                anchorPadding = PaddingValues(fabOuterMargin),
                itemSpacing = 12.dp,
                mainButtonSize = mainFabSize,
                mainIconCollapsed = Icons.Default.Menu,
                mainIconExpanded = Icons.Default.Close,
                mainContentDescriptionCollapsed = stringResource(R.string.control_layout_show_quick_actions),
                mainContentDescriptionExpanded = stringResource(R.string.control_layout_hide_quick_actions),
                mainContainerColorCollapsed = MaterialTheme.colorScheme.primaryContainer,
                mainContainerColorExpanded = MaterialTheme.colorScheme.primary,
                mainContentColorCollapsed = MaterialTheme.colorScheme.onPrimaryContainer,
                mainContentColorExpanded = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 布局列表项
 */
@Composable
private fun LayoutListItem(
    pack: ControlPackInfo,
    isDefault: Boolean,
    isQuickSwitch: Boolean,
    isSelected: Boolean,
    showMoreMenu: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onSetDefault: () -> Unit,
    onShowMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isDefault -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TouchApp,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.layout_default_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isQuickSwitch) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = stringResource(R.string.control_layout_quick_switch_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = pack.author.ifBlank { stringResource(R.string.control_layout_custom_author) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 更多菜单
            Box {
                IconButton(onClick = onShowMoreMenu, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.control_layout_more_actions), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMoreMenu
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.control_layout_edit)) },
                        onClick = { onDoubleClick(); onDismissMoreMenu() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    if (!isDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_set_default)) },
                            onClick = { onSetDefault(); onDismissMoreMenu() },
                            leadingIcon = { Icon(Icons.Default.Check, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.control_rename_layout)) },
                        onClick = onRenameClick,
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = onExportClick,
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = onDeleteClick,
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 右侧布局详情面板
 */
@Composable
private fun LayoutDetailPanel(
    layout: ControlPackInfo?,
    selectedPackId: String?,
    quickSwitchIds: List<String>,
    onEditClick: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleQuickSwitch: (Boolean) -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        AnimatedContent(
            targetState = layout,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it?.id ?: "empty_state" },
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 200))
                    .togetherWith(fadeOut(animationSpec = tween(durationMillis = 160)))
            },
            label = "control_layout_detail_transition"
        ) { currentLayout ->
            if (currentLayout != null) {
                val isDefault = currentLayout.id == selectedPackId
                val isQuickSwitch = currentLayout.id in quickSwitchIds
                val detailScrollState = rememberScrollState()

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(detailScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 布局信息
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.TouchApp,
                                        null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentLayout.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isDefault) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.layout_default_tag)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Star,
                                                    null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentLayout.author.ifBlank { stringResource(R.string.control_layout_custom_author) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentLayout.description.isNotBlank()) {
                            Text(
                                text = currentLayout.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        // 快速切换开关
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isQuickSwitch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.control_layout_quick_switch_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.control_layout_quick_switch_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isQuickSwitch,
                                    onCheckedChange = onToggleQuickSwitch
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 操作按钮：按实际文案宽度决定是一行还是纵向堆叠
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val actionSpacing = 8.dp
                        val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        val textMeasurer = rememberTextMeasurer()
                        val density = LocalDensity.current
                        val buttonTextStyle = MaterialTheme.typography.labelLarge
                        val actionSpecs = buildList {
                            add(
                                LayoutActionSpec(
                                    icon = Icons.Default.Visibility,
                                    label = stringResource(R.string.control_layout_preview),
                                    onClick = onPreviewClick,
                                    emphasized = false
                                )
                            )
                            if (!isDefault) {
                                add(
                                    LayoutActionSpec(
                                        icon = Icons.Default.Star,
                                        label = stringResource(R.string.action_set_default),
                                        onClick = onSetDefault,
                                        emphasized = false
                                    )
                                )
                            }
                            add(
                                LayoutActionSpec(
                                    icon = Icons.Default.Edit,
                                    label = stringResource(R.string.control_layout_edit),
                                    onClick = onEditClick,
                                    emphasized = true
                                )
                            )
                        }
                        val horizontalInsetPx = with(density) {
                            // button horizontal padding + icon + spacer + a small safety reserve
                            (12.dp * 2 + 18.dp + 6.dp + 8.dp).roundToPx()
                        }
                        val spacingPx = with(density) { actionSpacing.roundToPx() }
                        val availableWidthPx = with(density) { maxWidth.roundToPx() }
                        val widestActionWidthPx = actionSpecs.maxOfOrNull { action ->
                            textMeasurer.measure(
                                text = AnnotatedString(action.label),
                                style = buttonTextStyle,
                                maxLines = 1
                            ).size.width + horizontalInsetPx
                        } ?: 0
                        val useSingleRow = availableWidthPx >=
                            (widestActionWidthPx * actionSpecs.size) + (spacingPx * (actionSpecs.size - 1))

                        if (useSingleRow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(actionSpacing)
                            ) {
                                actionSpecs.forEach { action ->
                                    LayoutDetailActionButton(
                                        action = action,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = buttonPadding,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(actionSpacing)
                            ) {
                                actionSpecs.forEach { action ->
                                    LayoutDetailActionButton(
                                        action = action,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = buttonPadding,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TouchApp,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.control_layout_select_one),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private data class LayoutActionSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val emphasized: Boolean
)

@Composable
private fun LayoutDetailActionButton(
    action: LayoutActionSpec,
    modifier: Modifier,
    contentPadding: PaddingValues,
    maxLines: Int
) {
    if (action.emphasized) {
        Button(
            onClick = action.onClick,
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            LayoutActionButtonContent(
                icon = action.icon,
                label = action.label,
                maxLines = maxLines
            )
        }
    } else {
        OutlinedButton(
            onClick = action.onClick,
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            LayoutActionButtonContent(
                icon = action.icon,
                label = action.label,
                maxLines = maxLines
            )
        }
    }
}

@Composable
private fun LayoutActionButtonContent(
    icon: ImageVector,
    label: String,
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
