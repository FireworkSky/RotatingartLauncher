package com.app.ralaunch.feature.main.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import com.app.ralaunch.R
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.util.AssetIntegrityChecker
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import com.app.ralaunch.shared.core.platform.runtime.renderer.AndroidRendererRegistry
import com.app.ralaunch.core.ui.dialog.PatchManagementDialogCompose
import com.app.ralaunch.shared.core.component.dialogs.LanguageSelectDialog
import com.app.ralaunch.shared.core.component.dialogs.LicenseDialog
import com.app.ralaunch.shared.core.component.dialogs.LogViewerDialog
import com.app.ralaunch.shared.core.component.dialogs.RendererSelectDialog
import com.app.ralaunch.shared.core.component.dialogs.ThemeColorSelectDialog
import com.app.ralaunch.shared.core.model.domain.BackgroundType
import com.app.ralaunch.shared.core.model.domain.FpsLimit
import com.app.ralaunch.shared.core.model.domain.QualityLevel
import com.app.ralaunch.shared.core.model.domain.ThemeMode
import com.app.ralaunch.shared.core.theme.AppThemeState
import com.app.ralaunch.shared.feature.settings.ClickableSettingItem
import com.app.ralaunch.shared.feature.settings.SettingsCategory
import com.app.ralaunch.shared.feature.settings.SettingsDivider
import com.app.ralaunch.shared.feature.settings.SettingsEffect
import com.app.ralaunch.shared.feature.settings.SettingsEvent
import com.app.ralaunch.shared.feature.settings.SettingsScreenContent
import com.app.ralaunch.shared.feature.settings.SettingsSection
import com.app.ralaunch.shared.feature.settings.SettingsUiState
import com.app.ralaunch.shared.feature.settings.SettingsViewModel
import com.app.ralaunch.shared.feature.settings.SliderSettingItem
import com.app.ralaunch.shared.feature.settings.SwitchSettingItem
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource as androidStringResource

@Composable
fun SettingsScreenWrapper(
    onCheckLauncherUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val viewModelStoreOwner = activity as? ViewModelStoreOwner ?: return
    val viewModel: SettingsViewModel = koinViewModel(
        viewModelStoreOwner = viewModelStoreOwner
    )
    val uiState by viewModel.uiState.collectAsState()
    val assetStatusSummaryState = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        assetStatusSummaryState.value = AssetIntegrityChecker.getStatusSummary(context)
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsScreenContent(
        currentCategory = uiState.currentCategory,
        onCategoryClick = { viewModel.onEvent(SettingsEvent.SelectCategory(it)) }
    ) { category ->
        when (category) {
            SettingsCategory.APPEARANCE -> AppearanceSettingsPane(
                viewModel = viewModel,
                activity = activity,
                uiState = uiState
            )

            SettingsCategory.CONTROLS -> ControlsSettingsPane(
                viewModel = viewModel,
                uiState = uiState
            )

            SettingsCategory.GAME -> GameSettingsPane(
                viewModel = viewModel,
                uiState = uiState
            )

            SettingsCategory.LAUNCHER -> LauncherSettingsPane(
                viewModel = viewModel,
                uiState = uiState,
                assetStatusSummaryState = assetStatusSummaryState
            )

            SettingsCategory.DEVELOPER -> DeveloperSettingsPane(
                viewModel = viewModel,
                uiState = uiState
            )

            SettingsCategory.ABOUT -> AboutSettingsPane(
                uiState = uiState,
                onCheckLauncherUpdate = onCheckLauncherUpdate
            )
        }
    }
}

@Composable
private fun SettingsPaneColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun languageLabel(languageCode: String): String {
    return LocaleManager.getLanguageDisplayName(languageCode)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> androidStringResource(R.string.settings_appearance_theme_mode_system)
        ThemeMode.DARK -> androidStringResource(R.string.settings_appearance_theme_mode_dark)
        ThemeMode.LIGHT -> androidStringResource(R.string.settings_appearance_theme_mode_light)
    }
}

@Composable
private fun AppearanceSettingsPane(
    viewModel: SettingsViewModel,
    activity: Activity,
    uiState: SettingsUiState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                handleImageSelection(context, selectedUri, viewModel)
            }
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                handleVideoSelection(context, selectedUri, viewModel)
            }
        }
    }

    with(uiState) {
        SettingsPaneColumn {
            SettingsSection(title = androidStringResource(R.string.settings_appearance_theme_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_appearance_theme_mode_title),
                    subtitle = androidStringResource(R.string.settings_appearance_theme_mode_subtitle),
                    value = themeModeLabel(themeMode),
                    icon = Icons.Default.DarkMode,
                    onClick = {
                        val nextModeIndex = (ThemeMode.entries.indexOf(themeMode) + 1) % ThemeMode.entries.size
                        val nextMode = ThemeMode.entries[nextModeIndex]
                        SettingsAccess.themeMode = nextMode
                        viewModel.onEvent(SettingsEvent.SetThemeMode(nextMode))
                        AppThemeState.updateThemeMode(nextMode)
                        recreateActivityForUiRefresh(activity)
                    }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_appearance_theme_color_title),
                    subtitle = androidStringResource(R.string.settings_appearance_theme_color_subtitle),
                    icon = Icons.Default.Palette,
                    onClick = { showThemeColorDialog = true }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_appearance_background_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_appearance_background_image_title),
                    subtitle = if (backgroundType == BackgroundType.IMAGE) {
                        androidStringResource(R.string.settings_appearance_background_set)
                    } else {
                        androidStringResource(R.string.settings_appearance_background_select_image)
                    },
                    icon = Icons.Default.Image,
                    onClick = { imagePickerLauncher.launch("image/*") }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_appearance_background_video_title),
                    subtitle = if (backgroundType == BackgroundType.VIDEO) {
                        androidStringResource(R.string.settings_appearance_background_set)
                    } else {
                        androidStringResource(R.string.settings_appearance_background_select_video)
                    },
                    icon = Icons.Default.VideoLibrary,
                    onClick = { videoPickerLauncher.launch("video/*") }
                )

                if (backgroundType != BackgroundType.DEFAULT) {
                    SettingsDivider()

                    SliderSettingItem(
                        title = androidStringResource(R.string.settings_appearance_background_opacity_title),
                        subtitle = androidStringResource(R.string.settings_appearance_background_opacity_subtitle),
                        icon = Icons.Default.Opacity,
                        value = backgroundOpacity.toFloat(),
                        valueRange = BACKGROUND_OPACITY_RANGE,
                        steps = BACKGROUND_OPACITY_STEP_COUNT,
                        valueLabel = "$backgroundOpacity%",
                        onValueChange = {
                            val opacity = it.toInt()
                            viewModel.onEvent(SettingsEvent.SetBackgroundOpacity(opacity))
                            applyOpacityChange(opacity)
                        }
                    )
                }

                if (backgroundType == BackgroundType.VIDEO) {
                    SettingsDivider()

                    SliderSettingItem(
                        title = androidStringResource(R.string.settings_appearance_video_speed_title),
                        subtitle = androidStringResource(R.string.settings_appearance_video_speed_subtitle),
                        icon = Icons.Default.Speed,
                        value = videoPlaybackSpeed,
                        valueRange = VIDEO_PLAYBACK_SPEED_RANGE,
                        steps = VIDEO_PLAYBACK_SPEED_STEP_COUNT,
                        valueLabel = String.format(Locale.US, "%.1fx", videoPlaybackSpeed),
                        onValueChange = { speed ->
                            viewModel.onEvent(SettingsEvent.SetVideoPlaybackSpeed(speed))
                            applyVideoSpeedChange(speed)
                        }
                    )
                }

                if (backgroundType != BackgroundType.DEFAULT) {
                    SettingsDivider()

                    ClickableSettingItem(
                        title = androidStringResource(R.string.settings_appearance_restore_background_title),
                        subtitle = androidStringResource(R.string.settings_appearance_restore_background_subtitle),
                        icon = Icons.Default.Restore,
                        onClick = {
                            viewModel.onEvent(SettingsEvent.RestoreDefaultBackground)
                            AppThemeState.restoreDefaultBackground()
                            Toast.makeText(
                                context,
                                context.getString(R.string.appearance_background_restored),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            SettingsSection(title = androidStringResource(R.string.settings_appearance_language_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_appearance_language_title),
                    subtitle = androidStringResource(R.string.settings_appearance_language_subtitle),
                    value = languageLabel(language),
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        if (showLanguageDialog) {
            LanguageSelectDialog(
                currentLanguage = language,
                onSelect = { code ->
                    LocaleManager.setLanguage(context, code)
                    viewModel.onEvent(SettingsEvent.SetLanguage(code))
                    showLanguageDialog = false
                    recreateActivityForUiRefresh(activity)
                },
                onDismiss = { showLanguageDialog = false }
            )
        }

        if (showThemeColorDialog) {
            ThemeColorSelectDialog(
                currentColor = themeColor,
                onSelect = { color ->
                    viewModel.onEvent(SettingsEvent.SetThemeColor(color))
                    AppThemeState.updateThemeColor(color)
                    showThemeColorDialog = false
                },
                onDismiss = { showThemeColorDialog = false }
            )
        }
    }
}

@Composable
private fun ControlsSettingsPane(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    with(uiState) {
        SettingsPaneColumn {
            SettingsSection(title = androidStringResource(R.string.settings_controls_touch_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_controls_multitouch_title),
                    subtitle = androidStringResource(R.string.settings_controls_multitouch_subtitle),
                    icon = Icons.Default.TouchApp,
                    checked = touchMultitouchEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetTouchMultitouch(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_controls_mouse_right_stick_title),
                    subtitle = androidStringResource(R.string.settings_controls_mouse_right_stick_subtitle),
                    icon = Icons.Default.Mouse,
                    checked = mouseRightStickEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetMouseRightStick(it)) }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_controls_vibration_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_vibration),
                    subtitle = androidStringResource(R.string.settings_vibration_desc),
                    icon = Icons.Default.Gamepad,
                    checked = vibrationEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetVibrationEnabled(it)) }
                )

                if (vibrationEnabled) {
                    SettingsDivider()

                    SliderSettingItem(
                        title = androidStringResource(R.string.settings_controls_vibration_strength_title),
                        value = vibrationStrength,
                        valueRange = 0f..1f,
                        valueLabel = "${(vibrationStrength * 100).toInt()}%",
                        icon = Icons.Default.Tune,
                        onValueChange = { viewModel.onEvent(SettingsEvent.SetVibrationStrength(it)) }
                    )
                }
            }

            SettingsSection(title = androidStringResource(R.string.settings_controls_controller_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.virtual_controller_as_first),
                    subtitle = androidStringResource(R.string.virtual_controller_as_first_desc),
                    icon = Icons.Default.Gamepad,
                    checked = virtualControllerAsFirst,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetVirtualControllerAsFirst(it)) }
                )
            }
        }
    }
}

@Composable
private fun GameSettingsPane(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    var showRendererDialog by remember { mutableStateOf(false) }
    val availableRenderers = remember { buildRendererOptions() }
    val qualityOptions = listOf(
        QualityLevel.HIGH to androidStringResource(R.string.settings_quality_high),
        QualityLevel.MEDIUM to androidStringResource(R.string.settings_quality_medium),
        QualityLevel.LOW to androidStringResource(R.string.settings_quality_low)
    )
    val fpsOptions = listOf(
        FpsLimit.UNLIMITED to androidStringResource(R.string.settings_fps_unlimited),
        FpsLimit.FPS_30 to androidStringResource(R.string.settings_fps_30),
        FpsLimit.FPS_45 to androidStringResource(R.string.settings_fps_45),
        FpsLimit.FPS_60 to androidStringResource(R.string.settings_fps_60)
    )
    with(uiState) {
        val currentFpsName = fpsOptions.find { it.first == targetFps }?.second
            ?: androidStringResource(R.string.settings_fps_unlimited)

        SettingsPaneColumn {
            SettingsSection(title = androidStringResource(R.string.settings_game_performance_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.thread_affinity_big_core),
                    subtitle = androidStringResource(R.string.thread_affinity_big_core_desc),
                    icon = Icons.Default.Memory,
                    checked = bigCoreAffinityEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetBigCoreAffinity(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.low_latency_audio),
                    subtitle = androidStringResource(R.string.settings_game_low_latency_audio_subtitle),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    checked = lowLatencyAudioEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetLowLatencyAudio(it)) }
                )

                SettingsDivider()

                SliderSettingItem(
                    title = androidStringResource(R.string.settings_game_audio_buffer_title),
                    subtitle = androidStringResource(R.string.settings_game_audio_buffer_subtitle),
                    icon = Icons.Default.Tune,
                    value = audioBufferSizeToSliderPosition(ralAudioBufferSize),
                    valueRange = audioBufferSizeSliderRange(),
                    steps = audioBufferSizeSliderSteps(),
                    valueLabel = ralAudioBufferSize?.toString()
                        ?: androidStringResource(R.string.common_auto),
                    onValueChange = {
                        viewModel.onEvent(SettingsEvent.SetRalAudioBufferSize(sliderPositionToAudioBufferSize(it)))
                    }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_game_renderer_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.renderer_title),
                    subtitle = androidStringResource(R.string.renderer_desc),
                    value = AndroidRendererRegistry.getRendererDisplayName(rendererType),
                    icon = Icons.Default.Tv,
                    onClick = { showRendererDialog = true }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_game_quality_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_game_quality_preset_title),
                    subtitle = androidStringResource(R.string.settings_game_quality_preset_subtitle),
                    value = qualityOptions.find { it.first == qualityLevel }?.second
                        ?: qualityOptions.first().second,
                    icon = Icons.Default.Tune,
                    onClick = {
                        val currentIndex = qualityOptions.indexOfFirst { it.first == qualityLevel }
                            .takeIf { it >= 0 } ?: 0
                        val nextIndex = (currentIndex + 1) % qualityOptions.size
                        viewModel.onEvent(
                            SettingsEvent.SetQualityLevel(qualityOptions[nextIndex].first)
                        )
                    }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_game_shader_low_precision_title),
                    subtitle = androidStringResource(R.string.settings_game_shader_low_precision_subtitle),
                    icon = Icons.Default.FilterAlt,
                    checked = shaderLowPrecision,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetShaderLowPrecision(it)) }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_game_fps_limit_title),
                    subtitle = androidStringResource(R.string.settings_game_fps_limit_subtitle),
                    value = currentFpsName,
                    icon = Icons.Default.Speed,
                    onClick = {
                        val currentIndex = fpsOptions.indexOfFirst { it.first == targetFps }
                            .takeIf { it >= 0 } ?: 0
                        val nextIndex = (currentIndex + 1) % fpsOptions.size
                        viewModel.onEvent(SettingsEvent.SetTargetFps(fpsOptions[nextIndex].first))
                    }
                )
            }
        }

        if (showRendererDialog) {
            RendererSelectDialog(
                currentRenderer = rendererType,
                renderers = availableRenderers,
                onSelect = { renderer ->
                    viewModel.onEvent(SettingsEvent.SetRenderer(renderer))
                    showRendererDialog = false
                },
                onDismiss = { showRendererDialog = false }
            )
        }
    }
}

@Composable
private fun LauncherSettingsPane(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    assetStatusSummaryState: MutableState<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPatchManagementDialog by remember { mutableStateOf(false) }
    var showMultiplayerDisclaimerDialog by remember { mutableStateOf(false) }
    var showAssetCheckDialog by remember { mutableStateOf(false) }
    var assetCheckResult by remember { mutableStateOf<AssetIntegrityChecker.CheckResult?>(null) }
    var isCheckingAssets by remember { mutableStateOf(false) }
    var showReExtractConfirmDialog by remember { mutableStateOf(false) }
    var isReExtracting by remember { mutableStateOf(false) }
    val assetStatusSummary = assetStatusSummaryState.value

    SettingsPaneColumn {
        SettingsSection(title = androidStringResource(R.string.settings_launcher_assets_section)) {
            if (assetStatusSummary.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = assetStatusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            ClickableSettingItem(
                title = androidStringResource(R.string.settings_launcher_check_integrity_title),
                subtitle = androidStringResource(R.string.settings_launcher_check_integrity_subtitle),
                icon = Icons.Default.VerifiedUser,
                onClick = {
                    scope.launch {
                        isCheckingAssets = true
                        showAssetCheckDialog = true
                        assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                        isCheckingAssets = false
                        assetStatusSummaryState.value = AssetIntegrityChecker.getStatusSummary(context)
                    }
                }
            )

            SettingsDivider()

            ClickableSettingItem(
                title = androidStringResource(R.string.settings_reextract_runtime_title),
                subtitle = androidStringResource(R.string.settings_launcher_reextract_runtime_subtitle),
                icon = Icons.Default.RestartAlt,
                onClick = { showReExtractConfirmDialog = true }
            )
        }

        SettingsSection(title = androidStringResource(R.string.multiplayer_settings)) {
            SwitchSettingItem(
                title = androidStringResource(R.string.settings_launcher_enable_multiplayer_title),
                subtitle = androidStringResource(R.string.settings_launcher_enable_multiplayer_subtitle),
                icon = Icons.Default.Wifi,
                checked = uiState.multiplayerEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!uiState.multiplayerDisclaimerAccepted) {
                            showMultiplayerDisclaimerDialog = true
                        } else {
                            viewModel.onEvent(SettingsEvent.SetMultiplayerEnabled(true))
                        }
                    } else {
                        viewModel.onEvent(SettingsEvent.SetMultiplayerEnabled(false))
                    }
                }
            )
        }

        SettingsSection(title = androidStringResource(R.string.patch_management)) {
            ClickableSettingItem(
                title = androidStringResource(R.string.patch_management),
                subtitle = androidStringResource(R.string.patch_management_desc),
                icon = Icons.Default.Extension,
                onClick = { showPatchManagementDialog = true }
            )

            SettingsDivider()

            ClickableSettingItem(
                title = androidStringResource(R.string.force_reinstall_patches),
                subtitle = androidStringResource(R.string.force_reinstall_patches_desc),
                icon = Icons.Default.Refresh,
                onClick = { forceReinstallPatches(context) }
            )
        }
    }

    if (showPatchManagementDialog) {
        PatchManagementDialogCompose(
            onDismiss = { showPatchManagementDialog = false }
        )
    }

    if (showMultiplayerDisclaimerDialog) {
        MultiplayerDisclaimerDialog(
            onConfirm = {
                showMultiplayerDisclaimerDialog = false
                viewModel.onEvent(SettingsEvent.AcceptMultiplayerDisclaimer)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_multiplayer_enabled),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showMultiplayerDisclaimerDialog = false }
        )
    }

    if (showAssetCheckDialog) {
        AssetCheckResultDialog(
            isChecking = isCheckingAssets,
            result = assetCheckResult,
            onAutoFix = {
                assetCheckResult?.let { result ->
                    scope.launch {
                        isCheckingAssets = true
                        val fixResult = AssetIntegrityChecker.autoFix(context, result.issues) { _, _ -> }
                        isCheckingAssets = false

                        if (fixResult.success) {
                            Toast.makeText(context, fixResult.message, Toast.LENGTH_LONG).show()
                            if (fixResult.needsRestart) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_fix_restart_required),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            showAssetCheckDialog = false
                            assetCheckResult = AssetIntegrityChecker.checkIntegrity(context)
                            assetStatusSummaryState.value = AssetIntegrityChecker.getStatusSummary(context)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_fix_failed, fixResult.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = { showAssetCheckDialog = false }
        )
    }

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
            title = { Text(androidStringResource(R.string.settings_reextract_runtime_title)) },
            text = {
                Column {
                    Text(androidStringResource(R.string.settings_reextract_runtime_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidStringResource(R.string.settings_reextract_runtime_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isReExtracting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = androidStringResource(R.string.settings_reextract_runtime_in_progress),
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
                                val result = RuntimeLibraryLoader.forceReExtract(context) { _, _ -> }
                                isReExtracting = false
                                showReExtractConfirmDialog = false

                                if (result) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_reextract_runtime_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_reextract_runtime_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                assetStatusSummaryState.value = AssetIntegrityChecker.getStatusSummary(context)
                            } catch (e: Exception) {
                                isReExtracting = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_extract_failed_with_reason, e.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isReExtracting
                ) {
                    Text(androidStringResource(R.string.settings_confirm_extract))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReExtractConfirmDialog = false },
                    enabled = !isReExtracting
                ) {
                    Text(androidStringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DeveloperSettingsPane(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { exportUri ->
            scope.launch {
                exportLogs(context, exportUri)
            }
        }
    }

    with(uiState) {
        SettingsPaneColumn {
            SettingsSection(title = androidStringResource(R.string.settings_developer_logging_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_logging_enable_title),
                    subtitle = androidStringResource(R.string.settings_developer_logging_enable_subtitle),
                    icon = Icons.Default.Description,
                    checked = loggingEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetLoggingEnabled(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_verbose_logging_title),
                    subtitle = androidStringResource(R.string.settings_developer_verbose_logging_subtitle),
                    icon = Icons.Default.BugReport,
                    checked = verboseLogging,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetVerboseLogging(it)) }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_developer_view_logs_title),
                    subtitle = androidStringResource(R.string.settings_developer_view_logs_subtitle),
                    icon = Icons.Default.Visibility,
                    onClick = {
                        logs = loadLogs(context)
                        showLogViewerDialog = true
                    }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_developer_export_logs_title),
                    subtitle = androidStringResource(R.string.settings_developer_export_logs_subtitle),
                    icon = Icons.Default.Download,
                    onClick = { logExportLauncher.launch(buildLogFileName()) }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_developer_share_logs_title),
                    subtitle = androidStringResource(R.string.settings_developer_share_logs_subtitle),
                    icon = Icons.Default.Share,
                    onClick = {
                        scope.launch {
                            shareLogs(context)
                        }
                    }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_developer_performance_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.thread_affinity_big_core),
                    subtitle = androidStringResource(R.string.thread_affinity_big_core_desc),
                    icon = Icons.Default.Memory,
                    checked = bigCoreAffinityEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetBigCoreAffinity(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_kill_launcher_ui_title),
                    subtitle = androidStringResource(R.string.settings_developer_kill_launcher_ui_subtitle),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    checked = killLauncherUIEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetKillLauncherUI(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.low_latency_audio),
                    subtitle = androidStringResource(R.string.settings_game_low_latency_audio_subtitle),
                    icon = Icons.Default.Audiotrack,
                    checked = lowLatencyAudioEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetLowLatencyAudio(it)) }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_developer_dotnet_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_server_gc_title),
                    subtitle = androidStringResource(R.string.settings_developer_server_gc_subtitle),
                    icon = Icons.Default.Storage,
                    checked = serverGCEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetServerGC(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_concurrent_gc_title),
                    subtitle = androidStringResource(R.string.settings_developer_concurrent_gc_subtitle),
                    icon = Icons.Default.Sync,
                    checked = concurrentGCEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetConcurrentGC(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_tiered_compilation_title),
                    subtitle = androidStringResource(R.string.settings_developer_tiered_compilation_subtitle),
                    icon = Icons.Default.Layers,
                    checked = tieredCompilationEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetTieredCompilation(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_coreclr_compat_title),
                    subtitle = androidStringResource(R.string.settings_developer_coreclr_compat_subtitle),
                    icon = Icons.Default.Security,
                    checked = coreClrXiaomiCompatEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetCoreClrXiaomiCompat(it)) }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_developer_fna_section)) {
                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_map_buffer_range_title),
                    subtitle = androidStringResource(R.string.settings_developer_map_buffer_range_subtitle),
                    icon = Icons.Default.Speed,
                    checked = fnaMapBufferRangeOptEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetFnaMapBufferRangeOpt(it)) }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = androidStringResource(R.string.settings_developer_gl_perf_diag_title),
                    subtitle = androidStringResource(R.string.settings_developer_gl_perf_diag_subtitle),
                    icon = Icons.Default.Timeline,
                    checked = fnaGlPerfDiagnosticsEnabled,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.SetFnaGlPerfDiagnostics(it)) }
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_developer_maintenance_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_developer_clear_cache_title),
                    subtitle = androidStringResource(R.string.settings_developer_clear_cache_subtitle),
                    icon = Icons.Default.DeleteSweep,
                    onClick = { clearAppCache(context) }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.force_reinstall_patches),
                    subtitle = androidStringResource(R.string.force_reinstall_patches_desc),
                    icon = Icons.Default.Refresh,
                    onClick = { forceReinstallPatches(context) }
                )
            }
        }

        if (showLogViewerDialog) {
            LogViewerDialog(
                logs = logs,
                onExport = { logExportLauncher.launch(buildLogFileName()) },
                onClear = {
                    clearLogs(context)
                    logs = emptyList()
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_logs_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showLogViewerDialog = false }
            )
        }
    }
}

@Composable
private fun AboutSettingsPane(
    uiState: SettingsUiState,
    onCheckLauncherUpdate: () -> Unit
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var appInfoTapCount by rememberSaveable { mutableIntStateOf(0) }
    val communityLinks = listOf(
        SettingsLink(
            title = androidStringResource(R.string.about_discord_community),
            icon = Icons.Default.Forum,
            url = "https://discord.gg/cVkrRdffGp"
        ),
        SettingsLink(
            title = androidStringResource(R.string.about_qq_group),
            icon = Icons.Default.Group,
            url = "https://qm.qq.com/q/BWiPSj6wWQ"
        ),
        SettingsLink(
            title = androidStringResource(R.string.about_github),
            icon = Icons.Default.Code,
            url = "https://github.com/FireworkSky/RotatingartLauncher"
        )
    )
    val sponsorLinks = listOf(
        SettingsLink(
            title = androidStringResource(R.string.about_afdian_sponsor),
            icon = Icons.Default.Favorite,
            url = "https://afdian.com/a/RotatingartLauncher"
        ),
        SettingsLink(
            title = androidStringResource(R.string.about_patreon_sponsor),
            icon = Icons.Default.Star,
            url = "https://www.patreon.com/c/RotatingArtLauncher"
        )
    )
    val contributors = listOf(
        SettingsContributor(
            name = "FireworkSky",
            role = androidStringResource(R.string.about_project_author),
            githubUrl = "https://github.com/FireworkSky"
        ),
        SettingsContributor(
            name = "LaoSparrow",
            role = androidStringResource(R.string.about_core_developer),
            githubUrl = "https://github.com/LaoSparrow"
        )
    )

    with(uiState) {
        SettingsPaneColumn {
            SettingsSection(title = androidStringResource(R.string.settings_about_app_info_section)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextTapCount = appInfoTapCount + 1
                            if (nextTapCount >= APP_INFO_EASTER_EGG_TRIGGER_COUNT) {
                                appInfoTapCount = 0
                                openUrl(
                                    context,
                                    if (isChineseLanguage(context)) {
                                        APP_INFO_EASTER_EGG_ZH_URL
                                    } else {
                                        APP_INFO_EASTER_EGG_NON_ZH_URL
                                    }
                                )
                            } else {
                                appInfoTapCount = nextTapCount
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = androidStringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${androidStringResource(R.string.about_version_label)} $appVersion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (buildInfo.isNotEmpty()) {
                            Text(
                                text = "${androidStringResource(R.string.about_build_label)} $buildInfo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                SettingsDivider()

                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_about_check_update_title),
                    subtitle = androidStringResource(R.string.settings_about_check_update_subtitle),
                    icon = Icons.Default.Update,
                    onClick = onCheckLauncherUpdate
                )
            }

            SettingsSection(title = androidStringResource(R.string.settings_about_community_section)) {
                communityLinks.forEachIndexed { index, link ->
                    if (index > 0) {
                        SettingsDivider()
                    }
                    ClickableSettingItem(
                        title = link.title,
                        subtitle = androidStringResource(R.string.settings_about_community_subtitle),
                        icon = link.icon,
                        onClick = { openUrl(context, link.url) }
                    )
                }
            }

            SettingsSection(title = androidStringResource(R.string.settings_about_support_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_about_sponsor_wall_title),
                    subtitle = androidStringResource(R.string.settings_about_sponsor_wall_subtitle),
                    icon = Icons.Default.People,
                    onClick = { openSponsorsPage(context) }
                )

                sponsorLinks.forEach { link ->
                    SettingsDivider()
                    ClickableSettingItem(
                        title = link.title,
                        subtitle = androidStringResource(R.string.settings_about_become_sponsor_subtitle),
                        icon = link.icon,
                        onClick = { openUrl(context, link.url) }
                    )
                }
            }

            SettingsSection(title = androidStringResource(R.string.settings_about_contributors_section)) {
                contributors.forEachIndexed { index, contributor ->
                    if (index > 0) {
                        SettingsDivider()
                    }
                    ClickableSettingItem(
                        title = contributor.name,
                        subtitle = contributor.role,
                        icon = Icons.Default.Person,
                        onClick = { openUrl(context, contributor.githubUrl) }
                    )
                }
            }

            SettingsSection(title = androidStringResource(R.string.settings_about_open_source_section)) {
                ClickableSettingItem(
                    title = androidStringResource(R.string.settings_open_source_licenses),
                    subtitle = androidStringResource(R.string.settings_about_open_source_subtitle),
                    icon = Icons.Default.Description,
                    onClick = { showLicenseDialog = true }
                )
            }
        }

        if (showLicenseDialog) {
            LicenseDialog(onDismiss = { showLicenseDialog = false })
        }
    }
}

private data class SettingsLink(
    val title: String,
    val icon: ImageVector,
    val url: String
)

private data class SettingsContributor(
    val name: String,
    val role: String,
    val githubUrl: String
)

private val BACKGROUND_OPACITY_RANGE = 0f..100f
private const val BACKGROUND_OPACITY_STEP_COUNT = 9
private val VIDEO_PLAYBACK_SPEED_RANGE = 0.5f..2.0f
private const val VIDEO_PLAYBACK_SPEED_STEP_COUNT = 5
private val AUDIO_BUFFER_SIZE_OPTIONS: List<Int?> =
    listOf(null) + (4..10).map { 1 shl it }

private fun audioBufferSizeSliderRange(): ClosedFloatingPointRange<Float> =
    0f..AUDIO_BUFFER_SIZE_OPTIONS.lastIndex.toFloat()

private fun audioBufferSizeSliderSteps(): Int =
    (AUDIO_BUFFER_SIZE_OPTIONS.size - 2).coerceAtLeast(0)

private fun audioBufferSizeToSliderPosition(bufferSize: Int?): Float {
    val index = AUDIO_BUFFER_SIZE_OPTIONS.indexOf(bufferSize).takeIf { it >= 0 } ?: 0
    return index.toFloat()
}

private fun sliderPositionToAudioBufferSize(sliderValue: Float): Int? {
    val index = sliderValue.roundToInt().coerceIn(0, AUDIO_BUFFER_SIZE_OPTIONS.lastIndex)
    return AUDIO_BUFFER_SIZE_OPTIONS[index]
}

private const val APP_INFO_EASTER_EGG_TRIGGER_COUNT = 50
private const val APP_INFO_EASTER_EGG_NON_ZH_URL = "https://youtu.be/CB42Hz349JM"
private const val APP_INFO_EASTER_EGG_ZH_URL = "https://www.bilibili.com/video/BV19wHSe3E1v"
