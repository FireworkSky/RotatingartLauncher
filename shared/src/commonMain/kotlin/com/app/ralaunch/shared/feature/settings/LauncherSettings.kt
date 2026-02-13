package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 启动器设置状态
 */
data class LauncherState(
    val multiplayerEnabled: Boolean = false, // 联机功能开关
    val assetStatusSummary: String = ""      // 资产状态摘要
)

/**
 * 启动器设置内容 - 跨平台
 */
@Composable
fun LauncherSettingsContent(
    state: LauncherState,
    onPatchManagementClick: () -> Unit,
    onForceReinstallPatchesClick: () -> Unit,
    onMultiplayerToggle: (Boolean) -> Unit,
    onCheckIntegrityClick: () -> Unit,
    onReExtractRuntimeLibsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 资产管理
        AssetSection(
            assetStatusSummary = state.assetStatusSummary,
            onCheckIntegrityClick = onCheckIntegrityClick,
            onReExtractRuntimeLibsClick = onReExtractRuntimeLibsClick
        )

        // 联机设置
        MultiplayerSection(
            multiplayerEnabled = state.multiplayerEnabled,
            onMultiplayerToggle = onMultiplayerToggle
        )

        // 补丁管理
        PatchSection(
            onPatchManagementClick = onPatchManagementClick,
            onForceReinstallPatchesClick = onForceReinstallPatchesClick
        )
    }
}

@Composable
private fun AssetSection(
    assetStatusSummary: String,
    onCheckIntegrityClick: () -> Unit,
    onReExtractRuntimeLibsClick: () -> Unit
) {
    SettingsSection(title = "资产管理") {
        // 资产状态摘要
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
            title = "检查资产完整性",
            subtitle = "检查库文件和资源是否完整",
            icon = Icons.Default.VerifiedUser,
            onClick = onCheckIntegrityClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "重新解压运行时库",
            subtitle = "如果游戏启动失败，尝试重新解压",
            icon = Icons.Default.RestartAlt,
            onClick = onReExtractRuntimeLibsClick
        )
    }
}

@Composable
private fun MultiplayerSection(
    multiplayerEnabled: Boolean,
    onMultiplayerToggle: (Boolean) -> Unit
) {
    SettingsSection(title = "联机功能") {
        SwitchSettingItem(
            title = "启用联机功能",
            subtitle = "开启后可在游戏内使用 P2P 联机功能",
            icon = Icons.Default.Wifi,
            checked = multiplayerEnabled,
            onCheckedChange = onMultiplayerToggle
        )
    }
}

@Composable
private fun PatchSection(
    onPatchManagementClick: () -> Unit,
    onForceReinstallPatchesClick: () -> Unit
) {
    SettingsSection(title = "补丁管理") {
        ClickableSettingItem(
            title = "管理补丁",
            subtitle = "查看、导入或删除游戏补丁",
            icon = Icons.Default.Extension,
            onClick = onPatchManagementClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "强制重装补丁",
            subtitle = "下次启动游戏时重新安装所有补丁",
            icon = Icons.Default.Refresh,
            onClick = onForceReinstallPatchesClick
        )
    }
}
