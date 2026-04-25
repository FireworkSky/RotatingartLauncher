package com.app.ralaunch.feature.gog.vm

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.gog.data.GogDownloader
import com.app.ralaunch.feature.gog.data.api.GogAuthClient
import com.app.ralaunch.feature.gog.data.api.GogWebsiteApi
import com.app.ralaunch.feature.gog.data.model.GogGameFile
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderRule
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderVersion
import com.app.ralaunch.feature.gog.model.GogGameUi
import com.app.ralaunch.feature.gog.model.GogUiState
import com.app.ralaunch.feature.gog.ui.components.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GogScreenUiState(
    val gogUiState: GogUiState = GogUiState(),
    val showDownloadDialog: Boolean = false,
    val currentGame: GogGameUi? = null,
    val gameFiles: List<GogGameFile> = emptyList(),
    val currentModLoaderRule: ModLoaderRule? = null,
    val selectedGameFile: GogGameFile? = null,
    val selectedModLoaderVersion: ModLoaderVersion? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val downloadedGamePath: String? = null,
    val downloadedModLoaderPath: String? = null
)

sealed class GogUiEffect {
    data class ShowToast(val message: String) : GogUiEffect()
    data class ShowError(val message: String) : GogUiEffect()
    data class OpenUrl(val url: String) : GogUiEffect()
    data class NavigateToImport(
        val gamePath: String?,
        val modLoaderPath: String?,
        val gameName: String?
    ) : GogUiEffect()
}

class GogViewModel(
    private val appContext: Context,
    private val authClient: GogAuthClient,
    private val websiteApi: GogWebsiteApi,
    private val downloader: GogDownloader,
    private val modLoaderConfigManager: ModLoaderConfigManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(GogScreenUiState())
    val uiState: StateFlow<GogScreenUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<GogUiEffect>()
    val effect: SharedFlow<GogUiEffect> = _effect.asSharedFlow()

    fun loadIfLoggedIn() {
        if (!authClient.isLoggedIn()) return
        _uiState.update { it.copy(gogUiState = it.gogUiState.copy(isLoggedIn = true)) }
        loadUserInfoAndGames()
    }

    fun onWebLogin(authCode: String) {
        updateGogState {
            it.copy(
                isLoading = true,
                loadingMessage = appContext.getString(R.string.gog_logging_in)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = authClient.exchangeCodeForToken(authCode)
                if (success) {
                    updateGogState { it.copy(isLoggedIn = true) }
                    _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_login_success)))
                    loadUserInfoAndGames()
                } else {
                    updateGogState { it.copy(isLoading = false) }
                    _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_login_failed)))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "WebView 登录异常", e)
                updateGogState { it.copy(isLoading = false) }
                _effect.emit(
                    GogUiEffect.ShowError(
                        appContext.getString(R.string.gog_login_error, e.message ?: "")
                    )
                )
            }
        }
    }

    fun onLogout() {
        authClient.logout()
        downloader.cancel()
        _uiState.value = GogScreenUiState()
        viewModelScope.launch {
            _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_logged_out)))
        }
    }

    fun onVisitGog() {
        viewModelScope.launch {
            _effect.emit(GogUiEffect.OpenUrl("https://www.gog.com"))
        }
    }

    fun onSearchQueryChange(query: String) {
        updateGogState { state ->
            val filtered = if (query.isBlank()) {
                state.games
            } else {
                state.games.filter { it.title.contains(query, ignoreCase = true) }
            }
            state.copy(
                searchQuery = query,
                filteredGames = filtered
            )
        }
    }

    fun onGameClick(game: GogGameUi) {
        val rule = modLoaderConfigManager.getRule(game.id)
        updateGogState {
            it.copy(
                isLoading = true,
                loadingMessage = appContext.getString(R.string.gog_loading_version_info, game.title)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val detailsDeferred = async { websiteApi.getGameDetails(game.id.toString()) }
                val modLoaderDeferred = async {
                    if (rule != null) {
                        modLoaderConfigManager.getVersions(rule, forceRefresh = true)
                    }
                    rule
                }

                val details = detailsDeferred.await()
                val updatedRule = modLoaderDeferred.await()
                val linuxInstallers = details.installers.filter { it.os.equals("linux", ignoreCase = true) }

                updateGogState { it.copy(isLoading = false) }
                if (linuxInstallers.isEmpty()) {
                    _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_no_linux_version)))
                    return@launch
                }

                _uiState.update { state ->
                    state.copy(
                        showDownloadDialog = true,
                        currentGame = game,
                        gameFiles = linuxInstallers,
                        currentModLoaderRule = updatedRule,
                        selectedGameFile = null,
                        selectedModLoaderVersion = updatedRule?.versions?.firstOrNull { it.stable }
                            ?: updatedRule?.versions?.firstOrNull(),
                        downloadStatus = DownloadStatus.Idle,
                        downloadedGamePath = null,
                        downloadedModLoaderPath = null
                    )
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "获取游戏详情失败", e)
                updateGogState { it.copy(isLoading = false) }
                _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_get_details_failed)))
            }
        }
    }

    fun onSelectGameVersion(gameFile: GogGameFile) {
        _uiState.update { it.copy(selectedGameFile = gameFile) }
    }

    fun onSelectModLoaderVersion(version: ModLoaderVersion) {
        _uiState.update { it.copy(selectedModLoaderVersion = version) }
    }

    fun onStartDownload() {
        val selectedGameFile = _uiState.value.selectedGameFile
        if (selectedGameFile == null) {
            viewModelScope.launch {
                _effect.emit(
                    GogUiEffect.ShowToast(
                        appContext.getString(R.string.gog_select_game_version_prompt)
                    )
                )
            }
            return
        }

        val selectedModLoaderVersion = _uiState.value.selectedModLoaderVersion
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "GOG"
        )
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        viewModelScope.launch(Dispatchers.IO) {
            var downloadedGamePath: String? = null
            var downloadedModLoaderPath: String? = null

            try {
                val gameFileName = selectedGameFile.getFileName()
                val gameTargetFile = File(downloadDir, gameFileName)

                _uiState.update {
                    it.copy(
                        downloadStatus = DownloadStatus.Downloading(
                            fileName = gameFileName,
                            progress = 0f,
                            downloaded = 0,
                            total = selectedGameFile.size,
                            speed = 0
                        )
                    )
                }

                downloader.downloadGameFile(
                    gameFile = selectedGameFile,
                    targetDir = downloadDir,
                    progress = GogDownloader.DownloadProgress { downloaded, total, speed ->
                        _uiState.update {
                            it.copy(
                                downloadStatus = DownloadStatus.Downloading(
                                    fileName = gameFileName,
                                    progress = if (total > 0) downloaded.toFloat() / total else 0f,
                                    downloaded = downloaded,
                                    total = total,
                                    speed = speed
                                )
                            )
                        }
                    }
                )

                downloadedGamePath = gameTargetFile.absolutePath
                AppLog.i(TAG, "游戏下载完成: $downloadedGamePath")

                if (selectedModLoaderVersion != null && selectedModLoaderVersion.url.isNotEmpty()) {
                    val modLoaderFileName = selectedModLoaderVersion.fileName
                    val modLoaderTargetFile = File(downloadDir, modLoaderFileName)

                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.Downloading(
                                fileName = modLoaderFileName,
                                progress = 0f,
                                downloaded = 0,
                                total = 0,
                                speed = 0
                            )
                        )
                    }

                    downloadFromUrl(
                        url = selectedModLoaderVersion.url,
                        targetFile = modLoaderTargetFile
                    ) { downloaded, total, speed ->
                        _uiState.update {
                            it.copy(
                                downloadStatus = DownloadStatus.Downloading(
                                    fileName = modLoaderFileName,
                                    progress = if (total > 0) downloaded.toFloat() / total else 0f,
                                    downloaded = downloaded,
                                    total = total,
                                    speed = speed
                                )
                            )
                        }
                    }

                    downloadedModLoaderPath = modLoaderTargetFile.absolutePath
                    AppLog.i(TAG, "ModLoader 下载完成: $downloadedModLoaderPath")
                }

                _uiState.update {
                    it.copy(
                        downloadedGamePath = downloadedGamePath,
                        downloadedModLoaderPath = downloadedModLoaderPath,
                        downloadStatus = DownloadStatus.Completed(downloadedGamePath, downloadedModLoaderPath)
                    )
                }
                _effect.emit(GogUiEffect.ShowToast(appContext.getString(R.string.gog_download_complete)))
            } catch (e: Exception) {
                AppLog.e(TAG, "下载失败", e)
                val errorMsg = if (e.message?.contains("cancelled", ignoreCase = true) == true) {
                    appContext.getString(R.string.gog_download_cancelled)
                } else {
                    e.message ?: appContext.getString(R.string.common_unknown_error)
                }
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Failed(errorMsg)) }
            }
        }
    }

    fun onInstall() {
        val state = _uiState.value
        _uiState.update { it.copy(showDownloadDialog = false) }
        viewModelScope.launch {
            _effect.emit(
                GogUiEffect.NavigateToImport(
                    gamePath = state.downloadedGamePath,
                    modLoaderPath = state.downloadedModLoaderPath,
                    gameName = state.currentGame?.title
                )
            )
        }
    }

    fun dismissDownloadDialog() {
        if (_uiState.value.downloadStatus is DownloadStatus.Downloading) {
            downloader.cancel()
        }
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    private fun loadUserInfoAndGames() {
        updateGogState {
            it.copy(
                isLoading = true,
                loadingMessage = appContext.getString(R.string.gog_loading_user_info)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userInfo = websiteApi.getUserInfo()
                if (userInfo != null) {
                    updateGogState {
                        it.copy(
                            username = userInfo.username,
                            email = userInfo.email,
                            avatarUrl = userInfo.avatarUrl
                        )
                    }
                }

                updateGogState {
                    it.copy(loadingMessage = appContext.getString(R.string.gog_loading_games))
                }

                val games = websiteApi.getOwnedGames()
                val gameUiList = games.map { game ->
                    GogGameUi(
                        id = game.id,
                        title = game.title,
                        imageUrl = game.imageUrl
                    )
                }

                updateGogState {
                    it.copy(
                        isLoading = false,
                        games = gameUiList,
                        filteredGames = gameUiList
                    )
                }

                _effect.emit(
                    GogUiEffect.ShowToast(
                        if (games.isEmpty()) {
                            appContext.getString(R.string.gog_library_empty)
                        } else {
                            appContext.getString(R.string.gog_games_count, games.size)
                        }
                    )
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "加载数据失败", e)
                updateGogState { it.copy(isLoading = false) }
                _effect.emit(
                    GogUiEffect.ShowError(
                        appContext.getString(R.string.gog_load_games_failed, e.message ?: "")
                    )
                )
            }
        }
    }

    private fun updateGogState(transform: (GogUiState) -> GogUiState) {
        _uiState.update { state -> state.copy(gogUiState = transform(state.gogUiState)) }
    }

    private fun downloadFromUrl(
        url: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit
    ) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            val code = conn.responseCode
            if (code >= 400) {
                throw java.io.IOException(appContext.getString(R.string.gog_error_download_failed, code))
            }

            val total = conn.contentLengthLong
            var lastTime = System.currentTimeMillis()
            var lastDownloaded = 0L

            conn.inputStream.use { input ->
                java.io.FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var len: Int

                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        downloaded += len

                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastTime
                        if (timeDiff >= 1000) {
                            val bytesPerSecond = ((downloaded - lastDownloaded) * 1000) / timeDiff
                            onProgress(downloaded, total, bytesPerSecond)
                            lastTime = currentTime
                            lastDownloaded = downloaded
                        }
                    }

                    onProgress(downloaded, total, 0)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "GogViewModel"
    }
}
