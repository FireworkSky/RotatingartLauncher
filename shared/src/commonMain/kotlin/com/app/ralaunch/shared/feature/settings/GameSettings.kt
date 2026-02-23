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
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 游戏设置状态
 */
data class GameState(
    val bigCoreAffinityEnabled: Boolean = false, // 大核亲和性
    val lowLatencyAudioEnabled: Boolean = false, // 低延迟音频
    val ralAudioBufferSize: Int? = null,         // RAL_AUDIO_BUFFERSIZE
    val rendererDisplayName: String = "Native OpenGL ES 3", // 渲染器显示名称
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
    onRalAudioBufferSizeChange: (Int?) -> Unit,
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
            ralAudioBufferSize = state.ralAudioBufferSize,
            onBigCoreAffinityChange = onBigCoreAffinityChange,
            onLowLatencyAudioChange = onLowLatencyAudioChange,
            onRalAudioBufferSizeChange = onRalAudioBufferSizeChange
        )

        // 渲染设置
        RendererSection(
            rendererDisplayName = state.rendererDisplayName,
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
    ralAudioBufferSize: Int?,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    onRalAudioBufferSizeChange: (Int?) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_game_performance_section)) {
        SwitchSettingItem(
            title = stringResource(Res.string.thread_affinity_big_core),
            subtitle = stringResource(Res.string.thread_affinity_big_core_desc),
            icon = Icons.Default.Memory,
            checked = bigCoreAffinityEnabled,
            onCheckedChange = onBigCoreAffinityChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.low_latency_audio),
            subtitle = stringResource(Res.string.settings_game_low_latency_audio_subtitle),
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            checked = lowLatencyAudioEnabled,
            onCheckedChange = onLowLatencyAudioChange
        )

        SettingsDivider()

        SliderSettingItem(
            title = stringResource(Res.string.settings_game_audio_buffer_title),
            subtitle = stringResource(Res.string.settings_game_audio_buffer_subtitle),
            icon = Icons.Default.Tune,
            value = audioBufferSizeToSliderPosition(ralAudioBufferSize),
            valueRange = 0f..7f,
            steps = 6,
            valueLabel = ralAudioBufferSize?.toString() ?: stringResource(Res.string.common_auto),
            onValueChange = { sliderValue ->
                onRalAudioBufferSizeChange(sliderPositionToAudioBufferSize(sliderValue))
            }
        )
    }
}

private val AUDIO_BUFFER_SIZE_OPTIONS: List<Int?> =
    listOf(null) + (4..10).map { 1 shl it }

private fun audioBufferSizeToSliderPosition(bufferSize: Int?): Float {
    val index = AUDIO_BUFFER_SIZE_OPTIONS.indexOf(bufferSize).takeIf { it >= 0 } ?: 0
    return index.toFloat()
}

private fun sliderPositionToAudioBufferSize(sliderValue: Float): Int? {
    val index = sliderValue.roundToInt().coerceIn(0, AUDIO_BUFFER_SIZE_OPTIONS.lastIndex)
    return AUDIO_BUFFER_SIZE_OPTIONS[index]
}

@Composable
private fun RendererSection(
    rendererDisplayName: String,
    onRendererClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_game_renderer_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.renderer_title),
            subtitle = stringResource(Res.string.renderer_desc),
            value = rendererDisplayName,
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
    val qualityNames = listOf(
        stringResource(Res.string.settings_quality_high),
        stringResource(Res.string.settings_quality_medium),
        stringResource(Res.string.settings_quality_low)
    )
    val fpsOptions = listOf(
        0 to stringResource(Res.string.settings_fps_unlimited),
        30 to stringResource(Res.string.settings_fps_30),
        45 to stringResource(Res.string.settings_fps_45),
        60 to stringResource(Res.string.settings_fps_60)
    )
    val currentFpsName = fpsOptions.find { it.first == targetFps }?.second
        ?: stringResource(Res.string.settings_fps_unlimited)

    SettingsSection(title = stringResource(Res.string.settings_game_quality_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.settings_game_quality_preset_title),
            subtitle = stringResource(Res.string.settings_game_quality_preset_subtitle),
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
            title = stringResource(Res.string.settings_game_shader_low_precision_title),
            subtitle = stringResource(Res.string.settings_game_shader_low_precision_subtitle),
            icon = Icons.Default.FilterAlt,
            checked = shaderLowPrecision,
            onCheckedChange = onShaderLowPrecisionChange
        )

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.settings_game_fps_limit_title),
            subtitle = stringResource(Res.string.settings_game_fps_limit_subtitle),
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
