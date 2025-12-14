package com.app.ralaunch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Simple data class for Game - adjust fields as needed
data class Game(
    val id: Int,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(refreshKey: Int) {
    // Sample game list - replace with your actual data source
    val games = remember {
        listOf(
            Game(1, "Terraria")
        )
    }

    // State: Currently selected game. Null means no selection.
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    // State: Expanded state for the dropdown menu
    var isRuntimeMenuExpanded by remember { mutableStateOf(false) }
    // Example Runtime options - replace with your actual options
    val runtimeOptions = listOf(".NET 6.0", ".NET 7.0", ".NET 8.0")
    // State: Selected runtime. Default to ".NET 8.0"
    var selectedRuntime by remember { mutableStateOf(".NET 8.0") } // Set default here

    // Derived state: Check if a game is selected
    val isGameSelected = selectedGame != null


    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Left Side: Game Cards ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            items(games) { game ->
                GameCard(
                    game = game,
                    isSelected = game == selectedGame,
                    onClick = {
                        // Toggle selection: if clicked game is already selected, deselect; otherwise, select it.
                        selectedGame = if (game == selectedGame) null else game
                        // Note: Runtime selection is NOT reset on game change/deselect anymore
                        // Keeping the previously selected/default runtime
                    }
                )
            }
        }

        // --- Right Side: Detail Card ---
        DetailCard(
            selectedGame = selectedGame,
            runtimeOptions = runtimeOptions,
            selectedRuntime = selectedRuntime,
            isRuntimeMenuExpanded = isRuntimeMenuExpanded,
            onRuntimeMenuExpandedChange = { isRuntimeMenuExpanded = it },
            onRuntimeSelected = { selectedRuntime = it },
            onStartGameClick = { /* TODO: Implement start game logic */ },
            isGameSelected = isGameSelected
        )
    }
}

@Composable
fun GameCard(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )

    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = game.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCard(
    selectedGame: Game?,
    runtimeOptions: List<String>,
    selectedRuntime: String,
    isRuntimeMenuExpanded: Boolean,
    onRuntimeMenuExpandedChange: (Boolean) -> Unit,
    onRuntimeSelected: (String) -> Unit,
    onStartGameClick: () -> Unit,
    isGameSelected: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (isGameSelected && selectedGame != null) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "No game selected",
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = selectedGame?.name ?: "No Game Selected",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (isGameSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = isRuntimeMenuExpanded,
                onExpandedChange = onRuntimeMenuExpandedChange
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedRuntime,
                    onValueChange = { },
                    label = { Text("Select .NET Runtime") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRuntimeMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    enabled = isGameSelected // Enable/disable dropdown based on game selection
                )
                ExposedDropdownMenu(
                    expanded = isRuntimeMenuExpanded,
                    onDismissRequest = { onRuntimeMenuExpandedChange(false) }
                ) {
                    runtimeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onRuntimeSelected(option)
                                onRuntimeMenuExpandedChange(false)
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            Button(
                onClick = onStartGameClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp) // Space above the button
                    .height(IntrinsicSize.Min), // Ensures it has minimum height, not squashed
                enabled = isGameSelected // Enable only if a game is selected
                // Colors and shape use defaults from MaterialTheme unless overridden
            ) {
                Text(text = "Start Game", style = MaterialTheme.typography.labelLarge) // Standard button text style
            }
        }
    }
}