package com.app.ralaunch.feature.main

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.ralaunch.R
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.PermissionManager
import com.app.ralaunch.core.common.ThemeManager
import com.app.ralaunch.core.common.MessageHelper
import com.app.ralaunch.core.platform.android.provider.RaLaunchFileProvider
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.navigation.*
import com.app.ralaunch.shared.core.theme.AppThemeState
import com.app.ralaunch.shared.core.theme.RaLaunchTheme
import com.app.ralaunch.core.ui.base.BaseActivity
import com.app.ralaunch.feature.main.background.AppBackground
import com.app.ralaunch.feature.main.background.BackgroundType
import com.app.ralaunch.shared.core.model.ui.GameItemUi
import com.app.ralaunch.feature.main.contracts.ImportUiState
import com.app.ralaunch.feature.main.contracts.AppUpdateUiModel
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
import com.app.ralaunch.feature.main.screens.ControlLayoutScreenWrapper
import com.app.ralaunch.feature.main.screens.ControlStoreScreenWrapper
import com.app.ralaunch.feature.main.screens.DownloadScreenWrapper
import com.app.ralaunch.feature.main.screens.FileBrowserScreenWrapper
import com.app.ralaunch.feature.main.screens.ImportScreenWrapper
import com.app.ralaunch.feature.main.screens.AnnouncementScreenWrapper
import com.app.ralaunch.feature.main.screens.SettingsScreenWrapper
import com.app.ralaunch.feature.main.screens.buildRendererOptions
import com.app.ralaunch.feature.main.MainViewModel
import com.app.ralaunch.feature.main.MainViewModelFactory
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.ErrorHandler
import com.app.ralaunch.feature.main.SplashOverlay
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MainActivityCompose : BaseActivity() {

    // Managers
    private lateinit var themeManager: ThemeManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var mainViewModel: MainViewModel

    private val navState = NavState()
    private var activeUpdateDownloadId: Long = -1L
    private var updateDownloadPollingJob: Job? = null
    private var latestUpdateDownloadUrl: String? = null
    private var latestUpdateFallbackUrl: String? = null
    private var updateDownloadUiState by mutableStateOf<UpdateDownloadUiState?>(null)
    private var pendingInstallApkUri: Uri? = null
    private var waitingUnknownSourcePermission: Boolean = false

    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L || downloadId != activeUpdateDownloadId) return
            handleUpdateDownloadFinished(downloadId)
        }
    }

    // ==================== Lifecycle ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启用 Edge-to-Edge 沉浸式，让视频背景覆盖系统导航栏区域
        enableEdgeToEdge()
        
        DensityAdapter.adapt(this, true)

        themeManager = ThemeManager(this)
        themeManager.applyThemeFromSettings()

        super.onCreate(savedInstanceState)

        // 初始化全局主题状态（从 SettingsAccess 加载）
        initializeThemeState()

        initLogger()
        ErrorHandler.init(this)
        permissionManager = PermissionManager(this).apply { initialize() }
        mainViewModel = ViewModelProvider(this, MainViewModelFactory(this))[MainViewModel::class.java]
        registerUpdateDownloadReceiver()

        // 设置纯 Compose UI
        setContent {
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()
            val importState by mainViewModel.importUiState.collectAsStateWithLifecycle()

            // 监听 AppThemeState 实现实时更新 (使用 collectAsState 确保实时响应)
            val themeMode by AppThemeState.themeMode.collectAsState()
            val themeColor by AppThemeState.themeColor.collectAsState()
            val bgType by AppThemeState.backgroundType.collectAsState()
            val bgOpacity by AppThemeState.backgroundOpacity.collectAsState()
            val videoSpeed by AppThemeState.videoPlaybackSpeed.collectAsState()
            val bgImagePath by AppThemeState.backgroundImagePath.collectAsState()
            val bgVideoPath by AppThemeState.backgroundVideoPath.collectAsState()
            
            // 根据 AppThemeState 计算背景类型
            val backgroundType = remember(bgType, bgImagePath, bgVideoPath) {
                when (bgType) {
                    1 -> if (bgImagePath.isNotEmpty()) BackgroundType.Image(bgImagePath) else BackgroundType.None
                    2 -> if (bgVideoPath.isNotEmpty()) BackgroundType.Video(bgVideoPath) else BackgroundType.None
                    else -> BackgroundType.None
                }
            }
            
            // 计算页面透明度
            val pageAlpha = remember(bgOpacity) {
                if (bgOpacity > 0) bgOpacity / 100f else 1f
            }

            // Splash 覆盖层状态
            var showSplash by remember { mutableStateOf(true) }
            val isContentReady = !state.isLoading

            LaunchedEffect(Unit) {
                mainViewModel.effects.collect { effect ->
                    when (effect) {
                        is MainUiEffect.ShowToast -> MessageHelper.showToast(this@MainActivityCompose, effect.message)
                        is MainUiEffect.ShowSuccess -> MessageHelper.showSuccess(this@MainActivityCompose, effect.message)
                        is MainUiEffect.DownloadLauncherUpdate -> {
                            downloadLauncherUpdate(
                                downloadUrl = effect.downloadUrl,
                                latestVersion = effect.latestVersion,
                                fallbackUrl = effect.releaseUrl
                            )
                        }
                        is MainUiEffect.OpenUrl -> openExternalUrl(effect.url)
                        is MainUiEffect.ExitLauncher -> finishAffinity()
                    }
                }
            }

            RaLaunchTheme(
                themeMode = themeMode,
                themeColor = themeColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 主内容（始终渲染，Splash 覆盖在上方）
                    MainActivityContent(
                        state = state.copy(backgroundType = backgroundType),
                        importUiState = importState,
                        navState = navState,
                        pageAlpha = pageAlpha,
                        videoSpeed = videoSpeed,
                        onGameClick = { mainViewModel.onEvent(MainUiEvent.GameSelected(it)) },
                        onGameLongClick = { mainViewModel.onEvent(MainUiEvent.GameSelected(it)) },
                        onLaunchClick = { mainViewModel.onEvent(MainUiEvent.LaunchRequested) },
                        onDeleteClick = { mainViewModel.onEvent(MainUiEvent.DeleteRequested) },
                        onEditClick = { updatedGameUi -> mainViewModel.onEvent(MainUiEvent.GameEdited(updatedGameUi)) },
                        onDismissDeleteDialog = { mainViewModel.onEvent(MainUiEvent.DeleteDialogDismissed) },
                        onConfirmDelete = { mainViewModel.onEvent(MainUiEvent.DeleteConfirmed) },
                        availableUpdate = state.availableUpdate,
                        updateDownloadState = updateDownloadUiState,
                        onDismissUpdateDialog = { mainViewModel.onEvent(MainUiEvent.UpdateDialogDismissed) },
                        onIgnoreUpdateClick = { mainViewModel.onEvent(MainUiEvent.UpdateIgnoreClicked) },
                        onUpdateActionClick = { mainViewModel.onEvent(MainUiEvent.UpdateActionClicked) },
                        onCheckLauncherUpdateClick = {
                            mainViewModel.onEvent(MainUiEvent.CheckAppUpdateManually)
                        },
                        onDismissUpdateDownloadDialog = { updateDownloadUiState = null },
                        onInstallDownloadedUpdate = { installDownloadedUpdateFromDialog() },
                        onRetryUpdateDownload = { retryUpdateDownload() },
                        permissionManager = permissionManager,
                        onStartImport = { gameFilePath, modLoaderFilePath ->
                            mainViewModel.startImport(gameFilePath, modLoaderFilePath)
                        },
                        onDismissImportError = { mainViewModel.clearImportError() },
                        onImportCompletionHandled = { mainViewModel.resetImportCompletedFlag() }
                    )

                    // MD3 风格启动画面覆盖层
                    if (showSplash) {
                        SplashOverlay(
                            isReady = isContentReady,
                            onSplashFinished = { showSplash = false }
                        )
                    }
                }
            }
        }

        checkRestoreSettings()
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Exception) {
            AppLogger.error("MainActivityCompose", "onResume error: ${e.message}")
        }

        ErrorHandler.setCurrentActivity(this)
        resumePendingInstallIfPossible()

        // 恢复视频播放
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                mainViewModel.onEvent(MainUiEvent.AppResumed)
            }
        }, 200)
    }

    override fun onPause() {
        mainViewModel.onEvent(MainUiEvent.AppPaused)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(updateDownloadReceiver) }
        updateDownloadPollingJob?.cancel()
        updateDownloadPollingJob = null
        super.onDestroy()
        if (!isChangingConfigurations) AppLogger.close()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 先检查 NavState 是否可以处理返回
        if (navState.handleBackPress()) {
            return
        }
        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        themeManager.handleConfigurationChanged(newConfig)
    }

    // ==================== Init ====================

    private fun initLogger() {
        try {
            AppLogger.init(File(getExternalFilesDir(null), "logs"))
        } catch (e: Exception) {
            Log.e("MainActivityCompose", "Failed to initialize logger", e)
        }
    }

    private fun checkRestoreSettings() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("restore_settings_after_recreate", false)) {
            prefs.edit().putBoolean("restore_settings_after_recreate", false).apply()
            navState.navigateToSettings()
        }
    }
    
    /**
     * 初始化全局主题状态（从 SettingsAccess 加载）
     */
    private fun initializeThemeState() {
        val settings = SettingsAccess
        val bgType = when (settings.backgroundType?.lowercase()) {
            "image" -> 1
            "video" -> 2
            else -> 0
        }
        
        AppThemeState.initializeState(
            themeMode = settings.themeMode,
            themeColor = settings.themeColor,
            backgroundType = bgType,
            backgroundImagePath = settings.backgroundImagePath ?: "",
            backgroundVideoPath = settings.backgroundVideoPath ?: "",
            backgroundOpacity = settings.backgroundOpacity,
            videoPlaybackSpeed = settings.videoPlaybackSpeed
        )
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            MessageHelper.showToast(this, "无法打开链接")
        }
    }

    private fun registerUpdateDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateDownloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateDownloadReceiver, filter)
        }
    }

    private fun downloadLauncherUpdate(
        downloadUrl: String,
        latestVersion: String,
        fallbackUrl: String
    ) {
        val downloadManager = getSystemService(DownloadManager::class.java)
        if (downloadManager == null) {
            MessageHelper.showToast(this, "系统下载服务不可用")
            if (fallbackUrl.isNotBlank()) openExternalUrl(fallbackUrl)
            return
        }

        val fileName = "RotatingartLauncher-${latestVersion.trim().removePrefix("v")}.apk"
        val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (targetDir == null) {
            MessageHelper.showToast(this, "无法访问下载目录")
            if (fallbackUrl.isNotBlank()) openExternalUrl(fallbackUrl)
            return
        }
        val targetFile = File(targetDir, fileName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            val existingApkUri = runCatching {
                FileProvider.getUriForFile(
                    this,
                    RaLaunchFileProvider.AUTHORITY,
                    targetFile
                )
            }.getOrNull()
            if (existingApkUri != null) {
                updateDownloadUiState = UpdateDownloadUiState(
                    version = latestVersion,
                    status = UpdateDownloadStatus.COMPLETED,
                    progress = 100,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    downloadedApkUri = existingApkUri
                )
                MessageHelper.showInfo(this, "检测到已下载完成的更新包")
                return
            }
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("启动器更新")
            setDescription("正在下载版本 $latestVersion")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverRoaming(true)
            setAllowedOverMetered(true)
            setDestinationInExternalFilesDir(
                this@MainActivityCompose,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }

        runCatching {
            latestUpdateDownloadUrl = downloadUrl
            latestUpdateFallbackUrl = fallbackUrl
            activeUpdateDownloadId = downloadManager.enqueue(request)
            updateDownloadUiState = UpdateDownloadUiState(
                version = latestVersion,
                status = UpdateDownloadStatus.STARTING
            )
            startUpdateDownloadProgressPolling(activeUpdateDownloadId)
            MessageHelper.showToast(this, "已开始在启动器内下载更新")
        }.onFailure { error ->
            updateDownloadUiState = UpdateDownloadUiState(
                version = latestVersion,
                status = UpdateDownloadStatus.FAILED,
                errorMessage = "下载启动失败: ${error.message ?: "未知错误"}"
            )
            MessageHelper.showToast(this, "下载启动失败: ${error.message ?: "未知错误"}")
            if (fallbackUrl.isNotBlank()) {
                openExternalUrl(fallbackUrl)
            }
        }
    }

    private fun handleUpdateDownloadFinished(downloadId: Long) {
        updateDownloadPollingJob?.cancel()
        updateDownloadPollingJob = null
        val downloadManager = getSystemService(DownloadManager::class.java) ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val bytesSoFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val downloadedUri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (downloadedUri == null) {
                        val version = updateDownloadUiState?.version ?: "最新版本"
                        updateDownloadUiState = UpdateDownloadUiState(
                            version = version,
                            status = UpdateDownloadStatus.FAILED,
                            errorMessage = "下载完成但无法读取安装包"
                        )
                        MessageHelper.showToast(this, "下载完成但无法读取安装包")
                        return
                    }
                    val version = updateDownloadUiState?.version ?: "最新版本"
                    updateDownloadUiState = UpdateDownloadUiState(
                        version = version,
                        status = UpdateDownloadStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = bytesSoFar,
                        totalBytes = totalBytes,
                        downloadedApkUri = downloadedUri
                    )
                    MessageHelper.showSuccess(this, "下载完成")
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val version = updateDownloadUiState?.version ?: "最新版本"
                    updateDownloadUiState = UpdateDownloadUiState(
                        version = version,
                        status = UpdateDownloadStatus.FAILED,
                        errorMessage = "下载失败，错误码: $reason"
                    )
                    MessageHelper.showToast(this, "下载失败，错误码: $reason")
                }
            }
        }
        activeUpdateDownloadId = -1L
    }

    private fun startUpdateDownloadProgressPolling(downloadId: Long) {
        updateDownloadPollingJob?.cancel()
        updateDownloadPollingJob = lifecycleScope.launch {
            while (isActive && activeUpdateDownloadId == downloadId) {
                val snapshot = queryUpdateDownloadSnapshot(downloadId) ?: break
                val progress = snapshot.progressPercent()
                val version = updateDownloadUiState?.version ?: "最新版本"
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING -> {
                        updateDownloadUiState = UpdateDownloadUiState(
                            version = version,
                            status = UpdateDownloadStatus.DOWNLOADING,
                            progress = progress,
                            downloadedBytes = snapshot.bytesSoFar,
                            totalBytes = snapshot.totalBytes
                        )
                    }
                    DownloadManager.STATUS_FAILED -> {
                        updateDownloadUiState = UpdateDownloadUiState(
                            version = version,
                            status = UpdateDownloadStatus.FAILED,
                            progress = progress,
                            downloadedBytes = snapshot.bytesSoFar,
                            totalBytes = snapshot.totalBytes,
                            errorMessage = "下载失败，错误码: ${snapshot.reason}"
                        )
                        activeUpdateDownloadId = -1L
                        break
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        handleUpdateDownloadFinished(downloadId)
                        break
                    }
                }
                delay(450)
            }
        }
    }

    private fun queryUpdateDownloadSnapshot(downloadId: Long): DownloadSnapshot? {
        val downloadManager = getSystemService(DownloadManager::class.java) ?: return null
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadSnapshot(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                bytesSoFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }
    }

    private fun retryUpdateDownload() {
        val downloadUrl = latestUpdateDownloadUrl.orEmpty()
        val fallbackUrl = latestUpdateFallbackUrl.orEmpty()
        val version = updateDownloadUiState?.version.orEmpty()
        if (downloadUrl.isBlank() || version.isBlank()) return
        downloadLauncherUpdate(
            downloadUrl = downloadUrl,
            latestVersion = version,
            fallbackUrl = fallbackUrl
        )
    }

    private fun installDownloadedUpdateFromDialog() {
        val downloadedApkUri = updateDownloadUiState?.downloadedApkUri ?: return
        promptInstallDownloadedApk(downloadedApkUri)
    }

    private fun resumePendingInstallIfPossible() {
        if (!waitingUnknownSourcePermission) return
        val apkUri = pendingInstallApkUri ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            waitingUnknownSourcePermission = false
            promptInstallDownloadedApk(apkUri)
        }
    }

    private fun promptInstallDownloadedApk(apkUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkUri = apkUri
            waitingUnknownSourcePermission = true
            MessageHelper.showToast(this, "请先允许安装未知来源应用")
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(permissionIntent)
            return
        }

        runCatching {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
            pendingInstallApkUri = null
            waitingUnknownSourcePermission = false
            updateDownloadUiState = null
        }.onFailure { error ->
            MessageHelper.showToast(this, "无法启动安装: ${error.message ?: "未知错误"}")
        }
    }

}

/**
 * 主界面 Compose 内容
 */
@Composable
private fun MainActivityContent(
    state: MainUiState,
    importUiState: ImportUiState,
    navState: NavState,
    pageAlpha: Float = 1f,
    videoSpeed: Float = 1f,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: (updatedGame: GameItemUi) -> Unit,
    onDismissDeleteDialog: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    availableUpdate: AppUpdateUiModel? = null,
    updateDownloadState: UpdateDownloadUiState? = null,
    onDismissUpdateDialog: () -> Unit = {},
    onIgnoreUpdateClick: () -> Unit = {},
    onUpdateActionClick: () -> Unit = {},
    onCheckLauncherUpdateClick: () -> Unit = {},
    onDismissUpdateDownloadDialog: () -> Unit = {},
    onInstallDownloadedUpdate: () -> Unit = {},
    onRetryUpdateDownload: () -> Unit = {},
    onStartImport: (gameFilePath: String?, modLoaderFilePath: String?) -> Unit = { _, _ -> },
    onDismissImportError: () -> Unit = {},
    onImportCompletionHandled: () -> Unit = {},
    permissionManager: PermissionManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    val gameRendererOptions = remember {
        buildRendererOptions()
    }

    // 导入状态 - 提升到此层级避免导航时丢失
    var importGameFilePath by remember { mutableStateOf<String?>(null) }
    var importGameName by remember { mutableStateOf<String?>(null) }
    var importModLoaderFilePath by remember { mutableStateOf<String?>(null) }
    var importModLoaderName by remember { mutableStateOf<String?>(null) }
    
    // 当前文件选择类型 (game / modloader)
    var currentFileType by remember { mutableStateOf("") }
    var hasFilePermission by remember(permissionManager) {
        mutableStateOf(permissionManager?.hasRequiredPermissions() ?: true)
    }
    
    // 重置导入状态
    val resetImportState: () -> Unit = {
        importGameFilePath = null
        importGameName = null
        importModLoaderFilePath = null
        importModLoaderName = null
    }

    LaunchedEffect(importUiState.lastCompletedGameId) {
        val completedGameId = importUiState.lastCompletedGameId ?: return@LaunchedEffect
        resetImportState()
        if (navState.currentScreen is Screen.Import) {
            navState.navigateToGames()
        }
        onImportCompletionHandled()
        Log.d("MainActivityCompose", "Handled import completion for gameId=$completedGameId")
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容 - 背景层通过 backgroundLayer 传入，由 MainApp 自动标记为 hazeSource
        MainApp(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .graphicsLayer { alpha = pageAlpha },
            navState = navState,
            externalHazeState = hazeState,
            backgroundLayer = {
                // 背景层 - 沉浸式（作为毛玻璃模糊源）
                AppBackground(
                    backgroundType = state.backgroundType,
                    isPlaying = state.isVideoPlaying,
                    playbackSpeed = videoSpeed,
                    modifier = Modifier.fillMaxSize()
                )
                // 全局半透明遮罩，增加内容对比度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
            },
            appLogo = {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .graphicsLayer {
                            clip = true
                            scaleX = 1.42f
                            scaleY = 1.42f
                        },
                    contentScale = ContentScale.Crop
                )
            },
            games = state.games,
            selectedGame = state.selectedGame,
            isLoading = state.isLoading,
            onGameClick = onGameClick,
            onGameLongClick = onGameLongClick,
            onLaunchClick = onLaunchClick,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            gameRendererOptions = gameRendererOptions,
            iconLoader = { iconPath, modifier ->
                iconPath?.let {
                    AsyncImage(
                        model = File(it),
                        contentDescription = null,
                        modifier = modifier,
                        contentScale = ContentScale.Crop
                    )
                }
            },
            // 各页面的 Compose 实现
            settingsContent = {
                SettingsScreenWrapper(
                    onBack = { navState.navigateToGames() },
                    onCheckLauncherUpdate = onCheckLauncherUpdateClick
                )
            },
            controlsContent = {
                ControlLayoutScreenWrapper(
                    onBack = { navState.navigateToGames() },
                    onOpenStore = { navState.navigateToControlStore() }
                )
            },
            downloadContent = { 
                DownloadScreenWrapper(
                    onBack = { navState.navigateToGames() },
                    onNavigateToImport = { gamePath, modLoaderPath, gameName ->
                        // 设置导入参数
                        Log.d("MainActivityCompose", ">>> onNavigateToImport called: gamePath=$gamePath, modLoaderPath=$modLoaderPath, gameName=$gameName")
                        importGameFilePath = gamePath
                        importGameName = gameName
                        importModLoaderFilePath = modLoaderPath
                        importModLoaderName = modLoaderPath?.let { File(it).nameWithoutExtension }
                        // 导航到安装页面
                        Log.d("MainActivityCompose", ">>> navigating to Screen.Import")
                        navState.navigateTo(Screen.Import)
                    }
                )
            },
            announcementsContent = {
                AnnouncementScreenWrapper()
            },
            importContent = {
                ImportScreenWrapper(
                    gameFilePath = importGameFilePath,
                    detectedGameId = importGameName,
                    modLoaderFilePath = importModLoaderFilePath,
                    detectedModLoaderId = importModLoaderName,
                    importUiState = importUiState,
                    onBack = {
                        resetImportState()
                        navState.navigateToGames() 
                    },
                    onStartImport = { onStartImport(importGameFilePath, importModLoaderFilePath) },
                    onSelectGameFile = {
                        currentFileType = "game"
                        navState.navigateTo(Screen.FileBrowser(
                            initialPath = "",
                            allowedExtensions = listOf(".sh", ".zip"),
                            fileType = "game"
                        ))
                    },
                    onSelectModLoader = {
                        currentFileType = "modloader"
                        navState.navigateTo(Screen.FileBrowser(
                            initialPath = "",
                            allowedExtensions = listOf(".zip"),
                            fileType = "modloader"
                        ))
                    },
                    onDismissError = onDismissImportError
                )
            },
            controlStoreContent = { 
                ControlStoreScreenWrapper(
                    onBack = { navState.navigateToControls() }
                )
            },
            fileBrowserContent = { initialPath, allowedExtensions, fileType ->
                FileBrowserScreenWrapper(
                    initialPath = initialPath,
                    fileType = fileType,
                    allowedExtensions = allowedExtensions,
                    hasPermission = hasFilePermission,
                    onFileSelected = { path, type ->
                        val selectedType = type ?: currentFileType
                        val file = File(path)
                        
                        // 立即设置文件路径和名称
                        when (selectedType) {
                            "game" -> {
                                importGameFilePath = path
                                importGameName = file.nameWithoutExtension
                            }
                            "modloader" -> {
                                importModLoaderFilePath = path
                                importModLoaderName = file.nameWithoutExtension
                            }
                        }
                        
                        // 异步检测游戏/模组加载器名称
                        scope.launch(Dispatchers.IO) {
                            try {
                                when (selectedType) {
                                    "game" -> {
                                        val result = com.app.ralaunch.core.platform.install.InstallPluginRegistry.detectGame(file)
                                        result?.second?.definition?.displayName?.let { name ->
                                            withContext(Dispatchers.Main) {
                                                importGameName = name
                                            }
                                        }
                                    }
                                    "modloader" -> {
                                        val result = com.app.ralaunch.core.platform.install.InstallPluginRegistry.detectModLoader(file)
                                        result?.second?.definition?.displayName?.let { name ->
                                            withContext(Dispatchers.Main) {
                                                importModLoaderName = name
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // 保持文件名
                            }
                        }
                        
                        navState.goBack()
                    },
                    onBack = {
                        navState.goBack()
                    },
                    onRequestPermission = {
                        permissionManager?.requestRequiredPermissions(object : PermissionManager.PermissionCallback {
                            override fun onPermissionsGranted() {
                                hasFilePermission = true
                            }

                            override fun onPermissionsDenied() {
                                hasFilePermission = false
                            }
                        })
                    }
                )
            }
        )
        
        // 删除确认对话框 (纯 Compose)
        state.gamePendingDeletion?.let { game ->
            DeleteGameComposeDialog(
                gameName = game.displayedName,
                isDeleting = state.isDeletingGame,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteDialog
            )
        }

        availableUpdate?.let { update ->
            AppUpdateComposeDialog(
                update = update,
                onConfirm = onUpdateActionClick,
                onIgnore = onIgnoreUpdateClick,
                onDismiss = onDismissUpdateDialog
            )
        }

        updateDownloadState?.let { downloadState ->
            UpdateDownloadComposeDialog(
                state = downloadState,
                onDismiss = onDismissUpdateDownloadDialog,
                onInstall = onInstallDownloadedUpdate,
                onRetry = onRetryUpdateDownload
            )
        }
    }
}

/**
 * 纯 Compose 删除确认对话框
 */
@Composable
private fun DeleteGameComposeDialog(
    gameName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.delete_game_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isDeleting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.main_deleting_game_files),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.main_delete_game_confirm_message, gameName),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.main_delete_game_irreversible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            if (!isDeleting) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_confirm))
                }
            }
        },
        dismissButton = if (isDeleting) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun AppUpdateComposeDialog(
    update: AppUpdateUiModel,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    val previewNotes = remember(update.releaseNotes) {
        val normalized = update.releaseNotes.trim()
        if (normalized.length <= 240) normalized else normalized.take(240) + "..."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "当前版本: ${update.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "最新版本: ${update.latestVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = update.releaseName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (previewNotes.isNotEmpty()) {
                    Text(
                        text = previewNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (update.downloadUrl.isBlank()) "前往更新" else "下载更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) {
                Text("忽略此版本")
            }
        }
    )
}

@Composable
private fun UpdateDownloadComposeDialog(
    state: UpdateDownloadUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit
) {
    val title = when (state.status) {
        UpdateDownloadStatus.STARTING -> "正在准备下载"
        UpdateDownloadStatus.DOWNLOADING -> "正在下载更新"
        UpdateDownloadStatus.COMPLETED -> "下载完成"
        UpdateDownloadStatus.FAILED -> "下载失败"
    }
    val canDismiss = state.status == UpdateDownloadStatus.COMPLETED ||
        state.status == UpdateDownloadStatus.FAILED
    val progressFraction = (state.progress.coerceIn(0, 100) / 100f)
    val progressText = if (state.totalBytes > 0) {
        "${state.progress}% · ${state.downloadedBytes.toReadableSize()} / ${state.totalBytes.toReadableSize()}"
    } else {
        "${state.progress}% · ${state.downloadedBytes.toReadableSize()}"
    }

    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "目标版本: ${state.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.status == UpdateDownloadStatus.STARTING) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "正在连接下载服务，请稍候…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.status == UpdateDownloadStatus.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.status == UpdateDownloadStatus.COMPLETED) {
                    AssistChip(
                        onClick = onInstall,
                        label = { Text("已就绪，可安装") }
                    )
                }

                if (state.status == UpdateDownloadStatus.FAILED) {
                    Text(
                        text = state.errorMessage ?: "下载过程中出现未知错误",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (state.status) {
                UpdateDownloadStatus.COMPLETED -> {
                    Button(onClick = onInstall) { Text("立即安装") }
                }
                UpdateDownloadStatus.FAILED -> {
                    Button(onClick = onRetry) { Text("重试下载") }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (canDismiss) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

private fun Long.toReadableSize(): String {
    if (this <= 0) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        this >= gb -> String.format("%.2f GB", this / gb)
        this >= mb -> String.format("%.1f MB", this / mb)
        this >= kb -> String.format("%.1f KB", this / kb)
        else -> "$this B"
    }
}

private data class DownloadSnapshot(
    val status: Int,
    val bytesSoFar: Long,
    val totalBytes: Long,
    val reason: Int
) {
    fun progressPercent(): Int {
        if (totalBytes <= 0L) return 0
        return ((bytesSoFar.coerceAtLeast(0L) * 100) / totalBytes).toInt().coerceIn(0, 100)
    }
}

private data class UpdateDownloadUiState(
    val version: String,
    val status: UpdateDownloadStatus,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val downloadedApkUri: Uri? = null
)

private enum class UpdateDownloadStatus {
    STARTING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}
