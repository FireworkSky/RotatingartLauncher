package com.app.ralaunch.feature.main.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
import com.app.ralaunch.shared.core.component.dialogs.*
import com.app.ralaunch.shared.core.theme.AppThemeState
import com.app.ralaunch.shared.feature.settings.*
import com.app.ralaunch.core.ui.dialog.PatchManagementDialogCompose
import com.app.ralaunch.core.common.util.AssetIntegrityChecker
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.runtime.renderer.RendererRegistry
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import com.app.ralaunch.core.common.util.LocaleManager
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

/**
 * 设置页面包装器 - App 层
 * 
 * 整合所有设置功能，处理 Android 特定交互
 */
@Composable
fun SettingsScreenWrapper(
    onBack: () -> Unit = {},
    onCheckLauncherUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val scope = rememberCoroutineScope()
    val settingsRepository: SettingsRepositoryV2 = remember {
        KoinJavaComponent.get(SettingsRepositoryV2::class.java)
    }
    
    // 使用 Activity 级别的 ViewModel 缓存，避免页面切换时重建
    val viewModel: SettingsViewModel = remember {
        val appInfo: AppInfo = KoinJavaComponent.getOrNull(AppInfo::class.java) ?: AppInfo()
        ViewModelProvider(
            activity as ViewModelStoreOwner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = settingsRepository,
                        appInfo = appInfo
                    ) as T
                }
            }
        )[SettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()

    // 对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showPatchManagementDialog by remember { mutableStateOf(false) }
    var showMultiplayerDisclaimerDialog by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var appInfoTapCount by rememberSaveable { mutableIntStateOf(0) }
    
    // 联机设置状态
    var multiplayerEnabled by remember { mutableStateOf(settingsRepository.Settings.multiplayerEnabled) }

    // 资产完整性检查状态
    var showAssetCheckDialog by remember { mutableStateOf(false) }
    var assetCheckResult by remember { mutableStateOf<AssetIntegrityChecker.CheckResult?>(null) }
    var isCheckingAssets by remember { mutableStateOf(false) }
    var assetStatusSummary by remember { mutableStateOf("") }
    var showReExtractConfirmDialog by remember { mutableStateOf(false) }
    var isReExtracting by remember { mutableStateOf(false) }

    // 加载资产状态摘要
    LaunchedEffect(Unit) {
        assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
    }

    // 文件选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            scope.launch {
                handleImageSelection(context, it, viewModel)
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            scope.launch {
                handleVideoSelection(context, it, viewModel)
            }
        }
    }

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                exportLogs(context, it)
            }
        }
    }

    // 处理副作用
    LaunchedEffect(Unit) {
        viewModel.effect.collect { eff ->
            when (eff) {
                is SettingsEffect.OpenImagePicker -> imagePickerLauncher.launch("image/*")
                is SettingsEffect.OpenVideoPicker -> videoPickerLauncher.launch("video/*")
                is SettingsEffect.OpenUrl -> openUrl(context, eff.url)
                is SettingsEffect.ShowToast -> Toast.makeText(context, eff.message, Toast.LENGTH_SHORT).show()
                is SettingsEffect.OpenLanguageDialog -> showLanguageDialog = true
                is SettingsEffect.OpenThemeColorDialog -> showThemeColorDialog = true
                is SettingsEffect.OpenRendererDialog -> showRendererDialog = true
                is SettingsEffect.ViewLogsPage -> {
                    logs = loadLogs(context)
                    showLogViewerDialog = true
                }
                is SettingsEffect.ExportLogsToFile -> {
                    logExportLauncher.launch("ralaunch_logs_${System.currentTimeMillis()}.txt")
                }
                is SettingsEffect.OpenLicensePage -> showLicenseDialog = true
                is SettingsEffect.OpenSponsorsPage -> openSponsorsPage(context)
                is SettingsEffect.OpenPatchManagementDialog -> {
                    showPatchManagementDialog = true
                }
                is SettingsEffect.ClearCacheComplete -> clearAppCache(context)
                is SettingsEffect.ForceReinstallPatchesComplete -> forceReinstallPatches(context)
                is SettingsEffect.BackgroundOpacityChanged -> applyOpacityChange(eff.opacity)
                is SettingsEffect.VideoSpeedChanged -> applyVideoSpeedChange(eff.speed)
                is SettingsEffect.RestoreDefaultBackgroundComplete -> restoreDefaultBackground(context)
            }
        }
    }

    // 渲染设置页面
    SettingsScreenContent(
        currentCategory = uiState.currentCategory,
        onCategoryClick = { viewModel.onEvent(SettingsEvent.SelectCategory(it)) }
    ) { category ->
        when (category) {
            SettingsCategory.APPEARANCE -> {
                AppearanceSettingsContent(
                    state = AppearanceState(
                        themeMode = uiState.themeMode,
                        themeColor = uiState.themeColor,
                        backgroundType = uiState.backgroundType,
                        backgroundOpacity = uiState.backgroundOpacity,
                        videoPlaybackSpeed = uiState.videoPlaybackSpeed,
                        language = LocaleManager.getLanguageDisplayName(uiState.language)
                    ),
                    onThemeModeChange = { viewModel.onEvent(SettingsEvent.SetThemeMode(it)) },
                    onThemeColorClick = { viewModel.onEvent(SettingsEvent.OpenThemeColorSelector) },
                    onBackgroundTypeChange = { viewModel.onEvent(SettingsEvent.SetBackgroundType(it)) },
                    onSelectImageClick = { viewModel.onEvent(SettingsEvent.SelectBackgroundImage) },
                    onSelectVideoClick = { viewModel.onEvent(SettingsEvent.SelectBackgroundVideo) },
                    onBackgroundOpacityChange = { viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(it)) },
                    onVideoSpeedChange = { viewModel.onEvent(SettingsEvent.SetVideoPlaybackSpeed(it)) },
                    onRestoreDefaultBackground = { viewModel.onEvent(SettingsEvent.RestoreDefaultBackground) },
                    onLanguageClick = { viewModel.onEvent(SettingsEvent.OpenLanguageSelector) }
                )
            }
            SettingsCategory.CONTROLS -> {
                ControlsSettingsContent(
                    touchMultitouchEnabled = uiState.touchMultitouchEnabled,
                    onTouchMultitouchChange = { viewModel.onEvent(SettingsEvent.SetTouchMultitouch(it)) },
                    mouseRightStickEnabled = uiState.mouseRightStickEnabled,
                    onMouseRightStickChange = { viewModel.onEvent(SettingsEvent.SetMouseRightStick(it)) },
                    vibrationEnabled = uiState.vibrationEnabled,
                    onVibrationChange = { viewModel.onEvent(SettingsEvent.SetVibrationEnabled(it)) },
                    vibrationStrength = uiState.vibrationStrength,
                    onVibrationStrengthChange = { viewModel.onEvent(SettingsEvent.SetVibrationStrength(it)) }
                )
            }
            SettingsCategory.GAME -> {
                GameSettingsContent(
                    state = GameState(
                        bigCoreAffinityEnabled = uiState.bigCoreAffinityEnabled,
                        lowLatencyAudioEnabled = uiState.lowLatencyAudioEnabled,
                        ralAudioBufferSize = uiState.ralAudioBufferSize,
                        rendererDisplayName = RendererRegistry.getRendererDisplayName(uiState.rendererType),
                        qualityLevel = uiState.qualityLevel,
                        shaderLowPrecision = uiState.shaderLowPrecision,
                        targetFps = uiState.targetFps
                    ),
                    onBigCoreAffinityChange = { viewModel.onEvent(SettingsEvent.SetBigCoreAffinity(it)) },
                    onLowLatencyAudioChange = { viewModel.onEvent(SettingsEvent.SetLowLatencyAudio(it)) },
                    onRalAudioBufferSizeChange = { viewModel.onEvent(SettingsEvent.SetRalAudioBufferSize(it)) },
                    onRendererClick = { viewModel.onEvent(SettingsEvent.OpenRendererSelector) },
                    onQualityLevelChange = { viewModel.onEvent(SettingsEvent.SetQualityLevel(it)) },
                    onShaderLowPrecisionChange = { viewModel.onEvent(SettingsEvent.SetShaderLowPrecision(it)) },
                    onTargetFpsChange = { viewModel.onEvent(SettingsEvent.SetTargetFps(it)) }
                )
            }
            SettingsCategory.LAUNCHER -> {
                LauncherSettingsContent(
                    state = LauncherState(
                        multiplayerEnabled = multiplayerEnabled,
                        assetStatusSummary = assetStatusSummary
                    ),
                    onPatchManagementClick = { viewModel.onEvent(SettingsEvent.OpenPatchManagement) },
                    onForceReinstallPatchesClick = { viewModel.onEvent(SettingsEvent.ForceReinstallPatches) },
                    onMultiplayerToggle = { enabled ->
                        if (enabled) {
                            // 首次启用需要先显示声明对话框
                            if (!settingsRepository.Settings.multiplayerDisclaimerAccepted) {
                                showMultiplayerDisclaimerDialog = true
                            } else {
                                multiplayerEnabled = true
                                scope.launch {
                                    settingsRepository.update { this.multiplayerEnabled = true }
                                }
                            }
                        } else {
                            multiplayerEnabled = false
                            scope.launch {
                                settingsRepository.update { this.multiplayerEnabled = false }
                            }
                        }
                    },
                    // 资产完整性检查
                    onCheckIntegrityClick = {
                        scope.launch {
                            isCheckingAssets = true
                            showAssetCheckDialog = true
                            assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                            isCheckingAssets = false
                            // 刷新状态摘要
                            assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
                        }
                    },
                    onReExtractRuntimeLibsClick = {
                        showReExtractConfirmDialog = true
                    }
                )
            }
            SettingsCategory.DEVELOPER -> {
                DeveloperSettingsContent(
                    state = DeveloperState(
                        loggingEnabled = uiState.loggingEnabled,
                        verboseLogging = uiState.verboseLogging,
                        bigCoreAffinityEnabled = uiState.bigCoreAffinityEnabled,
                        killLauncherUIEnabled = uiState.killLauncherUIEnabled,
                        lowLatencyAudioEnabled = uiState.lowLatencyAudioEnabled,
                        serverGCEnabled = uiState.serverGCEnabled,
                        concurrentGCEnabled = uiState.concurrentGCEnabled,
                        tieredCompilationEnabled = uiState.tieredCompilationEnabled,
                        fnaMapBufferRangeOptEnabled = uiState.fnaMapBufferRangeOptEnabled
                    ),
                    onLoggingChange = { viewModel.onEvent(SettingsEvent.SetLoggingEnabled(it)) },
                    onVerboseLoggingChange = { viewModel.onEvent(SettingsEvent.SetVerboseLogging(it)) },
                    onViewLogsClick = { viewModel.onEvent(SettingsEvent.ViewLogs) },
                    onExportLogsClick = { viewModel.onEvent(SettingsEvent.ExportLogs) },
                    onClearCacheClick = { viewModel.onEvent(SettingsEvent.ClearCache) },
                    onBigCoreAffinityChange = { viewModel.onEvent(SettingsEvent.SetBigCoreAffinity(it)) },
                    onKillLauncherUIChange = { viewModel.onEvent(SettingsEvent.SetKillLauncherUI(it)) },
                    onLowLatencyAudioChange = { viewModel.onEvent(SettingsEvent.SetLowLatencyAudio(it)) },
                    onServerGCChange = { viewModel.onEvent(SettingsEvent.SetServerGC(it)) },
                    onConcurrentGCChange = { viewModel.onEvent(SettingsEvent.SetConcurrentGC(it)) },
                    onTieredCompilationChange = { viewModel.onEvent(SettingsEvent.SetTieredCompilation(it)) },
                    onFnaMapBufferRangeOptChange = { viewModel.onEvent(SettingsEvent.SetFnaMapBufferRangeOpt(it)) },
                    onForceReinstallPatchesClick = { viewModel.onEvent(SettingsEvent.ForceReinstallPatches) }
                )
            }
            SettingsCategory.ABOUT -> {
                AboutSettingsContent(
                    state = AboutState(
                        appVersion = uiState.appVersion,
                        buildInfo = uiState.buildInfo
                    ),
                    onCheckUpdateClick = {
                        onCheckLauncherUpdate()
                    },
                    onLicenseClick = { viewModel.onEvent(SettingsEvent.OpenLicense) },
                    onSponsorsClick = { viewModel.onEvent(SettingsEvent.OpenSponsors) },
                    onCommunityLinkClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) },
                    onContributorClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) },
                    onAppInfoCardClick = {
                        val nextTapCount = appInfoTapCount + 1
                        if (nextTapCount >= APP_INFO_EASTER_EGG_TRIGGER_COUNT) {
                            appInfoTapCount = 0
                            val url = if (isChineseLanguage(context)) {
                                APP_INFO_EASTER_EGG_ZH_URL
                            } else {
                                APP_INFO_EASTER_EGG_NON_ZH_URL
                            }
                            viewModel.onEvent(SettingsEvent.OpenUrl(url))
                        } else {
                            appInfoTapCount = nextTapCount
                        }
                    }
                )
            }
        }
    }

    // ==================== 对话框 ====================

    if (showLanguageDialog) {
        LanguageSelectDialog(
            currentLanguage = uiState.language,
            onSelect = { code ->
                LocaleManager.setLanguage(context, code)
                viewModel.onEvent(SettingsEvent.SetLanguage(code))
                Toast.makeText(context, context.getString(R.string.language_changed, LocaleManager.getLanguageDisplayName(code)), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeColorDialog) {
        ThemeColorSelectDialog(
            currentColor = uiState.themeColor,
            onSelect = { color ->
                // 更新 ViewModel
                viewModel.onEvent(SettingsEvent.SetThemeColor(color))
                // 更新全局主题状态
                AppThemeState.updateThemeColor(color)
            },
            onDismiss = { showThemeColorDialog = false }
        )
    }

    if (showRendererDialog) {
        // 获取设备上实际可用的渲染器
        val availableRenderers = remember {
            buildRendererOptions()
        }
        
        RendererSelectDialog(
            currentRenderer = uiState.rendererType,
            renderers = availableRenderers,
            onSelect = { renderer ->
                viewModel.onEvent(SettingsEvent.SetRenderer(renderer))
            },
            onDismiss = { showRendererDialog = false }
        )
    }

    if (showLogViewerDialog) {
        LogViewerDialog(
            logs = logs,
            onExport = {
                logExportLauncher.launch("ralaunch_logs_${System.currentTimeMillis()}.txt")
            },
            onClear = {
                clearLogs(context)
                logs = emptyList()
                Toast.makeText(context, context.getString(R.string.settings_logs_cleared), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLogViewerDialog = false }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(
            onDismiss = { showLicenseDialog = false }
        )
    }
    
    if (showPatchManagementDialog) {
        PatchManagementDialogCompose(
            onDismiss = { showPatchManagementDialog = false }
        )
    }

    // 联机功能声明对话框
    if (showMultiplayerDisclaimerDialog) {
        MultiplayerDisclaimerDialog(
            onConfirm = {
                multiplayerEnabled = true
                showMultiplayerDisclaimerDialog = false
                scope.launch {
                    settingsRepository.update {
                        this.multiplayerDisclaimerAccepted = true
                        this.multiplayerEnabled = true
                    }
                }
                Toast.makeText(context, context.getString(R.string.settings_multiplayer_enabled), Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showMultiplayerDisclaimerDialog = false
            }
        )
    }

    // 资产完整性检查结果对话框
    if (showAssetCheckDialog) {
        AssetCheckResultDialog(
            isChecking = isCheckingAssets,
            result = assetCheckResult,
            onAutoFix = {
                assetCheckResult?.let { result ->
                    scope.launch {
                        isCheckingAssets = true
                        val fixResult = AssetIntegrityChecker.autoFix(context, result.issues) { _, msg ->
                            // 可以添加进度显示
                        }
                        isCheckingAssets = false
                        
                        if (fixResult.success) {
                            Toast.makeText(context, fixResult.message, Toast.LENGTH_LONG).show()
                            if (fixResult.needsRestart) {
                                // 提示需要重启
                                Toast.makeText(context, context.getString(R.string.settings_fix_restart_required), Toast.LENGTH_LONG).show()
                            }
                            showAssetCheckDialog = false
                            // 重新检查
                            assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                            assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
                        } else {
                            Toast.makeText(context, context.getString(R.string.settings_fix_failed, fixResult.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDismiss = {
                showAssetCheckDialog = false
            }
        )
    }

    // 重新解压确认对话框
    if (showReExtractConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReExtractConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.settings_reextract_runtime_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_reextract_runtime_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_reextract_runtime_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isReExtracting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.settings_reextract_runtime_in_progress),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isReExtracting = true
                            try {
                                val result = RuntimeLibraryLoader.forceReExtract(context) { progress, msg ->
                                    // 进度回调
                                }
                                isReExtracting = false
                                showReExtractConfirmDialog = false
                                
                                if (result) {
                                    Toast.makeText(context, context.getString(R.string.settings_reextract_runtime_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.settings_reextract_runtime_failed), Toast.LENGTH_LONG).show()
                                }
                                // 刷新状态
                                assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
                            } catch (e: Exception) {
                                isReExtracting = false
                                Toast.makeText(context, context.getString(R.string.settings_extract_failed_with_reason, e.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isReExtracting
                ) {
                    Text(stringResource(R.string.settings_confirm_extract))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReExtractConfirmDialog = false },
                    enabled = !isReExtracting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private const val APP_INFO_EASTER_EGG_TRIGGER_COUNT = 50
private const val APP_INFO_EASTER_EGG_NON_ZH_URL = "https://youtu.be/CB42Hz349JM"
private const val APP_INFO_EASTER_EGG_ZH_URL = "https://www.bilibili.com/video/BV19wHSe3E1v"
