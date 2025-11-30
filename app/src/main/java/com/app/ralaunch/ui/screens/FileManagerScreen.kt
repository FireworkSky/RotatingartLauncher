package com.app.ralaunch.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.app.ralaunch.ui.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(refreshKey: Int) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 左右两个面板的状态
    val leftPanelState = remember { FilePanelState(context.filesDir.parentFile!!) }
    val rightPanelState = remember { FilePanelState(File("/storage/emulated/0")) }

    // 当前活动面板
    var activePanel by remember { mutableStateOf<FilePanelState?>(leftPanelState) }

    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showFileMenu by remember { mutableStateOf<FileItem?>(null) }
    var showOperationDialog by remember { mutableStateOf<OperationDialogData?>(null) }

    // 复制/移动操作
    var pendingOperation by remember { mutableStateOf<FileOperation?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建")
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 左面板
            FilePanel(
                state = leftPanelState,
                modifier = Modifier.weight(1f),
                isActive = activePanel == leftPanelState,
                onActivate = { activePanel = leftPanelState },
                onFileLongClick = { fileItem ->
                    showFileMenu = fileItem
                    activePanel = leftPanelState
                },
                onFileClick = { fileItem ->
                    if (fileItem.isDirectory) {
                        leftPanelState.navigateTo(fileItem.file)
                    } else {
                        openFile(context, fileItem.file, snackbarHostState, coroutineScope)
                    }
                }
            )

            // 右面板
            FilePanel(
                state = rightPanelState,
                modifier = Modifier.weight(1f),
                isActive = activePanel == rightPanelState,
                onActivate = { activePanel = rightPanelState },
                onFileLongClick = { fileItem ->
                    showFileMenu = fileItem
                    activePanel = rightPanelState
                },
                onFileClick = { fileItem ->
                    if (fileItem.isDirectory) {
                        rightPanelState.navigateTo(fileItem.file)
                    } else {
                        openFile(context, fileItem.file, snackbarHostState, coroutineScope)
                    }
                }
            )
        }

        // 创建文件/文件夹对话框
        if (showCreateDialog) {
            CreateFileDialog(
                currentDirectory = activePanel?.currentDirectory?.path ?: "",
                onCreateFile = { name ->
                    val targetPanel = activePanel ?: return@CreateFileDialog
                    try {
                        val newFile = File(targetPanel.currentDirectory, name)
                        if (newFile.createNewFile()) {
                            targetPanel.refresh()
                            showCreateDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("文件创建成功")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("文件已存在或创建失败")
                            }
                        }
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("创建失败: ${e.message}")
                        }
                    }
                },
                onCreateFolder = { name ->
                    val targetPanel = activePanel
                    try {
                        val newFolder = File(targetPanel?.currentDirectory, name)
                        if (newFolder.mkdirs()) {
                            targetPanel?.refresh()
                            showCreateDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("文件夹创建成功")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("文件夹已存在或创建失败")
                            }
                        }
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("创建失败: ${e.message}")
                        }
                    }
                },
                onDismiss = { showCreateDialog = false }
            )
        }

        // 文件操作菜单
        showFileMenu?.let { fileItem ->
            FileOperationDialog(
                fileItem = fileItem,
                onDismiss = { showFileMenu = null },
                onCopy = {
                    pendingOperation = FileOperation.Copy(fileItem.file, activePanel)
                    showFileMenu = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("已选择复制: ${fileItem.name}")
                    }
                },
                onMove = {
                    pendingOperation = FileOperation.Move(fileItem.file, activePanel)
                    showFileMenu = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("已选择移动: ${fileItem.name}")
                    }
                },
                onDelete = {
                    showOperationDialog = OperationDialogData.Delete(fileItem)
                    showFileMenu = null
                },
                onRename = {
                    showOperationDialog = OperationDialogData.Rename(fileItem)
                    showFileMenu = null
                },
                onOpen = {
                    openFile(context, fileItem.file, snackbarHostState, coroutineScope)
                    showFileMenu = null
                }
            )
        }

        // 操作对话框
        showOperationDialog?.let { dialogData ->
            when (dialogData) {
                is OperationDialogData.Delete -> {
                    DeleteConfirmationDialog(
                        fileItem = dialogData.fileItem,
                        onConfirm = {
                            if (dialogData.fileItem.file.deleteRecursively()) {
                                activePanel?.refresh()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("删除成功")
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("删除失败")
                                }
                            }
                            showOperationDialog = null
                        },
                        onDismiss = { showOperationDialog = null }
                    )
                }
                is OperationDialogData.Rename -> {
                    RenameDialog(
                        fileItem = dialogData.fileItem,
                        onConfirm = { newName ->
                            val newFile = File(dialogData.fileItem.file.parent, newName)
                            if (dialogData.fileItem.file.renameTo(newFile)) {
                                activePanel?.refresh()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("重命名成功")
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("重命名失败")
                                }
                            }
                            showOperationDialog = null
                        },
                        onDismiss = { showOperationDialog = null }
                    )
                }
            }
        }

        // 处理复制/移动操作
        pendingOperation?.let { operation ->
            val targetPanel = when (activePanel) {
                leftPanelState -> rightPanelState
                rightPanelState -> leftPanelState
                else -> null
            }

            if (targetPanel != null) {
                OperationConfirmationDialog(
                    operation = operation,
                    targetDirectory = targetPanel.currentDirectory,
                    onConfirm = {
                        try {
                            performFileOperation(operation, targetPanel.currentDirectory)
                            leftPanelState.refresh()
                            rightPanelState.refresh()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("操作成功")
                            }
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("操作失败: ${e.message}")
                            }
                        }
                        pendingOperation = null
                    },
                    onDismiss = { pendingOperation = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilePanel(
    state: FilePanelState,
    modifier: Modifier = Modifier,
    isActive: Boolean,
    onActivate: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .padding(8.dp)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onActivate() },
        tonalElevation = if (isActive) 3.dp else 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 路径导航栏
            PathNavigationBar(
                state = state,
                isActive = isActive,
                modifier = Modifier.padding(16.dp)
            )

            HorizontalDivider()

            // 文件列表
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.fileItems, key = { it.path }) { fileItem ->
                        FileListItem(
                            fileItem = fileItem,
                            onClick = { onFileClick(fileItem) },
                            onLongClick = { onFileLongClick(fileItem) },
                            showMenu = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathNavigationBar(
    state: FilePanelState,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var showPathInput by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf(state.currentDirectory.absolutePath) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        if (showPathInput) {
            OutlinedTextField(
                value = customPath,
                onValueChange = { customPath = it },
                label = { Text("输入路径") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val newDir = File(customPath)
                        if (newDir.exists() && newDir.isDirectory) {
                            state.navigateTo(newDir)
                            showPathInput = false
                            focusManager.clearFocus()
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        val newDir = File(customPath)
                        if (newDir.exists() && newDir.isDirectory) {
                            state.navigateTo(newDir)
                            showPathInput = false
                            focusManager.clearFocus()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "跳转")
                    }
                }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 导航按钮
                IconButton(
                    onClick = { state.navigateUp() },
                    enabled = state.canNavigateUp
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "返回上级")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 路径显示
                Text(
                    text = state.currentDirectory.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )

                // 项目计数
                BadgedBox(
                    badge = {
                        Badge {
                            Text(state.fileItems.size.toString())
                        }
                    }
                ) {
                    IconButton(onClick = { showPathInput = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "路径导航")
                    }
                }
            }

            // 完整路径提示
            Text(
                text = state.currentDirectory.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListItem(
    fileItem: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件图标
            Icon(
                imageVector = if (fileItem.isDirectory) Icons.Default.Folder
                else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = if (fileItem.isDirectory) "文件夹" else "文件",
                modifier = Modifier.size(32.dp),
                tint = if (fileItem.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileItem.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row {
                    Text(
                        text = fileItem.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (fileItem.formattedSize.isNotEmpty()) {
                        Text(
                            text = " • ${fileItem.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 操作菜单
            if (showMenu) {
                Box {
                    IconButton(
                        onClick = { showDropdown = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("打开") },
                            onClick = {
                                showDropdown = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开")
                            }
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("AutoboxingStateCreation")
@Composable
fun CreateFileDialog(
    currentDirectory: String,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var selectedType by remember { mutableIntStateOf(0) } // 0: 文件, 1: 文件夹

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建新项") },
        text = {
            Column {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入文件或文件夹名称") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "位置: $currentDirectory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (fileName.isNotEmpty()) {
                            if (selectedType == 0) onCreateFile(fileName)
                            else onCreateFolder(fileName)
                        }
                    },
                    enabled = fileName.isNotEmpty()
                ) {
                    Text(if (selectedType == 0) "创建文件" else "创建文件夹")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOperationDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit,
    onCopy: (File) -> Unit,
    onMove: (File) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onOpen: (FileItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "文件操作",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn {
                item {
                    // 文件信息区域
                    FileInfoSection(fileItem)

                    Spacer(modifier = Modifier.height(24.dp))

                    // 操作按钮区域
                    OperationGridSection(
                        fileItem = fileItem,
                        onOpen = onOpen,
                        onCopy = onCopy,
                        onMove = onMove,
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}


@Composable
private fun FileInfoSection(fileItem: FileItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 文件图标和名称
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (fileItem.isDirectory) Icons.Default.Folder
                    else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (fileItem.isDirectory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (fileItem.isDirectory) "文件夹" else "文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 详细信息
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("位置", fileItem.file.parent ?: "未知")
                if (fileItem.formattedSize.isNotEmpty()) {
                    InfoRow("大小", fileItem.formattedSize)
                }
                InfoRow("修改时间", fileItem.formattedDate)
                if (fileItem.extension.isNotEmpty()) {
                    InfoRow("类型", fileItem.extension.uppercase())
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
            fontWeight = FontWeight.Medium
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OperationGridSection(
    fileItem: FileItem,
    onOpen: (FileItem) -> Unit,
    onCopy: (File) -> Unit,
    onMove: (File) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit
) {
    Text(
        text = "可用操作",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // 两行两列的网格布局
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 第一行
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OperationButton(
                label = "打开",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                description = if (fileItem.isDirectory) "打开文件夹" else "打开文件",
                onClick = { onOpen(fileItem) },
                modifier = Modifier.weight(1f),
                isPrimary = true
            )

            OperationButton(
                label = "复制",
                icon = Icons.Default.ContentCopy,
                description = "复制到剪贴板",
                onClick = { onCopy(fileItem.file) },
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OperationButton(
                label = "移动",
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                description = "移动到其他位置",
                onClick = { onMove(fileItem.file) },
                modifier = Modifier.weight(1f)
            )

            OperationButton(
                label = "重命名",
                icon = Icons.Default.Create,
                description = "重命名文件",
                onClick = { onRename(fileItem) },
                modifier = Modifier.weight(1f)
            )
        }

        // 第三行（单独一行，因为是危险操作）
        OperationButton(
            label = "删除",
            icon = Icons.Default.Delete,
            description = "永久删除文件",
            onClick = { onDelete(fileItem) },
            modifier = Modifier.fillMaxWidth(),
            isDestructive = true
        )
    }
}

@Composable
private fun OperationButton(
    label: String,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false
) {
    val buttonColors = when {
        isDestructive -> ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        isPrimary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        else -> ButtonDefaults.outlinedButtonColors()
    }

    val borderColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else if (isPrimary) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outline
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = buttonColors,
        border = BorderStroke(
            width = if (isPrimary) 0.dp else 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    fileItem: FileItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Text("确定要删除 \"${fileItem.name}\" 吗？此操作不可恢复。")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun RenameDialog(
    fileItem: FileItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(fileItem.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新名称") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotEmpty() && newName != fileItem.name
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun OperationConfirmationDialog(
    operation: FileOperation,
    targetDirectory: File,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val operationText = when (operation) {
        is FileOperation.Copy -> "复制"
        is FileOperation.Move -> "移动"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认$operationText") },
        text = {
            Text("确定要${operationText} \"${operation.source.name}\" 到 \"${targetDirectory.name}\" 吗？")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(operationText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 状态管理类
class FilePanelState(initialDirectory: File) {
    var currentDirectory by mutableStateOf(initialDirectory)
    var fileItems by mutableStateOf<List<FileItem>>(emptyList())
    var isLoading by mutableStateOf(false)

    val canNavigateUp: Boolean
        get() = currentDirectory.parentFile != null

    init {
        refresh()
    }

    fun refresh() {
        isLoading = true
        val files = currentDirectory.listFiles() ?: emptyArray()
        fileItems = files
            .map { FileItem.fromFile(it) }
            .sortedWith(compareBy(
                { !it.isDirectory },
                { it.name.lowercase() }
            ))
        isLoading = false
    }

    fun navigateTo(directory: File) {
        currentDirectory = directory
        refresh()
    }

    fun navigateUp() {
        currentDirectory.parentFile?.let {
            currentDirectory = it
            refresh()
        }
    }
}

// 文件操作相关类
sealed class FileOperation {
    abstract val source: File
    abstract val sourcePanel: FilePanelState?

    data class Copy(override val source: File, override val sourcePanel: FilePanelState?) : FileOperation()
    data class Move(override val source: File, override val sourcePanel: FilePanelState?) : FileOperation()
}

sealed class OperationDialogData {
    data class Delete(val fileItem: FileItem) : OperationDialogData()
    data class Rename(val fileItem: FileItem) : OperationDialogData()
}

// 工具函数
fun performFileOperation(operation: FileOperation, targetDirectory: File) {
    val targetFile = File(targetDirectory, operation.source.name)

    when (operation) {
        is FileOperation.Copy -> {
            operation.source.copyTo(targetFile, overwrite = true)
        }
        is FileOperation.Move -> {
            operation.source.renameTo(targetFile)
        }
    }
}

fun openFile(context: Context, file: File, snackbarHostState: SnackbarHostState, coroutineScope: CoroutineScope) {
    try {
        // 检查文件是否存在
        if (!file.exists()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("文件不存在: ${file.name}")
            }
            return
        }

        // 获取文件的 MIME 类型
        val mimeType = getMimeType(file) ?: "*/*"

        // 使用 FileProvider 创建 URI
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 创建打开文件的 Intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) // 可选，如果需要写入权限
        }

        // 验证是否有应用可以处理这个 Intent
        val packageManager = context.packageManager
        val resolveInfo = packageManager.resolveActivity(intent, 0)

        if (resolveInfo != null) {
            // 有应用可以处理，启动选择器
            val chooserIntent = Intent.createChooser(intent, "选择应用打开 ${file.name}")
            context.startActivity(chooserIntent)

            coroutineScope.launch {
                snackbarHostState.showSnackbar("正在打开: ${file.name}")
            }
        } else {
            // 没有应用可以处理此文件类型
            coroutineScope.launch {
                snackbarHostState.showSnackbar("没有应用可以打开此文件类型: ${file.extension}")
            }
        }

    } catch (e: Exception) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("打开文件失败: ${e.message}")
        }
    }
}

/**
 * 根据文件扩展名获取 MIME 类型
 */
private fun getMimeType(file: File): String? {
    val extension = file.extension.lowercase()

    return when (extension) {
        // 文档类型
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        "rtf" -> "application/rtf"

        // 图片类型
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"

        // 音频类型
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"

        // 视频类型
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "3gp" -> "video/3gpp"

        // 压缩文件
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"

        // 代码文件
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "json" -> "application/json"
        "js" -> "application/javascript"
        "css" -> "text/css"

        // APK 文件
        "apk" -> "application/vnd.android.package-archive"

        // 其他常见类型
        else -> "*/*"
    }
}