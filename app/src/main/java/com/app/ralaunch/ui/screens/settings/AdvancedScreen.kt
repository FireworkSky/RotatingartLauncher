package com.app.ralaunch.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.app.ralaunch.locales.AppLanguage
import com.app.ralaunch.locales.AppLanguage.Companion.fromDisplayName
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.locales.LocaleManager.strings
import com.app.ralaunch.ui.components.SettingsComponents

@Composable
fun AdvancedScreen() {
    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsComponents.DropdownSetting(
                    title = strings.settingsRenderer,
                    icon = Icons.Default.Tune,
                    enabled = true,
                    options = listOf(

                    ),
                    selectedOption = LocaleManager.currentLanguage.displayName,
                    onOptionSelected = {
                        if (it == strings.settingsFollowSystem) LocaleManager.setLanguage(AppLanguage.SYSTEM)
                        else LocaleManager.setLanguage(fromDisplayName(it))
                    }
                )
            }
        }
    }
}