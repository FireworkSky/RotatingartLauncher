package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 游戏设置状态
 */
data class GameState(
    val bigCoreAffinityEnabled: Boolean = false, // 大核亲和性
    val lowLatencyAudioEnabled: Boolean = false, // 低延迟音频
    val rendererType: String = "auto",           // 渲染器类型
    val qualityLevel: Int = 0,                   // 0=高画质, 1=中画质, 2=低画质
    val shaderLowPrecision: Boolean = false,     // 低精度着色器
    val targetFps: Int = 0                       // 帧率限制, 0=无限制
)

/**
 * 游戏设置内容 - 跨平台
 */
@Composable
fun GameSettingsContent(
    state: GameState,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    onRendererClick: () -> Unit,
    onQualityLevelChange: (Int) -> Unit,
    onShaderLowPrecisionChange: (Boolean) -> Unit,
    onTargetFpsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 性能设置
        PerformanceSection(
            bigCoreAffinityEnabled = state.bigCoreAffinityEnabled,
            lowLatencyAudioEnabled = state.lowLatencyAudioEnabled,
            onBigCoreAffinityChange = onBigCoreAffinityChange,
            onLowLatencyAudioChange = onLowLatencyAudioChange
        )

        // 渲染设置
        RendererSection(
            rendererType = state.rendererType,
            onRendererClick = onRendererClick
        )

        // 画质设置
        QualitySection(
            qualityLevel = state.qualityLevel,
            shaderLowPrecision = state.shaderLowPrecision,
            targetFps = state.targetFps,
            onQualityLevelChange = onQualityLevelChange,
            onShaderLowPrecisionChange = onShaderLowPrecisionChange,
            onTargetFpsChange = onTargetFpsChange
        )
    }
}

@Composable
private fun PerformanceSection(
    bigCoreAffinityEnabled: Boolean,
    lowLatencyAudioEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit
) {
    SettingsSection(title = "性能") {
        SwitchSettingItem(
            title = "大核亲和性",
            subtitle = "将游戏线程绑定到高性能核心",
            icon = Icons.Default.Memory,
            checked = bigCoreAffinityEnabled,
            onCheckedChange = onBigCoreAffinityChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "低延迟音频",
            subtitle = "启用 AAudio 低延迟模式",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            checked = lowLatencyAudioEnabled,
            onCheckedChange = onLowLatencyAudioChange
        )
    }
}

@Composable
private fun RendererSection(
    rendererType: String,
    onRendererClick: () -> Unit
) {
    SettingsSection(title = "渲染") {
        ClickableSettingItem(
            title = "渲染器",
            subtitle = "选择图形渲染后端",
            value = rendererType,
            icon = Icons.Default.Tv,
            onClick = onRendererClick
        )
    }
}

@Composable
private fun QualitySection(
    qualityLevel: Int,
    shaderLowPrecision: Boolean,
    targetFps: Int,
    onQualityLevelChange: (Int) -> Unit,
    onShaderLowPrecisionChange: (Boolean) -> Unit,
    onTargetFpsChange: (Int) -> Unit
) {
    val qualityNames = listOf("高画质", "中画质", "低画质")
    val fpsOptions = listOf(0 to "无限制", 30 to "30 FPS", 45 to "45 FPS", 60 to "60 FPS")
    val currentFpsName = fpsOptions.find { it.first == targetFps }?.second ?: "无限制"

    SettingsSection(title = "画质") {
        ClickableSettingItem(
            title = "画质预设",
            subtitle = "选择画质等级，低画质可提高性能",
            value = qualityNames.getOrElse(qualityLevel) { qualityNames[0] },
            icon = Icons.Default.Tune,
            onClick = {
                // 循环切换画质预设
                val nextLevel = (qualityLevel + 1) % qualityNames.size
                onQualityLevelChange(nextLevel)
            }
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "低精度着色器",
            subtitle = "降低 Shader 精度以提升性能",
            icon = Icons.Default.FilterAlt,
            checked = shaderLowPrecision,
            onCheckedChange = onShaderLowPrecisionChange
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "帧率限制",
            subtitle = "限制游戏最大帧率，可节省电量",
            value = currentFpsName,
            icon = Icons.Default.Speed,
            onClick = {
                // 循环切换帧率: 无限制 -> 30 -> 45 -> 60 -> 无限制
                val currentIndex = fpsOptions.indexOfFirst { it.first == targetFps }
                val nextIndex = (currentIndex + 1) % fpsOptions.size
                onTargetFpsChange(fpsOptions[nextIndex].first)
            }
        )
    }
}
