package com.app.ralaunch.feature.main.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.editors.ControlEditorActivity
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.feature.controls.packs.ControlPackInfo
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.shared.core.component.menu.AnchoredActionItem
import com.app.ralaunch.shared.core.component.menu.AnchoredActionMenu
import com.app.ralaunch.shared.core.component.menu.AnchoredActionMenuStyle
import java.io.File

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
    val packManager = remember { KoinJavaComponent.get<ControlPackManager>(ControlPackManager::class.java) }

    // 状态
    var layouts by remember { mutableStateOf<List<ControlPackInfo>>(emptyList()) }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var quickSwitchIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showMoreMenu by remember { mutableStateOf<ControlPackInfo?>(null) }
    var exportingPackId by remember { mutableStateOf<String?>(null) }

    // 加载布局列表
    fun loadLayouts() {
        layouts = packManager.getInstalledPacks()
        selectedPackId = packManager.getSelectedPackId()
        quickSwitchIds = packManager.getQuickSwitchPackIds()
    }

    // 初始加载
    LaunchedEffect(Unit) {
        loadLayouts()
    }

    // Activity Result Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportUri ->
            exportingPackId?.let { packId ->
                exportPackToZip(context, packManager, exportUri, packId)
            }
        }
        exportingPackId = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            importPackFromUri(context, packManager, it) { loadLayouts() }
        }
    }

    // 创建新布局
    fun createNewLayout(name: String) {
        if (name.isBlank()) return

        if (layouts.any { it.name.equals(name, ignoreCase = true) }) {
            Toast.makeText(context, context.getString(R.string.control_layout_name_exists), Toast.LENGTH_SHORT).show()
            return
        }

        val newPack = packManager.createPack(name)

        if (selectedPackId == null) {
            packManager.setSelectedPackId(newPack.id)
        }

        showCreateDialog = false
        loadLayouts()
        ControlEditorActivity.start(context, newPack.id)
    }

    // 设为默认
    fun setDefaultLayout(pack: ControlPackInfo) {
        packManager.setSelectedPackId(pack.id)
        selectedPackId = pack.id
        Toast.makeText(context, context.getString(R.string.control_set_as_default), Toast.LENGTH_SHORT).show()
    }

    // 重命名
    fun renameLayout(pack: ControlPackInfo, newName: String) {
        if (newName.isBlank()) return

        if (layouts.any { it.id != pack.id && it.name.equals(newName, ignoreCase = true) }) {
            Toast.makeText(context, context.getString(R.string.control_layout_name_exists), Toast.LENGTH_SHORT).show()
            return
        }

        packManager.renamePack(pack.id, newName)
        showRenameDialog = null
        loadLayouts()
    }

    // 删除
    fun deleteLayout(pack: ControlPackInfo) {
        packManager.deletePack(pack.id)

        if (selectedPackId == pack.id) {
            val remaining = packManager.getInstalledPacks()
            packManager.setSelectedPackId(remaining.firstOrNull()?.id)
        }

        showDeleteDialog = null
        loadLayouts()
        Toast.makeText(context, context.getString(R.string.control_layout_deleted), Toast.LENGTH_SHORT).show()
    }

    // 预览状态
    var showPreviewDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    
    // 获取预览图路径
    fun getPreviewImages(pack: ControlPackInfo): List<File> {
        val packDir = packManager.getPackDir(pack.id)
        return if (pack.previewImagePaths.isNotEmpty()) {
            pack.previewImagePaths.mapNotNull { path ->
                val file = File(packDir, path)
                if (file.exists()) file else null
            }
        } else {
            // 尝试默认 preview.jpg/png
            listOf("preview.jpg", "preview.png", "preview.webp").mapNotNull { name ->
                val file = File(packDir, name)
                if (file.exists()) file else null
            }
        }
    }

    // UI
    ControlLayoutScreen(
        layouts = layouts,
        selectedPackId = selectedPackId,
        quickSwitchIds = quickSwitchIds,
        showCreateDialog = showCreateDialog,
        showDeleteDialog = showDeleteDialog,
        showRenameDialog = showRenameDialog,
        showMoreMenu = showMoreMenu,
        onBack = onBack,
        onOpenStore = onOpenStore,
        onCreateClick = { showCreateDialog = true },
        onCreateConfirm = { createNewLayout(it) },
        onCreateDismiss = { showCreateDialog = false },
        onLayoutClick = { pack -> ControlEditorActivity.start(context, pack.id) },
        onSetDefault = { setDefaultLayout(it) },
        onToggleQuickSwitch = { packId, enabled ->
            if (enabled) packManager.addToQuickSwitch(packId)
            else packManager.removeFromQuickSwitch(packId)
            quickSwitchIds = packManager.getQuickSwitchPackIds()
        },
        onShowMoreMenu = { showMoreMenu = it },
        onDismissMoreMenu = { showMoreMenu = null },
        onRenameClick = { showRenameDialog = it; showMoreMenu = null },
        onRenameConfirm = { pack, name -> renameLayout(pack, name) },
        onRenameDismiss = { showRenameDialog = null },
        onDeleteClick = { showDeleteDialog = it; showMoreMenu = null },
        onDeleteConfirm = { deleteLayout(it) },
        onDeleteDismiss = { showDeleteDialog = null },
        onExportClick = { pack ->
            exportingPackId = pack.id
            exportLauncher.launch("${pack.name}.zip")
            showMoreMenu = null
        },
        onImportClick = { importLauncher.launch(arrayOf("application/zip")) },
        onPreviewClick = { showPreviewDialog = it }
    )
    
    // 本地布局预览对话框
    showPreviewDialog?.let { pack ->
        LocalLayoutPreviewDialog(
            pack = pack,
            previewImages = getPreviewImages(pack),
            onDismiss = { showPreviewDialog = null },
            onEditClick = { 
                showPreviewDialog = null
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
    showCreateDialog: Boolean,
    showDeleteDialog: ControlPackInfo?,
    showRenameDialog: ControlPackInfo?,
    showMoreMenu: ControlPackInfo?,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
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
    // 当前选中的布局（用于右侧详情面板）
    var selectedLayout by remember { mutableStateOf<ControlPackInfo?>(null) }
    
    // 初始选中默认布局
    LaunchedEffect(layouts, selectedPackId) {
        if (selectedLayout == null || layouts.none { it.id == selectedLayout?.id }) {
            selectedLayout = layouts.find { it.id == selectedPackId } ?: layouts.firstOrNull()
        }
    }

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
                onLayoutSelect = { selectedLayout = it },
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

                Column(
                    modifier = Modifier.fillMaxSize()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentLayout.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentLayout.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
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

                Spacer(modifier = Modifier.weight(1f))

                // 操作按钮（窄屏自适应为两行，避免文字换行）
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    val useTwoRows = !isDefault && maxWidth < 560.dp

                    if (useTwoRows) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPreviewClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = buttonPadding
                                ) {
                                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.control_layout_preview), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = onSetDefault,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = buttonPadding
                                ) {
                                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_set_default), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Button(
                                onClick = onEditClick,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = buttonPadding
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.control_layout_edit), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPreviewClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = buttonPadding
                            ) {
                                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.control_layout_preview), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            if (!isDefault) {
                                OutlinedButton(
                                    onClick = onSetDefault,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = buttonPadding
                                ) {
                                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_set_default), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Button(
                                onClick = onEditClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = buttonPadding
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.control_layout_edit), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
