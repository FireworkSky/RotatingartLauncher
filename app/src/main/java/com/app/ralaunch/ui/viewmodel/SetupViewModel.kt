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
import com.app.ralaunch.locales.LocaleManager.strings
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
                description = ".NET 7/8/9/10 Runtime",
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
                overallStatus = strings.setupOverallProgressPreparing
            )
        }
        resetComponentsState()
    }

    private fun resetComponentsState() {
        val updatedComponents = _state.value.components.map { component ->
            component.copy(
                progress = 0,
                status = strings.setupOverallProgressPreparing,
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
                        extractionError = strings.setupExtractionFailedPrefix.format(e.message),
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
            updateComponentStatus(index, 0, strings.setupStartExtracting.format(component.name))

            try {
                // 实际解压组件
                extractComponentFromAssets(context, component, index)
                updateComponentStatus(index, 100, strings.setupExtractingSuccessful)
                markComponentAsInstalled(index)
            } catch (e: Exception) {
                updateComponentStatus(index, 0, strings.setupExtractionFailedPrefix.format(e.message))
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
        updateComponentStatus(componentIndex, 10, strings.setupCheckAssets)

        // 检查 assets 中是否存在文件
        val assetManager = context.assets
        val assetFiles = assetManager.list("") ?: emptyArray()
        if (component.fileName !in assetFiles) {
            throw IllegalStateException(strings.setupAssetFileMissingPrefix.format(component.fileName))
        }

        updateComponentStatus(componentIndex, 20, strings.setupCreateTargetDir)

        // 创建目标目录
        val outputDir = File(context.filesDir, "components")
        if (outputDir.exists()) {
            deleteDirectory(outputDir)
        }
        outputDir.mkdirs()

        updateComponentStatus(componentIndex, 30, strings.setupStartExtracting)

        // 实际解压 ZIP 文件（使用分块读取）
        extractZipFromAssetsWithChunkedReading(context, component.fileName, outputDir, componentIndex)

        updateComponentStatus(componentIndex, 95, strings.setupOverallProgressVerifying)

        // 验证解压结果
        if (!outputDir.exists() || outputDir.listFiles().isNullOrEmpty()) {
            throw IllegalStateException("The directory is empty or does not exist after extraction")
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

            updateComponentStatus(componentIndex, 40, strings.setupExtractingStructure)

            val buffer = ByteArray(BUFFER_SIZE)
            var entry: ZipEntry?

            while (zipInputStream.nextEntry.also { entry = it } != null) {
                entry?.let { zipEntry ->
                    val fileName = zipEntry.name

                    if (fileName.contains("..") || fileName.contains("/..") || fileName.contains("\\.."))
                        throw SecurityException(strings.setupInvalidFilePathPrefix.format(fileName))

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
                                    val status = "${strings.setupExtracting} - ${formatFileSize(entryBytesExtracted)}/${formatFileSize(zipEntry.size)}"
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
                            updateComponentStatus(componentIndex, finalProgress, strings.setupExtractingSuccessful)
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
                    val status = "${strings.setupExtracting} ($entriesProcessed/$totalEntries)"
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
            updateState { it.copy(overallProgress = 0, overallStatus = strings.setupOverallProgressPreparing) }
            return
        }

        val totalProgress = _state.value.components.sumOf { it.progress }
        val avgProgress = totalProgress / _state.value.components.size

        val status = when {
            avgProgress >= 100 -> strings.setupOverallProgressCompleted
            avgProgress >= 95 -> strings.setupOverallProgressVerifying
            avgProgress > 0 -> strings.setupOverallProgressInstalling
            else -> strings.setupOverallProgressPreparing
        }

        updateState { it.copy(overallProgress = avgProgress, overallStatus = status) }
    }

    private fun onInitializationComplete() {
        updateState {
            it.copy(
                overallProgress = 100,
                overallStatus = strings.setupOverallProgressCompleted,
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