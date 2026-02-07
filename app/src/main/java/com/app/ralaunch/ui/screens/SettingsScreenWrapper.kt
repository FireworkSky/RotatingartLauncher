package com.app.ralaunch.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.shared.ui.components.dialogs.*
import com.app.ralaunch.shared.ui.screens.settings.*
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.ui.compose.dialogs.PatchManagementDialogCompose
import com.app.ralaunch.ui.compose.settings.SettingsViewModel as AppSettingsViewModel
import com.app.ralaunch.ui.main.MainActivityCompose
import com.app.ralaunch.utils.AssetIntegrityChecker
import com.app.ralaunch.R
import com.app.ralaunch.runtime.RuntimeLibraryLoader
import com.app.ralaunch.utils.LocaleManager
import kotlinx.coroutines.launch

/**
 * 设置页面包装器 - App 层
 * 
 * 整合所有设置功能，处理 Android 特定交互
 */
@Composable
fun SettingsScreenWrapper(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val scope = rememberCoroutineScope()
    
    // 使用 Activity 级别的 ViewModel 缓存，避免页面切换时重建
    val viewModel: AppSettingsViewModel = remember {
        ViewModelProvider(
            activity as ViewModelStoreOwner,
            AppSettingsViewModel.Factory(context)
        )[AppSettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val effect by viewModel.effect.collectAsState()

    // 对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showPatchManagementDialog by remember { mutableStateOf(false) }
    var showMultiplayerDisclaimerDialog by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 联机设置状态
    var multiplayerEnabled by remember { mutableStateOf(SettingsManager.getInstance().isMultiplayerEnabled) }

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
    LaunchedEffect(effect) {
        effect?.let { eff ->
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
                is SettingsEffect.BackgroundOpacityChanged -> applyOpacityChange(context, eff.opacity)
                is SettingsEffect.VideoSpeedChanged -> applyVideoSpeedChange(context, eff.speed)
                is SettingsEffect.RestoreDefaultBackgroundComplete -> restoreDefaultBackground(context)
            }
            viewModel.clearEffect()
        }
    }

    // 渲染设置页面
    SettingsScreenContent(
        currentCategory = uiState.currentCategory,
        onCategoryClick = { viewModel.selectCategory(it) }
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
                        language = uiState.language
                    ),
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onThemeColorClick = { viewModel.onEvent(SettingsEvent.OpenThemeColorSelector) },
                    onBackgroundTypeChange = { viewModel.setBackgroundType(it) },
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
                    onTouchMultitouchChange = { viewModel.setTouchMultitouch(it) },
                    mouseRightStickEnabled = uiState.mouseRightStickEnabled,
                    onMouseRightStickChange = { viewModel.setMouseRightStick(it) },
                    vibrationEnabled = uiState.vibrationEnabled,
                    onVibrationChange = { viewModel.setVibrationEnabled(it) },
                    vibrationStrength = uiState.vibrationStrength,
                    onVibrationStrengthChange = { viewModel.setVibrationStrength(it) }
                )
            }
            SettingsCategory.GAME -> {
                GameSettingsContent(
                    bigCoreAffinityEnabled = uiState.bigCoreAffinityEnabled,
                    onBigCoreAffinityChange = { viewModel.setBigCoreAffinity(it) },
                    lowLatencyAudioEnabled = uiState.lowLatencyAudioEnabled,
                    onLowLatencyAudioChange = { viewModel.setLowLatencyAudio(it) },
                    rendererType = uiState.rendererType,
                    onRendererClick = { viewModel.onEvent(SettingsEvent.OpenRendererSelector) },
                    // 画质设置
                    qualityLevel = uiState.qualityLevel,
                    onQualityLevelChange = { viewModel.onEvent(SettingsEvent.SetQualityLevel(it)) },
                    shaderLowPrecision = uiState.shaderLowPrecision,
                    onShaderLowPrecisionChange = { viewModel.onEvent(SettingsEvent.SetShaderLowPrecision(it)) },
                    // 帧率设置
                    targetFps = uiState.targetFps,
                    onTargetFpsChange = { viewModel.onEvent(SettingsEvent.SetTargetFps(it)) }
                )
            }
            SettingsCategory.LAUNCHER -> {
                LauncherSettingsContent(
                    onPatchManagementClick = { viewModel.onEvent(SettingsEvent.OpenPatchManagement) },
                    onForceReinstallPatchesClick = { viewModel.onEvent(SettingsEvent.ForceReinstallPatches) },
                    multiplayerEnabled = multiplayerEnabled,
                    onMultiplayerToggle = { enabled ->
                        if (enabled) {
                            // 首次启用需要先显示声明对话框
                            if (!SettingsManager.getInstance().hasMultiplayerDisclaimerAccepted) {
                                showMultiplayerDisclaimerDialog = true
                            } else {
                                multiplayerEnabled = true
                                SettingsManager.getInstance().isMultiplayerEnabled = true
                            }
                        } else {
                            multiplayerEnabled = false
                            SettingsManager.getInstance().isMultiplayerEnabled = false
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
                    },
                    assetStatusSummary = assetStatusSummary
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
                    onLoggingChange = { viewModel.setLoggingEnabled(it) },
                    onVerboseLoggingChange = { viewModel.onEvent(SettingsEvent.SetVerboseLogging(it)) },
                    onViewLogsClick = { viewModel.onEvent(SettingsEvent.ViewLogs) },
                    onExportLogsClick = { viewModel.onEvent(SettingsEvent.ExportLogs) },
                    onClearCacheClick = { viewModel.onEvent(SettingsEvent.ClearCache) },
                    onBigCoreAffinityChange = { viewModel.setBigCoreAffinity(it) },
                    onKillLauncherUIChange = { viewModel.onEvent(SettingsEvent.SetKillLauncherUI(it)) },
                    onLowLatencyAudioChange = { viewModel.setLowLatencyAudio(it) },
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
                    onCheckUpdateClick = { viewModel.onEvent(SettingsEvent.CheckUpdate) },
                    onLicenseClick = { viewModel.onEvent(SettingsEvent.OpenLicense) },
                    onSponsorsClick = { viewModel.onEvent(SettingsEvent.OpenSponsors) },
                    onCommunityLinkClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) },
                    onContributorClick = { url -> viewModel.onEvent(SettingsEvent.OpenUrl(url)) }
                )
            }
        }
    }

    // ==================== 对话框 ====================

    if (showLanguageDialog) {
        LanguageSelectDialog(
            currentLanguage = getLanguageCode(uiState.language),
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
                // 保存到设置
                SettingsManager.getInstance().themeColor = color
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
            val compatible = com.app.ralaunch.renderer.RendererConfig.getCompatibleRenderers(context)
            // 始终包含 "自动选择" 选项
            val list = mutableListOf(
                RendererOption("auto", "自动选择", "根据设备自动选择最佳渲染器")
            )
            compatible.forEach { info ->
                list.add(RendererOption(info.id, info.displayName ?: info.id, info.description ?: ""))
            }
            list
        }
        
        RendererSelectDialog(
            currentRenderer = getRendererCode(uiState.rendererType),
            renderers = availableRenderers,
            onSelect = { rendererId ->
                viewModel.onEvent(SettingsEvent.SetRenderer(rendererId))
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
                Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLogViewerDialog = false }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
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
                SettingsManager.getInstance().hasMultiplayerDisclaimerAccepted = true
                SettingsManager.getInstance().isMultiplayerEnabled = true
                multiplayerEnabled = true
                showMultiplayerDisclaimerDialog = false
                Toast.makeText(context, "联机功能已启用", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "请重启应用以完成修复", Toast.LENGTH_LONG).show()
                            }
                            showAssetCheckDialog = false
                            // 重新检查
                            assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                            assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
                        } else {
                            Toast.makeText(context, "修复失败: ${fixResult.message}", Toast.LENGTH_LONG).show()
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
            title = { Text("重新解压运行时库") },
            text = {
                Column {
                    Text("此操作将删除现有运行时库并重新解压。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "如果游戏启动失败或提示库文件缺失，可以尝试此操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isReExtracting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "正在解压...",
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
                                    Toast.makeText(context, "运行时库重新解压成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "运行时库重新解压失败", Toast.LENGTH_LONG).show()
                                }
                                // 刷新状态
                                assetStatusSummary = AssetIntegrityChecker.getStatusSummary(context)
                            } catch (e: Exception) {
                                isReExtracting = false
                                Toast.makeText(context, "解压失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isReExtracting
                ) {
                    Text("确认解压")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReExtractConfirmDialog = false },
                    enabled = !isReExtracting
                ) {
                    Text("取消")
                }
            }
        )
    }
}


