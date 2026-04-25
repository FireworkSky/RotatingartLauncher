package com.app.ralaunch.feature.main.ui

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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.common.ErrorHandler
import com.app.ralaunch.core.common.MessageHelper
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.di.service.PermissionManagerServiceV1
import com.app.ralaunch.core.di.service.ThemeManagerServiceV1
import com.app.ralaunch.core.navigation.NavDestination
import com.app.ralaunch.core.navigation.NavState
import com.app.ralaunch.core.navigation.Screen
import com.app.ralaunch.core.navigation.handleBackPress
import com.app.ralaunch.core.navigation.handleEvent
import com.app.ralaunch.core.navigation.navigateToControlStore
import com.app.ralaunch.core.navigation.navigateToControls
import com.app.ralaunch.core.navigation.navigateToGameDetail
import com.app.ralaunch.core.navigation.navigateToGames
import com.app.ralaunch.core.navigation.navigateToSettings
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.core.platform.android.provider.RaLaunchFileProvider
import com.app.ralaunch.core.theme.AppThemeState
import com.app.ralaunch.core.theme.RaLaunchTheme
import com.app.ralaunch.core.ui.BaseActivity
import com.app.ralaunch.feature.announcement.ui.AnnouncementScreenWrapper
import com.app.ralaunch.feature.controls.packs.ui.ControlStoreScreenWrapper
import com.app.ralaunch.feature.controls.ui.ControlLayoutScreenWrapper
import com.app.ralaunch.feature.filebrowser.ui.FileBrowserScreenWrapper
import com.app.ralaunch.feature.gog.ui.DownloadScreenWrapper
import com.app.ralaunch.feature.installer.ui.InstallerScreenWrapper
import com.app.ralaunch.feature.installer.ui.rememberInstallerRouteActions
import com.app.ralaunch.feature.main.contracts.AppUpdateUiModel
import com.app.ralaunch.feature.main.contracts.ForceAnnouncementUiModel
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
import com.app.ralaunch.feature.main.ui.background.AppBackground
import com.app.ralaunch.feature.main.ui.background.BackgroundType
import com.app.ralaunch.feature.main.vm.MainViewModel
import com.app.ralaunch.feature.patch.ui.PatchManagementSubScreen
import com.app.ralaunch.feature.settings.ui.LogViewerSubScreen
import com.app.ralaunch.feature.settings.ui.RESTORE_SETTINGS_AFTER_RECREATE_KEY
import com.app.ralaunch.feature.settings.ui.SettingsScreenWrapper
import com.app.ralaunch.feature.settings.ui.buildRendererOptions
import dev.chrisbanes.haze.HazeState
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import com.app.ralaunch.core.model.BackgroundType as SettingsBackgroundType


class MainActivityCompose : BaseActivity() {

    // Managers
    private lateinit var themeManager: ThemeManagerServiceV1
    private lateinit var permissionManager: PermissionManagerServiceV1

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

        themeManager = ThemeManagerServiceV1(this)
        themeManager.applyThemeFromSettings()

        super.onCreate(savedInstanceState)

        // 初始化全局主题状态（从 SettingsAccess 加载）
        initializeThemeState()

        ErrorHandler.init(this)
        permissionManager = PermissionManagerServiceV1(this).apply { initialize() }
        registerUpdateDownloadReceiver()

        // 设置纯 Compose UI
        setContent {
            KoinContext {
                val themeMode by AppThemeState.themeMode.collectAsState()
                val themeColor by AppThemeState.themeColor.collectAsState()

                RaLaunchTheme(
                    themeMode = themeMode,
                    themeColor = themeColor
                ) {
                    MainScreenWrapper()
                }
            }
        }

        checkRestoreSettings()
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Exception) {
            AppLog.e("MainActivityCompose", "onResume error: ${e.message}")
        }

        ErrorHandler.setCurrentActivity(this)
        resumePendingInstallIfPossible()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(updateDownloadReceiver) }
        updateDownloadPollingJob?.cancel()
        updateDownloadPollingJob = null
        super.onDestroy()
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

    private fun checkRestoreSettings() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(RESTORE_SETTINGS_AFTER_RECREATE_KEY, false)) {
            prefs.edit { putBoolean(RESTORE_SETTINGS_AFTER_RECREATE_KEY, false) }
            navState.navigateToSettings()
        }
    }
    
    /**
     * 初始化全局主题状态（从 SettingsAccess 加载）
     */
    private fun initializeThemeState() {
        val settings = SettingsAccess

        AppThemeState.initializeState(
            themeMode = settings.themeMode,
            themeColor = settings.themeColor,
            backgroundType = settings.backgroundType,
            backgroundImagePath = settings.backgroundImagePath,
            backgroundVideoPath = settings.backgroundVideoPath,
            backgroundOpacity = settings.backgroundOpacity,
            videoPlaybackSpeed = settings.videoPlaybackSpeed
        )
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            MessageHelper.showToast(this, getString(R.string.settings_cannot_open_url))
        }
    }

    private fun registerUpdateDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
            FileUtils.deleteFileWithinRoot(targetFile, targetDir)
        }

        val request = DownloadManager.Request(downloadUrl.toUri()).apply {
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
        if (packageManager.canRequestPackageInstalls()) {
            waitingUnknownSourcePermission = false
            promptInstallDownloadedApk(apkUri)
        }
    }

    private fun promptInstallDownloadedApk(apkUri: Uri) {
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstallApkUri = apkUri
            waitingUnknownSourcePermission = true
            MessageHelper.showToast(this, getString(R.string.main_update_require_unknown_source_permission))
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:$packageName".toUri()
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

    @Composable
    private fun MainScreenWrapper() {
        val viewModel: MainViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        var showSplash by remember { mutableStateOf(true) }

        DisposableEffect(lifecycleOwner, viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            viewModel.onEvent(MainUiEvent.AppResumed)
                        }, 200)
                    }
                    Lifecycle.Event.ON_PAUSE -> viewModel.onEvent(MainUiEvent.AppPaused)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is MainUiEffect.ShowToast -> MessageHelper.showToast(
                        this@MainActivityCompose,
                        effect.message
                    )
                    is MainUiEffect.ShowSuccess -> MessageHelper.showSuccess(
                        this@MainActivityCompose,
                        effect.message
                    )
                    is MainUiEffect.DownloadLauncherUpdate -> {
                        downloadLauncherUpdate(
                            downloadUrl = effect.downloadUrl,
                            latestVersion = effect.latestVersion,
                            fallbackUrl = effect.releaseUrl
                        )
                    }
                    is MainUiEffect.OpenUrl -> openExternalUrl(effect.url)
                    is MainUiEffect.Navigate -> navState.handleEvent(effect.event)
                    is MainUiEffect.ExitLauncher -> finishAffinity()
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainActivityContent(
                state = state,
                onEvent = viewModel::onEvent
            )

            if (showSplash) {
                SplashOverlay(
                    isReady = !state.isLoading,
                    onSplashFinished = { showSplash = false }
                )
            }
        }
    }

    @Composable
    private fun MainActivityContent(
        state: MainUiState,
        onEvent: (MainUiEvent) -> Unit
    ) {
        val installerActions = rememberInstallerRouteActions()
        val hazeState = remember { HazeState() }
        val gameRendererOptions = remember { buildRendererOptions() }
        val bgType by AppThemeState.backgroundType.collectAsState()
        val bgOpacity by AppThemeState.backgroundOpacity.collectAsState()
        val videoSpeed by AppThemeState.videoPlaybackSpeed.collectAsState()
        val bgImagePath by AppThemeState.backgroundImagePath.collectAsState()
        val bgVideoPath by AppThemeState.backgroundVideoPath.collectAsState()

        val backgroundType = remember(bgType, bgImagePath, bgVideoPath) {
            when (bgType) {
                SettingsBackgroundType.IMAGE ->
                    if (bgImagePath.isNotEmpty()) BackgroundType.Image(bgImagePath) else BackgroundType.None
                SettingsBackgroundType.VIDEO ->
                    if (bgVideoPath.isNotEmpty()) BackgroundType.Video(bgVideoPath) else BackgroundType.None
                else -> BackgroundType.None
            }
        }
        val pageAlpha = remember(bgOpacity) {
            if (bgOpacity > 0) bgOpacity / 100f else 1f
        }

        var hasFilePermission by remember {
            mutableStateOf(permissionManager.hasRequiredPermissions())
        }
        val currentDestination by remember {
            derivedStateOf { navState.currentDestination }
        }

        val iconLoader: @Composable (String?, Modifier) -> Unit = { iconPath, modifier ->
            iconPath?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
        }

        LaunchedEffect(currentDestination, state.showAnnouncementBadge) {
            if (currentDestination == NavDestination.ANNOUNCEMENTS && state.showAnnouncementBadge) {
                onEvent(MainUiEvent.AnnouncementTabOpened)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainApp(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .graphicsLayer { alpha = pageAlpha },
                navState = navState,
                showAnnouncementBadge = state.showAnnouncementBadge,
                externalHazeState = hazeState,
                backgroundLayer = {
                    AppBackground(
                        backgroundType = backgroundType,
                        isPlaying = state.isVideoPlaying,
                        playbackSpeed = videoSpeed,
                        modifier = Modifier.fillMaxSize()
                    )
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
                pageContent = { targetScreen ->
                    when (targetScreen) {
                        is Screen.Games -> {
                            GameListContent(
                                games = state.games,
                                selectedGame = state.selectedGame,
                                onGameClick = { onEvent(MainUiEvent.GameSelected(it)) },
                                onGameLongClick = { onEvent(MainUiEvent.GameSelected(it)) },
                                onLaunchClick = { onEvent(MainUiEvent.LaunchRequested) },
                                onDeleteClick = { onEvent(MainUiEvent.DeleteRequested) },
                                onEditClick = {
                                    state.selectedGame?.id?.let { navState.navigateToGameDetail(it) }
                                },
                                onAddClick = { navState.navigateTo(Screen.Import) },
                                isLoading = state.isLoading,
                                iconLoader = iconLoader
                            )
                        }
                        is Screen.Controls -> {
                            ControlLayoutScreenWrapper(
                                onBack = { navState.navigateToGames() },
                                onOpenStore = { navState.navigateToControlStore() }
                            )
                        }
                        is Screen.Download -> {
                            DownloadScreenWrapper(
                                onBack = { navState.navigateToGames() },
                                onNavigateToImport = { gamePath, modLoaderPath, gameName ->
                                    installerActions.prefillFromDownload(gamePath, modLoaderPath, gameName)
                                    navState.navigateTo(Screen.Import)
                                }
                            )
                        }
                        is Screen.Announcements -> AnnouncementScreenWrapper()
                        is Screen.Settings -> {
                            SettingsScreenWrapper(
                                navState = navState,
                                onCheckLauncherUpdate = {
                                    onEvent(MainUiEvent.CheckAppUpdateManually)
                                }
                            )
                        }
                        is Screen.Import -> {
                            InstallerScreenWrapper(navState = navState)
                        }
                        is Screen.ControlStore -> {
                            ControlStoreScreenWrapper(
                                onBack = { navState.navigateToControls() }
                            )
                        }
                        is Screen.FileBrowser -> {
                            FileBrowserScreenWrapper(
                                initialPath = targetScreen.initialPath,
                                fileType = targetScreen.fileType,
                                allowedExtensions = targetScreen.allowedExtensions,
                                hasPermission = hasFilePermission,
                                onFileSelected = { path, type ->
                                    installerActions.onFileSelected(type ?: targetScreen.fileType, path)
                                    navState.goBack()
                                },
                                onBack = { navState.goBack() },
                                onRequestPermission = {
                                    permissionManager.requestRequiredPermissions(object : PermissionManagerServiceV1.PermissionCallback {
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
                        is Screen.GameDetail -> {
                            val game = state.games.find { it.id == targetScreen.storageId }
                            if (game != null) {
                                GameInfoEditSubScreen(
                                    game = game,
                                    rendererOptions = gameRendererOptions,
                                    onBack = { navState.goBack() },
                                    onSave = { onEvent(MainUiEvent.GameEdited(it)) }
                                )
                            } else {
                                PlaceholderScreen(stringResource(R.string.main_game_not_found, targetScreen.storageId))
                            }
                        }
                        is Screen.PatchManagement -> {
                            PatchManagementSubScreen(
                                onBack = { navState.goBack() }
                            )
                        }
                        is Screen.LogViewer -> {
                            LogViewerSubScreen(
                                onBack = { navState.goBack() }
                            )
                        }
                        is Screen.ControlEditor -> {
                            PlaceholderScreen(stringResource(R.string.main_control_editor_placeholder))
                        }
                        is Screen.Initialization -> Unit
                    }
                }
            )

            state.gamePendingDeletion?.let { game ->
                DeleteGameComposeDialog(
                    gameName = game.displayedName,
                    isDeleting = state.isDeletingGame,
                    onConfirm = { onEvent(MainUiEvent.DeleteConfirmed) },
                    onDismiss = { onEvent(MainUiEvent.DeleteDialogDismissed) }
                )
            }

            if (state.forceAnnouncement == null && updateDownloadUiState == null) {
                state.availableUpdate?.let { update ->
                    AppUpdateComposeDialog(
                        update = update,
                        onConfirm = { onEvent(MainUiEvent.UpdateActionClicked) },
                        onCloudDownload = { onEvent(MainUiEvent.UpdateCloudActionClicked) },
                        onIgnore = { onEvent(MainUiEvent.UpdateIgnoreClicked) },
                        onDismiss = { onEvent(MainUiEvent.UpdateDialogDismissed) }
                    )
                }
            }

            updateDownloadUiState?.let { downloadState ->
                UpdateDownloadComposeDialog(
                    state = downloadState,
                    onDismiss = { updateDownloadUiState = null },
                    onInstall = { installDownloadedUpdateFromDialog() },
                    onRetry = { retryUpdateDownload() }
                )
            }

            if (updateDownloadUiState == null) {
                state.forceAnnouncement?.let { announcement ->
                    ForceAnnouncementComposeDialog(
                        announcement = announcement,
                        onLearnMore = {
                            onEvent(MainUiEvent.AnnouncementPopupLearnMoreClicked)
                        },
                        onConfirm = {
                            onEvent(MainUiEvent.AnnouncementPopupViewClicked)
                        }
                    )
                }
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
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp.dp < 520.dp
    val widthFraction = if (configuration.screenWidthDp.dp >= 1000.dp) 0.78f else 0.92f
    val heightFraction = if (compactHeight) 0.94f else 0.88f
    val contentPadding = if (compactHeight) 16.dp else 24.dp
    val contentSpacing = if (compactHeight) 8.dp else 10.dp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
