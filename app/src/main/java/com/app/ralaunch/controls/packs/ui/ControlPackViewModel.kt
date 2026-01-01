package com.app.ralaunch.controls.packs.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.controls.packs.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 控件包商店 ViewModel
 */
class ControlPackViewModel(
    private val packManager: ControlPackManager,
    val repoService: ControlPackRepositoryService,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlPackUiState())
    val uiState: StateFlow<ControlPackUiState> = _uiState.asStateFlow()

    private var allPacks: List<ControlPackItem> = emptyList()

    init {
        loadPacks()
    }

    fun loadPacks(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // 获取远程仓库
                val repoResult = repoService.fetchRepository(forceRefresh)
                if (repoResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = repoResult.exceptionOrNull()?.message ?: context.getString(R.string.pack_load_failed)
                        )
                    }
                    return@launch
                }

                val repository = repoResult.getOrNull()!!

                // 获取已安装的包
                val installedPacks = withContext(Dispatchers.IO) {
                    packManager.getInstalledPacks()
                }

                // 合并数据
                val items = repository.packs.map { remotePack ->
                    val installedPack = installedPacks.find { it.id == remotePack.id }
                    val status = when {
                        installedPack == null -> ControlPackStatus.NOT_INSTALLED
                        remotePack.versionCode > installedPack.versionCode -> ControlPackStatus.UPDATE_AVAILABLE
                        else -> ControlPackStatus.INSTALLED
                    }
                    val iconPath = installedPack?.let { packManager.getPackIconPath(it.id) }

                    ControlPackItem(
                        info = remotePack,
                        status = status,
                        installedVersion = installedPack?.version,
                        localIconPath = iconPath
                    )
                }

                allPacks = items
                filterPacks(_uiState.value.searchQuery)
                
                // 更新分类列表
                val categories = repository.categories.sortedBy { it.order }
                _uiState.update { it.copy(isLoading = false, categories = categories) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(R.string.pack_load_failed)
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterPacks(query)
    }

    private fun filterPacks(query: String) {
        val filtered = if (query.isEmpty()) {
            allPacks
        } else {
            allPacks.filter {
                it.info.name.contains(query, ignoreCase = true) ||
                it.info.author.contains(query, ignoreCase = true) ||
                it.info.description.contains(query, ignoreCase = true) ||
                it.info.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
        _uiState.update { it.copy(packs = filtered) }
    }

    fun downloadPack(item: ControlPackItem) {
        updatePackStatus(item.info.id, ControlPackStatus.DOWNLOADING)

        viewModelScope.launch {
            try {
                val result = repoService.downloadAndInstall(
                    packInfo = item.info,
                    packManager = packManager,
                    listener = object : ControlPackRepositoryService.DownloadProgressListener {
                        override fun onProgress(downloaded: Long, total: Long, percent: Int) {
                            updatePackProgress(item.info.id, percent)
                        }

                        override fun onComplete(file: File) {
                            updatePackStatus(item.info.id, ControlPackStatus.INSTALLING)
                        }

                        override fun onError(error: String) {
                            updatePackStatus(item.info.id, ControlPackStatus.NOT_INSTALLED)
                        }
                    }
                )

                if (result.isSuccess) {
                    loadPacks() // 刷新列表
                } else {
                    // 下载或安装失败，重置状态
                    updatePackStatus(item.info.id, ControlPackStatus.NOT_INSTALLED)
                }
            } catch (e: Exception) {
                // 异常时重置状态
                updatePackStatus(item.info.id, ControlPackStatus.NOT_INSTALLED)
            }
        }
    }

    fun applyPack(item: ControlPackItem): Boolean {
        val layout = packManager.getPackLayout(item.info.id)
        if (layout != null) {
            packManager.setSelectedPackId(item.info.id)
            return true
        }
        return false
    }

    fun deletePack(item: ControlPackItem): Boolean {
        val success = packManager.deletePack(item.info.id)
        if (success) {
            loadPacks() // 刷新列表
        }
        return success
    }

    private fun updatePackStatus(packId: String, status: ControlPackStatus) {
        val currentPacks = _uiState.value.packs.toMutableList()
        val index = currentPacks.indexOfFirst { it.info.id == packId }
        if (index != -1) {
            currentPacks[index] = currentPacks[index].copy(status = status)
            _uiState.update { it.copy(packs = currentPacks) }
        }
        
        // 同步更新 allPacks
        val allIndex = allPacks.indexOfFirst { it.info.id == packId }
        if (allIndex != -1) {
            allPacks = allPacks.toMutableList().apply {
                this[allIndex] = this[allIndex].copy(status = status)
            }
        }
    }

    private fun updatePackProgress(packId: String, progress: Int) {
        val currentPacks = _uiState.value.packs.toMutableList()
        val index = currentPacks.indexOfFirst { it.info.id == packId }
        if (index != -1) {
            currentPacks[index] = currentPacks[index].copy(progress = progress)
            _uiState.update { it.copy(packs = currentPacks) }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val packManager = ControlPackManager(context)
            val repoService = ControlPackRepositoryService(context)
            return ControlPackViewModel(packManager, repoService, context.applicationContext) as T
        }
    }
}
