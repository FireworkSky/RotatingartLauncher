package com.app.ralaunch.feature.init

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.core.common.PermissionManager
import com.app.ralaunch.shared.core.model.ui.ComponentState
import com.app.ralaunch.shared.core.model.ui.InitUiState
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.theme.RaLaunchTheme

enum class InitPage { LEGAL, SETUP }

private data class InitPalette(
    val background: Brush,
    val card: Color,
    val cardSoft: Color,
    val border: Color,
    val primaryAction: Color,
    val primaryActionText: Color,
    val softAction: Color,
    val softActionText: Color,
    val overallProgress: Color,
    val overallProgressTrack: Color,
    val componentProgress: Color,
    val componentProgressTrack: Color
)

@Composable
private fun rememberInitPalette(): InitPalette {
    val cs = MaterialTheme.colorScheme
    return InitPalette(
        background = Brush.linearGradient(
            listOf(cs.surfaceContainerLowest, cs.surfaceContainerLow, cs.surfaceContainer)
        ),
        card = cs.surface,
        cardSoft = cs.surfaceContainerLow,
        border = cs.outlineVariant,
        primaryAction = cs.primary,
        primaryActionText = cs.onPrimary,
        softAction = cs.secondaryContainer,
        softActionText = cs.onSecondaryContainer,
        overallProgress = cs.primary,
        overallProgressTrack = cs.primaryContainer,
        componentProgress = cs.tertiary,
        componentProgressTrack = cs.surfaceVariant
    )
}

@Composable
fun InitializationScreen(
    permissionManager: PermissionManager,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onExtract: (List<ComponentState>, (Int, Int, Boolean, String) -> Unit) -> Unit,
    prefs: SharedPreferences,
    context: Context
) {
    var currentPage by remember { mutableStateOf(InitPage.LEGAL) }
    var uiState by remember { mutableStateOf(InitUiState()) }
    val palette = rememberInitPalette()
    val appVersionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("").ifBlank { "Unknown" }
    }

    LaunchedEffect(Unit) {
        uiState = uiState.copy(
            components = listOf(
                ComponentState(
                    name = "dotnet",
                    description = context.getString(R.string.init_component_dotnet_desc),
                    fileName = "dotnet.tar.xz",
                    needsExtraction = true
                )
            ),
            hasPermissions = permissionManager.hasRequiredPermissions()
        )
        if (prefs.getBoolean(AppConstants.InitKeys.LEGAL_AGREED, false)) currentPage = InitPage.SETUP
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "init_page_transition"
        ) { page ->
            when (page) {
                InitPage.LEGAL -> LegalPage(
                    palette = palette,
                    appVersionName = appVersionName,
                    onAccept = {
                        prefs.edit().putBoolean(AppConstants.InitKeys.LEGAL_AGREED, true).apply()
                        currentPage = InitPage.SETUP
                    },
                    onDecline = onExit,
                    context = context
                )
                InitPage.SETUP -> SetupPage(
                    palette = palette,
                    uiState = uiState,
                    onRequestPermissions = {
                        if (permissionManager.hasRequiredPermissions()) {
                            prefs.edit().putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true).apply()
                            uiState = uiState.copy(hasPermissions = true)
                        } else {
                            permissionManager.requestRequiredPermissions(object : PermissionManager.PermissionCallback {
                                override fun onPermissionsGranted() {
                                    prefs.edit().putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true).apply()
                                    uiState = uiState.copy(hasPermissions = true)
                                }
                                override fun onPermissionsDenied() {
                                    Toast.makeText(context, context.getString(R.string.init_permissions_denied), Toast.LENGTH_LONG).show()
                                }
                            })
                        }
                    },
                    onStartExtraction = {
                        if (uiState.isExtracting) return@SetupPage
                        uiState = uiState.copy(isExtracting = true, statusMessage = context.getString(R.string.init_preparing_file))
                        onExtract(uiState.components) { index, progress, installed, status ->
                            val next = uiState.components.toMutableList()
                            if (index in next.indices) next[index] = next[index].copy(progress = progress, isInstalled = installed, status = status)
                            val overall = if (next.isNotEmpty()) next.sumOf { it.progress.coerceIn(0, 100) } / next.size else 0
                            uiState = uiState.copy(components = next, overallProgress = overall, statusMessage = status, isComplete = overall >= 100)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LegalPage(
    palette: InitPalette,
    appVersionName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = palette.cardSoft,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_init_logo),
                    contentDescription = null,
                    modifier = Modifier.size(76.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.main_splash_brand), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.app_version, appVersionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = palette.card,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(stringResource(R.string.init_legal_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.init_legal_terms),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FireworkSky/RotatingartLauncher"))) }
                            .onFailure { Toast.makeText(context, context.getString(R.string.init_cannot_open_browser), Toast.LENGTH_SHORT).show() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.primaryAction)
                ) {
                    Text(stringResource(R.string.init_legal_official_download))
                }
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDecline, border = BorderStroke(1.dp, palette.border)) { Text(stringResource(R.string.init_exit)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAction, contentColor = palette.primaryActionText)
                    ) { Text(stringResource(R.string.init_accept_and_continue)) }
                }
            }
        }
    }
}

@Composable
private fun SetupPage(
    palette: InitPalette,
    uiState: InitUiState,
    onRequestPermissions: () -> Unit,
    onStartExtraction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = palette.card,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.init_runtime_components_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (uiState.isExtracting) stringResource(R.string.init_installing) else stringResource(R.string.init_click_to_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { uiState.overallProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = palette.overallProgress,
                    trackColor = palette.overallProgressTrack
                )
                Text(
                    "${uiState.overallProgress.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.overallProgress
                )
                Text(
                    text = uiState.statusMessage.ifBlank { stringResource(R.string.init_runtime_components_hint) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!uiState.hasPermissions) {
                    FilledTonalButton(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.softAction, contentColor = palette.softActionText)
                    ) { Text(stringResource(R.string.init_grant_permissions)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onStartExtraction,
                    enabled = uiState.hasPermissions && !uiState.isExtracting && !uiState.isComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAction, contentColor = palette.primaryActionText)
                ) { Text(stringResource(R.string.init_start_install)) }
            }
        }

        Surface(
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = palette.cardSoft,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.init_runtime_components_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.components.forEach { component ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = palette.card,
                            border = BorderStroke(1.dp, palette.border)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (component.name == "dotnet") stringResource(R.string.asset_check_component_dotnet_runtime) else component.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(component.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(
                                    progress = { if (component.isInstalled) 1f else component.progress.coerceIn(0, 100) / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = palette.componentProgress,
                                    trackColor = palette.componentProgressTrack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 420)
@Composable
private fun InitPreview() {
    val context = LocalContext.current
    val state = InitUiState(
        hasPermissions = true,
        components = listOf(ComponentState("dotnet", context.getString(R.string.init_component_dotnet_desc), "dotnet.tar.xz", true, progress = 45)),
        isExtracting = true,
        overallProgress = 45,
        statusMessage = context.getString(R.string.init_extracting)
    )
    RaLaunchTheme {
        SetupPage(
            palette = rememberInitPalette(),
            uiState = state,
            onRequestPermissions = {},
            onStartExtraction = {}
        )
    }
}
