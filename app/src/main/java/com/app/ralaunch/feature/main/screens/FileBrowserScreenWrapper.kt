package com.app.ralaunch.feature.main.screens

import android.os.Environment
import androidx.compose.runtime.*
import com.app.ralaunch.shared.feature.filebrowser.*
import java.io.File

/**
 * 文件浏览器 Screen Wrapper - App 层
 * 处理 Android 特定逻辑（文件系统访问、权限）
 */
@Composable
fun FileBrowserScreenWrapper(
    initialPath: String = "",
    fileType: String? = null,
    allowedExtensions: List<String> = emptyList(),
    title: String? = null,
    hasPermission: Boolean = true,
    onFileSelected: (String, String?) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit = {}
) {
    val fileBrowserTitle = title
    
    // 状态
    var currentPath by remember { mutableStateOf(initialPath) }
    var files by remember { mutableStateOf<List<FileItemData>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<FileItemData?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var isLoading by remember { mutableStateOf(false) }

    // 加载目录
    fun loadDirectory(path: String) {
        isLoading = true
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            isLoading = false
            return
        }

        currentPath = directory.absolutePath
        selectedFile = null
        
        val newFiles = mutableListOf<FileItemData>()

        // 添加上级目录
        directory.parentFile?.let { parent ->
            if (!isRootDirectory(directory)) {
                newFiles.add(FileItemData(
                    name = "..",
                    path = parent.absolutePath,
                    isDirectory = true,
                    isParent = true
                ))
            }
        }

        // 获取文件列表
        directory.listFiles()?.let { dirFiles ->
            // 文件夹
            dirFiles.filter { it.isDirectory && !it.isHidden }
                .forEach {
                    newFiles.add(FileItemData(
                        name = it.name,
                        path = it.absolutePath,
                        isDirectory = true,
                        size = 0,
                        lastModified = it.lastModified()
                    ))
                }

            // 文件
            dirFiles.filter { it.isFile && !it.isHidden && isFileAllowed(it, allowedExtensions) }
                .forEach {
                    newFiles.add(FileItemData(
                        name = it.name,
                        path = it.absolutePath,
                        isDirectory = false,
                        size = it.length(),
                        lastModified = it.lastModified()
                    ))
                }
        }

        files = sortFiles(newFiles, sortMode)
        isLoading = false
    }

    // 初始化
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val startPath = if (initialPath.isNotEmpty() && File(initialPath).exists()) {
                initialPath
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloads.exists()) downloads.absolutePath
                else Environment.getExternalStorageDirectory().absolutePath
            }
            loadDirectory(startPath)
        }
    }

    // 搜索过滤
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // 构建 UI 状态
    val uiState = FileBrowserUiState(
        currentPath = currentPath,
        files = filteredFiles,
        selectedFile = selectedFile,
        searchQuery = searchQuery,
        sortMode = sortMode,
        hasPermission = hasPermission,
        isLoading = isLoading
    )

    FileBrowserScreen(
        state = uiState,
        title = fileBrowserTitle,
        onSearchQueryChange = { searchQuery = it },
        onSortModeChange = { 
            sortMode = it
            files = sortFiles(files, it)
        },
        onFileClick = { file ->
            if (file.isDirectory) {
                loadDirectory(file.path)
            } else {
                selectedFile = if (selectedFile?.path == file.path) null else file
            }
        },
        onConfirm = {
            selectedFile?.let { file ->
                onFileSelected(file.path, fileType)
            }
        },
        onBack = onBack,
        onRequestPermission = onRequestPermission
    )
}

private fun isRootDirectory(directory: File): Boolean {
    val externalStorage = Environment.getExternalStorageDirectory()
    return directory.absolutePath == "/" || 
           directory.absolutePath == externalStorage.absolutePath
}

private fun isFileAllowed(file: File, allowedExtensions: List<String>): Boolean {
    if (allowedExtensions.isEmpty()) return true
    val fileName = file.name.lowercase()
    return allowedExtensions.any { fileName.endsWith(it.lowercase()) }
}

private fun sortFiles(files: List<FileItemData>, sortMode: SortMode): List<FileItemData> {
    val parentItem = files.firstOrNull { it.isParent }
    val directories = files.filter { it.isDirectory && !it.isParent }
    val regularFiles = files.filter { !it.isDirectory }

    val comparator: Comparator<FileItemData> = when (sortMode) {
        SortMode.SIZE -> compareByDescending { it.size }
        SortMode.TIME -> compareByDescending { it.lastModified }
        SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }

    val sortedDirs = directories.sortedWith(comparator)
    val sortedFiles = regularFiles.sortedWith(comparator)

    return buildList {
        parentItem?.let { add(it) }
        addAll(sortedDirs)
        addAll(sortedFiles)
    }
}
