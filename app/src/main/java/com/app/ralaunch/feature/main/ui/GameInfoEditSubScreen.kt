package com.app.ralaunch.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.R
import com.app.ralaunch.core.ui.dialog.DotNetRuntimeOption
import com.app.ralaunch.core.ui.dialog.DotNetRuntimeSelectDialog
import com.app.ralaunch.core.ui.dialog.RendererOption
import com.app.ralaunch.core.ui.dialog.RendererSelectDialog
import com.app.ralaunch.core.model.GameItemUi
import com.app.ralaunch.feature.main.vm.GameInfoEditViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GameInfoEditSubScreen(
    game: GameItemUi,
    rendererOptions: List<RendererOption>,
    onBack: () -> Unit,
    onSave: (GameItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameInfoEditViewModel: GameInfoEditViewModel = koinViewModel()
    val runtimeState by gameInfoEditViewModel.uiState.collectAsState()
    var editedName by remember(game.id) { mutableStateOf(game.displayedName) }
    var editedDescription by remember(game.id) { mutableStateOf(game.displayedDescription ?: "") }
    val initialRendererOverride = remember(game.id, rendererOptions) {
        game.rendererOverride?.takeIf { rendererId ->
            rendererOptions.any { option -> option.renderer == rendererId }
        }
    }
    var editedRendererOverride by remember(game.id, rendererOptions) { mutableStateOf(initialRendererOverride) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var editedDotNetRuntimeVersionOverride by remember(game.id) {
        mutableStateOf(game.dotNetRuntimeVersionOverride?.trim()?.takeIf { it.isNotEmpty() })
    }
    var showDotNetRuntimeDialog by remember { mutableStateOf(false) }

    val rendererDisplayName = remember(editedRendererOverride, rendererOptions) {
        editedRendererOverride?.let { rendererId ->
            rendererOptions.firstOrNull { it.renderer == rendererId }?.name ?: rendererId
        } ?: ""
    }
    val installedDotNetRuntimeVersions = runtimeState.installedDotNetRuntimeVersions
    val globalDotNetRuntimeVersion = runtimeState.globalDotNetRuntimeVersion
    val dotNetRuntimeOptions = remember(installedDotNetRuntimeVersions) {
        installedDotNetRuntimeVersions.map { version ->
            DotNetRuntimeOption(version = version)
        }
    }
    val effectiveDotNetRuntimeVersion = remember(
        editedDotNetRuntimeVersionOverride,
        installedDotNetRuntimeVersions,
        globalDotNetRuntimeVersion
    ) {
        editedDotNetRuntimeVersionOverride
            ?.takeIf { it in installedDotNetRuntimeVersions }
            ?: globalDotNetRuntimeVersion
    }
    val dotNetRuntimeDisplayName = editedDotNetRuntimeVersionOverride
        ?: stringResource(R.string.runtime_follow_global_settings)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_edit_game_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.main_edit_display_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text(stringResource(R.string.game_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editedDescription,
                onValueChange = { editedDescription = it },
                label = { Text(stringResource(R.string.game_description)) },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.main_renderer_optional),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = rendererDisplayName.ifEmpty {
                    stringResource(R.string.renderer_follow_global_settings)
                },
                onValueChange = {},
                label = { Text(stringResource(R.string.main_renderer_override)) },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { editedRendererOverride = null },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.renderer_follow_global))
                }

                Button(
                    onClick = { showRendererDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.renderer_select))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.main_dotnet_runtime_optional),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = dotNetRuntimeDisplayName,
                onValueChange = {},
                label = { Text(stringResource(R.string.main_dotnet_runtime_override)) },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "${stringResource(R.string.runtime_current_version)}: ${effectiveDotNetRuntimeVersion ?: stringResource(R.string.runtime_not_installed)}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { editedDotNetRuntimeVersionOverride = null },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_follow_global))
                }

                Button(
                    onClick = { showDotNetRuntimeDialog = true },
                    enabled = dotNetRuntimeOptions.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_select_version))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val updated = game.copy(
                            displayedName = editedName.trim(),
                            displayedDescription = editedDescription.trim().ifEmpty { null },
                            rendererOverride = editedRendererOverride,
                            dotNetRuntimeVersionOverride = editedDotNetRuntimeVersionOverride
                        )
                        onSave(updated)
                        onBack()
                    },
                    enabled = editedName.trim().isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.control_edit_save))
                }
            }
        }
    }

    if (showRendererDialog) {
        RendererSelectDialog(
            currentRenderer = editedRendererOverride ?: (rendererOptions.firstOrNull()?.renderer ?: "native"),
            renderers = rendererOptions,
            onSelect = { renderer ->
                editedRendererOverride = renderer
            },
            onDismiss = { showRendererDialog = false }
        )
    }

    if (showDotNetRuntimeDialog) {
        DotNetRuntimeSelectDialog(
            currentRuntimeVersion = editedDotNetRuntimeVersionOverride,
            runtimes = dotNetRuntimeOptions,
            onSelect = { version ->
                editedDotNetRuntimeVersionOverride = version
            },
            onDismiss = { showDotNetRuntimeDialog = false }
        )
    }
}
