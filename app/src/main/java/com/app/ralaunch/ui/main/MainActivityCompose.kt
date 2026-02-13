package com.app.ralaunch.ui.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.ralaunch.R
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.manager.PermissionManager
import com.app.ralaunch.manager.ThemeManager
import com.app.ralaunch.manager.common.MessageHelper
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.shared.ui.navigation.*
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import com.app.ralaunch.ui.base.BaseActivity
import com.app.ralaunch.ui.compose.background.AppBackground
import com.app.ralaunch.ui.compose.background.BackgroundType
import com.app.ralaunch.shared.ui.model.GameItemUi
import com.app.ralaunch.ui.contracts.MainUiEffect
import com.app.ralaunch.ui.contracts.MainUiEvent
import com.app.ralaunch.ui.contracts.MainUiState
import com.app.ralaunch.ui.screens.ControlLayoutScreenWrapper
import com.app.ralaunch.ui.screens.ControlStoreScreenWrapper
import com.app.ralaunch.ui.screens.DownloadScreenWrapper
import com.app.ralaunch.ui.screens.FileBrowserScreenWrapper
import com.app.ralaunch.ui.screens.ImportScreenWrapper
import com.app.ralaunch.ui.screens.SettingsScreenWrapper
import com.app.ralaunch.ui.viewmodel.MainViewModel
import com.app.ralaunch.ui.viewmodel.MainViewModelFactory
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.DensityAdapter
import com.app.ralaunch.error.ErrorHandler
import com.app.ralaunch.ui.compose.splash.SplashOverlay
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MainActivityCompose : BaseActivity() {

    // Managers
    private lateinit var themeManager: ThemeManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var mainViewModel: MainViewModel

    private val navState = NavState()

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

        // 设置纯 Compose UI
        setContent {
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()

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
                        permissionManager = permissionManager,
                        onImportComplete = { gameType, gameItem ->
                            gameItem?.let { game ->
                                mainViewModel.onEvent(MainUiEvent.ImportCompleted(gameType, game))
                            }
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

}

/**
 * 主界面 Compose 内容
 */
@Composable
private fun MainActivityContent(
    state: MainUiState,
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
    onImportComplete: (String, GameItem?) -> Unit = { _, _ -> },
    permissionManager: PermissionManager? = null
) {
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

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
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
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
                    onBack = { navState.navigateToGames() }
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
            importContent = {
                ImportScreenWrapper(
                    gameFilePath = importGameFilePath,
                    detectedGameId = importGameName,
                    modLoaderFilePath = importModLoaderFilePath,
                    detectedModLoaderId = importModLoaderName,
                    onBack = {
                        resetImportState()
                        navState.navigateToGames() 
                    },
                    onImportComplete = { type, gameItem ->
                        resetImportState()
                        onImportComplete(type, gameItem)
                        navState.navigateToGames()
                    },
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
                    }
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
                                        val result = com.app.ralaunch.installer.InstallPluginRegistry.detectGame(file)
                                        result?.second?.definition?.displayName?.let { name ->
                                            withContext(Dispatchers.Main) {
                                                importGameName = name
                                            }
                                        }
                                    }
                                    "modloader" -> {
                                        val result = com.app.ralaunch.installer.InstallPluginRegistry.detectModLoader(file)
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
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteDialog
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "删除游戏",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "确定要删除 \"$gameName\" 吗？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "此操作将删除游戏文件，不可撤销",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
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
