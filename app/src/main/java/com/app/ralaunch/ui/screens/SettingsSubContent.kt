package com.app.ralaunch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.ui.screens.settings.*

/**
 * 控制设置子内容
 */
@Composable
internal fun ControlsSettingsContent(
    touchMultitouchEnabled: Boolean,
    onTouchMultitouchChange: (Boolean) -> Unit,
    mouseRightStickEnabled: Boolean,
    onMouseRightStickChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "触控") {
            SwitchSettingItem(
                title = "多点触控",
                subtitle = "允许同时多个触控点",
                checked = touchMultitouchEnabled,
                onCheckedChange = onTouchMultitouchChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = "鼠标右摇杆",
                subtitle = "鼠标移动映射到右摇杆",
                checked = mouseRightStickEnabled,
                onCheckedChange = onMouseRightStickChange
            )
        }

        SettingsSection(title = "震动") {
            SwitchSettingItem(
                title = "启用震动",
                subtitle = "控制器震动反馈",
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )

            if (vibrationEnabled) {
                SettingsDivider()

                SliderSettingItem(
                    title = "震动强度",
                    value = vibrationStrength,
                    valueRange = 0f..1f,
                    valueLabel = "${(vibrationStrength * 100).toInt()}%",
                    onValueChange = onVibrationStrengthChange
                )
            }
        }
    }
}

/**
 * 游戏设置子内容
 */
@Composable
internal fun GameSettingsContent(
    bigCoreAffinityEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    lowLatencyAudioEnabled: Boolean,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    rendererType: String,
    onRendererClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "性能") {
            SwitchSettingItem(
                title = "大核心亲和性",
                subtitle = "将游戏线程绑定到高性能核心",
                checked = bigCoreAffinityEnabled,
                onCheckedChange = onBigCoreAffinityChange
            )

            SettingsDivider()

            SwitchSettingItem(
                title = "低延迟音频",
                subtitle = "降低音频延迟，可能增加功耗",
                checked = lowLatencyAudioEnabled,
                onCheckedChange = onLowLatencyAudioChange
            )
        }

        SettingsSection(title = "渲染") {
            ClickableSettingItem(
                title = "渲染器",
                subtitle = "选择图形渲染后端",
                value = rendererType,
                onClick = onRendererClick
            )
        }
    }
}
