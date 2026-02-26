package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 开发者设置状态
 */
data class DeveloperState(
    // 日志相关
    val loggingEnabled: Boolean = false,
    val verboseLogging: Boolean = false,
    
    // 性能相关
    val bigCoreAffinityEnabled: Boolean = false,
    val killLauncherUIEnabled: Boolean = false,
    val lowLatencyAudioEnabled: Boolean = false,
    
    // .NET 运行时
    val serverGCEnabled: Boolean = true,
    val concurrentGCEnabled: Boolean = true,
    val tieredCompilationEnabled: Boolean = true,
    val coreClrXiaomiCompatEnabled: Boolean = true,
    
    // FNA 优化
    val fnaMapBufferRangeOptEnabled: Boolean = false,
    val fnaGlPerfDiagnosticsEnabled: Boolean = false
)

/**
 * 开发者设置内容 - 跨平台
 */
@Composable
fun DeveloperSettingsContent(
    state: DeveloperState,
    // 日志回调
    onLoggingChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    // 性能回调
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onKillLauncherUIChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    // .NET 运行时回调
    onServerGCChange: (Boolean) -> Unit,
    onConcurrentGCChange: (Boolean) -> Unit,
    onTieredCompilationChange: (Boolean) -> Unit,
    onCoreClrXiaomiCompatChange: (Boolean) -> Unit,
    // FNA 回调
    onFnaMapBufferRangeOptChange: (Boolean) -> Unit,
    onFnaGlPerfDiagnosticsChange: (Boolean) -> Unit,
    // 其他
    onForceReinstallPatchesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 日志设置
        LoggingSection(
            loggingEnabled = state.loggingEnabled,
            verboseLogging = state.verboseLogging,
            onLoggingChange = onLoggingChange,
            onVerboseLoggingChange = onVerboseLoggingChange,
            onViewLogsClick = onViewLogsClick,
            onExportLogsClick = onExportLogsClick
        )

        // 性能设置
        PerformanceSection(
            bigCoreAffinityEnabled = state.bigCoreAffinityEnabled,
            killLauncherUIEnabled = state.killLauncherUIEnabled,
            lowLatencyAudioEnabled = state.lowLatencyAudioEnabled,
            onBigCoreAffinityChange = onBigCoreAffinityChange,
            onKillLauncherUIChange = onKillLauncherUIChange,
            onLowLatencyAudioChange = onLowLatencyAudioChange
        )

        // .NET 运行时设置
        DotNetRuntimeSection(
            serverGCEnabled = state.serverGCEnabled,
            concurrentGCEnabled = state.concurrentGCEnabled,
            tieredCompilationEnabled = state.tieredCompilationEnabled,
            coreClrXiaomiCompatEnabled = state.coreClrXiaomiCompatEnabled,
            onServerGCChange = onServerGCChange,
            onConcurrentGCChange = onConcurrentGCChange,
            onTieredCompilationChange = onTieredCompilationChange,
            onCoreClrXiaomiCompatChange = onCoreClrXiaomiCompatChange
        )

        // FNA 优化设置
        FnaOptimizationSection(
            mapBufferRangeOptEnabled = state.fnaMapBufferRangeOptEnabled,
            glPerfDiagnosticsEnabled = state.fnaGlPerfDiagnosticsEnabled,
            onMapBufferRangeOptChange = onFnaMapBufferRangeOptChange,
            onGlPerfDiagnosticsChange = onFnaGlPerfDiagnosticsChange
        )

        // 维护操作
        MaintenanceSection(
            onClearCacheClick = onClearCacheClick,
            onForceReinstallPatchesClick = onForceReinstallPatchesClick
        )
    }
}

@Composable
private fun LoggingSection(
    loggingEnabled: Boolean,
    verboseLogging: Boolean,
    onLoggingChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_developer_logging_section)) {
        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_logging_enable_title),
            subtitle = stringResource(Res.string.settings_developer_logging_enable_subtitle),
            icon = Icons.Default.Description,
            checked = loggingEnabled,
            onCheckedChange = onLoggingChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_verbose_logging_title),
            subtitle = stringResource(Res.string.settings_developer_verbose_logging_subtitle),
            icon = Icons.Default.BugReport,
            checked = verboseLogging,
            onCheckedChange = onVerboseLoggingChange
        )

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.settings_developer_view_logs_title),
            subtitle = stringResource(Res.string.settings_developer_view_logs_subtitle),
            icon = Icons.Default.Visibility,
            onClick = onViewLogsClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.settings_developer_export_logs_title),
            subtitle = stringResource(Res.string.settings_developer_export_logs_subtitle),
            icon = Icons.Default.Share,
            onClick = onExportLogsClick
        )
    }
}

@Composable
private fun PerformanceSection(
    bigCoreAffinityEnabled: Boolean,
    killLauncherUIEnabled: Boolean,
    lowLatencyAudioEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onKillLauncherUIChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_developer_performance_section)) {
        SwitchSettingItem(
            title = stringResource(Res.string.thread_affinity_big_core),
            subtitle = stringResource(Res.string.thread_affinity_big_core_desc),
            icon = Icons.Default.Memory,
            checked = bigCoreAffinityEnabled,
            onCheckedChange = onBigCoreAffinityChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_kill_launcher_ui_title),
            subtitle = stringResource(Res.string.settings_developer_kill_launcher_ui_subtitle),
            icon = Icons.Default.ExitToApp,
            checked = killLauncherUIEnabled,
            onCheckedChange = onKillLauncherUIChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.low_latency_audio),
            subtitle = stringResource(Res.string.settings_game_low_latency_audio_subtitle),
            icon = Icons.Default.Audiotrack,
            checked = lowLatencyAudioEnabled,
            onCheckedChange = onLowLatencyAudioChange
        )
    }
}

@Composable
private fun DotNetRuntimeSection(
    serverGCEnabled: Boolean,
    concurrentGCEnabled: Boolean,
    tieredCompilationEnabled: Boolean,
    coreClrXiaomiCompatEnabled: Boolean,
    onServerGCChange: (Boolean) -> Unit,
    onConcurrentGCChange: (Boolean) -> Unit,
    onTieredCompilationChange: (Boolean) -> Unit,
    onCoreClrXiaomiCompatChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_developer_dotnet_section)) {
        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_server_gc_title),
            subtitle = stringResource(Res.string.settings_developer_server_gc_subtitle),
            icon = Icons.Default.Storage,
            checked = serverGCEnabled,
            onCheckedChange = onServerGCChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_concurrent_gc_title),
            subtitle = stringResource(Res.string.settings_developer_concurrent_gc_subtitle),
            icon = Icons.Default.Sync,
            checked = concurrentGCEnabled,
            onCheckedChange = onConcurrentGCChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_tiered_compilation_title),
            subtitle = stringResource(Res.string.settings_developer_tiered_compilation_subtitle),
            icon = Icons.Default.Layers,
            checked = tieredCompilationEnabled,
            onCheckedChange = onTieredCompilationChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_coreclr_compat_title),
            subtitle = stringResource(Res.string.settings_developer_coreclr_compat_subtitle),
            icon = Icons.Default.Security,
            checked = coreClrXiaomiCompatEnabled,
            onCheckedChange = onCoreClrXiaomiCompatChange
        )
    }
}

@Composable
private fun FnaOptimizationSection(
    mapBufferRangeOptEnabled: Boolean,
    glPerfDiagnosticsEnabled: Boolean,
    onMapBufferRangeOptChange: (Boolean) -> Unit,
    onGlPerfDiagnosticsChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_developer_fna_section)) {
        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_map_buffer_range_title),
            subtitle = stringResource(Res.string.settings_developer_map_buffer_range_subtitle),
            icon = Icons.Default.Speed,
            checked = mapBufferRangeOptEnabled,
            onCheckedChange = onMapBufferRangeOptChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = stringResource(Res.string.settings_developer_gl_perf_diag_title),
            subtitle = stringResource(Res.string.settings_developer_gl_perf_diag_subtitle),
            icon = Icons.Default.Timeline,
            checked = glPerfDiagnosticsEnabled,
            onCheckedChange = onGlPerfDiagnosticsChange
        )
    }
}

@Composable
private fun MaintenanceSection(
    onClearCacheClick: () -> Unit,
    onForceReinstallPatchesClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_developer_maintenance_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.settings_developer_clear_cache_title),
            subtitle = stringResource(Res.string.settings_developer_clear_cache_subtitle),
            icon = Icons.Default.DeleteSweep,
            onClick = onClearCacheClick
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
