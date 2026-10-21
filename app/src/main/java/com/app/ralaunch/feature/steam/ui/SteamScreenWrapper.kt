package com.app.ralaunch.feature.steam.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.feature.steam.vm.SteamViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SteamScreenWrapper(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SteamViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        val message = state.message
        if (!message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    SteamScreen(
        state = state,
        games = viewModel.games,
        onAccountChange = viewModel::onAccountInput,
        onPasswordChange = viewModel::onPasswordInput,
        onGuardChange = viewModel::onGuardInput,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onSubmitGuard = viewModel::submitGuard,
        onInstall = viewModel::install,
        onWorkshopQueryChange = viewModel::onWorkshopQuery,
        onWorkshopSearch = viewModel::searchWorkshop,
        onWorkshopDownload = viewModel::downloadWorkshop,
        selectedBranches = state.selectedBranches,
        unlockedBranches = state.unlockedBranches,
        secretInput = state.secretInput,
        onBranchSelect = viewModel::onBranchSelect,
        onSecretInput = viewModel::onSecretInput,
        onUnlockSecret = viewModel::unlockSecret,
        modifier = modifier,
    )
}
