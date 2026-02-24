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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.app.ralaunch.feature.main.contracts.ForceAnnouncementUiModel
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
import dev.jeziellago.compose.markdowntext.MarkdownText


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
                        showAnnouncementBadge = state.showAnnouncementBadge,
                        forceAnnouncement = state.forceAnnouncement,
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
                        onUpdateCloudActionClick = { mainViewModel.onEvent(MainUiEvent.UpdateCloudActionClicked) },
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
                        onImportCompletionHandled = { mainViewModel.resetImportCompletedFlag() },
                        onAnnouncementsOpened = { mainViewModel.onEvent(MainUiEvent.AnnouncementTabOpened) },
                        onForceAnnouncementLearnMore = {
                            mainViewModel.onEvent(MainUiEvent.AnnouncementPopupConfirmed)
                        },
                        onForceAnnouncementConfirm = {
                            navState.navigateToAnnouncements()
                            mainViewModel.onEvent(MainUiEvent.AnnouncementPopupConfirmed)
                        }
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
            MessageHelper.showToast(this, getString(R.string.settings_cannot_open_url))
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
            MessageHelper.showToast(this, getString(R.string.main_update_download_service_unavailable))
            if (fallbackUrl.isNotBlank()) openExternalUrl(fallbackUrl)
            return
        }

        val fileName = "RotatingartLauncher-${latestVersion.trim().removePrefix("v")}.apk"
        val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (targetDir == null) {
            MessageHelper.showToast(this, getString(R.string.main_update_download_dir_unavailable))
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
                MessageHelper.showInfo(this, getString(R.string.main_update_download_detected_existing_apk))
                return
            }
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(getString(R.string.main_update_notification_title))
            setDescription(getString(R.string.main_update_notification_downloading_version, latestVersion))
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
            MessageHelper.showToast(this, getString(R.string.main_update_download_started))
        }.onFailure { error ->
            val errorReason = error.message ?: getString(R.string.common_unknown_error)
            val errorMessage = getString(R.string.main_update_download_start_failed, errorReason)
            updateDownloadUiState = UpdateDownloadUiState(
                version = latestVersion,
                status = UpdateDownloadStatus.FAILED,
                errorMessage = errorMessage
            )
            MessageHelper.showToast(this, errorMessage)
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
                        val version = updateDownloadUiState?.version
                            ?: getString(R.string.main_update_latest_version_label)
                        updateDownloadUiState = UpdateDownloadUiState(
                            version = version,
                            status = UpdateDownloadStatus.FAILED,
                            errorMessage = getString(R.string.main_update_download_completed_apk_unreadable)
                        )
                        MessageHelper.showToast(this, getString(R.string.main_update_download_completed_apk_unreadable))
                        return
                    }
                    val version = updateDownloadUiState?.version
                        ?: getString(R.string.main_update_latest_version_label)
                    updateDownloadUiState = UpdateDownloadUiState(
                        version = version,
                        status = UpdateDownloadStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = bytesSoFar,
                        totalBytes = totalBytes,
                        downloadedApkUri = downloadedUri
                    )
                    MessageHelper.showSuccess(this, getString(R.string.gog_download_complete))
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val version = updateDownloadUiState?.version
                        ?: getString(R.string.main_update_latest_version_label)
                    val errorMessage = getString(R.string.main_update_download_failed_with_code, reason)
                    updateDownloadUiState = UpdateDownloadUiState(
                        version = version,
                        status = UpdateDownloadStatus.FAILED,
                        errorMessage = errorMessage
                    )
                    MessageHelper.showToast(this, errorMessage)
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
                val version = updateDownloadUiState?.version
                    ?: getString(R.string.main_update_latest_version_label)
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
                            errorMessage = getString(
                                R.string.main_update_download_failed_with_code,
                                snapshot.reason
                            )
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
            MessageHelper.showToast(this, getString(R.string.main_update_require_unknown_source_permission))
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
            val errorReason = error.message ?: getString(R.string.common_unknown_error)
            MessageHelper.showToast(
                this,
                getString(R.string.main_update_install_launch_failed, errorReason)
            )
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
    showAnnouncementBadge: Boolean,
    forceAnnouncement: ForceAnnouncementUiModel? = null,
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
    onUpdateCloudActionClick: () -> Unit = {},
    onCheckLauncherUpdateClick: () -> Unit = {},
    onDismissUpdateDownloadDialog: () -> Unit = {},
    onInstallDownloadedUpdate: () -> Unit = {},
    onRetryUpdateDownload: () -> Unit = {},
    onStartImport: (gameFilePath: String?, modLoaderFilePath: String?) -> Unit = { _, _ -> },
    onDismissImportError: () -> Unit = {},
    onImportCompletionHandled: () -> Unit = {},
    onAnnouncementsOpened: () -> Unit = {},
    onForceAnnouncementLearnMore: () -> Unit = {},
    onForceAnnouncementConfirm: () -> Unit = {},
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
    val currentDestination by remember {
        derivedStateOf { navState.currentDestination }
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

    LaunchedEffect(currentDestination, showAnnouncementBadge) {
        if (currentDestination == NavDestination.ANNOUNCEMENTS && showAnnouncementBadge) {
            onAnnouncementsOpened()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容 - 背景层通过 backgroundLayer 传入，由 MainApp 自动标记为 hazeSource
        MainApp(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .graphicsLayer { alpha = pageAlpha },
            navState = navState,
            showAnnouncementBadge = showAnnouncementBadge,
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

        if (forceAnnouncement == null && updateDownloadState == null) {
            availableUpdate?.let { update ->
                AppUpdateComposeDialog(
                    update = update,
                    onConfirm = onUpdateActionClick,
                    onCloudDownload = onUpdateCloudActionClick,
                    onIgnore = onIgnoreUpdateClick,
                    onDismiss = onDismissUpdateDialog
                )
            }
        }

        updateDownloadState?.let { downloadState ->
            UpdateDownloadComposeDialog(
                state = downloadState,
                onDismiss = onDismissUpdateDownloadDialog,
                onInstall = onInstallDownloadedUpdate,
                onRetry = onRetryUpdateDownload
            )
        }

        if (updateDownloadState == null) {
            forceAnnouncement?.let { announcement ->
                ForceAnnouncementComposeDialog(
                    announcement = announcement,
                    onLearnMore = onForceAnnouncementLearnMore,
                    onConfirm = onForceAnnouncementConfirm
                )
            }
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
    onCloudDownload: () -> Unit,
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
                text = stringResource(R.string.main_update_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.main_update_current_version,
                        update.currentVersion
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        R.string.main_update_latest_version,
                        update.latestVersion
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = update.releaseName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (update.publishedAt.isNotBlank()) {
                    Text(
                        text = update.publishedAt,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (previewNotes.isNotEmpty()) {
                    Text(
                        text = previewNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (update.cloudDownloadUrl.isNotBlank()) {
                    OutlinedButton(onClick = onCloudDownload) {
                        Text(stringResource(R.string.main_update_action_download_cloud))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(
                    if (update.githubDownloadUrl.isNotBlank()) {
                        stringResource(R.string.main_update_action_download_github)
                    } else if (update.downloadUrl.isBlank()) {
                        stringResource(R.string.main_update_action_open_release)
                    } else {
                        stringResource(R.string.main_update_action_download)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) {
                Text(stringResource(R.string.main_update_action_ignore_version))
            }
        }
    )
}

@Composable
private fun ForceAnnouncementComposeDialog(
    announcement: ForceAnnouncementUiModel,
    onLearnMore: () -> Unit,
    onConfirm: () -> Unit
) {
    val markdownContent = remember(announcement.markdown) {
        announcement.markdown
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
    val markdownScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val compactHeight = maxHeight < 520.dp
            val widthFraction = if (maxWidth >= 1000.dp) 0.78f else 0.92f
            val heightFraction = if (compactHeight) 0.94f else 0.88f
            val contentPadding = if (compactHeight) 16.dp else 24.dp
            val contentSpacing = if (compactHeight) 8.dp else 10.dp
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(widthFraction)
                    .fillMaxHeight(heightFraction),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing)
                ) {
                    Text(
                        text = stringResource(R.string.main_announcement_dialog_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (compactHeight) 2 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = announcement.publishedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (announcement.tags.isNotEmpty()) {
                        Text(
                            text = announcement.tags.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(markdownScrollState)
                                .padding(14.dp)
                        ) {
                            if (!markdownContent.isNullOrBlank()) {
                                MarkdownText(markdown = markdownContent)
                            } else {
                                Text(
                                    text = stringResource(R.string.announcement_no_content),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onLearnMore) {
                            Text(stringResource(R.string.main_announcement_dialog_action_learn_more))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onConfirm) {
                            Text(stringResource(R.string.main_announcement_dialog_action_view))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDownloadComposeDialog(
    state: UpdateDownloadUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit
) {
    val title = when (state.status) {
        UpdateDownloadStatus.STARTING -> stringResource(R.string.main_update_status_starting)
        UpdateDownloadStatus.DOWNLOADING -> stringResource(R.string.main_update_status_downloading)
        UpdateDownloadStatus.COMPLETED -> stringResource(R.string.main_update_status_completed)
        UpdateDownloadStatus.FAILED -> stringResource(R.string.main_update_status_failed)
    }
    val canDismiss = state.status == UpdateDownloadStatus.COMPLETED ||
        state.status == UpdateDownloadStatus.FAILED
    val progressFraction = (state.progress.coerceIn(0, 100) / 100f)
    val progressText = if (state.totalBytes > 0) {
        stringResource(
            R.string.main_update_progress_with_total,
            state.progress,
            state.downloadedBytes.toReadableSize(),
            state.totalBytes.toReadableSize()
        )
    } else {
        stringResource(
            R.string.main_update_progress_without_total,
            state.progress,
            state.downloadedBytes.toReadableSize()
        )
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
                    text = stringResource(R.string.main_update_target_version, state.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.status == UpdateDownloadStatus.STARTING) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.main_update_connecting_service),
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
                        label = { Text(stringResource(R.string.main_update_ready_to_install)) }
                    )
                }

                if (state.status == UpdateDownloadStatus.FAILED) {
                    Text(
                        text = state.errorMessage ?: stringResource(R.string.main_update_download_unknown_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (state.status) {
                UpdateDownloadStatus.COMPLETED -> {
                    Button(onClick = onInstall) { Text(stringResource(R.string.gog_install_now)) }
                }
                UpdateDownloadStatus.FAILED -> {
                    Button(onClick = onRetry) { Text(stringResource(R.string.main_update_action_retry_download)) }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (canDismiss) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}

@Composable
private fun Long.toReadableSize(): String {
    if (this <= 0) return stringResource(R.string.main_update_size_zero_bytes)
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        this >= gb -> stringResource(R.string.main_update_size_gb, this / gb)
        this >= mb -> stringResource(R.string.main_update_size_mb, this / mb)
        this >= kb -> stringResource(R.string.main_update_size_kb, this / kb)
        else -> stringResource(R.string.main_update_size_bytes, this)
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
