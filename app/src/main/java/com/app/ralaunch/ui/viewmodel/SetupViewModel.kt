package com.app.ralaunch.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.ui.model.ComponentItem
import com.app.ralaunch.ui.model.SetupScreen
import com.app.ralaunch.ui.model.SetupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SetupViewModel : ViewModel() {

    private companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_LEGAL_AGREED = "legal_agreed"
        const val KEY_COMPONENTS_EXTRACTED = "components_extracted"
        const val BUFFER_SIZE = 8192 // 8KB 缓冲区
        const val PROGRESS_UPDATE_THRESHOLD = 1024 * 1024 // 每1MB更新一次进度
    }

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        loadComponents()
    }

    private fun loadComponents() {
        val components = listOf(
            ComponentItem(
                name = ".NET Runtime",
                description = ".NET 7/8/9/10 运行时环境",
                fileName = "dotnet.zip"
            )
        )
        updateState { it.copy(components = components) }
    }

    fun checkInitializationStatus(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legalAgreed = prefs.getBoolean(KEY_LEGAL_AGREED, false)
        val componentsExtracted = prefs.getBoolean(KEY_COMPONENTS_EXTRACTED, false)

        viewModelScope.launch {
            if (componentsExtracted) {
                onInitializationComplete()
            } else if (legalAgreed) {
                showExtractionScreen()
            } else {
                showLegalScreen()
            }
        }
    }

    fun acceptLegalAgreement(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_LEGAL_AGREED, true) }

        viewModelScope.launch {
            showExtractionScreen()
        }
    }

    private fun showLegalScreen() {
        updateState { it.copy(currentScreen = SetupScreen.Legal) }
    }

    private fun showExtractionScreen() {
        updateState {
            it.copy(
                currentScreen = SetupScreen.Extraction,
                overallProgress = 0,
                overallStatus = "准备安装..."
            )
        }
        resetComponentsState()
    }

    private fun resetComponentsState() {
        val updatedComponents = _state.value.components.map { component ->
            component.copy(
                progress = 0,
                status = "等待安装",
                isInstalled = false
            )
        }
        updateState { it.copy(components = updatedComponents) }
    }

    fun startExtraction(context: Context) {
        if (_state.value.isExtracting) return

        updateState { it.copy(isExtracting = true, extractionError = null) }

        viewModelScope.launch {
            try {
                // 实际解压逻辑
                extractAllComponents(context)

                // 保存完成状态
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit { putBoolean(KEY_COMPONENTS_EXTRACTED, true) }

                onInitializationComplete()
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        extractionError = "解压失败: ${e.message}",
                        isExtracting = false
                    )
                }
            }
        }
    }

    /**
     * 实际解压所有组件
     */
    private suspend fun extractAllComponents(context: Context) {
        for ((index, component) in _state.value.components.withIndex()) {
            updateComponentStatus(index, 0, "开始解压 ${component.name}...")

            try {
                // 实际解压组件
                extractComponentFromAssets(context, component, index)
                updateComponentStatus(index, 100, "解压完成")
                markComponentAsInstalled(index)
            } catch (e: Exception) {
                updateComponentStatus(index, 0, "解压失败: ${e.message}")
                throw e // 重新抛出异常，让外层捕获
            }
        }
    }

    /**
     * 从 assets 解压单个组件
     */
    private suspend fun extractComponentFromAssets(
        context: Context,
        component: ComponentItem,
        componentIndex: Int
    ) {
        updateComponentStatus(componentIndex, 10, "准备资源文件...")

        // 检查 assets 中是否存在文件
        val assetManager = context.assets
        val assetFiles = assetManager.list("") ?: emptyArray()
        if (component.fileName !in assetFiles) {
            throw IllegalStateException("资源文件 ${component.fileName} 不存在")
        }

        updateComponentStatus(componentIndex, 20, "创建目标目录...")

        // 创建目标目录
        val outputDir = File(context.filesDir, "components")
        if (outputDir.exists()) {
            deleteDirectory(outputDir)
        }
        outputDir.mkdirs()

        updateComponentStatus(componentIndex, 30, "开始解压文件...")

        // 实际解压 ZIP 文件（使用分块读取）
        extractZipFromAssetsWithChunkedReading(context, component.fileName, outputDir, componentIndex)

        updateComponentStatus(componentIndex, 95, "验证文件完整性...")

        // 验证解压结果
        if (!outputDir.exists() || outputDir.listFiles().isNullOrEmpty()) {
            throw IllegalStateException("解压后目录为空或不存在")
        }
    }

    /**
     * 使用分块读取从 assets 解压 ZIP 文件
     */
    private suspend fun extractZipFromAssetsWithChunkedReading(
        context: Context,
        assetFileName: String,
        outputDir: File,
        componentIndex: Int
    ) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var zipInputStream: ZipInputStream? = null

        try {
            // 获取 assets 中的文件流
            inputStream = context.assets.open(assetFileName)
            zipInputStream = ZipInputStream(inputStream)

            // 先获取ZIP文件总大小和条目信息
            val zipInfo = getZipFileInfo(context, assetFileName)
            val totalEntries = zipInfo.entryCount
            val totalSize = zipInfo.totalSize

            var entriesProcessed = 0
            var totalBytesExtracted: Long = 0

            updateComponentStatus(componentIndex, 40, "解压文件结构...")

            val buffer = ByteArray(BUFFER_SIZE)
            var entry: ZipEntry?

            while (zipInputStream.nextEntry.also { entry = it } != null) {
                entry?.let { zipEntry ->
                    val fileName = zipEntry.name

                    // 安全检查：防止路径遍历攻击
                    if (fileName.contains("..") || fileName.contains("/..") || fileName.contains("\\..")) {
                        throw SecurityException("无效的文件路径: $fileName")
                    }

                    val outputFile = File(outputDir, fileName)

                    if (zipEntry.isDirectory) {
                        // 创建目录
                        outputFile.mkdirs()
                    } else {
                        // 创建父目录
                        outputFile.parentFile?.mkdirs()

                        // 使用分块读取解压文件
                        FileOutputStream(outputFile).use { outputStream ->
                            var bytesRead: Int
                            var entryBytesExtracted: Long = 0
                            var lastProgressUpdateSize: Long = 0

                            while (zipInputStream.read(buffer).also { bytesRead = it } > 0) {
                                outputStream.write(buffer, 0, bytesRead)
                                entryBytesExtracted += bytesRead
                                totalBytesExtracted += bytesRead

                                // 定期更新进度（避免更新太频繁）
                                if (entryBytesExtracted - lastProgressUpdateSize >= PROGRESS_UPDATE_THRESHOLD) {
                                    val progress = calculateProgress(
                                        entriesProcessed,
                                        totalEntries,
                                        totalBytesExtracted,
                                        totalSize,
                                        entryBytesExtracted,
                                        zipEntry.size
                                    )
                                    val status = "解压中: ${formatFileSize(entryBytesExtracted)}/${formatFileSize(zipEntry.size)}"
                                    updateComponentStatus(componentIndex, progress, status)
                                    lastProgressUpdateSize = entryBytesExtracted
                                }
                            }

                            // 确保文件解压完成时更新进度
                            val finalProgress = calculateProgress(
                                entriesProcessed,
                                totalEntries,
                                totalBytesExtracted,
                                totalSize,
                                entryBytesExtracted,
                                zipEntry.size
                            )
                            updateComponentStatus(componentIndex, finalProgress, "文件解压完成")
                        }
                    }

                    zipInputStream.closeEntry()
                    entriesProcessed++

                    // 更新条目完成进度
                    val progress = calculateProgress(
                        entriesProcessed,
                        totalEntries,
                        totalBytesExtracted,
                        totalSize,
                        0L,
                        0L
                    )
                    val status = "解压中 ($entriesProcessed/$totalEntries)"
                    updateComponentStatus(componentIndex, progress, status)
                }
            }
        } finally {
            // 确保流被关闭
            zipInputStream?.close()
            inputStream?.close()
        }
    }

    /**
     * 获取ZIP文件信息（条目数和总大小）
     */
    private suspend fun getZipFileInfo(context: Context, assetFileName: String): ZipFileInfo =
        withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var zipInputStream: ZipInputStream? = null

            try {
                inputStream = context.assets.open(assetFileName)
                zipInputStream = ZipInputStream(inputStream)

                var entryCount = 0
                var totalSize: Long = 0

                var entry: ZipEntry?
                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    entry?.let { zipEntry ->
                        if (!zipEntry.isDirectory) {
                            totalSize += zipEntry.size
                        }
                        entryCount++
                        zipInputStream.closeEntry()
                    }
                }

                return@withContext ZipFileInfo(entryCount, totalSize)
            } finally {
                zipInputStream?.close()
                inputStream?.close()
            }
        }

    /**
     * 计算解压进度（修正版）
     */
    private fun calculateProgress(
        entriesProcessed: Int,
        totalEntries: Int,
        totalBytesExtracted: Long,
        totalZipSize: Long,
        currentEntryBytesExtracted: Long,
        currentEntrySize: Long
    ): Int {
        if (totalEntries == 0 || totalZipSize == 0L) return 40

        // 基于条目数的进度（权重30%）：从40%到70%
        val entriesProgress = (entriesProcessed * 30) / totalEntries

        // 基于字节数的进度（权重60%）：从40%到100%
        val bytesProgress = if (totalZipSize > 0) {
            ((totalBytesExtracted * 60) / totalZipSize).toInt()
        } else 0

        // 当前文件的进度（权重10%）
        val currentFileProgress = if (currentEntrySize > 0) {
            ((currentEntryBytesExtracted * 10) / currentEntrySize).toInt()
        } else 0

        // 总进度 = 基础40% + 条目进度 + 字节进度 + 当前文件进度
        var progress = 40 + entriesProgress + bytesProgress + currentFileProgress

        // 确保进度不会超过95%（留5%给最后的验证）
        progress = progress.coerceAtMost(95)

        return progress
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    /**
     * 删除目录及其所有内容
     */
    private fun deleteDirectory(directory: File) {
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        }
    }

    private fun updateComponentStatus(index: Int, progress: Int, status: String) {
        val updatedComponents = _state.value.components.toMutableList()
        if (index < updatedComponents.size) {
            updatedComponents[index] = updatedComponents[index].copy(
                progress = progress.coerceIn(0, 100),
                status = status
            )
            updateState { it.copy(components = updatedComponents) }
            updateOverallProgress()
        }
    }

    private fun markComponentAsInstalled(index: Int) {
        val updatedComponents = _state.value.components.toMutableList()
        if (index < updatedComponents.size) {
            updatedComponents[index] = updatedComponents[index].copy(
                isInstalled = true
            )
            updateState { it.copy(components = updatedComponents) }
        }
    }

    private fun updateOverallProgress() {
        if (_state.value.components.isEmpty()) {
            updateState { it.copy(overallProgress = 0, overallStatus = "准备安装...") }
            return
        }

        val totalProgress = _state.value.components.sumOf { it.progress }
        val avgProgress = totalProgress / _state.value.components.size

        val status = when {
            avgProgress >= 100 -> "安装完成"
            avgProgress >= 95 -> "验证文件..."
            avgProgress > 0 -> "安装中..."
            else -> "准备安装..."
        }

        updateState { it.copy(overallProgress = avgProgress, overallStatus = status) }
    }

    private fun onInitializationComplete() {
        updateState {
            it.copy(
                overallProgress = 100,
                overallStatus = "安装完成",
                isExtracting = false
            )
        }
    }

    fun retryExtraction(context: Context) {
        updateState { it.copy(extractionError = null) }
        startExtraction(context)
    }

    private fun updateState(update: (SetupState) -> SetupState) {
        _state.value = update(_state.value)
    }

    /**
     * ZIP文件信息数据类
     */
    private data class ZipFileInfo(val entryCount: Int, val totalSize: Long)
}