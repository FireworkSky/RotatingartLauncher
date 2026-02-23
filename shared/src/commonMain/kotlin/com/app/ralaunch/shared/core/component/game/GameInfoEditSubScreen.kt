package com.app.ralaunch.shared.core.component.game

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.component.dialogs.RendererOption
import com.app.ralaunch.shared.core.component.dialogs.RendererSelectDialog
import com.app.ralaunch.shared.core.model.ui.GameItemUi

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GameInfoEditSubScreen(
    game: GameItemUi,
    rendererOptions: List<RendererOption>,
    onBack: () -> Unit,
    onSave: (GameItemUi) -> Unit,
    followGlobalSettingsText: String,
    editGameInfoTitle: String,
    backContentDescription: String,
    editDisplayInfoText: String,
    gameNameLabelText: String,
    gameDescriptionLabelText: String,
    rendererOptionalText: String,
    rendererOverrideLabelText: String,
    followGlobalButtonText: String,
    selectRendererButtonText: String,
    cancelButtonText: String,
    saveButtonText: String,
    modifier: Modifier = Modifier
) {
    var editedName by remember(game.id) { mutableStateOf(game.displayedName) }
    var editedDescription by remember(game.id) { mutableStateOf(game.displayedDescription ?: "") }
    val initialRendererOverride = remember(game.id, rendererOptions) {
        game.rendererOverride?.takeIf { rendererId ->
            rendererOptions.any { option -> option.renderer == rendererId }
        }
    }
    var editedRendererOverride by remember(game.id, rendererOptions) { mutableStateOf(initialRendererOverride) }
    var showRendererDialog by remember { mutableStateOf(false) }

    val rendererDisplayName = remember(editedRendererOverride, rendererOptions, followGlobalSettingsText) {
        editedRendererOverride?.let { rendererId ->
            rendererOptions.firstOrNull { it.renderer == rendererId }?.name ?: rendererId
        } ?: followGlobalSettingsText
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(editGameInfoTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription
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
                text = editDisplayInfoText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text(gameNameLabelText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editedDescription,
                onValueChange = { editedDescription = it },
                label = { Text(gameDescriptionLabelText) },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rendererOptionalText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = rendererDisplayName,
                onValueChange = {},
                label = { Text(rendererOverrideLabelText) },
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
                    Text(followGlobalButtonText)
                }

                Button(
                    onClick = { showRendererDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(selectRendererButtonText)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onBack) {
                    Text(cancelButtonText)
                }
                Button(
                    onClick = {
                        val updated = game.copy(
                            displayedName = editedName.trim(),
                            displayedDescription = editedDescription.trim().ifEmpty { null },
                            rendererOverride = editedRendererOverride
                        )
                        onSave(updated)
                        onBack()
                    },
                    enabled = editedName.trim().isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(saveButtonText)
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
}
