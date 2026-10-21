package com.app.ralaunch.feature.steam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.app.ralaunch.feature.steam.data.SteamGame
import com.app.ralaunch.feature.steam.vm.SteamUiState
import com.app.ralaunch.feature.steam.vm.WorkshopEntry

@Composable
fun SteamScreen(
    state: SteamUiState,
    games: List<SteamGame>,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onGuardChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSubmitGuard: () -> Unit,
    onInstall: (SteamGame) -> Unit,
    onWorkshopQueryChange: (String) -> Unit,
    onWorkshopSearch: () -> Unit,
    onWorkshopDownload: (WorkshopEntry) -> Unit,
    selectedBranches: Map<Int, String>,
    unlockedBranches: Set<String>,
    secretInput: String,
    onBranchSelect: (Int, String) -> Unit,
    onSecretInput: (String) -> Unit,
    onUnlockSecret: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { AccountCard(state, onAccountChange, onPasswordChange, onGuardChange, onLogin, onLogout, onSubmitGuard) }
        if (state.status.isNotBlank() || state.progress != null) {
            item { StatusCard(state) }
        }
        item {
            Text("Steam 游戏", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(games, key = { it.appId }) { game ->
            GameCard(game, state, onInstall, selectedBranches, unlockedBranches, onBranchSelect)
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("创意工坊", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.workshopQuery,
                    onValueChange = onWorkshopQueryChange,
                    label = { Text("搜索 mod") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onWorkshopSearch, enabled = !state.workshopBusy) { Text("搜索") }
            }
        }
        item {
            SecretCodeRow(secretInput, onSecretInput, onUnlockSecret, state.unlockedBranches)
        }
        items(state.workshopItems, key = { it.publishedFileId }) { item ->
            WorkshopCard(item, onDownload = { onWorkshopDownload(item) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AccountCard(
    state: SteamUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onGuardChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSubmitGuard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Steam 账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.loggedIn) {
                Text("已登录：${state.accountName.ifBlank { "（未记录账号名）" }}")
                OutlinedButton(onClick = onLogout) { Text("退出登录") }
            } else {
                OutlinedTextField(
                    value = state.accountInput,
                    onValueChange = onAccountChange,
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.needGuard) {
                    OutlinedTextField(
                        value = state.guardInput,
                        onValueChange = onGuardChange,
                        label = { Text("Steam Guard 验证码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onSubmitGuard) { Text("提交验证码") }
                }
                Button(onClick = onLogin, enabled = !state.busy) {
                    if (state.busy) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("登录")
                }
                Text(
                    "匿名也可用：工坊查询/下载不需要账号；下载游戏内容才需要 license。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: SteamUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${"%.1f".format(progress)}%" + (state.currentFile?.let { "  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    game: SteamGame,
    state: SteamUiState,
    onInstall: (SteamGame) -> Unit,
    selectedBranches: Map<Int, String>,
    unlockedBranches: Set<String>,
    onBranchSelect: (Int, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                val installed = state.installed[game.appId]
                if (installed != null) {
                    Text(installed, style = MaterialTheme.typography.bodySmall)
                }
            }
            val currentBranch = selectedBranches[game.appId] ?: game.branch
            Text("分支 $currentBranch · ${game.platform} · appid ${game.appId}", style = MaterialTheme.typography.bodySmall)
            if (game.branches.isNotEmpty()) {
                val visible = game.branches.filter { branch ->
                    branch.secretCode == null || branch.id in unlockedBranches
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { branch ->
                        val selected = branch.id == currentBranch
                        if (selected) {
                            Button(onClick = {}, enabled = false) { Text(branch.label) }
                        } else {
                            OutlinedButton(onClick = { onBranchSelect(game.appId, branch.id) }) { Text(branch.label) }
                        }
                    }
                }
            }
            if (game.notes.isNotBlank()) {
                Text(game.notes, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                Button(onClick = { onInstall(game) }, enabled = !state.busy) {
                    Text(if (game.requiresTerraria) "下载并覆盖" else "下载")
                }
                if (game.requiresTerraria) {
                    Spacer(Modifier.width(8.dp))
                    Text("需要先装好 Terraria", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun SecretCodeRow(
    secretInput: String,
    onSecretInput: (String) -> Unit,
    onUnlockSecret: () -> Unit,
    unlockedBranches: Set<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("秘密代码", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (unlockedBranches.isEmpty()) "输入秘密代码可解锁隐藏分支" else "已解锁 ${unlockedBranches.size} 个隐藏分支",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = onSecretInput,
                    label = { Text("代码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onUnlockSecret) { Text("解锁") }
            }
        }
    }
}

@Composable
private fun WorkshopCard(item: WorkshopEntry, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${"%.1f".format(item.fileSize / 1024.0 / 1024.0)} MB · ${item.subscriptions} 订阅 · ${item.tags.take(4).joinToString(" / ")}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.description.isNotBlank()) {
                Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            TextButton(onClick = onDownload) { Text("下载到 Mods") }
        }
    }
}
