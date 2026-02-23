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
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    SettingsSection(title = stringResource(Res.string.settings_launcher_assets_section)) {
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
            title = stringResource(Res.string.settings_launcher_check_integrity_title),
            subtitle = stringResource(Res.string.settings_launcher_check_integrity_subtitle),
            icon = Icons.Default.VerifiedUser,
            onClick = onCheckIntegrityClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.settings_reextract_runtime_title),
            subtitle = stringResource(Res.string.settings_launcher_reextract_runtime_subtitle),
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
    SettingsSection(title = stringResource(Res.string.multiplayer_settings)) {
        SwitchSettingItem(
            title = stringResource(Res.string.settings_launcher_enable_multiplayer_title),
            subtitle = stringResource(Res.string.settings_launcher_enable_multiplayer_subtitle),
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
    SettingsSection(title = stringResource(Res.string.patch_management)) {
        ClickableSettingItem(
            title = stringResource(Res.string.patch_management),
            subtitle = stringResource(Res.string.patch_management_desc),
            icon = Icons.Default.Extension,
            onClick = onPatchManagementClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.force_reinstall_patches),
            subtitle = stringResource(Res.string.force_reinstall_patches_desc),
            icon = Icons.Default.Refresh,
            onClick = onForceReinstallPatchesClick
        )
    }
}
