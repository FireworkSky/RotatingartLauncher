package com.app.ralaunch.feature.patch.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.R
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.vm.PatchManagementViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PatchManagementSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val gameRepository: GameRepositoryV2? = remember {
        try { KoinJavaComponent.getOrNull(GameRepositoryV2::class.java) } catch (_: Exception) { null }
    }
    val patchManager: PatchManager? = remember {
        try { KoinJavaComponent.getOrNull(PatchManager::class.java) } catch (_: Exception) { null }
    }
    
    var games by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var selectedGameIndex by remember { mutableIntStateOf(-1) }
    var patches by remember { mutableStateOf<List<Patch>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        games = gameRepository?.games?.value ?: emptyList()
    }
    
    LaunchedEffect(selectedGame) {
        patches = selectedGame?.let { game ->
            patchManager?.getApplicablePatches(game.gameId) ?: emptyList()
        } ?: emptyList()
    }
    
    val patchFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importPatchFile(context, patchManager, it) { success ->
                    if (success) {
                        selectedGame?.let { game ->
                            patches = patchManager?.getApplicablePatches(game.gameId) ?: emptyList()
                        }
                    }
                }
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.patch_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row {
                        TextButton(
                            onClick = {
                                patchFilePicker.launch("application/zip")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.patch_dialog_import))
                        }
                        
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.ok))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.patch_dialog_import))
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GameListPanel(
                        games = games,
                        selectedIndex = selectedGameIndex,
                        onGameSelected = { game, index ->
                            selectedGame = game
                            selectedGameIndex = index
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    PatchListPanel(
                        patches = patches,
                        selectedGame = selectedGame,
                        patchManager = patchManager,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameListPanel(
    games: List<GameItem>,
    selectedIndex: Int,
    onGameSelected: (GameItem, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.game_list_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (games.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.patch_no_games),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(games) { index, game ->
                        GameSelectableItem(
                            game = game,
                            isSelected = index == selectedIndex, // FIXED
                            onClick = { onGameSelected(game, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSelectableItem(
    game: GameItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0f)
    }
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val iconPathFull = game.iconPathFull
                if (!iconPathFull.isNullOrEmpty() && File(iconPathFull).exists()) {
                    val bitmap = remember(iconPathFull) {
                        BitmapFactory.decodeFile(iconPathFull)?.asImageBitmap()
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: DefaultGameIcon()
                } else {
                    DefaultGameIcon()
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = game.displayedName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DefaultGameIcon() {
    Icon(
        imageVector = Icons.Default.Gamepad,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PatchListPanel(
    patches: List<Patch>,
    selectedGame: GameItem?,
    isPatchEnabled: (String) -> Boolean,
    onPatchEnabledChange: (Patch, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.patch_list_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            when {
                selectedGame == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_game_selected),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                patches.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_patches_for_game),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(patches) { patch ->
                            PatchItem(
                                patch = patch,
                                isEnabled = isPatchEnabled(patch.manifest.id),
                                onCheckedChange = { enabled ->
                                    onPatchEnabledChange(patch, enabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchItem(
    patch: Patch,
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val gameAsmPath = remember(selectedGame) {
        selectedGame.gameExePathFull?.let { File(it) } ?: File(selectedGame.gameExePathRelative)
    }
    var isEnabled by remember(patch, selectedGame) {
        mutableStateOf(patchManager?.isPatchEnabled(gameAsmPath, patch.manifest.id) ?: false)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patch.manifest.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (patch.manifest.description.isNotEmpty()) {
                    Text(
                        text = patch.manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

private suspend fun importPatchFile(
    context: android.content.Context,
    patchManager: PatchManager?,
    uri: Uri,
    onResult: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            TemporaryFileAcquirer().use { tfa ->
                val tempPatchFile = tfa.acquireTempFilePath("imported_patch.zip")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempPatchFile).use { outputStream ->
                        StreamUtils.transferTo(inputStream, outputStream)
                    }
                }
                val result = patchManager?.installPatch(tempPatchFile) ?: false
                withContext(Dispatchers.Main) {
                    if (result) {
                        Toast.makeText(context, R.string.patch_dialog_import_successful, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.patch_dialog_import_failed, Toast.LENGTH_SHORT).show()
                    }
                    onResult(result)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.patch_dialog_import_failed, Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        }
    }
}
