package com.app.ralaunch.shared.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 外观设置内容 - 跨平台
 */
@Composable
fun AppearanceSettingsContent(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    themeColor: Int,
    onThemeColorClick: () -> Unit,
    backgroundType: Int,
    onBackgroundTypeChange: (Int) -> Unit,
    onSelectImageClick: () -> Unit,
    onSelectVideoClick: () -> Unit,
    language: String,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 可本地化文本
    themeTitle: String = "主题",
    themeModeTitle: String = "主题模式",
    themeModeSubtitle: String = "选择浅色、深色或跟随系统",
    themeColorTitle: String = "主题色",
    themeColorSubtitle: String = "自定义应用主色调",
    backgroundTitle: String = "背景",
    backgroundTypeTitle: String = "背景类型",
    backgroundTypeSubtitle: String = "选择背景显示方式",
    selectImageTitle: String = "选择背景图片",
    selectImageSubtitle: String = "从相册选择图片作为背景",
    selectVideoTitle: String = "选择背景视频",
    selectVideoSubtitle: String = "选择视频作为动态背景",
    languageTitle: String = "语言",
    appLanguageTitle: String = "应用语言",
    appLanguageSubtitle: String = "更改应用显示语言",
    themeModeValues: List<String> = listOf("跟随系统", "深色", "浅色"),
    backgroundTypeValues: List<String> = listOf("默认", "图片", "视频")
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 主题设置
        item {
            SettingsSection(title = themeTitle) {
                ClickableSettingItem(
                    title = themeModeTitle,
                    subtitle = themeModeSubtitle,
                    value = themeModeValues.getOrElse(themeMode) { themeModeValues[0] },
                    icon = Icons.Default.DarkMode,
                    onClick = {
                        val nextMode = (themeMode + 1) % 3
                        onThemeModeChange(nextMode)
                    }
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = themeColorTitle,
                    subtitle = themeColorSubtitle,
                    icon = Icons.Default.Palette,
                    onClick = onThemeColorClick
                )
            }
        }

        // 背景设置
        item {
            SettingsSection(title = backgroundTitle) {
                ClickableSettingItem(
                    title = backgroundTypeTitle,
                    subtitle = backgroundTypeSubtitle,
                    value = backgroundTypeValues.getOrElse(backgroundType) { backgroundTypeValues[0] },
                    icon = Icons.Default.Image,
                    onClick = {
                        val nextType = (backgroundType + 1) % 3
                        onBackgroundTypeChange(nextType)
                    }
                )

                if (backgroundType == 1) {
                    SettingsDivider()
                    ClickableSettingItem(
                        title = selectImageTitle,
                        subtitle = selectImageSubtitle,
                        icon = Icons.Default.Photo,
                        onClick = onSelectImageClick
                    )
                }

                if (backgroundType == 2) {
                    SettingsDivider()
                    ClickableSettingItem(
                        title = selectVideoTitle,
                        subtitle = selectVideoSubtitle,
                        icon = Icons.Default.VideoLibrary,
                        onClick = onSelectVideoClick
                    )
                }
            }
        }

        // 语言设置
        item {
            SettingsSection(title = languageTitle) {
                ClickableSettingItem(
                    title = appLanguageTitle,
                    subtitle = appLanguageSubtitle,
                    value = language,
                    icon = Icons.Default.Language,
                    onClick = onLanguageClick
                )
            }
        }
    }
}

/**
 * 控制设置内容 - 跨平台
 */
@Composable
fun ControlsSettingsContent(
    touchMultitouchEnabled: Boolean,
    onTouchMultitouchChange: (Boolean) -> Unit,
    mouseRightStickEnabled: Boolean,
    onMouseRightStickChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    // 可本地化文本
    touchTitle: String = "触控",
    multitouchTitle: String = "触控多点触摸",
    multitouchSubtitle: String = "启用后支持多点触摸输入",
    mouseRightStickTitle: String = "鼠标右摇杆",
    mouseRightStickSubtitle: String = "将鼠标移动映射到右摇杆",
    vibrationTitle: String = "振动",
    enableVibrationTitle: String = "启用振动",
    enableVibrationSubtitle: String = "控制按钮触发振动反馈",
    vibrationStrengthTitle: String = "振动强度",
    vibrationStrengthSubtitle: String = "调整振动反馈的强度"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 触控设置
        item {
            SettingsSection(title = touchTitle) {
                SwitchSettingItem(
                    title = multitouchTitle,
                    subtitle = multitouchSubtitle,
                    icon = Icons.Default.TouchApp,
                    checked = touchMultitouchEnabled,
                    onCheckedChange = onTouchMultitouchChange
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = mouseRightStickTitle,
                    subtitle = mouseRightStickSubtitle,
                    icon = Icons.Default.Mouse,
                    checked = mouseRightStickEnabled,
                    onCheckedChange = onMouseRightStickChange
                )
            }
        }

        // 振动设置
        item {
            SettingsSection(title = vibrationTitle) {
                SwitchSettingItem(
                    title = enableVibrationTitle,
                    subtitle = enableVibrationSubtitle,
                    icon = Icons.Default.Vibration,
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationChange
                )

                if (vibrationEnabled) {
                    SettingsDivider()
                    SliderSettingItem(
                        title = vibrationStrengthTitle,
                        subtitle = vibrationStrengthSubtitle,
                        value = vibrationStrength,
                        valueRange = 0f..1f,
                        valueLabel = "${(vibrationStrength * 100).toInt()}%",
                        onValueChange = onVibrationStrengthChange
                    )
                }
            }
        }
    }
}

/**
 * 游戏设置内容 - 跨平台
 */
@Composable
fun GameSettingsContent(
    bigCoreAffinityEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    lowLatencyAudioEnabled: Boolean,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    rendererType: String,
    onRendererClick: () -> Unit,
    // 画质设置
    qualityLevel: Int = 0,
    onQualityLevelChange: (Int) -> Unit = {},
    shaderLowPrecision: Boolean = false,
    onShaderLowPrecisionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    // 可本地化文本
    performanceTitle: String = "性能",
    bigCoreTitle: String = "大核亲和性",
    bigCoreSubtitle: String = "将游戏线程绑定到高性能核心",
    lowLatencyTitle: String = "低延迟音频",
    lowLatencySubtitle: String = "启用 AAudio 低延迟模式",
    renderTitle: String = "渲染",
    rendererTitle: String = "渲染器",
    rendererSubtitle: String = "选择图形渲染后端",
    // 画质设置文本
    qualityTitle: String = "画质",
    qualityLevelTitle: String = "画质预设",
    qualityLevelSubtitle: String = "选择画质等级，低画质可提高性能",
    shaderPrecisionTitle: String = "低精度着色器",
    shaderPrecisionSubtitle: String = "降低 Shader 精度以提升性能"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 性能设置
        item {
            SettingsSection(title = performanceTitle) {
                SwitchSettingItem(
                    title = bigCoreTitle,
                    subtitle = bigCoreSubtitle,
                    icon = Icons.Default.Memory,
                    checked = bigCoreAffinityEnabled,
                    onCheckedChange = onBigCoreAffinityChange
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = lowLatencyTitle,
                    subtitle = lowLatencySubtitle,
                    icon = Icons.Default.VolumeUp,
                    checked = lowLatencyAudioEnabled,
                    onCheckedChange = onLowLatencyAudioChange
                )
            }
        }

        // 渲染设置
        item {
            SettingsSection(title = renderTitle) {
                ClickableSettingItem(
                    title = rendererTitle,
                    subtitle = rendererSubtitle,
                    value = rendererType,
                    icon = Icons.Default.Tv,
                    onClick = onRendererClick
                )
            }
        }

        // 画质设置
        item {
            val qualityNames = listOf("高画质", "中画质", "低画质")
            SettingsSection(title = qualityTitle) {
                ClickableSettingItem(
                    title = qualityLevelTitle,
                    subtitle = qualityLevelSubtitle,
                    value = qualityNames.getOrElse(qualityLevel) { qualityNames[0] },
                    icon = Icons.Default.Tune,
                    onClick = {
                        val nextLevel = (qualityLevel + 1) % 3
                        onQualityLevelChange(nextLevel)
                    }
                )

                SettingsDivider()

                SwitchSettingItem(
                    title = shaderPrecisionTitle,
                    subtitle = shaderPrecisionSubtitle,
                    icon = Icons.Default.FilterAlt,
                    checked = shaderLowPrecision,
                    onCheckedChange = onShaderLowPrecisionChange
                )
            }
        }
    }
}

/**
 * 启动器设置内容 - 跨平台
 */
@Composable
fun LauncherSettingsContent(
    onPatchManagementClick: () -> Unit,
    onForceReinstallPatchesClick: () -> Unit,
    // 联机设置
    multiplayerEnabled: Boolean = false,
    onMultiplayerToggle: (Boolean) -> Unit = {},
    // 资产完整性检查
    onCheckIntegrityClick: () -> Unit = {},
    onReExtractRuntimeLibsClick: () -> Unit = {},
    assetStatusSummary: String = "",
    modifier: Modifier = Modifier,
    // 可本地化文本
    patchTitle: String = "补丁管理",
    managePatchesTitle: String = "管理补丁",
    managePatchesSubtitle: String = "查看、导入或删除游戏补丁",
    forceReinstallTitle: String = "强制重装补丁",
    forceReinstallSubtitle: String = "下次启动游戏时重新安装所有补丁",
    // 联机设置文本
    multiplayerTitle: String = "联机功能",
    multiplayerToggleTitle: String = "启用联机功能",
    multiplayerToggleSubtitle: String = "开启后可在游戏内使用 P2P 联机功能",
    // 资产检查文本
    assetTitle: String = "资产管理",
    checkIntegrityTitle: String = "检查资产完整性",
    checkIntegritySubtitle: String = "检查库文件和资源是否完整",
    reExtractTitle: String = "重新解压运行时库",
    reExtractSubtitle: String = "如果游戏启动失败，尝试重新解压"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 资产管理
        item {
            SettingsSection(title = assetTitle) {
                // 资产状态摘要
                if (assetStatusSummary.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.material3.MaterialTheme.shapes.small
                    ) {
                        androidx.compose.material3.Text(
                            text = assetStatusSummary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                ClickableSettingItem(
                    title = checkIntegrityTitle,
                    subtitle = checkIntegritySubtitle,
                    icon = Icons.Default.VerifiedUser,
                    onClick = onCheckIntegrityClick
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = reExtractTitle,
                    subtitle = reExtractSubtitle,
                    icon = Icons.Default.RestartAlt,
                    onClick = onReExtractRuntimeLibsClick
                )
            }
        }

        // 联机设置
        item {
            SettingsSection(title = multiplayerTitle) {
                SwitchSettingItem(
                    title = multiplayerToggleTitle,
                    subtitle = multiplayerToggleSubtitle,
                    icon = Icons.Default.Wifi,
                    checked = multiplayerEnabled,
                    onCheckedChange = onMultiplayerToggle
                )
            }
        }

        // 补丁管理
        item {
            SettingsSection(title = patchTitle) {
                ClickableSettingItem(
                    title = managePatchesTitle,
                    subtitle = managePatchesSubtitle,
                    icon = Icons.Default.Extension,
                    onClick = onPatchManagementClick
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = forceReinstallTitle,
                    subtitle = forceReinstallSubtitle,
                    icon = Icons.Default.Refresh,
                    onClick = onForceReinstallPatchesClick
                )
            }
        }
    }
}

/**
 * 开发者设置内容 - 跨平台
 */
@Composable
fun DeveloperSettingsContent(
    loggingEnabled: Boolean,
    onLoggingChange: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 可本地化文本
    logsTitle: String = "日志",
    enableLoggingTitle: String = "启用日志",
    enableLoggingSubtitle: String = "记录详细的运行日志",
    viewLogsTitle: String = "查看日志",
    viewLogsSubtitle: String = "查看最近的运行日志",
    exportLogsTitle: String = "导出日志",
    exportLogsSubtitle: String = "导出日志文件用于问题反馈",
    cacheTitle: String = "缓存",
    clearCacheTitle: String = "清除缓存",
    clearCacheSubtitle: String = "清理应用缓存数据"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 日志设置
        item {
            SettingsSection(title = logsTitle) {
                SwitchSettingItem(
                    title = enableLoggingTitle,
                    subtitle = enableLoggingSubtitle,
                    icon = Icons.Default.BugReport,
                    checked = loggingEnabled,
                    onCheckedChange = onLoggingChange
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = viewLogsTitle,
                    subtitle = viewLogsSubtitle,
                    icon = Icons.Default.Description,
                    onClick = onViewLogsClick
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = exportLogsTitle,
                    subtitle = exportLogsSubtitle,
                    icon = Icons.Default.Upload,
                    onClick = onExportLogsClick
                )
            }
        }

        // 缓存设置
        item {
            SettingsSection(title = cacheTitle) {
                ClickableSettingItem(
                    title = clearCacheTitle,
                    subtitle = clearCacheSubtitle,
                    icon = Icons.Default.Delete,
                    onClick = onClearCacheClick
                )
            }
        }
    }
}

/**
 * 关于页面内容 - 跨平台
 */
@Composable
fun AboutSettingsContent(
    appVersion: String,
    buildInfo: String,
    onGitHubClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 可本地化文本
    versionTitle: String = "版本",
    appVersionTitle: String = "应用版本",
    buildInfoTitle: String = "构建信息",
    linksTitle: String = "链接",
    githubTitle: String = "GitHub",
    githubSubtitle: String = "访问项目源代码",
    licenseTitle: String = "开源许可",
    licenseSubtitle: String = "查看开源库许可信息"
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 版本信息
        item {
            SettingsSection(title = versionTitle) {
                ClickableSettingItem(
                    title = appVersionTitle,
                    value = appVersion,
                    icon = Icons.Default.Info,
                    onClick = onCheckUpdateClick
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = buildInfoTitle,
                    value = buildInfo,
                    icon = Icons.Default.Build,
                    onClick = {}
                )
            }
        }

        // 链接
        item {
            SettingsSection(title = linksTitle) {
                ClickableSettingItem(
                    title = githubTitle,
                    subtitle = githubSubtitle,
                    icon = Icons.Default.Code,
                    onClick = onGitHubClick
                )

                SettingsDivider()

                ClickableSettingItem(
                    title = licenseTitle,
                    subtitle = licenseSubtitle,
                    icon = Icons.Default.Policy,
                    onClick = onLicenseClick
                )
            }
        }
    }
}
