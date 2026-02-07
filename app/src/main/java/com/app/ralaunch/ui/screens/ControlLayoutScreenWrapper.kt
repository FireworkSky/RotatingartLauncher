package com.app.ralaunch.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.controls.editors.ControlEditorActivity
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.controls.packs.ControlPackInfo
import com.app.ralaunch.controls.packs.ControlPackManager
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
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showMoreMenu by remember { mutableStateOf<ControlPackInfo?>(null) }
    var exportingPackId by remember { mutableStateOf<String?>(null) }

    // 加载布局列表
    fun loadLayouts() {
        layouts = packManager.getInstalledPacks()
        selectedPackId = packManager.getSelectedPackId()
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
            Toast.makeText(context, "布局名称已存在", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "已设为默认布局", Toast.LENGTH_SHORT).show()
    }

    // 重命名
    fun renameLayout(pack: ControlPackInfo, newName: String) {
        if (newName.isBlank()) return

        if (layouts.any { it.id != pack.id && it.name.equals(newName, ignoreCase = true) }) {
            Toast.makeText(context, "布局名称已存在", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "布局已删除", Toast.LENGTH_SHORT).show()
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
                isDefault = selectedLayout?.id == selectedPackId,
                onEditClick = { selectedLayout?.let { onLayoutClick(it) } },
                onSetDefault = { selectedLayout?.let { onSetDefault(it) } },
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
            title = { Text("删除布局") },
            text = { Text("确定要删除 \"${pack.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteConfirm(pack) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text("取消") }
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
                    "暂无控制布局",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建布局")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onImportClick) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入布局")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenStore) {
                    Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("控件商店")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(layouts, key = { it.id }) { pack ->
                    LayoutListItem(
                        pack = pack,
                        isDefault = pack.id == selectedPackId,
                        isSelected = pack.id == selectedLayout?.id,
                        showMoreMenu = showMoreMenu?.id == pack.id,
                        onClick = { onLayoutSelect(pack) },
                        onDoubleClick = { onLayoutClick(pack) },
                        onSetDefault = { onSetDefault(pack) },
                        onShowMoreMenu = { onShowMoreMenu(pack) },
                        onDismissMoreMenu = onDismissMoreMenu,
                        onRenameClick = { onRenameClick(pack) },
                        onDeleteClick = { onDeleteClick(pack) },
                        onExportClick = { onExportClick(pack) }
                    )
                }
            }
        }

        // FAB 区域 - 商店 + 导入 + 新建
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 商店按钮
            FloatingActionButton(
                onClick = onOpenStore,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Storefront, "控件商店")
            }
            
            // 导入按钮
            FloatingActionButton(
                onClick = onImportClick,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(Icons.Default.FileOpen, "导入布局")
            }
            
            // 新建按钮
            if (layouts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, "新建布局")
                }
            }
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
                                text = "默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = pack.author.ifBlank { "自定义布局" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 更多菜单
            Box {
                IconButton(onClick = onShowMoreMenu, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMoreMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { onDoubleClick(); onDismissMoreMenu() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    if (!isDefault) {
                        DropdownMenuItem(
                            text = { Text("设为默认") },
                            onClick = { onSetDefault(); onDismissMoreMenu() },
                            leadingIcon = { Icon(Icons.Default.Check, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = onRenameClick,
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("导出") },
                        onClick = onExportClick,
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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
    isDefault: Boolean,
    onEditClick: () -> Unit,
    onSetDefault: () -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        if (layout != null) {
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
                                text = layout.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("默认") },
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
                            text = layout.author.ifBlank { "自定义布局" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (layout.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = layout.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("预览")
                    }
                    if (!isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("设为默认")
                        }
                    }
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("编辑")
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
                        text = "选择一个布局",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}


